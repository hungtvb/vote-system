'use client';

import { useCallback, useEffect, useSyncExternalStore } from 'react';
import { authApi } from '@/shared/api/auth-api';
import { runAuthorizedRequest } from '@/shared/api/authorized-request';
import { ApiError } from '@/shared/api/transport';
import type { Session, UpdateUserProfileRequest, UserProfile } from '@/shared/api/types';
import { userApi } from '@/shared/api/user-api';
import { resolveAuthProfile, sessionOnly } from '@/shared/auth/session-bootstrap';
import {
  clearProfileSnapshot,
  readProfileSnapshot,
  snapshotFromProfile,
  type ProfilePresentationSnapshot,
  writeProfileSnapshot
} from '@/shared/auth/profile-snapshot';

interface SessionState {
  session: Session | null;
  profile: UserProfile | null;
  profileSnapshot: ProfilePresentationSnapshot | null;
  restoring: boolean;
}

class SessionTransitionCancelled extends Error {}

const SERVER_STATE: SessionState = { session: null, profile: null, profileSnapshot: null, restoring: true };
let state: SessionState = SERVER_STATE;
let sessionEpoch = 0;
let restorePromise: Promise<void> | null = null;
let refreshPromise: Promise<Session> | null = null;
let snapshotHydrated = false;
const listeners = new Set<() => void>();

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function getSnapshot(): SessionState {
  return state;
}

function getServerSnapshot(): SessionState {
  return SERVER_STATE;
}

function publish(next: Partial<SessionState>) {
  state = { ...state, ...next };
  for (const listener of listeners) listener();
}

function browserStorage(): Storage | null {
  if (typeof window === 'undefined') return null;
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

function hydrateProfileSnapshotShared() {
  if (snapshotHydrated) return;
  snapshotHydrated = true;
  const storage = browserStorage();
  if (!storage) return;
  publish({ profileSnapshot: readProfileSnapshot(storage) });
}

function replaceProfileSnapshotShared(profile: UserProfile): ProfilePresentationSnapshot {
  const snapshot = snapshotFromProfile(profile);
  const storage = browserStorage();
  if (storage && state.profileSnapshot && state.profileSnapshot.userId !== profile.id) {
    clearProfileSnapshot(storage);
  }
  if (storage && !writeProfileSnapshot(storage, profile)) clearProfileSnapshot(storage);
  return snapshot;
}

function clearSessionShared() {
  sessionEpoch += 1;
  const storage = browserStorage();
  if (storage) clearProfileSnapshot(storage);
  publish({ session: null, profile: null, profileSnapshot: null });
}

async function prepareSession(next: Session) {
  // The /users/me fallback supports one rolling-deploy window with an older backend.
  // Normal register, login and refresh responses include profile and never take it.
  const nextProfile = await resolveAuthProfile(next, userApi.current);
  if (nextProfile.id !== next.userId) {
    throw new Error('Authenticated profile does not match the issued session');
  }
  return { nextSession: sessionOnly(next), nextProfile };
}

async function prepareAndPublishSession(next: Session, expectedEpoch: number) {
  const prepared = await prepareSession(next);
  if (expectedEpoch !== sessionEpoch) throw new SessionTransitionCancelled();
  publish({
    session: prepared.nextSession,
    profile: prepared.nextProfile,
    profileSnapshot: replaceProfileSnapshotShared(prepared.nextProfile)
  });
  return prepared;
}

async function saveSessionShared(next: Session): Promise<UserProfile> {
  const expectedEpoch = ++sessionEpoch;
  try {
    const prepared = await prepareAndPublishSession(next, expectedEpoch);
    return prepared.nextProfile;
  } catch (error) {
    if (!(error instanceof SessionTransitionCancelled) && expectedEpoch === sessionEpoch) {
      clearSessionShared();
    }
    throw error;
  }
}

function refreshSessionShared(): Promise<Session> {
  if (!refreshPromise) {
    const expectedEpoch = sessionEpoch;
    refreshPromise = authApi.refresh()
      .then(next => prepareAndPublishSession(next, expectedEpoch))
      .then(prepared => prepared.nextSession)
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

function ensureSessionRestore(): Promise<void> {
  if (!restorePromise) {
    restorePromise = refreshSessionShared()
      .then(() => undefined)
      .catch(error => {
        if (error instanceof SessionTransitionCancelled) return;
        clearSessionShared();
        if (!(error instanceof ApiError) || (error.status !== 401 && error.status !== 403)) {
          console.error('Session restore failed', error);
        }
      })
      .finally(() => publish({ restoring: false }));
  }
  return restorePromise;
}

async function runAuthorizedShared<T>(operation: (activeSession: Session) => Promise<T>): Promise<T> {
  if (state.restoring) await ensureSessionRestore();
  return runAuthorizedRequest(operation, {
    getSession: () => state.session,
    refresh: refreshSessionShared,
    setSession: () => undefined,
    clearSession: clearSessionShared
  });
}

async function updateProfileShared(payload: UpdateUserProfileRequest): Promise<UserProfile> {
  const expectedEpoch = sessionEpoch;
  const nextProfile = await runAuthorizedShared(activeSession =>
    userApi.updateCurrent(payload, activeSession.accessToken));
  if (expectedEpoch === sessionEpoch) {
    publish({ profile: nextProfile, profileSnapshot: replaceProfileSnapshotShared(nextProfile) });
  }
  return nextProfile;
}

async function logoutShared() {
  const active = state.session;
  clearSessionShared();
  try {
    if (active) await authApi.logout(active.accessToken);
  } catch (error) {
    console.error('Logout request failed', error);
  }
}

async function logoutAllShared() {
  const active = state.session;
  clearSessionShared();
  try {
    if (active) await authApi.logoutAll(active.accessToken);
  } catch (error) {
    console.error('Logout-all request failed', error);
  }
}

export function updateActiveUserProfile(payload: UpdateUserProfileRequest): Promise<UserProfile> {
  return updateProfileShared(payload);
}

export function useSession() {
  const snapshot = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

  useEffect(() => {
    hydrateProfileSnapshotShared();
    void ensureSessionRestore();
  }, []);

  const saveSession = useCallback((next: Session) => saveSessionShared(next), []);
  const updateProfile = useCallback((payload: UpdateUserProfileRequest) => updateProfileShared(payload), []);
  const clearSession = useCallback(() => clearSessionShared(), []);
  const runAuthorized = useCallback(<T,>(operation: (activeSession: Session) => Promise<T>) =>
    runAuthorizedShared(operation), []);
  const logout = useCallback(() => logoutShared(), []);
  const logoutAll = useCallback(() => logoutAllShared(), []);

  return {
    session: snapshot.session,
    profile: snapshot.profile,
    profileSnapshot: snapshot.profileSnapshot,
    restoring: snapshot.restoring,
    saveSession,
    updateProfile,
    clearSession,
    runAuthorized,
    logout,
    logoutAll
  };
}

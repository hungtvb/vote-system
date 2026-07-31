'use client';

import { useCallback, useEffect, useSyncExternalStore } from 'react';
import { authApi } from '@/shared/api/auth-api';
import { runAuthorizedRequest } from '@/shared/api/authorized-request';
import { ApiError } from '@/shared/api/transport';
import type { Session, UpdateUserProfileRequest, UserProfile } from '@/shared/api/types';
import { userApi } from '@/shared/api/user-api';
import { resolveAuthProfile, sessionOnly } from '@/shared/auth/session-bootstrap';

interface SessionState {
  session: Session | null;
  profile: UserProfile | null;
  restoring: boolean;
}

const SERVER_STATE: SessionState = { session: null, profile: null, restoring: true };
let state: SessionState = SERVER_STATE;
let restorePromise: Promise<void> | null = null;
let refreshPromise: Promise<Session> | null = null;
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

function clearSessionShared() {
  publish({ session: null, profile: null });
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

async function saveSessionShared(next: Session): Promise<UserProfile> {
  try {
    const prepared = await prepareSession(next);
    publish({ session: prepared.nextSession, profile: prepared.nextProfile });
    return prepared.nextProfile;
  } catch (error) {
    clearSessionShared();
    throw error;
  }
}

function refreshSessionShared(): Promise<Session> {
  if (!refreshPromise) {
    refreshPromise = authApi.refresh()
      .then(async next => {
        await saveSessionShared(next);
        return sessionOnly(next);
      })
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
    setSession: next => publish({ session: next }),
    clearSession: clearSessionShared
  });
}

async function updateProfileShared(payload: UpdateUserProfileRequest): Promise<UserProfile> {
  const nextProfile = await runAuthorizedShared(activeSession =>
    userApi.updateCurrent(payload, activeSession.accessToken));
  publish({ profile: nextProfile });
  return nextProfile;
}

async function logoutShared() {
  const active = state.session;
  try {
    if (active) await authApi.logout(active.accessToken);
  } catch (error) {
    console.error('Logout request failed', error);
  } finally {
    clearSessionShared();
  }
}

async function logoutAllShared() {
  const active = state.session;
  try {
    if (active) await authApi.logoutAll(active.accessToken);
  } catch (error) {
    console.error('Logout-all request failed', error);
  } finally {
    clearSessionShared();
  }
}

export function updateActiveUserProfile(payload: UpdateUserProfileRequest): Promise<UserProfile> {
  return updateProfileShared(payload);
}

export function useSession() {
  const snapshot = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

  useEffect(() => {
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
    restoring: snapshot.restoring,
    saveSession,
    updateProfile,
    clearSession,
    runAuthorized,
    logout,
    logoutAll
  };
}

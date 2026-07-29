'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { authApi } from '@/shared/api/auth-api';
import { runAuthorizedRequest } from '@/shared/api/authorized-request';
import { ApiError } from '@/shared/api/transport';
import type { AuthBootstrap, Session, UpdateUserProfileRequest, UserProfile } from '@/shared/api/types';
import { userApi } from '@/shared/api/user-api';

type ProfileUpdater = (payload: UpdateUserProfileRequest) => Promise<UserProfile>;
let activeProfileUpdater: ProfileUpdater | null = null;

export function updateActiveUserProfile(payload: UpdateUserProfileRequest): Promise<UserProfile> {
  if (!activeProfileUpdater) return Promise.reject(new Error('Authenticated profile session is unavailable'));
  return activeProfileUpdater(payload);
}

function sessionOnly(next: Session): Session {
  return {
    tokenType: next.tokenType,
    accessToken: next.accessToken,
    expiresInSeconds: next.expiresInSeconds,
    userId: next.userId,
    email: next.email,
    role: next.role
  };
}

function hasBootstrapProfile(next: Session): next is AuthBootstrap {
  const profile = (next as Partial<AuthBootstrap>).profile;
  return Boolean(profile && profile.id === next.userId);
}

export function useSession() {
  const [session, setSession] = useState<Session | null>(null);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [restoring, setRestoring] = useState(true);
  const sessionRef = useRef<Session | null>(null);

  const commitSession = useCallback((next: Session | null) => {
    sessionRef.current = next;
    setSession(next);
  }, []);

  const clearSession = useCallback(() => {
    commitSession(null);
    setProfile(null);
  }, [commitSession]);

  const saveSession = useCallback(async (next: Session) => {
    try {
      // The /users/me fallback supports one rolling-deploy window with an older backend.
      // Normal register, login and refresh responses include profile and never take it.
      const nextProfile = hasBootstrapProfile(next)
        ? next.profile
        : await userApi.current(next.accessToken);
      if (nextProfile.id !== next.userId) throw new Error('Authenticated profile does not match the issued session');
      commitSession(sessionOnly(next));
      setProfile(nextProfile);
      return nextProfile;
    } catch (error) {
      clearSession();
      throw error;
    }
  }, [clearSession, commitSession]);

  const refreshSession = useCallback(async () => {
    const next = await authApi.refresh();
    await saveSession(next);
    return sessionOnly(next);
  }, [saveSession]);

  useEffect(() => {
    let active = true;
    void authApi.refresh()
      .then(async next => {
        if (!active) return;
        await saveSession(next);
      })
      .catch(error => {
        if (!active) return;
        clearSession();
        if (!(error instanceof ApiError) || (error.status !== 401 && error.status !== 403)) {
          console.error('Session restore failed', error);
        }
      })
      .finally(() => { if (active) setRestoring(false); });
    return () => { active = false; };
  }, [clearSession, saveSession]);

  const runAuthorized = useCallback(<T,>(operation: (activeSession: Session) => Promise<T>) =>
    runAuthorizedRequest(operation, {
      getSession: () => sessionRef.current,
      refresh: refreshSession,
      setSession: commitSession,
      clearSession
    }), [clearSession, commitSession, refreshSession]);

  const updateProfile = useCallback(async (payload: UpdateUserProfileRequest) => {
    const nextProfile = await runAuthorized(activeSession =>
      userApi.updateCurrent(payload, activeSession.accessToken));
    setProfile(nextProfile);
    return nextProfile;
  }, [runAuthorized]);

  useEffect(() => {
    activeProfileUpdater = updateProfile;
    return () => {
      if (activeProfileUpdater === updateProfile) activeProfileUpdater = null;
    };
  }, [updateProfile]);

  const logout = useCallback(async () => {
    const active = sessionRef.current;
    try {
      if (active) await authApi.logout(active.accessToken);
    } catch (error) {
      console.error('Logout request failed', error);
    } finally {
      clearSession();
    }
  }, [clearSession]);

  const logoutAll = useCallback(async () => {
    const active = sessionRef.current;
    try {
      if (active) await authApi.logoutAll(active.accessToken);
    } catch (error) {
      console.error('Logout-all request failed', error);
    } finally {
      clearSession();
    }
  }, [clearSession]);

  return { session, profile, restoring, saveSession, updateProfile, clearSession, runAuthorized, logout, logoutAll };
}

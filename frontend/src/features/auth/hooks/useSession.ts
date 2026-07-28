'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { authApi } from '@/shared/api/auth-api';
import { runAuthorizedRequest } from '@/shared/api/authorized-request';
import { ApiError } from '@/shared/api/transport';
import type { Session, UpdateUserProfileRequest, UserProfile } from '@/shared/api/types';
import { userApi } from '@/shared/api/user-api';

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
      const nextProfile = await userApi.current(next.accessToken);
      commitSession(next);
      setProfile(nextProfile);
      return nextProfile;
    } catch (error) {
      clearSession();
      throw error;
    }
  }, [clearSession, commitSession]);

  useEffect(() => {
    let active = true;
    void authApi.refresh()
      .then(async next => {
        const nextProfile = await userApi.current(next.accessToken);
        if (active) {
          commitSession(next);
          setProfile(nextProfile);
        }
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
  }, [clearSession, commitSession]);

  const runAuthorized = useCallback(<T,>(operation: (activeSession: Session) => Promise<T>) =>
    runAuthorizedRequest(operation, {
      getSession: () => sessionRef.current,
      refresh: authApi.refresh,
      setSession: commitSession,
      clearSession
    }), [clearSession, commitSession]);

  const updateProfile = useCallback(async (payload: UpdateUserProfileRequest) => {
    const nextProfile = await runAuthorized(activeSession =>
      userApi.updateCurrent(payload, activeSession.accessToken));
    setProfile(nextProfile);
    return nextProfile;
  }, [runAuthorized]);

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
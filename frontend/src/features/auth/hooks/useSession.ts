'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, ApiError } from '@/shared/api/client';
import type { Session, UserProfile } from '@/shared/api/types';

export function useSession() {
  const [session, setSession] = useState<Session | null>(null);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [restoring, setRestoring] = useState(true);

  const clearSession = useCallback(() => {
    setSession(null);
    setProfile(null);
  }, []);

  const saveSession = useCallback(async (next: Session) => {
    setSession(next);
    try {
      const nextProfile = await api.currentUser(next.accessToken);
      setProfile(nextProfile);
      return nextProfile;
    } catch (error) {
      clearSession();
      throw error;
    }
  }, [clearSession]);

  useEffect(() => {
    let active = true;
    void api.refresh()
      .then(async next => {
        const nextProfile = await api.currentUser(next.accessToken);
        if (active) {
          setSession(next);
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
  }, [clearSession]);

  const logout = useCallback(async () => {
    if (session) {
      try {
        await api.logout(session.accessToken);
      } finally {
        clearSession();
      }
      return;
    }
    clearSession();
  }, [clearSession, session]);

  const logoutAll = useCallback(async () => {
    if (!session) return clearSession();
    try {
      await api.logoutAll(session.accessToken);
    } finally {
      clearSession();
    }
  }, [clearSession, session]);

  return { session, profile, restoring, saveSession, clearSession, logout, logoutAll };
}

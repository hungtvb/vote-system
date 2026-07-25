'use client';

import { useCallback, useEffect, useState } from 'react';
import { api, ApiError } from '@/shared/api/client';
import type { Session } from '@/shared/api/types';

export function useSession() {
  const [session, setSession] = useState<Session | null>(null);
  const [restoring, setRestoring] = useState(true);

  useEffect(() => {
    let active = true;
    void api.refresh()
      .then(next => { if (active) setSession(next); })
      .catch(error => {
        if (active && (!(error instanceof ApiError) || (error.status !== 401 && error.status !== 403))) {
          console.error('Session restore failed', error);
        }
      })
      .finally(() => { if (active) setRestoring(false); });
    return () => { active = false; };
  }, []);

  const saveSession = useCallback((next: Session) => {
    setSession(next);
  }, []);

  const clearSession = useCallback(() => {
    setSession(null);
  }, []);

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

  return { session, restoring, saveSession, clearSession, logout, logoutAll };
}
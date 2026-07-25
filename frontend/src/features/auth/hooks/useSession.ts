'use client';

import { useCallback, useEffect, useState } from 'react';
import { api } from '@/shared/api/client';
import type { Session } from '@/shared/api/types';

const SESSION_KEY = 'vote-system.session';

export function useSession() {
  const [session, setSession] = useState<Session | null>(null);
  const [restoring, setRestoring] = useState(true);

  useEffect(() => {
    const stored = window.localStorage.getItem(SESSION_KEY);
    if (stored) {
      try {
        setSession(JSON.parse(stored) as Session);
      } catch {
        window.localStorage.removeItem(SESSION_KEY);
      }
    }
    setRestoring(false);
  }, []);

  const saveSession = useCallback((next: Session) => {
    setSession(next);
    window.localStorage.setItem(SESSION_KEY, JSON.stringify(next));
  }, []);

  const clearSession = useCallback(() => {
    window.localStorage.removeItem(SESSION_KEY);
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

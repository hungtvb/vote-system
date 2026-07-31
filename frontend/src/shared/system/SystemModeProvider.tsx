'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import {
  systemStatusApi,
  type PublicSystemStatus,
  type SystemMode
} from '@/shared/api/system-status-api';
import { useI18n } from '@/shared/i18n/I18nProvider';
import {
  SYSTEM_MODE_SIGNAL_EVENT,
  type SystemModeSignal
} from './system-mode-signal';
import { selectPublicMessage } from './system-mode-state';

interface SystemModeContextValue {
  status: PublicSystemStatus | null;
  mode: SystemMode;
  loading: boolean;
  refreshing: boolean;
  publicMessage: string;
  canWrite: boolean;
  refresh: () => Promise<void>;
}

const SystemModeContext = createContext<SystemModeContextValue | null>(null);

export function SystemModeProvider({ children }: { children: ReactNode }) {
  const { locale } = useI18n();
  const [status, setStatus] = useState<PublicSystemStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const sequence = useRef(0);
  const activeRequest = useRef<AbortController | null>(null);

  const refresh = useCallback(async () => {
    activeRequest.current?.abort();
    const controller = new AbortController();
    activeRequest.current = controller;
    const requestSequence = ++sequence.current;
    setRefreshing(true);

    try {
      const authoritative = await systemStatusApi.get(controller.signal);
      if (requestSequence === sequence.current) setStatus(authoritative);
    } catch {
      // Status lookup intentionally fails open. Existing state is retained and
      // backend enforcement remains authoritative for every operation.
    } finally {
      if (requestSequence === sequence.current) {
        setLoading(false);
        setRefreshing(false);
      }
      if (activeRequest.current === controller) activeRequest.current = null;
    }
  }, []);

  useEffect(() => {
    void refresh();
    return () => activeRequest.current?.abort();
  }, [refresh]);

  useEffect(() => {
    const handleSignal = (event: Event) => {
      const detail = (event as CustomEvent<SystemModeSignal>).detail;
      if (!detail) return;

      // Fence any older NORMAL response and immediately reconcile the UI with
      // the stable backend rejection while the public status read catches up.
      sequence.current += 1;
      activeRequest.current?.abort();
      setStatus(current => ({
        mode: detail.mode,
        messageVi: current?.messageVi,
        messageEn: current?.messageEn,
        estimatedEndAt: current?.estimatedEndAt,
        updatedAt: current?.updatedAt ?? new Date().toISOString()
      }));
      setLoading(false);
      void refresh();
    };

    globalThis.addEventListener(SYSTEM_MODE_SIGNAL_EVENT, handleSignal);
    return () => globalThis.removeEventListener(SYSTEM_MODE_SIGNAL_EVENT, handleSignal);
  }, [refresh]);

  const mode = status?.mode ?? 'NORMAL';
  const publicMessage = useMemo(() => selectPublicMessage(status, locale), [locale, status]);
  const value = useMemo<SystemModeContextValue>(() => ({
    status,
    mode,
    loading,
    refreshing,
    publicMessage,
    canWrite: mode === 'NORMAL',
    refresh
  }), [loading, mode, publicMessage, refresh, refreshing, status]);

  return <SystemModeContext.Provider value={value}>{children}</SystemModeContext.Provider>;
}

export function useSystemMode(): SystemModeContextValue {
  const context = useContext(SystemModeContext);
  if (!context) throw new Error('useSystemMode must be used inside SystemModeProvider');
  return context;
}

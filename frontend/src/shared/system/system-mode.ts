import type { ApiProblem } from '@/shared/api/transport';
import type { PublicSystemStatus, SystemMode } from '@/shared/api/system-status-api';
import type { Locale } from '@/shared/i18n/locale-policy';

export const SYSTEM_READ_ONLY_CODE = 'SYSTEM_READ_ONLY';
export const SYSTEM_MAINTENANCE_CODE = 'SYSTEM_MAINTENANCE';

export function modeFromProblem(problem?: ApiProblem): Exclude<SystemMode, 'NORMAL'> | null {
  if (problem?.code === SYSTEM_READ_ONLY_CODE) return 'READ_ONLY';
  if (problem?.code === SYSTEM_MAINTENANCE_CODE) return 'MAINTENANCE';
  return null;
}

export function inferStatusFromProblem(
  problem: ApiProblem,
  current: PublicSystemStatus | null,
  observedAt = new Date().toISOString()
): PublicSystemStatus | null {
  const mode = modeFromProblem(problem);
  if (!mode) return null;
  if (current?.mode === mode) return current;

  return {
    mode,
    messageVi: null,
    messageEn: null,
    estimatedEndAt: null,
    updatedAt: observedAt
  };
}

export type SystemStatusEvent =
  | { type: 'status-loaded'; status: PublicSystemStatus }
  | { type: 'status-failed' }
  | { type: 'api-problem'; problem: ApiProblem; observedAt?: string };

export function reconcileSystemStatus(
  current: PublicSystemStatus | null,
  event: SystemStatusEvent
): PublicSystemStatus | null {
  if (event.type === 'status-loaded') return event.status;
  if (event.type === 'status-failed') return current;
  return inferStatusFromProblem(event.problem, current, event.observedAt) ?? current;
}

export function localizedSystemMessage(status: PublicSystemStatus, locale: Locale): string | null {
  const message = locale === 'vi' ? status.messageVi : status.messageEn;
  return message && message.trim() ? message : null;
}

export function systemWritesBlocked(mode: SystemMode): boolean {
  return mode !== 'NORMAL';
}

import type { ApiProblem } from '@/shared/api/transport';
import type { SystemMode } from '@/shared/api/system-status-api';

export const SYSTEM_MODE_SIGNAL_EVENT = 'vote-system-mode-signal';

export interface SystemModeSignal {
  mode: Exclude<SystemMode, 'NORMAL'>;
  code: 'SYSTEM_READ_ONLY' | 'SYSTEM_MAINTENANCE';
}

export function systemModeSignalFromProblem(problem?: ApiProblem): SystemModeSignal | null {
  if (problem?.code === 'SYSTEM_READ_ONLY') {
    return { mode: 'READ_ONLY', code: 'SYSTEM_READ_ONLY' };
  }
  if (problem?.code === 'SYSTEM_MAINTENANCE') {
    return { mode: 'MAINTENANCE', code: 'SYSTEM_MAINTENANCE' };
  }
  return null;
}

export function emitSystemModeSignal(problem?: ApiProblem): void {
  const detail = systemModeSignalFromProblem(problem);
  if (!detail || typeof globalThis.dispatchEvent !== 'function' || typeof CustomEvent !== 'function') return;
  globalThis.dispatchEvent(new CustomEvent<SystemModeSignal>(SYSTEM_MODE_SIGNAL_EVENT, { detail }));
}

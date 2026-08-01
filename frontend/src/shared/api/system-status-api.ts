import { http, type ApiRequester } from './transport';

export type SystemMode = 'NORMAL' | 'READ_ONLY' | 'MAINTENANCE';

export interface PublicSystemStatus {
  mode: SystemMode;
  messageVi?: string | null;
  messageEn?: string | null;
  estimatedEndAt?: string | null;
  updatedAt: string;
}

export function createSystemStatusApi(request: ApiRequester = http.request) {
  return {
    status: (signal?: AbortSignal) =>
      request<PublicSystemStatus>('/api/v1/system/status', { signal })
  };
}

export const systemStatusApi = createSystemStatusApi();

import { http, type ApiRequester } from './transport';

export type SystemMode = 'NORMAL' | 'READ_ONLY' | 'MAINTENANCE';

export interface PublicSystemStatus {
  mode: SystemMode;
  messageVi?: string;
  messageEn?: string;
  estimatedEndAt?: string;
  updatedAt: string;
}

export function createSystemStatusApi(request: ApiRequester = http.request) {
  return {
    get: (signal?: AbortSignal) =>
      request<PublicSystemStatus>('/api/v1/system/status', { signal })
  };
}

export const systemStatusApi = createSystemStatusApi();

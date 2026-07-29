import type { AuthBootstrap } from './types';
import { http, type ApiRequester } from './transport';

export function createAuthApi(request: ApiRequester = http.request) {
  let refreshPromise: Promise<AuthBootstrap> | null = null;

  return {
    register(email: string, password: string, displayName?: string) {
      const normalizedDisplayName = displayName?.trim();
      return request<AuthBootstrap>('/api/v1/auth/register', {
        method: 'POST',
        body: JSON.stringify({
          email,
          password,
          ...(normalizedDisplayName ? { displayName: normalizedDisplayName } : {})
        })
      });
    },

    login(email: string, password: string) {
      return request<AuthBootstrap>('/api/v1/auth/login', {
        method: 'POST',
        body: JSON.stringify({ email, password })
      });
    },

    refresh(): Promise<AuthBootstrap> {
      if (!refreshPromise) {
        refreshPromise = request<AuthBootstrap>('/api/v1/auth/refresh', { method: 'POST' })
          .finally(() => { refreshPromise = null; });
      }
      return refreshPromise;
    },

    logout(token: string) {
      return request<void>('/api/v1/auth/logout', { method: 'POST' }, token);
    },

    logoutAll(token: string) {
      return request<void>('/api/v1/auth/logout-all', { method: 'POST' }, token);
    }
  };
}

export const authApi = createAuthApi();

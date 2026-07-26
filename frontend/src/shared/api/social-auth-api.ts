import { http, type ApiRequester } from './transport';
import type { SocialProvider } from './types';
import type { AuthIntent } from '@/shared/auth/auth-intent';

export type SocialProviderId = Lowercase<SocialProvider>;

interface SocialStartResponse {
  authorizationUrl: string;
}

interface SocialProvidersResponse {
  providers: SocialProviderId[];
}

export function createSocialAuthApi(request: ApiRequester = http.request) {
  return {
    providers() {
      return request<SocialProvidersResponse>('/api/v1/auth/social/providers');
    },

    start(provider: SocialProviderId, intent: AuthIntent) {
      return request<SocialStartResponse>(`/api/v1/auth/social/${provider}/start`, {
        method: 'POST',
        body: JSON.stringify({ intent })
      });
    },

    startLink(provider: SocialProviderId, token: string) {
      return request<SocialStartResponse>(`/api/v1/auth/social/${provider}/link/start`, {
        method: 'POST'
      }, token);
    }
  };
}

export const socialAuthApi = createSocialAuthApi();

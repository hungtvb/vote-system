import type { PublicUserProfile, UpdateUserProfileRequest, UserProfile } from './types';
import { http, type ApiRequester } from './transport';

export function createUserApi(request: ApiRequester = http.request) {
  return {
    current(token: string) {
      return request<UserProfile>('/api/v1/users/me', {}, token);
    },
    updateCurrent(payload: UpdateUserProfileRequest, token: string) {
      return request<UserProfile>('/api/v1/users/me', {
        method: 'PATCH',
        body: JSON.stringify(payload)
      }, token);
    },
    publicProfile(userId: string) {
      return request<PublicUserProfile>(`/api/v1/users/${encodeURIComponent(userId)}`);
    }
  };
}

export const userApi = createUserApi();
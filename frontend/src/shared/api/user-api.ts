import type { UserProfile } from './types';
import { http, type ApiRequester } from './transport';

export function createUserApi(request: ApiRequester = http.request) {
  return {
    current(token: string) {
      return request<UserProfile>('/api/v1/users/me', {}, token);
    }
  };
}

export const userApi = createUserApi();

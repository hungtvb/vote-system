import assert from 'node:assert/strict';
import test from 'node:test';

import { createSocialAuthApi } from '../social-auth-api';

test('provider discovery is public and returns backend-owned availability', async () => {
  const calls: Array<{ path: string; token?: string }> = [];
  const api = createSocialAuthApi(async <T>(path: string, _options?: RequestInit, token?: string) => {
    calls.push({ path, token });
    return { providers: ['google', 'github'] } as T;
  });

  const response = await api.providers();
  assert.deepEqual(response.providers, ['google', 'github']);
  assert.deepEqual(calls[0], {
    path: '/api/v1/auth/social/providers',
    token: undefined
  });
});

test('social sign-in sends only provider path and allowlisted intent', async () => {
  const calls: Array<{ path: string; options?: RequestInit; token?: string }> = [];
  const api = createSocialAuthApi(async <T>(path: string, options?: RequestInit, token?: string) => {
    calls.push({ path, options, token });
    return { authorizationUrl: 'https://api.test/oauth2/authorization/google' } as T;
  });

  await api.start('google', 'create-ballot');
  assert.equal(calls[0]?.path, '/api/v1/auth/social/google/start');
  assert.equal(calls[0]?.options?.method, 'POST');
  assert.deepEqual(JSON.parse(String(calls[0]?.options?.body)), { intent: 'create-ballot' });
  assert.equal(calls[0]?.token, undefined);
});

test('provider linking requires the current bearer token', async () => {
  const calls: Array<{ path: string; token?: string }> = [];
  const api = createSocialAuthApi(async <T>(path: string, _options?: RequestInit, token?: string) => {
    calls.push({ path, token });
    return { authorizationUrl: 'https://api.test/oauth2/authorization/github' } as T;
  });

  await api.startLink('github', 'access-token');
  assert.deepEqual(calls[0], {
    path: '/api/v1/auth/social/github/link/start',
    token: 'access-token'
  });
});

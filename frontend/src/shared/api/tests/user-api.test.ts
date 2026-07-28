import assert from 'node:assert/strict';
import test from 'node:test';

import { createUserApi } from '../user-api';
import type { UpdateUserProfileRequest, UserProfile } from '../types';

const PROFILE: UserProfile = {
  id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
  email: 'voter@example.com',
  displayName: 'Alex Voter',
  initials: 'AV',
  bio: 'Public-interest software builder.',
  avatarIcon: 'BUILDER',
  avatarColor: 'INK_BLUE',
  preferredLocale: 'vi',
  role: 'USER',
  linkedProviders: ['GOOGLE'],
  createdAt: '2026-07-20T09:00:00Z',
  updatedAt: '2026-07-28T03:00:00Z'
};

test('current profile uses the authenticated private endpoint', async () => {
  const calls: Array<{ path: string; options?: RequestInit; token?: string }> = [];
  const api = createUserApi(async <T>(path: string, options?: RequestInit, token?: string) => {
    calls.push({ path, options, token });
    return PROFILE as T;
  });

  const response = await api.current('access-token');

  assert.equal(response.email, 'voter@example.com');
  assert.deepEqual(calls[0], {
    path: '/api/v1/users/me',
    options: {},
    token: 'access-token'
  });
});

test('profile update sends the complete locked Ballot Mark contract', async () => {
  const calls: Array<{ path: string; options?: RequestInit; token?: string }> = [];
  const api = createUserApi(async <T>(path: string, options?: RequestInit, token?: string) => {
    calls.push({ path, options, token });
    return PROFILE as T;
  });
  const payload: UpdateUserProfileRequest = {
    displayName: 'Alex Voter',
    bio: 'Public-interest software builder.',
    avatarIcon: 'BUILDER',
    avatarColor: 'INK_BLUE',
    preferredLocale: 'vi'
  };

  await api.updateCurrent(payload, 'access-token');

  assert.equal(calls[0]?.path, '/api/v1/users/me');
  assert.equal(calls[0]?.options?.method, 'PATCH');
  assert.deepEqual(JSON.parse(String(calls[0]?.options?.body)), payload);
  assert.equal(calls[0]?.token, 'access-token');
});

test('public profile request encodes the user id and sends no bearer token', async () => {
  const calls: Array<{ path: string; token?: string }> = [];
  const api = createUserApi(async <T>(path: string, _options?: RequestInit, token?: string) => {
    calls.push({ path, token });
    return {
      id: PROFILE.id,
      displayName: PROFILE.displayName,
      initials: PROFILE.initials,
      bio: PROFILE.bio,
      avatarIcon: PROFILE.avatarIcon,
      avatarColor: PROFILE.avatarColor,
      createdAt: PROFILE.createdAt
    } as T;
  });

  await api.publicProfile('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');

  assert.deepEqual(calls[0], {
    path: '/api/v1/users/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    token: undefined
  });
});

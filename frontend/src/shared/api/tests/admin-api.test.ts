import assert from 'node:assert/strict';
import test from 'node:test';

import { createAdminApi } from '../admin-api';
import type { ApiRequester } from '../transport';

interface CapturedRequest {
  path: string;
  options?: RequestInit;
  token?: string;
}

function captureRequester(response: unknown = {}): { request: ApiRequester; calls: CapturedRequest[] } {
  const calls: CapturedRequest[] = [];
  return {
    calls,
    request: async <T>(path: string, options?: RequestInit, token?: string) => {
      calls.push({ path, options, token });
      return response as T;
    }
  };
}

test('admin user search serializes only bounded supplied filters', async () => {
  const fixture = captureRequester({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true });
  const api = createAdminApi(fixture.request);

  await api.users({
    query: 'name + email',
    role: 'ADMIN',
    accountStatus: 'ACTIVE',
    page: 2,
    size: 12
  }, 'admin-token');

  assert.equal(fixture.calls.length, 1);
  assert.equal(
    fixture.calls[0].path,
    '/api/v1/admin/users?query=name+%2B+email&role=ADMIN&accountStatus=ACTIVE&page=2&size=12'
  );
  assert.equal(fixture.calls[0].token, 'admin-token');
  assert.equal(fixture.calls[0].options?.method, undefined);
});

test('admin ballot search omits empty filters and preserves moderation state', async () => {
  const fixture = captureRequester({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true });
  const api = createAdminApi(fixture.request);

  await api.posts({ query: '', category: undefined, moderationStatus: 'HIDDEN', page: 0, size: 12 }, 'admin-token');

  assert.equal(fixture.calls[0].path, '/api/v1/admin/posts?moderationStatus=HIDDEN&page=0&size=12');
});

test('suspension sends a required reason and optional expiry as JSON', async () => {
  const fixture = captureRequester({ id: 'user-id', accountStatus: 'SUSPENDED', statusUpdatedAt: '2026-07-30T00:00:00Z', revokedSessions: 1 });
  const api = createAdminApi(fixture.request);

  await api.suspendUser('user/id', 'Repeated abuse after warning', '2026-08-01T12:00:00.000Z', 'admin-token');

  assert.equal(fixture.calls[0].path, '/api/v1/admin/users/user%2Fid/suspend');
  assert.equal(fixture.calls[0].options?.method, 'POST');
  assert.deepEqual(JSON.parse(String(fixture.calls[0].options?.body)), {
    reason: 'Repeated abuse after warning',
    until: '2026-08-01T12:00:00.000Z'
  });
  assert.equal(fixture.calls[0].token, 'admin-token');
});

test('ballot soft delete uses the audited administrator mutation endpoint', async () => {
  const fixture = captureRequester({ id: 'post-id', moderationStatus: 'DELETED', moderationUpdatedAt: '2026-07-30T00:00:00Z' });
  const api = createAdminApi(fixture.request);

  await api.deletePost('post-id', 'Confirmed policy violation', 'admin-token');

  assert.equal(fixture.calls[0].path, '/api/v1/admin/posts/post-id/delete');
  assert.deepEqual(JSON.parse(String(fixture.calls[0].options?.body)), {
    reason: 'Confirmed policy violation'
  });
});

test('administrator system status reads the protected contract', async () => {
  const fixture = captureRequester({ mode: 'NORMAL', updatedAt: '2026-07-31T00:00:00Z' });
  const api = createAdminApi(fixture.request);

  await api.systemStatus('admin-token');

  assert.equal(fixture.calls[0].path, '/api/v1/admin/system/status');
  assert.equal(fixture.calls[0].options?.method, undefined);
  assert.equal(fixture.calls[0].token, 'admin-token');
});

test('administrator system status update uses PUT and preserves nullable localized fields', async () => {
  const fixture = captureRequester({ mode: 'MAINTENANCE', updatedAt: '2026-07-31T00:00:00Z' });
  const api = createAdminApi(fixture.request);

  await api.updateSystemStatus({
    mode: 'MAINTENANCE',
    messageVi: 'Hệ thống đang bảo trì',
    messageEn: 'The system is under maintenance',
    estimatedEndAt: '2026-07-31T04:00:00Z',
    reason: 'Planned maintenance'
  }, 'admin-token');

  assert.equal(fixture.calls[0].path, '/api/v1/admin/system/status');
  assert.equal(fixture.calls[0].options?.method, 'PUT');
  assert.deepEqual(JSON.parse(String(fixture.calls[0].options?.body)), {
    mode: 'MAINTENANCE',
    messageVi: 'Hệ thống đang bảo trì',
    messageEn: 'The system is under maintenance',
    estimatedEndAt: '2026-07-31T04:00:00Z',
    reason: 'Planned maintenance'
  });
  assert.equal(fixture.calls[0].token, 'admin-token');
});

import assert from 'node:assert/strict';
import test from 'node:test';

import { createAuthApi } from '../auth-api';
import { runAuthorizedRequest } from '../authorized-request';
import type { AuthBootstrap, Session, UserProfile } from '../types';
import { ApiError, createHttpClient, parseRetryAfter, subscribeApiProblems, type ApiRequester } from '../transport';

const OLD_SESSION: Session = {
  tokenType: 'Bearer',
  accessToken: 'old-token',
  expiresInSeconds: 900,
  userId: 'user-1',
  email: 'voter@example.com',
  role: 'USER'
};

const PROFILE: UserProfile = {
  id: OLD_SESSION.userId,
  email: OLD_SESSION.email,
  displayName: 'Registry Voter',
  initials: 'RV',
  bio: null,
  avatarIcon: 'CITIZEN',
  avatarColor: 'NAVY',
  preferredLocale: 'vi',
  role: OLD_SESSION.role,
  linkedProviders: [],
  createdAt: '2026-07-20T09:00:00Z',
  updatedAt: '2026-07-29T01:00:00Z'
};

const NEW_BOOTSTRAP: AuthBootstrap = {
  ...OLD_SESSION,
  accessToken: 'new-token',
  refreshExpiresInSeconds: 2_592_000,
  profile: PROFILE
};

test('concurrent refresh calls share one in-flight bootstrap request', async () => {
  let refreshCalls = 0;
  let resolveRefresh!: (session: AuthBootstrap) => void;
  const refreshGate = new Promise<AuthBootstrap>(resolve => { resolveRefresh = resolve; });
  const request: ApiRequester = <T>() => {
    refreshCalls += 1;
    return refreshGate as Promise<T>;
  };
  const api = createAuthApi(request);

  const first = api.refresh();
  const second = api.refresh();

  assert.strictEqual(first, second);
  assert.equal(refreshCalls, 1);
  resolveRefresh(NEW_BOOTSTRAP);
  assert.deepEqual(await Promise.all([first, second]), [NEW_BOOTSTRAP, NEW_BOOTSTRAP]);
});

test('concurrent 401 operations trigger one bootstrap refresh and retry once with the new token', async () => {
  let refreshCalls = 0;
  let resolveRefresh!: (session: AuthBootstrap) => void;
  const refreshGate = new Promise<AuthBootstrap>(resolve => { resolveRefresh = resolve; });
  const auth = createAuthApi(<T>(path: string) => {
    assert.equal(path, '/api/v1/auth/refresh');
    refreshCalls += 1;
    return refreshGate as Promise<T>;
  });

  let current: Session | null = OLD_SESSION;
  let clearCalls = 0;
  const attempts = new Map<string, number>();
  const operation = async (session: Session) => {
    attempts.set(session.accessToken, (attempts.get(session.accessToken) ?? 0) + 1);
    if (session.accessToken === OLD_SESSION.accessToken) throw new ApiError('Expired', 401);
    return session.accessToken;
  };
  const context = {
    getSession: () => current,
    refresh: auth.refresh,
    setSession: (session: Session) => { current = session; },
    clearSession: () => { current = null; clearCalls += 1; }
  };

  const first = runAuthorizedRequest(operation, context);
  const second = runAuthorizedRequest(operation, context);
  await new Promise(resolve => setImmediate(resolve));

  assert.equal(refreshCalls, 1);
  resolveRefresh(NEW_BOOTSTRAP);
  assert.deepEqual(await Promise.all([first, second]), ['new-token', 'new-token']);
  assert.equal(attempts.get('old-token'), 2);
  assert.equal(attempts.get('new-token'), 2);
  assert.equal(clearCalls, 0);
  assert.equal(current?.accessToken, 'new-token');
});

test('failed refresh clears the session', async () => {
  let current: Session | null = OLD_SESSION;
  let clearCalls = 0;

  await assert.rejects(
    runAuthorizedRequest(
      async () => { throw new ApiError('Expired', 401); },
      {
        getSession: () => current,
        refresh: async () => { throw new ApiError('Refresh rejected', 401); },
        setSession: session => { current = session; },
        clearSession: () => { current = null; clearCalls += 1; }
      }
    ),
    (error: unknown) => error instanceof ApiError && error.message === 'Refresh rejected'
  );

  assert.equal(clearCalls, 1);
  assert.equal(current, null);
});

test('a retried 401 is not refreshed again and clears the session', async () => {
  let current: Session | null = OLD_SESSION;
  let refreshCalls = 0;
  let operationCalls = 0;
  let clearCalls = 0;

  await assert.rejects(
    runAuthorizedRequest(
      async () => { operationCalls += 1; throw new ApiError('Still expired', 401); },
      {
        getSession: () => current,
        refresh: async () => { refreshCalls += 1; return NEW_BOOTSTRAP; },
        setSession: session => { current = session; },
        clearSession: () => { current = null; clearCalls += 1; }
      }
    ),
    ApiError
  );

  assert.equal(operationCalls, 2);
  assert.equal(refreshCalls, 1);
  assert.equal(clearCalls, 1);
});

test('transport includes cookies and normalizes problem responses', async () => {
  let capturedUrl = '';
  let capturedInit: RequestInit | undefined;
  const { request } = createHttpClient({
    baseUrl: 'https://api.example.test',
    fetchImpl: async (input, init) => {
      capturedUrl = String(input);
      capturedInit = init;
      return new Response(JSON.stringify({ title: 'Too Many Requests', detail: 'Slow down' }), {
        status: 429,
        headers: { 'Content-Type': 'application/problem+json', 'Retry-After': '3' }
      });
    }
  });

  await assert.rejects(
    request('/api/v1/posts', {}, 'access-token'),
    (error: unknown) => {
      assert.ok(error instanceof ApiError);
      assert.equal(error.status, 429);
      assert.equal(error.retryAfter, 3);
      assert.equal(error.message, 'Slow down');
      return true;
    }
  );

  assert.equal(capturedUrl, 'https://api.example.test/api/v1/posts');
  assert.equal(capturedInit?.credentials, 'include');
  assert.equal(new Headers(capturedInit?.headers).get('Authorization'), 'Bearer access-token');
});

test('transport publishes stable problem codes for global system-mode reconciliation', async () => {
  const observed: Array<{ code?: string; mode?: string }> = [];
  const unsubscribe = subscribeApiProblems(problem => observed.push({ code: problem.code, mode: problem.mode }));
  const { request } = createHttpClient({
    fetchImpl: async () => new Response(JSON.stringify({
      title: 'Service under maintenance',
      status: 503,
      code: 'SYSTEM_MAINTENANCE',
      mode: 'MAINTENANCE'
    }), {
      status: 503,
      headers: { 'Content-Type': 'application/problem+json' }
    })
  });

  try {
    await assert.rejects(request('/api/v1/posts'), ApiError);
  } finally {
    unsubscribe();
  }

  assert.deepEqual(observed, [{ code: 'SYSTEM_MAINTENANCE', mode: 'MAINTENANCE' }]);
});


test('a failing problem observer cannot replace the transport ApiError', async () => {
  const unsubscribe = subscribeApiProblems(() => { throw new Error('observer failed'); });
  const { request } = createHttpClient({
    fetchImpl: async () => new Response(JSON.stringify({ detail: 'Authoritative failure', code: 'SYSTEM_READ_ONLY' }), {
      status: 503,
      headers: { 'Content-Type': 'application/problem+json' }
    })
  });

  try {
    await assert.rejects(
      request('/api/v1/posts'),
      (error: unknown) => error instanceof ApiError && error.message === 'Authoritative failure'
    );
  } finally {
    unsubscribe();
  }
});

test('Retry-After supports both seconds and HTTP dates', () => {
  const now = Date.parse('2026-07-25T08:00:00Z');
  assert.equal(parseRetryAfter('7', now), 7);
  assert.equal(parseRetryAfter('Sat, 25 Jul 2026 08:00:05 GMT', now), 5);
  assert.equal(parseRetryAfter('invalid', now), undefined);
});

test('logout calls the backend with credentials and the access token', async () => {
  const calls: Array<{ path: string; method?: string; token?: string }> = [];
  const api = createAuthApi(async <T>(path: string, options?: RequestInit, token?: string) => {
    calls.push({ path, method: options?.method, token });
    return undefined as T;
  });

  await api.logout('logout-token');
  await api.logoutAll('logout-all-token');

  assert.deepEqual(calls, [
    { path: '/api/v1/auth/logout', method: 'POST', token: 'logout-token' },
    { path: '/api/v1/auth/logout-all', method: 'POST', token: 'logout-all-token' }
  ]);
});

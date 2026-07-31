import assert from 'node:assert/strict';
import test from 'node:test';

import { createSystemStatusApi } from '../system-status-api';
import type { ApiRequester } from '../transport';
import { systemModeSignalFromProblem } from '../../system/system-mode-signal';
import { selectPublicMessage } from '../../system/SystemModeProvider';

interface CapturedRequest {
  path: string;
  options?: RequestInit;
  token?: string;
}

function captureRequester(response: unknown): { request: ApiRequester; calls: CapturedRequest[] } {
  const calls: CapturedRequest[] = [];
  return {
    calls,
    request: async <T>(path: string, options?: RequestInit, token?: string) => {
      calls.push({ path, options, token });
      return response as T;
    }
  };
}

test('public system status uses the anonymous authoritative endpoint', async () => {
  const fixture = captureRequester({ mode: 'NORMAL', updatedAt: '2026-07-31T00:00:00Z' });
  const api = createSystemStatusApi(fixture.request);
  const controller = new AbortController();

  const status = await api.get(controller.signal);

  assert.equal(status.mode, 'NORMAL');
  assert.equal(fixture.calls.length, 1);
  assert.equal(fixture.calls[0].path, '/api/v1/system/status');
  assert.equal(fixture.calls[0].token, undefined);
  assert.equal(fixture.calls[0].options?.signal, controller.signal);
});

test('stable backend problem codes map only to read-only and maintenance signals', () => {
  assert.deepEqual(systemModeSignalFromProblem({ code: 'SYSTEM_READ_ONLY' }), {
    mode: 'READ_ONLY',
    code: 'SYSTEM_READ_ONLY'
  });
  assert.deepEqual(systemModeSignalFromProblem({ code: 'SYSTEM_MAINTENANCE' }), {
    mode: 'MAINTENANCE',
    code: 'SYSTEM_MAINTENANCE'
  });
  assert.equal(systemModeSignalFromProblem({ code: 'SYSTEM_STATUS_UNAVAILABLE' }), null);
  assert.equal(systemModeSignalFromProblem({ code: 'OTHER_FAILURE' }), null);
});

test('public message preserves authored text and falls back across locales', () => {
  const status = {
    mode: 'MAINTENANCE' as const,
    messageVi: 'Hệ thống đang bảo trì — giữ nguyên nội dung.',
    messageEn: 'The registry is under maintenance.',
    updatedAt: '2026-07-31T00:00:00Z'
  };

  assert.equal(selectPublicMessage(status, 'vi'), status.messageVi);
  assert.equal(selectPublicMessage(status, 'en'), status.messageEn);
  assert.equal(selectPublicMessage({ ...status, messageEn: undefined }, 'en'), status.messageVi);
  assert.equal(selectPublicMessage({ ...status, messageVi: undefined, messageEn: undefined }, 'vi'), '');
});

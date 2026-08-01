import assert from 'node:assert/strict';
import test from 'node:test';

import { createSystemStatusApi } from '../system-status-api';
import type { ApiRequester } from '../transport';

test('public system status API uses the anonymous recovery endpoint', async () => {
  const calls: Array<{ path: string; options?: RequestInit; token?: string }> = [];
  const request: ApiRequester = async <T>(path: string, options?: RequestInit, token?: string) => {
    calls.push({ path, options, token });
    return {
      mode: 'READ_ONLY',
      messageVi: 'Chỉ đọc',
      messageEn: 'Read only',
      estimatedEndAt: null,
      updatedAt: '2026-08-01T00:00:00Z'
    } as T;
  };

  const controller = new AbortController();
  const status = await createSystemStatusApi(request).status(controller.signal);

  assert.equal(status.mode, 'READ_ONLY');
  assert.equal(calls.length, 1);
  assert.equal(calls[0].path, '/api/v1/system/status');
  assert.equal(calls[0].options?.signal, controller.signal);
  assert.equal(calls[0].token, undefined);
});

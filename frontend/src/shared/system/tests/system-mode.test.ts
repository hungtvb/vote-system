import assert from 'node:assert/strict';
import test from 'node:test';

import type { PublicSystemStatus } from '../../api/system-status-api';
import {
  inferStatusFromProblem,
  localizedSystemMessage,
  modeFromProblem,
  reconcileSystemStatus,
  systemWritesBlocked
} from '../system-mode';

const STATUS: PublicSystemStatus = {
  mode: 'READ_ONLY',
  messageVi: '  Thông báo giữ nguyên khoảng trắng  ',
  messageEn: 'Read-only notice',
  estimatedEndAt: '2026-08-01T12:00:00Z',
  updatedAt: '2026-08-01T11:00:00Z'
};

test('stable backend problem codes map to system modes', () => {
  assert.equal(modeFromProblem({ code: 'SYSTEM_READ_ONLY' }), 'READ_ONLY');
  assert.equal(modeFromProblem({ code: 'SYSTEM_MAINTENANCE' }), 'MAINTENANCE');
  assert.equal(modeFromProblem({ code: 'OTHER' }), null);
});

test('problem inference never reuses stale administrator-authored copy across modes', () => {
  const inferred = inferStatusFromProblem(
    { code: 'SYSTEM_MAINTENANCE' },
    STATUS,
    '2026-08-01T11:30:00Z'
  );

  assert.deepEqual(inferred, {
    mode: 'MAINTENANCE',
    messageVi: null,
    messageEn: null,
    estimatedEndAt: null,
    updatedAt: '2026-08-01T11:30:00Z'
  });
});

test('localized administrator messages are displayed unmodified', () => {
  assert.equal(localizedSystemMessage(STATUS, 'vi'), STATUS.messageVi);
  assert.equal(localizedSystemMessage(STATUS, 'en'), STATUS.messageEn);
  assert.equal(localizedSystemMessage({ ...STATUS, messageEn: '   ' }, 'en'), null);
});

test('all non-normal modes block public write entry points', () => {
  assert.equal(systemWritesBlocked('NORMAL'), false);
  assert.equal(systemWritesBlocked('READ_ONLY'), true);
  assert.equal(systemWritesBlocked('MAINTENANCE'), true);
});


test('status request failure fails open without inventing maintenance', () => {
  assert.equal(reconcileSystemStatus(null, { type: 'status-failed' }), null);
  assert.equal(reconcileSystemStatus(STATUS, { type: 'status-failed' }), STATUS);
});

test('authoritative normal status clears an inferred maintenance state', () => {
  const inferred = reconcileSystemStatus(null, {
    type: 'api-problem',
    problem: { code: 'SYSTEM_MAINTENANCE' },
    observedAt: '2026-08-01T11:30:00Z'
  });
  const normal: PublicSystemStatus = {
    mode: 'NORMAL',
    messageVi: null,
    messageEn: null,
    estimatedEndAt: null,
    updatedAt: '2026-08-01T11:31:00Z'
  };

  assert.equal(inferred?.mode, 'MAINTENANCE');
  assert.deepEqual(reconcileSystemStatus(inferred, { type: 'status-loaded', status: normal }), normal);
});

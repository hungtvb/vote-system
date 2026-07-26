import assert from 'node:assert/strict';
import test from 'node:test';

import { beginAuth, cancelAuth, completeAuth } from '../auth-intent';

test('direct registration completes without opening ballot creation', () => {
  const workflow = beginAuth('register');
  const result = completeAuth(workflow);
  assert.equal(result.resumeCreateBallot, false);
  assert.equal(result.workflow.open, false);
});

test('guest create ballot resumes the creation form after authentication', () => {
  const workflow = beginAuth('login', 'create-ballot');
  const result = completeAuth(workflow);
  assert.equal(result.resumeCreateBallot, true);
  assert.equal(result.workflow.open, false);
});

test('cancelling authentication discards the pending ballot intent', () => {
  const workflow = beginAuth('register', 'create-ballot');
  const cancelled = cancelAuth();
  assert.equal(cancelled.open, false);
  assert.equal(cancelled.intent, 'authenticate');
  assert.notEqual(cancelled, workflow);
});

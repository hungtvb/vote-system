import assert from 'node:assert/strict';
import test from 'node:test';
import { resolveFeedRequestAccess } from '../feed-request';

test('public feeds load immediately while session restoration is running', () => {
  assert.equal(resolveFeedRequestAccess({
    feed: 'LATEST',
    restoring: true,
    authenticated: false
  }), 'public');
});

test('public feeds use authentication after the session is available', () => {
  assert.equal(resolveFeedRequestAccess({
    feed: 'HOT',
    restoring: false,
    authenticated: true
  }), 'authenticated');
});

test('MINE waits for restoration and requires a session', () => {
  assert.equal(resolveFeedRequestAccess({
    feed: 'MINE',
    restoring: true,
    authenticated: true
  }), 'skip');
  assert.equal(resolveFeedRequestAccess({
    feed: 'MINE',
    restoring: false,
    authenticated: false
  }), 'skip');
  assert.equal(resolveFeedRequestAccess({
    feed: 'MINE',
    restoring: false,
    authenticated: true
  }), 'authenticated');
});

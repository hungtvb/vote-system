import assert from 'node:assert/strict';
import test from 'node:test';

import { buildBallotListPath, createBallotApi } from '../ballot-api';

test('ballot list path serializes feed, search and filters for the backend', () => {
  const path = buildBallotListPath({
    feed: 'MINE',
    page: 2,
    size: 8,
    query: '  architecture vote  ',
    category: ' TECHNOLOGY ',
    status: 'OPEN'
  });
  const url = new URL(path, 'https://vote.test');
  assert.equal(url.pathname, '/api/v1/posts');
  assert.equal(url.searchParams.get('feed'), 'MINE');
  assert.equal(url.searchParams.get('page'), '2');
  assert.equal(url.searchParams.get('size'), '8');
  assert.equal(url.searchParams.get('query'), 'architecture vote');
  assert.equal(url.searchParams.get('category'), 'TECHNOLOGY');
  assert.equal(url.searchParams.get('status'), 'OPEN');
});

test('blank optional filters are omitted instead of sent as empty strings', () => {
  const path = buildBallotListPath({ feed: 'LATEST', page: 0, size: 8, query: ' ', category: '' });
  const url = new URL(path, 'https://vote.test');
  assert.equal(url.searchParams.has('query'), false);
  assert.equal(url.searchParams.has('category'), false);
  assert.equal(url.searchParams.has('status'), false);
});

test('ballot list forwards the bearer token to the shared transport', async () => {
  const calls: Array<{ path: string; token?: string }> = [];
  const api = createBallotApi(async <T>(path: string, _options?: RequestInit, token?: string) => {
    calls.push({ path, token });
    return { content: [], totalElements: 0, totalPages: 0, number: 0, size: 8, first: true, last: true, empty: true } as T;
  });

  await api.list({ feed: 'MINE', page: 0, size: 8 }, 'access-token');
  assert.equal(calls[0]?.token, 'access-token');
  assert.match(calls[0]?.path ?? '', /feed=MINE/);
});

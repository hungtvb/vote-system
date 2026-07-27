import assert from 'node:assert/strict';
import test from 'node:test';
import { createBallotApi } from '../ballot-api';
import type { ApiRequester } from '../transport';
import type { Ballot, PageResponse } from '../types';

const emptyPage: PageResponse<Ballot> = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 8,
  first: true,
  last: true,
  empty: true
};

test('ballot list forwards its AbortSignal to the shared transport', async () => {
  const controller = new AbortController();
  let capturedSignal: AbortSignal | null | undefined;

  const request: ApiRequester = async <T>(_path: string, options?: RequestInit): Promise<T> => {
    capturedSignal = options?.signal;
    return emptyPage as T;
  };

  await createBallotApi(request).list({
    feed: 'LATEST',
    page: 0,
    size: 8
  }, undefined, controller.signal);

  assert.equal(capturedSignal, controller.signal);
});

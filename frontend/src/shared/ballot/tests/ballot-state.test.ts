import assert from 'node:assert/strict';
import test from 'node:test';

import type { Ballot, VoteResponse } from '@/shared/api/types';
import {
  applyOptimisticVote,
  applyStreamUpdate,
  applyVoteResponse,
  mergeUniqueBallots,
  reconcileAuthoritativeBallot,
  reconcileVoteResponse,
  rollbackVoteSnapshot
} from '../ballot-state';

function ballot(overrides: Partial<Ballot> = {}): Ballot {
  return {
    id: 'post-1',
    authorId: 'author-1',
    author: { id: 'author-1', displayName: 'Voter One', initials: 'VO' },
    ballotNumber: 'BAL-1',
    title: 'A ballot',
    content: 'Ballot content',
    category: 'GENERAL',
    status: 'OPEN',
    voteScore: 2,
    upVotes: 6,
    downVotes: 4,
    totalVotes: 10,
    verdictThreshold: 70,
    verdict: 'UNDECIDED',
    finalVerdict: false,
    createdAt: '2026-07-25T00:00:00Z',
    updatedAt: '2026-07-25T00:00:00Z',
    ...overrides
  };
}

function voteResponse(overrides: Partial<VoteResponse> = {}): VoteResponse {
  return {
    postId: 'post-1',
    voteScore: 9,
    upVotes: 10,
    downVotes: 1,
    totalVotes: 11,
    myVote: 'UP',
    verdictThreshold: 70,
    verdict: 'UP',
    ...overrides
  };
}

test('UP to UP removes the existing vote', () => {
  const next = applyOptimisticVote(ballot({ myVote: 'UP' }), 'UP');
  assert.equal(next.upVotes, 5);
  assert.equal(next.downVotes, 4);
  assert.equal(next.totalVotes, 9);
  assert.equal(next.voteScore, 1);
  assert.equal(next.myVote, undefined);
});

test('DOWN to DOWN removes the existing vote', () => {
  const next = applyOptimisticVote(ballot({ myVote: 'DOWN' }), 'DOWN');
  assert.equal(next.upVotes, 6);
  assert.equal(next.downVotes, 3);
  assert.equal(next.totalVotes, 9);
  assert.equal(next.voteScore, 3);
  assert.equal(next.myVote, undefined);
});

test('UP to DOWN moves one count between both options', () => {
  const next = applyOptimisticVote(ballot({ myVote: 'UP' }), 'DOWN');
  assert.equal(next.upVotes, 5);
  assert.equal(next.downVotes, 5);
  assert.equal(next.totalVotes, 10);
  assert.equal(next.voteScore, 0);
  assert.equal(next.myVote, 'DOWN');
});

test('DOWN to UP moves one count between both options', () => {
  const next = applyOptimisticVote(ballot({ myVote: 'DOWN' }), 'UP');
  assert.equal(next.upVotes, 7);
  assert.equal(next.downVotes, 3);
  assert.equal(next.totalVotes, 10);
  assert.equal(next.voteScore, 4);
  assert.equal(next.myVote, 'UP');
});

test('authoritative vote response reconciles optimistic values without replacing ballot identity', () => {
  const source = ballot();
  const next = applyVoteResponse(source, voteResponse());
  assert.equal(next.id, source.id);
  assert.equal(next.title, source.title);
  assert.equal(next.upVotes, 10);
  assert.equal(next.verdict, 'UP');
});

test('late vote response preserves newer stream counts and only reconciles personal vote', () => {
  const snapshot = ballot();
  const streamed = applyStreamUpdate(snapshot, {
    postId: snapshot.id,
    voteScore: 20,
    upVotes: 21,
    downVotes: 1,
    totalVotes: 22,
    verdictThreshold: 70,
    verdict: 'UP',
    updatedAt: '2026-07-25T00:00:02Z'
  });
  const reconciled = reconcileVoteResponse(streamed, voteResponse({ myVote: 'DOWN' }), snapshot.updatedAt);
  assert.equal(reconciled.upVotes, 21);
  assert.equal(reconciled.totalVotes, 22);
  assert.equal(reconciled.myVote, 'DOWN');
});

test('vote response replaces optimistic shared state when no newer stream arrived', () => {
  const snapshot = ballot();
  const optimistic = applyOptimisticVote(snapshot, 'UP');
  const reconciled = reconcileVoteResponse(optimistic, voteResponse(), snapshot.updatedAt);
  assert.equal(reconciled.upVotes, 10);
  assert.equal(reconciled.totalVotes, 11);
  assert.equal(reconciled.myVote, 'UP');
});

test('newer stream update replaces shared counts while preserving personal vote state', () => {
  const source = ballot({ myVote: 'DOWN' });
  const next = applyStreamUpdate(source, {
    postId: source.id,
    voteScore: 10,
    upVotes: 12,
    downVotes: 2,
    totalVotes: 14,
    verdictThreshold: 70,
    verdict: 'UP',
    updatedAt: '2026-07-25T00:00:01Z'
  });
  assert.equal(next.upVotes, 12);
  assert.equal(next.downVotes, 2);
  assert.equal(next.verdict, 'UP');
  assert.equal(next.myVote, 'DOWN');
});

test('equal, older, invalid, and different-ballot stream updates are ignored', () => {
  const source = ballot({ updatedAt: '2026-07-25T00:00:02Z' });
  const shared = {
    postId: source.id,
    voteScore: 99,
    upVotes: 99,
    downVotes: 0,
    totalVotes: 99,
    verdictThreshold: 70,
    verdict: 'UP' as const
  };
  assert.equal(applyStreamUpdate(source, { ...shared, updatedAt: source.updatedAt }), source);
  assert.equal(applyStreamUpdate(source, { ...shared, updatedAt: '2026-07-25T00:00:01Z' }), source);
  assert.equal(applyStreamUpdate(source, { ...shared, updatedAt: 'invalid' }), source);
  assert.equal(applyStreamUpdate(source, { ...shared, postId: 'post-2', updatedAt: '2026-07-25T00:00:03Z' }), source);
});

test('stale authoritative GET preserves newer stream counts and reconciles myVote', () => {
  const current = ballot({
    myVote: 'UP',
    upVotes: 20,
    totalVotes: 24,
    updatedAt: '2026-07-25T00:00:03Z'
  });
  const staleGet = ballot({ myVote: 'DOWN', updatedAt: '2026-07-25T00:00:02Z' });
  const reconciled = reconcileAuthoritativeBallot(current, staleGet);
  assert.equal(reconciled.upVotes, 20);
  assert.equal(reconciled.totalVotes, 24);
  assert.equal(reconciled.myVote, 'DOWN');
});

test('newer authoritative GET replaces the current ballot', () => {
  const current = ballot({ updatedAt: '2026-07-25T00:00:02Z' });
  const authoritative = ballot({ upVotes: 30, totalVotes: 34, updatedAt: '2026-07-25T00:00:03Z' });
  assert.equal(reconcileAuthoritativeBallot(current, authoritative), authoritative);
});

test('rollback does not overwrite a newer stream update', () => {
  const snapshot = ballot();
  const optimistic = applyOptimisticVote(snapshot, 'UP');
  assert.equal(rollbackVoteSnapshot(optimistic, snapshot), snapshot);

  const streamed = applyStreamUpdate(optimistic, {
    postId: snapshot.id,
    voteScore: 3,
    upVotes: 7,
    downVotes: 4,
    totalVotes: 11,
    verdictThreshold: 70,
    verdict: 'UNDECIDED',
    updatedAt: '2026-07-25T00:00:01Z'
  });
  assert.equal(rollbackVoteSnapshot(streamed, snapshot), streamed);
});

test('load-more merge drops duplicate ballot IDs', () => {
  const first = ballot();
  const second = ballot({ id: 'post-2', ballotNumber: 'BAL-2' });
  assert.deepEqual(mergeUniqueBallots([first], [first, second]).map(item => item.id), ['post-1', 'post-2']);
});

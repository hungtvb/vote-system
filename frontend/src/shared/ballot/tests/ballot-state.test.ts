import assert from 'node:assert/strict';
import test from 'node:test';

import type { Ballot } from '@/shared/api/types';
import { applyOptimisticVote, applyVoteResponse, mergeUniqueBallots } from '../ballot-state';

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
  const next = applyVoteResponse(source, {
    postId: source.id,
    voteScore: 9,
    upVotes: 10,
    downVotes: 1,
    totalVotes: 11,
    myVote: 'UP',
    verdictThreshold: 70,
    verdict: 'UP'
  });
  assert.equal(next.id, source.id);
  assert.equal(next.title, source.title);
  assert.equal(next.upVotes, 10);
  assert.equal(next.verdict, 'UP');
});

test('load-more merge drops duplicate ballot IDs', () => {
  const first = ballot();
  const second = ballot({ id: 'post-2', ballotNumber: 'BAL-2' });
  assert.deepEqual(mergeUniqueBallots([first], [first, second]).map(item => item.id), ['post-1', 'post-2']);
});

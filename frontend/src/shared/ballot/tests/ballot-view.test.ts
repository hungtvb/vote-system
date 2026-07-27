import assert from 'node:assert/strict';
import test from 'node:test';

import { calculateVoteBreakdown, formatVoteScore, getVerdictStamp, verdictTransitionKey } from '../ballot-view';

test('vote percentages cover zero, even split, and unanimous boundaries', () => {
  assert.deepEqual(calculateVoteBreakdown(0, 0), {
    upPercentage: 0,
    downPercentage: 0,
    totalVotes: 0
  });
  assert.deepEqual(calculateVoteBreakdown(50, 50), {
    upPercentage: 50,
    downPercentage: 50,
    totalVotes: 100
  });
  assert.deepEqual(calculateVoteBreakdown(100, 0), {
    upPercentage: 100,
    downPercentage: 0,
    totalVotes: 100
  });
});

test('vote percentages are rounded while always adding to one hundred', () => {
  const breakdown = calculateVoteBreakdown(2, 1);
  assert.equal(breakdown.upPercentage, 67);
  assert.equal(breakdown.downPercentage, 33);
  assert.equal(breakdown.upPercentage + breakdown.downPercentage, 100);
});

test('vote score formatting keeps the sign before a four-digit magnitude', () => {
  assert.equal(formatVoteScore(-2), '-0002');
  assert.equal(formatVoteScore(-1), '-0001');
  assert.equal(formatVoteScore(0), '+0000');
  assert.equal(formatVoteScore(2), '+0002');
  assert.equal(formatVoteScore(2.9), '+0002');
  assert.equal(formatVoteScore(Number.NaN), '+0000');
});

test('current undecided ballots have no stamp but closed undecided ballots retain a final record', () => {
  assert.equal(getVerdictStamp('UNDECIDED', false), null);
  assert.deepEqual(getVerdictStamp('UNDECIDED', true), {
    eyebrow: 'FINAL VERDICT',
    label: 'UNDECIDED',
    tone: 'neutral'
  });
});

test('current and final verdicts use distinct copy and stable transition keys', () => {
  assert.deepEqual(getVerdictStamp('UP', false), {
    eyebrow: 'CURRENT VERDICT',
    label: 'ENDORSED',
    tone: 'up'
  });
  assert.deepEqual(getVerdictStamp('DOWN', true), {
    eyebrow: 'FINAL VERDICT',
    label: 'REJECTED',
    tone: 'down'
  });
  assert.notEqual(verdictTransitionKey('UP', false), verdictTransitionKey('UP', true));
  assert.notEqual(verdictTransitionKey('UNDECIDED', false), verdictTransitionKey('UP', false));
});

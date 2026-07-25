'use client';

import { calculateVoteBreakdown } from '@/shared/ballot/ballot-view';
import type { Ballot, VoteType } from '@/shared/api/types';
import styles from './BallotCard.module.scss';

interface BallotOptionsProps {
  ballot: Ballot;
  busy: boolean;
  onVote: (type: VoteType) => void;
}

export function BallotOptions({ ballot, busy, onVote }: BallotOptionsProps) {
  const disabled = busy || ballot.status === 'CLOSED';
  const breakdown = calculateVoteBreakdown(ballot.upVotes, ballot.downVotes);

  return (
    <div className={styles.votePanel} data-qa-vote-split={`${breakdown.upPercentage}/${breakdown.downPercentage}`}>
      <span className={styles.voteLabel}>OFFICIAL BALLOT</span>
      <div className={styles.counter} aria-label={`Vote score ${ballot.voteScore}`}>
        {ballot.voteScore >= 0 ? '+' : ''}{ballot.voteScore.toString().padStart(4, '0')}
      </div>

      <VoteOption
        type="UP"
        label="ENDORSE"
        count={ballot.upVotes}
        percentage={breakdown.upPercentage}
        selected={ballot.myVote === 'UP'}
        disabled={disabled}
        onVote={onVote}
      />
      <VoteOption
        type="DOWN"
        label="REJECT"
        count={ballot.downVotes}
        percentage={breakdown.downPercentage}
        selected={ballot.myVote === 'DOWN'}
        disabled={disabled}
        onVote={onVote}
      />

      <p className={styles.optionHint}>
        {ballot.status === 'CLOSED' ? 'VOTING CLOSED · RESULT LOCKED' : 'SELECT THE SAME OPTION AGAIN TO REMOVE YOUR VOTE'}
      </p>
    </div>
  );
}

interface VoteOptionProps {
  type: VoteType;
  label: string;
  count: number;
  percentage: number;
  selected: boolean;
  disabled: boolean;
  onVote: (type: VoteType) => void;
}

function VoteOption({ type, label, count, percentage, selected, disabled, onVote }: VoteOptionProps) {
  const selectedClass = selected ? (type === 'UP' ? styles.selectedUp : styles.selectedDown) : '';

  return (
    <button
      type="button"
      disabled={disabled}
      aria-pressed={selected}
      aria-label={`${label}: ${count} votes, ${percentage} percent${selected ? ', your current choice' : ''}`}
      className={`${styles.option} ${selectedClass}`}
      onClick={() => onVote(type)}
    >
      <span className={styles.optionName}>{label}</span>
      <b className={styles.optionCount}>{count}</b>
      <span className={styles.optionPercent}>{percentage}%</span>
      <span className={styles.track} aria-hidden="true">
        <span className={styles.fill} style={{ '--vote-share': `${percentage}%` } as React.CSSProperties} />
      </span>
    </button>
  );
}

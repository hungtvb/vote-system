'use client';

import type { CSSProperties } from 'react';
import { calculateVoteBreakdown, formatVoteScore } from '@/shared/ballot/ballot-view';
import type { Ballot, VoteType } from '@/shared/api/types';
import { useI18n } from '@/shared/i18n/I18nProvider';
import styles from './BallotCard.module.scss';

interface BallotOptionsProps {
  ballot: Ballot;
  busy: boolean;
  readOnly: boolean;
  onVote: (type: VoteType) => void;
}

export function BallotOptions({ ballot, busy, readOnly, onVote }: BallotOptionsProps) {
  const { formatNumber, t } = useI18n();
  const disabled = busy || readOnly || ballot.status === 'CLOSED';
  const breakdown = calculateVoteBreakdown(ballot.upVotes, ballot.downVotes);

  return (
    <div className={styles.votePanel} data-qa-vote-split={`${breakdown.upPercentage}/${breakdown.downPercentage}`}>
      <span className={styles.voteLabel}>{t('ballots', 'officialBallot')}</span>
      <div className={styles.counter} data-qa-vote-score={formatVoteScore(ballot.voteScore)} aria-label={t('ballots', 'voteScore', { score: ballot.voteScore })}>
        {formatVoteScore(ballot.voteScore)}
      </div>

      <VoteOption
        type="UP"
        label={t('ballots', 'endorse')}
        count={ballot.upVotes}
        percentage={breakdown.upPercentage}
        selected={ballot.myVote === 'UP'}
        disabled={disabled}
        onVote={onVote}
        formatNumber={formatNumber}
        ariaLabel={(count, percentage, selected) => t('ballots', 'voteOptionLabel', {
          label: t('ballots', 'endorse'),
          count: formatNumber(count),
          percentage: formatNumber(percentage),
          selected: selected ? t('ballots', 'currentChoice') : ''
        })}
      />
      <VoteOption
        type="DOWN"
        label={t('ballots', 'reject')}
        count={ballot.downVotes}
        percentage={breakdown.downPercentage}
        selected={ballot.myVote === 'DOWN'}
        disabled={disabled}
        onVote={onVote}
        formatNumber={formatNumber}
        ariaLabel={(count, percentage, selected) => t('ballots', 'voteOptionLabel', {
          label: t('ballots', 'reject'),
          count: formatNumber(count),
          percentage: formatNumber(percentage),
          selected: selected ? t('ballots', 'currentChoice') : ''
        })}
      />

      <p className={styles.optionHint}>
        {readOnly
          ? t('system', 'readOnlyVoteHint')
          : ballot.status === 'CLOSED'
            ? t('ballots', 'votingClosed')
            : t('ballots', 'removeVoteHint')}
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
  formatNumber: (value: number, options?: Intl.NumberFormatOptions) => string;
  ariaLabel: (count: number, percentage: number, selected: boolean) => string;
}

function VoteOption({ type, label, count, percentage, selected, disabled, onVote, formatNumber, ariaLabel }: VoteOptionProps) {
  const selectedClass = selected ? (type === 'UP' ? styles.selectedUp : styles.selectedDown) : '';
  const shareStyle = { '--vote-share': `${percentage}%` } as CSSProperties;

  return (
    <button
      type="button"
      data-qa-vote-option={type}
      disabled={disabled}
      aria-pressed={selected}
      aria-label={ariaLabel(count, percentage, selected)}
      className={`${styles.option} ${selectedClass}`}
      onClick={() => onVote(type)}
    >
      <span className={styles.optionName}>{label}</span>
      <b className={styles.optionCount}>{formatNumber(count)}</b>
      <span className={styles.optionPercent}>{formatNumber(percentage)}%</span>
      <span className={styles.track} aria-hidden="true">
        <span className={styles.fill} style={shareStyle} />
      </span>
    </button>
  );
}

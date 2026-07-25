import type { Ballot, VoteType } from '@/shared/api/types';
import styles from './BallotApp.module.scss';

export function VoteControl({ ballot, busy, onVote }: { ballot: Ballot; busy: boolean; onVote: (type: VoteType) => void }) {
  const disabled = busy || ballot.status === 'CLOSED';
  return (
    <div className={styles.votePanel}>
      <span className={styles.voteLabel}>OFFICIAL BALLOT</span>
      <div className={styles.counter}>{ballot.voteScore >= 0 ? '+' : ''}{ballot.voteScore.toString().padStart(4, '0')}</div>
      <button disabled={disabled} aria-pressed={ballot.myVote === 'UP'} className={ballot.myVote === 'UP' ? styles.selectedUp : ''} onClick={() => onVote('UP')}>
        <span>ENDORSE</span><b>{ballot.upVotes}</b><i>{ballot.myVote === 'UP' ? '✓' : '○'}</i>
      </button>
      <button disabled={disabled} aria-pressed={ballot.myVote === 'DOWN'} className={ballot.myVote === 'DOWN' ? styles.selectedDown : ''} onClick={() => onVote('DOWN')}>
        <span>REJECT</span><b>{ballot.downVotes}</b><i>{ballot.myVote === 'DOWN' ? '×' : '○'}</i>
      </button>
    </div>
  );
}

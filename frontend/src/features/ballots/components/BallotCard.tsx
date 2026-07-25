import type { Ballot, VoteType } from '@/shared/api/types';
import { VoteControl } from './VoteControl';
import styles from './BallotApp.module.scss';

export function BallotCard({ ballot, busy, onVote }: { ballot: Ballot; busy: boolean; onVote: (type: VoteType) => void }) {
  return (
    <article className={styles.ballotCard} aria-busy={busy}>
      <header className={styles.cardHeader}>
        <div><span>{ballot.ballotNumber}</span><span>{new Date(ballot.createdAt).toLocaleDateString('vi-VN')}</span></div>
        <span className={ballot.status === 'CLOSED' ? styles.closed : styles.open}>{ballot.status}</span>
      </header>
      <div className={styles.cardBody}>
        <div className={styles.copy}>
          <p className={styles.category}>{ballot.category}</p>
          <h2>{ballot.title}</h2>
          <p>{ballot.content}</p>
          <div className={styles.meta}>AUTHOR ID: {ballot.authorId.slice(0, 8).toUpperCase()} · {ballot.totalVotes} REGISTERED VOTES</div>
        </div>
        <VoteControl ballot={ballot} busy={busy} onVote={onVote} />
      </div>
      {ballot.verdict !== 'UNDECIDED' && <div className={styles.verdict}>{ballot.finalVerdict ? 'FINAL' : 'CURRENT'} VERDICT: {ballot.verdict}</div>}
    </article>
  );
}

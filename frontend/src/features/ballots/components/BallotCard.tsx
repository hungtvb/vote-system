import type { Ballot, VoteType } from '@/shared/api/types';
import { VoteControl } from './VoteControl';
import styles from './BallotApp.module.scss';

interface BallotCardProps {
  ballot: Ballot;
  busy: boolean;
  owned: boolean;
  onOpen: () => void;
  onVote: (type: VoteType) => void;
  onEdit: () => void;
  onDelete: () => void;
  onCloseBallot: () => void;
}

export function BallotCard({ ballot, busy, owned, onOpen, onVote, onEdit, onDelete, onCloseBallot }: BallotCardProps) {
  return (
    <article className={styles.ballotCard} aria-busy={busy}>
      <header className={styles.cardHeader}>
        <div><span>{ballot.ballotNumber}</span><span>{new Date(ballot.createdAt).toLocaleDateString('vi-VN')}</span></div>
        <div className={styles.cardHeaderActions}>
          {owned && <span className={styles.ownerMark}>YOUR RECORD</span>}
          <span className={ballot.status === 'CLOSED' ? styles.closed : styles.open}>{ballot.status}</span>
        </div>
      </header>
      <div className={styles.cardBody}>
        <div className={styles.copy}>
          <p className={styles.category}>{ballot.category}</p>
          <button type="button" className={styles.titleButton} onClick={onOpen} aria-label={`Open ballot ${ballot.title}`}>
            <h2>{ballot.title}</h2>
          </button>
          <p>{ballot.content}</p>
          <div className={styles.authorLine} data-qa-author>
            <span className={styles.authorInitials} aria-hidden="true">{ballot.author.initials}</span>
            <span><strong>FILED BY {ballot.author.displayName}</strong><small>{ballot.totalVotes} REGISTERED VOTES</small></span>
          </div>
          <div className={styles.cardActions}>
            <button type="button" onClick={onOpen}>VIEW FULL RECORD</button>
            {owned && (
              <div className={styles.ownerActions} aria-label="Owner actions">
                <button type="button" onClick={onEdit} disabled={busy || ballot.status === 'CLOSED'}>EDIT</button>
                <button type="button" onClick={onCloseBallot} disabled={busy || ballot.status === 'CLOSED'}>CLOSE BALLOT</button>
                <button type="button" className={styles.dangerButton} onClick={onDelete} disabled={busy}>DELETE</button>
              </div>
            )}
          </div>
        </div>
        <VoteControl ballot={ballot} busy={busy} onVote={onVote} />
      </div>
      {ballot.verdict !== 'UNDECIDED' && <div className={styles.verdict}>{ballot.finalVerdict ? 'FINAL' : 'CURRENT'} VERDICT: {ballot.verdict}</div>}
    </article>
  );
}

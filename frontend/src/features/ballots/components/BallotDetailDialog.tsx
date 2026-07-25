'use client';

import type { Ballot, VoteType } from '@/shared/api/types';
import { VoteControl } from './VoteControl';
import styles from './BallotApp.module.scss';

interface BallotDetailDialogProps {
  ballot: Ballot;
  busy: boolean;
  owned: boolean;
  onClose: () => void;
  onVote: (type: VoteType) => void;
  onEdit: () => void;
  onDelete: () => void;
  onCloseBallot: () => void;
}

export function BallotDetailDialog({ ballot, busy, owned, onClose, onVote, onEdit, onDelete, onCloseBallot }: BallotDetailDialogProps) {
  return (
    <div className={styles.backdrop} onMouseDown={event => event.target === event.currentTarget && onClose()}>
      <section className={`${styles.dialog} ${styles.detailDialog}`} role="dialog" aria-modal="true" aria-labelledby="ballot-detail-title">
        <div className={styles.detailHeader}>
          <div>
            <p className={styles.formTab}>{ballot.ballotNumber}</p>
            <h2 id="ballot-detail-title">{ballot.title}</h2>
          </div>
          <button type="button" className={styles.textButton} onClick={onClose}>CLOSE</button>
        </div>

        <div className={styles.detailMeta}>
          <span>{ballot.status}</span>
          <span>{new Date(ballot.createdAt).toLocaleString('vi-VN')}</span>
          <span>{ballot.totalVotes} REGISTERED VOTES</span>
        </div>

        <div className={styles.detailBody}>
          <div>
            <p className={styles.category}>{ballot.category}</p>
            <div className={styles.fullContent}>{ballot.content}</div>
            <div className={styles.meta}>AUTHOR ID: {ballot.authorId.slice(0, 8).toUpperCase()}</div>
            {owned && (
              <div className={styles.ownerActions} aria-label="Owner actions">
                <button type="button" onClick={onEdit} disabled={busy || ballot.status === 'CLOSED'}>EDIT</button>
                <button type="button" onClick={onCloseBallot} disabled={busy || ballot.status === 'CLOSED'}>CLOSE BALLOT</button>
                <button type="button" className={styles.dangerButton} onClick={onDelete} disabled={busy}>DELETE</button>
              </div>
            )}
          </div>
          <VoteControl ballot={ballot} busy={busy} onVote={onVote} />
        </div>

        {ballot.verdict !== 'UNDECIDED' && <div className={styles.detailVerdict}>{ballot.finalVerdict ? 'FINAL' : 'CURRENT'} VERDICT: {ballot.verdict}</div>}
      </section>
    </div>
  );
}

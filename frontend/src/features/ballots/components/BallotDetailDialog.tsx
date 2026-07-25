'use client';

import { useModalDialog } from '@/shared/hooks/useModalDialog';
import type { Ballot, VoteType } from '@/shared/api/types';
import { BallotActions } from './BallotActions';
import { BallotOptions } from './BallotOptions';
import { BallotStamp } from './BallotStamp';
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
  const modal = useModalDialog(onClose);

  return (
    <div className={styles.backdrop} onMouseDown={modal.onBackdropMouseDown}>
      <section ref={modal.dialogRef} tabIndex={-1} className={`${styles.dialog} ${styles.detailDialog}`} role="dialog" aria-modal="true" aria-labelledby="ballot-detail-title" onKeyDown={modal.onDialogKeyDown}>
        <div className={styles.detailHeader}>
          <div>
            <p className={styles.formTab}>{ballot.ballotNumber}</p>
            <h2 id="ballot-detail-title">{ballot.title}</h2>
          </div>
          <button type="button" autoFocus className={styles.textButton} onClick={onClose}>CLOSE</button>
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
            <div className={styles.authorLine} data-qa-author>
              <span className={styles.authorInitials} aria-hidden="true">{ballot.author.initials}</span>
              <span><strong>FILED BY {ballot.author.displayName}</strong><small>PUBLIC AUTHOR RECORD</small></span>
            </div>
            <BallotActions
              owned={owned}
              busy={busy}
              closed={ballot.status === 'CLOSED'}
              onEdit={onEdit}
              onDelete={onDelete}
              onCloseBallot={onCloseBallot}
            />
            <BallotStamp verdict={ballot.verdict} finalVerdict={ballot.finalVerdict} placement="detail" />
          </div>
          <BallotOptions ballot={ballot} busy={busy} onVote={onVote} />
        </div>
      </section>
    </div>
  );
}

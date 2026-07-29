'use client';

import { useModalDialog } from '@/shared/hooks/useModalDialog';
import { useI18n } from '@/shared/i18n/I18nProvider';
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
  const { formatDate, formatNumber, t } = useI18n();
  const modal = useModalDialog(onClose);

  return (
    <div className={styles.backdrop} onMouseDown={modal.onBackdropMouseDown}>
      <section ref={modal.dialogRef} tabIndex={-1} className={`${styles.dialog} ${styles.detailDialog}`} role="dialog" aria-modal="true" aria-labelledby="ballot-detail-title" onKeyDown={modal.onDialogKeyDown}>
        <div className={styles.detailHeader}>
          <div>
            <p className={styles.formTab}>{ballot.ballotNumber}</p>
            <h2 id="ballot-detail-title">{ballot.title}</h2>
          </div>
          <button type="button" className={styles.closeIcon} onClick={onClose} aria-label={t('common', 'close')}>×</button>
        </div>

        <div className={styles.detailMeta}>
          <span>{ballot.status === 'CLOSED' ? t('ballots', 'statusClosed') : t('ballots', 'statusOpen')}</span>
          <span>{formatDate(ballot.createdAt, { dateStyle: 'medium', timeStyle: 'short' })}</span>
          <span>{t('ballots', 'registeredVotes', { count: formatNumber(ballot.totalVotes) })}</span>
        </div>

        <div className={styles.detailBody}>
          <div>
            <p className={styles.category}>{ballot.category}</p>
            <div className={styles.fullContent}>{ballot.content}</div>
            <div className={styles.authorLine} data-qa-author>
              <span className={styles.authorInitials} aria-hidden="true">{ballot.author.initials}</span>
              <span><strong>{t('ballots', 'filedBy', { name: ballot.author.displayName })}</strong><small>{t('ballots', 'publicAuthorRecord')}</small></span>
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

'use client';

import { useI18n } from '@/shared/i18n/I18nProvider';
import styles from './BallotCard.module.scss';

interface BallotActionsProps {
  owned: boolean;
  busy: boolean;
  closed: boolean;
  onOpen?: () => void;
  onEdit: () => void;
  onDelete: () => void;
  onCloseBallot: () => void;
}

export function BallotActions({ owned, busy, closed, onOpen, onEdit, onDelete, onCloseBallot }: BallotActionsProps) {
  const { t } = useI18n();

  return (
    <div className={styles.actions}>
      {onOpen && <button type="button" onClick={onOpen}>{t('ballots', 'viewFullRecord')}</button>}
      {owned && (
        <div className={styles.ownerActions} data-qa-owner-actions aria-label={t('ballots', 'ownerActions')}>
          <button type="button" data-qa-owner-action="edit" onClick={onEdit} disabled={busy || closed}>{t('ballots', 'edit')}</button>
          <button type="button" data-qa-owner-action="close" onClick={onCloseBallot} disabled={busy || closed}>{t('ballots', 'closeBallot')}</button>
          <button type="button" data-qa-owner-action="delete" className={styles.danger} onClick={onDelete} disabled={busy}>{t('ballots', 'delete')}</button>
        </div>
      )}
    </div>
  );
}

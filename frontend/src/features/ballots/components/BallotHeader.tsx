'use client';

import type { Ballot } from '@/shared/api/types';
import { useI18n } from '@/shared/i18n/I18nProvider';
import styles from './BallotCard.module.scss';

interface BallotHeaderProps {
  ballot: Ballot;
  owned: boolean;
}

export function BallotHeader({ ballot, owned }: BallotHeaderProps) {
  const { formatDate, t } = useI18n();
  const closed = ballot.status === 'CLOSED';

  return (
    <header className={styles.header}>
      <div className={styles.headerMeta}>
        <span className={styles.ballotNumber}>{ballot.ballotNumber}</span>
        <time dateTime={ballot.createdAt}>{formatDate(ballot.createdAt)}</time>
      </div>
      <div className={styles.headerState}>
        {owned && <span className={styles.ownerMark}>{t('ballots', 'yourRecord')}</span>}
        <span className={`${styles.status} ${closed ? styles.statusClosed : ''}`}>
          {closed ? t('ballots', 'statusClosed') : t('ballots', 'statusOpen')}
        </span>
      </div>
    </header>
  );
}

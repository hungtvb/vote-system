import type { Ballot } from '@/shared/api/types';
import styles from './BallotCard.module.scss';

interface BallotHeaderProps {
  ballot: Ballot;
  owned: boolean;
}

export function BallotHeader({ ballot, owned }: BallotHeaderProps) {
  const closed = ballot.status === 'CLOSED';

  return (
    <header className={styles.header}>
      <div className={styles.headerMeta}>
        <span className={styles.ballotNumber}>{ballot.ballotNumber}</span>
        <time dateTime={ballot.createdAt}>{new Date(ballot.createdAt).toLocaleDateString('vi-VN')}</time>
      </div>
      <div className={styles.headerState}>
        {owned && <span className={styles.ownerMark}>YOUR RECORD</span>}
        <span className={`${styles.status} ${closed ? styles.statusClosed : ''}`}>{ballot.status}</span>
      </div>
    </header>
  );
}

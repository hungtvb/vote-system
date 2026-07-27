import type { Ballot, VoteType } from '@/shared/api/types';
import { useI18n } from '@/shared/i18n/I18nProvider';
import { BallotActions } from './BallotActions';
import { BallotHeader } from './BallotHeader';
import { BallotOptions } from './BallotOptions';
import { BallotStamp } from './BallotStamp';
import styles from './BallotCard.module.scss';

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
  const { t, formatNumber } = useI18n();

  return (
    <article className={styles.ballotCard} aria-busy={busy}>
      <BallotHeader ballot={ballot} owned={owned} />

      <div className={styles.body}>
        <div className={styles.copy}>
          <p className={styles.category}>{ballot.category}</p>
          <button type="button" className={styles.titleButton} onClick={onOpen} aria-label={t('ballots', 'openBallot', { title: ballot.title })}>
            <h2>{ballot.title}</h2>
          </button>
          <p className={styles.excerpt}>{ballot.content}</p>

          <div className={styles.authorLine} data-qa-author>
            <span className={styles.authorInitials} aria-hidden="true">{ballot.author.initials}</span>
            <span className={styles.authorCopy}>
              <strong>{t('ballots', 'filedBy', { name: ballot.author.displayName })}</strong>
              <small>{t('ballots', 'registeredVotes', { count: formatNumber(ballot.totalVotes) })}</small>
            </span>
          </div>

          <BallotActions
            owned={owned}
            busy={busy}
            closed={ballot.status === 'CLOSED'}
            onOpen={onOpen}
            onEdit={onEdit}
            onDelete={onDelete}
            onCloseBallot={onCloseBallot}
          />
        </div>

        <BallotOptions ballot={ballot} busy={busy} onVote={onVote} />
      </div>

      <BallotStamp verdict={ballot.verdict} finalVerdict={ballot.finalVerdict} />
    </article>
  );
}

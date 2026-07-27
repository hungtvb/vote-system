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
  return (
    <div className={styles.actions}>
      {onOpen && <button type="button" onClick={onOpen}>VIEW FULL RECORD</button>}
      {owned && (
        <div className={styles.ownerActions} data-qa-owner-actions aria-label="Owner actions">
          <button type="button" data-qa-owner-action="edit" onClick={onEdit} disabled={busy || closed}>EDIT</button>
          <button type="button" data-qa-owner-action="close" onClick={onCloseBallot} disabled={busy || closed}>CLOSE BALLOT</button>
          <button type="button" data-qa-owner-action="delete" className={styles.danger} onClick={onDelete} disabled={busy}>DELETE</button>
        </div>
      )}
    </div>
  );
}

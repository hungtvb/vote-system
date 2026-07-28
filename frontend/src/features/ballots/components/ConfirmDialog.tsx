'use client';

import { useCallback, useState } from 'react';
import { useModalDialog } from '@/shared/hooks/useModalDialog';
import { useI18n } from '@/shared/i18n/I18nProvider';
import styles from './ConfirmDialog.module.scss';

interface ConfirmDialogProps {
  title: string;
  description: string;
  reference?: string;
  confirmLabel: string;
  pendingLabel: string;
  onClose: () => void;
  onConfirm: () => Promise<void>;
}

export function ConfirmDialog({
  title,
  description,
  reference,
  confirmLabel,
  pendingLabel,
  onClose,
  onConfirm
}: ConfirmDialogProps) {
  const { t } = useI18n();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const requestClose = useCallback(() => {
    if (!pending) onClose();
  }, [onClose, pending]);
  const modal = useModalDialog(requestClose);

  async function confirm() {
    if (pending) return;
    setPending(true);
    setError('');
    try {
      await onConfirm();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : t('errors', 'actionFailed'));
      setPending(false);
    }
  }

  return (
    <div className={styles.backdrop} data-qa-confirm-backdrop onMouseDown={modal.onBackdropMouseDown}>
      <section
        ref={modal.dialogRef}
        className={styles.dialog}
        data-qa-confirm-dialog
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        aria-describedby="confirm-dialog-description"
        aria-busy={pending}
        onKeyDown={modal.onDialogKeyDown}
      >
        <p className={styles.eyebrow}>{t('ballots', 'officialRecordAction')}</p>
        <h2 id="confirm-dialog-title">{title}</h2>
        {reference && <p className={styles.reference}>{reference}</p>}
        <p id="confirm-dialog-description" className={styles.description}>{description}</p>
        {error && <p className={styles.error} role="alert">{error}</p>}
        <div className={styles.actions}>
          <button type="button" autoFocus disabled={pending} onClick={requestClose}>{t('common', 'cancel')}</button>
          <button
            type="button"
            className={styles.danger}
            data-qa-confirm-action
            disabled={pending}
            onClick={() => void confirm()}
          >
            {pending ? pendingLabel : confirmLabel}
          </button>
        </div>
      </section>
    </div>
  );
}

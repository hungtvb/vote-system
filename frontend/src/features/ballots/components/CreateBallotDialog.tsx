'use client';

import { useState } from 'react';
import type { FormEvent } from 'react';
import { useModalDialog } from '@/shared/hooks/useModalDialog';
import { useI18n } from '@/shared/i18n/I18nProvider';
import styles from './BallotApp.module.scss';

export function CreateBallotDialog({ onClose, onCreate }: { onClose: () => void; onCreate: (title: string, content: string) => Promise<void> | void }) {
  const { formatNumber, t } = useI18n();
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const modal = useModalDialog(onClose);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      await onCreate(title.trim(), content.trim());
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t('errors', 'createBallot'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={styles.backdrop} onMouseDown={modal.onBackdropMouseDown}>
      <section
        ref={modal.dialogRef}
        tabIndex={-1}
        className={`${styles.dialog} ${styles.createDialog}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby="create-title"
        onKeyDown={modal.onDialogKeyDown}
        data-qa-create-dialog
      >
        <div className={styles.dialogHeader}>
          <div>
            <p className={styles.formTab}>{t('ballots', 'createFormCode')}</p>
            <h2 id="create-title">{t('ballots', 'createTitle')}</h2>
          </div>
          <button type="button" className={styles.closeIcon} onClick={onClose} disabled={busy} aria-label={t('common', 'close')}>×</button>
        </div>
        <form onSubmit={submit}>
          <label>{t('ballots', 'ballotTitle')}<input required autoFocus maxLength={200} value={title} onChange={event => setTitle(event.target.value)} /></label>
          <label>{t('ballots', 'detailedStatement')}<textarea required maxLength={20000} rows={10} value={content} onChange={event => setContent(event.target.value)} /></label>
          <small>{formatNumber(content.length)} / {formatNumber(20000)}</small>
          {error && <span className={styles.error} role="alert">{error}</span>}
          <div className={styles.formActions}>
            <button className={styles.primaryButton} disabled={busy || !title.trim() || !content.trim()} data-qa-submit-ballot>
              {busy ? t('ballots', 'submitting') : t('ballots', 'submitBallot')}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}

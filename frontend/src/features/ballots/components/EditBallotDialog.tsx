'use client';

import { useState } from 'react';
import type { FormEvent } from 'react';
import { useModalDialog } from '@/shared/hooks/useModalDialog';
import { useI18n } from '@/shared/i18n/I18nProvider';
import type { Ballot } from '@/shared/api/types';
import styles from './BallotApp.module.scss';

export function EditBallotDialog({ ballot, onClose, onSave }: { ballot: Ballot; onClose: () => void; onSave: (title: string, content: string) => Promise<void> }) {
  const { formatNumber, t } = useI18n();
  const [title, setTitle] = useState(ballot.title);
  const [content, setContent] = useState(ballot.content);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const modal = useModalDialog(onClose);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      await onSave(title.trim(), content.trim());
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t('errors', 'updateBallot'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={styles.backdrop} onMouseDown={modal.onBackdropMouseDown}>
      <section ref={modal.dialogRef} tabIndex={-1} className={`${styles.dialog} ${styles.createDialog}`} role="dialog" aria-modal="true" aria-labelledby="edit-ballot-title" onKeyDown={modal.onDialogKeyDown}>
        <p className={styles.formTab}>{t('ballots', 'editFormCode')}</p>
        <h2 id="edit-ballot-title">{t('ballots', 'editTitle')}</h2>
        <form onSubmit={submit}>
          <label>{t('ballots', 'entryTitle')}<input required autoFocus maxLength={200} value={title} onChange={event => setTitle(event.target.value)} /></label>
          <label>{t('ballots', 'detailedStatement')}<textarea required maxLength={20000} rows={10} value={content} onChange={event => setContent(event.target.value)} /></label>
          <small>{formatNumber(content.length)} / {formatNumber(20000)}</small>
          {error && <span className={styles.error} role="alert">{error}</span>}
          <div className={styles.formActions}>
            <button type="button" className={styles.textButton} onClick={onClose}>{t('common', 'cancel')}</button>
            <button className={styles.primaryButton} disabled={busy || !title.trim() || !content.trim()}>{busy ? t('ballots', 'saving') : t('ballots', 'saveChanges')}</button>
          </div>
        </form>
      </section>
    </div>
  );
}

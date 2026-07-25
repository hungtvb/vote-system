'use client';

import { useState } from 'react';
import type { FormEvent } from 'react';
import type { Ballot } from '@/shared/api/types';
import styles from './BallotApp.module.scss';

export function EditBallotDialog({ ballot, onClose, onSave }: { ballot: Ballot; onClose: () => void; onSave: (title: string, content: string) => Promise<void> }) {
  const [title, setTitle] = useState(ballot.title);
  const [content, setContent] = useState(ballot.content);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      await onSave(title.trim(), content.trim());
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to update record.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={styles.backdrop} onMouseDown={event => event.target === event.currentTarget && onClose()}>
      <section className={`${styles.dialog} ${styles.createDialog}`} role="dialog" aria-modal="true" aria-labelledby="edit-ballot-title">
        <p className={styles.formTab}>FORM-8B: AMENDMENT</p>
        <h2 id="edit-ballot-title">Amend record</h2>
        <form onSubmit={submit}>
          <label>TITLE OF ENTRY<input required maxLength={200} value={title} onChange={event => setTitle(event.target.value)} /></label>
          <label>DETAILED STATEMENT<textarea required maxLength={20000} rows={10} value={content} onChange={event => setContent(event.target.value)} /></label>
          <small>{content.length} / 20,000</small>
          {error && <span className={styles.error} role="alert">{error}</span>}
          <div className={styles.formActions}>
            <button type="button" className={styles.textButton} onClick={onClose}>CANCEL</button>
            <button className={styles.primaryButton} disabled={busy || !title.trim() || !content.trim()}>{busy ? 'SAVING...' : 'SAVE CHANGES'}</button>
          </div>
        </form>
      </section>
    </div>
  );
}

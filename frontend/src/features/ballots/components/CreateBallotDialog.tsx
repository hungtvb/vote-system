'use client';

import { useState } from 'react';
import type { FormEvent } from 'react';
import styles from './BallotApp.module.scss';

export function CreateBallotDialog({ onClose, onCreate }: { onClose: () => void; onCreate: (title: string, content: string) => Promise<void> | void }) {
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      await onCreate(title.trim(), content.trim());
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Unable to file record.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={styles.backdrop} onMouseDown={event => event.target === event.currentTarget && onClose()}>
      <section className={`${styles.dialog} ${styles.createDialog}`} role="dialog" aria-modal="true" aria-labelledby="create-title">
        <p className={styles.formTab}>FORM-8A: SUBMISSION</p>
        <h2 id="create-title">Official entry</h2>
        <form onSubmit={submit}>
          <label>TITLE OF ENTRY<input required maxLength={200} value={title} onChange={event => setTitle(event.target.value)} /></label>
          <label>DETAILED STATEMENT<textarea required maxLength={20000} rows={10} value={content} onChange={event => setContent(event.target.value)} /></label>
          <small>{content.length} / 20,000</small>
          {error && <span className={styles.error} role="alert">{error}</span>}
          <div className={styles.formActions}>
            <button type="button" className={styles.textButton} onClick={onClose}>CANCEL</button>
            <button className={styles.primaryButton} disabled={busy || !title.trim() || !content.trim()}>{busy ? 'FILING...' : 'SUBMIT RECORD'}</button>
          </div>
        </form>
      </section>
    </div>
  );
}

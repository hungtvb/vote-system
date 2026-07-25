'use client';

import { useState } from 'react';
import type { FormEvent } from 'react';
import { api } from '@/shared/api/client';
import { useModalDialog } from '@/shared/hooks/useModalDialog';
import type { Session } from '@/shared/api/types';
import styles from '@/features/ballots/components/BallotApp.module.scss';

export function AuthDialog({ onClose, onAuthenticated }: { onClose: () => void; onAuthenticated: (session: Session) => void }) {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const modal = useModalDialog(onClose);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (mode === 'register' && password !== confirm) return setError('Mật khẩu xác nhận không khớp.');
    setBusy(true);
    setError('');
    try {
      const session = mode === 'login' ? await api.login(email, password) : await api.register(email, password);
      onAuthenticated(session);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Authorization denied.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={styles.backdrop} onMouseDown={modal.onBackdropMouseDown}>
      <section ref={modal.dialogRef} tabIndex={-1} className={styles.dialog} role="dialog" aria-modal="true" aria-labelledby="auth-title" onKeyDown={modal.onDialogKeyDown}>
        <div className={styles.dialogTabs} role="tablist" aria-label="Authentication mode">
          <button type="button" role="tab" aria-selected={mode === 'login'} onClick={() => setMode('login')}>REGISTRY ACCESS</button>
          <button type="button" role="tab" aria-selected={mode === 'register'} onClick={() => setMode('register')}>NEW ENTRY</button>
        </div>
        <h2 id="auth-title">Official record</h2>
        <p>FORM ID: AUTH-8821</p>
        <form onSubmit={submit}>
          <label>IDENTIFICATION (EMAIL)<input required autoFocus type="email" autoComplete="email" value={email} onChange={event => setEmail(event.target.value)} /></label>
          <label>AUTHORIZATION KEY<input required minLength={8} type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} value={password} onChange={event => setPassword(event.target.value)} /></label>
          {mode === 'register' && <label>CONFIRM KEY<input required type="password" autoComplete="new-password" value={confirm} onChange={event => setConfirm(event.target.value)} /></label>}
          {error && <span className={styles.error} role="alert">{error}</span>}
          <button className={styles.primaryButton} disabled={busy}>{busy ? 'VERIFYING...' : mode === 'login' ? 'VERIFY CREDENTIALS' : 'SUBMIT ENTRY'}</button>
          <button type="button" className={styles.textButton} onClick={onClose}>CANCEL</button>
        </form>
      </section>
    </div>
  );
}

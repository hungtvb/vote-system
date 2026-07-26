'use client';

import { useState } from 'react';
import type { FormEvent } from 'react';
import { authApi } from '@/shared/api/auth-api';
import { socialAuthApi, type SocialProviderId } from '@/shared/api/social-auth-api';
import type { Session } from '@/shared/api/types';
import type { AuthIntent } from '@/shared/auth/auth-intent';
import { useModalDialog } from '@/shared/hooks/useModalDialog';
import styles from '@/features/ballots/components/BallotApp.module.scss';

export type AuthMode = 'login' | 'register';

interface AuthDialogProps {
  initialMode?: AuthMode;
  intent?: AuthIntent;
  onClose: () => void;
  onAuthenticated: (session: Session) => void | Promise<void>;
}

export function AuthDialog({ initialMode = 'login', intent = 'authenticate', onClose, onAuthenticated }: AuthDialogProps) {
  const [mode, setMode] = useState<AuthMode>(initialMode);
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [socialBusy, setSocialBusy] = useState<SocialProviderId | null>(null);
  const modal = useModalDialog(onClose);

  function changeMode(nextMode: AuthMode) {
    setMode(nextMode);
    setError('');
  }

  async function startSocial(provider: SocialProviderId) {
    setSocialBusy(provider);
    setError('');
    try {
      const response = await socialAuthApi.start(provider, intent);
      window.location.assign(response.authorizationUrl);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Social sign-in is unavailable.');
      setSocialBusy(null);
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (mode === 'register' && password !== confirm) return setError('Mật khẩu xác nhận không khớp.');
    setBusy(true);
    setError('');
    try {
      const session = mode === 'login'
        ? await authApi.login(email, password)
        : await authApi.register(email, password, displayName);
      await onAuthenticated(session);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Authorization denied.');
    } finally {
      setBusy(false);
    }
  }

  const locked = busy || socialBusy !== null;

  return (
    <div className={styles.backdrop} onMouseDown={modal.onBackdropMouseDown}>
      <section
        ref={modal.dialogRef}
        tabIndex={-1}
        className={styles.dialog}
        role="dialog"
        aria-modal="true"
        aria-labelledby="auth-title"
        onKeyDown={modal.onDialogKeyDown}
        data-qa-auth-dialog
        data-auth-mode={mode}
        data-auth-intent={intent}
      >
        <div className={styles.dialogTabs} role="tablist" aria-label="Authentication mode">
          <button type="button" role="tab" aria-selected={mode === 'login'} data-qa-auth-tab style={{ minHeight: 44 }} onClick={() => changeMode('login')}>SIGN IN</button>
          <button type="button" role="tab" aria-selected={mode === 'register'} data-qa-auth-tab style={{ minHeight: 44 }} onClick={() => changeMode('register')}>REGISTER</button>
        </div>
        <h2 id="auth-title">Voter account</h2>
        <p>{intent === 'create-ballot' ? 'AUTHENTICATE TO CONTINUE YOUR BALLOT' : mode === 'login' ? 'ACCESS YOUR EXISTING VOTER ID' : 'CREATE A NEW VOTER ID'}</p>

        <div className={styles.socialActions} aria-label="Social sign-in providers">
          <button type="button" disabled={locked} data-qa-social-provider="google" onClick={() => void startSocial('google')}>
            {socialBusy === 'google' ? 'CONNECTING TO GOOGLE...' : 'CONTINUE WITH GOOGLE'}
          </button>
          <button type="button" disabled={locked} data-qa-social-provider="github" onClick={() => void startSocial('github')}>
            {socialBusy === 'github' ? 'CONNECTING TO GITHUB...' : 'CONTINUE WITH GITHUB'}
          </button>
        </div>
        <div className={styles.authDivider}><span>OR USE EMAIL</span></div>

        <form onSubmit={submit}>
          {mode === 'register' && (
            <label>
              PUBLIC VOTER NAME (OPTIONAL)
              <input style={{ minHeight: 44 }} maxLength={80} autoComplete="name" value={displayName} onChange={event => setDisplayName(event.target.value)} placeholder="A public name or stored pseudonym" />
            </label>
          )}
          <label>EMAIL<input required autoFocus style={{ minHeight: 44 }} type="email" autoComplete="email" value={email} onChange={event => setEmail(event.target.value)} /></label>
          <label>PASSWORD<input required style={{ minHeight: 44 }} minLength={8} type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} value={password} onChange={event => setPassword(event.target.value)} /></label>
          {mode === 'register' && <label>CONFIRM PASSWORD<input required style={{ minHeight: 44 }} type="password" autoComplete="new-password" value={confirm} onChange={event => setConfirm(event.target.value)} /></label>}
          {error && <span className={styles.error} role="alert">{error}</span>}
          <button className={styles.primaryButton} disabled={locked} data-qa-auth-submit>
            {busy ? 'VERIFYING...' : mode === 'login' ? 'SIGN IN' : 'CREATE ACCOUNT'}
          </button>
          <button type="button" className={styles.textButton} disabled={locked} onClick={onClose}>CANCEL</button>
        </form>
      </section>
    </div>
  );
}

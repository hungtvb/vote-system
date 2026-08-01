'use client';

import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { authApi } from '@/shared/api/auth-api';
import { socialAuthApi, type SocialProviderId } from '@/shared/api/social-auth-api';
import type { AuthBootstrap } from '@/shared/api/types';
import type { AuthIntent } from '@/shared/auth/auth-intent';
import { useModalDialog } from '@/shared/hooks/useModalDialog';
import { useI18n } from '@/shared/i18n/I18nProvider';
import styles from '@/features/ballots/components/BallotApp.module.scss';
import authStyles from './AuthDialog.module.scss';

export type AuthMode = 'login' | 'register';

interface AuthDialogProps {
  initialMode?: AuthMode;
  intent?: AuthIntent;
  socialProviders?: SocialProviderId[];
  allowRegistration?: boolean;
  onClose: () => void;
  onAuthenticated: (session: AuthBootstrap) => void | Promise<void>;
}

function GoogleIcon() {
  return (
    <svg className={authStyles.providerIcon} viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <path fill="#4285F4" d="M21.6 12.23c0-.71-.06-1.4-.19-2.07H12v3.92h5.38a4.6 4.6 0 0 1-2 3.02v2.55h3.24c1.9-1.75 2.98-4.33 2.98-7.42Z" />
      <path fill="#34A853" d="M12 22c2.7 0 4.98-.9 6.63-2.35l-3.24-2.55c-.9.6-2.05.96-3.39.96-2.61 0-4.82-1.77-5.61-4.15H3.05v2.63A10 10 0 0 0 12 22Z" />
      <path fill="#FBBC05" d="M6.39 13.91A6.02 6.02 0 0 1 6.08 12c0-.66.11-1.3.31-1.91V7.46H3.05A10 10 0 0 0 2 12c0 1.61.39 3.14 1.05 4.54l3.34-2.63Z" />
      <path fill="#EA4335" d="M12 5.94c1.47 0 2.79.51 3.83 1.5l2.87-2.88A9.64 9.64 0 0 0 12 2a10 10 0 0 0-8.95 5.46l3.34 2.63C7.18 7.71 9.39 5.94 12 5.94Z" />
    </svg>
  );
}

function GitHubIcon() {
  return (
    <svg className={authStyles.providerIcon} viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <path fill="currentColor" d="M12 2a10 10 0 0 0-3.16 19.49c.5.09.68-.22.68-.48v-1.87c-2.78.6-3.37-1.18-3.37-1.18-.46-1.16-1.11-1.47-1.11-1.47-.91-.62.07-.61.07-.61 1 .07 1.53 1.03 1.53 1.03.9 1.53 2.35 1.09 2.92.83.09-.65.35-1.09.64-1.34-2.22-.25-4.56-1.11-4.56-4.94 0-1.09.39-1.98 1.03-2.68-.1-.25-.45-1.27.1-2.64 0 0 .84-.27 2.75 1.02A9.55 9.55 0 0 1 12 6.82c.85 0 1.71.12 2.51.34 1.91-1.29 2.75-1.02 2.75-1.02.55 1.37.2 2.39.1 2.64.64.7 1.03 1.59 1.03 2.68 0 3.84-2.34 4.68-4.57 4.93.36.31.68.92.68 1.86v2.76c0 .27.18.58.69.48A10 10 0 0 0 12 2Z" />
    </svg>
  );
}

export function AuthDialog({
  initialMode = 'login',
  intent = 'authenticate',
  socialProviders = [],
  allowRegistration = true,
  onClose,
  onAuthenticated
}: AuthDialogProps) {
  const { t } = useI18n();
  const [mode, setMode] = useState<AuthMode>(initialMode === 'register' && !allowRegistration ? 'login' : initialMode);
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [socialBusy, setSocialBusy] = useState<SocialProviderId | null>(null);
  const modal = useModalDialog(onClose);

  useEffect(() => {
    if (!allowRegistration && mode === 'register') setMode('login');
  }, [allowRegistration, mode]);

  function changeMode(nextMode: AuthMode) {
    if (nextMode === 'register' && !allowRegistration) return;
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
      setError(caught instanceof Error ? caught.message : t('auth', 'socialUnavailable'));
      setSocialBusy(null);
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (mode === 'register' && !allowRegistration) return setError(t('system', 'readOnlyActionUnavailable'));
    if (mode === 'register' && password !== confirm) return setError(t('auth', 'passwordMismatch'));
    setBusy(true);
    setError('');
    try {
      const session = mode === 'login'
        ? await authApi.login(email, password)
        : await authApi.register(email, password, displayName);
      await onAuthenticated(session);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t('auth', 'authorizationDenied'));
    } finally {
      setBusy(false);
    }
  }

  const locked = busy || socialBusy !== null;
  const hasSocialProviders = socialProviders.length > 0;

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
        <div className={styles.dialogHeader}>
          <div className={styles.dialogTabs} role="tablist" aria-label={t('auth', 'voterAccount')}>
            <button type="button" role="tab" aria-selected={mode === 'login'} data-qa-auth-tab style={{ minHeight: 44 }} onClick={() => changeMode('login')}>{t('auth', 'signIn')}</button>
            <button type="button" role="tab" aria-selected={mode === 'register'} data-qa-auth-tab style={{ minHeight: 44 }} disabled={!allowRegistration} title={!allowRegistration ? t('system', 'readOnlyActionUnavailable') : undefined} onClick={() => changeMode('register')}>{t('auth', 'register')}</button>
          </div>
          <button type="button" className={styles.closeIcon} onClick={onClose} disabled={locked} aria-label={t('common', 'close')}>×</button>
        </div>
        <h2 id="auth-title">{t('auth', 'voterAccount')}</h2>
        <p>{intent === 'create-ballot' ? t('auth', 'authenticateToCreate') : mode === 'login' ? t('auth', 'accessExisting') : t('auth', 'createNew')}</p>

        {hasSocialProviders && (
          <>
            <div className={authStyles.socialActions} aria-label={t('auth', 'connectedProviders')}>
              {socialProviders.includes('google') && (
                <button type="button" className={authStyles.socialButton} disabled={locked} data-qa-social-provider="google" onClick={() => void startSocial('google')}>
                  <GoogleIcon />
                  <span>{socialBusy === 'google' ? t('auth', 'connectingGoogle') : t('auth', 'continueGoogle')}</span>
                </button>
              )}
              {socialProviders.includes('github') && (
                <button type="button" className={authStyles.socialButton} disabled={locked} data-qa-social-provider="github" onClick={() => void startSocial('github')}>
                  <GitHubIcon />
                  <span>{socialBusy === 'github' ? t('auth', 'connectingGithub') : t('auth', 'continueGithub')}</span>
                </button>
              )}
            </div>
            <div className={authStyles.divider}><span>{t('auth', 'orUseEmail')}</span></div>
          </>
        )}

        <form onSubmit={submit}>
          {mode === 'register' && (
            <label>
              {t('auth', 'publicName')}
              <input style={{ minHeight: 44 }} maxLength={80} autoComplete="name" value={displayName} onChange={event => setDisplayName(event.target.value)} />
            </label>
          )}
          <label>{t('auth', 'email')}<input required autoFocus style={{ minHeight: 44 }} type="email" autoComplete="email" value={email} onChange={event => setEmail(event.target.value)} /></label>
          <label>{t('auth', 'password')}<input required style={{ minHeight: 44 }} minLength={8} type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} value={password} onChange={event => setPassword(event.target.value)} /></label>
          {mode === 'register' && <label>{t('auth', 'confirmPassword')}<input required style={{ minHeight: 44 }} type="password" autoComplete="new-password" value={confirm} onChange={event => setConfirm(event.target.value)} /></label>}
          {error && <span className={styles.error} role="alert">{error}</span>}
          <button className={styles.primaryButton} disabled={locked} data-qa-auth-submit>
            {busy ? t('auth', 'verifying') : mode === 'login' ? t('auth', 'signIn') : t('auth', 'createAccount')}
          </button>
        </form>
      </section>
    </div>
  );
}

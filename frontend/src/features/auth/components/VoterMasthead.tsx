'use client';

import type { Session, SocialProvider, UserProfile } from '@/shared/api/types';
import type { SocialProviderId } from '@/shared/api/social-auth-api';
import styles from './VoterMasthead.module.scss';

interface VoterMastheadProps {
  query: string;
  session: Session | null;
  profile: UserProfile | null;
  restoring: boolean;
  linkingProvider?: SocialProviderId | null;
  onQueryChange: (query: string) => void;
  onLogin: () => void;
  onRegister: () => void;
  onCreate: () => void;
  onLinkProvider: (provider: SocialProviderId) => void;
  onLogout: () => void;
  onLogoutAll: () => void;
}

export function VoterMasthead({
  query,
  session,
  profile,
  restoring,
  linkingProvider = null,
  onQueryChange,
  onLogin,
  onRegister,
  onCreate,
  onLinkProvider,
  onLogout,
  onLogoutAll
}: VoterMastheadProps) {
  const authenticated = Boolean(session && profile);
  const linkedProviders = new Set<SocialProvider>(profile?.linkedProviders ?? []);

  return (
    <header className={styles.header}>
      <a className={styles.brand} href="#top" aria-label="Vote System home">
        <span className={styles.seal}>VS</span>
        <span>Vote System</span>
      </a>

      <label className={styles.search}>
        <span>SEARCH PUBLIC REGISTRY</span>
        <input
          type="search"
          maxLength={200}
          value={query}
          onChange={event => onQueryChange(event.target.value)}
          placeholder="Title, content or ballot number..."
        />
      </label>

      <div className={styles.actions}>
        {restoring && !authenticated && <span className={styles.restoring} role="status">RESTORING ID...</span>}

        {!restoring && !authenticated && (
          <div className={styles.guestActions} data-qa-guest-actions>
            <button type="button" className={styles.textButton} onClick={onLogin}>SIGN IN</button>
            <button type="button" className={styles.textButton} onClick={onRegister}>REGISTER</button>
          </div>
        )}

        <button type="button" className={styles.primaryButton} onClick={onCreate} data-qa-create-ballot>
          CREATE BALLOT
        </button>

        {authenticated && profile && (
          <details className={styles.voterMenu} data-qa-voter-id>
            <summary aria-label={`Open voter ID menu for ${profile.displayName}`}>
              <span className={styles.initials} aria-hidden="true">{profile.initials}</span>
              <span className={styles.identityCopy}>
                <strong>{profile.displayName}</strong>
                <span>{profile.role} · SIGNED IN</span>
              </span>
              <span className={styles.chevron} aria-hidden="true">⌄</span>
            </summary>
            <div className={styles.menuPanel}>
              <p>OFFICIAL VOTER ID</p>
              <strong>{profile.displayName}</strong>
              <span className={styles.email}>{profile.email ?? 'EMAIL NOT SHARED'}</span>
              <span>ROLE: {profile.role}</span>
              <span>STATUS: VERIFIED SESSION</span>
              <div className={styles.menuActions} aria-label="Connected login providers">
                {linkedProviders.has('GOOGLE')
                  ? <span>GOOGLE: LINKED</span>
                  : <button type="button" disabled={linkingProvider !== null} onClick={() => onLinkProvider('google')}>{linkingProvider === 'google' ? 'CONNECTING GOOGLE...' : 'LINK GOOGLE'}</button>}
                {linkedProviders.has('GITHUB')
                  ? <span>GITHUB: LINKED</span>
                  : <button type="button" disabled={linkingProvider !== null} onClick={() => onLinkProvider('github')}>{linkingProvider === 'github' ? 'CONNECTING GITHUB...' : 'LINK GITHUB'}</button>}
                <button type="button" onClick={onLogout}>LOG OUT</button>
                <button type="button" onClick={onLogoutAll}>LOG OUT ALL DEVICES</button>
              </div>
            </div>
          </details>
        )}
      </div>
    </header>
  );
}

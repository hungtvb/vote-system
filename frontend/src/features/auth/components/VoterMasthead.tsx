'use client';

import type { Session, UserProfile } from '@/shared/api/types';
import styles from './VoterMasthead.module.scss';

interface VoterMastheadProps {
  query: string;
  session: Session | null;
  profile: UserProfile | null;
  restoring: boolean;
  onQueryChange: (query: string) => void;
  onLogin: () => void;
  onRegister: () => void;
  onCreate: () => void;
  onLogout: () => void;
  onLogoutAll: () => void;
}

export function VoterMasthead({
  query,
  session,
  profile,
  restoring,
  onQueryChange,
  onLogin,
  onRegister,
  onCreate,
  onLogout,
  onLogoutAll
}: VoterMastheadProps) {
  const authenticated = Boolean(session && profile);

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

        <button type="button" className={styles.primaryButton} onClick={onCreate}>
          {authenticated ? 'CREATE BALLOT' : 'START A BALLOT'}
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
              <span className={styles.email}>{profile.email}</span>
              <span>ROLE: {profile.role}</span>
              <span>STATUS: VERIFIED SESSION</span>
              <div className={styles.menuActions}>
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

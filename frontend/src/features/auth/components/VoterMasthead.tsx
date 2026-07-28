'use client';

import { useEffect, useState } from 'react';
import { BallotMark } from '@/features/profile/components/BallotMark';
import { ProfileDialog } from '@/features/profile/components/ProfileDialog';
import { updateActiveUserProfile } from '@/features/auth/hooks/useSession';
import type { Session, SocialProvider, UpdateUserProfileRequest, UserProfile } from '@/shared/api/types';
import type { SocialProviderId } from '@/shared/api/social-auth-api';
import { useI18n } from '@/shared/i18n/I18nProvider';
import localeStyles from '@/shared/i18n/LanguageSwitcher.module.scss';
import styles from './VoterMasthead.module.scss';

interface VoterMastheadProps {
  query: string;
  session: Session | null;
  profile: UserProfile | null;
  restoring: boolean;
  socialProviders?: SocialProviderId[];
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
  socialProviders = [],
  linkingProvider = null,
  onQueryChange,
  onLogin,
  onRegister,
  onCreate,
  onLinkProvider,
  onLogout,
  onLogoutAll
}: VoterMastheadProps) {
  const { locale, setLocale, t } = useI18n();
  const [visibleProfile, setVisibleProfile] = useState(profile);
  const [profileOpen, setProfileOpen] = useState(false);

  useEffect(() => {
    setVisibleProfile(profile);
    if (!profile) setProfileOpen(false);
  }, [profile]);

  const authenticated = Boolean(session && visibleProfile);
  const linkedProviders = new Set<SocialProvider>(visibleProfile?.linkedProviders ?? []);
  const googleEnabled = socialProviders.includes('google');
  const githubEnabled = socialProviders.includes('github');

  async function saveProfile(payload: UpdateUserProfileRequest): Promise<UserProfile> {
    const updated = await updateActiveUserProfile(payload);
    setVisibleProfile(updated);
    setLocale(updated.preferredLocale);
    return updated;
  }

  return (
    <>
      <header className={styles.header}>
        <a className={styles.brand} href="#top" aria-label="Vote System home">
          <span className={styles.seal}>VS</span>
          <span>Vote System</span>
        </a>

        <label className={styles.search}>
          <span>{t('ballots', 'searchRegistry')}</span>
          <input
            type="search"
            maxLength={200}
            value={query}
            onChange={event => onQueryChange(event.target.value)}
            placeholder={t('ballots', 'searchPlaceholder')}
          />
        </label>

        <div className={styles.actions}>
          <div className={localeStyles.switcher} role="group" aria-label={t('common', 'language')}>
            <button type="button" aria-pressed={locale === 'vi'} title={t('common', 'vietnamese')} onClick={() => setLocale('vi')}>VI</button>
            <button type="button" aria-pressed={locale === 'en'} title={t('common', 'english')} onClick={() => setLocale('en')}>EN</button>
          </div>

          {restoring && !authenticated && <span className={styles.restoring} role="status">{t('auth', 'restoring')}</span>}

          {!restoring && !authenticated && (
            <div className={styles.guestActions} data-qa-guest-actions>
              <button type="button" className={styles.textButton} onClick={onLogin}>{t('auth', 'signIn')}</button>
              <button type="button" className={styles.textButton} onClick={onRegister}>{t('auth', 'register')}</button>
            </div>
          )}

          <button type="button" className={styles.primaryButton} onClick={onCreate} data-qa-create-ballot>
            {t('ballots', 'createBallot')}
          </button>

          {authenticated && visibleProfile && (
            <details className={styles.voterMenu} data-qa-voter-id>
              <summary aria-label={t('auth', 'openVoterMenu', { name: visibleProfile.displayName })}>
                <BallotMark icon={visibleProfile.avatarIcon ?? 'CITIZEN'} color={visibleProfile.avatarColor ?? 'NAVY'} size="small" />
                <span className={styles.identityCopy}>
                  <strong>{visibleProfile.displayName}</strong>
                  <span>{visibleProfile.role} · {t('auth', 'signedIn')}</span>
                </span>
                <span className={styles.chevron} aria-hidden="true">⌄</span>
              </summary>
              <div className={styles.menuPanel}>
                <p>{t('auth', 'officialVoterId')}</p>
                <strong>{visibleProfile.displayName}</strong>
                <span className={styles.email}>{visibleProfile.email ?? t('auth', 'emailNotShared')}</span>
                <span>{t('auth', 'role', { role: visibleProfile.role })}</span>
                <span>{t('auth', 'verifiedSession')}</span>
                <div className={styles.menuActions} aria-label={t('auth', 'connectedProviders')}>
                  <button
                    type="button"
                    onClick={event => {
                      const details = event.currentTarget.closest('details');
                      if (details) details.open = false;
                      setProfileOpen(true);
                    }}
                  >
                    {t('profile', 'editProfile')}
                  </button>
                  {linkedProviders.has('GOOGLE')
                    ? <span>{t('auth', 'googleLinked')}</span>
                    : googleEnabled && <button type="button" disabled={linkingProvider !== null} onClick={() => onLinkProvider('google')}>{linkingProvider === 'google' ? t('auth', 'connectingGoogle') : t('auth', 'linkGoogle')}</button>}
                  {linkedProviders.has('GITHUB')
                    ? <span>{t('auth', 'githubLinked')}</span>
                    : githubEnabled && <button type="button" disabled={linkingProvider !== null} onClick={() => onLinkProvider('github')}>{linkingProvider === 'github' ? t('auth', 'connectingGithub') : t('auth', 'linkGithub')}</button>}
                  <button type="button" onClick={onLogout}>{t('auth', 'logout')}</button>
                  <button type="button" onClick={onLogoutAll}>{t('auth', 'logoutAll')}</button>
                </div>
              </div>
            </details>
          )}
        </div>
      </header>

      {profileOpen && visibleProfile && (
        <ProfileDialog
          profile={visibleProfile}
          onClose={() => setProfileOpen(false)}
          onSave={saveProfile}
        />
      )}
    </>
  );
}

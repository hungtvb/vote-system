'use client';

import { useState } from 'react';
import type { FormEvent } from 'react';
import { useModalDialog } from '@/shared/hooks/useModalDialog';
import { useI18n } from '@/shared/i18n/I18nProvider';
import type {
  AvatarColor,
  AvatarIcon,
  PreferredLocale,
  SocialProvider,
  UpdateUserProfileRequest,
  UserProfile
} from '@/shared/api/types';
import { BallotMark } from './BallotMark';
import styles from './ProfileDialog.module.scss';

const AVATAR_ICONS: AvatarIcon[] = [
  'CITIZEN',
  'ADVOCATE',
  'THINKER',
  'ORGANIZER',
  'VOLUNTEER',
  'CREATOR',
  'LEADER',
  'ANALYST',
  'VISIONARY',
  'BUILDER'
];

const AVATAR_COLORS: AvatarColor[] = ['NAVY', 'SEAL', 'KRAFT', 'GRAPHITE', 'MOSS', 'INK_BLUE'];

interface ProfileDialogProps {
  profile: UserProfile;
  onClose: () => void;
  onSave: (payload: UpdateUserProfileRequest) => Promise<UserProfile>;
}

export function ProfileDialog({ profile, onClose, onSave }: ProfileDialogProps) {
  const { formatNumber, locale, t } = useI18n();
  const [displayName, setDisplayName] = useState(profile.displayName);
  const [bio, setBio] = useState(profile.bio ?? '');
  const [avatarIcon, setAvatarIcon] = useState<AvatarIcon>(profile.avatarIcon ?? 'CITIZEN');
  const [avatarColor, setAvatarColor] = useState<AvatarColor>(profile.avatarColor ?? 'NAVY');
  const [preferredLocale, setPreferredLocale] = useState<PreferredLocale>(profile.preferredLocale ?? locale);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const modal = useModalDialog(onClose);
  const linkedProviders = new Set<SocialProvider>(profile.linkedProviders ?? []);
  const normalizedName = displayName.trim().replace(/\s+/g, ' ');
  const validName = normalizedName.length >= 2 && normalizedName.length <= 40;

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!validName || busy) return;
    setBusy(true);
    setError('');
    try {
      await onSave({
        displayName: normalizedName,
        bio: bio.trim() || null,
        avatarIcon,
        avatarColor,
        preferredLocale
      });
      onClose();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t('profile', 'saveFailed'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={styles.backdrop} onMouseDown={modal.onBackdropMouseDown}>
      <section
        ref={modal.dialogRef}
        tabIndex={-1}
        className={styles.dialog}
        role="dialog"
        aria-modal="true"
        aria-labelledby="profile-dialog-title"
        onKeyDown={modal.onDialogKeyDown}
        data-qa-profile-dialog
      >
        <header className={styles.header}>
          <div>
            <p>{t('profile', 'formCode')}</p>
            <h2 id="profile-dialog-title">{t('profile', 'title')}</h2>
          </div>
          <button type="button" className={styles.closeButton} onClick={onClose} aria-label={t('common', 'close')}>×</button>
        </header>

        <form onSubmit={submit}>
          <section className={styles.preview} aria-label={t('profile', 'preview')}>
            <BallotMark
              icon={avatarIcon}
              color={avatarColor}
              size="large"
              label={t('profile', 'avatarLabel', { name: normalizedName || profile.displayName })}
            />
            <div>
              <strong>{normalizedName || profile.displayName}</strong>
              <span>{bio.trim() || t('profile', 'bioFallback')}</span>
            </div>
          </section>

          <fieldset className={styles.picker}>
            <legend>{t('profile', 'chooseIcon')}</legend>
            <div className={styles.iconGrid}>
              {AVATAR_ICONS.map(icon => (
                <button
                  key={icon}
                  type="button"
                  className={styles.iconChoice}
                  aria-pressed={avatarIcon === icon}
                  aria-label={t('profile', `icon${toPascal(icon)}` as never)}
                  title={t('profile', `icon${toPascal(icon)}` as never)}
                  onClick={() => setAvatarIcon(icon)}
                >
                  <BallotMark icon={icon} color={avatarColor} size="medium" />
                </button>
              ))}
            </div>
          </fieldset>

          <fieldset className={styles.picker}>
            <legend>{t('profile', 'chooseColor')}</legend>
            <div className={styles.colorGrid}>
              {AVATAR_COLORS.map(color => (
                <button
                  key={color}
                  type="button"
                  className={styles.colorChoice}
                  data-color={color}
                  aria-pressed={avatarColor === color}
                  aria-label={t('profile', `color${toPascal(color)}` as never)}
                  title={t('profile', `color${toPascal(color)}` as never)}
                  onClick={() => setAvatarColor(color)}
                >
                  <span aria-hidden="true" />
                </button>
              ))}
            </div>
          </fieldset>

          <label className={styles.field}>
            <span>{t('profile', 'displayName')}</span>
            <input
              autoFocus
              required
              minLength={2}
              maxLength={40}
              value={displayName}
              onChange={event => setDisplayName(event.target.value)}
            />
            <small>{formatNumber(displayName.length)} / {formatNumber(40)}</small>
          </label>

          <label className={styles.field}>
            <span>{t('profile', 'bio')}</span>
            <textarea maxLength={160} rows={4} value={bio} onChange={event => setBio(event.target.value)} />
            <small>{formatNumber(bio.length)} / {formatNumber(160)}</small>
          </label>

          <fieldset className={styles.localePicker}>
            <legend>{t('profile', 'preferredLanguage')}</legend>
            <button type="button" aria-pressed={preferredLocale === 'vi'} onClick={() => setPreferredLocale('vi')}>VI · {t('common', 'vietnamese')}</button>
            <button type="button" aria-pressed={preferredLocale === 'en'} onClick={() => setPreferredLocale('en')}>EN · {t('common', 'english')}</button>
          </fieldset>

          <section className={styles.providers} aria-label={t('profile', 'linkedAccounts')}>
            <strong>{t('profile', 'linkedAccounts')}</strong>
            <span>Google · {linkedProviders.has('GOOGLE') ? t('profile', 'linked') : t('profile', 'notLinked')}</span>
            <span>GitHub · {linkedProviders.has('GITHUB') ? t('profile', 'linked') : t('profile', 'notLinked')}</span>
          </section>

          {error && <div className={styles.error} role="alert">{error}</div>}

          <div className={styles.actions}>
            <button type="button" className={styles.secondaryButton} onClick={onClose}>{t('common', 'cancel')}</button>
            <button type="submit" className={styles.primaryButton} disabled={busy || !validName}>
              {busy ? t('profile', 'saving') : t('profile', 'save')}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}

function toPascal(value: string): string {
  return value.toLowerCase().split('_').map(part => part.charAt(0).toUpperCase() + part.slice(1)).join('');
}

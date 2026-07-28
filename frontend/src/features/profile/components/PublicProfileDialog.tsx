'use client';

import { useEffect, useState } from 'react';
import { userApi } from '@/shared/api/user-api';
import type { PublicUserProfile } from '@/shared/api/types';
import { useModalDialog } from '@/shared/hooks/useModalDialog';
import { useI18n } from '@/shared/i18n/I18nProvider';
import { BallotMark } from './BallotMark';
import styles from './PublicProfileDialog.module.scss';

interface PublicProfileDialogProps {
  userId: string;
  initialProfile?: PublicUserProfile;
  onClose: () => void;
}

export function PublicProfileDialog({ userId, initialProfile, onClose }: PublicProfileDialogProps) {
  const { formatDate, t } = useI18n();
  const [profile, setProfile] = useState<PublicUserProfile | null>(initialProfile ?? null);
  const [loading, setLoading] = useState(!initialProfile);
  const [error, setError] = useState('');
  const modal = useModalDialog(onClose);
  const bio = profile?.bio?.trim();

  useEffect(() => {
    if (initialProfile) return;
    let active = true;
    setLoading(true);
    setError('');
    void userApi.publicProfile(userId)
      .then(response => {
        if (active) setProfile(response);
      })
      .catch(caught => {
        if (active) setError(caught instanceof Error ? caught.message : t('errors', 'unknown'));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [initialProfile, t, userId]);

  return (
    <div className={styles.backdrop} onMouseDown={modal.onBackdropMouseDown}>
      <section
        ref={modal.dialogRef}
        tabIndex={-1}
        className={styles.dialog}
        role="dialog"
        aria-modal="true"
        aria-labelledby="public-profile-title"
        onKeyDown={modal.onDialogKeyDown}
        data-qa-public-profile-dialog
      >
        <button type="button" className={styles.closeButton} onClick={onClose} aria-label={t('common', 'close')}>×</button>
        <p className={styles.eyebrow}>{t('ballots', 'publicAuthorRecord')}</p>

        {loading && <p className={styles.state} role="status">{t('common', 'loading')}</p>}
        {error && <p className={styles.error} role="alert">{error}</p>}

        {profile && (
          <div className={styles.content}>
            <BallotMark
              icon={profile.avatarIcon ?? 'CITIZEN'}
              color={profile.avatarColor ?? 'NAVY'}
              size="large"
              label={t('profile', 'avatarLabel', { name: profile.displayName })}
            />
            <h2 id="public-profile-title">{profile.displayName}</h2>
            {bio && <p className={styles.bio}>{bio}</p>}
            <time dateTime={profile.createdAt}>
              {formatDate(profile.createdAt, { dateStyle: 'long' })}
            </time>
          </div>
        )}
      </section>
    </div>
  );
}

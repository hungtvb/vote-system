'use client';

import type { PublicSystemStatus } from '@/shared/api/system-status-api';
import { useI18n } from '@/shared/i18n/I18nProvider';
import { localizedSystemMessage } from '@/shared/system/system-mode';
import localeStyles from '@/shared/i18n/LanguageSwitcher.module.scss';
import styles from './SystemModeNotice.module.scss';

interface ReadOnlyBannerProps {
  status: PublicSystemStatus;
}

export function ReadOnlyBanner({ status }: ReadOnlyBannerProps) {
  const { formatDate, locale, t } = useI18n();
  const message = localizedSystemMessage(status, locale);

  return (
    <aside className={styles.readOnlyBanner} role="status" aria-live="polite" data-qa-system-mode="READ_ONLY">
      <div className={styles.bannerStamp} aria-hidden="true">{t('system', 'readOnlyStamp')}</div>
      <div className={styles.bannerCopy}>
        <strong>{t('system', 'readOnlyTitle')}</strong>
        <span>{message ?? t('system', 'readOnlyDescription')}</span>
        {status.estimatedEndAt && (
          <small>{t('system', 'estimatedRestoration', {
            time: formatDate(status.estimatedEndAt, { dateStyle: 'medium', timeStyle: 'short' })
          })}</small>
        )}
      </div>
    </aside>
  );
}

interface MaintenanceScreenProps {
  status: PublicSystemStatus;
  refreshing: boolean;
  onRetry: () => void;
}

export function MaintenanceScreen({ status, refreshing, onRetry }: MaintenanceScreenProps) {
  const { formatDate, locale, setLocale, t } = useI18n();
  const message = localizedSystemMessage(status, locale);

  return (
    <main className={styles.maintenancePage} data-qa-system-mode="MAINTENANCE">
      <header className={styles.maintenanceHeader}>
        <a className={styles.brand} href="/" aria-label="Vote System home">
          <span className={styles.seal}>VS</span>
          <span>Vote System</span>
        </a>
        <div className={localeStyles.switcher} role="group" aria-label={t('common', 'language')}>
          <button type="button" aria-pressed={locale === 'vi'} title={t('common', 'vietnamese')} onClick={() => setLocale('vi')}>VI</button>
          <button type="button" aria-pressed={locale === 'en'} title={t('common', 'english')} onClick={() => setLocale('en')}>EN</button>
        </div>
      </header>

      <section className={styles.maintenanceCard} role="status" aria-live="polite" aria-busy={refreshing}>
        <p className={styles.eyebrow}>{t('system', 'maintenanceEyebrow')}</p>
        <div className={styles.maintenanceStamp} aria-hidden="true">{t('system', 'maintenanceStamp')}</div>
        <h1>{t('system', 'maintenanceTitle')}</h1>
        <p className={styles.description}>{message ?? t('system', 'maintenanceDescription')}</p>

        {status.estimatedEndAt && (
          <div className={styles.restorationTime}>
            <span>{t('system', 'estimatedRestorationLabel')}</span>
            <strong>{formatDate(status.estimatedEndAt, { dateStyle: 'full', timeStyle: 'short' })}</strong>
          </div>
        )}

        <button type="button" className={styles.retryButton} data-qa-system-retry onClick={onRetry} disabled={refreshing}>
          {refreshing ? t('system', 'checkingStatus') : t('system', 'retryStatus')}
        </button>
        <small>{t('system', 'maintenanceFootnote')}</small>
      </section>
    </main>
  );
}

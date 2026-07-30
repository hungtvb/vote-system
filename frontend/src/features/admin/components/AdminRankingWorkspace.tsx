'use client';

import { useEffect } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useSession } from '@/features/auth/hooks/useSession';
import type { AdminSection } from '@/shared/api/admin-api';
import { useI18n } from '@/shared/i18n/I18nProvider';
import { AdminRankingPanel } from './AdminRankingPanel';
import styles from './AdminWorkspace.module.scss';

const SECTIONS: AdminSection[] = ['overview', 'ballots', 'users', 'audit', 'ranking'];

export function AdminRankingWorkspace() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const { locale, setLocale, t } = useI18n();
  const { session, profile, restoring, logout } = useSession();
  const isAdmin = Boolean(session && profile?.role === 'ADMIN');
  const copy = COPY[locale];

  useEffect(() => {
    if (restoring || session) return;
    router.replace('/?admin=signin');
  }, [restoring, router, session]);

  function selectSection(section: AdminSection) {
    const next = new URLSearchParams(searchParams.toString());
    if (section === 'overview') next.delete('section');
    else next.set('section', section);
    next.delete('page');
    next.delete('q');
    next.delete('state');
    const serialized = next.toString();
    router.replace(serialized ? `${pathname}?${serialized}` : pathname, { scroll: false });
  }

  if (restoring || (!session && !profile)) {
    return <AdminGate title={t('admin', 'restoring')} description={t('admin', 'restoringDescription')} />;
  }

  if (!isAdmin) {
    return (
      <AdminGate title={t('admin', 'accessDenied')} description={t('admin', 'accessDeniedDescription')}>
        <a className={styles.primaryButton} href="/">{t('admin', 'returnRegistry')}</a>
      </AdminGate>
    );
  }

  return (
    <main className={styles.workspace}>
      <header className={styles.topbar}>
        <a className={styles.brand} href="/">
          <span className={styles.seal}>VS</span>
          <span><strong>Vote System</strong><small>{t('admin', 'workspace')}</small></span>
        </a>
        <div className={styles.topActions}>
          <div className={styles.localeSwitch} role="group" aria-label={t('common', 'language')}>
            <button type="button" aria-pressed={locale === 'vi'} onClick={() => setLocale('vi')}>VI</button>
            <button type="button" aria-pressed={locale === 'en'} onClick={() => setLocale('en')}>EN</button>
          </div>
          <span className={styles.adminIdentity}>{profile?.displayName}</span>
          <button type="button" className={styles.textButton} onClick={() => void logout().then(() => router.replace('/'))}>{t('auth', 'logout')}</button>
        </div>
      </header>

      <div className={styles.shell}>
        <aside className={styles.sidebar} aria-label={t('admin', 'navigation')}>
          <div className={styles.sidebarHeading}>
            <span>{t('admin', 'controlDesk')}</span>
            <strong>{t('admin', 'moderationRegistry')}</strong>
          </div>
          <nav className={styles.navList}>
            {SECTIONS.map(section => (
              <button
                key={section}
                type="button"
                aria-current={section === 'ranking' ? 'page' : undefined}
                onClick={() => selectSection(section)}
              >
                <span aria-hidden="true">{sectionIcon(section)}</span>
                <span>{t('admin', section)}</span>
              </button>
            ))}
            <button type="button" disabled title={t('admin', 'comingLater')}>
              <span aria-hidden="true">◇</span>
              <span>{t('admin', 'systemOperations')}</span>
            </button>
          </nav>
          <p className={styles.sidebarNote}>{t('admin', 'backendAuthority')}</p>
        </aside>

        <section className={styles.content}>
          <div className={styles.pageHeader}>
            <div>
              <span className={styles.eyebrow}>{t('admin', 'officialOperations')}</span>
              <h1>{copy.title}</h1>
              <p>{copy.description}</p>
            </div>
          </div>
          <AdminRankingPanel />
        </section>
      </div>
    </main>
  );
}

function AdminGate({ title, description, children }: { title: string; description: string; children?: React.ReactNode }) {
  return (
    <main className={styles.gate}>
      <div className={styles.gateCard}>
        <span className={styles.seal}>VS</span>
        <span className={styles.eyebrow}>PROTECTED ADMIN WORKSPACE</span>
        <h1>{title}</h1>
        <p>{description}</p>
        {children}
      </div>
    </main>
  );
}

function sectionIcon(section: AdminSection): string {
  return ({ overview: '▦', ballots: '▤', users: '◎', audit: '≡', ranking: '↗' })[section];
}

const COPY = {
  vi: {
    title: 'Vận hành xếp hạng',
    description: 'Theo dõi thế hệ Redis đang phát hành, so sánh với PostgreSQL và rebuild nguyên tử có kiểm toán.'
  },
  en: {
    title: 'Ranking operations',
    description: 'Inspect the published Redis generation, compare it with PostgreSQL, and run an audited atomic rebuild.'
  }
} as const;

'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useSession } from '@/features/auth/hooks/useSession';
import {
  adminApi,
  type AccountStatus,
  type AdminAuditLog,
  type AdminPage,
  type AdminPost,
  type AdminSection,
  type AdminUser,
  type ModerationStatus
} from '@/shared/api/admin-api';
import { ApiError } from '@/shared/api/transport';
import { useI18n } from '@/shared/i18n/I18nProvider';
import { AdminActionDialog } from './AdminActionDialog';
import styles from './AdminWorkspace.module.scss';

const PAGE_SIZE = 12;
const SECTIONS: AdminSection[] = ['overview', 'ballots', 'users', 'audit', 'ranking'];

type PendingAction =
  | { kind: 'hide-post' | 'restore-post' | 'delete-post'; target: AdminPost }
  | { kind: 'suspend-user' | 'ban-user' | 'restore-user' | 'revoke-user-sessions'; target: AdminUser };

interface OverviewState {
  users?: number;
  ballots?: number;
  audit?: number;
  unavailable: boolean;
}

export function AdminWorkspace() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const { locale, setLocale, t, formatDate, formatNumber } = useI18n();
  const { session, profile, restoring, runAuthorized, logout } = useSession();

  const section = resolveSection(searchParams.get('section'));
  const page = parsePage(searchParams.get('page'));
  const query = searchParams.get('q')?.slice(0, 200) ?? '';
  const stateFilter = searchParams.get('state') ?? '';
  const [queryInput, setQueryInput] = useState(query);
  const [overview, setOverview] = useState<OverviewState>({ unavailable: false });
  const [users, setUsers] = useState<AdminPage<AdminUser> | null>(null);
  const [posts, setPosts] = useState<AdminPage<AdminPost> | null>(null);
  const [audit, setAudit] = useState<AdminPage<AdminAuditLog> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [pendingAction, setPendingAction] = useState<PendingAction | null>(null);
  const [mutationBusy, setMutationBusy] = useState(false);
  const [mutationError, setMutationError] = useState('');
  const isAdmin = Boolean(session && profile?.role === 'ADMIN');

  useEffect(() => setQueryInput(query), [query]);

  useEffect(() => {
    if (restoring || session) return;
    router.replace('/?admin=signin');
  }, [restoring, router, session]);

  const updateUrl = useCallback((changes: Record<string, string | number | null>) => {
    const next = new URLSearchParams(searchParams.toString());
    for (const [key, value] of Object.entries(changes)) {
      if (value === null || value === '') next.delete(key);
      else next.set(key, String(value));
    }
    const serialized = next.toString();
    router.replace(serialized ? `${pathname}?${serialized}` : pathname, { scroll: false });
  }, [pathname, router, searchParams]);

  const loadCurrentSection = useCallback(async (signal?: AbortSignal) => {
    if (!isAdmin) return;
    setLoading(true);
    setError('');
    try {
      if (section === 'overview') {
        const [userPage, postPage, auditPage] = await Promise.all([
          runAuthorized(active => adminApi.users({ page: 0, size: 1 }, active.accessToken, signal)),
          runAuthorized(active => adminApi.posts({ page: 0, size: 1 }, active.accessToken, signal)),
          runAuthorized(active => adminApi.auditLogs({ page: 0, size: 1 }, active.accessToken, signal))
        ]);
        setOverview({
          users: userPage.totalElements,
          ballots: postPage.totalElements,
          audit: auditPage.totalElements,
          unavailable: false
        });
      } else if (section === 'users') {
        const accountStatus = isAccountStatus(stateFilter) ? stateFilter : undefined;
        setUsers(await runAuthorized(active => adminApi.users({
          query: query || undefined,
          accountStatus,
          page,
          size: PAGE_SIZE
        }, active.accessToken, signal)));
      } else if (section === 'ballots') {
        const moderationStatus = isModerationStatus(stateFilter) ? stateFilter : undefined;
        setPosts(await runAuthorized(active => adminApi.posts({
          query: query || undefined,
          moderationStatus,
          page,
          size: PAGE_SIZE
        }, active.accessToken, signal)));
      } else if (section === 'audit') {
        setAudit(await runAuthorized(active => adminApi.auditLogs({
          action: query || undefined,
          targetType: stateFilter === 'POST' || stateFilter === 'USER' || stateFilter === 'RANKING'
            ? stateFilter
            : undefined,
          page,
          size: PAGE_SIZE
        }, active.accessToken, signal)));
      }
    } catch (cause) {
      if (cause instanceof DOMException && cause.name === 'AbortError') return;
      if (section === 'overview') setOverview(current => ({ ...current, unavailable: true }));
      setError(safeError(cause, t('admin', 'loadFailed')));
    } finally {
      setLoading(false);
    }
  }, [isAdmin, page, query, runAuthorized, section, stateFilter, t]);

  useEffect(() => {
    if (!isAdmin || section === 'ranking') return;
    const controller = new AbortController();
    void loadCurrentSection(controller.signal);
    return () => controller.abort();
  }, [isAdmin, loadCurrentSection, section]);

  const activePage = section === 'users' ? users : section === 'ballots' ? posts : section === 'audit' ? audit : null;
  const actionCopy = useMemo(() => pendingAction ? resolveActionCopy(pendingAction, t) : null, [pendingAction, t]);

  function selectSection(nextSection: AdminSection) {
    setNotice('');
    setError('');
    updateUrl({ section: nextSection === 'overview' ? null : nextSection, page: null, q: null, state: null });
  }

  function submitFilters(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    updateUrl({ q: queryInput.trim() || null, page: null });
  }

  async function performAction(reason: string, until: string | null) {
    if (!pendingAction) return;
    setMutationBusy(true);
    setMutationError('');
    try {
      await runAuthorized(active => {
        const token = active.accessToken;
        switch (pendingAction.kind) {
          case 'hide-post': return adminApi.hidePost(pendingAction.target.id, reason, token);
          case 'restore-post': return adminApi.restorePost(pendingAction.target.id, reason, token);
          case 'delete-post': return adminApi.deletePost(pendingAction.target.id, reason, token);
          case 'suspend-user': return adminApi.suspendUser(pendingAction.target.id, reason, until, token);
          case 'ban-user': return adminApi.banUser(pendingAction.target.id, reason, until, token);
          case 'restore-user': return adminApi.restoreUser(pendingAction.target.id, reason, token);
          case 'revoke-user-sessions': return adminApi.revokeUserSessions(pendingAction.target.id, reason, token);
        }
      });
      setPendingAction(null);
      setNotice(t('admin', 'actionCompleted'));
      await loadCurrentSection();
    } catch (cause) {
      setMutationError(safeError(cause, t('admin', 'actionFailed')));
    } finally {
      setMutationBusy(false);
    }
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
            {SECTIONS.map(item => (
              <button
                key={item}
                type="button"
                aria-current={section === item ? 'page' : undefined}
                onClick={() => selectSection(item)}
              >
                <span aria-hidden="true">{sectionIcon(item)}</span>
                <span>{t('admin', item)}</span>
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
              <h1>{t('admin', `${section}Title`)}</h1>
              <p>{t('admin', `${section}Description`)}</p>
            </div>
            {section !== 'overview' && section !== 'ranking' && (
              <button type="button" className={styles.secondaryButton} disabled={loading} onClick={() => void loadCurrentSection()}>
                {loading ? t('admin', 'refreshing') : t('admin', 'refresh')}
              </button>
            )}
          </div>

          {notice && <p className={styles.successNotice} role="status">{notice}</p>}
          {error && <p className={styles.errorNotice} role="alert">{error}</p>}

          {section === 'overview' && <Overview overview={overview} loading={loading} formatNumber={formatNumber} t={t} />}
          {section === 'users' && (
            <>
              <FilterBar
                query={queryInput}
                state={stateFilter}
                placeholder={t('admin', 'userSearchPlaceholder')}
                stateLabel={t('admin', 'accountState')}
                options={['ACTIVE', 'SUSPENDED', 'BANNED']}
                onQuery={setQueryInput}
                onState={value => updateUrl({ state: value || null, page: null })}
                onSubmit={submitFilters}
                t={t}
              />
              <UsersList
                page={users}
                loading={loading}
                currentUserId={profile?.id ?? ''}
                formatDate={formatDate}
                onAction={setPendingAction}
                t={t}
              />
            </>
          )}
          {section === 'ballots' && (
            <>
              <FilterBar
                query={queryInput}
                state={stateFilter}
                placeholder={t('admin', 'ballotSearchPlaceholder')}
                stateLabel={t('admin', 'moderationState')}
                options={['VISIBLE', 'HIDDEN', 'DELETED']}
                onQuery={setQueryInput}
                onState={value => updateUrl({ state: value || null, page: null })}
                onSubmit={submitFilters}
                t={t}
              />
              <PostsList page={posts} loading={loading} formatDate={formatDate} formatNumber={formatNumber} onAction={setPendingAction} t={t} />
            </>
          )}
          {section === 'audit' && (
            <>
              <FilterBar
                query={queryInput}
                state={stateFilter}
                placeholder={t('admin', 'auditSearchPlaceholder')}
                stateLabel={t('admin', 'targetType')}
                options={['POST', 'USER', 'RANKING']}
                onQuery={setQueryInput}
                onState={value => updateUrl({ state: value || null, page: null })}
                onSubmit={submitFilters}
                t={t}
              />
              <AuditList page={audit} loading={loading} formatDate={formatDate} t={t} />
            </>
          )}
          {section === 'ranking' && <RankingPlaceholder t={t} />}

          {activePage && activePage.totalPages > 1 && (
            <Pagination
              page={activePage.page}
              totalPages={activePage.totalPages}
              onPage={nextPage => updateUrl({ page: nextPage })}
              t={t}
            />
          )}
        </section>
      </div>

      {pendingAction && actionCopy && (
        <AdminActionDialog
          {...actionCopy}
          busy={mutationBusy}
          error={mutationError}
          reasonLabel={t('admin', 'reason')}
          reasonPlaceholder={t('admin', 'reasonPlaceholder')}
          cancelLabel={t('common', 'cancel')}
          closeLabel={t('common', 'close')}
          busyLabel={t('admin', 'applying')}
          onClose={() => { if (!mutationBusy) { setPendingAction(null); setMutationError(''); } }}
          onConfirm={performAction}
        />
      )}
    </main>
  );
}

function Overview({ overview, loading, formatNumber, t }: {
  overview: OverviewState;
  loading: boolean;
  formatNumber: (value: number) => string;
  t: Translation;
}) {
  const cards = [
    ['registeredUsers', overview.users],
    ['registeredBallots', overview.ballots],
    ['auditEvents', overview.audit]
  ] as const;
  return (
    <>
      <div className={styles.metricGrid}>
        {cards.map(([label, value]) => (
          <article className={styles.metricCard} key={label}>
            <span>{t('admin', label)}</span>
            <strong>{loading ? '···' : value === undefined ? '—' : formatNumber(value)}</strong>
            <small>{value === undefined ? t('admin', 'unavailable') : t('admin', 'authoritativeRecord')}</small>
          </article>
        ))}
        <article className={`${styles.metricCard} ${styles.metricPending}`}>
          <span>{t('admin', 'rankingHealth')}</span>
          <strong>—</strong>
          <small>{t('admin', 'rankingPending')}</small>
        </article>
      </div>
      <section className={styles.callout}>
        <div>
          <span className={styles.eyebrow}>{t('admin', 'operationsNotice')}</span>
          <h2>{t('admin', 'overviewCalloutTitle')}</h2>
        </div>
        <p>{t('admin', 'overviewCalloutDescription')}</p>
      </section>
    </>
  );
}

function FilterBar({ query, state, placeholder, stateLabel, options, onQuery, onState, onSubmit, t }: {
  query: string;
  state: string;
  placeholder: string;
  stateLabel: string;
  options: string[];
  onQuery: (value: string) => void;
  onState: (value: string) => void;
  onSubmit: (event: React.FormEvent<HTMLFormElement>) => void;
  t: Translation;
}) {
  return (
    <form className={styles.filters} onSubmit={onSubmit}>
      <label className={styles.field}>
        <span>{t('admin', 'search')}</span>
        <input type="search" maxLength={200} value={query} placeholder={placeholder} onChange={event => onQuery(event.target.value)} />
      </label>
      <label className={styles.field}>
        <span>{stateLabel}</span>
        <select value={state} onChange={event => onState(event.target.value)}>
          <option value="">{t('admin', 'allStates')}</option>
          {options.map(option => <option key={option} value={option}>{option}</option>)}
        </select>
      </label>
      <button type="submit" className={styles.primaryButton}>{t('admin', 'applyFilters')}</button>
    </form>
  );
}

function UsersList({ page, loading, currentUserId, formatDate, onAction, t }: {
  page: AdminPage<AdminUser> | null;
  loading: boolean;
  currentUserId: string;
  formatDate: DateFormatter;
  onAction: (action: PendingAction) => void;
  t: Translation;
}) {
  if (loading && !page) return <LoadingPanel label={t('admin', 'loadingUsers')} />;
  if (!page?.content.length) return <EmptyPanel label={t('admin', 'noUsers')} />;
  return (
    <div className={styles.recordList}>
      {page.content.map(user => {
        const self = user.id === currentUserId;
        return (
          <article className={styles.recordCard} key={user.id}>
            <div className={styles.recordPrimary}>
              <span className={styles.recordCode}>{user.role} · {user.initials}</span>
              <h2>{user.displayName}</h2>
              <p className={styles.breakable}>{user.email}</p>
              <div className={styles.badges}>
                <StatusBadge value={user.accountStatus} />
                {user.linkedProviders.map(provider => <span key={provider}>{provider}</span>)}
              </div>
            </div>
            <dl className={styles.recordFacts}>
              <div><dt>{t('admin', 'created')}</dt><dd>{formatDate(user.createdAt, dateOptions)}</dd></div>
              <div><dt>{t('admin', 'restrictionUntil')}</dt><dd>{user.statusUntil ? formatDate(user.statusUntil, dateOptions) : '—'}</dd></div>
            </dl>
            <div className={styles.recordActions}>
              {user.accountStatus === 'ACTIVE' ? (
                <>
                  <button type="button" disabled={self} onClick={() => onAction({ kind: 'suspend-user', target: user })}>{t('admin', 'suspend')}</button>
                  <button type="button" disabled={self} className={styles.dangerText} onClick={() => onAction({ kind: 'ban-user', target: user })}>{t('admin', 'ban')}</button>
                  <button type="button" disabled={self} onClick={() => onAction({ kind: 'revoke-user-sessions', target: user })}>{t('admin', 'revokeSessions')}</button>
                </>
              ) : (
                <button type="button" onClick={() => onAction({ kind: 'restore-user', target: user })}>{t('admin', 'restore')}</button>
              )}
              {self && <small>{t('admin', 'currentAdmin')}</small>}
            </div>
          </article>
        );
      })}
    </div>
  );
}

function PostsList({ page, loading, formatDate, formatNumber, onAction, t }: {
  page: AdminPage<AdminPost> | null;
  loading: boolean;
  formatDate: DateFormatter;
  formatNumber: (value: number) => string;
  onAction: (action: PendingAction) => void;
  t: Translation;
}) {
  if (loading && !page) return <LoadingPanel label={t('admin', 'loadingBallots')} />;
  if (!page?.content.length) return <EmptyPanel label={t('admin', 'noBallots')} />;
  return (
    <div className={styles.recordList}>
      {page.content.map(post => (
        <article className={styles.recordCard} key={post.id}>
          <div className={styles.recordPrimary}>
            <span className={styles.recordCode}>{post.ballotNumber}</span>
            <h2>{post.title}</h2>
            <p>{post.author.displayName} · {post.category}</p>
            <div className={styles.badges}>
              <StatusBadge value={post.moderationStatus} />
              <span>{post.status}</span>
              <span>{formatNumber(post.totalVotes)} {t('admin', 'votes')}</span>
            </div>
          </div>
          <dl className={styles.recordFacts}>
            <div><dt>{t('admin', 'score')}</dt><dd>{formatNumber(post.voteScore)}</dd></div>
            <div><dt>{t('admin', 'created')}</dt><dd>{formatDate(post.createdAt, dateOptions)}</dd></div>
          </dl>
          <div className={styles.recordActions}>
            {post.moderationStatus === 'VISIBLE' && <button type="button" onClick={() => onAction({ kind: 'hide-post', target: post })}>{t('admin', 'hide')}</button>}
            {post.moderationStatus === 'HIDDEN' && <button type="button" onClick={() => onAction({ kind: 'restore-post', target: post })}>{t('admin', 'restore')}</button>}
            {post.moderationStatus !== 'DELETED' && <button type="button" className={styles.dangerText} onClick={() => onAction({ kind: 'delete-post', target: post })}>{t('admin', 'softDelete')}</button>}
            {post.moderationStatus === 'DELETED' && <small>{t('admin', 'terminalRecord')}</small>}
          </div>
        </article>
      ))}
    </div>
  );
}

function AuditList({ page, loading, formatDate, t }: {
  page: AdminPage<AdminAuditLog> | null;
  loading: boolean;
  formatDate: DateFormatter;
  t: Translation;
}) {
  if (loading && !page) return <LoadingPanel label={t('admin', 'loadingAudit')} />;
  if (!page?.content.length) return <EmptyPanel label={t('admin', 'noAudit')} />;
  return (
    <div className={styles.auditList}>
      {page.content.map(entry => (
        <article className={styles.auditCard} key={entry.id}>
          <div className={styles.auditHeader}>
            <div><span>{entry.action}</span><strong>{entry.targetType}</strong></div>
            <time>{formatDate(entry.createdAt, dateOptions)}</time>
          </div>
          <p>{entry.reason}</p>
          <dl>
            <div><dt>{t('admin', 'actor')}</dt><dd>{entry.actorId}</dd></div>
            <div><dt>{t('admin', 'target')}</dt><dd>{entry.targetId}</dd></div>
            {Object.entries(entry.metadata).map(([key, value]) => <div key={key}><dt>{key}</dt><dd>{value}</dd></div>)}
          </dl>
        </article>
      ))}
    </div>
  );
}

function RankingPlaceholder({ t }: { t: Translation }) {
  return (
    <section className={styles.rankingPlaceholder}>
      <span className={styles.pendingMark}>◇</span>
      <div>
        <span className={styles.eyebrow}>{t('admin', 'dependencyPending')}</span>
        <h2>{t('admin', 'rankingUnavailableTitle')}</h2>
        <p>{t('admin', 'rankingUnavailableDescription')}</p>
      </div>
    </section>
  );
}

function Pagination({ page, totalPages, onPage, t }: { page: number; totalPages: number; onPage: (page: number) => void; t: Translation }) {
  return (
    <nav className={styles.pagination} aria-label={t('admin', 'pagination')}>
      <button type="button" disabled={page === 0} onClick={() => onPage(page - 1)}>{t('admin', 'previous')}</button>
      <span>{t('admin', 'pageOf', { page: page + 1, total: totalPages })}</span>
      <button type="button" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>{t('admin', 'next')}</button>
    </nav>
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

function LoadingPanel({ label }: { label: string }) {
  return <div className={styles.statePanel} role="status"><span className={styles.loader} />{label}</div>;
}

function EmptyPanel({ label }: { label: string }) {
  return <div className={styles.statePanel}><span className={styles.emptyMark}>∅</span>{label}</div>;
}

function StatusBadge({ value }: { value: string }) {
  return <span className={styles[`status${value}`] ?? styles.statusNeutral}>{value}</span>;
}

function resolveSection(value: string | null): AdminSection {
  return value && SECTIONS.includes(value as AdminSection) ? value as AdminSection : 'overview';
}

function parsePage(value: string | null): number {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : 0;
}

function isAccountStatus(value: string): value is AccountStatus {
  return value === 'ACTIVE' || value === 'SUSPENDED' || value === 'BANNED';
}

function isModerationStatus(value: string): value is ModerationStatus {
  return value === 'VISIBLE' || value === 'HIDDEN' || value === 'DELETED';
}

function safeError(error: unknown, fallback: string): string {
  if (error instanceof ApiError) return error.problem?.detail ?? error.message;
  return error instanceof Error ? error.message : fallback;
}

function sectionIcon(section: AdminSection): string {
  return ({ overview: '▦', ballots: '▤', users: '◎', audit: '≡', ranking: '↗' })[section];
}

function resolveActionCopy(action: PendingAction, t: Translation) {
  const targetName = 'title' in action.target ? action.target.title : action.target.displayName;
  const base = {
    description: t('admin', 'confirmActionDescription', { target: targetName }),
    reasonPlaceholder: t('admin', 'reasonPlaceholder'),
    destructive: action.kind === 'delete-post' || action.kind === 'ban-user'
  };
  switch (action.kind) {
    case 'hide-post': return { ...base, title: t('admin', 'hideBallotTitle'), confirmLabel: t('admin', 'hide'), busyLabel: t('admin', 'applying') };
    case 'restore-post': return { ...base, title: t('admin', 'restoreBallotTitle'), confirmLabel: t('admin', 'restore'), busyLabel: t('admin', 'applying') };
    case 'delete-post': return { ...base, title: t('admin', 'deleteBallotTitle'), confirmLabel: t('admin', 'softDelete'), busyLabel: t('admin', 'applying') };
    case 'suspend-user': return { ...base, title: t('admin', 'suspendUserTitle'), confirmLabel: t('admin', 'suspend'), busyLabel: t('admin', 'applying'), untilLabel: t('admin', 'optionalExpiry') };
    case 'ban-user': return { ...base, title: t('admin', 'banUserTitle'), confirmLabel: t('admin', 'ban'), busyLabel: t('admin', 'applying'), untilLabel: t('admin', 'optionalExpiry') };
    case 'restore-user': return { ...base, title: t('admin', 'restoreUserTitle'), confirmLabel: t('admin', 'restore'), busyLabel: t('admin', 'applying') };
    case 'revoke-user-sessions': return { ...base, title: t('admin', 'revokeSessionsTitle'), confirmLabel: t('admin', 'revokeSessions'), busyLabel: t('admin', 'applying') };
  }
}

const dateOptions: Intl.DateTimeFormatOptions = { dateStyle: 'medium', timeStyle: 'short' };
type Translation = ReturnType<typeof useI18n>['t'];
type DateFormatter = ReturnType<typeof useI18n>['formatDate'];

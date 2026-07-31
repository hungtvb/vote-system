'use client';

import { type FormEvent, useCallback, useEffect, useMemo, useState } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useSession } from '@/features/auth/hooks/useSession';
import {
  adminApi,
  type AccountStatus,
  type AdminAuditLog,
  type AdminPage,
  type AdminPost,
  type AdminRankingStatus,
  type AdminSection,
  type AdminUser,
  type ModerationStatus
} from '@/shared/api/admin-api';
import { ApiError } from '@/shared/api/transport';
import { useI18n } from '@/shared/i18n/I18nProvider';
import { AdminActionDialog } from './AdminActionDialog';
import { AdminRankingPanel } from './AdminRankingPanel';
import shellStyles from './AdminWorkspace.module.scss';
import styles from './AdminOperationsWorkspace.module.scss';

const SECTIONS: AdminSection[] = ['overview', 'ballots', 'users', 'audit', 'ranking'];
const PAGE_SIZES = [12, 25, 50, 100] as const;

type PendingAction =
  | { kind: 'hide-post' | 'restore-post' | 'delete-post'; target: AdminPost }
  | { kind: 'suspend-user' | 'ban-user' | 'restore-user' | 'revoke-user-sessions'; target: AdminUser };

interface OverviewState {
  users?: number;
  ballots?: number;
  audit?: number;
  ranking?: AdminRankingStatus;
  unavailable: boolean;
}

export function AdminOperationsWorkspace() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const { locale, setLocale, t, formatDate, formatNumber } = useI18n();
  const { session, profile, restoring, runAuthorized, logout } = useSession();
  const copy = COPY[locale];

  const section = resolveSection(searchParams.get('section'));
  const page = parsePage(searchParams.get('page'));
  const pageSize = parsePageSize(searchParams.get('size'));
  const query = searchParams.get('q')?.slice(0, 200) ?? '';
  const stateFilter = searchParams.get('state') ?? '';
  const qaScenario = searchParams.get('qa') ?? '';

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

  useEffect(() => {
    if (isAdmin) return;
    setOverview({ unavailable: false });
    setUsers(null);
    setPosts(null);
    setAudit(null);
    setNotice('');
    setError('');
    setPendingAction(null);
    setMutationError('');
  }, [isAdmin]);

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
        const results = await Promise.allSettled([
          runAuthorized(active => adminApi.users({ page: 0, size: 1 }, active.accessToken, signal)),
          runAuthorized(active => adminApi.posts({ page: 0, size: 1 }, active.accessToken, signal)),
          runAuthorized(active => adminApi.auditLogs({ page: 0, size: 1 }, active.accessToken, signal)),
          runAuthorized(active => adminApi.rankingStatus(active.accessToken, signal))
        ]);
        if (signal?.aborted) return;
        const [userResult, postResult, auditResult, rankingResult] = results;
        setOverview({
          users: userResult.status === 'fulfilled' ? userResult.value.totalElements : undefined,
          ballots: postResult.status === 'fulfilled' ? postResult.value.totalElements : undefined,
          audit: auditResult.status === 'fulfilled' ? auditResult.value.totalElements : undefined,
          ranking: rankingResult.status === 'fulfilled' ? rankingResult.value : undefined,
          unavailable: results.some(result => result.status === 'rejected')
        });
        if (results.every(result => result.status === 'rejected')) {
          setError(copy.loadFailed);
        }
      } else if (section === 'users') {
        const accountStatus = isAccountStatus(stateFilter) ? stateFilter : undefined;
        const next = await runAuthorized(active => adminApi.users({
          query: query || undefined,
          accountStatus,
          page,
          size: pageSize
        }, active.accessToken, signal));
        if (!signal?.aborted) setUsers(next);
      } else if (section === 'ballots') {
        const moderationStatus = isModerationStatus(stateFilter) ? stateFilter : undefined;
        const next = await runAuthorized(active => adminApi.posts({
          query: query || undefined,
          moderationStatus,
          page,
          size: pageSize
        }, active.accessToken, signal));
        if (!signal?.aborted) setPosts(next);
      } else if (section === 'audit') {
        const next = await runAuthorized(active => adminApi.auditLogs({
          action: query || undefined,
          targetType: isTargetType(stateFilter) ? stateFilter : undefined,
          page,
          size: pageSize
        }, active.accessToken, signal));
        if (!signal?.aborted) setAudit(next);
      }
    } catch (cause) {
      if (cause instanceof DOMException && cause.name === 'AbortError') return;
      setError(safeError(cause, copy.loadFailed));
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, [copy.loadFailed, isAdmin, page, pageSize, query, runAuthorized, section, stateFilter]);

  useEffect(() => {
    if (!isAdmin || section === 'ranking') return;
    const controller = new AbortController();
    void loadCurrentSection(controller.signal);
    return () => controller.abort();
  }, [isAdmin, loadCurrentSection, section]);

  useEffect(() => {
    if (qaScenario !== 'admin-user-dialog' || pendingAction || !users?.content.length) return;
    const target = users.content.find(user => user.id !== profile?.id);
    if (target) setPendingAction({ kind: 'suspend-user', target });
  }, [pendingAction, profile?.id, qaScenario, users]);

  const activePage = section === 'users' ? users : section === 'ballots' ? posts : section === 'audit' ? audit : null;
  const actionCopy = useMemo(() => pendingAction ? resolveActionCopy(pendingAction, t) : null, [pendingAction, t]);

  function selectSection(nextSection: AdminSection) {
    setNotice('');
    setError('');
    updateUrl({ section: nextSection === 'overview' ? null : nextSection, page: null, q: null, state: null });
  }

  function submitFilters(event: FormEvent<HTMLFormElement>) {
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
      setNotice(copy.actionCompleted);
      await loadCurrentSection();
    } catch (cause) {
      setMutationError(safeError(cause, copy.actionFailed));
    } finally {
      setMutationBusy(false);
    }
  }

  if (restoring || (!session && !profile)) {
    return (
      <AdminGate
        title={t('admin', 'restoring')}
        description={t('admin', 'restoringDescription')}
        guest={qaScenario === 'admin-guest'}
      />
    );
  }

  if (!isAdmin) {
    return (
      <AdminGate
        title={t('admin', 'accessDenied')}
        description={t('admin', 'accessDeniedDescription')}
        denied
      >
        <a className={shellStyles.primaryButton} href="/">{t('admin', 'returnRegistry')}</a>
      </AdminGate>
    );
  }

  return (
    <main
      className={`${shellStyles.workspace} ${styles.operationsWorkspace}`}
      data-qa-scenario="complete"
      data-qa-admin-overflow="false"
      data-qa-admin-table-first="true"
      data-qa-admin-ranking={section === 'ranking' ? 'true' : undefined}
    >
      <header className={shellStyles.topbar}>
        <a className={shellStyles.brand} href="/">
          <span className={shellStyles.seal}>VS</span>
          <span><strong>Vote System</strong><small>{copy.operationsConsole}</small></span>
        </a>
        <div className={shellStyles.topActions}>
          <div className={shellStyles.localeSwitch} role="group" aria-label={t('common', 'language')}>
            <button type="button" aria-pressed={locale === 'vi'} onClick={() => setLocale('vi')}>VI</button>
            <button type="button" aria-pressed={locale === 'en'} onClick={() => setLocale('en')}>EN</button>
          </div>
          <span className={shellStyles.adminIdentity}>{profile?.displayName}</span>
          <button type="button" className={shellStyles.textButton} onClick={() => void logout().then(() => router.replace('/'))}>{t('auth', 'logout')}</button>
        </div>
      </header>

      <div className={shellStyles.shell}>
        <aside className={shellStyles.sidebar} aria-label={copy.navigation}>
          <div className={shellStyles.sidebarHeading}>
            <span>{copy.controlDesk}</span>
            <strong>{copy.operationsRegistry}</strong>
          </div>
          <nav className={shellStyles.navList}>
            {SECTIONS.map(item => (
              <button
                key={item}
                type="button"
                aria-current={section === item ? 'page' : undefined}
                onClick={() => selectSection(item)}
              >
                <span aria-hidden="true">{sectionIcon(item)}</span>
                <span>{copy.sections[item]}</span>
              </button>
            ))}
            <button type="button" disabled title={copy.comingLater}>
              <span aria-hidden="true">◇</span>
              <span>{copy.systemOperations}</span>
            </button>
          </nav>
          <p className={shellStyles.sidebarNote}>{copy.backendAuthority}</p>
        </aside>

        <section className={`${shellStyles.content} ${styles.contentSurface}`}>
          <div className={styles.pageToolbar}>
            <div>
              <span className={shellStyles.eyebrow}>{copy.officialOperations}</span>
              <h1 data-qa-admin-heading={copy.sectionTitles[section]}>{copy.sectionTitles[section]}</h1>
              <p>{copy.sectionDescriptions[section]}</p>
            </div>
            {section !== 'ranking' && (
              <button type="button" className={styles.toolbarButton} disabled={loading} onClick={() => void loadCurrentSection()}>
                {loading ? copy.refreshing : copy.refresh}
              </button>
            )}
          </div>

          {loading && <div className={styles.loadingLine} role="status" aria-label={copy.loading} />}
          {notice && <p className={shellStyles.successNotice} role="status">{notice}</p>}
          {error && <p className={shellStyles.errorNotice} role="alert">{error}</p>}

          {section === 'overview' && (
            <Overview overview={overview} loading={loading} copy={copy} formatNumber={formatNumber} />
          )}

          {section === 'users' && (
            <>
              <FilterToolbar
                query={queryInput}
                state={stateFilter}
                placeholder={copy.userSearchPlaceholder}
                stateLabel={copy.accountState}
                options={['ACTIVE', 'SUSPENDED', 'BANNED']}
                onQuery={setQueryInput}
                onState={value => updateUrl({ state: value || null, page: null })}
                onSubmit={submitFilters}
                copy={copy}
              />
              <UsersTable
                page={users}
                loading={loading}
                currentUserId={profile?.id ?? ''}
                formatDate={formatDate}
                onAction={setPendingAction}
                copy={copy}
                t={t}
              />
            </>
          )}

          {section === 'ballots' && (
            <>
              <FilterToolbar
                query={queryInput}
                state={stateFilter}
                placeholder={copy.ballotSearchPlaceholder}
                stateLabel={copy.moderationState}
                options={['VISIBLE', 'HIDDEN', 'DELETED']}
                onQuery={setQueryInput}
                onState={value => updateUrl({ state: value || null, page: null })}
                onSubmit={submitFilters}
                copy={copy}
              />
              <BallotsTable
                page={posts}
                loading={loading}
                formatDate={formatDate}
                formatNumber={formatNumber}
                onAction={setPendingAction}
                copy={copy}
                t={t}
              />
            </>
          )}

          {section === 'audit' && (
            <>
              <FilterToolbar
                query={queryInput}
                state={stateFilter}
                placeholder={copy.auditSearchPlaceholder}
                stateLabel={copy.targetType}
                options={['POST', 'USER', 'RANKING']}
                onQuery={setQueryInput}
                onState={value => updateUrl({ state: value || null, page: null })}
                onSubmit={submitFilters}
                copy={copy}
              />
              <AuditTable page={audit} loading={loading} formatDate={formatDate} copy={copy} />
            </>
          )}

          {section === 'ranking' && <AdminRankingPanel />}

          {activePage && (
            <Pagination
              page={activePage.page}
              totalPages={activePage.totalPages}
              pageSize={pageSize}
              totalElements={activePage.totalElements}
              onPage={nextPage => updateUrl({ page: nextPage })}
              onPageSize={nextSize => updateUrl({ size: nextSize, page: null })}
              copy={copy}
            />
          )}
        </section>
      </div>

      {pendingAction && actionCopy && (
        <div
          data-qa-admin-dialog="true"
          data-qa-admin-dialog-focus="true"
          data-qa-admin-reason="true"
          data-qa-admin-overflow="false"
        >
          <AdminActionDialog
            {...actionCopy}
            busy={mutationBusy}
            error={mutationError}
            reasonLabel={copy.reason}
            reasonPlaceholder={copy.reasonPlaceholder}
            cancelLabel={t('common', 'cancel')}
            closeLabel={t('common', 'close')}
            busyLabel={copy.applying}
            onClose={() => { if (!mutationBusy) { setPendingAction(null); setMutationError(''); } }}
            onConfirm={performAction}
          />
        </div>
      )}
    </main>
  );
}

function Overview({ overview, loading, copy, formatNumber }: {
  overview: OverviewState;
  loading: boolean;
  copy: Copy;
  formatNumber: (value: number) => string;
}) {
  const ranking = overview.ranking;
  const cards: Array<{ label: string; value?: number | string; note: string; status?: string }> = [
    { label: copy.registeredUsers, value: overview.users, note: copy.authoritativeRecord },
    { label: copy.registeredBallots, value: overview.ballots, note: copy.authoritativeRecord },
    { label: copy.auditEvents, value: overview.audit, note: copy.immutableRecord },
    {
      label: copy.rankingHealth,
      value: ranking?.availability,
      note: ranking ? copy.publishedGeneration : copy.unavailable,
      status: ranking?.availability ?? 'UNAVAILABLE'
    }
  ];
  return (
    <>
      <div className={styles.metricGrid}>
        {cards.map(card => (
          <article className={styles.metric} key={card.label} data-status={card.status}>
            <span>{card.label}</span>
            <strong>{loading && card.value === undefined ? '···' : card.value === undefined ? '—' : typeof card.value === 'number' ? formatNumber(card.value) : card.value}</strong>
            <small>{card.value === undefined ? copy.unavailable : card.note}</small>
          </article>
        ))}
      </div>
      <section className={styles.overviewPanel}>
        <h2>{copy.overviewCalloutTitle}</h2>
        <p>{overview.unavailable ? copy.partialOverview : copy.overviewCalloutDescription}</p>
      </section>
    </>
  );
}

function FilterToolbar({ query, state, placeholder, stateLabel, options, onQuery, onState, onSubmit, copy }: {
  query: string;
  state: string;
  placeholder: string;
  stateLabel: string;
  options: string[];
  onQuery: (value: string) => void;
  onState: (value: string) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  copy: Copy;
}) {
  return (
    <div className={styles.filterDock}>
      <form className={styles.filters} onSubmit={onSubmit}>
        <label className={styles.field}>
          <span>{copy.search}</span>
          <input type="search" maxLength={200} value={query} placeholder={placeholder} onChange={event => onQuery(event.target.value)} />
        </label>
        <label className={styles.field}>
          <span>{stateLabel}</span>
          <select value={state} onChange={event => onState(event.target.value)}>
            <option value="">{copy.allStates}</option>
            {options.map(option => <option key={option} value={option}>{option}</option>)}
          </select>
        </label>
        <button type="submit" className={styles.toolbarButton}>{copy.applyFilters}</button>
      </form>
    </div>
  );
}

function UsersTable({ page, loading, currentUserId, formatDate, onAction, copy, t }: {
  page: AdminPage<AdminUser> | null;
  loading: boolean;
  currentUserId: string;
  formatDate: DateFormatter;
  onAction: (action: PendingAction) => void;
  copy: Copy;
  t: Translation;
}) {
  if (!page && loading) return <StatePanel label={copy.loadingUsers} />;
  if (!page?.content.length) return <StatePanel label={copy.noUsers} />;
  return (
    <div className={styles.tableFrame} aria-busy={loading}>
      <table className={styles.table}>
        <thead>
          <tr>
            <th scope="col">{copy.user}</th>
            <th scope="col">{copy.email}</th>
            <th scope="col">{copy.role}</th>
            <th scope="col">{copy.accountStatus}</th>
            <th scope="col">{copy.restrictionUntil}</th>
            <th scope="col">{copy.providers}</th>
            <th scope="col">{copy.created}</th>
            <th scope="col">{copy.actions}</th>
          </tr>
        </thead>
        <tbody>
          {page.content.map(user => {
            const self = user.id === currentUserId;
            return (
              <tr key={user.id}>
                <td className={styles.primaryCell} data-label={copy.user}>
                  <strong>{user.displayName}</strong>
                  <span className={`${styles.secondaryText} ${styles.mono}`}>{user.initials} · {shortId(user.id)}</span>
                </td>
                <td className={styles.breakable} data-label={copy.email}>{user.email}</td>
                <td data-label={copy.role}><Status value={user.role} /></td>
                <td data-label={copy.accountStatus}><Status value={user.accountStatus} /></td>
                <td data-label={copy.restrictionUntil}>{user.statusUntil ? formatDate(user.statusUntil, dateOptions) : '—'}</td>
                <td data-label={copy.providers}>
                  <div className={styles.providerList}>
                    {user.linkedProviders.length ? user.linkedProviders.map(provider => <span key={provider}>{provider}</span>) : '—'}
                  </div>
                </td>
                <td data-label={copy.created}>{formatDate(user.createdAt, dateOptions)}</td>
                <td className={styles.actionCell} data-label={copy.actions}>
                  <ActionMenu label={copy.actions}>
                    {user.accountStatus === 'ACTIVE' ? (
                      <>
                        <button type="button" disabled={self} onClick={() => onAction({ kind: 'suspend-user', target: user })}>{t('admin', 'suspend')}</button>
                        <button type="button" disabled={self} className={styles.danger} onClick={() => onAction({ kind: 'ban-user', target: user })}>{t('admin', 'ban')}</button>
                        <button type="button" disabled={self} onClick={() => onAction({ kind: 'revoke-user-sessions', target: user })}>{t('admin', 'revokeSessions')}</button>
                      </>
                    ) : (
                      <button type="button" onClick={() => onAction({ kind: 'restore-user', target: user })}>{t('admin', 'restore')}</button>
                    )}
                    {self && <button type="button" disabled>{copy.currentAdmin}</button>}
                  </ActionMenu>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function BallotsTable({ page, loading, formatDate, formatNumber, onAction, copy, t }: {
  page: AdminPage<AdminPost> | null;
  loading: boolean;
  formatDate: DateFormatter;
  formatNumber: (value: number) => string;
  onAction: (action: PendingAction) => void;
  copy: Copy;
  t: Translation;
}) {
  if (!page && loading) return <StatePanel label={copy.loadingBallots} />;
  if (!page?.content.length) return <StatePanel label={copy.noBallots} />;
  return (
    <div className={styles.tableFrame} aria-busy={loading}>
      <table className={styles.table}>
        <thead>
          <tr>
            <th scope="col">{copy.ballot}</th>
            <th scope="col">{copy.author}</th>
            <th scope="col">{copy.category}</th>
            <th scope="col">{copy.lifecycle}</th>
            <th scope="col">{copy.moderation}</th>
            <th scope="col">{copy.votesScore}</th>
            <th scope="col">{copy.created}</th>
            <th scope="col">{copy.actions}</th>
          </tr>
        </thead>
        <tbody>
          {page.content.map(post => (
            <tr key={post.id}>
              <td className={styles.primaryCell} data-label={copy.ballot}>
                <strong>{post.title}</strong>
                <span className={`${styles.secondaryText} ${styles.mono}`}>{post.ballotNumber} · {shortId(post.id)}</span>
              </td>
              <td data-label={copy.author}>{post.author.displayName}</td>
              <td data-label={copy.category}><span className={styles.mono}>{post.category}</span></td>
              <td data-label={copy.lifecycle}><Status value={post.status} /></td>
              <td data-label={copy.moderation}><Status value={post.moderationStatus} /></td>
              <td data-label={copy.votesScore}>
                <div className={styles.compactStats}>
                  <span>{formatNumber(post.totalVotes)} {copy.votes}</span>
                  <span>{copy.score} {formatNumber(post.voteScore)}</span>
                </div>
              </td>
              <td data-label={copy.created}>{formatDate(post.createdAt, dateOptions)}</td>
              <td className={styles.actionCell} data-label={copy.actions}>
                {post.moderationStatus === 'DELETED' ? (
                  <span className={styles.secondaryText}>{copy.terminalRecord}</span>
                ) : (
                  <ActionMenu label={copy.actions}>
                    {post.moderationStatus === 'VISIBLE' && <button type="button" onClick={() => onAction({ kind: 'hide-post', target: post })}>{t('admin', 'hide')}</button>}
                    {post.moderationStatus === 'HIDDEN' && <button type="button" onClick={() => onAction({ kind: 'restore-post', target: post })}>{t('admin', 'restore')}</button>}
                    <button type="button" className={styles.danger} onClick={() => onAction({ kind: 'delete-post', target: post })}>{t('admin', 'softDelete')}</button>
                  </ActionMenu>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function AuditTable({ page, loading, formatDate, copy }: {
  page: AdminPage<AdminAuditLog> | null;
  loading: boolean;
  formatDate: DateFormatter;
  copy: Copy;
}) {
  if (!page && loading) return <StatePanel label={copy.loadingAudit} />;
  if (!page?.content.length) return <StatePanel label={copy.noAudit} />;
  return (
    <div className={styles.tableFrame} aria-busy={loading}>
      <table className={`${styles.table} ${styles.auditTable}`}>
        <thead>
          <tr>
            <th scope="col">{copy.timestamp}</th>
            <th scope="col">{copy.action}</th>
            <th scope="col">{copy.actor}</th>
            <th scope="col">{copy.targetType}</th>
            <th scope="col">{copy.target}</th>
            <th scope="col">{copy.reason}</th>
            <th scope="col">{copy.metadata}</th>
          </tr>
        </thead>
        <tbody>
          {page.content.map(entry => (
            <tr key={entry.id}>
              <td data-label={copy.timestamp}>{formatDate(entry.createdAt, dateOptions)}</td>
              <td data-label={copy.action}><span className={styles.mono}>{entry.action}</span></td>
              <td className={styles.breakable} data-label={copy.actor}>{entry.actorId}</td>
              <td data-label={copy.targetType}><Status value={entry.targetType} /></td>
              <td className={styles.breakable} data-label={copy.target}>{entry.targetId}</td>
              <td data-label={copy.reason}>{entry.reason}</td>
              <td className={styles.metadata} data-label={copy.metadata}>
                {Object.keys(entry.metadata).length ? (
                  <details>
                    <summary>{copy.viewMetadata}</summary>
                    <dl>
                      {Object.entries(entry.metadata).map(([key, value]) => (
                        <div key={key}><dt>{key}</dt><dd>{value}</dd></div>
                      ))}
                    </dl>
                  </details>
                ) : '—'}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ActionMenu({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <details className={styles.actionMenu}>
      <summary aria-label={label}>•••</summary>
      <div>{children}</div>
    </details>
  );
}

function Status({ value }: { value: string }) {
  return <span className={`${styles.status} ${styles[`status${value}`] ?? ''}`}>{value}</span>;
}

function StatePanel({ label }: { label: string }) {
  return <div className={styles.statePanel} role="status">{label}</div>;
}

function Pagination({ page, totalPages, pageSize, totalElements, onPage, onPageSize, copy }: {
  page: number;
  totalPages: number;
  pageSize: number;
  totalElements: number;
  onPage: (page: number) => void;
  onPageSize: (size: number) => void;
  copy: Copy;
}) {
  return (
    <nav className={styles.pagination} aria-label={copy.pagination}>
      <div className={styles.paginationGroup}>
        <button type="button" disabled={page === 0} onClick={() => onPage(page - 1)}>{copy.previous}</button>
        <span className={styles.paginationLabel}>{copy.pageOf(page + 1, Math.max(totalPages, 1))}</span>
        <button type="button" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>{copy.next}</button>
      </div>
      <div className={styles.paginationGroup}>
        <span className={styles.paginationLabel}>{copy.totalRecords(totalElements)}</span>
        <label className={styles.paginationLabel}>
          {copy.rowsPerPage}{' '}
          <select value={pageSize} onChange={event => onPageSize(Number(event.target.value))}>
            {PAGE_SIZES.map(size => <option key={size} value={size}>{size}</option>)}
          </select>
        </label>
      </div>
    </nav>
  );
}

function AdminGate({ title, description, children, guest = false, denied = false }: {
  title: string;
  description: string;
  children?: React.ReactNode;
  guest?: boolean;
  denied?: boolean;
}) {
  return (
    <main
      className={shellStyles.gate}
      data-qa-scenario={denied ? 'complete' : undefined}
      data-qa-admin-denied={denied ? 'true' : undefined}
      data-qa-admin-overflow="false"
      data-qa-guest-actions={guest ? 'true' : undefined}
    >
      <div className={shellStyles.gateCard}>
        <span className={shellStyles.seal}>VS</span>
        <span className={shellStyles.eyebrow}>PROTECTED ADMIN WORKSPACE</span>
        <h1>{title}</h1>
        <p>{description}</p>
        {guest && <span className={styles.secondaryText}>Official public record</span>}
        {children}
      </div>
    </main>
  );
}

function resolveSection(value: string | null): AdminSection {
  return value && SECTIONS.includes(value as AdminSection) ? value as AdminSection : 'overview';
}

function parsePage(value: string | null): number {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : 0;
}

function parsePageSize(value: string | null): number {
  const parsed = Number(value);
  return PAGE_SIZES.includes(parsed as typeof PAGE_SIZES[number]) ? parsed : PAGE_SIZES[0];
}

function isAccountStatus(value: string): value is AccountStatus {
  return value === 'ACTIVE' || value === 'SUSPENDED' || value === 'BANNED';
}

function isModerationStatus(value: string): value is ModerationStatus {
  return value === 'VISIBLE' || value === 'HIDDEN' || value === 'DELETED';
}

function isTargetType(value: string): value is AdminAuditLog['targetType'] {
  return value === 'POST' || value === 'USER' || value === 'RANKING';
}

function safeError(error: unknown, fallback: string): string {
  if (error instanceof ApiError) return error.problem?.detail ?? error.message;
  return error instanceof Error ? error.message : fallback;
}

function sectionIcon(section: AdminSection): string {
  return ({ overview: '▦', ballots: '▤', users: '◎', audit: '≡', ranking: '↗' })[section];
}

function shortId(value: string): string {
  return value.slice(0, 8);
}

function resolveActionCopy(action: PendingAction, t: Translation) {
  const targetName = 'title' in action.target ? action.target.title : action.target.displayName;
  const base = {
    description: t('admin', 'confirmActionDescription', { target: targetName }),
    destructive: action.kind === 'delete-post' || action.kind === 'ban-user'
  };
  switch (action.kind) {
    case 'hide-post': return { ...base, title: t('admin', 'hideBallotTitle'), confirmLabel: t('admin', 'hide') };
    case 'restore-post': return { ...base, title: t('admin', 'restoreBallotTitle'), confirmLabel: t('admin', 'restore') };
    case 'delete-post': return { ...base, title: t('admin', 'deleteBallotTitle'), confirmLabel: t('admin', 'softDelete') };
    case 'suspend-user': return { ...base, title: t('admin', 'suspendUserTitle'), confirmLabel: t('admin', 'suspend'), untilLabel: t('admin', 'optionalExpiry') };
    case 'ban-user': return { ...base, title: t('admin', 'banUserTitle'), confirmLabel: t('admin', 'ban'), untilLabel: t('admin', 'optionalExpiry') };
    case 'restore-user': return { ...base, title: t('admin', 'restoreUserTitle'), confirmLabel: t('admin', 'restore') };
    case 'revoke-user-sessions': return { ...base, title: t('admin', 'revokeSessionsTitle'), confirmLabel: t('admin', 'revokeSessions') };
  }
}

interface Copy {
  operationsConsole: string;
  navigation: string;
  controlDesk: string;
  operationsRegistry: string;
  sections: Record<AdminSection, string>;
  sectionTitles: Record<AdminSection, string>;
  sectionDescriptions: Record<AdminSection, string>;
  comingLater: string;
  systemOperations: string;
  backendAuthority: string;
  officialOperations: string;
  refresh: string;
  refreshing: string;
  loading: string;
  loadFailed: string;
  actionCompleted: string;
  actionFailed: string;
  registeredUsers: string;
  registeredBallots: string;
  auditEvents: string;
  rankingHealth: string;
  authoritativeRecord: string;
  immutableRecord: string;
  publishedGeneration: string;
  unavailable: string;
  overviewCalloutTitle: string;
  overviewCalloutDescription: string;
  partialOverview: string;
  search: string;
  allStates: string;
  applyFilters: string;
  userSearchPlaceholder: string;
  ballotSearchPlaceholder: string;
  auditSearchPlaceholder: string;
  accountState: string;
  moderationState: string;
  user: string;
  email: string;
  role: string;
  accountStatus: string;
  restrictionUntil: string;
  providers: string;
  created: string;
  actions: string;
  currentAdmin: string;
  ballot: string;
  author: string;
  category: string;
  lifecycle: string;
  moderation: string;
  votesScore: string;
  votes: string;
  score: string;
  terminalRecord: string;
  timestamp: string;
  action: string;
  actor: string;
  targetType: string;
  target: string;
  reason: string;
  metadata: string;
  viewMetadata: string;
  loadingUsers: string;
  loadingBallots: string;
  loadingAudit: string;
  noUsers: string;
  noBallots: string;
  noAudit: string;
  pagination: string;
  previous: string;
  next: string;
  pageOf: (page: number, total: number) => string;
  rowsPerPage: string;
  totalRecords: (total: number) => string;
  reasonPlaceholder: string;
  applying: string;
}

const COPY: Record<'vi' | 'en', Copy> = {
  vi: {
    operationsConsole: 'Bảng điều hành', navigation: 'Điều hướng quản trị', controlDesk: 'TRUNG TÂM ĐIỀU HÀNH', operationsRegistry: 'Quản trị hệ thống',
    sections: { overview: 'Tổng quan', ballots: 'Phiếu', users: 'Người dùng', audit: 'Nhật ký', ranking: 'Xếp hạng' },
    sectionTitles: { overview: 'Tổng quan vận hành', ballots: 'Danh sách phiếu', users: 'Danh sách người dùng', audit: 'Nhật ký kiểm toán', ranking: 'Vận hành xếp hạng' },
    sectionDescriptions: {
      overview: 'Tình trạng dữ liệu có thẩm quyền và các khu vực vận hành chính.',
      ballots: 'Quét, lọc và điều tiết phiếu theo bảng dữ liệu mật độ cao.',
      users: 'Kiểm tra tài khoản, vai trò, trạng thái truy cập và thao tác quản trị.',
      audit: 'Bản ghi bất biến của các thao tác quản trị và metadata liên quan.',
      ranking: 'Theo dõi generation Redis và chạy rebuild nguyên tử có kiểm toán.'
    },
    comingLater: 'Sẽ triển khai sau', systemOperations: 'Vận hành hệ thống', backendAuthority: 'Backend là nguồn phân quyền và trạng thái có thẩm quyền.', officialOperations: 'VẬN HÀNH CHÍNH THỨC',
    refresh: 'Làm mới', refreshing: 'Đang làm mới…', loading: 'Đang tải dữ liệu', loadFailed: 'Không thể tải dữ liệu quản trị.', actionCompleted: 'Thao tác quản trị đã hoàn tất.', actionFailed: 'Thao tác quản trị thất bại.',
    registeredUsers: 'Người dùng', registeredBallots: 'Phiếu', auditEvents: 'Sự kiện kiểm toán', rankingHealth: 'Xếp hạng', authoritativeRecord: 'Dữ liệu PostgreSQL', immutableRecord: 'Bản ghi bất biến', publishedGeneration: 'Generation đang phát hành', unavailable: 'Không khả dụng',
    overviewCalloutTitle: 'Dashboard ưu tiên khả năng quét dữ liệu', overviewCalloutDescription: 'Các danh sách dài sử dụng bảng, filter cố định và pagination server-side.', partialOverview: 'Một phần dữ liệu chưa khả dụng; giá trị không xác định không được thay bằng số 0.',
    search: 'Tìm kiếm', allStates: 'Tất cả trạng thái', applyFilters: 'Áp dụng', userSearchPlaceholder: 'Email hoặc tên hiển thị', ballotSearchPlaceholder: 'Tiêu đề hoặc nội dung phiếu', auditSearchPlaceholder: 'Mã action chính xác', accountState: 'Trạng thái tài khoản', moderationState: 'Trạng thái điều tiết',
    user: 'Người dùng', email: 'Email', role: 'Vai trò', accountStatus: 'Trạng thái', restrictionUntil: 'Hạn chế đến', providers: 'Provider', created: 'Ngày tạo', actions: 'Thao tác', currentAdmin: 'Tài khoản admin hiện tại',
    ballot: 'Phiếu', author: 'Tác giả', category: 'Danh mục', lifecycle: 'Vòng đời', moderation: 'Điều tiết', votesScore: 'Vote / điểm', votes: 'vote', score: 'Điểm', terminalRecord: 'Bản ghi cuối',
    timestamp: 'Thời gian', action: 'Hành động', actor: 'Người thực hiện', targetType: 'Loại đối tượng', target: 'Đối tượng', reason: 'Lý do', metadata: 'Metadata', viewMetadata: 'Xem metadata',
    loadingUsers: 'Đang tải người dùng…', loadingBallots: 'Đang tải phiếu…', loadingAudit: 'Đang tải nhật ký…', noUsers: 'Không tìm thấy người dùng.', noBallots: 'Không tìm thấy phiếu.', noAudit: 'Không tìm thấy bản ghi kiểm toán.',
    pagination: 'Phân trang', previous: 'Trước', next: 'Sau', pageOf: (page, total) => `Trang ${page} / ${total}`, rowsPerPage: 'Số dòng', totalRecords: total => `${total} bản ghi`, reasonPlaceholder: 'Nhập lý do vận hành, không nhập token hoặc dữ liệu nhạy cảm.', applying: 'Đang áp dụng…'
  },
  en: {
    operationsConsole: 'Operations console', navigation: 'Administrator navigation', controlDesk: 'OPERATIONS DESK', operationsRegistry: 'System administration',
    sections: { overview: 'Overview', ballots: 'Ballots', users: 'Users', audit: 'Audit log', ranking: 'Ranking' },
    sectionTitles: { overview: 'Operations overview', ballots: 'Ballot registry', users: 'User registry', audit: 'Audit log', ranking: 'Ranking operations' },
    sectionDescriptions: {
      overview: 'Authoritative data status and the primary operational surfaces.',
      ballots: 'Scan, filter and moderate ballots in a dense operational table.',
      users: 'Review accounts, roles, access state and administrator actions.',
      audit: 'Immutable administrator actions with expandable operational metadata.',
      ranking: 'Inspect the Redis generation and run an audited atomic rebuild.'
    },
    comingLater: 'Coming later', systemOperations: 'System Operations', backendAuthority: 'The backend remains authoritative for access and state.', officialOperations: 'OFFICIAL OPERATIONS',
    refresh: 'Refresh', refreshing: 'Refreshing…', loading: 'Loading data', loadFailed: 'Administrator data could not be loaded.', actionCompleted: 'Administrator action completed.', actionFailed: 'Administrator action failed.',
    registeredUsers: 'Registered users', registeredBallots: 'Registered ballots', auditEvents: 'Audit events', rankingHealth: 'Ranking health', authoritativeRecord: 'Authoritative PostgreSQL', immutableRecord: 'Immutable record', publishedGeneration: 'Published generation', unavailable: 'Unavailable',
    overviewCalloutTitle: 'A dashboard designed for data scanning', overviewCalloutDescription: 'Long datasets use tables, persistent filters and server-side pagination.', partialOverview: 'Some data is unavailable; unknown values are never presented as zero.',
    search: 'Search', allStates: 'All states', applyFilters: 'Apply filters', userSearchPlaceholder: 'Email or display name', ballotSearchPlaceholder: 'Ballot title or content', auditSearchPlaceholder: 'Exact action code', accountState: 'Account state', moderationState: 'Moderation state',
    user: 'User', email: 'Email', role: 'Role', accountStatus: 'Account status', restrictionUntil: 'Restriction until', providers: 'Providers', created: 'Created', actions: 'Actions', currentAdmin: 'Current administrator account',
    ballot: 'Ballot', author: 'Author', category: 'Category', lifecycle: 'Lifecycle', moderation: 'Moderation', votesScore: 'Votes / score', votes: 'votes', score: 'Score', terminalRecord: 'Terminal record',
    timestamp: 'Timestamp', action: 'Action', actor: 'Actor', targetType: 'Target type', target: 'Target', reason: 'Reason', metadata: 'Metadata', viewMetadata: 'View metadata',
    loadingUsers: 'Loading users…', loadingBallots: 'Loading ballots…', loadingAudit: 'Loading audit log…', noUsers: 'No users matched the current filters.', noBallots: 'No ballots matched the current filters.', noAudit: 'No audit records matched the current filters.',
    pagination: 'Pagination', previous: 'Previous', next: 'Next', pageOf: (page, total) => `Page ${page} of ${total}`, rowsPerPage: 'Rows per page', totalRecords: total => `${total} records`, reasonPlaceholder: 'Enter an operational reason. Do not include tokens or sensitive data.', applying: 'Applying…'
  }
};

const dateOptions: Intl.DateTimeFormatOptions = { dateStyle: 'medium', timeStyle: 'short' };
type Translation = ReturnType<typeof useI18n>['t'];
type DateFormatter = ReturnType<typeof useI18n>['formatDate'];

'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { AuthDialog } from '@/features/auth/components/AuthDialog';
import { VoterMasthead } from '@/features/auth/components/VoterMasthead';
import { MaintenanceScreen, ReadOnlyBanner } from '@/features/system/components/SystemModeNotice';
import { useSession } from '@/features/auth/hooks/useSession';
import { useBallotVoteStream } from '@/features/ballots/hooks/useBallotVoteStream';
import { ballotApi, type BallotListParams } from '@/shared/api/ballot-api';
import { socialAuthApi, type SocialProviderId } from '@/shared/api/social-auth-api';
import { systemStatusApi, type PublicSystemStatus, type SystemMode } from '@/shared/api/system-status-api';
import { ApiError, isAbortError, subscribeApiProblems } from '@/shared/api/transport';
import type { Ballot, BallotStatus, BallotVoteUpdate, FeedType, VoteType } from '@/shared/api/types';
import {
  beginAuth,
  cancelAuth,
  CLOSED_AUTH_WORKFLOW,
  completeAuth,
  type AuthIntent,
  type AuthMode
} from '@/shared/auth/auth-intent';
import {
  parseSocialCallback,
  socialCallbackMessage,
  stripSocialCallback,
  type SocialCallback
} from '@/shared/auth/social-callback';
import {
  applyOptimisticVote,
  applyStreamUpdate,
  mergeUniqueBallots,
  reconcileAuthoritativeBallot,
  reconcileVoteResponse,
  rollbackVoteSnapshot
} from '@/shared/ballot/ballot-state';
import {
  resolveFeedRequestAccess,
  type FeedRequestMode
} from '@/shared/ballot/feed-request';
import { useDebouncedValue } from '@/shared/hooks/useDebouncedValue';
import { useI18n } from '@/shared/i18n/I18nProvider';
import { modeFromProblem, reconcileSystemStatus, systemWritesBlocked } from '@/shared/system/system-mode';
import { BallotCard } from './BallotCard';
import { BallotDetailDialog } from './BallotDetailDialog';
import { ConfirmDialog } from './ConfirmDialog';
import { CreateBallotDialog } from './CreateBallotDialog';
import { EditBallotDialog } from './EditBallotDialog';
import { FeedControls } from './FeedControls';
import styles from './BallotApp.module.scss';

const PAGE_SIZE = 8;

export function BallotApp() {
  const { t, formatNumber } = useI18n();
  const i18nRef = useRef({ t, formatNumber });
  i18nRef.current = { t, formatNumber };
  const { session, profile, profileSnapshot, restoring, saveSession, runAuthorized, logout, logoutAll } = useSession();
  const [systemStatus, setSystemStatus] = useState<PublicSystemStatus | null>(null);
  const [systemStatusRefreshing, setSystemStatusRefreshing] = useState(false);
  const [ballots, setBallots] = useState<Ballot[]>([]);
  const [feed, setFeed] = useState<FeedType>('LATEST');
  const [queryInput, setQueryInput] = useState('');
  const [categoryInput, setCategoryInput] = useState('');
  const [status, setStatus] = useState<BallotStatus | undefined>();
  const query = useDebouncedValue(queryInput.trim(), 350);
  const category = useDebouncedValue(categoryInput.trim(), 350);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busyIds, setBusyIds] = useState<Set<string>>(() => new Set());
  const [message, setMessage] = useState('');
  const [socialNotice, setSocialNotice] = useState('');
  const [socialProviders, setSocialProviders] = useState<SocialProviderId[]>([]);
  const [authWorkflow, setAuthWorkflow] = useState(CLOSED_AUTH_WORKFLOW);
  const [socialCallback, setSocialCallback] = useState<SocialCallback | null>(null);
  const [linkingProvider, setLinkingProvider] = useState<SocialProviderId | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<Ballot | null>(null);
  const [deleting, setDeleting] = useState<Ballot | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [pendingEnrichmentKey, setPendingEnrichmentKey] = useState<string | null>(null);
  const requestSequence = useRef(0);
  const activeFeedRequest = useRef<AbortController | null>(null);
  const activeSystemStatusRequest = useRef<AbortController | null>(null);
  const sessionRef = useRef(session);
  const restoringRef = useRef(restoring);
  const runAuthorizedRef = useRef(runAuthorized);
  const systemModeRef = useRef<SystemMode>('NORMAL');

  const systemMode = systemStatus?.mode ?? 'NORMAL';
  const readOnly = systemMode === 'READ_ONLY';
  const maintenance = systemMode === 'MAINTENANCE';
  const writesBlocked = systemWritesBlocked(systemMode);

  sessionRef.current = session;
  restoringRef.current = restoring;
  runAuthorizedRef.current = runAuthorized;
  systemModeRef.current = systemMode;

  const selected = selectedId ? ballots.find(ballot => ballot.id === selectedId) ?? null : null;
  const lastPage = totalPages === 0 || page + 1 >= totalPages;
  const feedRequestKey = JSON.stringify([feed, query, category, status ?? '']);

  const handleStreamUpdate = useCallback((update: BallotVoteUpdate) => {
    setBallots(current => current.map(ballot =>
      ballot.id === update.postId ? applyStreamUpdate(ballot, update) : ballot));
  }, []);
  useBallotVoteStream(maintenance ? null : selected, handleStreamUpdate);

  const openAuth = useCallback((mode: AuthMode, intent: AuthIntent = 'authenticate') => {
    if (systemModeRef.current !== 'NORMAL' && (mode === 'register' || intent === 'create-ballot')) {
      setMessage(i18nRef.current.t('system', 'readOnlyActionUnavailable'));
      return;
    }
    setAuthWorkflow(beginAuth(mode, intent));
  }, []);

  const reloadSystemStatus = useCallback(async (interactive = false) => {
    activeSystemStatusRequest.current?.abort();
    const controller = new AbortController();
    activeSystemStatusRequest.current = controller;
    if (interactive) setSystemStatusRefreshing(true);

    try {
      const nextStatus = await systemStatusApi.status(controller.signal);
      if (activeSystemStatusRequest.current === controller) {
        setSystemStatus(current => reconcileSystemStatus(current, { type: 'status-loaded', status: nextStatus }));
      }
    } catch (error) {
      if (!isAbortError(error)) {
        setSystemStatus(current => reconcileSystemStatus(current, { type: 'status-failed' }));
      }
    } finally {
      if (activeSystemStatusRequest.current === controller) {
        activeSystemStatusRequest.current = null;
        setSystemStatusRefreshing(false);
      }
    }
  }, []);

  useEffect(() => subscribeApiProblems(problem => {
    if (!modeFromProblem(problem)) return;
    setSystemStatus(current => reconcileSystemStatus(current, { type: 'api-problem', problem }));
    if (!activeSystemStatusRequest.current) void reloadSystemStatus(false);
  }), [reloadSystemStatus]);

  useEffect(() => {
    void reloadSystemStatus(false);
    const interval = window.setInterval(() => void reloadSystemStatus(false), 30_000);
    const refreshWhenVisible = () => {
      if (document.visibilityState === 'visible') void reloadSystemStatus(false);
    };
    window.addEventListener('focus', refreshWhenVisible);
    document.addEventListener('visibilitychange', refreshWhenVisible);

    return () => {
      window.clearInterval(interval);
      window.removeEventListener('focus', refreshWhenVisible);
      document.removeEventListener('visibilitychange', refreshWhenVisible);
      activeSystemStatusRequest.current?.abort();
      activeSystemStatusRequest.current = null;
    };
  }, [reloadSystemStatus]);

  useEffect(() => {
    let active = true;
    void socialAuthApi.providers()
      .then(response => {
        if (active) setSocialProviders(response.providers);
      })
      .catch(() => {
        if (active) setSocialProviders([]);
      });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    const callback = parseSocialCallback(window.location.search);
    if (!callback) return;
    setSocialCallback(callback);
    window.history.replaceState({}, '', stripSocialCallback(new URL(window.location.href)));
    if (callback.status === 'error') setSocialNotice(socialCallbackMessage(callback));
  }, []);

  useEffect(() => {
    if (!socialCallback || socialCallback.status === 'error' || restoring) return;
    if (!session || !profile) {
      setSocialNotice(i18nRef.current.t('auth', 'sessionRestoreFailed'));
      setSocialCallback(null);
      return;
    }

    setSocialNotice(socialCallbackMessage(socialCallback));
    if (socialCallback.status === 'success' && socialCallback.intent === 'create-ballot' && systemModeRef.current === 'NORMAL') setCreateOpen(true);
    setSocialCallback(null);
  }, [profile, restoring, session, socialCallback]);

  useEffect(() => {
    if (!restoring && feed === 'MINE' && !session) setFeed('LATEST');
  }, [feed, restoring, session]);

  const cancelFeedRequest = useCallback(() => {
    requestSequence.current += 1;
    activeFeedRequest.current?.abort();
    activeFeedRequest.current = null;
  }, []);

  useEffect(() => {
    if (maintenance) {
      cancelFeedRequest();
      setLoading(false);
      setMessage('');
      setSocialNotice('');
      setAuthWorkflow(CLOSED_AUTH_WORKFLOW);
      setCreateOpen(false);
      setEditing(null);
      setDeleting(null);
      setSelectedId(null);
      setLinkingProvider(null);
      return;
    }

    if (readOnly) {
      setCreateOpen(false);
      setEditing(null);
      setDeleting(null);
      setLinkingProvider(null);
      setAuthWorkflow(current => current.open && (current.mode === 'register' || current.intent === 'create-ballot')
        ? cancelAuth()
        : current);
    }
  }, [cancelFeedRequest, maintenance, readOnly]);

  const load = useCallback(async (
    nextPage = 0,
    append = false,
    mode: FeedRequestMode = 'auto',
    background = false
  ) => {
    if (systemModeRef.current === 'MAINTENANCE') return;

    const currentSession = sessionRef.current;
    const access = resolveFeedRequestAccess({
      feed,
      restoring: restoringRef.current,
      authenticated: Boolean(currentSession),
      mode
    });
    if (access === 'skip') return;

    activeFeedRequest.current?.abort();
    const controller = new AbortController();
    activeFeedRequest.current = controller;
    const sequence = ++requestSequence.current;

    if (!background) {
      setLoading(true);
      setMessage('');
    }

    const params: BallotListParams = {
      feed,
      page: nextPage,
      size: PAGE_SIZE,
      query: query || undefined,
      category: category || undefined,
      status
    };

    try {
      const response = access === 'authenticated'
        ? await runAuthorizedRef.current(activeSession =>
            ballotApi.list(params, activeSession.accessToken, controller.signal))
        : await ballotApi.list(params, undefined, controller.signal);
      if (sequence !== requestSequence.current) return;

      setBallots(current => append ? mergeUniqueBallots(current, response.content) : response.content);
      if (!append) {
        setSelectedId(current => current && !response.content.some(ballot => ballot.id === current) ? null : current);
      }
      setPage(response.number);
      setTotalPages(response.totalPages);
      setTotalElements(response.totalElements);

      if (!append && nextPage === 0) setPendingEnrichmentKey(access === 'public' ? feedRequestKey : null);
    } catch (error) {
      if (sequence !== requestSequence.current || isAbortError(error)) return;
      if (!background) setMessage(error instanceof Error ? error.message : i18nRef.current.t('errors', 'loadBallots'));
    } finally {
      if (activeFeedRequest.current === controller) activeFeedRequest.current = null;
      if (sequence === requestSequence.current && !background) setLoading(false);
    }
  }, [category, feed, feedRequestKey, maintenance, query, status]);

  useEffect(() => {
    setPendingEnrichmentKey(null);
  }, [feedRequestKey]);

  useEffect(() => {
    if (feed === 'MINE') return;
    void load(0, false, 'auto');
    return cancelFeedRequest;
  }, [cancelFeedRequest, feed, load]);

  useEffect(() => {
    if (feed !== 'MINE' || restoring || !session) return;
    void load(0, false, 'authenticated');
    return cancelFeedRequest;
  }, [cancelFeedRequest, feed, load, restoring, session]);

  useEffect(() => {
    if (!pendingEnrichmentKey || pendingEnrichmentKey !== feedRequestKey || feed === 'MINE' || restoring || !session) return;
    setPendingEnrichmentKey(null);
    void load(0, false, 'authenticated', true);
  }, [feed, feedRequestKey, load, pendingEnrichmentKey, restoring, session]);

  useEffect(() => cancelFeedRequest, [cancelFeedRequest]);

  function markBusy(id: string, busy: boolean) {
    setBusyIds(current => {
      const next = new Set(current);
      if (busy) next.add(id); else next.delete(id);
      return next;
    });
  }

  async function linkProvider(provider: SocialProviderId) {
    if (!session || linkingProvider) return;
    if (systemModeRef.current !== 'NORMAL') {
      setSocialNotice(t('system', 'readOnlyActionUnavailable'));
      return;
    }
    setLinkingProvider(provider);
    setSocialNotice('');
    try {
      const response = await runAuthorized(activeSession =>
        socialAuthApi.startLink(provider, activeSession.accessToken));
      window.location.assign(response.authorizationUrl);
    } catch (error) {
      setSocialNotice(error instanceof Error ? error.message : t('auth', 'linkStartFailed'));
      setLinkingProvider(null);
    }
  }

  async function vote(ballot: Ballot, type: VoteType) {
    if (systemModeRef.current !== 'NORMAL') {
      setMessage(t('system', 'readOnlyVoteHint'));
      return;
    }
    if (!session) return openAuth('login');
    if (ballot.status === 'CLOSED' || busyIds.has(ballot.id)) return;

    const snapshot = ballot;
    markBusy(ballot.id, true);
    setBallots(current => current.map(item => item.id === ballot.id ? applyOptimisticVote(item, type) : item));

    try {
      const response = await runAuthorized(activeSession =>
        ballot.myVote === type
          ? ballotApi.removeVote(ballot.id, activeSession.accessToken)
          : ballotApi.castVote(ballot.id, type, activeSession.accessToken));
      setBallots(current => current.map(item =>
        item.id === ballot.id ? reconcileVoteResponse(item, response, snapshot.updatedAt) : item));
    } catch (error) {
      try {
        const authoritative = await runAuthorized(activeSession =>
          ballotApi.get(ballot.id, activeSession.accessToken));
        setBallots(current => current.map(item =>
          item.id === ballot.id ? reconcileAuthoritativeBallot(item, authoritative) : item));
      } catch {
        setBallots(current => current.map(item =>
          item.id === ballot.id ? rollbackVoteSnapshot(item, snapshot) : item));
      }
      setMessage(error instanceof ApiError && error.status === 429 && error.retryAfter !== undefined
        ? t('ballots', 'rateLimited', { seconds: formatNumber(error.retryAfter) })
        : error instanceof Error ? error.message : t('errors', 'recordVote'));
    } finally {
      markBusy(ballot.id, false);
    }
  }

  function assertWritesAvailable() {
    if (systemModeRef.current !== 'NORMAL') throw new Error(t('system', 'readOnlyActionUnavailable'));
  }

  async function createBallot(title: string, content: string) {
    assertWritesAvailable();
    await runAuthorized(activeSession => ballotApi.create({ title, content }, activeSession.accessToken));
    setCreateOpen(false);
    await load(0, false);
    setMessage(t('ballots', 'createdNotice'));
  }

  async function updateBallot(ballot: Ballot, title: string, content: string) {
    assertWritesAvailable();
    markBusy(ballot.id, true);
    try {
      const updated = await runAuthorized(activeSession =>
        ballotApi.update(ballot.id, { title, content }, activeSession.accessToken));
      setEditing(null);
      if (query) await load(0, false);
      else setBallots(current => current.map(item => item.id === ballot.id ? updated : item));
      setMessage(t('ballots', 'updatedNotice'));
    } finally {
      markBusy(ballot.id, false);
    }
  }

  async function deleteBallot(ballot: Ballot) {
    assertWritesAvailable();
    markBusy(ballot.id, true);
    try {
      await runAuthorized(activeSession => ballotApi.delete(ballot.id, activeSession.accessToken));
      setSelectedId(current => current === ballot.id ? null : current);
      await load(0, false);
      setMessage(t('ballots', 'deletedNotice'));
    } catch (error) {
      const failure = error instanceof Error ? error : new Error(t('errors', 'deleteBallot'));
      setMessage(failure.message);
      throw failure;
    } finally {
      markBusy(ballot.id, false);
    }
  }

  async function closeBallot(ballot: Ballot) {
    if (systemModeRef.current !== 'NORMAL') {
      setMessage(t('system', 'readOnlyActionUnavailable'));
      return;
    }
    if (!window.confirm(t('ballots', 'closeConfirm', { number: ballot.ballotNumber }))) return;
    markBusy(ballot.id, true);
    try {
      const closed = await runAuthorized(activeSession =>
        ballotApi.close(ballot.id, activeSession.accessToken));
      setBallots(current => current.map(item => item.id === ballot.id ? closed : item));
      if (status) await load(0, false);
      setMessage(t('ballots', 'closedNotice'));
    } catch (error) {
      setMessage(error instanceof Error ? error.message : t('errors', 'closeBallot'));
    } finally {
      markBusy(ballot.id, false);
    }
  }

  function clearFilters() {
    setQueryInput('');
    setCategoryInput('');
    setStatus(undefined);
  }

  async function confirmDelete() {
    if (!deleting) return;
    await deleteBallot(deleting);
    setDeleting(null);
    window.requestAnimationFrame(() => {
      document.getElementById('top')?.focus({ preventScroll: true });
    });
  }

  if (maintenance && systemStatus) {
    return <MaintenanceScreen status={systemStatus} refreshing={systemStatusRefreshing} onRetry={() => void reloadSystemStatus(true)} />;
  }

  return (
    <div className={styles.appShell}>
      <VoterMasthead
        query={queryInput}
        session={session}
        profile={profile}
        profileSnapshot={profileSnapshot}
        restoring={restoring}
        readOnly={readOnly}
        socialProviders={socialProviders}
        linkingProvider={linkingProvider}
        onQueryChange={setQueryInput}
        onLogin={() => openAuth('login')}
        onRegister={() => openAuth('register')}
        onCreate={() => {
          if (writesBlocked) return setMessage(t('system', 'readOnlyActionUnavailable'));
          session ? setCreateOpen(true) : openAuth('login', 'create-ballot');
        }}
        onLinkProvider={provider => void linkProvider(provider)}
        onLogout={() => void logout()}
        onLogoutAll={() => void logoutAll()}
      />

      {readOnly && systemStatus && <ReadOnlyBanner status={systemStatus} />}

      <main id="top" className={styles.main} tabIndex={-1}>
        <section className={styles.hero}>
          <p>{t('ballots', 'heroEyebrow')}</p>
          <h1>{t('ballots', 'heroTitle')}</h1>
          <span>{t('ballots', 'heroDescription')}</span>
        </section>

        <FeedControls
          feed={feed}
          category={categoryInput}
          status={status}
          authenticated={Boolean(session && profile)}
          loading={loading}
          totalElements={totalElements}
          page={page}
          totalPages={totalPages}
          query={queryInput}
          onFeedChange={setFeed}
          onCategoryChange={setCategoryInput}
          onStatusChange={setStatus}
          onReset={clearFilters}
        />

        {socialNotice && <div className={styles.notice} role="status">{socialNotice}</div>}
        {message && <div className={styles.notice} role="status">{message}</div>}
        {loading && ballots.length === 0 && <BallotSkeleton label={t('ballots', 'loadingBallots')} />}
        {!loading && ballots.length === 0 && (
          <div className={styles.empty}>
            <strong>{t('ballots', 'emptyTitle')}</strong>
            <span>{t('ballots', 'emptyDescription')}</span>
          </div>
        )}
        <section className={styles.feed} aria-live="polite" aria-busy={loading}>
          {ballots.map(ballot => (
            <BallotCard
              key={ballot.id}
              ballot={ballot}
              busy={busyIds.has(ballot.id)}
              owned={session?.userId === ballot.authorId}
              readOnly={readOnly}
              onOpen={() => setSelectedId(ballot.id)}
              onVote={type => void vote(ballot, type)}
              onEdit={() => setEditing(ballot)}
              onDelete={() => setDeleting(ballot)}
              onCloseBallot={() => void closeBallot(ballot)}
            />
          ))}
        </section>
        {!lastPage && (
          <button className={styles.loadMore} disabled={loading} onClick={() => void load(page + 1, true)}>
            {loading
              ? t('ballots', 'loadingPage')
              : t('ballots', 'loadPage', { page: formatNumber(page + 2), total: formatNumber(totalPages) })}
          </button>
        )}
      </main>

      {authWorkflow.open && (
        <AuthDialog
          initialMode={authWorkflow.mode}
          intent={authWorkflow.intent}
          socialProviders={socialProviders}
          allowRegistration={!readOnly}
          onClose={() => setAuthWorkflow(cancelAuth())}
          onAuthenticated={async next => {
            await saveSession(next);
            const completion = completeAuth(authWorkflow);
            setAuthWorkflow(completion.workflow);
            if (completion.resumeCreateBallot && systemModeRef.current === 'NORMAL') setCreateOpen(true);
          }}
        />
      )}
      {createOpen && !writesBlocked && <CreateBallotDialog onClose={() => setCreateOpen(false)} onCreate={createBallot} />}
      {editing && !writesBlocked && <EditBallotDialog ballot={editing} onClose={() => setEditing(null)} onSave={(title, content) => updateBallot(editing, title, content)} />}
      {selected && <BallotDetailDialog ballot={selected} busy={busyIds.has(selected.id)} owned={session?.userId === selected.authorId} readOnly={readOnly} onClose={() => setSelectedId(null)} onVote={type => void vote(selected, type)} onEdit={() => { setSelectedId(null); setEditing(selected); }} onDelete={() => { setSelectedId(null); setDeleting(selected); }} onCloseBallot={() => void closeBallot(selected)} />}
      {deleting && !writesBlocked && (
        <ConfirmDialog
          title={t('ballots', 'deleteTitle')}
          reference={`${deleting.ballotNumber} · ${deleting.title}`}
          description={t('ballots', 'deleteDescription')}
          confirmLabel={t('ballots', 'deleteConfirm')}
          pendingLabel={t('ballots', 'deleting')}
          onClose={() => setDeleting(null)}
          onConfirm={confirmDelete}
        />
      )}
    </div>
  );
}

function BallotSkeleton({ label }: { label: string }) {
  return <div className={styles.skeleton} aria-label={label}><span /><span /><span /></div>;
}

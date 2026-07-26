'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { AuthDialog } from '@/features/auth/components/AuthDialog';
import { VoterMasthead } from '@/features/auth/components/VoterMasthead';
import { useSession } from '@/features/auth/hooks/useSession';
import { useBallotVoteStream } from '@/features/ballots/hooks/useBallotVoteStream';
import { ballotApi, type BallotListParams } from '@/shared/api/ballot-api';
import { socialAuthApi, type SocialProviderId } from '@/shared/api/social-auth-api';
import { ApiError } from '@/shared/api/transport';
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
import { useDebouncedValue } from '@/shared/hooks/useDebouncedValue';
import { BallotCard } from './BallotCard';
import { BallotDetailDialog } from './BallotDetailDialog';
import { CreateBallotDialog } from './CreateBallotDialog';
import { EditBallotDialog } from './EditBallotDialog';
import { FeedControls } from './FeedControls';
import styles from './BallotApp.module.scss';

const PAGE_SIZE = 8;

export function BallotApp() {
  const { session, profile, restoring, saveSession, runAuthorized, logout, logoutAll } = useSession();
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
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const requestSequence = useRef(0);

  const selected = selectedId ? ballots.find(ballot => ballot.id === selectedId) ?? null : null;
  const lastPage = totalPages === 0 || page + 1 >= totalPages;

  const handleStreamUpdate = useCallback((update: BallotVoteUpdate) => {
    setBallots(current => current.map(ballot =>
      ballot.id === update.postId ? applyStreamUpdate(ballot, update) : ballot));
  }, []);
  useBallotVoteStream(selected, handleStreamUpdate);

  const openAuth = useCallback((mode: AuthMode, intent: AuthIntent = 'authenticate') => {
    setAuthWorkflow(beginAuth(mode, intent));
  }, []);

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
    if (callback.status === 'error') {
      setSocialNotice(socialCallbackMessage(callback));
    }
  }, []);

  useEffect(() => {
    if (!socialCallback || socialCallback.status === 'error' || restoring) return;
    if (!session || !profile) {
      setSocialNotice('Social authentication completed, but the Vote System session could not be restored.');
      setSocialCallback(null);
      return;
    }

    setSocialNotice(socialCallbackMessage(socialCallback));
    if (socialCallback.status === 'success' && socialCallback.intent === 'create-ballot') {
      setCreateOpen(true);
    }
    setSocialCallback(null);
  }, [profile, restoring, session, socialCallback]);

  useEffect(() => {
    if (!restoring && feed === 'MINE' && !session) setFeed('LATEST');
  }, [feed, restoring, session]);

  const load = useCallback(async (nextPage = 0, append = false) => {
    if (restoring) return;
    if (feed === 'MINE' && !session) return;

    const sequence = ++requestSequence.current;
    setLoading(true);
    setMessage('');

    const params: BallotListParams = {
      feed,
      page: nextPage,
      size: PAGE_SIZE,
      query: query || undefined,
      category: category || undefined,
      status
    };

    try {
      const response = session
        ? await runAuthorized(activeSession => ballotApi.list(params, activeSession.accessToken))
        : await ballotApi.list(params);
      if (sequence !== requestSequence.current) return;

      setBallots(current => append ? mergeUniqueBallots(current, response.content) : response.content);
      if (!append) {
        setSelectedId(current => current && !response.content.some(ballot => ballot.id === current) ? null : current);
      }
      setPage(response.number);
      setTotalPages(response.totalPages);
      setTotalElements(response.totalElements);
    } catch (error) {
      if (sequence !== requestSequence.current) return;
      setMessage(error instanceof Error ? error.message : 'Không thể tải sổ phiếu.');
    } finally {
      if (sequence === requestSequence.current) setLoading(false);
    }
  }, [category, feed, query, restoring, runAuthorized, session, status]);

  useEffect(() => {
    void load(0, false);
    return () => { requestSequence.current += 1; };
  }, [load]);

  function markBusy(id: string, busy: boolean) {
    setBusyIds(current => {
      const next = new Set(current);
      if (busy) next.add(id); else next.delete(id);
      return next;
    });
  }

  async function linkProvider(provider: SocialProviderId) {
    if (!session || linkingProvider) return;
    setLinkingProvider(provider);
    setSocialNotice('');
    try {
      const response = await runAuthorized(activeSession =>
        socialAuthApi.startLink(provider, activeSession.accessToken));
      window.location.assign(response.authorizationUrl);
    } catch (error) {
      setSocialNotice(error instanceof Error ? error.message : 'Unable to start account linking.');
      setLinkingProvider(null);
    }
  }

  async function vote(ballot: Ballot, type: VoteType) {
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
        ? `Đã đạt giới hạn. Thử lại sau ${error.retryAfter} giây.`
        : error instanceof Error ? error.message : 'Không thể ghi nhận phiếu.');
    } finally {
      markBusy(ballot.id, false);
    }
  }

  async function createBallot(title: string, content: string) {
    await runAuthorized(activeSession => ballotApi.create({ title, content }, activeSession.accessToken));
    setCreateOpen(false);
    await load(0, false);
    setMessage('Hồ sơ đã được ghi vào sổ công khai. Kết quả hiện tại đã được tải lại từ máy chủ.');
  }

  async function updateBallot(ballot: Ballot, title: string, content: string) {
    markBusy(ballot.id, true);
    try {
      const updated = await runAuthorized(activeSession =>
        ballotApi.update(ballot.id, { title, content }, activeSession.accessToken));
      setEditing(null);
      if (query) {
        await load(0, false);
      } else {
        setBallots(current => current.map(item => item.id === ballot.id ? updated : item));
      }
      setMessage('Hồ sơ đã được cập nhật.');
    } finally {
      markBusy(ballot.id, false);
    }
  }

  async function deleteBallot(ballot: Ballot) {
    if (!window.confirm(`Delete ballot ${ballot.ballotNumber}? This cannot be undone.`)) return;
    markBusy(ballot.id, true);
    try {
      await runAuthorized(activeSession => ballotApi.delete(ballot.id, activeSession.accessToken));
      setSelectedId(current => current === ballot.id ? null : current);
      await load(0, false);
      setMessage('Hồ sơ đã được xóa khỏi sổ công khai.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Không thể xóa hồ sơ.');
    } finally {
      markBusy(ballot.id, false);
    }
  }

  async function closeBallot(ballot: Ballot) {
    if (!window.confirm(`Close ballot ${ballot.ballotNumber}? Voting will stop permanently.`)) return;
    markBusy(ballot.id, true);
    try {
      const closed = await runAuthorized(activeSession =>
        ballotApi.close(ballot.id, activeSession.accessToken));
      setBallots(current => current.map(item => item.id === ballot.id ? closed : item));
      if (status) await load(0, false);
      setMessage('Lá phiếu đã được đóng và kết quả cuối cùng đã được chốt.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Không thể đóng lá phiếu.');
    } finally {
      markBusy(ballot.id, false);
    }
  }

  function clearFilters() {
    setQueryInput('');
    setCategoryInput('');
    setStatus(undefined);
  }

  return (
    <div className={styles.appShell}>
      <VoterMasthead
        query={queryInput}
        session={session}
        profile={profile}
        restoring={restoring}
        socialProviders={socialProviders}
        linkingProvider={linkingProvider}
        onQueryChange={setQueryInput}
        onLogin={() => openAuth('login')}
        onRegister={() => openAuth('register')}
        onCreate={() => session ? setCreateOpen(true) : openAuth('login', 'create-ballot')}
        onLinkProvider={provider => void linkProvider(provider)}
        onLogout={() => void logout()}
        onLogoutAll={() => void logoutAll()}
      />

      <main id="top" className={styles.main}>
        <section className={styles.hero}>
          <p>PUBLIC DECISION REGISTRY · SERVER INDEX</p>
          <h1>Official public record</h1>
          <span>Search the complete registry, inspect ranked feeds, and cast a recorded decision.</span>
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
        {loading && ballots.length === 0 && <BallotSkeleton />}
        {!loading && ballots.length === 0 && (
          <div className={styles.empty}>
            <strong>NO SERVER RECORDS FOUND</strong>
            <span>Không có hồ sơ phù hợp với feed và bộ lọc hiện tại.</span>
          </div>
        )}
        <section className={styles.feed} aria-live="polite" aria-busy={loading}>
          {ballots.map(ballot => (
            <BallotCard
              key={ballot.id}
              ballot={ballot}
              busy={busyIds.has(ballot.id)}
              owned={session?.userId === ballot.authorId}
              onOpen={() => setSelectedId(ballot.id)}
              onVote={type => void vote(ballot, type)}
              onEdit={() => setEditing(ballot)}
              onDelete={() => void deleteBallot(ballot)}
              onCloseBallot={() => void closeBallot(ballot)}
            />
          ))}
        </section>
        {!lastPage && (
          <button className={styles.loadMore} disabled={loading} onClick={() => void load(page + 1, true)}>
            {loading ? 'LOADING SERVER PAGE...' : `LOAD PAGE ${page + 2} OF ${totalPages}`}
          </button>
        )}
      </main>

      {authWorkflow.open && (
        <AuthDialog
          initialMode={authWorkflow.mode}
          intent={authWorkflow.intent}
          socialProviders={socialProviders}
          onClose={() => setAuthWorkflow(cancelAuth())}
          onAuthenticated={async next => {
            await saveSession(next);
            const completion = completeAuth(authWorkflow);
            setAuthWorkflow(completion.workflow);
            if (completion.resumeCreateBallot) setCreateOpen(true);
          }}
        />
      )}
      {createOpen && <CreateBallotDialog onClose={() => setCreateOpen(false)} onCreate={createBallot} />}
      {editing && <EditBallotDialog ballot={editing} onClose={() => setEditing(null)} onSave={(title, content) => updateBallot(editing, title, content)} />}
      {selected && <BallotDetailDialog ballot={selected} busy={busyIds.has(selected.id)} owned={session?.userId === selected.authorId} onClose={() => setSelectedId(null)} onVote={type => void vote(selected, type)} onEdit={() => { setSelectedId(null); setEditing(selected); }} onDelete={() => void deleteBallot(selected)} onCloseBallot={() => void closeBallot(selected)} />}
    </div>
  );
}

function BallotSkeleton() {
  return <div className={styles.skeleton} aria-label="Loading ballots"><span /><span /><span /></div>;
}

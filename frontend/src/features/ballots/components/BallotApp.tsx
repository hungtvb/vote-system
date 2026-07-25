'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AuthDialog } from '@/features/auth/components/AuthDialog';
import { VoterMasthead } from '@/features/auth/components/VoterMasthead';
import { useSession } from '@/features/auth/hooks/useSession';
import { ballotApi } from '@/shared/api/ballot-api';
import { ApiError } from '@/shared/api/transport';
import type { Ballot, FeedType, VoteResponse, VoteType } from '@/shared/api/types';
import { BallotCard } from './BallotCard';
import { BallotDetailDialog } from './BallotDetailDialog';
import { CreateBallotDialog } from './CreateBallotDialog';
import { EditBallotDialog } from './EditBallotDialog';
import styles from './BallotApp.module.scss';

const PAGE_SIZE = 8;
type AuthMode = 'login' | 'register';

function applyVoteResponse(ballot: Ballot, response: VoteResponse): Ballot {
  return { ...ballot, ...response, id: ballot.id };
}

function optimisticVote(ballot: Ballot, type: VoteType): Ballot {
  let upVotes = ballot.upVotes;
  let downVotes = ballot.downVotes;
  const removing = ballot.myVote === type;
  if (ballot.myVote === 'UP') upVotes = Math.max(0, upVotes - 1);
  if (ballot.myVote === 'DOWN') downVotes = Math.max(0, downVotes - 1);
  if (!removing && type === 'UP') upVotes += 1;
  if (!removing && type === 'DOWN') downVotes += 1;
  return { ...ballot, upVotes, downVotes, totalVotes: upVotes + downVotes, voteScore: upVotes - downVotes, myVote: removing ? undefined : type };
}

export function BallotApp() {
  const { session, profile, restoring, saveSession, runAuthorized, logout, logoutAll } = useSession();
  const [ballots, setBallots] = useState<Ballot[]>([]);
  const [feed, setFeed] = useState<FeedType>('LATEST');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [lastPage, setLastPage] = useState(true);
  const [loading, setLoading] = useState(true);
  const [busyIds, setBusyIds] = useState<Set<string>>(() => new Set());
  const [message, setMessage] = useState('');
  const [authOpen, setAuthOpen] = useState(false);
  const [authMode, setAuthMode] = useState<AuthMode>('login');
  const [createOpen, setCreateOpen] = useState(false);
  const [editing, setEditing] = useState<Ballot | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const requestSequence = useRef(0);

  const selected = selectedId ? ballots.find(ballot => ballot.id === selectedId) ?? null : null;

  const openAuth = useCallback((mode: AuthMode) => {
    setAuthMode(mode);
    setAuthOpen(true);
  }, []);

  const load = useCallback(async (nextPage = 0, append = false) => {
    if (restoring) return;
    const sequence = ++requestSequence.current;
    setLoading(true);
    setMessage('');
    try {
      const response = session
        ? await runAuthorized(active => ballotApi.list(feed, nextPage, PAGE_SIZE, active.accessToken))
        : await ballotApi.list(feed, nextPage, PAGE_SIZE);
      if (sequence !== requestSequence.current) return;
      setBallots(current => append ? [...current, ...response.content.filter(item => !current.some(existing => existing.id === item.id))] : response.content);
      setPage(response.number);
      setLastPage(response.last);
    } catch (error) {
      if (sequence !== requestSequence.current) return;
      setMessage(error instanceof Error ? error.message : 'Không thể tải sổ phiếu.');
    } finally {
      if (sequence === requestSequence.current) setLoading(false);
    }
  }, [feed, restoring, runAuthorized, session]);

  useEffect(() => {
    void load();
    return () => { requestSequence.current += 1; };
  }, [load]);

  const visible = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) return ballots;
    return ballots.filter(ballot => `${ballot.title} ${ballot.content} ${ballot.ballotNumber} ${ballot.author.displayName}`.toLowerCase().includes(normalized));
  }, [ballots, query]);

  function markBusy(id: string, busy: boolean) {
    setBusyIds(current => {
      const next = new Set(current);
      if (busy) next.add(id); else next.delete(id);
      return next;
    });
  }

  async function vote(ballot: Ballot, type: VoteType) {
    if (!session) return openAuth('login');
    if (ballot.status === 'CLOSED' || busyIds.has(ballot.id)) return;
    const snapshot = ballot;
    markBusy(ballot.id, true);
    setBallots(current => current.map(item => item.id === ballot.id ? optimisticVote(item, type) : item));
    try {
      const response = await runAuthorized(active =>
        ballot.myVote === type
          ? ballotApi.removeVote(ballot.id, active.accessToken)
          : ballotApi.castVote(ballot.id, type, active.accessToken));
      setBallots(current => current.map(item => item.id === ballot.id ? applyVoteResponse(item, response) : item));
    } catch (error) {
      setBallots(current => current.map(item => item.id === ballot.id ? snapshot : item));
      setMessage(error instanceof ApiError && error.status === 429 && error.retryAfter !== undefined ? `Đã đạt giới hạn. Thử lại sau ${error.retryAfter} giây.` : error instanceof Error ? error.message : 'Không thể ghi nhận phiếu.');
    } finally {
      markBusy(ballot.id, false);
    }
  }

  async function createBallot(title: string, content: string) {
    await runAuthorized(active => ballotApi.create({ title, content }, active.accessToken));
    setCreateOpen(false);
    await load(0, false);
    setMessage('Hồ sơ đã được ghi vào sổ công khai. Feed hiện tại đã được tải lại từ máy chủ.');
  }

  async function updateBallot(ballot: Ballot, title: string, content: string) {
    markBusy(ballot.id, true);
    try {
      const updated = await runAuthorized(active =>
        ballotApi.update(ballot.id, { title, content }, active.accessToken));
      setBallots(current => current.map(item => item.id === ballot.id ? updated : item));
      setEditing(null);
      setMessage('Hồ sơ đã được cập nhật.');
    } finally {
      markBusy(ballot.id, false);
    }
  }

  async function deleteBallot(ballot: Ballot) {
    if (!window.confirm(`Delete ballot ${ballot.ballotNumber}? This cannot be undone.`)) return;
    markBusy(ballot.id, true);
    try {
      await runAuthorized(active => ballotApi.delete(ballot.id, active.accessToken));
      setBallots(current => current.filter(item => item.id !== ballot.id));
      setSelectedId(current => current === ballot.id ? null : current);
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
      const closed = await runAuthorized(active => ballotApi.close(ballot.id, active.accessToken));
      setBallots(current => current.map(item => item.id === ballot.id ? closed : item));
      setMessage('Lá phiếu đã được đóng và kết quả cuối cùng đã được chốt.');
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Không thể đóng lá phiếu.');
    } finally {
      markBusy(ballot.id, false);
    }
  }

  return (
    <div className={styles.appShell}>
      <VoterMasthead
        query={query}
        session={session}
        profile={profile}
        restoring={restoring}
        onQueryChange={setQuery}
        onLogin={() => openAuth('login')}
        onRegister={() => openAuth('register')}
        onCreate={() => session ? setCreateOpen(true) : openAuth('register')}
        onLogout={() => void logout()}
        onLogoutAll={() => void logoutAll()}
      />

      <main id="top" className={styles.main}>
        <section className={styles.hero}><p>PUBLIC DECISION REGISTRY · CURRENT SESSION</p><h1>Official public record</h1><span>Browse active ballots, inspect totals, and cast a recorded decision.</span></section>
        <nav className={styles.feedTabs} aria-label="Feed mode">{(['LATEST', 'HOT', 'TOP_DAY', 'TOP_WEEK'] as FeedType[]).map(item => <button key={item} className={feed === item ? styles.activeTab : ''} onClick={() => setFeed(item)}>{item.replace('_', ' ')}</button>)}</nav>
        {message && <div className={styles.notice} role="status">{message}</div>}
        {loading && ballots.length === 0 && <BallotSkeleton />}
        {!loading && visible.length === 0 && <div className={styles.empty}><strong>NO RECORDS FOUND</strong><span>Không có hồ sơ phù hợp với điều kiện hiện tại.</span></div>}
        <section className={styles.feed} aria-live="polite">{visible.map(ballot => <BallotCard key={ballot.id} ballot={ballot} busy={busyIds.has(ballot.id)} owned={session?.userId === ballot.authorId} onOpen={() => setSelectedId(ballot.id)} onVote={type => void vote(ballot, type)} onEdit={() => setEditing(ballot)} onDelete={() => void deleteBallot(ballot)} onCloseBallot={() => void closeBallot(ballot)} />)}</section>
        {!lastPage && <button className={styles.loadMore} disabled={loading} onClick={() => void load(page + 1, true)}>{loading ? 'LOADING...' : 'LOAD MORE RECORDS'}</button>}
      </main>

      {authOpen && <AuthDialog initialMode={authMode} onClose={() => setAuthOpen(false)} onAuthenticated={async next => { await saveSession(next); setAuthOpen(false); }} />}
      {createOpen && <CreateBallotDialog onClose={() => setCreateOpen(false)} onCreate={createBallot} />}
      {editing && <EditBallotDialog ballot={editing} onClose={() => setEditing(null)} onSave={(title, content) => updateBallot(editing, title, content)} />}
      {selected && <BallotDetailDialog ballot={selected} busy={busyIds.has(selected.id)} owned={session?.userId === selected.authorId} onClose={() => setSelectedId(null)} onVote={type => void vote(selected, type)} onEdit={() => { setSelectedId(null); setEditing(selected); }} onDelete={() => void deleteBallot(selected)} onCloseBallot={() => void closeBallot(selected)} />}
    </div>
  );
}

function BallotSkeleton() { return <div className={styles.skeleton} aria-label="Loading ballots"><span /><span /><span /></div>; }

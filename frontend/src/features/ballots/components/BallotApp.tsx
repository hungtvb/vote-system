'use client';

import { useCallback, useMemo, useState } from 'react';
import { AuthDialog } from '@/features/auth/components/AuthDialog';
import { useSession } from '@/features/auth/hooks/useSession';
import { api, ApiError } from '@/shared/api/client';
import type { Ballot, FeedType, Session, VoteResponse, VoteType } from '@/shared/api/types';
import { BallotCard } from './BallotCard';
import { CreateBallotDialog } from './CreateBallotDialog';
import styles from './BallotApp.module.scss';

const PAGE_SIZE = 8;

function applyVoteResponse(ballot: Ballot, response: VoteResponse): Ballot {
  return { ...ballot, ...response, id: ballot.id };
}

function optimisticVote(ballot: Ballot, type: VoteType): Ballot {
  let upVotes = ballot.upVotes;
  let downVotes = ballot.downVotes;
  const removing = ballot.myVote === type;
  if (ballot.myVote === 'UP') upVotes -= 1;
  if (ballot.myVote === 'DOWN') downVotes -= 1;
  if (!removing && type === 'UP') upVotes += 1;
  if (!removing && type === 'DOWN') downVotes += 1;
  return { ...ballot, upVotes, downVotes, totalVotes: upVotes + downVotes, voteScore: upVotes - downVotes, myVote: removing ? undefined : type };
}

export function BallotApp() {
  const { session, restoring, saveSession, clearSession, logout, logoutAll } = useSession();
  const [ballots, setBallots] = useState<Ballot[]>([]);
  const [feed, setFeed] = useState<FeedType>('LATEST');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [lastPage, setLastPage] = useState(true);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [message, setMessage] = useState('');
  const [authOpen, setAuthOpen] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);

  const withRefresh = useCallback(async <T,>(operation: (activeSession: Session | null) => Promise<T>) => {
    try {
      return await operation(session);
    } catch (error) {
      if (!(error instanceof ApiError) || error.status !== 401 || !session) throw error;
      try {
        const refreshed = await api.refresh();
        saveSession(refreshed);
        return await operation(refreshed);
      } catch (refreshError) {
        clearSession();
        throw refreshError;
      }
    }
  }, [clearSession, saveSession, session]);

  const load = useCallback(async (nextPage = 0, append = false) => {
    if (restoring) return;
    setLoading(true);
    setMessage('');
    try {
      const response = await withRefresh(active => api.listBallots(feed, nextPage, PAGE_SIZE, active?.accessToken));
      setBallots(current => append ? [...current, ...response.content] : response.content);
      setPage(response.number);
      setLastPage(response.last);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Không thể tải sổ phiếu.');
    } finally {
      setLoading(false);
    }
  }, [feed, restoring, withRefresh]);

  useState(() => { void load(); });

  const visible = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) return ballots;
    return ballots.filter(ballot => `${ballot.title} ${ballot.content} ${ballot.ballotNumber}`.toLowerCase().includes(normalized));
  }, [ballots, query]);

  async function vote(ballot: Ballot, type: VoteType) {
    if (!session) return setAuthOpen(true);
    if (ballot.status === 'CLOSED') return;
    const snapshot = ballot;
    setBusyId(ballot.id);
    setBallots(current => current.map(item => item.id === ballot.id ? optimisticVote(item, type) : item));
    try {
      const response = await withRefresh(active => {
        if (!active) throw new ApiError('Authentication required.', 401);
        return ballot.myVote === type ? api.removeVote(ballot.id, active.accessToken) : api.castVote(ballot.id, type, active.accessToken);
      });
      setBallots(current => current.map(item => item.id === ballot.id ? applyVoteResponse(item, response) : item));
    } catch (error) {
      setBallots(current => current.map(item => item.id === ballot.id ? snapshot : item));
      setMessage(error instanceof ApiError && error.status === 429 && error.retryAfter ? `Đã đạt giới hạn. Thử lại sau ${error.retryAfter} giây.` : error instanceof Error ? error.message : 'Không thể ghi nhận phiếu.');
    } finally {
      setBusyId(null);
    }
  }

  async function createBallot(title: string, content: string) {
    const created = await withRefresh(active => {
      if (!active) throw new ApiError('Authentication required.', 401);
      return api.createBallot({ title, content }, active.accessToken);
    });
    setBallots(current => [created, ...current]);
    setCreateOpen(false);
    setMessage('Hồ sơ đã được ghi vào sổ công khai.');
  }

  return (
    <div className={styles.appShell}>
      <header className={styles.header}>
        <a className={styles.brand} href="#top" aria-label="Vote System home"><span className={styles.seal}>VS</span><span>Vote System</span></a>
        <label className={styles.search}><span>SEARCH LOADED RECORDS</span><input value={query} onChange={event => setQuery(event.target.value)} placeholder="Tìm trong các hồ sơ đã tải..." /></label>
        <div className={styles.headerActions}>
          <button className={styles.textButton} onClick={() => session ? void logout() : setAuthOpen(true)}>{session ? 'LOGOUT' : 'LOGIN'}</button>
          {session && <button className={styles.textButton} onClick={() => void logoutAll()}>LOGOUT ALL</button>}
          <button className={styles.primaryButton} onClick={() => session ? setCreateOpen(true) : setAuthOpen(true)}>CREATE POST</button>
          {session && <div className={styles.voterId}><strong>VOTER ID</strong><span>{session.email}</span></div>}
        </div>
      </header>

      <main id="top" className={styles.main}>
        <section className={styles.hero}><p>PUBLIC DECISION REGISTRY · CURRENT SESSION</p><h1>Official public record</h1><span>Browse active ballots, inspect totals, and cast a recorded decision.</span></section>
        <nav className={styles.feedTabs} aria-label="Feed mode">{(['LATEST', 'HOT', 'TOP_DAY', 'TOP_WEEK'] as FeedType[]).map(item => <button key={item} className={feed === item ? styles.activeTab : ''} onClick={() => setFeed(item)}>{item.replace('_', ' ')}</button>)}</nav>
        {message && <div className={styles.notice} role="status">{message}</div>}
        {loading && ballots.length === 0 && <BallotSkeleton />}
        {!loading && visible.length === 0 && <div className={styles.empty}><strong>NO RECORDS FOUND</strong><span>Không có hồ sơ phù hợp với điều kiện hiện tại.</span></div>}
        <section className={styles.feed} aria-live="polite">{visible.map(ballot => <BallotCard key={ballot.id} ballot={ballot} busy={busyId === ballot.id} onVote={type => void vote(ballot, type)} />)}</section>
        {!lastPage && <button className={styles.loadMore} disabled={loading} onClick={() => void load(page + 1, true)}>{loading ? 'LOADING...' : 'LOAD MORE RECORDS'}</button>}
      </main>

      {authOpen && <AuthDialog onClose={() => setAuthOpen(false)} onAuthenticated={next => { saveSession(next); setAuthOpen(false); }} />}
      {createOpen && <CreateBallotDialog onClose={() => setCreateOpen(false)} onCreate={createBallot} />}
    </div>
  );
}

function BallotSkeleton() { return <div className={styles.skeleton} aria-label="Loading ballots"><span /><span /><span /></div>; }

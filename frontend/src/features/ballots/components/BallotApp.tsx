'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { api, ApiError } from '@/shared/api/client';
import type { Ballot, FeedType, Session, VoteResponse, VoteType } from '@/shared/api/types';
import styles from './BallotApp.module.scss';

const PAGE_SIZE = 8;
const SESSION_KEY = 'vote-system.session';

function applyVoteResponse(ballot: Ballot, response: VoteResponse): Ballot {
  return {
    ...ballot,
    voteScore: response.voteScore,
    upVotes: response.upVotes,
    downVotes: response.downVotes,
    totalVotes: response.totalVotes,
    myVote: response.myVote,
    verdictThreshold: response.verdictThreshold,
    verdict: response.verdict
  };
}

function optimisticVote(ballot: Ballot, type: VoteType): Ballot {
  let upVotes = ballot.upVotes;
  let downVotes = ballot.downVotes;
  const removing = ballot.myVote === type;

  if (ballot.myVote === 'UP') upVotes -= 1;
  if (ballot.myVote === 'DOWN') downVotes -= 1;
  if (!removing && type === 'UP') upVotes += 1;
  if (!removing && type === 'DOWN') downVotes += 1;

  return {
    ...ballot,
    upVotes,
    downVotes,
    totalVotes: upVotes + downVotes,
    voteScore: upVotes - downVotes,
    myVote: removing ? undefined : type
  };
}

export function BallotApp() {
  const [session, setSession] = useState<Session | null>(null);
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

  useEffect(() => {
    const stored = window.localStorage.getItem(SESSION_KEY);
    if (stored) {
      try { setSession(JSON.parse(stored) as Session); } catch { window.localStorage.removeItem(SESSION_KEY); }
    }
  }, []);

  const load = useCallback(async (nextPage = 0, append = false) => {
    setLoading(true);
    setMessage('');
    try {
      const response = await api.listBallots(feed, nextPage, PAGE_SIZE, session?.accessToken);
      setBallots(current => append ? [...current, ...response.content] : response.content);
      setPage(response.number);
      setLastPage(response.last);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Không thể tải sổ phiếu.');
    } finally {
      setLoading(false);
    }
  }, [feed, session?.accessToken]);

  useEffect(() => { void load(); }, [load]);

  const visible = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) return ballots;
    return ballots.filter(ballot => `${ballot.title} ${ballot.content} ${ballot.ballotNumber}`.toLowerCase().includes(normalized));
  }, [ballots, query]);

  function saveSession(next: Session) {
    setSession(next);
    window.localStorage.setItem(SESSION_KEY, JSON.stringify(next));
    setAuthOpen(false);
  }

  async function logout() {
    if (session) {
      try { await api.logout(session.accessToken); } catch { /* local logout still proceeds */ }
    }
    window.localStorage.removeItem(SESSION_KEY);
    setSession(null);
  }

  async function vote(ballot: Ballot, type: VoteType) {
    if (!session) return setAuthOpen(true);
    if (ballot.status === 'CLOSED') return;

    const snapshot = ballot;
    setBusyId(ballot.id);
    setBallots(current => current.map(item => item.id === ballot.id ? optimisticVote(item, type) : item));

    try {
      const response = ballot.myVote === type
        ? await api.removeVote(ballot.id, session.accessToken)
        : await api.castVote(ballot.id, type, session.accessToken);
      setBallots(current => current.map(item => item.id === ballot.id ? applyVoteResponse(item, response) : item));
    } catch (error) {
      setBallots(current => current.map(item => item.id === ballot.id ? snapshot : item));
      const text = error instanceof ApiError && error.status === 429 && error.retryAfter
        ? `Đã đạt giới hạn. Thử lại sau ${error.retryAfter} giây.`
        : error instanceof Error ? error.message : 'Không thể ghi nhận phiếu.';
      setMessage(text);
    } finally {
      setBusyId(null);
    }
  }

  async function createBallot(title: string, content: string) {
    if (!session) return setAuthOpen(true);
    const created = await api.createBallot({ title, content }, session.accessToken);
    setBallots(current => [created, ...current]);
    setCreateOpen(false);
    setMessage('Hồ sơ đã được ghi vào sổ công khai.');
  }

  return (
    <div className={styles.appShell}>
      <header className={styles.header}>
        <a className={styles.brand} href="#top" aria-label="Vote System home">
          <span className={styles.seal}>VS</span><span>Vote System</span>
        </a>
        <label className={styles.search}>
          <span>SEARCH ARCHIVES</span>
          <input value={query} onChange={event => setQuery(event.target.value)} placeholder="Tìm hồ sơ, số phiếu..." />
        </label>
        <div className={styles.headerActions}>
          <button className={styles.textButton} onClick={() => session ? void logout() : setAuthOpen(true)}>{session ? 'LOGOUT' : 'LOGIN'}</button>
          <button className={styles.primaryButton} onClick={() => session ? setCreateOpen(true) : setAuthOpen(true)}>CREATE POST</button>
          {session && <div className={styles.voterId}><strong>VOTER ID</strong><span>{session.email}</span></div>}
        </div>
      </header>

      <main id="top" className={styles.main}>
        <section className={styles.hero}>
          <p>PUBLIC DECISION REGISTRY · CURRENT SESSION</p>
          <h1>Official public record</h1>
          <span>Browse active ballots, inspect totals, and cast a recorded decision.</span>
        </section>

        <nav className={styles.feedTabs} aria-label="Feed mode">
          {(['LATEST', 'HOT', 'TOP_DAY', 'TOP_WEEK'] as FeedType[]).map(item => (
            <button key={item} className={feed === item ? styles.activeTab : ''} onClick={() => setFeed(item)}>{item.replace('_', ' ')}</button>
          ))}
        </nav>

        {message && <div className={styles.notice} role="status">{message}</div>}
        {loading && ballots.length === 0 && <BallotSkeleton />}
        {!loading && visible.length === 0 && <div className={styles.empty}><strong>NO RECORDS FOUND</strong><span>Không có hồ sơ phù hợp với điều kiện hiện tại.</span></div>}

        <section className={styles.feed} aria-live="polite">
          {visible.map(ballot => (
            <article key={ballot.id} className={styles.ballotCard} aria-busy={busyId === ballot.id}>
              <header className={styles.cardHeader}>
                <div><span>{ballot.ballotNumber}</span><span>{new Date(ballot.createdAt).toLocaleDateString('vi-VN')}</span></div>
                <span className={ballot.status === 'CLOSED' ? styles.closed : styles.open}>{ballot.status}</span>
              </header>
              <div className={styles.cardBody}>
                <div className={styles.copy}>
                  <p className={styles.category}>{ballot.category}</p>
                  <h2>{ballot.title}</h2>
                  <p>{ballot.content}</p>
                  <div className={styles.meta}>AUTHOR ID: {ballot.authorId.slice(0, 8).toUpperCase()} · {ballot.totalVotes} REGISTERED VOTES</div>
                </div>
                <VoteControl ballot={ballot} busy={busyId === ballot.id} onVote={type => void vote(ballot, type)} />
              </div>
              {ballot.verdict !== 'UNDECIDED' && <div className={styles.verdict}>{ballot.finalVerdict ? 'FINAL' : 'CURRENT'} VERDICT: {ballot.verdict}</div>}
            </article>
          ))}
        </section>

        {!lastPage && <button className={styles.loadMore} disabled={loading} onClick={() => void load(page + 1, true)}>{loading ? 'LOADING...' : 'LOAD MORE RECORDS'}</button>}
      </main>

      {authOpen && <AuthDialog onClose={() => setAuthOpen(false)} onAuthenticated={saveSession} />}
      {createOpen && <CreateDialog onClose={() => setCreateOpen(false)} onCreate={createBallot} />}
    </div>
  );
}

function VoteControl({ ballot, busy, onVote }: { ballot: Ballot; busy: boolean; onVote: (type: VoteType) => void }) {
  return (
    <div className={styles.votePanel}>
      <span className={styles.voteLabel}>OFFICIAL BALLOT</span>
      <div className={styles.counter}>{ballot.voteScore >= 0 ? '+' : ''}{ballot.voteScore.toString().padStart(4, '0')}</div>
      <button disabled={busy || ballot.status === 'CLOSED'} className={ballot.myVote === 'UP' ? styles.selectedUp : ''} onClick={() => onVote('UP')}><span>ENDORSE</span><b>{ballot.upVotes}</b><i>{ballot.myVote === 'UP' ? '✓' : '○'}</i></button>
      <button disabled={busy || ballot.status === 'CLOSED'} className={ballot.myVote === 'DOWN' ? styles.selectedDown : ''} onClick={() => onVote('DOWN')}><span>REJECT</span><b>{ballot.downVotes}</b><i>{ballot.myVote === 'DOWN' ? '×' : '○'}</i></button>
    </div>
  );
}

function AuthDialog({ onClose, onAuthenticated }: { onClose: () => void; onAuthenticated: (session: Session) => void }) {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (mode === 'register' && password !== confirm) return setError('Mật khẩu xác nhận không khớp.');
    setBusy(true); setError('');
    try {
      const session = mode === 'login' ? await api.login(email, password) : await api.register(email, password);
      onAuthenticated(session);
    } catch (caught) { setError(caught instanceof Error ? caught.message : 'Authorization denied.'); }
    finally { setBusy(false); }
  }

  return <div className={styles.backdrop} onMouseDown={event => event.target === event.currentTarget && onClose()}><section className={styles.dialog} role="dialog" aria-modal="true"><div className={styles.dialogTabs}><button onClick={() => setMode('login')}>REGISTRY ACCESS</button><button onClick={() => setMode('register')}>NEW ENTRY</button></div><h2>Official record</h2><p>FORM ID: AUTH-8821</p><form onSubmit={submit}><label>IDENTIFICATION (EMAIL)<input required type="email" value={email} onChange={event => setEmail(event.target.value)} /></label><label>AUTHORIZATION KEY<input required minLength={8} type="password" value={password} onChange={event => setPassword(event.target.value)} /></label>{mode === 'register' && <label>CONFIRM KEY<input required type="password" value={confirm} onChange={event => setConfirm(event.target.value)} /></label>}{error && <span className={styles.error}>{error}</span>}<button className={styles.primaryButton} disabled={busy}>{busy ? 'VERIFYING...' : mode === 'login' ? 'VERIFY CREDENTIALS' : 'SUBMIT ENTRY'}</button><button type="button" className={styles.textButton} onClick={onClose}>CANCEL</button></form></section></div>;
}

function CreateDialog({ onClose, onCreate }: { onClose: () => void; onCreate: (title: string, content: string) => Promise<void> | void }) {
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  async function submit(event: FormEvent) { event.preventDefault(); setBusy(true); setError(''); try { await onCreate(title.trim(), content.trim()); } catch (caught) { setError(caught instanceof Error ? caught.message : 'Unable to file record.'); } finally { setBusy(false); } }
  return <div className={styles.backdrop} onMouseDown={event => event.target === event.currentTarget && onClose()}><section className={`${styles.dialog} ${styles.createDialog}`} role="dialog" aria-modal="true"><p className={styles.formTab}>FORM-8A: SUBMISSION</p><h2>Official entry</h2><form onSubmit={submit}><label>TITLE OF ENTRY<input required maxLength={200} value={title} onChange={event => setTitle(event.target.value)} /></label><label>DETAILED STATEMENT<textarea required maxLength={20000} rows={10} value={content} onChange={event => setContent(event.target.value)} /></label><small>{content.length} / 20,000</small>{error && <span className={styles.error}>{error}</span>}<div className={styles.formActions}><button type="button" className={styles.textButton} onClick={onClose}>CANCEL</button><button className={styles.primaryButton} disabled={busy || !title.trim() || !content.trim()}>{busy ? 'FILING...' : 'SUBMIT RECORD'}</button></div></form></section></div>;
}

function BallotSkeleton() { return <div className={styles.skeleton} aria-label="Loading ballots"><span /><span /><span /></div>; }

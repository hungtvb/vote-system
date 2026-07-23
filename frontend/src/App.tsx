import { useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError, api } from './api';
import { loadSession, saveSession } from './session';
import type { AuthPayload, Post, Session, UpdatePostPayload, VoteType } from './types';

type AuthMode = 'login' | 'register';
type FeedMode = 'latest' | 'top' | 'mine';
type ToastTone = 'success' | 'error';
type ToastState = { message: string; tone: ToastTone };

const PAGE_SIZE = 10;

function ArrowIcon({ direction }: { direction: 'up' | 'down' }) {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><path d={direction === 'up' ? 'm6 14 6-6 6 6' : 'm6 10 6 6 6-6'} /></svg>;
}

function LogoMark() {
  return <span className="logo-mark" aria-hidden="true"><span /><span /><span /></span>;
}

function SearchIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="m20 20-3.2-3.2" /></svg>;
}

function PlusIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 5v14M5 12h14" /></svg>;
}

function CloseIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 6l12 12M18 6 6 18" /></svg>;
}

function EditIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 20h4l10.5-10.5a2.1 2.1 0 0 0-4-4L4 16v4Z" /><path d="m13.5 6.5 4 4" /></svg>;
}

function TrashIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M9 7V4h6v3m-8 0 1 13h8l1-13M10 11v5M14 11v5" /></svg>;
}

function ShareIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="18" cy="5" r="2.5" /><circle cx="6" cy="12" r="2.5" /><circle cx="18" cy="19" r="2.5" /><path d="m8.2 10.8 7.5-4.5M8.2 13.2l7.5 4.5" /></svg>;
}

function formatRelativeTime(value: string): string {
  const seconds = Math.round((new Date(value).getTime() - Date.now()) / 1000);
  const formatter = new Intl.RelativeTimeFormat('vi', { numeric: 'auto' });
  const ranges: Array<[Intl.RelativeTimeFormatUnit, number]> = [
    ['year', 31_536_000],
    ['month', 2_592_000],
    ['day', 86_400],
    ['hour', 3_600],
    ['minute', 60]
  ];

  for (const [unit, divisor] of ranges) {
    if (Math.abs(seconds) >= divisor) return formatter.format(Math.round(seconds / divisor), unit);
  }

  return formatter.format(seconds, 'second');
}

function wasEdited(post: Post): boolean {
  return new Date(post.updatedAt).getTime() - new Date(post.createdAt).getTime() > 1000;
}

interface AuthModalProps {
  open: boolean;
  mode: AuthMode;
  onModeChange: (mode: AuthMode) => void;
  onClose: () => void;
  onAuthenticated: (session: Session) => void;
}

function AuthModal({ open, mode, onModeChange, onClose, onAuthenticated }: AuthModalProps) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open) return undefined;
    setError('');
    const handleKeyDown = (event: KeyboardEvent) => event.key === 'Escape' && onClose();
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [open, onClose]);

  if (!open) return null;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError('');
    const payload: AuthPayload = { email: email.trim(), password };

    try {
      const nextSession = mode === 'login' ? await api.login(payload) : await api.register(payload);
      onAuthenticated(nextSession);
      setPassword('');
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không thể xác thực tài khoản.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section className="modal-panel" role="dialog" aria-modal="true" aria-labelledby="auth-title">
        <button className="icon-button modal-close" type="button" onClick={onClose} aria-label="Đóng"><CloseIcon /></button>
        <LogoMark />
        <h2 id="auth-title">{mode === 'login' ? 'Đăng nhập PulseVote' : 'Tạo tài khoản'}</h2>
        <p>{mode === 'login' ? 'Tiếp tục tham gia các cuộc bình chọn.' : 'Đăng bài, bình chọn và quản lý nội dung của bạn.'}</p>

        <div className="segmented-control" aria-label="Chọn hình thức xác thực">
          <button type="button" className={mode === 'login' ? 'active' : ''} onClick={() => onModeChange('login')}>Đăng nhập</button>
          <button type="button" className={mode === 'register' ? 'active' : ''} onClick={() => onModeChange('register')}>Đăng ký</button>
        </div>

        <form className="form-stack" onSubmit={submit}>
          <label>Email<input autoFocus required type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="you@example.com" /></label>
          <label>Mật khẩu<input required minLength={mode === 'register' ? 8 : undefined} type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} value={password} onChange={(event) => setPassword(event.target.value)} placeholder={mode === 'register' ? 'Tối thiểu 8 ký tự' : 'Nhập mật khẩu'} /></label>
          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="primary-button full-width" type="submit" disabled={submitting}>{submitting ? 'Đang xử lý…' : mode === 'login' ? 'Đăng nhập' : 'Tạo tài khoản'}</button>
        </form>
      </section>
    </div>
  );
}

interface EditorModalProps {
  post: Post | null;
  busy: boolean;
  onClose: () => void;
  onSave: (post: Post, payload: UpdatePostPayload) => Promise<void>;
}

function PostEditorModal({ post, busy, onClose, onSave }: EditorModalProps) {
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');

  useEffect(() => {
    if (!post) return;
    setTitle(post.title);
    setContent(post.content);
  }, [post]);

  useEffect(() => {
    if (!post) return undefined;
    const handleKeyDown = (event: KeyboardEvent) => event.key === 'Escape' && !busy && onClose();
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [post, busy, onClose]);

  if (!post) return null;
  const activePost = post;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await onSave(activePost, { title: title.trim(), content: content.trim() });
  }

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && !busy && onClose()}>
      <section className="modal-panel editor-modal" role="dialog" aria-modal="true" aria-labelledby="editor-title">
        <button className="icon-button modal-close" type="button" onClick={onClose} disabled={busy} aria-label="Đóng"><CloseIcon /></button>
        <h2 id="editor-title">Chỉnh sửa chủ đề</h2>
        <p>Điểm bình chọn hiện tại sẽ được giữ nguyên.</p>
        <form className="form-stack" onSubmit={submit}>
          <label>Tiêu đề<input autoFocus required maxLength={200} value={title} onChange={(event) => setTitle(event.target.value)} /></label>
          <label>Nội dung<textarea required maxLength={5000} rows={7} value={content} onChange={(event) => setContent(event.target.value)} /></label>
          <div className="form-footer"><span>{content.length}/5000</span><div><button className="secondary-button" type="button" onClick={onClose} disabled={busy}>Hủy</button><button className="primary-button" type="submit" disabled={busy || !title.trim() || !content.trim()}>{busy ? 'Đang lưu…' : 'Lưu thay đổi'}</button></div></div>
        </form>
      </section>
    </div>
  );
}

interface ComposerProps {
  session: Session | null;
  onRequireAuth: () => void;
  onCreated: (post: Post) => void;
  onError: (message: string) => void;
}

function PostComposer({ session, onRequireAuth, onCreated, onError }: ComposerProps) {
  const [expanded, setExpanded] = useState(false);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!session) return onRequireAuth();
    setSubmitting(true);

    try {
      const post = await api.createPost({ title: title.trim(), content: content.trim() }, session.accessToken);
      onCreated(post);
      setTitle('');
      setContent('');
      setExpanded(false);
    } catch (caught) {
      onError(caught instanceof Error ? caught.message : 'Không thể tạo bài viết.');
    } finally {
      setSubmitting(false);
    }
  }

  if (!expanded) {
    return (
      <button className="composer-trigger" type="button" onClick={() => session ? setExpanded(true) : onRequireAuth()}>
        <span className="avatar">{session?.email.slice(0, 1).toUpperCase() ?? '?'}</span>
        <span><strong>{session ? 'Đăng một chủ đề mới' : 'Bạn muốn hỏi cộng đồng điều gì?'}</strong><small>{session ? 'Viết câu hỏi rõ ràng và thêm đủ bối cảnh.' : 'Đăng nhập để đăng bài và bình chọn.'}</small></span>
        <span className="composer-action"><PlusIcon /> <b>Đăng chủ đề</b></span>
      </button>
    );
  }

  return (
    <form className="composer-card" onSubmit={submit}>
      <div className="composer-heading"><div><h2>Đăng chủ đề mới</h2><p>Đặt một câu hỏi ngắn gọn, dễ hiểu.</p></div><button className="icon-button" type="button" onClick={() => setExpanded(false)} aria-label="Thu gọn"><CloseIcon /></button></div>
      <label>Tiêu đề<input required maxLength={200} value={title} onChange={(event) => setTitle(event.target.value)} placeholder="Ví dụ: Điều gì làm một sản phẩm đáng tin?" autoFocus /></label>
      <label>Nội dung<textarea required maxLength={5000} rows={5} value={content} onChange={(event) => setContent(event.target.value)} placeholder="Thêm bối cảnh để mọi người có thể bình chọn có căn cứ…" /></label>
      <div className="form-footer"><span>{content.length}/5000</span><button className="primary-button" type="submit" disabled={submitting || !title.trim() || !content.trim()}>{submitting ? 'Đang đăng…' : 'Đăng chủ đề'}</button></div>
    </form>
  );
}

interface PostCardProps {
  post: Post;
  owned: boolean;
  voteBusy: boolean;
  mutationBusy: boolean;
  onVote: (post: Post, type: VoteType) => void;
  onShare: (post: Post) => void;
  onEdit: (post: Post) => void;
  onDelete: (post: Post) => void;
}

function PostCard({ post, owned, voteBusy, mutationBusy, onVote, onShare, onEdit, onDelete }: PostCardProps) {
  const busy = voteBusy || mutationBusy;

  return (
    <article className={`post-card${busy ? ' busy' : ''}`} id={`post-${post.id}`} aria-busy={busy}>
      <header className="post-header">
        <span className="avatar compact">{post.authorId.slice(0, 1).toUpperCase()}</span>
        <div><strong>{owned ? 'Bạn' : `member-${post.authorId.slice(0, 6)}`}</strong><span>{formatRelativeTime(post.createdAt)}{wasEdited(post) ? ' · đã chỉnh sửa' : ''}</span></div>
        {owned && <span className="owned-label">Bài của bạn</span>}
      </header>

      <div className="post-copy">
        <h2>{post.title}</h2>
        <p>{post.content}</p>
      </div>

      <footer className="post-footer">
        <div className="vote-control" aria-label={`Điểm bình chọn: ${post.voteScore}`}>
          <button type="button" className={post.myVote === 'UP' ? 'vote-button active up' : 'vote-button'} disabled={busy} onClick={() => onVote(post, 'UP')} aria-label="Upvote" aria-pressed={post.myVote === 'UP'}><ArrowIcon direction="up" /></button>
          <span className="vote-score"><strong>{post.voteScore}</strong><small>điểm</small></span>
          <button type="button" className={post.myVote === 'DOWN' ? 'vote-button active down' : 'vote-button'} disabled={busy} onClick={() => onVote(post, 'DOWN')} aria-label="Downvote" aria-pressed={post.myVote === 'DOWN'}><ArrowIcon direction="down" /></button>
        </div>

        <div className="post-actions">
          {voteBusy && <span className="saving-state">Đang lưu…</span>}
          <button type="button" onClick={() => onShare(post)}><ShareIcon /> Chia sẻ</button>
          {owned && <><button type="button" onClick={() => onEdit(post)} disabled={busy}><EditIcon /> Sửa</button><button className="danger-action" type="button" onClick={() => onDelete(post)} disabled={busy}><TrashIcon /> Xóa</button></>}
        </div>
      </footer>
    </article>
  );
}

export default function App() {
  const [session, setSession] = useState<Session | null>(() => loadSession());
  const [posts, setPosts] = useState<Post[]>([]);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [search, setSearch] = useState('');
  const [feedMode, setFeedMode] = useState<FeedMode>('latest');
  const [authOpen, setAuthOpen] = useState(false);
  const [authMode, setAuthMode] = useState<AuthMode>('login');
  const [busyVotes, setBusyVotes] = useState<Set<string>>(() => new Set());
  const [busyPosts, setBusyPosts] = useState<Set<string>>(() => new Set());
  const [editingPost, setEditingPost] = useState<Post | null>(null);
  const [toast, setToast] = useState<ToastState | null>(null);

  function notify(message: string, tone: ToastTone = 'success') {
    setToast({ message, tone });
  }

  function clearSession() {
    saveSession(null);
    setSession(null);
    setEditingPost(null);
    setFeedMode('latest');
  }

  function handleUnauthorized(caught: unknown): boolean {
    if (!(caught instanceof ApiError) || caught.status !== 401) return false;
    clearSession();
    setAuthMode('login');
    setAuthOpen(true);
    notify('Phiên đăng nhập đã hết hạn.', 'error');
    return true;
  }

  useEffect(() => {
    if (!toast) return undefined;
    const timer = window.setTimeout(() => setToast(null), 3200);
    return () => window.clearTimeout(timer);
  }, [toast]);

  useEffect(() => {
    const locked = authOpen || Boolean(editingPost);
    const previous = document.body.style.overflow;
    if (locked) document.body.style.overflow = 'hidden';
    return () => { document.body.style.overflow = previous; };
  }, [authOpen, editingPost]);

  useEffect(() => {
    let active = true;
    setLoading(true);
    api.listPosts(0, PAGE_SIZE, session?.accessToken)
      .then((result) => {
        if (!active) return;
        setPosts(result.content);
        setPage(0);
        setHasMore(!result.last);
        setTotalElements(result.totalElements);
      })
      .catch((caught: unknown) => {
        if (!active) return;
        if (!handleUnauthorized(caught)) notify(caught instanceof Error ? caught.message : 'Không thể tải bảng tin.', 'error');
      })
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [session?.accessToken]);

  const visiblePosts = useMemo(() => {
    const normalized = search.trim().toLocaleLowerCase('vi');
    const filtered = posts.filter((post) => {
      const matchesSearch = !normalized || `${post.title} ${post.content}`.toLocaleLowerCase('vi').includes(normalized);
      const matchesMode = feedMode !== 'mine' || post.authorId === session?.userId;
      return matchesSearch && matchesMode;
    });

    return [...filtered].sort((left, right) => {
      if (feedMode === 'top') return right.voteScore - left.voteScore || new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime();
      return new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime();
    });
  }, [posts, search, feedMode, session?.userId]);

  const totalVotes = useMemo(() => posts.reduce((sum, post) => sum + Math.abs(post.voteScore), 0), [posts]);

  function handleAuthenticated(nextSession: Session) {
    saveSession(nextSession);
    setSession(nextSession);
    setAuthOpen(false);
    notify(`Xin chào ${nextSession.email}`);
  }

  function requireAuth() {
    setAuthMode('login');
    setAuthOpen(true);
  }

  function selectFeedMode(mode: FeedMode) {
    if (mode === 'mine' && !session) return requireAuth();
    setFeedMode(mode);
  }

  async function loadMore() {
    const nextPage = page + 1;
    setLoadingMore(true);
    try {
      const result = await api.listPosts(nextPage, PAGE_SIZE, session?.accessToken);
      setPosts((current) => [...current, ...result.content]);
      setPage(nextPage);
      setHasMore(!result.last);
    } catch (caught) {
      if (!handleUnauthorized(caught)) notify(caught instanceof Error ? caught.message : 'Không thể tải thêm bài viết.', 'error');
    } finally {
      setLoadingMore(false);
    }
  }

  async function handleVote(post: Post, type: VoteType) {
    if (!session) return requireAuth();
    if (busyVotes.has(post.id) || busyPosts.has(post.id)) return;
    const previous = post;
    const removing = previous.myVote === type;
    const nextVote = removing ? undefined : type;
    const delta = removing ? (type === 'UP' ? -1 : 1) : previous.myVote ? (type === 'UP' ? 2 : -2) : (type === 'UP' ? 1 : -1);

    setBusyVotes((current) => new Set(current).add(post.id));
    setPosts((current) => current.map((item) => item.id === post.id ? { ...item, voteScore: item.voteScore + delta, myVote: nextVote } : item));

    try {
      const result = removing ? await api.removeVote(post.id, session.accessToken) : await api.castVote(post.id, type, session.accessToken);
      setPosts((current) => current.map((item) => item.id === post.id ? { ...item, voteScore: result.voteScore, myVote: result.myVote } : item));
    } catch (caught) {
      setPosts((current) => current.map((item) => item.id === post.id ? previous : item));
      if (!handleUnauthorized(caught)) notify(caught instanceof Error ? caught.message : 'Không thể cập nhật bình chọn.', 'error');
    } finally {
      setBusyVotes((current) => { const next = new Set(current); next.delete(post.id); return next; });
    }
  }

  async function updatePost(post: Post, payload: UpdatePostPayload) {
    if (!session || busyPosts.has(post.id)) return;
    setBusyPosts((current) => new Set(current).add(post.id));
    try {
      const updated = await api.updatePost(post.id, payload, session.accessToken);
      setPosts((current) => current.map((item) => item.id === post.id ? updated : item));
      setEditingPost(null);
      notify('Đã lưu thay đổi bài viết.');
    } catch (caught) {
      if (!handleUnauthorized(caught)) notify(caught instanceof Error ? caught.message : 'Không thể cập nhật bài viết.', 'error');
    } finally {
      setBusyPosts((current) => { const next = new Set(current); next.delete(post.id); return next; });
    }
  }

  async function deletePost(post: Post) {
    if (!session || busyPosts.has(post.id)) return;
    if (!window.confirm(`Xóa chủ đề “${post.title}”? Toàn bộ bình chọn của bài viết cũng sẽ bị xóa.`)) return;
    setBusyPosts((current) => new Set(current).add(post.id));
    try {
      await api.deletePost(post.id, session.accessToken);
      setPosts((current) => current.filter((item) => item.id !== post.id));
      setTotalElements((current) => Math.max(0, current - 1));
      if (editingPost?.id === post.id) setEditingPost(null);
      notify('Đã xóa bài viết.');
    } catch (caught) {
      if (!handleUnauthorized(caught)) notify(caught instanceof Error ? caught.message : 'Không thể xóa bài viết.', 'error');
    } finally {
      setBusyPosts((current) => { const next = new Set(current); next.delete(post.id); return next; });
    }
  }

  async function sharePost(post: Post) {
    try {
      await navigator.clipboard.writeText(`${window.location.origin}${window.location.pathname}#post-${post.id}`);
      notify('Đã sao chép liên kết bài viết.');
    } catch {
      notify('Không thể sao chép liên kết.', 'error');
    }
  }

  return (
    <div className="app-shell" id="top">
      <header className="site-header">
        <div className="header-inner">
          <a className="brand" href="#top" aria-label="PulseVote - Trang chủ"><LogoMark /><span>PulseVote</span></a>
          <a className="header-feed-link" href="#feed">Bảng tin</a>
          <div className="header-actions">
            {session ? <>
              <span className="user-chip"><span>{session.email.slice(0, 1).toUpperCase()}</span><b>{session.email}</b></span>
              <button className="secondary-button" type="button" onClick={() => { clearSession(); notify('Bạn đã đăng xuất.'); }}>Đăng xuất</button>
            </> : <>
              <button className="text-button" type="button" onClick={() => { setAuthMode('login'); setAuthOpen(true); }}>Đăng nhập</button>
              <button className="primary-button compact" type="button" onClick={() => { setAuthMode('register'); setAuthOpen(true); }}>Tham gia</button>
            </>}
          </div>
        </div>
      </header>

      <main>
        <section className="intro-section">
          <div className="intro-copy">
            <span className="intro-kicker">Cộng đồng bình chọn</span>
            <h1>Đặt câu hỏi. Nhìn thấy tín hiệu thật.</h1>
            <p>Chia sẻ một góc nhìn, nhận phản hồi rõ ràng và thay đổi lựa chọn bất kỳ lúc nào.</p>
            <div className="intro-actions"><a className="primary-button" href="#feed">Xem bảng tin</a>{!session && <button className="text-button" type="button" onClick={() => { setAuthMode('register'); setAuthOpen(true); }}>Tạo tài khoản</button>}</div>
          </div>
          <dl className="intro-stats"><div><dt>{totalElements}</dt><dd>chủ đề</dd></div><div><dt>{totalVotes}</dt><dd>điểm đã ghi nhận</dd></div><div><dt>1</dt><dd>lựa chọn mỗi người</dd></div></dl>
        </section>

        <section className="product-layout" id="feed">
          <div className="feed-column">
            <PostComposer session={session} onRequireAuth={requireAuth} onCreated={(post) => { setPosts((current) => [post, ...current]); setTotalElements((current) => current + 1); notify('Chủ đề đã được đăng.'); }} onError={(message) => notify(message, 'error')} />

            <div className="feed-toolbar">
              <div><h2>Bảng tin</h2><p>{visiblePosts.length} chủ đề đang hiển thị</p></div>
              <label className="search-box"><SearchIcon /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Tìm chủ đề" aria-label="Tìm bài viết" />{search && <button type="button" onClick={() => setSearch('')} aria-label="Xóa tìm kiếm"><CloseIcon /></button>}</label>
            </div>

            <div className="feed-tabs" role="tablist" aria-label="Chế độ bảng tin">
              <button type="button" role="tab" aria-selected={feedMode === 'latest'} className={feedMode === 'latest' ? 'active' : ''} onClick={() => selectFeedMode('latest')}>Mới nhất</button>
              <button type="button" role="tab" aria-selected={feedMode === 'top'} className={feedMode === 'top' ? 'active' : ''} onClick={() => selectFeedMode('top')}>Nhiều điểm</button>
              <button type="button" role="tab" aria-selected={feedMode === 'mine'} className={feedMode === 'mine' ? 'active' : ''} onClick={() => selectFeedMode('mine')}>Bài của tôi</button>
            </div>

            {loading ? (
              <div className="skeleton-list" aria-label="Đang tải bài viết">{[0, 1, 2].map((item) => <div className="post-skeleton" key={item}><span /><div><i /><i /><i /></div></div>)}</div>
            ) : visiblePosts.length ? (
              <div className="post-list">{visiblePosts.map((post) => <PostCard key={post.id} post={post} owned={session?.userId === post.authorId} voteBusy={busyVotes.has(post.id)} mutationBusy={busyPosts.has(post.id)} onVote={handleVote} onShare={sharePost} onEdit={setEditingPost} onDelete={deletePost} />)}</div>
            ) : (
              <div className="empty-state"><LogoMark /><h3>{search ? 'Không tìm thấy chủ đề' : feedMode === 'mine' ? 'Bạn chưa đăng chủ đề nào' : 'Chưa có chủ đề nào'}</h3><p>{search ? 'Thử một từ khóa khác hoặc xóa tìm kiếm.' : 'Hãy bắt đầu cuộc thảo luận đầu tiên.'}</p>{search && <button className="secondary-button" type="button" onClick={() => setSearch('')}>Xóa tìm kiếm</button>}</div>
            )}

            {!search && feedMode !== 'mine' && hasMore && <button className="load-more" type="button" onClick={loadMore} disabled={loadingMore}>{loadingMore ? 'Đang tải…' : 'Xem thêm chủ đề'}</button>}
          </div>

          <aside className="sidebar" aria-label="Thông tin PulseVote">
            <section className="sidebar-card">
              <h2>Cách hoạt động</h2>
              <ol><li><span>1</span><div><strong>Đăng chủ đề</strong><p>Viết câu hỏi và bối cảnh rõ ràng.</p></div></li><li><span>2</span><div><strong>Bình chọn</strong><p>Upvote, downvote hoặc đổi ý.</p></div></li><li><span>3</span><div><strong>Đọc tín hiệu</strong><p>Điểm số cập nhật ngay trên bảng tin.</p></div></li></ol>
            </section>
            <section className="sidebar-card compact-card"><h2>Nguyên tắc</h2><p>Mỗi tài khoản có một lựa chọn trên mỗi chủ đề. Chỉ tác giả mới có thể sửa hoặc xóa bài của mình.</p></section>
          </aside>
        </section>
      </main>

      <footer><a className="brand" href="#top"><LogoMark /><span>PulseVote</span></a><p>Một nơi đơn giản để cộng đồng thể hiện quan điểm.</p><span>Hưng · 2026</span></footer>
      <AuthModal open={authOpen} mode={authMode} onModeChange={setAuthMode} onClose={() => setAuthOpen(false)} onAuthenticated={handleAuthenticated} />
      <PostEditorModal post={editingPost} busy={editingPost ? busyPosts.has(editingPost.id) : false} onClose={() => setEditingPost(null)} onSave={updatePost} />
      {toast && <div className={`toast ${toast.tone}`} role="status" aria-live="polite">{toast.message}</div>}
    </div>
  );
}

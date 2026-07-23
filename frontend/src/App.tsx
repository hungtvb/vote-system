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

function MenuIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16" /></svg>;
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

function SignalIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 18v-3M9 18v-6M14 18V8M19 18V4" /></svg>;
}

function ShieldIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3 5 6v5c0 4.8 2.8 8.2 7 10 4.2-1.8 7-5.2 7-10V6l-7-3Z" /><path d="m9 12 2 2 4-4" /></svg>;
}

function formatRelativeTime(value: string): string {
  const seconds = Math.round((new Date(value).getTime() - Date.now()) / 1000);
  const formatter = new Intl.RelativeTimeFormat('vi', { numeric: 'auto' });
  const ranges: Array<[Intl.RelativeTimeFormatUnit, number]> = [
    ['year', 31_536_000], ['month', 2_592_000], ['day', 86_400], ['hour', 3_600], ['minute', 60]
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
      <section className="auth-modal" role="dialog" aria-modal="true" aria-labelledby="auth-title">
        <div className="modal-orbit" aria-hidden="true" />
        <button className="icon-button modal-close" type="button" onClick={onClose} aria-label="Đóng"><CloseIcon /></button>
        <div className="modal-brand"><LogoMark /></div>
        <p className="eyebrow">PulseVote community</p>
        <h2 id="auth-title">{mode === 'login' ? 'Chào mừng trở lại' : 'Tạo tài khoản mới'}</h2>
        <p className="modal-copy">{mode === 'login' ? 'Đăng nhập để bình chọn và chia sẻ góc nhìn của bạn.' : 'Tham gia cộng đồng và biến mỗi ý kiến thành tín hiệu có giá trị.'}</p>
        <div className="auth-switch" aria-label="Chọn hình thức xác thực">
          <button type="button" className={mode === 'login' ? 'active' : ''} onClick={() => onModeChange('login')}>Đăng nhập</button>
          <button type="button" className={mode === 'register' ? 'active' : ''} onClick={() => onModeChange('register')}>Đăng ký</button>
        </div>
        <form className="auth-form" onSubmit={submit}>
          <label>Email<input autoFocus required type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder="you@example.com" /></label>
          <label>Mật khẩu<input required minLength={mode === 'register' ? 8 : undefined} type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} value={password} onChange={(event) => setPassword(event.target.value)} placeholder={mode === 'register' ? 'Tối thiểu 8 ký tự' : 'Nhập mật khẩu'} /></label>
          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="primary-button full-width" type="submit" disabled={submitting}>{submitting ? 'Đang xử lý…' : mode === 'login' ? 'Đăng nhập' : 'Tạo tài khoản'}</button>
        </form>
        <p className="modal-trust"><ShieldIcon /> Phiên đăng nhập được bảo vệ bằng JWT.</p>
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
      <section className="auth-modal editor-modal" role="dialog" aria-modal="true" aria-labelledby="editor-title">
        <button className="icon-button modal-close" type="button" onClick={onClose} disabled={busy} aria-label="Đóng"><CloseIcon /></button>
        <p className="eyebrow">Quản lý chủ đề</p>
        <h2 id="editor-title">Chỉnh sửa bài viết</h2>
        <p className="modal-copy">Thay đổi được cập nhật trên bảng tin nhưng vẫn giữ nguyên điểm bình chọn.</p>
        <form className="auth-form editor-form" onSubmit={submit}>
          <label>Tiêu đề<input autoFocus required maxLength={200} value={title} onChange={(event) => setTitle(event.target.value)} /></label>
          <label>Nội dung<textarea required maxLength={5000} rows={7} value={content} onChange={(event) => setContent(event.target.value)} /></label>
          <div className="editor-footer"><span>{content.length}/5000</span><div><button className="secondary-button" type="button" onClick={onClose} disabled={busy}>Hủy</button><button className="primary-button" type="submit" disabled={busy || !title.trim() || !content.trim()}>{busy ? 'Đang lưu…' : 'Lưu thay đổi'}</button></div></div>
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
        <span className="avatar small">{session?.email.slice(0, 1).toUpperCase() ?? '?'}</span>
        <span className="composer-prompt"><strong>{session ? 'Tạo một chủ đề mới' : 'Bạn có góc nhìn muốn chia sẻ?'}</strong><small>{session ? 'Đặt câu hỏi rõ ràng để nhận phản hồi có giá trị.' : 'Đăng nhập để đăng bài và bình chọn.'}</small></span>
        <span className="composer-plus"><PlusIcon /></span>
      </button>
    );
  }

  return (
    <form className="composer-card" onSubmit={submit}>
      <div className="composer-heading"><div><p className="eyebrow">Tạo chủ đề mới</p><h2>Chia sẻ điều đáng để cộng đồng bàn luận</h2></div><button className="icon-button" type="button" onClick={() => setExpanded(false)} aria-label="Thu gọn"><CloseIcon /></button></div>
      <label>Tiêu đề<input required maxLength={200} value={title} onChange={(event) => setTitle(event.target.value)} placeholder="Một câu hỏi hoặc quan điểm rõ ràng" autoFocus /></label>
      <label>Nội dung<textarea required maxLength={5000} rows={5} value={content} onChange={(event) => setContent(event.target.value)} placeholder="Thêm bối cảnh để mọi người có thể bình chọn có căn cứ…" /></label>
      <div className="composer-footer"><span>{content.length}/5000</span><button className="primary-button" type="submit" disabled={submitting || !title.trim() || !content.trim()}>{submitting ? 'Đang đăng…' : 'Đăng chủ đề'}</button></div>
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
  const voteState = mutationBusy
    ? 'Đang cập nhật…'
    : voteBusy
      ? 'Đang gửi bình chọn…'
      : post.myVote
        ? `Bạn đã ${post.myVote === 'UP' ? 'upvote' : 'downvote'}`
        : 'Chưa bình chọn';

  return (
    <article className={`post-card${mutationBusy ? ' mutating' : ''}${voteBusy ? ' voting' : ''}`} id={`post-${post.id}`} aria-busy={busy}>
      <div className="vote-rail" aria-label={`Điểm bình chọn: ${post.voteScore}`}>
        <button type="button" className={post.myVote === 'UP' ? 'vote-button active up' : 'vote-button'} disabled={busy} onClick={() => onVote(post, 'UP')} aria-label="Upvote" aria-pressed={post.myVote === 'UP'}><ArrowIcon direction="up" /></button>
        <strong>{post.voteScore}</strong>
        <span className="vote-caption">điểm</span>
        <button type="button" className={post.myVote === 'DOWN' ? 'vote-button active down' : 'vote-button'} disabled={busy} onClick={() => onVote(post, 'DOWN')} aria-label="Downvote" aria-pressed={post.myVote === 'DOWN'}><ArrowIcon direction="down" /></button>
      </div>
      <div className="post-body">
        <div className="post-meta">
          <span className="avatar">{post.authorId.slice(0, 1).toUpperCase()}</span>
          <div><strong>{owned ? 'Bạn' : `member-${post.authorId.slice(0, 6)}`}</strong><span>{formatRelativeTime(post.createdAt)}{wasEdited(post) ? ' · đã chỉnh sửa' : ''}</span></div>
          <div className="post-meta-actions"><span className="topic-badge">{owned ? 'Của bạn' : post.voteScore > 3 ? 'Đang nổi bật' : 'Thảo luận'}</span>{owned && <div className="owner-actions"><button type="button" onClick={() => onEdit(post)} disabled={busy} aria-label="Chỉnh sửa bài viết" title="Chỉnh sửa"><EditIcon /></button><button className="danger" type="button" onClick={() => onDelete(post)} disabled={busy} aria-label="Xóa bài viết" title="Xóa"><TrashIcon /></button></div>}</div>
        </div>
        <h2>{post.title}</h2>
        <p>{post.content}</p>
        <div className="post-actions">
          <button className="share-action" type="button" onClick={() => onShare(post)}><ShareIcon /> Chia sẻ</button>
          <span className={`vote-state${post.myVote ? ` ${post.myVote.toLowerCase()}` : ''}`}>{voteBusy && <i />} {voteState}</span>
        </div>
      </div>
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
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
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
      if (feedMode === 'top') {
        return right.voteScore - left.voteScore || new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime();
      }
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
    if (mode === 'mine' && !session) {
      requireAuth();
      return;
    }
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
    <div className="app-shell">
      <header className="site-header">
        <div className="header-inner">
          <a className="brand" href="#top" aria-label="PulseVote - Trang chủ" onClick={() => setMobileNavOpen(false)}><LogoMark /><span>PulseVote</span><small>beta</small></a>
          <nav className={mobileNavOpen ? 'main-nav open' : 'main-nav'} aria-label="Điều hướng chính">
            <a className="active" href="#feed" onClick={() => setMobileNavOpen(false)}>Bảng tin</a>
            <a href="#about" onClick={() => setMobileNavOpen(false)}>Cách hoạt động</a>
          </nav>
          <div className="header-actions">
            {session ? <>
              <span className="user-chip"><span>{session.email.slice(0, 1).toUpperCase()}</span><b>{session.email}</b></span>
              <button className="ghost-button logout-button" type="button" onClick={() => { clearSession(); notify('Bạn đã đăng xuất.'); }}>Đăng xuất</button>
            </> : <>
              <button className="ghost-button login-button" type="button" onClick={() => { setAuthMode('login'); setAuthOpen(true); }}>Đăng nhập</button>
              <button className="primary-button compact" type="button" onClick={() => { setAuthMode('register'); setAuthOpen(true); }}>Tham gia</button>
            </>}
            <button className="mobile-menu-button" type="button" onClick={() => setMobileNavOpen((current) => !current)} aria-expanded={mobileNavOpen} aria-label={mobileNavOpen ? 'Đóng menu' : 'Mở menu'}>{mobileNavOpen ? <CloseIcon /> : <MenuIcon />}</button>
          </div>
        </div>
      </header>

      <main id="top">
        <section className="hero">
          <div className="hero-glow one" />
          <div className="hero-glow two" />
          <div className="hero-grid">
            <div className="hero-content">
              <span className="hero-pill"><i /> Nơi mọi ý kiến đều có trọng lượng</span>
              <h1>Biến góc nhìn thành <em>tín hiệu cộng đồng.</em></h1>
              <p>Đặt câu hỏi, chia sẻ quan điểm và xem cộng đồng phản hồi trong một trải nghiệm bình chọn nhanh, rõ ràng và minh bạch.</p>
              <div className="hero-actions">
                <a className="primary-button hero-cta" href="#feed">Khám phá bảng tin</a>
                {!session && <button className="text-button" type="button" onClick={() => { setAuthMode('register'); setAuthOpen(true); }}>Tạo tài khoản miễn phí →</button>}
              </div>
              <div className="hero-stats">
                <div><strong>{totalElements}</strong><span>chủ đề công khai</span></div>
                <div><strong>{totalVotes}</strong><span>tín hiệu đã ghi nhận</span></div>
                <div><strong>1 click</strong><span>để thay đổi lựa chọn</span></div>
              </div>
            </div>

            <div className="hero-preview" aria-label="Minh họa trải nghiệm bình chọn">
              <div className="preview-window">
                <div className="preview-topbar"><span><i /> Live community pulse</span><b>Realtime</b></div>
                <article className="preview-topic featured">
                  <div className="preview-vote"><ArrowIcon direction="up" /><strong>128</strong><small>điểm</small></div>
                  <div><span className="preview-label">Đang nổi bật</span><h2>Điều gì khiến một sản phẩm đáng tin?</h2><p>Minh bạch, phản hồi nhanh và quyền thay đổi quyết định.</p></div>
                </article>
                <article className="preview-topic compact-topic">
                  <div className="preview-avatar">H</div>
                  <div><strong>Một câu hỏi tốt tạo ra thảo luận tốt.</strong><span>24 phản hồi tích cực</span></div>
                  <SignalIcon />
                </article>
                <div className="preview-assurance"><span><ShieldIcon /></span><div><strong>Mỗi người, một lựa chọn</strong><small>Có thể đổi ý bất kỳ lúc nào.</small></div></div>
              </div>
            </div>
          </div>
        </section>

        <section className="content-grid" id="feed">
          <div className="feed-column">
            <PostComposer session={session} onRequireAuth={requireAuth} onCreated={(post) => { setPosts((current) => [post, ...current]); setTotalElements((current) => current + 1); notify('Chủ đề đã được đăng.'); }} onError={(message) => notify(message, 'error')} />

            <div className="feed-toolbar">
              <div className="feed-heading"><p className="eyebrow">Cộng đồng đang nói gì</p><h2>Bảng tin</h2><span>{visiblePosts.length} chủ đề đang hiển thị</span></div>
              <div className="feed-controls">
                <div className="feed-tabs" role="tablist" aria-label="Chế độ bảng tin">
                  <button type="button" role="tab" aria-selected={feedMode === 'latest'} className={feedMode === 'latest' ? 'active' : ''} onClick={() => selectFeedMode('latest')}>Mới nhất</button>
                  <button type="button" role="tab" aria-selected={feedMode === 'top'} className={feedMode === 'top' ? 'active' : ''} onClick={() => selectFeedMode('top')}>Nổi bật</button>
                  <button type="button" role="tab" aria-selected={feedMode === 'mine'} className={feedMode === 'mine' ? 'active' : ''} onClick={() => selectFeedMode('mine')}>Của tôi</button>
                </div>
                <label className="search-box"><SearchIcon /><input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Tìm chủ đề…" aria-label="Tìm bài viết" />{search && <button type="button" onClick={() => setSearch('')} aria-label="Xóa tìm kiếm"><CloseIcon /></button>}</label>
              </div>
            </div>

            {loading ? (
              <div className="skeleton-list" aria-label="Đang tải bài viết">{[0, 1, 2].map((item) => <div className="post-skeleton" key={item}><span /><div><i /><i /><i /></div></div>)}</div>
            ) : visiblePosts.length ? (
              <div className="post-list">{visiblePosts.map((post) => <PostCard key={post.id} post={post} owned={session?.userId === post.authorId} voteBusy={busyVotes.has(post.id)} mutationBusy={busyPosts.has(post.id)} onVote={handleVote} onShare={sharePost} onEdit={setEditingPost} onDelete={deletePost} />)}</div>
            ) : (
              <div className="empty-state"><LogoMark /><h3>{search ? 'Không tìm thấy chủ đề phù hợp' : feedMode === 'mine' ? 'Bạn chưa đăng chủ đề nào' : 'Chưa có chủ đề nào'}</h3><p>{search ? 'Thử một từ khóa ngắn hơn hoặc xóa bộ lọc.' : 'Hãy là người bắt đầu cuộc thảo luận đầu tiên.'}</p>{search && <button className="secondary-button" type="button" onClick={() => setSearch('')}>Xóa tìm kiếm</button>}</div>
            )}
            {!search && feedMode !== 'mine' && hasMore && <button className="load-more" type="button" onClick={loadMore} disabled={loadingMore}>{loadingMore ? 'Đang tải…' : 'Xem thêm chủ đề'}</button>}
          </div>

          <aside className="sidebar" id="about">
            <section className="sidebar-card community-card">
              <span className="status-dot" />
              <p className="eyebrow">Cộng đồng đang hoạt động</p>
              <h2>Một không gian để ý kiến được lắng nghe.</h2>
              <p>Vote không chỉ là con số. Nó giúp góc nhìn hữu ích nổi bật và tạo nên thảo luận có chất lượng.</p>
              <div className="sidebar-metrics"><div><strong>{totalElements}</strong><span>Chủ đề</span></div><div><strong>{totalVotes}</strong><span>Tín hiệu</span></div></div>
              {!session && <button className="primary-button full-width" type="button" onClick={() => { setAuthMode('register'); setAuthOpen(true); }}>Tham gia PulseVote</button>}
            </section>

            <section className="sidebar-card principles-card">
              <p className="eyebrow">Thiết kế cho niềm tin</p>
              <ul>
                <li><span><SignalIcon /></span><div><strong>Phản hồi tức thì</strong><p>Điểm vote cập nhật ngay khi bạn tương tác.</p></div></li>
                <li><span><ShieldIcon /></span><div><strong>Quyền sở hữu rõ ràng</strong><p>Chỉ tác giả mới có thể sửa hoặc xóa nội dung.</p></div></li>
              </ul>
            </section>

            <section className="sidebar-card rules-card">
              <p className="eyebrow">Cách hoạt động</p>
              <ol><li><span>01</span><div><strong>Đăng chủ đề</strong><p>Nêu rõ câu hỏi và bối cảnh.</p></div></li><li><span>02</span><div><strong>Bình chọn</strong><p>Upvote, downvote hoặc đổi ý.</p></div></li><li><span>03</span><div><strong>Quản lý nội dung</strong><p>Sửa hoặc xóa chủ đề của mình.</p></div></li></ol>
            </section>
            <p className="sidebar-note">Spring Boot 3 · PostgreSQL · React</p>
          </aside>
        </section>
      </main>

      <footer><div><a className="brand" href="#top"><LogoMark /><span>PulseVote</span></a><p>Biến mỗi góc nhìn thành một tín hiệu có giá trị.</p></div><span>Production-oriented side project by Hưng · 2026</span></footer>
      <AuthModal open={authOpen} mode={authMode} onModeChange={setAuthMode} onClose={() => setAuthOpen(false)} onAuthenticated={handleAuthenticated} />
      <PostEditorModal post={editingPost} busy={editingPost ? busyPosts.has(editingPost.id) : false} onClose={() => setEditingPost(null)} onSave={updatePost} />
      {toast && <div className={`toast ${toast.tone}`} role="status"><span>{toast.tone === 'success' ? '✓' : '!'}</span>{toast.message}</div>}
    </div>
  );
}

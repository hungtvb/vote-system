import { FormEvent, useEffect, useMemo, useState } from 'react';
import { ApiError, api } from './api';
import { loadSession, saveSession } from './session';
import type { AuthPayload, Post, Session, VoteType } from './types';

type AuthMode = 'login' | 'register';
type Toast = { message: string; tone: 'success' | 'error' } | null;

const PAGE_SIZE = 10;

function ArrowIcon({ direction }: { direction: 'up' | 'down' }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d={direction === 'up' ? 'm6 14 6-6 6 6' : 'm6 10 6 6 6-6'} />
    </svg>
  );
}

function LogoMark() {
  return (
    <span className="logo-mark" aria-hidden="true">
      <span />
      <span />
      <span />
    </span>
  );
}

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-3.2-3.2" />
    </svg>
  );
}

function PlusIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 5v14M5 12h14" />
    </svg>
  );
}

function CloseIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M6 6l12 12M18 6 6 18" />
    </svg>
  );
}

function formatRelativeTime(value: string): string {
  const timestamp = new Date(value).getTime();
  const seconds = Math.round((timestamp - Date.now()) / 1000);
  const formatter = new Intl.RelativeTimeFormat('vi', { numeric: 'auto' });
  const ranges: Array<[Intl.RelativeTimeFormatUnit, number]> = [
    ['year', 60 * 60 * 24 * 365],
    ['month', 60 * 60 * 24 * 30],
    ['day', 60 * 60 * 24],
    ['hour', 60 * 60],
    ['minute', 60]
  ];

  for (const [unit, divisor] of ranges) {
    if (Math.abs(seconds) >= divisor) {
      return formatter.format(Math.round(seconds / divisor), unit);
    }
  }
  return formatter.format(seconds, 'second');
}

function shortAuthor(authorId: string): string {
  return `member-${authorId.slice(0, 6)}`;
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
    if (!open) return;
    setError('');
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [open, onClose]);

  if (!open) return null;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setError('');
    const payload: AuthPayload = { email: email.trim(), password };
    try {
      const session = mode === 'login' ? await api.login(payload) : await api.register(payload);
      onAuthenticated(session);
      setPassword('');
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Không thể xác thực tài khoản.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => {
      if (event.target === event.currentTarget) onClose();
    }}>
      <section className="auth-modal" role="dialog" aria-modal="true" aria-labelledby="auth-title">
        <button className="icon-button modal-close" type="button" onClick={onClose} aria-label="Đóng">
          <CloseIcon />
        </button>
        <div className="modal-brand"><LogoMark /></div>
        <p className="eyebrow">PulseVote community</p>
        <h2 id="auth-title">{mode === 'login' ? 'Chào mừng trở lại' : 'Tạo tài khoản mới'}</h2>
        <p className="modal-copy">
          {mode === 'login'
            ? 'Đăng nhập để bình chọn và chia sẻ góc nhìn của bạn.'
            : 'Tham gia cộng đồng và biến mỗi ý kiến thành tín hiệu có giá trị.'}
        </p>

        <div className="auth-switch" aria-label="Chọn hình thức xác thực">
          <button type="button" className={mode === 'login' ? 'active' : ''} onClick={() => onModeChange('login')}>
            Đăng nhập
          </button>
          <button type="button" className={mode === 'register' ? 'active' : ''} onClick={() => onModeChange('register')}>
            Đăng ký
          </button>
        </div>

        <form className="auth-form" onSubmit={submit}>
          <label>
            Email
            <input
              autoFocus
              required
              type="email"
              autoComplete="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              placeholder="you@example.com"
            />
          </label>
          <label>
            Mật khẩu
            <input
              required
              minLength={mode === 'register' ? 8 : undefined}
              type="password"
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder={mode === 'register' ? 'Tối thiểu 8 ký tự' : 'Nhập mật khẩu'}
            />
          </label>
          {error && <p className="form-error" role="alert">{error}</p>}
          <button className="primary-button full-width" type="submit" disabled={submitting}>
            {submitting ? 'Đang xử lý…' : mode === 'login' ? 'Đăng nhập' : 'Tạo tài khoản'}
          </button>
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

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!session) {
      onRequireAuth();
      return;
    }
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
  };

  if (!expanded) {
    return (
      <button className="composer-trigger" type="button" onClick={() => session ? setExpanded(true) : onRequireAuth()}>
        <span className="avatar small">{session?.email.slice(0, 1).toUpperCase() ?? '?'}</span>
        <span>{session ? 'Bạn đang nghĩ gì?' : 'Đăng nhập để bắt đầu một cuộc bình chọn'}</span>
        <span className="composer-plus"><PlusIcon /></span>
      </button>
    );
  }

  return (
    <form className="composer-card" onSubmit={submit}>
      <div className="composer-heading">
        <div>
          <p className="eyebrow">Tạo chủ đề mới</p>
          <h2>Chia sẻ điều đáng để cộng đồng bàn luận</h2>
        </div>
        <button className="icon-button" type="button" onClick={() => setExpanded(false)} aria-label="Thu gọn">
          <CloseIcon />
        </button>
      </div>
      <label>
        Tiêu đề
        <input
          required
          maxLength={180}
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          placeholder="Một câu hỏi hoặc quan điểm rõ ràng"
          autoFocus
        />
      </label>
      <label>
        Nội dung
        <textarea
          required
          maxLength={5000}
          rows={5}
          value={content}
          onChange={(event) => setContent(event.target.value)}
          placeholder="Thêm bối cảnh để mọi người có thể bình chọn có căn cứ…"
        />
      </label>
      <div className="composer-footer">
        <span>{content.length}/5000</span>
        <button className="primary-button" type="submit" disabled={submitting || !title.trim() || !content.trim()}>
          {submitting ? 'Đang đăng…' : 'Đăng chủ đề'}
        </button>
      </div>
    </form>
  );
}

interface PostCardProps {
  post: Post;
  busy: boolean;
  onVote: (post: Post, type: VoteType) => void;
  onShare: (post: Post) => void;
}

function PostCard({ post, busy, onVote, onShare }: PostCardProps) {
  return (
    <article className="post-card" id={`post-${post.id}`}>
      <div className="vote-rail" aria-label={`Điểm bình chọn: ${post.voteScore}`}>
        <button
          type="button"
          className={post.myVote === 'UP' ? 'vote-button active up' : 'vote-button'}
          disabled={busy}
          onClick={() => onVote(post, 'UP')}
          aria-label="Upvote"
          aria-pressed={post.myVote === 'UP'}
        >
          <ArrowIcon direction="up" />
        </button>
        <strong>{post.voteScore}</strong>
        <button
          type="button"
          className={post.myVote === 'DOWN' ? 'vote-button active down' : 'vote-button'}
          disabled={busy}
          onClick={() => onVote(post, 'DOWN')}
          aria-label="Downvote"
          aria-pressed={post.myVote === 'DOWN'}
        >
          <ArrowIcon direction="down" />
        </button>
      </div>

      <div className="post-body">
        <div className="post-meta">
          <span className="avatar">{post.authorId.slice(0, 1).toUpperCase()}</span>
          <div>
            <strong>{shortAuthor(post.authorId)}</strong>
            <span>{formatRelativeTime(post.createdAt)}</span>
          </div>
          <span className="topic-badge">Thảo luận</span>
        </div>
        <h2>{post.title}</h2>
        <p>{post.content}</p>
        <div className="post-actions">
          <button type="button" onClick={() => onShare(post)}>Chia sẻ</button>
          <span>{post.myVote ? `Bạn đã ${post.myVote === 'UP' ? 'upvote' : 'downvote'}` : 'Chưa bình chọn'}</span>
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
  const [authOpen, setAuthOpen] = useState(false);
  const [authMode, setAuthMode] = useState<AuthMode>('login');
  const [busyVotes, setBusyVotes] = useState<Set<string>>(() => new Set());
  const [toast, setToast] = useState<Toast>(null);

  const notify = (message: string, tone: Toast['tone'] = 'success') => setToast({ message, tone });

  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 3200);
    return () => window.clearTimeout(timer);
  }, [toast]);

  const refreshPosts = async () => {
    setLoading(true);
    try {
      const result = await api.listPosts(0, PAGE_SIZE, session?.accessToken);
      setPosts(result.content);
      setPage(0);
      setHasMore(!result.last);
      setTotalElements(result.totalElements);
    } catch (caught) {
      notify(caught instanceof Error ? caught.message : 'Không thể tải bảng tin.', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void refreshPosts();
    // Refresh when authentication changes so the API can return myVote.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session?.accessToken]);

  const visiblePosts = useMemo(() => {
    const normalized = search.trim().toLocaleLowerCase('vi');
    if (!normalized) return posts;
    return posts.filter((post) => `${post.title} ${post.content}`.toLocaleLowerCase('vi').includes(normalized));
  }, [posts, search]);

  const handleAuthenticated = (nextSession: Session) => {
    saveSession(nextSession);
    setSession(nextSession);
    setAuthOpen(false);
    notify(`Xin chào ${nextSession.email}`);
  };

  const logout = () => {
    saveSession(null);
    setSession(null);
    notify('Bạn đã đăng xuất.');
  };

  const requireAuth = () => {
    setAuthMode('login');
    setAuthOpen(true);
  };

  const loadMore = async () => {
    const nextPage = page + 1;
    setLoadingMore(true);
    try {
      const result = await api.listPosts(nextPage, PAGE_SIZE, session?.accessToken);
      setPosts((current) => [...current, ...result.content]);
      setPage(nextPage);
      setHasMore(!result.last);
    } catch (caught) {
      notify(caught instanceof Error ? caught.message : 'Không thể tải thêm bài viết.', 'error');
    } finally {
      setLoadingMore(false);
    }
  };

  const handleCreated = (post: Post) => {
    setPosts((current) => [post, ...current]);
    setTotalElements((current) => current + 1);
    notify('Chủ đề đã được đăng.');
  };

  const handleVote = async (post: Post, type: VoteType) => {
    if (!session) {
      requireAuth();
      return;
    }
    if (busyVotes.has(post.id)) return;

    const previous = post;
    const removing = previous.myVote === type;
    const nextVote = removing ? undefined : type;
    const delta = removing
      ? type === 'UP' ? -1 : 1
      : previous.myVote
        ? type === 'UP' ? 2 : -2
        : type === 'UP' ? 1 : -1;

    setBusyVotes((current) => new Set(current).add(post.id));
    setPosts((current) => current.map((item) => item.id === post.id
      ? { ...item, voteScore: item.voteScore + delta, myVote: nextVote }
      : item));

    try {
      const result = removing
        ? await api.removeVote(post.id, session.accessToken)
        : await api.castVote(post.id, type, session.accessToken);
      setPosts((current) => current.map((item) => item.id === post.id
        ? { ...item, voteScore: result.voteScore, myVote: result.myVote }
        : item));
    } catch (caught) {
      setPosts((current) => current.map((item) => item.id === post.id ? previous : item));
      if (caught instanceof ApiError && caught.status === 401) {
        saveSession(null);
        setSession(null);
        requireAuth();
        notify('Phiên đăng nhập đã hết hạn.', 'error');
      } else {
        notify(caught instanceof Error ? caught.message : 'Không thể cập nhật bình chọn.', 'error');
      }
    } finally {
      setBusyVotes((current) => {
        const next = new Set(current);
        next.delete(post.id);
        return next;
      });
    }
  };

  const sharePost = async (post: Post) => {
    const url = `${window.location.origin}${window.location.pathname}#post-${post.id}`;
    try {
      await navigator.clipboard.writeText(url);
      notify('Đã sao chép liên kết bài viết.');
    } catch {
      notify('Không thể sao chép liên kết.', 'error');
    }
  };

  return (
    <div className="app-shell">
      <header className="site-header">
        <div className="header-inner">
          <a className="brand" href="#top" aria-label="PulseVote - Trang chủ">
            <LogoMark />
            <span>PulseVote</span>
          </a>
          <nav aria-label="Điều hướng chính">
            <a className="active" href="#feed">Bảng tin</a>
            <a href="#about">Cách hoạt động</a>
          </nav>
          <div className="header-actions">
            {session ? (
              <>
                <span className="user-chip"><span>{session.email.slice(0, 1).toUpperCase()}</span>{session.email}</span>
                <button className="ghost-button" type="button" onClick={logout}>Đăng xuất</button>
              </>
            ) : (
              <>
                <button className="ghost-button" type="button" onClick={() => { setAuthMode('login'); setAuthOpen(true); }}>Đăng nhập</button>
                <button className="primary-button compact" type="button" onClick={() => { setAuthMode('register'); setAuthOpen(true); }}>Tham gia</button>
              </>
            )}
          </div>
        </div>
      </header>

      <main id="top">
        <section className="hero">
          <div className="hero-glow one" />
          <div className="hero-glow two" />
          <div className="hero-content">
            <span className="hero-pill"><i /> Nơi mọi ý kiến đều có trọng lượng</span>
            <h1>Biến góc nhìn thành<br /><em>tín hiệu cộng đồng.</em></h1>
            <p>Đặt câu hỏi, chia sẻ quan điểm và xem cộng đồng phản hồi trong một trải nghiệm bình chọn nhanh, rõ ràng và minh bạch.</p>
            <div className="hero-actions">
              <a className="primary-button hero-cta" href="#feed">Khám phá bảng tin</a>
              {!session && <button className="text-button" type="button" onClick={() => { setAuthMode('register'); setAuthOpen(true); }}>Tạo tài khoản miễn phí →</button>}
            </div>
            <div className="hero-stats" aria-label="Thống kê cộng đồng">
              <div><strong>{totalElements}</strong><span>chủ đề công khai</span></div>
              <div><strong>1 click</strong><span>để thay đổi bình chọn</span></div>
              <div><strong>Realtime</strong><span>cập nhật ngay trên giao diện</span></div>
            </div>
          </div>
        </section>

        <section className="content-grid" id="feed">
          <div className="feed-column">
            <PostComposer
              session={session}
              onRequireAuth={requireAuth}
              onCreated={handleCreated}
              onError={(message) => notify(message, 'error')}
            />

            <div className="feed-toolbar">
              <div>
                <p className="eyebrow">Cộng đồng đang nói gì</p>
                <h2>Bảng tin mới nhất</h2>
              </div>
              <label className="search-box">
                <SearchIcon />
                <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Tìm trong bài đã tải" aria-label="Tìm bài viết" />
              </label>
            </div>

            {loading ? (
              <div className="skeleton-list" aria-label="Đang tải bài viết">
                {[0, 1, 2].map((item) => <div className="post-skeleton" key={item}><span /><div><i /><i /><i /></div></div>)}
              </div>
            ) : visiblePosts.length ? (
              <div className="post-list">
                {visiblePosts.map((post) => (
                  <PostCard key={post.id} post={post} busy={busyVotes.has(post.id)} onVote={handleVote} onShare={sharePost} />
                ))}
              </div>
            ) : (
              <div className="empty-state">
                <LogoMark />
                <h3>{search ? 'Không tìm thấy chủ đề phù hợp' : 'Chưa có chủ đề nào'}</h3>
                <p>{search ? 'Thử một từ khóa khác.' : 'Hãy là người bắt đầu cuộc thảo luận đầu tiên.'}</p>
              </div>
            )}

            {!search && hasMore && (
              <button className="load-more" type="button" onClick={loadMore} disabled={loadingMore}>
                {loadingMore ? 'Đang tải…' : 'Xem thêm chủ đề'}
              </button>
            )}
          </div>

          <aside className="sidebar" id="about">
            <section className="sidebar-card community-card">
              <span className="status-dot" />
              <p className="eyebrow">Cộng đồng đang hoạt động</p>
              <h2>Một không gian để ý kiến được lắng nghe.</h2>
              <p>Vote không chỉ là con số. Nó giúp những góc nhìn hữu ích nổi bật và tạo nên các cuộc thảo luận có chất lượng.</p>
              {!session && <button className="primary-button full-width" type="button" onClick={() => { setAuthMode('register'); setAuthOpen(true); }}>Tham gia PulseVote</button>}
            </section>

            <section className="sidebar-card rules-card">
              <p className="eyebrow">Cách hoạt động</p>
              <ol>
                <li><span>01</span><div><strong>Đăng chủ đề</strong><p>Nêu rõ câu hỏi và bối cảnh.</p></div></li>
                <li><span>02</span><div><strong>Bình chọn</strong><p>Upvote, downvote hoặc đổi ý bất kỳ lúc nào.</p></div></li>
                <li><span>03</span><div><strong>Đọc tín hiệu</strong><p>Điểm số được cập nhật ngay và lưu an toàn.</p></div></li>
              </ol>
            </section>

            <p className="sidebar-note">Built with Spring Boot 3 · PostgreSQL · React</p>
          </aside>
        </section>
      </main>

      <footer>
        <a className="brand" href="#top"><LogoMark /><span>PulseVote</span></a>
        <p>Side project production-oriented by Hưng.</p>
      </footer>

      <AuthModal
        open={authOpen}
        mode={authMode}
        onModeChange={setAuthMode}
        onClose={() => setAuthOpen(false)}
        onAuthenticated={handleAuthenticated}
      />

      {toast && <div className={`toast ${toast.tone}`} role="status">{toast.message}</div>}
    </div>
  );
}

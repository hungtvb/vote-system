import type { Ballot, FeedType, PageResponse, Session, VoteResponse, VoteType } from './types';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? '';

export class ApiError extends Error {
  constructor(message: string, public readonly status: number, public readonly retryAfter?: number) {
    super(message);
    this.name = 'ApiError';
  }
}

async function request<T>(path: string, options: RequestInit = {}, token?: string): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');
  if (options.body) headers.set('Content-Type', 'application/json');
  if (token) headers.set('Authorization', `Bearer ${token}`);

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
    credentials: 'include'
  });

  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const problem = (await response.json()) as { detail?: string; message?: string; title?: string };
      message = problem.detail ?? problem.message ?? problem.title ?? message;
    } catch {
      // Keep fallback message.
    }
    const retryAfter = Number(response.headers.get('Retry-After')) || undefined;
    throw new ApiError(message, response.status, retryAfter);
  }

  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export const api = {
  register(email: string, password: string) {
    return request<Session>('/api/v1/auth/register', { method: 'POST', body: JSON.stringify({ email, password }) });
  },
  login(email: string, password: string) {
    return request<Session>('/api/v1/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) });
  },
  refresh() {
    return request<Session>('/api/v1/auth/refresh', { method: 'POST' });
  },
  logout(token: string) {
    return request<void>('/api/v1/auth/logout', { method: 'POST' }, token);
  },
  logoutAll(token: string) {
    return request<void>('/api/v1/auth/logout-all', { method: 'POST' }, token);
  },
  listBallots(feed: FeedType, page: number, size: number, token?: string) {
    return request<PageResponse<Ballot>>(`/api/v1/posts?feed=${feed}&page=${page}&size=${size}`, {}, token);
  },
  getBallot(postId: string, token?: string) {
    return request<Ballot>(`/api/v1/posts/${postId}`, {}, token);
  },
  createBallot(payload: { title: string; content: string }, token: string) {
    return request<Ballot>('/api/v1/posts', { method: 'POST', body: JSON.stringify(payload) }, token);
  },
  updateBallot(postId: string, payload: { title: string; content: string }, token: string) {
    return request<Ballot>(`/api/v1/posts/${postId}`, { method: 'PUT', body: JSON.stringify(payload) }, token);
  },
  deleteBallot(postId: string, token: string) {
    return request<void>(`/api/v1/posts/${postId}`, { method: 'DELETE' }, token);
  },
  castVote(postId: string, type: VoteType, token: string) {
    return request<VoteResponse>(`/api/v1/posts/${postId}/vote`, { method: 'PUT', body: JSON.stringify({ type }) }, token);
  },
  removeVote(postId: string, token: string) {
    return request<VoteResponse>(`/api/v1/posts/${postId}/vote`, { method: 'DELETE' }, token);
  }
};

import type {
  AuthPayload,
  CreatePostPayload,
  PageResponse,
  Post,
  Session,
  UpdatePostPayload,
  VoteResponse,
  VoteType
} from './types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

export class ApiError extends Error {
  constructor(message: string, public readonly status: number) {
    super(message);
    this.name = 'ApiError';
  }
}

async function request<T>(path: string, options: RequestInit = {}, token?: string): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');
  if (options.body) {
    headers.set('Content-Type', 'application/json');
  }
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers });
  if (!response.ok) {
    let message = `Yêu cầu thất bại (${response.status})`;
    try {
      const problem = (await response.json()) as { detail?: string; title?: string; message?: string };
      message = problem.detail ?? problem.message ?? problem.title ?? message;
    } catch {
      // Keep the fallback message when the response is not JSON.
    }
    throw new ApiError(message, response.status);
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export const api = {
  register(payload: AuthPayload) {
    return request<Session>('/api/v1/auth/register', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  },

  login(payload: AuthPayload) {
    return request<Session>('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify(payload)
    });
  },

  listPosts(page: number, size: number, token?: string) {
    return request<PageResponse<Post>>(`/api/v1/posts?page=${page}&size=${size}`, {}, token);
  },

  createPost(payload: CreatePostPayload, token: string) {
    return request<Post>('/api/v1/posts', {
      method: 'POST',
      body: JSON.stringify(payload)
    }, token);
  },

  updatePost(postId: string, payload: UpdatePostPayload, token: string) {
    return request<Post>(`/api/v1/posts/${postId}`, {
      method: 'PUT',
      body: JSON.stringify(payload)
    }, token);
  },

  deletePost(postId: string, token: string) {
    return request<void>(`/api/v1/posts/${postId}`, {
      method: 'DELETE'
    }, token);
  },

  castVote(postId: string, type: VoteType, token: string) {
    return request<VoteResponse>(`/api/v1/posts/${postId}/vote`, {
      method: 'PUT',
      body: JSON.stringify({ type })
    }, token);
  },

  removeVote(postId: string, token: string) {
    return request<VoteResponse>(`/api/v1/posts/${postId}/vote`, {
      method: 'DELETE'
    }, token);
  }
};
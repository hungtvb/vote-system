import type { Ballot, BallotStatus, FeedType, PageResponse, VoteResponse, VoteType } from './types';
import { http, type ApiRequester } from './transport';

export interface BallotListParams {
  feed: FeedType;
  page: number;
  size: number;
  query?: string;
  category?: string;
  status?: BallotStatus;
}

export function buildBallotListPath(params: BallotListParams): string {
  const search = new URLSearchParams({
    feed: params.feed,
    page: String(params.page),
    size: String(params.size)
  });
  const query = params.query?.trim();
  const category = params.category?.trim();
  if (query) search.set('query', query);
  if (category) search.set('category', category);
  if (params.status) search.set('status', params.status);
  return `/api/v1/posts?${search.toString()}`;
}

export function createBallotApi(request: ApiRequester = http.request) {
  return {
    list(params: BallotListParams, token?: string) {
      return request<PageResponse<Ballot>>(buildBallotListPath(params), {}, token);
    },

    get(postId: string, token?: string) {
      return request<Ballot>(`/api/v1/posts/${postId}`, {}, token);
    },

    create(payload: { title: string; content: string }, token: string) {
      return request<Ballot>('/api/v1/posts', {
        method: 'POST',
        body: JSON.stringify(payload)
      }, token);
    },

    update(postId: string, payload: { title: string; content: string }, token: string) {
      return request<Ballot>(`/api/v1/posts/${postId}`, {
        method: 'PUT',
        body: JSON.stringify(payload)
      }, token);
    },

    delete(postId: string, token: string) {
      return request<void>(`/api/v1/posts/${postId}`, { method: 'DELETE' }, token);
    },

    close(postId: string, token: string) {
      return request<Ballot>(`/api/v1/posts/${postId}/close`, { method: 'POST' }, token);
    },

    castVote(postId: string, type: VoteType, token: string) {
      return request<VoteResponse>(`/api/v1/posts/${postId}/vote`, {
        method: 'PUT',
        body: JSON.stringify({ type })
      }, token);
    },

    removeVote(postId: string, token: string) {
      return request<VoteResponse>(`/api/v1/posts/${postId}/vote`, { method: 'DELETE' }, token);
    }
  };
}

export const ballotApi = createBallotApi();

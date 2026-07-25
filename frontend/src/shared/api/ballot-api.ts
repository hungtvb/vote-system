import type { Ballot, FeedType, PageResponse, VoteResponse, VoteType } from './types';
import { http, type ApiRequester } from './transport';

export function createBallotApi(request: ApiRequester = http.request) {
  return {
    list(feed: FeedType, page: number, size: number, token?: string) {
      return request<PageResponse<Ballot>>(`/api/v1/posts?feed=${feed}&page=${page}&size=${size}`, {}, token);
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

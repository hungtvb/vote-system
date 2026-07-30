import type { AuthorSummary, BallotStatus, SocialProvider, VoteVerdict } from './types';
import { http, type ApiRequester } from './transport';

export type AccountStatus = 'ACTIVE' | 'SUSPENDED' | 'BANNED';
export type ModerationStatus = 'VISIBLE' | 'HIDDEN' | 'DELETED';
export type RankingAvailability = 'HEALTHY' | 'STALE' | 'REBUILDING' | 'UNAVAILABLE';
export type AdminSection = 'overview' | 'ballots' | 'users' | 'audit' | 'ranking';

export interface AdminPage<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface AdminUser {
  id: string;
  email: string;
  displayName: string;
  initials: string;
  role: 'USER' | 'ADMIN';
  accountStatus: AccountStatus;
  statusUntil?: string;
  statusUpdatedAt?: string;
  linkedProviders: SocialProvider[];
  createdAt: string;
  updatedAt: string;
}

export interface AdminPost {
  id: string;
  authorId: string;
  author: AuthorSummary;
  ballotNumber: string;
  title: string;
  content: string;
  category: string;
  status: BallotStatus;
  moderationStatus: ModerationStatus;
  moderationUpdatedAt?: string;
  closesAt?: string;
  closedAt?: string;
  voteScore: number;
  upVotes: number;
  downVotes: number;
  totalVotes: number;
  verdictThreshold: number;
  verdict: VoteVerdict;
  finalVerdict: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AdminAuditLog {
  id: string;
  actorId: string;
  action: string;
  targetType: 'POST' | 'USER' | 'RANKING';
  targetId: string;
  reason: string;
  metadata: Record<string, string>;
  createdAt: string;
}

export interface AdminRankingStatus {
  availability: RankingAvailability;
  visibleBallots: number;
  eligibleDayBallots: number;
  eligibleWeekBallots: number;
  hotMembers: number | null;
  topDayMembers: number | null;
  topWeekMembers: number | null;
  generation?: string;
  lastSuccessfulRebuildAt?: string;
  rebuildInProgress: boolean;
}

export interface AdminUserModerationResponse {
  id: string;
  accountStatus: AccountStatus;
  statusUntil?: string;
  statusUpdatedAt: string;
  revokedSessions: number;
}

export interface AdminPostModerationResponse {
  id: string;
  moderationStatus: ModerationStatus;
  moderationUpdatedAt: string;
}

export type AdminModerationResponse = AdminUserModerationResponse | AdminPostModerationResponse;

export interface AdminUserListParams {
  query?: string;
  role?: 'USER' | 'ADMIN';
  accountStatus?: AccountStatus;
  page?: number;
  size?: number;
}

export interface AdminPostListParams {
  query?: string;
  category?: string;
  status?: BallotStatus;
  moderationStatus?: ModerationStatus;
  page?: number;
  size?: number;
}

export interface AdminAuditListParams {
  action?: string;
  actorId?: string;
  targetType?: 'POST' | 'USER' | 'RANKING';
  targetId?: string;
  page?: number;
  size?: number;
}

export function createAdminApi(request: ApiRequester = http.request) {
  return {
    users: (params: AdminUserListParams, token: string, signal?: AbortSignal) =>
      request<AdminPage<AdminUser>>(`/api/v1/admin/users${queryString(params)}`, { signal }, token),
    user: (userId: string, token: string, signal?: AbortSignal) =>
      request<AdminUser>(`/api/v1/admin/users/${encodeURIComponent(userId)}`, { signal }, token),
    posts: (params: AdminPostListParams, token: string, signal?: AbortSignal) =>
      request<AdminPage<AdminPost>>(`/api/v1/admin/posts${queryString(params)}`, { signal }, token),
    post: (postId: string, token: string, signal?: AbortSignal) =>
      request<AdminPost>(`/api/v1/admin/posts/${encodeURIComponent(postId)}`, { signal }, token),
    auditLogs: (params: AdminAuditListParams, token: string, signal?: AbortSignal) =>
      request<AdminPage<AdminAuditLog>>(`/api/v1/admin/audit-logs${queryString(params)}`, { signal }, token),
    rankingStatus: (token: string, signal?: AbortSignal) =>
      request<AdminRankingStatus>('/api/v1/admin/rankings/status', { signal }, token),
    rebuildRanking: (reason: string, token: string) =>
      mutate<AdminRankingStatus>(request, '/api/v1/admin/rankings/rebuild', { reason }, token),
    suspendUser: (userId: string, reason: string, until: string | null, token: string) =>
      mutate<AdminModerationResponse>(request, `/api/v1/admin/users/${encodeURIComponent(userId)}/suspend`, { reason, until }, token),
    banUser: (userId: string, reason: string, until: string | null, token: string) =>
      mutate<AdminModerationResponse>(request, `/api/v1/admin/users/${encodeURIComponent(userId)}/ban`, { reason, until }, token),
    restoreUser: (userId: string, reason: string, token: string) =>
      mutate<AdminModerationResponse>(request, `/api/v1/admin/users/${encodeURIComponent(userId)}/restore`, { reason }, token),
    revokeUserSessions: (userId: string, reason: string, token: string) =>
      mutate<AdminModerationResponse>(request, `/api/v1/admin/users/${encodeURIComponent(userId)}/revoke-sessions`, { reason }, token),
    hidePost: (postId: string, reason: string, token: string) =>
      mutate<AdminModerationResponse>(request, `/api/v1/admin/posts/${encodeURIComponent(postId)}/hide`, { reason }, token),
    restorePost: (postId: string, reason: string, token: string) =>
      mutate<AdminModerationResponse>(request, `/api/v1/admin/posts/${encodeURIComponent(postId)}/restore`, { reason }, token),
    deletePost: (postId: string, reason: string, token: string) =>
      mutate<AdminModerationResponse>(request, `/api/v1/admin/posts/${encodeURIComponent(postId)}/delete`, { reason }, token)
  };
}

async function mutate<T>(request: ApiRequester, path: string, body: object, token: string): Promise<T> {
  return request<T>(path, { method: 'POST', body: JSON.stringify(body) }, token);
}

function queryString<T extends object>(params: T): string {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue;
    query.set(key, String(value));
  }
  const value = query.toString();
  return value ? `?${value}` : '';
}

export const adminApi = createAdminApi();

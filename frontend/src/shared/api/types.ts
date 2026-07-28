export type VoteType = 'UP' | 'DOWN';
export type VoteVerdict = 'UP' | 'DOWN' | 'UNDECIDED';
export type BallotStatus = 'OPEN' | 'CLOSED';
export type FeedType = 'LATEST' | 'HOT' | 'TOP_DAY' | 'TOP_WEEK' | 'MINE';
export type SocialProvider = 'GOOGLE' | 'GITHUB';
export type PreferredLocale = 'vi' | 'en';
export type AvatarIcon =
  | 'CITIZEN'
  | 'ADVOCATE'
  | 'THINKER'
  | 'ORGANIZER'
  | 'VOLUNTEER'
  | 'CREATOR'
  | 'LEADER'
  | 'ANALYST'
  | 'VISIONARY'
  | 'BUILDER';
export type AvatarColor = 'NAVY' | 'SEAL' | 'KRAFT' | 'GRAPHITE' | 'MOSS' | 'INK_BLUE';

export interface Session {
  tokenType: string;
  accessToken: string;
  expiresInSeconds: number;
  userId: string;
  email: string | null;
  role: string;
}

export interface UserProfile {
  id: string;
  email: string | null;
  displayName: string;
  initials: string;
  bio: string | null;
  avatarIcon: AvatarIcon;
  avatarColor: AvatarColor;
  preferredLocale: PreferredLocale;
  role: string;
  linkedProviders: SocialProvider[];
  createdAt: string;
  updatedAt: string;
}

export interface UpdateUserProfileRequest {
  displayName: string;
  bio: string | null;
  avatarIcon: AvatarIcon;
  avatarColor: AvatarColor;
  preferredLocale: PreferredLocale;
}

export interface PublicUserProfile {
  id: string;
  displayName: string;
  initials: string;
  bio: string | null;
  avatarIcon: AvatarIcon;
  avatarColor: AvatarColor;
  createdAt: string;
}

export interface AuthorSummary {
  id: string;
  displayName: string;
  initials: string;
  avatarIcon?: AvatarIcon;
  avatarColor?: AvatarColor;
}

export interface Ballot {
  id: string;
  authorId: string;
  author: AuthorSummary;
  ballotNumber: string;
  title: string;
  content: string;
  category: string;
  status: BallotStatus;
  closesAt?: string;
  closedAt?: string;
  voteScore: number;
  upVotes: number;
  downVotes: number;
  totalVotes: number;
  myVote?: VoteType;
  verdictThreshold: number;
  verdict: VoteVerdict;
  finalVerdict: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface VoteResponse {
  postId: string;
  voteScore: number;
  upVotes: number;
  downVotes: number;
  totalVotes: number;
  myVote?: VoteType;
  verdictThreshold: number;
  verdict: VoteVerdict;
}

export interface BallotVoteUpdate {
  postId: string;
  voteScore: number;
  upVotes: number;
  downVotes: number;
  totalVotes: number;
  verdictThreshold: number;
  verdict: VoteVerdict;
  updatedAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

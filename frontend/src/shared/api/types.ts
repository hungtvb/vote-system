export type VoteType = 'UP' | 'DOWN';
export type VoteVerdict = 'UP' | 'DOWN' | 'UNDECIDED';
export type BallotStatus = 'OPEN' | 'CLOSED';
export type FeedType = 'LATEST' | 'HOT' | 'TOP_DAY' | 'TOP_WEEK' | 'MINE';

export interface Session {
  tokenType: string;
  accessToken: string;
  expiresInSeconds: number;
  userId: string;
  email: string;
  role: string;
}

export interface UserProfile {
  id: string;
  email: string;
  displayName: string;
  initials: string;
  role: string;
  createdAt: string;
  updatedAt: string;
}

export interface AuthorSummary {
  id: string;
  displayName: string;
  initials: string;
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

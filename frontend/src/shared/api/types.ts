export type VoteType = 'UP' | 'DOWN';
export type VoteVerdict = 'UP' | 'DOWN' | 'UNDECIDED';
export type BallotStatus = 'OPEN' | 'CLOSED';
export type FeedType = 'LATEST' | 'HOT' | 'TOP_DAY' | 'TOP_WEEK';

export interface Session {
  tokenType: string;
  accessToken: string;
  expiresInSeconds: number;
  userId: string;
  email: string;
  role: string;
}

export interface Ballot {
  id: string;
  authorId: string;
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

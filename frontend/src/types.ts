export type VoteType = 'UP' | 'DOWN';

export interface Session {
  tokenType: string;
  accessToken: string;
  expiresInSeconds: number;
  userId: string;
  email: string;
  role: string;
}

export interface Post {
  id: string;
  authorId: string;
  title: string;
  content: string;
  voteScore: number;
  myVote?: VoteType;
  createdAt: string;
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

export interface VoteResponse {
  postId: string;
  voteScore: number;
  myVote?: VoteType;
}

export interface AuthPayload {
  email: string;
  password: string;
}

export interface PostPayload {
  title: string;
  content: string;
}

export type CreatePostPayload = PostPayload;
export type UpdatePostPayload = PostPayload;
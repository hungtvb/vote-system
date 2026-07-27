import type { FeedType } from '../api/types';

export type FeedRequestMode = 'auto' | 'public' | 'authenticated';
export type FeedRequestAccess = 'skip' | 'public' | 'authenticated';

interface FeedRequestContext {
  feed: FeedType;
  restoring: boolean;
  authenticated: boolean;
  mode?: FeedRequestMode;
}

export function resolveFeedRequestAccess({
  feed,
  restoring,
  authenticated,
  mode = 'auto'
}: FeedRequestContext): FeedRequestAccess {
  if (feed === 'MINE') {
    return !restoring && authenticated ? 'authenticated' : 'skip';
  }
  if (mode === 'public') return 'public';
  if (mode === 'authenticated') return authenticated ? 'authenticated' : 'skip';
  return !restoring && authenticated ? 'authenticated' : 'public';
}

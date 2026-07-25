'use client';

import { useEffect, useRef } from 'react';
import type { BallotStatus, FeedType } from '@/shared/api/types';
import styles from './FeedControls.module.scss';

const PUBLIC_FEEDS: FeedType[] = ['LATEST', 'HOT', 'TOP_DAY', 'TOP_WEEK'];

interface FeedControlsProps {
  feed: FeedType;
  category: string;
  status?: BallotStatus;
  authenticated: boolean;
  loading: boolean;
  totalElements: number;
  page: number;
  totalPages: number;
  query: string;
  onFeedChange: (feed: FeedType) => void;
  onCategoryChange: (category: string) => void;
  onStatusChange: (status?: BallotStatus) => void;
  onReset: () => void;
}

export function FeedControls({
  feed,
  category,
  status,
  authenticated,
  loading,
  totalElements,
  page,
  totalPages,
  query,
  onFeedChange,
  onCategoryChange,
  onStatusChange,
  onReset
}: FeedControlsProps) {
  const feeds = authenticated ? [...PUBLIC_FEEDS, 'MINE' as const] : PUBLIC_FEEDS;
  const hasFilters = Boolean(query.trim() || category.trim() || status);
  const tabsRef = useRef<HTMLElement | null>(null);
  const activeTabRef = useRef<HTMLButtonElement | null>(null);

  useEffect(() => {
    const tabs = tabsRef.current;
    const active = activeTabRef.current;
    if (!tabs || !active) return;

    const targetLeft = active.offsetLeft - ((tabs.clientWidth - active.offsetWidth) / 2);
    tabs.scrollTo({ left: Math.max(0, targetLeft), behavior: 'auto' });
  }, [feed]);

  return (
    <section className={styles.controls} aria-label="Ballot feed controls">
      <nav ref={tabsRef} className={styles.tabs} aria-label="Feed mode" data-qa-feed-tabs>
        {feeds.map(item => (
          <button
            key={item}
            ref={feed === item ? activeTabRef : undefined}
            type="button"
            aria-pressed={feed === item}
            data-qa-active-feed-tab={feed === item ? 'true' : undefined}
            className={feed === item ? styles.active : ''}
            onClick={() => onFeedChange(item)}
          >
            {item === 'MINE' ? 'MY BALLOTS' : item.replace('_', ' ')}
          </button>
        ))}
      </nav>

      <div className={styles.filters}>
        <label>
          CATEGORY
          <input
            value={category}
            maxLength={50}
            onChange={event => onCategoryChange(event.target.value)}
            placeholder="Exact category, e.g. TECHNOLOGY"
          />
        </label>
        <label>
          STATUS
          <select value={status ?? ''} onChange={event => onStatusChange((event.target.value || undefined) as BallotStatus | undefined)}>
            <option value="">ALL STATUSES</option>
            <option value="OPEN">OPEN</option>
            <option value="CLOSED">CLOSED</option>
          </select>
        </label>
        <button type="button" className={styles.reset} disabled={!hasFilters} onClick={onReset}>CLEAR FILTERS</button>
      </div>

      <p className={styles.summary} role="status">
        <span><strong>{loading ? 'UPDATING' : totalElements}</strong> {loading ? 'SERVER RECORDS' : totalElements === 1 ? 'RECORD' : 'RECORDS'}</span>
        <span>{totalPages === 0 ? 'PAGE 0 OF 0' : `PAGE ${page + 1} OF ${totalPages}`}</span>
      </p>
    </section>
  );
}

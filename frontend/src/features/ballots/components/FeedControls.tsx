'use client';

import { useEffect, useRef } from 'react';
import type { BallotStatus, FeedType } from '@/shared/api/types';
import { useI18n } from '@/shared/i18n/I18nProvider';
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
  const { t, formatNumber } = useI18n();
  const feeds = authenticated ? [...PUBLIC_FEEDS, 'MINE' as const] : PUBLIC_FEEDS;
  const hasFilters = Boolean(query.trim() || category.trim() || status);
  const tabsRef = useRef<HTMLElement | null>(null);
  const activeTabRef = useRef<HTMLButtonElement | null>(null);
  const feedLabels: Record<FeedType, string> = {
    LATEST: t('ballots', 'feedLatest'),
    HOT: t('ballots', 'feedHot'),
    TOP_DAY: t('ballots', 'feedTopDay'),
    TOP_WEEK: t('ballots', 'feedTopWeek'),
    MINE: t('ballots', 'feedMine')
  };

  useEffect(() => {
    const tabs = tabsRef.current;
    const active = activeTabRef.current;
    if (!tabs || !active) return;

    const targetLeft = active.offsetLeft - ((tabs.clientWidth - active.offsetWidth) / 2);
    tabs.scrollTo({ left: Math.max(0, targetLeft), behavior: 'auto' });
  }, [feed]);

  return (
    <section className={styles.controls} aria-label={t('ballots', 'feedControls')}>
      <nav ref={tabsRef} className={styles.tabs} aria-label={t('ballots', 'feedMode')} data-qa-feed-tabs>
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
            {feedLabels[item]}
          </button>
        ))}
      </nav>

      <div className={styles.filters}>
        <label>
          {t('ballots', 'category')}
          <input
            value={category}
            maxLength={50}
            onChange={event => onCategoryChange(event.target.value)}
            placeholder={t('ballots', 'categoryPlaceholder')}
          />
        </label>
        <label>
          {t('ballots', 'status')}
          <select value={status ?? ''} onChange={event => onStatusChange((event.target.value || undefined) as BallotStatus | undefined)}>
            <option value="">{t('ballots', 'allStatuses')}</option>
            <option value="OPEN">{t('ballots', 'statusOpen')}</option>
            <option value="CLOSED">{t('ballots', 'statusClosed')}</option>
          </select>
        </label>
        <button type="button" className={styles.reset} disabled={!hasFilters} onClick={onReset}>{t('ballots', 'clearFilters')}</button>
      </div>

      <p className={styles.summary} role="status">
        <span>
          <strong>{loading ? t('ballots', 'updating') : formatNumber(totalElements)}</strong>{' '}
          {loading ? t('ballots', 'serverRecords') : totalElements === 1 ? t('ballots', 'record') : t('ballots', 'records')}
        </span>
        <span>{t('ballots', 'pageOf', { page: totalPages === 0 ? 0 : page + 1, total: totalPages })}</span>
      </p>
    </section>
  );
}

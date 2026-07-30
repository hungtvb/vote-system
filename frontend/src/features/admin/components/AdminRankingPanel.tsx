'use client';

import { useCallback, useEffect, useState } from 'react';
import { useSession } from '@/features/auth/hooks/useSession';
import { adminApi, type AdminRankingStatus } from '@/shared/api/admin-api';
import { ApiError } from '@/shared/api/transport';
import { useI18n } from '@/shared/i18n/I18nProvider';
import { AdminActionDialog } from './AdminActionDialog';
import styles from './AdminWorkspace.module.scss';

export function AdminRankingPanel() {
  const { runAuthorized } = useSession();
  const { t, formatDate, formatNumber } = useI18n();
  const [status, setStatus] = useState<AdminRankingStatus | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [rebuilding, setRebuilding] = useState(false);
  const [rebuildError, setRebuildError] = useState('');

  const loadStatus = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    setError('');
    try {
      const next = await runAuthorized(active =>
        adminApi.rankingStatus(active.accessToken, signal));
      setStatus(next);
    } catch (cause) {
      if (cause instanceof DOMException && cause.name === 'AbortError') return;
      setStatus(null);
      setError(safeError(cause, t('admin', 'loadFailed')));
    } finally {
      setLoading(false);
    }
  }, [runAuthorized, t]);

  useEffect(() => {
    const controller = new AbortController();
    void loadStatus(controller.signal);
    return () => {
      controller.abort();
      setStatus(null);
    };
  }, [loadStatus]);

  async function rebuild(reason: string) {
    setRebuilding(true);
    setRebuildError('');
    setNotice('');
    try {
      const next = await runAuthorized(active =>
        adminApi.rebuildRanking(reason, active.accessToken));
      setStatus(next);
      setDialogOpen(false);
      setNotice(t('admin', 'actionCompleted'));
    } catch (cause) {
      setRebuildError(safeError(cause, t('admin', 'actionFailed')));
    } finally {
      setRebuilding(false);
    }
  }

  const unavailable = status?.availability === 'UNAVAILABLE';
  const busy = rebuilding || status?.rebuildInProgress === true;

  return (
    <>
      <div className={styles.pageActions}>
        <button
          type="button"
          className={styles.secondaryButton}
          disabled={loading || rebuilding}
          onClick={() => void loadStatus()}
        >
          {loading ? t('admin', 'refreshing') : t('admin', 'refresh')}
        </button>
        <button
          type="button"
          className={styles.primaryButton}
          disabled={loading || busy || unavailable}
          onClick={() => { setRebuildError(''); setDialogOpen(true); }}
        >
          {busy ? 'REBUILDING...' : 'REBUILD RANKING'}
        </button>
      </div>

      {notice && <p className={styles.successNotice} role="status">{notice}</p>}
      {error && <p className={styles.errorNotice} role="alert">{error}</p>}

      {loading && !status ? (
        <div className={styles.statePanel} role="status">
          <span className={styles.loader} />{t('admin', 'refreshing')}
        </div>
      ) : (
        <>
          <section className={styles.callout}>
            <div>
              <span className={styles.eyebrow}>RANKING STATUS</span>
              <h2>{status?.availability ?? 'UNAVAILABLE'}</h2>
            </div>
            <p>
              {status?.availability === 'HEALTHY'
                ? 'Published Redis rankings match the authoritative visible PostgreSQL ballot counts.'
                : status?.availability === 'REBUILDING'
                  ? 'A single-flight rebuild is currently publishing a verified generation.'
                  : status?.availability === 'STALE'
                    ? 'Published ranking counts or UTC windows differ from authoritative PostgreSQL data.'
                    : 'Redis ranking data is unavailable. Public feeds continue using the database fallback.'}
            </p>
          </section>

          <div className={styles.metricGrid}>
            <Metric label="VISIBLE BALLOTS" value={status?.visibleBallots} formatNumber={formatNumber} />
            <Metric label="HOT MEMBERS" value={status?.hotMembers} formatNumber={formatNumber} />
            <Metric label="TOP DAY" value={status?.topDayMembers} expected={status?.eligibleDayBallots} formatNumber={formatNumber} />
            <Metric label="TOP WEEK" value={status?.topWeekMembers} expected={status?.eligibleWeekBallots} formatNumber={formatNumber} />
          </div>

          <section className={styles.rankingPlaceholder}>
            <span className={styles.pendingMark}>↗</span>
            <div>
              <span className={styles.eyebrow}>PUBLISHED GENERATION</span>
              <h2 className={styles.breakable}>{status?.generation ?? 'LEGACY / NOT PUBLISHED'}</h2>
              <p>
                LAST SUCCESSFUL REBUILD · {status?.lastSuccessfulRebuildAt
                  ? formatDate(status.lastSuccessfulRebuildAt, dateOptions)
                  : '—'}
              </p>
            </div>
          </section>
        </>
      )}

      {dialogOpen && (
        <AdminActionDialog
          title="Rebuild published rankings?"
          description="A temporary HOT, TOP DAY, and TOP WEEK generation will be verified before an atomic publish. The current generation stays active if the rebuild fails."
          reasonLabel={t('admin', 'reason')}
          reasonPlaceholder={t('admin', 'reasonPlaceholder')}
          confirmLabel="REBUILD RANKING"
          busyLabel="REBUILDING..."
          cancelLabel={t('common', 'cancel')}
          closeLabel={t('common', 'close')}
          busy={rebuilding}
          error={rebuildError}
          onClose={() => { if (!rebuilding) { setDialogOpen(false); setRebuildError(''); } }}
          onConfirm={reason => rebuild(reason)}
        />
      )}
    </>
  );
}

function Metric({ label, value, expected, formatNumber }: {
  label: string;
  value?: number | null;
  expected?: number;
  formatNumber: (value: number) => string;
}) {
  return (
    <article className={styles.metricCard}>
      <span>{label}</span>
      <strong>{value === null || value === undefined ? '—' : formatNumber(value)}</strong>
      <small>{expected === undefined ? 'AUTHORITATIVE / PUBLISHED' : `EXPECTED ${formatNumber(expected)}`}</small>
    </article>
  );
}

function safeError(error: unknown, fallback: string): string {
  if (error instanceof ApiError) return error.problem?.detail ?? error.message;
  return error instanceof Error ? error.message : fallback;
}

const dateOptions: Intl.DateTimeFormatOptions = {
  dateStyle: 'medium',
  timeStyle: 'short'
};

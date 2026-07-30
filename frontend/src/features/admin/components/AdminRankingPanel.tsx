'use client';

import { useCallback, useEffect, useState } from 'react';
import { useSession } from '@/features/auth/hooks/useSession';
import { adminApi, type AdminRankingStatus, type RankingAvailability } from '@/shared/api/admin-api';
import { ApiError } from '@/shared/api/transport';
import { useI18n } from '@/shared/i18n/I18nProvider';
import { AdminActionDialog } from './AdminActionDialog';
import styles from './AdminWorkspace.module.scss';
import panelStyles from './AdminRankingPanel.module.scss';

export function AdminRankingPanel() {
  const { runAuthorized } = useSession();
  const { locale, t, formatDate, formatNumber } = useI18n();
  const copy = COPY[locale];
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
      setStatus(await runAuthorized(active =>
        adminApi.rankingStatus(active.accessToken, signal)));
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
      setStatus(await runAuthorized(active =>
        adminApi.rebuildRanking(reason, active.accessToken)));
      setDialogOpen(false);
      setNotice(copy.completed);
    } catch (cause) {
      setRebuildError(safeError(cause, t('admin', 'actionFailed')));
    } finally {
      setRebuilding(false);
    }
  }

  const unavailable = !status || status.availability === 'UNAVAILABLE';
  const busy = rebuilding || status?.rebuildInProgress === true;

  return (
    <>
      <div className={panelStyles.actions}>
        <button type="button" className={styles.secondaryButton} disabled={loading || rebuilding} onClick={() => void loadStatus()}>
          {loading ? t('admin', 'refreshing') : t('admin', 'refresh')}
        </button>
        <button
          type="button"
          className={styles.primaryButton}
          disabled={loading || busy || unavailable}
          onClick={() => { setRebuildError(''); setDialogOpen(true); }}
        >
          {busy ? copy.rebuilding : copy.rebuild}
        </button>
      </div>

      {notice && <p className={styles.successNotice} role="status">{notice}</p>}
      {error && <p className={styles.errorNotice} role="alert">{error}</p>}

      {loading && !status ? (
        <div className={styles.statePanel} role="status"><span className={styles.loader} />{copy.loading}</div>
      ) : (
        <>
          <section className={styles.callout}>
            <div>
              <span className={styles.eyebrow}>{copy.status}</span>
              <h2>{status?.availability ?? 'UNAVAILABLE'}</h2>
            </div>
            <p>{availabilityDescription(status?.availability, copy)}</p>
          </section>

          <div className={styles.metricGrid}>
            <Metric label={copy.visibleBallots} value={status?.visibleBallots} note={copy.authoritative} formatNumber={formatNumber} />
            <Metric label={copy.hotMembers} value={status?.hotMembers} note={copy.published} formatNumber={formatNumber} />
            <Metric label="TOP DAY" value={status?.topDayMembers} note={expectedNote(status?.eligibleDayBallots, copy, formatNumber)} formatNumber={formatNumber} />
            <Metric label="TOP WEEK" value={status?.topWeekMembers} note={expectedNote(status?.eligibleWeekBallots, copy, formatNumber)} formatNumber={formatNumber} />
          </div>

          <section className={styles.rankingPlaceholder}>
            <span className={styles.pendingMark}>↗</span>
            <div>
              <span className={styles.eyebrow}>{copy.generation}</span>
              <h2 className={styles.breakable}>{status?.generation ?? copy.legacy}</h2>
              <p>{copy.lastRebuild} · {status?.lastSuccessfulRebuildAt
                ? formatDate(status.lastSuccessfulRebuildAt, dateOptions)
                : '—'}</p>
            </div>
          </section>
        </>
      )}

      {dialogOpen && (
        <AdminActionDialog
          title={copy.dialogTitle}
          description={copy.dialogDescription}
          reasonLabel={t('admin', 'reason')}
          reasonPlaceholder={t('admin', 'reasonPlaceholder')}
          confirmLabel={copy.rebuild}
          busyLabel={copy.rebuilding}
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

function Metric({ label, value, note, formatNumber }: {
  label: string;
  value?: number | null;
  note: string;
  formatNumber: (value: number) => string;
}) {
  return (
    <article className={styles.metricCard}>
      <span>{label}</span>
      <strong>{value === null || value === undefined ? '—' : formatNumber(value)}</strong>
      <small>{note}</small>
    </article>
  );
}

function expectedNote(value: number | null | undefined, copy: Copy, formatNumber: (value: number) => string): string {
  return `${copy.expected} ${value === null || value === undefined ? '—' : formatNumber(value)}`;
}

function availabilityDescription(availability: RankingAvailability | undefined, copy: Copy): string {
  if (availability === 'HEALTHY') return copy.healthy;
  if (availability === 'REBUILDING') return copy.rebuildingDescription;
  if (availability === 'STALE') return copy.stale;
  return copy.unavailable;
}

function safeError(error: unknown, fallback: string): string {
  if (error instanceof ApiError) return error.problem?.detail ?? error.message;
  return error instanceof Error ? error.message : fallback;
}

interface Copy {
  status: string;
  visibleBallots: string;
  hotMembers: string;
  authoritative: string;
  published: string;
  expected: string;
  generation: string;
  legacy: string;
  lastRebuild: string;
  loading: string;
  rebuild: string;
  rebuilding: string;
  completed: string;
  healthy: string;
  stale: string;
  rebuildingDescription: string;
  unavailable: string;
  dialogTitle: string;
  dialogDescription: string;
}

const COPY: Record<'vi' | 'en', Copy> = {
  vi: {
    status: 'TRẠNG THÁI XẾP HẠNG', visibleBallots: 'PHIẾU ĐANG HIỂN THỊ', hotMembers: 'THÀNH VIÊN HOT',
    authoritative: 'DỮ LIỆU POSTGRESQL', published: 'THẾ HỆ ĐANG PHÁT HÀNH', expected: 'KỲ VỌNG',
    generation: 'THẾ HỆ ĐANG PHÁT HÀNH', legacy: 'LEGACY / CHƯA PHÁT HÀNH', lastRebuild: 'REBUILD THÀNH CÔNG GẦN NHẤT',
    loading: 'ĐANG TẢI TRẠNG THÁI XẾP HẠNG...', rebuild: 'REBUILD XẾP HẠNG', rebuilding: 'ĐANG REBUILD...',
    completed: 'Thế hệ xếp hạng đã được kiểm tra, phát hành nguyên tử và ghi nhật ký.',
    healthy: 'Xếp hạng Redis đang khớp với số phiếu công khai có thẩm quyền trong PostgreSQL.',
    stale: 'Số lượng hoặc cửa sổ UTC của xếp hạng đang khác dữ liệu PostgreSQL có thẩm quyền.',
    rebuildingDescription: 'Một tác vụ single-flight đang xây dựng và phát hành thế hệ đã được kiểm tra.',
    unavailable: 'Không thể đọc dữ liệu xếp hạng Redis. Public feed tiếp tục fallback về PostgreSQL.',
    dialogTitle: 'Rebuild xếp hạng đang phát hành?',
    dialogDescription: 'Hệ thống sẽ tạo thế hệ HOT, TOP DAY và TOP WEEK tạm, kiểm tra đầy đủ rồi mới phát hành nguyên tử. Thế hệ hiện tại vẫn hoạt động nếu rebuild thất bại.'
  },
  en: {
    status: 'RANKING STATUS', visibleBallots: 'VISIBLE BALLOTS', hotMembers: 'HOT MEMBERS',
    authoritative: 'AUTHORITATIVE POSTGRESQL', published: 'PUBLISHED GENERATION', expected: 'EXPECTED',
    generation: 'PUBLISHED GENERATION', legacy: 'LEGACY / NOT PUBLISHED', lastRebuild: 'LAST SUCCESSFUL REBUILD',
    loading: 'LOADING RANKING STATUS...', rebuild: 'REBUILD RANKING', rebuilding: 'REBUILDING...',
    completed: 'The verified ranking generation was atomically published and audited.',
    healthy: 'Published Redis rankings match the authoritative visible PostgreSQL ballot counts.',
    stale: 'Published ranking counts or UTC windows differ from authoritative PostgreSQL data.',
    rebuildingDescription: 'A single-flight rebuild is building and publishing a verified generation.',
    unavailable: 'Redis ranking data is unavailable. Public feeds continue using the PostgreSQL fallback.',
    dialogTitle: 'Rebuild published rankings?',
    dialogDescription: 'A temporary HOT, TOP DAY, and TOP WEEK generation will be fully verified before atomic publish. The current generation stays active if the rebuild fails.'
  }
};

const dateOptions: Intl.DateTimeFormatOptions = { dateStyle: 'medium', timeStyle: 'short' };

'use client';

import { type FormEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSession } from '@/features/auth/hooks/useSession';
import {
  adminApi,
  type AdminSystemStatus,
  type SystemMode,
  type UpdateAdminSystemStatusInput
} from '@/shared/api/admin-api';
import { ApiError } from '@/shared/api/transport';
import { useI18n } from '@/shared/i18n/I18nProvider';
import type { MessageKey } from '@/shared/i18n/I18nProvider';
import shellStyles from './AdminWorkspace.module.scss';
import styles from './AdminSystemOperationsPanel.module.scss';

interface DraftStatus {
  mode: SystemMode;
  messageVi: string;
  messageEn: string;
  estimatedEndAt: string;
}

const MODES: SystemMode[] = ['NORMAL', 'READ_ONLY', 'MAINTENANCE'];

export function AdminSystemOperationsPanel() {
  const { runAuthorized } = useSession();
  const { t, formatDate } = useI18n();
  const [status, setStatus] = useState<AdminSystemStatus | null>(null);
  const [draft, setDraft] = useState<DraftStatus>(emptyDraft());
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [confirming, setConfirming] = useState(false);

  const loadStatus = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    setError('');
    try {
      const authoritative = await runAuthorized(active =>
        adminApi.systemStatus(active.accessToken, signal));
      if (!signal?.aborted) {
        setStatus(authoritative);
        setDraft(toDraft(authoritative));
      }
    } catch (cause) {
      if (cause instanceof DOMException && cause.name === 'AbortError') return;
      setError(safeError(cause, t('admin', 'systemLoadFailed')));
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, [runAuthorized, t]);

  useEffect(() => {
    const controller = new AbortController();
    void loadStatus(controller.signal);
    return () => controller.abort();
  }, [loadStatus]);

  const dirty = useMemo(() => status ? !sameState(status, draft) : false, [draft, status]);
  const impact = useMemo(() => impactKeys(draft.mode), [draft.mode]);
  const invalidEstimate = draft.estimatedEndAt
    ? new Date(draft.estimatedEndAt).getTime() <= Date.now()
    : false;

  function selectMode(mode: SystemMode) {
    setNotice('');
    setError('');
    setDraft(current => mode === 'NORMAL'
      ? { mode, messageVi: '', messageEn: '', estimatedEndAt: '' }
      : { ...current, mode });
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!status || !dirty || invalidEstimate || busy) return;
    setError('');
    setNotice('');
    setConfirming(true);
  }

  async function apply(reason: string) {
    if (!status || busy) return;
    setBusy(true);
    setError('');
    try {
      const input = toInput(draft, reason);
      const committed = await runAuthorized(active =>
        adminApi.updateSystemStatus(input, active.accessToken));

      // The PUT response is the committed server record. Keep it visible even if
      // the follow-up reconciliation read is temporarily unavailable.
      setStatus(committed);
      setDraft(toDraft(committed));
      setConfirming(false);
      setNotice(t('admin', 'systemUpdateCompleted'));

      try {
        const authoritative = await runAuthorized(active =>
          adminApi.systemStatus(active.accessToken));
        setStatus(authoritative);
        setDraft(toDraft(authoritative));
      } catch (reloadCause) {
        setError(safeError(reloadCause, t('admin', 'systemLoadFailed')));
      }
    } catch (cause) {
      setError(safeError(cause, t('admin', 'systemUpdateFailed')));
    } finally {
      setBusy(false);
    }
  }

  if (loading && !status) {
    return <div className={shellStyles.statePanel} role="status">{t('admin', 'systemLoading')}</div>;
  }

  return (
    <div className={styles.panel} data-qa-system-operations="true">
      {notice && <p className={shellStyles.successNotice} role="status">{notice}</p>}
      {error && <p className={shellStyles.errorNotice} role="alert">{error}</p>}

      {status && (
        <section className={styles.currentStatus} aria-labelledby="system-current-status">
          <div className={styles.statusHeading}>
            <div>
              <span className={shellStyles.eyebrow}>{t('admin', 'systemAuthoritativeStatus')}</span>
              <h2 id="system-current-status">{t('admin', 'systemCurrentMode')}</h2>
            </div>
            <span className={styles.modeStamp} data-mode={status.mode}>{status.mode}</span>
          </div>
          <dl className={styles.statusGrid}>
            <StatusField label={t('admin', 'systemMessageVi')} value={status.messageVi || '—'} />
            <StatusField label={t('admin', 'systemMessageEn')} value={status.messageEn || '—'} />
            <StatusField
              label={t('admin', 'systemEstimatedEnd')}
              value={status.estimatedEndAt ? formatDate(status.estimatedEndAt, dateOptions) : '—'}
            />
            <StatusField label={t('admin', 'systemLastUpdated')} value={formatDate(status.updatedAt, dateOptions)} />
            <StatusField label={t('admin', 'systemUpdatedBy')} value={status.updatedBy ? shortId(status.updatedBy) : '—'} mono />
          </dl>
        </section>
      )}

      <form className={styles.controlForm} onSubmit={submit} data-qa-system-form="true">
        <fieldset className={styles.modeFieldset} disabled={busy || loading}>
          <legend>{t('admin', 'systemSelectMode')}</legend>
          <div className={styles.modeGrid}>
            {MODES.map(mode => (
              <label className={styles.modeOption} data-mode={mode} key={mode}>
                <input
                  type="radio"
                  name="system-mode"
                  value={mode}
                  checked={draft.mode === mode}
                  onChange={() => selectMode(mode)}
                />
                <span>
                  <strong>{mode}</strong>
                  <small>{t('admin', modeDescriptionKey(mode))}</small>
                </span>
              </label>
            ))}
          </div>
        </fieldset>

        <section className={styles.impactPreview} aria-live="polite">
          <span className={shellStyles.eyebrow}>{t('admin', 'systemImpactPreview')}</span>
          <h3>{t('admin', modeImpactTitleKey(draft.mode))}</h3>
          <ul>
            {impact.map(key => <li key={key}>{t('admin', key)}</li>)}
          </ul>
        </section>

        {draft.mode !== 'NORMAL' && (
          <div className={styles.messageGrid}>
            <label className={styles.field}>
              <span>{t('admin', 'systemMessageVi')}</span>
              <textarea
                rows={4}
                maxLength={200}
                value={draft.messageVi}
                onChange={event => setDraft(current => ({ ...current, messageVi: event.target.value }))}
                placeholder={t('admin', 'systemMessageViPlaceholder')}
              />
              <small>{draft.messageVi.trim().length} / 200</small>
            </label>
            <label className={styles.field}>
              <span>{t('admin', 'systemMessageEn')}</span>
              <textarea
                rows={4}
                maxLength={200}
                value={draft.messageEn}
                onChange={event => setDraft(current => ({ ...current, messageEn: event.target.value }))}
                placeholder={t('admin', 'systemMessageEnPlaceholder')}
              />
              <small>{draft.messageEn.trim().length} / 200</small>
            </label>
            <label className={styles.field}>
              <span>{t('admin', 'systemEstimatedEnd')}</span>
              <input
                type="datetime-local"
                min={minimumDateTime()}
                value={draft.estimatedEndAt}
                onChange={event => setDraft(current => ({ ...current, estimatedEndAt: event.target.value }))}
              />
              <small>{t('admin', 'systemEstimatedEndHint')}</small>
            </label>
          </div>
        )}

        {invalidEstimate && <p className={shellStyles.errorNotice} role="alert">{t('admin', 'systemEstimatedEndInvalid')}</p>}

        <div className={styles.formActions}>
          <button type="button" className={shellStyles.secondaryButton} disabled={loading || busy} onClick={() => void loadStatus()}>
            {loading ? t('admin', 'refreshing') : t('admin', 'refresh')}
          </button>
          <button
            type="submit"
            className={draft.mode === 'MAINTENANCE' ? shellStyles.dangerButton : shellStyles.primaryButton}
            disabled={!status || !dirty || invalidEstimate || loading || busy}
          >
            {t('admin', 'systemReviewChange')}
          </button>
        </div>
      </form>

      {confirming && status && (
        <SystemModeConfirmationDialog
          from={status.mode}
          to={draft.mode}
          busy={busy}
          error={error}
          onClose={() => { if (!busy) { setConfirming(false); setError(''); } }}
          onConfirm={apply}
        />
      )}
    </div>
  );
}

function StatusField({ label, value, mono = false }: { label: string; value: string; mono?: boolean }) {
  return <div><dt>{label}</dt><dd className={mono ? styles.mono : undefined}>{value}</dd></div>;
}

function SystemModeConfirmationDialog({ from, to, busy, error, onClose, onConfirm }: {
  from: SystemMode;
  to: SystemMode;
  busy: boolean;
  error: string;
  onClose: () => void;
  onConfirm: (reason: string) => Promise<void>;
}) {
  const { t } = useI18n();
  const dialogRef = useRef<HTMLDialogElement>(null);
  const returnFocusRef = useRef<HTMLElement | null>(null);
  const busyRef = useRef(busy);
  const onCloseRef = useRef(onClose);
  const [reason, setReason] = useState('');
  busyRef.current = busy;
  onCloseRef.current = onClose;

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    returnFocusRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    dialog.showModal();
    const cancel = (event: Event) => {
      event.preventDefault();
      if (!busyRef.current) onCloseRef.current();
    };
    dialog.addEventListener('cancel', cancel);
    return () => {
      dialog.removeEventListener('cancel', cancel);
      if (dialog.open) dialog.close();
      const target = returnFocusRef.current;
      if (target?.isConnected) window.requestAnimationFrame(() => target.focus());
    };
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalized = reason.trim();
    if (!normalized || busy) return;
    await onConfirm(normalized);
  }

  return (
    <dialog
      ref={dialogRef}
      className={`${shellStyles.dialog} ${styles.systemDialog}`}
      aria-labelledby="system-mode-dialog-title"
      aria-describedby="system-mode-dialog-description"
      data-qa-system-dialog="true"
    >
      <form className={shellStyles.dialogBody} onSubmit={submit}>
        <div className={shellStyles.dialogHeader}>
          <div>
            <span className={shellStyles.eyebrow}>{t('admin', 'systemConfirmationEyebrow')}</span>
            <h2 id="system-mode-dialog-title">{t('admin', confirmationTitleKey(to))}</h2>
          </div>
          <button type="button" className={shellStyles.iconButton} aria-label={t('common', 'close')} disabled={busy} onClick={onClose}>×</button>
        </div>
        <p id="system-mode-dialog-description" className={styles.dialogDescription}>{t('admin', 'systemConfirmationDescription', { from, to })}</p>
        <div className={styles.transitionLine} aria-label={`${from} to ${to}`}>
          <span>{from}</span><span aria-hidden="true">→</span><strong>{to}</strong>
        </div>
        <label className={shellStyles.field}>
          <span>{t('admin', 'reason')}</span>
          <textarea
            autoFocus
            required
            rows={4}
            maxLength={500}
            value={reason}
            placeholder={t('admin', 'reasonPlaceholder')}
            onChange={event => setReason(event.target.value)}
          />
          <small>{reason.trim().length} / 500</small>
        </label>
        {error && <p className={shellStyles.errorNotice} role="alert">{error}</p>}
        <div className={shellStyles.dialogActions}>
          <button type="button" className={shellStyles.secondaryButton} disabled={busy} onClick={onClose}>{t('common', 'cancel')}</button>
          <button
            type="submit"
            className={to === 'MAINTENANCE' ? shellStyles.dangerButton : shellStyles.primaryButton}
            disabled={busy || !reason.trim()}
          >
            {busy ? t('admin', 'applying') : t('admin', confirmationButtonKey(to))}
          </button>
        </div>
      </form>
    </dialog>
  );
}

function emptyDraft(): DraftStatus {
  return { mode: 'NORMAL', messageVi: '', messageEn: '', estimatedEndAt: '' };
}

function toDraft(status: AdminSystemStatus): DraftStatus {
  return {
    mode: status.mode,
    messageVi: status.messageVi ?? '',
    messageEn: status.messageEn ?? '',
    estimatedEndAt: status.estimatedEndAt ? toLocalDateTimeInput(new Date(status.estimatedEndAt)) : ''
  };
}

function toInput(draft: DraftStatus, reason: string): UpdateAdminSystemStatusInput {
  if (draft.mode === 'NORMAL') {
    return { mode: 'NORMAL', messageVi: null, messageEn: null, estimatedEndAt: null, reason };
  }
  return {
    mode: draft.mode,
    messageVi: draft.messageVi.trim() || null,
    messageEn: draft.messageEn.trim() || null,
    estimatedEndAt: draft.estimatedEndAt ? new Date(draft.estimatedEndAt).toISOString() : null,
    reason
  };
}

function sameState(status: AdminSystemStatus, draft: DraftStatus): boolean {
  const input = toInput(draft, 'comparison');
  return status.mode === input.mode
    && (status.messageVi ?? null) === input.messageVi
    && (status.messageEn ?? null) === input.messageEn
    && normalizeToLocalMinute(status.estimatedEndAt) === normalizeToLocalMinute(input.estimatedEndAt ?? undefined);
}

function normalizeToLocalMinute(value?: string): string | null {
  return value ? toLocalDateTimeInput(new Date(value)) : null;
}

function impactKeys(mode: SystemMode): MessageKey<'admin'>[] {
  if (mode === 'READ_ONLY') return ['systemImpactReadOnly1', 'systemImpactReadOnly2', 'systemImpactReadOnly3', 'systemImpactReadOnly4'];
  if (mode === 'MAINTENANCE') return ['systemImpactMaintenance1', 'systemImpactMaintenance2', 'systemImpactMaintenance3'];
  return ['systemImpactNormal1', 'systemImpactNormal2'];
}

function modeDescriptionKey(mode: SystemMode): MessageKey<'admin'> {
  return ({
    NORMAL: 'systemModeNormalDescription',
    READ_ONLY: 'systemModeReadOnlyDescription',
    MAINTENANCE: 'systemModeMaintenanceDescription'
  } as const)[mode];
}

function modeImpactTitleKey(mode: SystemMode): MessageKey<'admin'> {
  return ({
    NORMAL: 'systemImpactNormalTitle',
    READ_ONLY: 'systemImpactReadOnlyTitle',
    MAINTENANCE: 'systemImpactMaintenanceTitle'
  } as const)[mode];
}

function confirmationTitleKey(mode: SystemMode): MessageKey<'admin'> {
  return mode === 'MAINTENANCE' ? 'systemConfirmMaintenanceTitle'
    : mode === 'NORMAL' ? 'systemConfirmNormalTitle'
      : 'systemConfirmReadOnlyTitle';
}

function confirmationButtonKey(mode: SystemMode): MessageKey<'admin'> {
  return mode === 'MAINTENANCE' ? 'systemEnableMaintenance'
    : mode === 'NORMAL' ? 'systemRestoreNormal'
      : 'systemEnableReadOnly';
}

function toLocalDateTimeInput(value: Date): string {
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${pad(value.getHours())}:${pad(value.getMinutes())}`;
}

function minimumDateTime(): string {
  return toLocalDateTimeInput(new Date(Date.now() + 60_000));
}

function shortId(value: string): string {
  return value.slice(0, 8);
}

function safeError(error: unknown, fallback: string): string {
  if (error instanceof ApiError) return error.problem?.detail ?? error.message;
  return error instanceof Error ? error.message : fallback;
}

const dateOptions: Intl.DateTimeFormatOptions = { dateStyle: 'medium', timeStyle: 'short' };

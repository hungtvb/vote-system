'use client';

import { type FormEvent, useEffect, useRef, useState } from 'react';
import styles from './AdminWorkspace.module.scss';

interface AdminActionDialogProps {
  title: string;
  description: string;
  reasonLabel: string;
  reasonPlaceholder: string;
  untilLabel?: string;
  confirmLabel: string;
  busyLabel: string;
  cancelLabel: string;
  closeLabel: string;
  destructive?: boolean;
  busy: boolean;
  error?: string;
  onClose: () => void;
  onConfirm: (reason: string, until: string | null) => Promise<void>;
}

export function AdminActionDialog({
  title,
  description,
  reasonLabel,
  reasonPlaceholder,
  untilLabel,
  confirmLabel,
  busyLabel,
  cancelLabel,
  closeLabel,
  destructive = false,
  busy,
  error,
  onClose,
  onConfirm
}: AdminActionDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const busyRef = useRef(busy);
  const onCloseRef = useRef(onClose);
  const [reason, setReason] = useState('');
  const [until, setUntil] = useState('');
  const now = new Date();
  const minUntil = toLocalDateTimeInput(new Date(now.getTime() + 60_000));
  const maxUntil = toLocalDateTimeInput(new Date(now.getTime() + 365 * 24 * 60 * 60 * 1_000));
  busyRef.current = busy;
  onCloseRef.current = onClose;

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    dialog.showModal();
    const handleCancel = (event: Event) => {
      event.preventDefault();
      if (!busyRef.current) onCloseRef.current();
    };
    dialog.addEventListener('cancel', handleCancel);
    return () => {
      dialog.removeEventListener('cancel', handleCancel);
      if (dialog.open) dialog.close();
    };
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedReason = reason.trim();
    if (!normalizedReason || busy) return;
    await onConfirm(
      normalizedReason,
      until ? new Date(until).toISOString() : null
    );
  }

  return (
    <dialog ref={dialogRef} className={styles.dialog} aria-labelledby="admin-action-title">
      <form onSubmit={submit} className={styles.dialogBody}>
        <div className={styles.dialogHeader}>
          <div>
            <span className={styles.eyebrow}>ADMIN ACTION</span>
            <h2 id="admin-action-title">{title}</h2>
          </div>
          <button type="button" className={styles.iconButton} aria-label={closeLabel} disabled={busy} onClick={onClose}>×</button>
        </div>
        <p className={styles.dialogDescription}>{description}</p>
        <label className={styles.field}>
          <span>{reasonLabel}</span>
          <textarea
            autoFocus
            required
            maxLength={500}
            rows={5}
            value={reason}
            placeholder={reasonPlaceholder}
            onChange={event => setReason(event.target.value)}
          />
          <small>{reason.trim().length} / 500</small>
        </label>
        {untilLabel && (
          <label className={styles.field}>
            <span>{untilLabel}</span>
            <input
              type="datetime-local"
              min={minUntil}
              max={maxUntil}
              value={until}
              onChange={event => setUntil(event.target.value)}
            />
          </label>
        )}
        {error && <p className={styles.errorNotice} role="alert">{error}</p>}
        <div className={styles.dialogActions}>
          <button type="button" className={styles.secondaryButton} disabled={busy} onClick={onClose}>{cancelLabel}</button>
          <button
            type="submit"
            className={destructive ? styles.dangerButton : styles.primaryButton}
            disabled={busy || !reason.trim()}
          >
            {busy ? busyLabel : confirmLabel}
          </button>
        </div>
      </form>
    </dialog>
  );
}

function toLocalDateTimeInput(value: Date): string {
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${pad(value.getHours())}:${pad(value.getMinutes())}`;
}

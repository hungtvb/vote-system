import type { PublicSystemStatus } from '@/shared/api/system-status-api';

export function selectPublicMessage(status: PublicSystemStatus | null, locale: 'vi' | 'en'): string {
  if (!status) return '';
  const primary = locale === 'vi' ? status.messageVi : status.messageEn;
  const fallback = locale === 'vi' ? status.messageEn : status.messageVi;
  return primary || fallback || '';
}

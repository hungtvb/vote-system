import type { AuthIntent } from './auth-intent';
import type { SocialProviderId } from '@/shared/api/social-auth-api';

export type SocialCallbackStatus = 'success' | 'linked' | 'error';

export interface SocialCallback {
  status: SocialCallbackStatus;
  provider?: SocialProviderId;
  intent: AuthIntent;
  code?: string;
}

const PROVIDERS = new Set<SocialProviderId>(['google', 'github']);

type CallbackLocale = 'vi' | 'en';

const ERROR_MESSAGES: Record<CallbackLocale, Record<string, string>> = {
  vi: {
    access_denied: 'Đăng nhập mạng xã hội đã bị hủy.',
    account_link_required: 'Email đã xác minh này đã thuộc một tài khoản Vote System. Hãy đăng nhập bằng tài khoản đó rồi liên kết nhà cung cấp từ Mã cử tri.',
    identity_already_linked: 'Danh tính từ nhà cung cấp này đã được liên kết với một tài khoản Vote System khác.',
    provider_already_linked: 'Nhà cung cấp này đã được liên kết với tài khoản Vote System của bạn.',
    email_owned_by_another_account: 'Email từ nhà cung cấp thuộc một tài khoản Vote System khác.',
    oauth_failed: 'Không thể hoàn tất đăng nhập mạng xã hội.'
  },
  en: {
    access_denied: 'Social sign-in was cancelled.',
    account_link_required: 'This verified email already belongs to a Vote System account. Sign in with that account, then link the provider from Voter ID.',
    identity_already_linked: 'This provider identity is already linked to another Vote System account.',
    provider_already_linked: 'This provider is already linked to your Vote System account.',
    email_owned_by_another_account: 'The provider email belongs to another Vote System account.',
    oauth_failed: 'Social sign-in could not be completed.'
  }
};

export function parseSocialCallback(search: string): SocialCallback | null {
  const params = new URLSearchParams(search);
  const status = params.get('social');
  if (status !== 'success' && status !== 'linked' && status !== 'error') return null;

  const rawProvider = params.get('provider');
  const provider = rawProvider && PROVIDERS.has(rawProvider as SocialProviderId)
    ? rawProvider as SocialProviderId
    : undefined;
  const intent = params.get('intent') === 'create-ballot' ? 'create-ballot' : 'authenticate';
  const rawCode = params.get('code');
  const code = rawCode && /^[a-z0-9_-]{1,64}$/.test(rawCode) ? rawCode : undefined;

  return { status, provider, intent, code };
}

export function socialCallbackMessage(callback: SocialCallback): string {
  const locale = callbackLocale();
  const provider = providerName(callback.provider, locale);

  if (callback.status === 'linked') {
    return locale === 'vi'
      ? `${provider} đã được liên kết với Mã cử tri của bạn.`
      : `${provider} is now linked to your Voter ID.`;
  }
  if (callback.status === 'success') {
    return locale === 'vi'
      ? `Đã đăng nhập bằng ${provider}.`
      : `Signed in with ${provider}.`;
  }
  return ERROR_MESSAGES[locale][callback.code ?? 'oauth_failed'] ?? ERROR_MESSAGES[locale].oauth_failed;
}

export function stripSocialCallback(url: URL): string {
  const next = new URL(url.toString());
  for (const key of ['social', 'provider', 'intent', 'code']) next.searchParams.delete(key);
  return `${next.pathname}${next.search}${next.hash}`;
}

function callbackLocale(): CallbackLocale {
  if (typeof document === 'undefined') return 'en';
  return document.documentElement.lang.toLowerCase().startsWith('en') ? 'en' : 'vi';
}

function providerName(provider: SocialProviderId | undefined, locale: CallbackLocale): string {
  if (provider === 'github') return 'GitHub';
  if (provider === 'google') return 'Google';
  return locale === 'vi' ? 'nhà cung cấp' : 'the provider';
}

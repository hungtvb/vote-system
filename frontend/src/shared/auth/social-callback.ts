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
const ERROR_MESSAGES: Record<string, string> = {
  access_denied: 'Social sign-in was cancelled.',
  account_link_required: 'This verified email already belongs to a Vote System account. Sign in with that account, then link the provider from Voter ID.',
  identity_already_linked: 'This provider identity is already linked to another Vote System account.',
  provider_already_linked: 'This provider is already linked to your Vote System account.',
  email_owned_by_another_account: 'The provider email belongs to another Vote System account.',
  oauth_failed: 'Social sign-in could not be completed.'
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
  if (callback.status === 'linked') {
    return `${providerName(callback.provider)} is now linked to your Voter ID.`;
  }
  if (callback.status === 'success') {
    return `Signed in with ${providerName(callback.provider)}.`;
  }
  return ERROR_MESSAGES[callback.code ?? 'oauth_failed'] ?? ERROR_MESSAGES.oauth_failed;
}

export function stripSocialCallback(url: URL): string {
  const next = new URL(url.toString());
  for (const key of ['social', 'provider', 'intent', 'code']) next.searchParams.delete(key);
  return `${next.pathname}${next.search}${next.hash}`;
}

function providerName(provider?: SocialProviderId): string {
  return provider === 'github' ? 'GitHub' : provider === 'google' ? 'Google' : 'the provider';
}

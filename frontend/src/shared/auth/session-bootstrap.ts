import type { AuthBootstrap, Session, UserProfile } from '@/shared/api/types';

export type LegacyProfileLoader = (accessToken: string) => Promise<UserProfile>;

export function sessionOnly(next: Session): Session {
  return {
    tokenType: next.tokenType,
    accessToken: next.accessToken,
    expiresInSeconds: next.expiresInSeconds,
    userId: next.userId,
    email: next.email,
    role: next.role
  };
}

export function hasBootstrapProfile(next: Session): next is AuthBootstrap {
  const profile = (next as Partial<AuthBootstrap>).profile;
  return Boolean(profile && profile.id === next.userId);
}

export async function resolveAuthProfile(
  next: Session,
  loadLegacyProfile: LegacyProfileLoader
): Promise<UserProfile> {
  if (hasBootstrapProfile(next)) return next.profile;
  return loadLegacyProfile(next.accessToken);
}

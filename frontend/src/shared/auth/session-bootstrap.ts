import type { AuthBootstrap, Session, UserProfile } from '../api/types';

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
  return Boolean((next as Partial<AuthBootstrap>).profile);
}

export async function resolveAuthProfile(
  next: Session,
  loadLegacyProfile: LegacyProfileLoader
): Promise<UserProfile> {
  if (!hasBootstrapProfile(next)) return loadLegacyProfile(next.accessToken);
  if (next.profile.id !== next.userId) {
    throw new Error('Authenticated profile does not match the issued session');
  }
  return next.profile;
}

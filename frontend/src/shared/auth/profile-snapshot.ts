import type { AvatarColor, AvatarIcon, SocialProvider, UserProfile } from '../api/types';

const STORAGE_KEY = 'vote-system.profile-presentation.v1';
const SNAPSHOT_VERSION = 1 as const;
const AVATAR_ICONS = new Set<AvatarIcon>([
  'CITIZEN', 'ADVOCATE', 'THINKER', 'ORGANIZER', 'VOLUNTEER',
  'CREATOR', 'LEADER', 'ANALYST', 'VISIONARY', 'BUILDER'
]);
const AVATAR_COLORS = new Set<AvatarColor>([
  'NAVY', 'SEAL', 'KRAFT', 'GRAPHITE', 'MOSS', 'INK_BLUE'
]);
const ROLES = new Set(['USER', 'ADMIN']);
const PROVIDERS = new Set<SocialProvider>(['GOOGLE', 'GITHUB']);

export interface ProfilePresentationSnapshot {
  version: typeof SNAPSHOT_VERSION;
  userId: string;
  displayName: string;
  initials: string;
  avatarIcon: AvatarIcon;
  avatarColor: AvatarColor;
  roleLabel: 'USER' | 'ADMIN';
  linkedProviders: SocialProvider[];
}

export interface SnapshotStorage {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

export function snapshotFromProfile(profile: UserProfile): ProfilePresentationSnapshot {
  return {
    version: SNAPSHOT_VERSION,
    userId: profile.id,
    displayName: profile.displayName,
    initials: profile.initials,
    avatarIcon: profile.avatarIcon,
    avatarColor: profile.avatarColor,
    roleLabel: profile.role === 'ADMIN' ? 'ADMIN' : 'USER',
    linkedProviders: [...new Set(profile.linkedProviders)].filter(provider => PROVIDERS.has(provider))
  };
}

export function readProfileSnapshot(storage: SnapshotStorage): ProfilePresentationSnapshot | null {
  try {
    const raw = storage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed: unknown = JSON.parse(raw);
    if (!isProfileSnapshot(parsed)) {
      storage.removeItem(STORAGE_KEY);
      return null;
    }
    return parsed;
  } catch {
    try {
      storage.removeItem(STORAGE_KEY);
    } catch {
      // Storage can be unavailable in privacy-restricted browsers. Auth remains authoritative.
    }
    return null;
  }
}

export function writeProfileSnapshot(
  storage: SnapshotStorage,
  profile: UserProfile
): ProfilePresentationSnapshot | null {
  const snapshot = snapshotFromProfile(profile);
  try {
    storage.setItem(STORAGE_KEY, JSON.stringify(snapshot));
    return snapshot;
  } catch {
    return null;
  }
}

export function clearProfileSnapshot(storage: SnapshotStorage): void {
  try {
    storage.removeItem(STORAGE_KEY);
  } catch {
    // Clearing browser presentation state must never break logout or session invalidation.
  }
}

export function profileSnapshotStorageKey(): string {
  return STORAGE_KEY;
}

function isProfileSnapshot(value: unknown): value is ProfilePresentationSnapshot {
  if (!isRecord(value) || value.version !== SNAPSHOT_VERSION) return false;
  const keys = Object.keys(value).sort();
  const expectedKeys = [
    'avatarColor', 'avatarIcon', 'displayName', 'initials',
    'linkedProviders', 'roleLabel', 'userId', 'version'
  ];
  if (keys.length !== expectedKeys.length || keys.some((key, index) => key !== expectedKeys[index])) return false;
  if (!isBoundedString(value.userId, 1, 64)) return false;
  if (!isBoundedString(value.displayName, 2, 40)) return false;
  if (!isBoundedString(value.initials, 1, 8)) return false;
  if (!AVATAR_ICONS.has(value.avatarIcon as AvatarIcon)) return false;
  if (!AVATAR_COLORS.has(value.avatarColor as AvatarColor)) return false;
  if (!ROLES.has(value.roleLabel as string)) return false;
  if (!Array.isArray(value.linkedProviders) || value.linkedProviders.length > PROVIDERS.size) return false;
  return value.linkedProviders.every(provider => typeof provider === 'string' && PROVIDERS.has(provider as SocialProvider))
    && new Set(value.linkedProviders).size === value.linkedProviders.length;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isBoundedString(value: unknown, min: number, max: number): value is string {
  return typeof value === 'string' && value.length >= min && value.length <= max;
}

import assert from 'node:assert/strict';
import test from 'node:test';

import type { UserProfile } from '../../api/types';
import {
  clearProfileSnapshot,
  profileSnapshotStorageKey,
  readProfileSnapshot,
  snapshotFromProfile,
  type SnapshotStorage,
  writeProfileSnapshot
} from '../profile-snapshot';

const PROFILE: UserProfile = {
  id: '0198b742-61df-7b16-a7e8-462f306cc6ed',
  email: 'private@example.com',
  displayName: 'Registry Voter',
  initials: 'RV',
  bio: 'Private profile biography',
  avatarIcon: 'ANALYST',
  avatarColor: 'INK_BLUE',
  preferredLocale: 'vi',
  role: 'ADMIN',
  linkedProviders: ['GOOGLE', 'GITHUB'],
  createdAt: '2026-07-20T09:00:00Z',
  updatedAt: '2026-08-01T10:00:00Z'
};

class MemoryStorage implements SnapshotStorage {
  readonly values = new Map<string, string>();
  removed: string[] = [];

  getItem(key: string) {
    return this.values.get(key) ?? null;
  }

  setItem(key: string, value: string) {
    this.values.set(key, value);
  }

  removeItem(key: string) {
    this.removed.push(key);
    this.values.delete(key);
  }
}

test('snapshot contains presentation data only', () => {
  const snapshot = snapshotFromProfile(PROFILE);
  const serialized = JSON.stringify(snapshot);

  assert.deepEqual(snapshot, {
    version: 1,
    userId: PROFILE.id,
    displayName: 'Registry Voter',
    initials: 'RV',
    avatarIcon: 'ANALYST',
    avatarColor: 'INK_BLUE',
    roleLabel: 'ADMIN',
    linkedProviders: ['GOOGLE', 'GITHUB']
  });
  for (const forbidden of ['accessToken', 'refreshToken', 'email', 'bio', 'preferredLocale', 'createdAt', 'updatedAt']) {
    assert.equal(serialized.includes(forbidden), false);
  }
  assert.equal(serialized.includes('private@example.com'), false);
});

test('valid versioned snapshot round-trips through storage', () => {
  const storage = new MemoryStorage();
  const written = writeProfileSnapshot(storage, PROFILE);

  assert.deepEqual(readProfileSnapshot(storage), written);
  assert.equal(storage.values.size, 1);
});

test('version mismatch and malformed values are removed', () => {
  const storage = new MemoryStorage();
  const key = profileSnapshotStorageKey();

  storage.values.set(key, JSON.stringify({ ...snapshotFromProfile(PROFILE), version: 2 }));
  assert.equal(readProfileSnapshot(storage), null);
  assert.deepEqual(storage.removed, [key]);

  storage.values.set(key, JSON.stringify({ ...snapshotFromProfile(PROFILE), roleLabel: 'SUPERADMIN' }));
  assert.equal(readProfileSnapshot(storage), null);

  storage.values.set(key, JSON.stringify({ ...snapshotFromProfile(PROFILE), accessToken: 'must-not-survive' }));
  assert.equal(readProfileSnapshot(storage), null);
  assert.deepEqual(storage.removed, [key, key, key]);
});

test('invalid JSON and storage failures fail closed without breaking auth', () => {
  const key = profileSnapshotStorageKey();
  const storage = new MemoryStorage();
  storage.values.set(key, '{not-json');
  assert.equal(readProfileSnapshot(storage), null);

  const blocked: SnapshotStorage = {
    getItem() { throw new Error('blocked'); },
    setItem() { throw new Error('blocked'); },
    removeItem() { throw new Error('blocked'); }
  };
  assert.equal(readProfileSnapshot(blocked), null);
  assert.equal(writeProfileSnapshot(blocked, PROFILE), null);
  assert.doesNotThrow(() => clearProfileSnapshot(blocked));
});

test('clear removes the current schema key', () => {
  const storage = new MemoryStorage();
  writeProfileSnapshot(storage, PROFILE);
  clearProfileSnapshot(storage);
  assert.equal(readProfileSnapshot(storage), null);
  assert.deepEqual(storage.removed, [profileSnapshotStorageKey()]);
});

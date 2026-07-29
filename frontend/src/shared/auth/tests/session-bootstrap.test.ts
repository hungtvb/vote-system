import assert from 'node:assert/strict';
import test from 'node:test';

import type { AuthBootstrap, Session, UserProfile } from '../../api/types';
import { resolveAuthProfile, sessionOnly } from '../session-bootstrap';

const PROFILE: UserProfile = {
  id: 'user-1',
  email: 'voter@example.com',
  displayName: 'Registry Voter',
  initials: 'RV',
  bio: null,
  avatarIcon: 'CITIZEN',
  avatarColor: 'NAVY',
  preferredLocale: 'vi',
  role: 'USER',
  linkedProviders: ['GOOGLE'],
  createdAt: '2026-07-20T09:00:00Z',
  updatedAt: '2026-07-29T01:00:00Z'
};

const BOOTSTRAP: AuthBootstrap = {
  tokenType: 'Bearer',
  accessToken: 'bootstrap-token',
  expiresInSeconds: 900,
  refreshExpiresInSeconds: 2_592_000,
  userId: PROFILE.id,
  email: PROFILE.email,
  role: PROFILE.role,
  profile: PROFILE
};

const LEGACY_SESSION: Session = sessionOnly(BOOTSTRAP);

test('bootstrap profile resolves without the legacy /users/me loader', async () => {
  let legacyCalls = 0;
  const profile = await resolveAuthProfile(BOOTSTRAP, async () => {
    legacyCalls += 1;
    return PROFILE;
  });

  assert.strictEqual(profile, PROFILE);
  assert.equal(legacyCalls, 0);
  assert.deepEqual(sessionOnly(BOOTSTRAP), LEGACY_SESSION);
  assert.equal('profile' in sessionOnly(BOOTSTRAP), false);
  assert.equal('accessToken' in PROFILE, false);
});

test('legacy auth response uses the compatibility profile loader once', async () => {
  const tokens: string[] = [];
  const profile = await resolveAuthProfile(LEGACY_SESSION, async token => {
    tokens.push(token);
    return PROFILE;
  });

  assert.strictEqual(profile, PROFILE);
  assert.deepEqual(tokens, ['bootstrap-token']);
});

test('mismatched bootstrap profile is rejected without fallback', async () => {
  let legacyCalls = 0;
  const mismatched: AuthBootstrap = {
    ...BOOTSTRAP,
    profile: { ...PROFILE, id: 'another-user' }
  };

  await assert.rejects(
    resolveAuthProfile(mismatched, async () => {
      legacyCalls += 1;
      return PROFILE;
    }),
    /does not match/
  );
  assert.equal(legacyCalls, 0);
});

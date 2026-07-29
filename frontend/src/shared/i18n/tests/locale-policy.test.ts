import assert from 'node:assert/strict';
import test from 'node:test';

import {
  DEFAULT_LOCALE,
  resolveGuestLocale,
  resolveProfileBootstrapLocale,
  resolveProfileSavedLocale
} from '../locale-policy';

test('saved local preference wins over browser language', () => {
  assert.equal(resolveGuestLocale('en', ['vi-VN']), 'en');
  assert.equal(resolveGuestLocale('vi', ['en-US']), 'vi');
});

test('browser language fallback uses the first supported primary language', () => {
  assert.equal(resolveGuestLocale(null, ['fr-FR', 'en-US', 'vi-VN']), 'en');
  assert.equal(resolveGuestLocale(undefined, ['de-DE', 'vi_VN']), 'vi');
});

test('invalid saved and unsupported browser locales fall back to Vietnamese', () => {
  assert.equal(resolveGuestLocale('en-US', ['fr-FR']), DEFAULT_LOCALE);
  assert.equal(resolveGuestLocale('unknown', []), 'vi');
});

test('authenticated profile locale applies once per resolved user', () => {
  const first = resolveProfileBootstrapLocale(null, {
    id: 'user-1',
    preferredLocale: 'en'
  });

  assert.deepEqual(first, {
    appliedUserId: 'user-1',
    localeToApply: 'en'
  });

  const afterManualOverride = resolveProfileBootstrapLocale(first.appliedUserId, {
    id: 'user-1',
    preferredLocale: 'en'
  });

  assert.deepEqual(afterManualOverride, {
    appliedUserId: 'user-1',
    localeToApply: null
  });
});

test('a different authenticated user receives their own preferred locale', () => {
  assert.deepEqual(
    resolveProfileBootstrapLocale('user-1', {
      id: 'user-2',
      preferredLocale: 'vi'
    }),
    {
      appliedUserId: 'user-2',
      localeToApply: 'vi'
    }
  );
});

test('clearing the authenticated profile resets the applied-user boundary', () => {
  assert.deepEqual(resolveProfileBootstrapLocale('user-1', null), {
    appliedUserId: null,
    localeToApply: null
  });
});

test('saving profile locale applies the persisted value immediately', () => {
  assert.deepEqual(
    resolveProfileSavedLocale({ id: 'user-1', preferredLocale: 'en' }),
    {
      appliedUserId: 'user-1',
      localeToApply: 'en'
    }
  );
});

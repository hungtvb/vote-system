import assert from 'node:assert/strict';
import test from 'node:test';

import { parseSocialCallback, socialCallbackMessage, stripSocialCallback } from '../social-callback';

test('social success preserves the create ballot continuation intent', () => {
  assert.deepEqual(parseSocialCallback('?social=success&provider=google&intent=create-ballot'), {
    status: 'success',
    provider: 'google',
    intent: 'create-ballot',
    code: undefined
  });
});

test('direct social login defaults to authenticate intent', () => {
  assert.deepEqual(parseSocialCallback('?social=success&provider=github'), {
    status: 'success',
    provider: 'github',
    intent: 'authenticate',
    code: undefined
  });
});

test('unsafe providers and error codes are not reflected', () => {
  assert.deepEqual(parseSocialCallback('?social=error&provider=javascript:alert(1)&code=%3Cscript%3E'), {
    status: 'error',
    provider: undefined,
    intent: 'authenticate',
    code: undefined
  });
});

test('account-link-required callback has actionable copy', () => {
  const callback = parseSocialCallback('?social=error&code=account_link_required');
  assert.ok(callback);
  assert.match(socialCallbackMessage(callback), /link the provider from Voter ID/i);
});

test('unavailable account callback stays generic and actionable', () => {
  const callback = parseSocialCallback('?social=error&code=account_unavailable');
  assert.ok(callback);
  const message = socialCallbackMessage(callback);
  assert.match(message, /account is currently unavailable/i);
  assert.doesNotMatch(message, /suspend|ban|reason/i);
});

test('callback cleanup removes only social parameters', () => {
  const url = new URL('https://vote.test/?feed=HOT&social=success&provider=google&intent=create-ballot#top');
  assert.equal(stripSocialCallback(url), '/?feed=HOT#top');
});

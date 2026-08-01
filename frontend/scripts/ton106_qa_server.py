#!/usr/bin/env python3
"""Extend the deterministic visual QA server with auth and social-login scenarios."""

from __future__ import annotations

import mimetypes
import threading
from http.server import ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

import visual_qa_server as base

EXTRA_QA_SCRIPT = r"""
<script>
(() => {
  const mode = new URLSearchParams(location.search).get('qa') || 'feed';
  const supported = new Set([
    'feed',
    'detail',
    'auth',
    'auth-bootstrap',
    'auth-menu',
    'auth-stamp',
    'server-search',
    'pagination',
    'auth-mine',
    'guest-register-dialog',
    'guest-create-auth',
    'guest-register-complete',
    'guest-create-resume',
    'social-buttons',
    'auth-social-create',
    'auth-social-error',
    'auth-social-linked',
    'reduced-motion',
    'auth-owner-actions',
    'auth-delete-modal',
    'auth-negative-score',
    'read-only',
    'auth-read-only',
    'maintenance',
    'maintenance-retry'
  ]);
  if (!supported.has(mode)) return;

  const root = document.documentElement;
  const setInput = (input, value) => {
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set;
    setter?.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
  };
  const waitFor = (predicate, callback, attempt = 0) => {
    if (predicate()) return callback();
    if (attempt >= 70) return record('timeout');
    setTimeout(() => waitFor(predicate, callback, attempt + 1), 80);
  };
  const keyTouchTargets = () => [...document.querySelectorAll(
    '[data-qa-create-ballot], [data-qa-auth-submit], [data-qa-submit-ballot], [data-qa-auth-tab], [data-qa-social-provider], [data-qa-owner-action], [data-qa-confirm-action], [data-qa-system-retry]'
  )].filter(element => {
    const rect = element.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0;
  });
  const textWraps = element => {
    const range = document.createRange();
    range.selectNodeContents(element);
    return range.getClientRects().length > 1;
  };
  const record = (status = 'complete') => {
    const dialog = document.querySelector('[role="dialog"]');
    const authDialog = document.querySelector('[data-qa-auth-dialog]');
    const createDialog = document.querySelector('[data-qa-create-dialog]');
    const confirmDialog = document.querySelector('[data-qa-confirm-dialog]');
    const authSubmit = document.querySelector('[data-qa-auth-submit]');
    const submitBallot = document.querySelector('[data-qa-submit-ballot]');
    const createButton = document.querySelector('[data-qa-create-ballot]');
    const registerButton = document.querySelector('[data-qa-register]');
    const loginButton = document.querySelector('[data-qa-login]');
    const voteOptions = [...document.querySelectorAll('[data-qa-vote-option]')];
    const systemNotice = document.querySelector('[data-qa-system-mode]');
    const systemRetry = document.querySelector('[data-qa-system-retry]');
    const profileEdit = document.querySelector('[data-qa-profile-edit]');
    const providerLinks = [...document.querySelectorAll('[data-qa-provider-link]')];
    const socialButtons = [...document.querySelectorAll('[data-qa-social-provider]')];
    const ownerActions = [...document.querySelectorAll('[data-qa-owner-action]')];
    const ownerWrapViolations = ownerActions.filter(textWraps);
    const voteScores = [...document.querySelectorAll('[data-qa-vote-score]')]
      .map(element => element.getAttribute('data-qa-vote-score') || '');
    const scoreFormatViolations = voteScores.filter(score => !/^[+-]\d{4,}$/.test(score));
    const tabs = document.querySelector('[data-qa-feed-tabs]');
    const nestedVerticalOverflow = Boolean(tabs && tabs.scrollHeight > tabs.clientHeight + 1);
    const documentHorizontalOverflow = root.scrollWidth > root.clientWidth;
    const targets = keyTouchTargets();
    const violations = targets.filter(element => {
      const rect = element.getBoundingClientRect();
      return rect.height < 44 || rect.width < 44;
    });
    const confirmLabels = confirmDialog?.textContent?.replace(/\s+/g, ' ').trim() || '';
    const confirmButtons = confirmDialog?.querySelectorAll('button') || [];
    const expectsConfirm = mode === 'auth-mine' || mode === 'auth-delete-modal';
    const confirmViolation = expectsConfirm && (
      !confirmDialog
      || confirmButtons.length < 2
      || !confirmDialog.contains(document.activeElement)
    );

    root.dataset.qaScenario = status;
    root.dataset.qaOverflow = String(
      documentHorizontalOverflow
      || nestedVerticalOverflow
      || ownerWrapViolations.length > 0
      || scoreFormatViolations.length > 0
      || confirmViolation
    );
    root.dataset.qaFeedTabsVerticalOverflow = String(nestedVerticalOverflow);
    root.dataset.qaOwnerActionWrapViolations = String(ownerWrapViolations.length);
    root.dataset.qaOwnerActions = String(ownerActions.length);
    root.dataset.qaConfirmDialog = String(Boolean(confirmDialog));
    root.dataset.qaConfirmLabels = confirmLabels;
    root.dataset.qaVoteScores = voteScores.join(',');
    root.dataset.qaVoteScoreFormatViolations = String(scoreFormatViolations.length);
    root.dataset.qaAuthDialog = String(Boolean(authDialog));
    root.dataset.qaAuthMode = authDialog?.getAttribute('data-auth-mode') || '';
    root.dataset.qaAuthIntent = authDialog?.getAttribute('data-auth-intent') || '';
    root.dataset.qaAuthSubmit = authSubmit?.textContent?.trim() || '';
    root.dataset.qaCreateDialog = String(Boolean(createDialog));
    root.dataset.qaSubmitBallot = submitBallot?.textContent?.trim() || '';
    root.dataset.qaCreateLabel = createButton?.textContent?.trim() || '';
    root.dataset.qaCreatePresent = String(Boolean(createButton));
    root.dataset.qaCreateDisabled = String(Boolean(createButton?.disabled));
    root.dataset.qaRegisterDisabled = String(Boolean(registerButton?.disabled));
    root.dataset.qaLoginDisabled = String(Boolean(loginButton?.disabled));
    root.dataset.qaVoteOptions = String(voteOptions.length);
    root.dataset.qaVoteOptionsDisabled = String(voteOptions.filter(button => button.disabled).length);
    root.dataset.qaOwnerActionsDisabled = String(ownerActions.filter(button => button.disabled).length);
    root.dataset.qaProfileEditDisabled = String(Boolean(profileEdit?.disabled));
    root.dataset.qaProviderLinksDisabled = String(providerLinks.filter(button => button.disabled).length);
    root.dataset.qaSystemMode = systemNotice?.getAttribute('data-qa-system-mode') || '';
    root.dataset.qaSystemNotice = systemNotice?.textContent?.replace(/\s+/g, ' ').trim() || '';
    root.dataset.qaSystemRetry = String(Boolean(systemRetry));
    root.dataset.qaCards = String(document.querySelectorAll('article').length);
    root.dataset.qaVoterAfterAuth = String(Boolean(document.querySelector('[data-qa-voter-id]')));
    root.dataset.qaFocusInsideDialog = String(Boolean(dialog && dialog.contains(document.activeElement)));
    root.dataset.qaTouchTargets = String(targets.length);
    root.dataset.qaTouchViolations = String(violations.length);
    root.dataset.qaSocialProviders = socialButtons.map(button => button.getAttribute('data-qa-social-provider')).join(',');
    root.dataset.qaSocialLabels = socialButtons.map(button => button.textContent?.trim()).join('|');
    root.dataset.qaNotice = [...document.querySelectorAll('[role="status"]')]
      .map(element => element.textContent?.trim()).filter(Boolean).join('|');
    root.dataset.qaVoterMenu = document.querySelector('[data-qa-voter-id]')?.textContent?.replace(/\s+/g, ' ').trim() || '';
    root.dataset.qaReducedMotion = String(matchMedia('(prefers-reduced-motion: reduce)').matches);
    root.dataset.qaReducedScroll = getComputedStyle(document.documentElement).scrollBehavior;
  };
  const fillRegistration = () => {
    const dialog = document.querySelector('[data-qa-auth-dialog]');
    const name = dialog?.querySelector('input[autocomplete="name"]');
    const email = dialog?.querySelector('input[type="email"]');
    const passwords = dialog?.querySelectorAll('input[type="password"]') || [];
    if (name) setInput(name, 'QA Registered Voter');
    if (email) setInput(email, `qa-${Date.now()}@example.com`);
    if (passwords[0]) setInput(passwords[0], 'runtime-password');
    if (passwords[1]) setInput(passwords[1], 'runtime-password');
  };
  const submitRegistration = () => {
    fillRegistration();
    setTimeout(() => document.querySelector('[data-qa-auth-dialog] form')?.requestSubmit(), 80);
  };
  const guestAction = index => document.querySelectorAll('[data-qa-guest-actions] button')[index];
  const authTab = index => document.querySelectorAll('[data-qa-auth-tab]')[index];
  const start = () => {
    if (mode === 'read-only') {
      return waitFor(
        () => document.querySelector('[data-qa-system-mode="READ_ONLY"]')
          && document.querySelectorAll('[data-qa-vote-option]').length === 6,
        () => setTimeout(() => record(), 120)
      );
    }
    if (mode === 'auth-read-only') {
      return waitFor(
        () => document.querySelector('[data-qa-system-mode="READ_ONLY"]')
          && document.querySelectorAll('[data-qa-owner-action]').length === 3
          && document.querySelectorAll('[data-qa-provider-link]').length === 2,
        () => setTimeout(() => record(), 120)
      );
    }
    if (mode === 'maintenance') return setTimeout(() => record(), 120);
    if (mode === 'maintenance-retry') {
      document.querySelector('[data-qa-system-retry]')?.click();
      return waitFor(
        () => !document.querySelector('[data-qa-system-mode]')
          && Boolean(document.querySelector('[data-qa-create-ballot]')),
        () => setTimeout(() => record('restored'), 180)
      );
    }
    if (mode === 'reduced-motion') return setTimeout(() => record(), 250);

    if (mode === 'auth-owner-actions' || mode === 'auth-negative-score') {
      return waitFor(
        () => document.querySelectorAll('[data-qa-owner-action]').length === 3,
        () => setTimeout(() => record(), 120)
      );
    }
    if (mode === 'auth-delete-modal') {
      return waitFor(
        () => Boolean(document.querySelector('[data-qa-owner-action="delete"]')),
        () => {
          document.querySelector('[data-qa-owner-action="delete"]')?.click();
          waitFor(
            () => Boolean(document.querySelector('[data-qa-confirm-dialog]')),
            () => setTimeout(() => record(), 120)
          );
        }
      );
    }
    if (mode === 'auth-mine') {
      return waitFor(
        () => Boolean(document.querySelector('[data-qa-active-feed-tab="true"]'))
          && Boolean(document.querySelector('[data-qa-owner-action="delete"]')),
        () => {
          document.querySelector('[data-qa-owner-action="delete"]')?.click();
          waitFor(
            () => Boolean(document.querySelector('[data-qa-confirm-dialog]')),
            () => setTimeout(() => record(), 120)
          );
        }
      );
    }
    if (mode === 'auth-social-create') {
      return waitFor(
        () => Boolean(document.querySelector('[data-qa-create-dialog]')) && Boolean(document.querySelector('[data-qa-voter-id]')),
        () => setTimeout(() => record(), 120)
      );
    }
    if (mode === 'auth-social-error' || mode === 'auth-social-linked') {
      return waitFor(
        () => Boolean(document.querySelector('[data-qa-voter-id]'))
          && [...document.querySelectorAll('[role="status"]')].some(element => Boolean(element.textContent?.trim())),
        () => setTimeout(() => record(), 120)
      );
    }

    if (mode === 'guest-register-dialog' || mode === 'guest-register-complete') {
      guestAction(1)?.click();
      return waitFor(
        () => document.querySelector('[data-qa-auth-dialog]')?.getAttribute('data-auth-mode') === 'register',
        () => {
          if (mode === 'guest-register-dialog') return setTimeout(() => record(), 100);
          submitRegistration();
          waitFor(
            () => Boolean(document.querySelector('[data-qa-voter-id]')) && !document.querySelector('[data-qa-auth-dialog]'),
            () => setTimeout(() => record(), 120)
          );
        }
      );
    }

    if (mode === 'social-buttons') {
      guestAction(0)?.click();
      return waitFor(
        () => document.querySelectorAll('[data-qa-social-provider]').length === 2,
        () => setTimeout(() => record(), 100)
      );
    }

    if (mode === 'guest-create-auth' || mode === 'guest-create-resume') {
      document.querySelector('[data-qa-create-ballot]')?.click();
      return waitFor(
        () => Boolean(document.querySelector('[data-qa-auth-dialog]')),
        () => {
          if (mode === 'guest-create-auth') return setTimeout(() => record(), 100);
          authTab(1)?.click();
          waitFor(
            () => document.querySelector('[data-qa-auth-dialog]')?.getAttribute('data-auth-mode') === 'register',
            () => {
              submitRegistration();
              waitFor(
                () => Boolean(document.querySelector('[data-qa-create-dialog]')),
                () => setTimeout(() => record(), 120)
              );
            }
          );
        }
      );
    }

    setTimeout(() => record(), 1300);
  };

  const maintenanceScenario = mode === 'maintenance' || mode === 'maintenance-retry';
  waitFor(
    () => maintenanceScenario
      ? Boolean(document.querySelector('[data-qa-system-mode="MAINTENANCE"]'))
      : Boolean(document.querySelector('[data-qa-create-ballot]')),
    () => setTimeout(start, 500)
  );
})();
</script>
"""

base.QA_SCRIPT = base.QA_SCRIPT + EXTRA_QA_SCRIPT


class Handler(base.Handler):
    _status_counts: dict[str, int] = {}
    _status_lock = threading.Lock()

    def _profile(self) -> dict:
        linked = ["GOOGLE"] if self._fixture_mode() == "auth-social-linked" else []
        return {
            **base.PROFILE,
            "bio": "",
            "avatarIcon": "CITIZEN",
            "avatarColor": "NAVY",
            "preferredLocale": "en",
            "linkedProviders": linked,
        }

    def _bootstrap(self) -> dict:
        return {**base.SESSION, "profile": self._profile()}

    def do_POST(self) -> None:  # noqa: N802
        path = urlparse(self.path).path
        if path in ("/api/v1/auth/login", "/api/v1/auth/register"):
            content_length = int(self.headers.get("Content-Length") or 0)
            if content_length:
                self.rfile.read(content_length)
            self._json(200 if path.endswith("login") else 201, self._bootstrap())
            return
        if path == "/api/v1/auth/refresh" and self._authenticated_fixture():
            self._json(200, self._bootstrap())
            return
        super().do_POST()

    def do_GET(self) -> None:  # noqa: N802
        parsed = urlparse(self.path)
        mode = self._fixture_mode()
        if parsed.path == "/api/v1/system/status":
            with self._status_lock:
                count = self._status_counts.get(mode, 0) + 1
                self._status_counts[mode] = count

            system_mode = "NORMAL"
            if mode in ("read-only", "auth-read-only"):
                system_mode = "READ_ONLY"
            elif mode == "maintenance" or (mode == "maintenance-retry" and count == 1):
                system_mode = "MAINTENANCE"

            self._json(200, {
                "mode": system_mode,
                "messageVi": "Thông báo vận hành dài do quản trị viên cung cấp để xác minh khả năng xuống dòng trên màn hình nhỏ.\nDữ liệu này phải được giữ nguyên, không bị cắt hoặc gây tràn ngang.",
                "messageEn": "A long administrator-authored operating notice verifies wrapping on compact screens.\nThis content must remain unchanged, readable, and free from horizontal overflow.",
                "estimatedEndAt": "2027-08-01T08:30:00Z" if system_mode != "NORMAL" else None,
                "updatedAt": "2026-08-01T07:00:00Z",
            })
            return
        if parsed.path == "/api/v1/auth/social/providers":
            self._json(200, {"providers": ["google", "github"]})
            return
        if parsed.path == "/api/v1/users/me":
            if mode == "auth-bootstrap":
                self._json(500, {"title": "Unexpected legacy profile request"})
                return
            if self.headers.get("Authorization") == "Bearer visual-qa-access-token":
                self._json(200, self._profile())
            else:
                self._json(401, {"title": "Unauthorized"})
            return
        if parsed.path == "/api/v1/posts" and mode in ("auth", "auth-negative-score"):
            params = parse_qs(parsed.query)
            page = int(params.get("page", ["0"])[0])
            size = int(params.get("size", ["8"])[0])
            negative = {**base.BALLOTS[0], "voteScore": -1}
            self._json(200, base.page_payload([negative, *base.BALLOTS[1:]], page, size))
            return
        super().do_GET()


if __name__ == "__main__":
    if not (base.OUT / "index.html").exists():
        raise SystemExit("frontend/out/index.html is missing; run npm run build first")
    mimetypes.add_type("application/javascript", ".js")
    server = ThreadingHTTPServer(("127.0.0.1", base.PORT), Handler)
    print(f"TON-107 QA server listening on http://127.0.0.1:{base.PORT}", flush=True)
    server.serve_forever()

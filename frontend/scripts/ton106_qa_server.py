#!/usr/bin/env python3
"""Extend the deterministic visual QA server with auth-intent scenarios."""

from __future__ import annotations

import mimetypes
from http.server import ThreadingHTTPServer
from urllib.parse import urlparse

import visual_qa_server as base

EXTRA_QA_SCRIPT = r"""
<script>
(() => {
  const mode = new URLSearchParams(location.search).get('qa') || 'feed';
  const supported = new Set([
    'guest-register-dialog',
    'guest-create-auth',
    'guest-register-complete',
    'guest-create-resume',
    'reduced-motion'
  ]);
  if (!supported.has(mode)) return;

  const root = document.documentElement;
  const byText = (selector, text) => [...document.querySelectorAll(selector)]
    .find(element => element.textContent?.trim() === text);
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
    '[data-qa-create-ballot], [data-qa-auth-submit], [data-qa-submit-ballot], [data-qa-auth-tab]'
  )].filter(element => {
    const rect = element.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0;
  });
  const record = (status = 'complete') => {
    const dialog = document.querySelector('[role="dialog"]');
    const authDialog = document.querySelector('[data-qa-auth-dialog]');
    const createDialog = document.querySelector('[data-qa-create-dialog]');
    const authSubmit = document.querySelector('[data-qa-auth-submit]');
    const submitBallot = document.querySelector('[data-qa-submit-ballot]');
    const createButton = document.querySelector('[data-qa-create-ballot]');
    const targets = keyTouchTargets();
    const violations = targets.filter(element => {
      const rect = element.getBoundingClientRect();
      return rect.height < 44 || rect.width < 44;
    });

    root.dataset.qaScenario = status;
    root.dataset.qaAuthDialog = String(Boolean(authDialog));
    root.dataset.qaAuthMode = authDialog?.getAttribute('data-auth-mode') || '';
    root.dataset.qaAuthSubmit = authSubmit?.textContent?.trim() || '';
    root.dataset.qaCreateDialog = String(Boolean(createDialog));
    root.dataset.qaSubmitBallot = submitBallot?.textContent?.trim() || '';
    root.dataset.qaCreateLabel = createButton?.textContent?.trim() || '';
    root.dataset.qaVoterAfterAuth = String(Boolean(document.querySelector('[data-qa-voter-id]')));
    root.dataset.qaFocusInsideDialog = String(Boolean(dialog && dialog.contains(document.activeElement)));
    root.dataset.qaTouchTargets = String(targets.length);
    root.dataset.qaTouchViolations = String(violations.length);
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
  const start = () => {
    if (mode === 'reduced-motion') return setTimeout(() => record(), 250);

    if (mode === 'guest-register-dialog' || mode === 'guest-register-complete') {
      byText('button', 'REGISTER')?.click();
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

    document.querySelector('[data-qa-create-ballot]')?.click();
    waitFor(
      () => Boolean(document.querySelector('[data-qa-auth-dialog]')),
      () => {
        if (mode === 'guest-create-auth') return setTimeout(() => record(), 100);
        byText('[role="tab"]', 'REGISTER')?.click();
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
  };

  waitFor(
    () => Boolean(document.querySelector('[data-qa-create-ballot]')),
    () => setTimeout(start, 500)
  );
})();
</script>
"""

base.QA_SCRIPT = base.QA_SCRIPT + EXTRA_QA_SCRIPT


class Handler(base.Handler):
    def do_POST(self) -> None:  # noqa: N802
        path = urlparse(self.path).path
        if path in ("/api/v1/auth/login", "/api/v1/auth/register"):
            content_length = int(self.headers.get("Content-Length") or 0)
            if content_length:
                self.rfile.read(content_length)
            self._json(200 if path.endswith("login") else 201, base.SESSION)
            return
        super().do_POST()

    def do_GET(self) -> None:  # noqa: N802
        parsed = urlparse(self.path)
        if parsed.path == "/api/v1/users/me":
            if self.headers.get("Authorization") == "Bearer visual-qa-access-token":
                self._json(200, base.PROFILE)
            else:
                self._json(401, {"title": "Unauthorized"})
            return
        super().do_GET()


if __name__ == "__main__":
    if not (base.OUT / "index.html").exists():
        raise SystemExit("frontend/out/index.html is missing; run npm run build first")
    mimetypes.add_type("application/javascript", ".js")
    server = ThreadingHTTPServer(("127.0.0.1", base.PORT), Handler)
    print(f"TON-106 QA server listening on http://127.0.0.1:{base.PORT}", flush=True)
    server.serve_forever()

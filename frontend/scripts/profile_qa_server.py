#!/usr/bin/env python3
"""Extend the existing visual QA host with profile-dialog checks."""

from __future__ import annotations

import mimetypes
from http.server import ThreadingHTTPServer

import ton106_qa_server as base

PROFILE_QA_SCRIPT = r"""
<script>
(() => {
  const mode = new URLSearchParams(location.search).get('qa') || '';
  if (!new Set(['auth-profile-editor', 'auth-public-profile']).has(mode)) return;

  const root = document.documentElement;
  const waitFor = (predicate, callback, attempt = 0) => {
    if (predicate()) return callback();
    if (attempt >= 80) {
      root.dataset.qaScenario = 'timeout';
      root.dataset.qaOverflow = 'true';
      return;
    }
    setTimeout(() => waitFor(predicate, callback, attempt + 1), 80);
  };
  const visible = element => {
    const rect = element.getBoundingClientRect();
    return rect.width > 0 && rect.height > 0;
  };
  const setCommonState = dialog => {
    dialog?.scrollTo({ top: 0, behavior: 'auto' });
    dialog?.querySelector('button')?.focus({ preventScroll: true });
    const horizontalOverflow = root.scrollWidth > root.clientWidth
      || Boolean(dialog && dialog.scrollWidth > dialog.clientWidth + 1);
    root.dataset.qaScenario = 'complete';
    root.dataset.qaFocusInsideDialog = String(Boolean(dialog && dialog.contains(document.activeElement)));
    root.dataset.qaOverflow = String(horizontalOverflow);
  };
  const recordEditor = () => {
    const dialog = document.querySelector('[data-qa-profile-dialog]');
    const marks = [...(dialog?.querySelectorAll('[data-qa-ballot-mark]') || [])];
    const distinctMarks = new Set(marks.map(mark => mark.getAttribute('data-qa-ballot-mark')).filter(Boolean));
    const maskViolations = marks.filter(mark => {
      const glyph = mark.querySelector('[data-qa-ballot-mark-glyph]');
      if (!glyph) return true;
      const style = getComputedStyle(glyph);
      const mask = style.webkitMaskImage || style.maskImage;
      return !mask || mask === 'none';
    });
    const sizeViolations = marks.filter(mark => {
      const rect = mark.getBoundingClientRect();
      return rect.width < 44 || rect.height < 44;
    });
    const touchTargets = [...(dialog?.querySelectorAll('button') || [])].filter(visible);
    const touchViolations = touchTargets.filter(button => {
      const rect = button.getBoundingClientRect();
      return rect.width < 44 || rect.height < 44;
    });

    setCommonState(dialog);
    root.dataset.qaProfileDialog = String(Boolean(dialog));
    root.dataset.qaBallotMarks = String(marks.length);
    root.dataset.qaDistinctBallotMarks = String(distinctMarks.size);
    root.dataset.qaBallotMarkMaskViolations = String(maskViolations.length);
    root.dataset.qaBallotMarkSizeViolations = String(sizeViolations.length);
    root.dataset.qaTouchTargets = String(touchTargets.length);
    root.dataset.qaTouchViolations = String(touchViolations.length);
    if (maskViolations.length || sizeViolations.length || touchViolations.length) root.dataset.qaOverflow = 'true';
  };
  const recordPublicProfile = () => {
    const dialog = document.querySelector('[data-qa-public-profile-dialog]');
    const fallbackCopy = 'Add a short introduction to your public profile.';
    setCommonState(dialog);
    root.dataset.qaPublicProfileDialog = String(Boolean(dialog));
    root.dataset.qaPublicProfileBio = String(Boolean(dialog?.querySelector('[data-qa-public-profile-bio]')));
    root.dataset.qaPublicProfileDefaultCopy = String(Boolean(dialog?.textContent?.includes(fallbackCopy)));
  };

  waitFor(
    () => Boolean(document.querySelector('[data-qa-voter-id]')),
    () => {
      const voter = document.querySelector('[data-qa-voter-id]');
      voter?.querySelector('summary')?.click();
      const actionLabel = mode === 'auth-profile-editor' ? 'EDIT PROFILE' : 'VIEW PROFILE';
      waitFor(
        () => [...(voter?.querySelectorAll('button') || [])].some(button => button.textContent?.includes(actionLabel)),
        () => {
          [...(voter?.querySelectorAll('button') || [])]
            .find(button => button.textContent?.includes(actionLabel))
            ?.click();
          if (mode === 'auth-profile-editor') {
            waitFor(
              () => document.querySelectorAll('[data-qa-profile-dialog] [data-qa-ballot-mark]').length >= 11,
              () => setTimeout(recordEditor, 220)
            );
            return;
          }
          waitFor(
            () => Boolean(document.querySelector('[data-qa-public-profile-dialog]')),
            () => setTimeout(recordPublicProfile, 220)
          );
        }
      );
    }
  );
})();
</script>
"""

base.base.QA_SCRIPT = base.base.QA_SCRIPT + PROFILE_QA_SCRIPT


if __name__ == "__main__":
    if not (base.base.OUT / "index.html").exists():
        raise SystemExit("frontend/out/index.html is missing; run npm run build first")
    mimetypes.add_type("application/javascript", ".js")
    server = ThreadingHTTPServer(("127.0.0.1", base.base.PORT), base.Handler)
    print(f"Profile QA server listening on http://127.0.0.1:{base.base.PORT}", flush=True)
    server.serve_forever()

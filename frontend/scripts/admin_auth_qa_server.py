#!/usr/bin/env python3
"""Exercise the admin workspace against rotating-refresh and system-mode boundaries."""

from __future__ import annotations

import json
import mimetypes
from http.server import ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

import admin_qa_server as admin

admin.ADMIN_QA_SCRIPT = admin.ADMIN_QA_SCRIPT.replace(
    "No authoritative ranking contract yet",
    "RANKING STATUS",
)
admin.ADMIN_QA_SCRIPT = admin.ADMIN_QA_SCRIPT.replace(
    "    if (mode === 'admin-user-dialog') {",
    """    if (mode === 'admin-system') {
      buttonWithText('System')?.click();
      return waitFor(
        () => document.body.textContent?.includes('Current operating mode'),
        () => setTimeout(() => record(), 160)
      );
    }
    if (mode === 'admin-system-dialog') {
      buttonWithText('System')?.click();
      return waitFor(
        () => Boolean(document.querySelector('input[name="system-mode"][value="MAINTENANCE"]')),
        () => {
          const maintenance = document.querySelector('input[name="system-mode"][value="MAINTENANCE"]');
          maintenance?.click();
          waitFor(
            () => Boolean(buttonWithText('Review mode change')),
            () => {
              buttonWithText('Review mode change')?.click();
              waitFor(() => Boolean(document.querySelector('dialog[open]')), () => setTimeout(() => record(), 140));
            }
          );
        }
      );
    }
    if (mode === 'admin-system-update') {
      buttonWithText('System')?.click();
      return waitFor(
        () => Boolean(document.querySelector('input[name="system-mode"][value="READ_ONLY"]')),
        () => {
          document.querySelector('input[name="system-mode"][value="READ_ONLY"]')?.click();
          waitFor(
            () => Boolean(buttonWithText('Review mode change')),
            () => {
              buttonWithText('Review mode change')?.click();
              waitFor(
                () => Boolean(document.querySelector('dialog[open] textarea[required]')),
                () => {
                  const reason = document.querySelector('dialog[open] textarea[required]');
                  const setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')?.set;
                  setter?.call(reason, 'Controlled read-only verification');
                  reason?.dispatchEvent(new Event('input', { bubbles: true }));
                  waitFor(
                    () => Boolean(buttonWithText('Enable read-only')),
                    () => {
                      buttonWithText('Enable read-only')?.click();
                      waitFor(
                        () => document.body.textContent?.includes('authoritative operating mode was updated'),
                        () => setTimeout(() => record(), 180)
                      );
                    }
                  );
                }
              );
            }
          );
        }
      );
    }
    if (mode === 'admin-user-dialog') {""",
)

_refresh_count = 0
_system_status = {
    "mode": "NORMAL",
    "messageVi": None,
    "messageEn": None,
    "estimatedEndAt": None,
    "updatedAt": "2026-07-31T02:00:00Z",
    "updatedBy": admin.ADMIN_ID,
}


def reset_system_status() -> None:
    global _system_status
    _system_status = {
        "mode": "NORMAL",
        "messageVi": None,
        "messageEn": None,
        "estimatedEndAt": None,
        "updatedAt": "2026-07-31T02:00:00Z",
        "updatedBy": admin.ADMIN_ID,
    }


class Handler(admin.Handler):
    def do_POST(self) -> None:  # noqa: N802
        global _refresh_count
        parsed = urlparse(self.path)
        if parsed.path == "/api/v1/auth/refresh" and self._fixture_mode() == "admin-ranking":
            _refresh_count += 1
            print(f"admin-ranking refresh #{_refresh_count}", flush=True)
            if _refresh_count > 1:
                self._json(401, {"title": "Unauthorized", "detail": "Refresh token already rotated"})
                return
        super().do_POST()

    def do_PUT(self) -> None:  # noqa: N802
        global _system_status
        parsed = urlparse(self.path)
        if parsed.path != "/api/v1/admin/system/status":
            self._json(404, {"title": "Not found"})
            return
        if not self._authorized_admin():
            self._json(403, {"title": "Forbidden"})
            return
        content_length = int(self.headers.get("Content-Length") or 0)
        body = json.loads(self.rfile.read(content_length) or b"{}") if content_length else {}
        if not str(body.get("reason") or "").strip():
            self._json(400, {"title": "Request validation failed", "detail": "Reason is required"})
            return
        mode = body.get("mode")
        if mode not in {"NORMAL", "READ_ONLY", "MAINTENANCE"}:
            self._json(400, {"title": "Request validation failed", "detail": "Invalid mode"})
            return
        _system_status = {
            "mode": mode,
            "messageVi": None if mode == "NORMAL" else body.get("messageVi"),
            "messageEn": None if mode == "NORMAL" else body.get("messageEn"),
            "estimatedEndAt": None if mode == "NORMAL" else body.get("estimatedEndAt"),
            "updatedAt": "2026-07-31T03:00:00Z",
            "updatedBy": admin.ADMIN_ID,
        }
        print(f"admin-system updated to {mode}", flush=True)
        self._json(200, _system_status)

    def do_GET(self) -> None:  # noqa: N802
        global _refresh_count
        parsed = urlparse(self.path)
        mode = parse_qs(parsed.query).get("qa", [""])[0]
        if parsed.path in ("/admin", "/admin/"):
            if mode == "admin-ranking":
                _refresh_count = 0
            if mode in {"admin-system", "admin-system-dialog", "admin-system-update"}:
                reset_system_status()

        if parsed.path == "/api/v1/admin/rankings/status":
            if not self._authorized_admin():
                self._json(403, {"title": "Forbidden"})
                return
            self._json(200, {
                "availability": "HEALTHY",
                "visibleBallots": 2,
                "eligibleDayBallots": 2,
                "eligibleWeekBallots": 2,
                "hotMembers": 2,
                "topDayMembers": 2,
                "topWeekMembers": 2,
                "generation": "qa-shared-session-generation",
                "lastSuccessfulRebuildAt": "2026-07-31T01:00:00Z",
                "rebuildInProgress": False,
            })
            return

        if parsed.path == "/api/v1/admin/system/status":
            if not self._authorized_admin():
                self._json(403, {"title": "Forbidden"})
                return
            print(f"admin-system authoritative read {_system_status['mode']}", flush=True)
            self._json(200, _system_status)
            return

        super().do_GET()


if __name__ == "__main__":
    if not (admin.base.base.OUT / "admin" / "index.html").exists():
        raise SystemExit("frontend/out/admin/index.html is missing; run npm run build first")
    mimetypes.add_type("application/javascript", ".js")
    server = ThreadingHTTPServer(("127.0.0.1", admin.base.base.PORT), Handler)
    print(f"admin auth QA server listening on http://127.0.0.1:{admin.base.base.PORT}", flush=True)
    server.serve_forever()

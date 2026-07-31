#!/usr/bin/env python3
"""Exercise the admin workspace against a single-use refresh boundary."""

from __future__ import annotations

import mimetypes
from http.server import ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

import admin_qa_server as admin

_refresh_count = 0


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

    def do_GET(self) -> None:  # noqa: N802
        global _refresh_count
        parsed = urlparse(self.path)
        mode = parse_qs(parsed.query).get("qa", [""])[0]
        if parsed.path in ("/admin", "/admin/") and mode == "admin-ranking":
            _refresh_count = 0

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

        super().do_GET()


if __name__ == "__main__":
    if not (admin.base.base.OUT / "admin" / "index.html").exists():
        raise SystemExit("frontend/out/admin/index.html is missing; run npm run build first")
    mimetypes.add_type("application/javascript", ".js")
    server = ThreadingHTTPServer(("127.0.0.1", admin.base.base.PORT), Handler)
    print(f"admin auth QA server listening on http://127.0.0.1:{admin.base.base.PORT}", flush=True)
    server.serve_forever()

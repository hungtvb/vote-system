#!/usr/bin/env python3
"""Verify a committed system-mode update survives a failed reconciliation read."""

from __future__ import annotations

import mimetypes
from http.server import ThreadingHTTPServer
from urllib.parse import urlparse

import admin_auth_qa_server as base

base.admin.ADMIN_QA_SCRIPT = base.admin.ADMIN_QA_SCRIPT.replace(
    "() => document.body.textContent?.includes('authoritative operating mode was updated'),",
    "() => document.body.textContent?.includes('Authoritative reconciliation read unavailable'),",
)

_fail_next_status_read = False


class Handler(base.Handler):
    def do_PUT(self) -> None:  # noqa: N802
        global _fail_next_status_read
        parsed = urlparse(self.path)
        if parsed.path == "/api/v1/admin/system/status":
            super().do_PUT()
            _fail_next_status_read = True
            return
        super().do_PUT()

    def do_GET(self) -> None:  # noqa: N802
        global _fail_next_status_read
        parsed = urlparse(self.path)
        if parsed.path in ("/admin", "/admin/"):
            _fail_next_status_read = False
        if parsed.path == "/api/v1/admin/system/status" and _fail_next_status_read:
            _fail_next_status_read = False
            print("admin-system reconciliation read failed after commit", flush=True)
            self._json(503, {
                "title": "Service unavailable",
                "detail": "Authoritative reconciliation read unavailable",
            })
            return
        super().do_GET()


if __name__ == "__main__":
    if not (base.admin.base.base.OUT / "admin" / "index.html").exists():
        raise SystemExit("frontend/out/admin/index.html is missing; run npm run build first")
    mimetypes.add_type("application/javascript", ".js")
    server = ThreadingHTTPServer(("127.0.0.1", base.admin.base.base.PORT), Handler)
    print(
        f"admin system partial-success QA server listening on http://127.0.0.1:{base.admin.base.base.PORT}",
        flush=True,
    )
    server.serve_forever()

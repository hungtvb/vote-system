#!/usr/bin/env python3
"""Serve the exported admin workspace with deterministic protected API fixtures."""

from __future__ import annotations

import json
import mimetypes
from http.server import ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

import ton106_qa_server as base

ADMIN_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
TARGET_USER_ID = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
AUTHOR_ID = "cccccccc-cccc-cccc-cccc-cccccccccccc"

USERS = [
    {
        "id": ADMIN_ID,
        "email": "admin.registry@example.com",
        "displayName": "Alexandria Registry Administrator",
        "initials": "AR",
        "role": "ADMIN",
        "accountStatus": "ACTIVE",
        "statusUntil": None,
        "statusUpdatedAt": None,
        "linkedProviders": ["GOOGLE"],
        "createdAt": "2026-07-20T09:00:00Z",
        "updatedAt": "2026-07-30T10:00:00Z",
    },
    {
        "id": TARGET_USER_ID,
        "email": "long.operational.identity.for.moderation@example.com",
        "displayName": "Community Participant With A Long Moderation Display Name",
        "initials": "CP",
        "role": "USER",
        "accountStatus": "ACTIVE",
        "statusUntil": None,
        "statusUpdatedAt": None,
        "linkedProviders": ["GITHUB"],
        "createdAt": "2026-07-21T09:00:00Z",
        "updatedAt": "2026-07-29T10:00:00Z",
    },
    {
        "id": "dddddddd-dddd-dddd-dddd-dddddddddddd",
        "email": "suspended.voter@example.com",
        "displayName": "Suspended Registry Participant",
        "initials": "SR",
        "role": "USER",
        "accountStatus": "SUSPENDED",
        "statusUntil": "2026-08-15T12:00:00Z",
        "statusUpdatedAt": "2026-07-30T08:00:00Z",
        "linkedProviders": [],
        "createdAt": "2026-07-22T09:00:00Z",
        "updatedAt": "2026-07-30T08:00:00Z",
    },
]

POSTS = [
    {
        "id": "11111111-1111-1111-1111-111111111111",
        "authorId": AUTHOR_ID,
        "author": {"id": AUTHOR_ID, "displayName": "Morgan Lee", "initials": "ML"},
        "ballotNumber": "BALLOT-2026-0001",
        "title": "Should public software projects publish a plain-language decision record for every major architectural change?",
        "content": "A long public record used to verify administrator moderation layout and safe wrapping.",
        "category": "TECHNOLOGY",
        "status": "OPEN",
        "moderationStatus": "VISIBLE",
        "moderationUpdatedAt": None,
        "closesAt": "2026-08-01T12:00:00Z",
        "voteScore": 120,
        "upVotes": 150,
        "downVotes": 30,
        "totalVotes": 180,
        "verdictThreshold": 70,
        "verdict": "UP",
        "finalVerdict": False,
        "createdAt": "2026-07-25T02:00:00Z",
        "updatedAt": "2026-07-30T05:00:00Z",
    },
    {
        "id": "22222222-2222-2222-2222-222222222222",
        "authorId": AUTHOR_ID,
        "author": {"id": AUTHOR_ID, "displayName": "Morgan Lee", "initials": "ML"},
        "ballotNumber": "BALLOT-2026-0002",
        "title": "Hidden ballot retained for administrator review",
        "content": "This hidden record remains unavailable to public paths.",
        "category": "COMMUNITY",
        "status": "CLOSED",
        "moderationStatus": "HIDDEN",
        "moderationUpdatedAt": "2026-07-30T07:00:00Z",
        "closesAt": None,
        "closedAt": "2026-07-29T12:00:00Z",
        "voteScore": -4,
        "upVotes": 8,
        "downVotes": 12,
        "totalVotes": 20,
        "verdictThreshold": 70,
        "verdict": "DOWN",
        "finalVerdict": True,
        "createdAt": "2026-07-24T02:00:00Z",
        "updatedAt": "2026-07-30T07:00:00Z",
    },
]

AUDIT = [
    {
        "id": "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
        "actorId": ADMIN_ID,
        "action": "ADMIN_HIDE_POST",
        "targetType": "POST",
        "targetId": "22222222-2222-2222-2222-222222222222",
        "reason": "Confirmed policy violation after manual review of the complete ballot record.",
        "metadata": {"previous_status": "VISIBLE", "new_status": "HIDDEN", "request_id": "qa-admin-request-001"},
        "createdAt": "2026-07-30T07:00:00Z",
    },
    {
        "id": "ffffffff-ffff-ffff-ffff-ffffffffffff",
        "actorId": ADMIN_ID,
        "action": "ADMIN_SUSPEND_USER",
        "targetType": "USER",
        "targetId": TARGET_USER_ID,
        "reason": "Repeated abuse after warning.",
        "metadata": {"previous_status": "ACTIVE", "new_status": "SUSPENDED", "restriction_until": "2026-08-15T12:00:00Z"},
        "createdAt": "2026-07-30T06:00:00Z",
    },
]

ADMIN_QA_SCRIPT = r"""
<script>
(() => {
  const mode = new URLSearchParams(location.search).get('qa') || '';
  if (!mode.startsWith('admin-')) return;
  const root = document.documentElement;
  const waitFor = (predicate, callback, attempt = 0) => {
    if (predicate()) return callback();
    if (attempt >= 100) return record('timeout');
    setTimeout(() => waitFor(predicate, callback, attempt + 1), 80);
  };
  const buttonWithText = text => [...document.querySelectorAll('button')]
    .find(button => !button.disabled && button.textContent?.trim().toUpperCase().includes(text.toUpperCase()));
  const record = (status = 'complete') => {
    const dialog = document.querySelector('dialog[open]');
    root.dataset.qaScenario = status;
    root.dataset.qaAdminOverflow = String(root.scrollWidth > root.clientWidth);
    root.dataset.qaAdminHeading = document.querySelector('main h1')?.textContent?.trim() || '';
    root.dataset.qaAdminArticles = String(document.querySelectorAll('main article').length);
    root.dataset.qaAdminDialog = String(Boolean(dialog));
    root.dataset.qaAdminDialogFocus = String(Boolean(dialog && dialog.contains(document.activeElement)));
    root.dataset.qaAdminReason = String(Boolean(dialog?.querySelector('textarea[required][maxlength="500"]')));
    root.dataset.qaAdminDenied = String(document.body.textContent?.includes('Administrator access denied'));
    root.dataset.qaAdminRankingUnknown = String(document.body.textContent?.includes('No authoritative ranking contract yet'));
  };
  const openSection = label => {
    buttonWithText(label)?.click();
    waitFor(() => document.querySelectorAll('main article').length > 0, () => setTimeout(() => record(), 180));
  };
  const start = () => {
    if (mode === 'admin-denied') return waitFor(
      () => document.body.textContent?.includes('Administrator access denied'),
      () => setTimeout(() => record(), 100)
    );
    if (mode === 'admin-users') return openSection('Users');
    if (mode === 'admin-ballots') return openSection('Ballots');
    if (mode === 'admin-audit') return openSection('Audit log');
    if (mode === 'admin-ranking') {
      buttonWithText('Ranking')?.click();
      return waitFor(
        () => document.body.textContent?.includes('No authoritative ranking contract yet'),
        () => setTimeout(() => record(), 100)
      );
    }
    if (mode === 'admin-user-dialog') {
      buttonWithText('Users')?.click();
      return waitFor(
        () => Boolean(buttonWithText('Suspend')),
        () => {
          buttonWithText('Suspend')?.click();
          waitFor(() => Boolean(document.querySelector('dialog[open]')), () => setTimeout(() => record(), 120));
        }
      );
    }
    waitFor(
      () => document.body.textContent?.includes('Registered users'),
      () => setTimeout(() => record(), 160)
    );
  };
  setTimeout(start, 250);
})();
</script>
"""


def admin_page(content: list[dict], page: int = 0, size: int = 12, total: int | None = None) -> dict:
    total_elements = len(content) if total is None else total
    total_pages = 0 if total_elements == 0 else max(1, (total_elements + size - 1) // size)
    return {
        "content": content,
        "page": page,
        "size": size,
        "totalElements": total_elements,
        "totalPages": total_pages,
        "first": page == 0,
        "last": total_pages == 0 or page + 1 >= total_pages,
    }


class Handler(base.Handler):
    def _profile(self) -> dict:
        profile = super()._profile()
        role = "USER" if self._fixture_mode() == "admin-denied" else "ADMIN"
        return {
            **profile,
            "id": ADMIN_ID,
            "email": "admin.registry@example.com",
            "displayName": "Alexandria Registry Administrator",
            "initials": "AR",
            "role": role,
        }

    def _authorized_admin(self) -> bool:
        return (
            self._authenticated_fixture()
            and self._profile()["role"] == "ADMIN"
            and self.headers.get("Authorization") == "Bearer visual-qa-access-token"
        )

    def do_POST(self) -> None:  # noqa: N802
        parsed = urlparse(self.path)
        if parsed.path.startswith("/api/v1/admin/"):
            content_length = int(self.headers.get("Content-Length") or 0)
            body = json.loads(self.rfile.read(content_length) or b"{}") if content_length else {}
            if not self._authorized_admin():
                self._json(403, {"title": "Forbidden"})
                return
            if not str(body.get("reason") or "").strip():
                self._json(400, {"title": "Request validation failed"})
                return
            if parsed.path.endswith("/hide"):
                self._json(200, {"id": POSTS[0]["id"], "moderationStatus": "HIDDEN", "moderationUpdatedAt": "2026-07-30T13:00:00Z"})
                return
            if parsed.path.endswith("/restore") and "/posts/" in parsed.path:
                self._json(200, {"id": POSTS[1]["id"], "moderationStatus": "VISIBLE", "moderationUpdatedAt": "2026-07-30T13:00:00Z"})
                return
            if parsed.path.endswith("/delete"):
                self._json(200, {"id": POSTS[0]["id"], "moderationStatus": "DELETED", "moderationUpdatedAt": "2026-07-30T13:00:00Z"})
                return
            self._json(200, {"id": TARGET_USER_ID, "accountStatus": "SUSPENDED", "statusUpdatedAt": "2026-07-30T13:00:00Z", "revokedSessions": 1})
            return
        super().do_POST()

    def do_GET(self) -> None:  # noqa: N802
        parsed = urlparse(self.path)
        mode = parse_qs(parsed.query).get("qa", [""])[0]
        if parsed.path in ("/admin", "/admin/"):
            index = base.base.OUT / "admin" / "index.html"
            html = index.read_text(encoding="utf-8").replace("</body>", f"{ADMIN_QA_SCRIPT}</body>")
            body = html.encode("utf-8")
            authenticated = mode != "admin-guest"
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Set-Cookie", f"qa_mode={mode}; Path=/; SameSite=Lax")
            self.send_header("Set-Cookie", "qa_auth=1; Path=/; SameSite=Lax" if authenticated else "qa_auth=; Path=/; Max-Age=0; SameSite=Lax")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return

        if parsed.path.startswith("/api/v1/admin/"):
            if not self._authorized_admin():
                self._json(403, {"title": "Forbidden"})
                return
            params = parse_qs(parsed.query)
            page = int(params.get("page", ["0"])[0])
            size = int(params.get("size", ["12"])[0])
            if parsed.path == "/api/v1/admin/users":
                status = params.get("accountStatus", [""])[0]
                query = params.get("query", [""])[0].lower()
                records = [record for record in USERS if (not status or record["accountStatus"] == status)]
                if query:
                    records = [record for record in records if query in record["email"].lower() or query in record["displayName"].lower()]
                self._json(200, admin_page(records, page, size))
                return
            if parsed.path == "/api/v1/admin/posts":
                status = params.get("moderationStatus", [""])[0]
                query = params.get("query", [""])[0].lower()
                records = [record for record in POSTS if (not status or record["moderationStatus"] == status)]
                if query:
                    records = [record for record in records if query in record["title"].lower() or query in record["content"].lower()]
                self._json(200, admin_page(records, page, size))
                return
            if parsed.path == "/api/v1/admin/audit-logs":
                self._json(200, admin_page(AUDIT, page, size))
                return
            self._json(404, {"title": "Not found"})
            return
        super().do_GET()


if __name__ == "__main__":
    if not (base.base.OUT / "admin" / "index.html").exists():
        raise SystemExit("frontend/out/admin/index.html is missing; run npm run build first")
    mimetypes.add_type("application/javascript", ".js")
    server = ThreadingHTTPServer(("127.0.0.1", base.base.PORT), Handler)
    print(f"admin QA server listening on http://127.0.0.1:{base.base.PORT}", flush=True)
    server.serve_forever()

#!/usr/bin/env python3
"""Serve the exported Next.js app with deterministic API fixtures for visual QA."""

from __future__ import annotations

import json
import mimetypes
import os
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "out"
PORT = int(os.environ.get("VISUAL_QA_PORT", "4173"))

SESSION = {
    "tokenType": "Bearer",
    "accessToken": "visual-qa-access-token",
    "expiresInSeconds": 900,
    "refreshExpiresInSeconds": 2592000,
    "userId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
    "email": "long.registry.identity@example.com",
    "role": "USER",
}

PROFILE = {
    "id": SESSION["userId"],
    "email": SESSION["email"],
    "displayName": "Alexandria Registry Participant With A Very Long Public Name",
    "initials": "AR",
    "role": "USER",
    "createdAt": "2026-07-20T09:00:00Z",
    "updatedAt": "2026-07-25T06:00:00Z",
}

BALLOTS = [
    {
        "id": "11111111-1111-1111-1111-111111111111",
        "authorId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        "author": {"id": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa", "displayName": PROFILE["displayName"], "initials": "AR"},
        "ballotNumber": "BALLOT-2026-0001",
        "title": "Should public software projects publish a plain-language decision record for every major architectural change?",
        "content": "A long-form public statement used to verify typography, wrapping, card density, and the full-record dialog across compact and wide viewports. The record should remain readable without introducing horizontal scrolling.",
        "category": "TECHNOLOGY",
        "status": "OPEN",
        "closesAt": "2026-08-01T12:00:00Z",
        "voteScore": 126,
        "upVotes": 184,
        "downVotes": 58,
        "totalVotes": 242,
        "verdictThreshold": 70,
        "verdict": "UP",
        "finalVerdict": False,
        "createdAt": "2026-07-25T02:00:00Z",
        "updatedAt": "2026-07-25T05:00:00Z",
    },
    {
        "id": "22222222-2222-2222-2222-222222222222",
        "authorId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
        "author": {"id": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb", "displayName": "Morgan Lee", "initials": "ML"},
        "ballotNumber": "BALLOT-2026-0002",
        "title": "Would a four-day workweek improve software quality?",
        "content": "Teams report different outcomes depending on staffing, release cadence, and operational responsibilities.",
        "category": "WORK",
        "status": "OPEN",
        "voteScore": -8,
        "upVotes": 46,
        "downVotes": 54,
        "totalVotes": 100,
        "verdictThreshold": 70,
        "verdict": "UNDECIDED",
        "finalVerdict": False,
        "createdAt": "2026-07-24T10:30:00Z",
        "updatedAt": "2026-07-25T04:30:00Z",
    },
    {
        "id": "33333333-3333-3333-3333-333333333333",
        "authorId": "cccccccc-cccc-cccc-cccc-cccccccccccc",
        "author": {"id": "cccccccc-cccc-cccc-cccc-cccccccccccc", "displayName": "Voter CCCCCCCC", "initials": "VC"},
        "ballotNumber": "BALLOT-2026-0003",
        "title": "Should AI-generated code require mandatory human review before production deployment?",
        "content": "This closed record verifies final-verdict styling and disabled voting controls.",
        "category": "AI POLICY",
        "status": "CLOSED",
        "closedAt": "2026-07-25T01:00:00Z",
        "voteScore": 91,
        "upVotes": 120,
        "downVotes": 29,
        "totalVotes": 149,
        "verdictThreshold": 70,
        "verdict": "UP",
        "finalVerdict": True,
        "createdAt": "2026-07-20T09:00:00Z",
        "updatedAt": "2026-07-25T01:00:00Z",
    },
]

PAGE = {
    "content": BALLOTS,
    "totalElements": len(BALLOTS),
    "totalPages": 1,
    "number": 0,
    "size": 8,
    "first": True,
    "last": True,
    "empty": False,
}

QA_SCRIPT = r"""
<script>
(() => {
  const mode = new URLSearchParams(location.search).get('qa') || 'feed';
  const finish = () => {
    const root = document.documentElement;
    const dialog = document.querySelector('[role="dialog"]');
    const voter = document.querySelector('[data-qa-voter-id]');
    root.dataset.qaOverflow = String(root.scrollWidth > root.clientWidth);
    root.dataset.qaCards = String(document.querySelectorAll('article').length);
    root.dataset.qaAuthors = String(document.querySelectorAll('[data-qa-author]').length);
    root.dataset.qaDialog = String(Boolean(dialog));
    root.dataset.qaDialogOverflow = String(Boolean(dialog) && dialog.scrollWidth > dialog.clientWidth);
    root.dataset.qaVoter = String(Boolean(voter));
    root.dataset.qaGuest = String(Boolean(document.querySelector('[data-qa-guest-actions]')));
    root.dataset.qaMenu = String(Boolean(voter && voter.hasAttribute('open')));
  };
  const waitForReady = (attempt = 0) => {
    const openButton = [...document.querySelectorAll('button')]
      .find(button => button.textContent && button.textContent.includes('VIEW FULL RECORD'));
    const voter = document.querySelector('[data-qa-voter-id]');
    const needsVoter = mode.startsWith('auth');
    if ((!openButton || (needsVoter && !voter)) && attempt < 70) {
      return setTimeout(() => waitForReady(attempt + 1), 100);
    }
    if (mode === 'detail' && openButton) openButton.click();
    if (mode === 'auth-menu' && voter) voter.querySelector('summary')?.click();
    setTimeout(finish, mode === 'feed' ? 150 : 500);
  };
  waitForReady();
})();
</script>
"""


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(OUT), **kwargs)

    def _json(self, status: int, payload: object) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _authenticated_fixture(self) -> bool:
        return "qa_auth=1" in (self.headers.get("Cookie") or "")

    def do_POST(self) -> None:  # noqa: N802
        path = urlparse(self.path).path
        if path == "/api/v1/auth/refresh":
            if self._authenticated_fixture():
                self._json(200, SESSION)
            else:
                self._json(401, {"title": "No QA session", "detail": "Guest fixture"})
            return
        self._json(404, {"title": "Not found"})

    def do_GET(self) -> None:  # noqa: N802
        parsed = urlparse(self.path)
        if parsed.path == "/api/v1/posts":
            self._json(200, PAGE)
            return
        if parsed.path == "/api/v1/users/me":
            if self._authenticated_fixture() and self.headers.get("Authorization") == "Bearer visual-qa-access-token":
                self._json(200, PROFILE)
            else:
                self._json(401, {"title": "Unauthorized"})
            return
        if parsed.path.startswith("/api/"):
            self._json(404, {"title": "Not found"})
            return

        requested = parsed.path
        if requested in ("", "/"):
            index = OUT / "index.html"
            html = index.read_text(encoding="utf-8").replace("</body>", f"{QA_SCRIPT}</body>")
            body = html.encode("utf-8")
            mode = parse_qs(parsed.query).get("qa", ["feed"])[0]
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Set-Cookie", "qa_auth=1; Path=/; SameSite=Lax" if mode.startswith("auth") else "qa_auth=; Path=/; Max-Age=0; SameSite=Lax")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            return

        super().do_GET()

    def log_message(self, format: str, *args: object) -> None:
        print(f"visual-qa: {self.address_string()} - {format % args}", flush=True)


if __name__ == "__main__":
    if not (OUT / "index.html").exists():
        raise SystemExit("frontend/out/index.html is missing; run npm run build first")
    mimetypes.add_type("application/javascript", ".js")
    server = ThreadingHTTPServer(("127.0.0.1", PORT), Handler)
    print(f"visual-qa server listening on http://127.0.0.1:{PORT}", flush=True)
    server.serve_forever()

#!/usr/bin/env python3
"""Serve the exported Next.js app with deterministic API fixtures for visual QA."""

from __future__ import annotations

import json
import mimetypes
import os
from http.cookies import SimpleCookie
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
        "authorId": SESSION["userId"],
        "author": {"id": SESSION["userId"], "displayName": PROFILE["displayName"], "initials": "AR"},
        "ballotNumber": "BALLOT-2026-0001",
        "title": "Should public software projects publish a plain-language decision record for every major architectural change?",
        "content": "A long-form public statement used to verify typography, wrapping, card density, and the full-record dialog across compact and wide viewports. The record should remain readable without introducing horizontal scrolling.",
        "category": "TECHNOLOGY",
        "status": "OPEN",
        "closesAt": "2026-08-01T12:00:00Z",
        "voteScore": 200,
        "upVotes": 200,
        "downVotes": 0,
        "totalVotes": 200,
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
        "voteScore": 0,
        "upVotes": 50,
        "downVotes": 50,
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
        "title": "Should an archived ballot with no recorded votes remain visibly undecided?",
        "content": "This closed zero-vote record verifies the final undecided stamp, disabled controls, and zero percentage boundaries.",
        "category": "PUBLIC RECORD",
        "status": "CLOSED",
        "closedAt": "2026-07-25T01:00:00Z",
        "voteScore": 0,
        "upVotes": 0,
        "downVotes": 0,
        "totalVotes": 0,
        "verdictThreshold": 70,
        "verdict": "UNDECIDED",
        "finalVerdict": True,
        "createdAt": "2026-07-20T09:00:00Z",
        "updatedAt": "2026-07-25T01:00:00Z",
    },
]

SERVER_ONLY_BALLOT = {
    **BALLOTS[1],
    "id": "44444444-4444-4444-4444-444444444444",
    "ballotNumber": "BALLOT-2026-0099",
    "title": "Registry-wide server-only search result",
    "content": "This record is absent from the initially loaded page and can only appear after a backend query.",
    "category": "SEARCH QA",
}


def page_payload(content: list[dict], page: int = 0, size: int = 8, total: int | None = None, total_pages: int | None = None) -> dict:
    total_elements = len(content) if total is None else total
    pages = (1 if total_elements else 0) if total_pages is None else total_pages
    return {
        "content": content,
        "totalElements": total_elements,
        "totalPages": pages,
        "number": page,
        "size": size,
        "first": page == 0,
        "last": pages == 0 or page + 1 >= pages,
        "empty": not content,
    }


QA_SCRIPT = r"""
<script>
(() => {
  const mode = new URLSearchParams(location.search).get('qa') || 'feed';
  const setInput = (input, value) => {
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set;
    setter?.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  };
  const finish = () => {
    const root = document.documentElement;
    const dialog = document.querySelector('[role="dialog"]');
    const voter = document.querySelector('[data-qa-voter-id]');
    const stamps = [...document.querySelectorAll('[data-qa-verdict-stamp]')];
    const activeFeed = [...document.querySelectorAll('button[aria-pressed="true"]')]
      .map(button => button.textContent?.trim()).find(text => ['LATEST', 'HOT', 'TOP DAY', 'TOP WEEK', 'MY BALLOTS'].includes(text || '')) || '';
    root.dataset.qaOverflow = String(root.scrollWidth > root.clientWidth);
    root.dataset.qaCards = String(document.querySelectorAll('article').length);
    root.dataset.qaAuthors = String(document.querySelectorAll('[data-qa-author]').length);
    root.dataset.qaDialog = String(Boolean(dialog));
    root.dataset.qaDialogOverflow = String(Boolean(dialog) && dialog.scrollWidth > dialog.clientWidth);
    root.dataset.qaVoter = String(Boolean(voter));
    root.dataset.qaGuest = String(Boolean(document.querySelector('[data-qa-guest-actions]')));
    root.dataset.qaMenu = String(Boolean(voter && voter.hasAttribute('open')));
    root.dataset.qaVoteSplits = [...document.querySelectorAll('[data-qa-vote-split]')]
      .map(element => element.getAttribute('data-qa-vote-split')).join(',');
    root.dataset.qaStamps = String(stamps.length);
    root.dataset.qaCurrentStamps = String(stamps.filter(stamp => stamp.getAttribute('data-verdict-kind') === 'current').length);
    root.dataset.qaFinalStamps = String(stamps.filter(stamp => stamp.getAttribute('data-verdict-kind') === 'final').length);
    root.dataset.qaStampAnimating = String(stamps.filter(stamp => stamp.getAttribute('data-verdict-animate') === 'true').length);
    root.dataset.qaActiveFeed = activeFeed;
    root.dataset.qaTitles = [...document.querySelectorAll('article h2')].map(title => title.textContent?.trim()).join('|');
    root.dataset.qaPagination = [...document.querySelectorAll('[role="status"]')].map(node => node.textContent?.trim()).join('|');
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
    if (mode === 'auth-stamp') {
      const secondCard = document.querySelectorAll('article')[1];
      const endorse = [...(secondCard?.querySelectorAll('button') || [])]
        .find(button => button.textContent && button.textContent.includes('ENDORSE'));
      endorse?.click();
      return setTimeout(finish, 220);
    }
    if (mode === 'server-search') {
      const search = document.querySelector('input[type="search"]');
      if (search) setInput(search, 'registry-wide');
      return setTimeout(finish, 1100);
    }
    if (mode === 'pagination') {
      const loadMore = [...document.querySelectorAll('button')]
        .find(button => button.textContent && button.textContent.includes('LOAD PAGE'));
      loadMore?.click();
      return setTimeout(finish, 800);
    }
    if (mode === 'auth-mine') {
      const mine = [...document.querySelectorAll('button')]
        .find(button => button.textContent?.trim() === 'MY BALLOTS');
      mine?.click();
      return setTimeout(finish, 800);
    }
    setTimeout(finish, mode === 'feed' ? 150 : 500);
  };
  waitForReady();
})();
</script>
"""


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(OUT), **kwargs)

    def _cookies(self) -> SimpleCookie:
        cookies = SimpleCookie()
        cookies.load(self.headers.get("Cookie") or "")
        return cookies

    def _fixture_mode(self) -> str:
        morsel = self._cookies().get("qa_mode")
        return morsel.value if morsel else "feed"

    def _authenticated_fixture(self) -> bool:
        morsel = self._cookies().get("qa_auth")
        return bool(morsel and morsel.value == "1")

    def _json(self, status: int, payload: object) -> None:
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self) -> None:  # noqa: N802
        path = urlparse(self.path).path
        if path == "/api/v1/auth/refresh":
            if self._authenticated_fixture():
                self._json(200, SESSION)
            else:
                self._json(401, {"title": "No QA session", "detail": "Guest fixture"})
            return
        self._json(404, {"title": "Not found"})

    def do_PUT(self) -> None:  # noqa: N802
        path = urlparse(self.path).path
        content_length = int(self.headers.get("Content-Length") or 0)
        if content_length:
            self.rfile.read(content_length)
        if path == "/api/v1/posts/22222222-2222-2222-2222-222222222222/vote" and self._authenticated_fixture():
            self._json(200, {
                "postId": "22222222-2222-2222-2222-222222222222",
                "voteScore": 100,
                "upVotes": 100,
                "downVotes": 0,
                "totalVotes": 100,
                "myVote": "UP",
                "verdictThreshold": 70,
                "verdict": "UP",
            })
            return
        self._json(404, {"title": "Not found"})

    def do_GET(self) -> None:  # noqa: N802
        parsed = urlparse(self.path)
        if parsed.path == "/api/v1/posts":
            params = parse_qs(parsed.query)
            mode = self._fixture_mode()
            feed = params.get("feed", ["LATEST"])[0]
            page = int(params.get("page", ["0"])[0])
            size = int(params.get("size", ["8"])[0])
            query = params.get("query", [""])[0].strip().lower()
            category = params.get("category", [""])[0].strip().lower()
            status = params.get("status", [""])[0].strip().upper()

            if feed == "MINE":
                if not self._authenticated_fixture():
                    self._json(401, {"title": "Unauthorized", "detail": "Authentication is required for the MINE feed"})
                    return
                records = [ballot for ballot in BALLOTS if ballot["authorId"] == SESSION["userId"]]
                self._json(200, page_payload(records, page, size))
                return

            if mode == "server-search" and query == "registry-wide":
                self._json(200, page_payload([SERVER_ONLY_BALLOT], page, size))
                return

            records = list(BALLOTS)
            if category:
                records = [ballot for ballot in records if ballot["category"].lower() == category]
            if status:
                records = [ballot for ballot in records if ballot["status"] == status]

            if mode == "pagination":
                pages = [records[:2], records[2:]]
                content = pages[page] if page < len(pages) else []
                self._json(200, page_payload(content, page, size, total=len(records), total_pages=2))
                return

            self._json(200, page_payload(records, page, size))
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
            self.send_header("Set-Cookie", f"qa_mode={mode}; Path=/; SameSite=Lax")
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

# Ballot Edition Stitch reference

This directory stores the approved Google Stitch design reference for the Vote System public MVP.

## Source of truth

1. Root [`DESIGN.md`](../../../DESIGN.md) — product scope, responsive behavior, accessibility, API-backed states, and implementation rules.
2. [`stitch-design.md`](stitch-design.md) — approved visual tokens and Ballot Edition rationale from Stitch.
3. [`screens.md`](screens.md) — screen-level decisions and review notes.
4. [`IMPLEMENTATION-NOTES.md`](IMPLEMENTATION-NOTES.md) — required production adjustments and non-goals.
5. [`EXPORT-MANIFEST.md`](EXPORT-MANIFEST.md) — original ZIP inventory and SHA-256 checksums.
6. [`STITCH-PROJECT.md`](STITCH-PROJECT.md) — approved Stitch project link.

## Approved export inventory

The reviewed ZIP contains Feed, Post Detail, Create Post, Login/Register, stamped logo, generated HTML, desktop PNG previews, and Stitch design notes. Exact original paths and checksums are recorded in `EXPORT-MANIFEST.md`.

## Usage

These artifacts are design references, not production code. Rebuild the UI in Next.js, TypeScript, and SCSS Modules.

Use the original PNG previews for desktop visual QA. Use root `DESIGN.md` for mobile and tablet behavior because the export only supplied desktop previews.

Generated HTML may be inspected to understand spacing and composition, but must not be copied wholesale into production components. Production semantics, accessibility, state handling, and API integration take precedence.

The original binary ZIP and PNG files are intentionally identified by checksum rather than converted to base64 text files. A future binary upload must preserve the paths and checksums listed in `EXPORT-MANIFEST.md`.

## Scope guard

The active UI scope is public web MVP only. Do not introduce admin pages, article images, comments, tags, bookmarks, sharing, or other unsupported product features.
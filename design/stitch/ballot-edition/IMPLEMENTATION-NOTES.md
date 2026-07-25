# Implementation handoff notes

## Approved screen references

- Feed
- Post Detail
- Create Post / Edit Post
- Login / Register
- Stamped logo

## Required production adjustments

- Reduce feed card height so desktop shows approximately four to six records per viewport.
- Make voting controls larger and more prominent than the Stitch desktop export.
- Implement explicit UP, DOWN, submitting, success, error, and rollback states.
- Keep the public application header-led; do not introduce a permanent application sidebar.
- Preserve search prominence and keyboard accessibility.
- Reuse Create Post structure for Edit Post.
- Implement mobile-first layouts at 375px and 768px rather than shrinking desktop markup.
- Add loading skeletons, empty states, retryable errors, session expiry, rate-limit messaging, delete confirmation, and logout confirmations.
- Respect server-authoritative `voteScore`, `upVotes`, `downVotes`, `totalVotes`, `myVote`, `verdictThreshold`, and `verdict` fields.

## Non-goals

- Do not implement admin UI from the earlier broad prompt.
- Do not add images to posts.
- Do not add comments, tags, categories, bookmarks, sharing, or unrelated reactions.
- Do not treat generated Stitch HTML as a component architecture.

## Visual QA order

1. Match typography, palette, paper surfaces, dividers, and stamped logo.
2. Match Feed composition and vote hierarchy.
3. Match Post Detail and form composition.
4. Verify authenticated and owner-only actions.
5. Verify 375px, 768px, and 1440px layouts.
6. Verify keyboard, focus, contrast, reduced motion, optimistic update, and rollback behavior.
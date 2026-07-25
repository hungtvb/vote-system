# Approved Stitch screens

The uploaded Stitch export contains four approved public UI references and one logo study.

## Feed

- Editorial list layout with Ballot Edition framing
- Search and primary navigation in the page shell
- Post cards use filing-card structure and a compact vote readout
- Production implementation should reduce desktop card height by roughly 20–25%
- Do not keep a permanent public sidebar on mobile or desktop MVP
- Add explicit selected, submitting, rollback, empty, and loading states

## Post detail

- Full post body presented as an official record
- Voting is the primary interaction
- Balance the layout with author, created/updated time, score, up/down totals, total votes, and server verdict
- Do not add comments, related posts, or analytics in the MVP

## Create post

- Official-submission document treatment
- Only title and content fields
- Increase content editing area in production
- Keep Publish visually primary and Cancel clearly secondary
- Reuse for edit mode with Save Changes, delete confirmation, and optional unsaved-change guard

## Login and register

- Registration-paper visual language
- Login fields: email and password
- Register fields: email, password, confirm password
- Include API errors, validation, loading, and session-expired states
- Keep both screens visually consistent

## Stamped logo

- Use as a reference for the Vote System wordmark/seal
- Production logo must remain legible at small header sizes
- Avoid relying on texture that becomes muddy on mobile or low-density screens

## Required responsive review

The Stitch export is a visual baseline, not a completed responsive specification. Verify and adjust at:

- 375px mobile
- 768px tablet
- 1440px desktop

The implementation must avoid horizontal scrolling, keep all touch targets at least 44px, and preserve prominent voting controls on mobile.

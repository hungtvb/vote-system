# Ballot Edition Stitch reference

This directory records the approved Google Stitch direction used as the visual reference for the Vote System public MVP.

## Files

- `stitch-design.md`: approved design tokens, typography, motifs, and component language
- `screens.md`: decisions extracted from the Feed, Post Detail, Create Post, Login/Register, and logo screens

The root [`DESIGN.md`](../../../DESIGN.md) is the implementation source of truth. It limits the design to capabilities supported by the current backend and defines responsive, accessibility, and API-driven states.

## Original export

The reviewed source package was `stitch_ballot_edition_voting_system.zip`. It contained generated HTML and PNG previews for the approved screens. Those generated files are reference material rather than production code; the durable design decisions have been normalized into the documents in this directory.

Production FE must rebuild the UI with semantic Next.js components, TypeScript models, and SCSS Modules. Do not copy Stitch-generated markup directly.

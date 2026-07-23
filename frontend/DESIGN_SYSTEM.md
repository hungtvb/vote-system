# PulseVote design system

This document is the UI source of truth for the product. It follows the `ui-ux-pro-max` workflow: plan with explicit tokens, commit to one aesthetic, build, inspect the rendered result, and review responsive/accessibility quality before merging.

## Product intent

- **Purpose:** help people publish a question, vote once, change their choice, and read the community signal quickly.
- **Tone:** clear, neutral, trustworthy, human.
- **Constraints:** mobile-first, content-heavy, no external font dependency, accessible tap targets, predictable interaction states.
- **Differentiation:** the vote control is the single signature element. Everything else stays quiet.

## Chosen aesthetic

- Minimal product UI
- Light neutral surfaces
- Flat controls with subtle depth
- Sentence case copy
- One blue accent
- No decorative hero mockups
- No purple/pink AI gradients
- No oversized bold typography
- No uppercase microcopy with wide tracking
- No stacked CSS override layers

## Typography

Use the native system sans stack:

```css
-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif
```

Weights:

- 400: body copy
- 500: secondary emphasis
- 600: buttons, metadata, labels
- 700: page and card headings

Rules:

- Body: 14–16px, line-height 1.55–1.7
- Card title: 17–19px
- Mobile page title: about 34px
- Desktop page title: max 60px
- Sentence case by default
- Never use letter spacing as decoration

## Color tokens

```text
Text             #172033
Muted            #667085
Subtle           #98A2B3
Border           #E4E7EC
Surface          #FFFFFF
Page background  #F7F8FA
Primary          #2563EB
Primary hover    #1D4ED8
Primary soft     #EFF6FF
Danger           #B42318
Danger soft      #FEF3F2
Success          #067647
```

Text contrast must meet WCAG AA.

## Spacing and shape

Spacing scale:

```text
4, 8, 12, 16, 20, 24, 32, 40, 48
```

Radius:

```text
8px controls
10–12px grouped controls
16px cards
18px modals
```

Shadows are reserved for modals, toasts, and subtle card separation. Do not use glow effects.

## Interaction rules

- Minimum touch target: 44×44px
- Every interactive element has a visible `:focus-visible` state
- Optimistic vote changes must show a saving state and rollback on failure
- Disable conflicting actions while a mutation is active
- Respect `prefers-reduced-motion`
- Use SVG icons, never emoji as functional icons
- Controls must reflow rather than shrink on mobile

## Responsive quality gates

Validate at:

- 375px
- 768px
- 1024px
- 1440px

Required checks:

- No horizontal scrolling
- Header controls remain usable
- Feed appears within the first short scroll on mobile
- Search and tabs remain readable
- Post title and body do not collapse below readable sizes
- Vote controls remain the clearest action
- Modal fits the viewport and is keyboard-dismissible
- Focus order follows visual order

## Pre-merge design loop

1. Build production assets.
2. Open the real rendered page.
3. Capture the four target viewport screenshots.
4. Test login, create, vote, edit, delete, search, tabs, modal, and toast states.
5. Fix blocker/high visual or accessibility issues.
6. Merge only after frontend, backend, and Docker gates pass.

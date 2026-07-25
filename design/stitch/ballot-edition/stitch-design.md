---
name: Ballot Edition
colors:
  primary: '#1E2A3A'
  background: '#F0E9D8'
  accent: '#B8342E'
  secondary: '#C9A876'
  text: '#3A362E'
typography:
  display:
    fontFamily: Playfair Display
    fontSize: 48px
    fontWeight: '700'
    lineHeight: 56px
  display-mobile:
    fontFamily: Playfair Display
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
  body:
    fontFamily: Public Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  metadata:
    fontFamily: JetBrains Mono
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
spacing:
  unit: 4px
  gutter: 24px
  margin: 32px
  container-max: 1100px
---

# Stitch visual language

The approved design uses an editorial-brutalist interpretation of ballots, filing cards, archival documents, perforated rules, ink stamps, and mechanical counters.

## Principles

- Sharp or nearly sharp corners
- Structural 1px or 2px Navy borders
- No glassmorphism, soft blur, or decorative gradients
- Depth through tonal paper layers rather than shadows
- 4px baseline spacing with 16px and 24px component padding
- Fixed, readable content width rather than full-screen dashboard layouts
- Mobile layouts stack while retaining ballot dividers and filing-card hierarchy

## Components

### Vote control

Use two intentional ballot choices. Neutral state has a Navy outline. Selected state includes a stamped, checked, or punched mark and must not rely on color alone.

### Mechanical counter

Use JetBrains Mono inside a rectangular bordered readout. It displays score and may animate with a short tick when authoritative vote data changes.

### Buttons

Primary buttons use Navy with Bone text. Secondary buttons use a transparent surface with a Navy border. Active press may shift the face by 2px to simulate physical depression.

### Filing cards

Post cards use sharp corners, Navy outlines, metadata tabs, and perforated internal separators. Production feed cards should be denser than the original Stitch desktop output so several posts remain visible in one viewport.

### Forms

Labels use uppercase institutional styling. Inputs may use strong underline or structured border treatments. The create/edit form should resemble an official submission document without reducing usability.

### Stamps

Use Seal Red sparingly for DOWN state, destructive feedback, and short status stamps such as submitted or verdict. Stamps are feedback, not permanent decorative noise.

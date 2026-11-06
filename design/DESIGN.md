---
name: Milkrun
description: Driver app for an offline-first delivery route, in the packing room's own marks.
colors:
  kraft-ground: "#C9A57A"
  kraft-fluting: "#B08A5E"
  kraft-ink: "#1B1610"
  kraft-ink-2: "#3E3020"
  kraft-label: "#F8F4EA"
  kraft-label-ink: "#15110C"
  kraft-label-ink-2: "#5A4A36"
  kraft-tape: "#E9B949"
  kraft-on-tape: "#1B1610"
  kraft-sticker: "#FF6B1A"
  kraft-on-sticker: "#1B1610"
  kraft-sticker-edge: "#F8F4EA"
  kraft-primary: "#1B1610"
  kraft-on-primary: "#F8F4EA"
  kraft-done-number: "#5E4934"
  night-ground: "#211912"
  night-fluting: "#3A2D20"
  night-ink: "#EEDDC3"
  night-ink-2: "#C2AC8C"
  night-label: "#33281D"
  night-label-ink: "#F6E9D4"
  night-label-ink-2: "#CDB89A"
  night-tape: "#D9A63C"
  night-on-tape: "#1B1610"
  night-sticker: "#FF7A2E"
  night-on-sticker: "#1B1610"
  night-sticker-edge: "#E9DCC6"
  night-primary: "#EEDDC3"
  night-on-primary: "#1B1610"
  night-done-number: "#8C7559"
  sun-ground: "#FFFFFF"
  sun-fluting: "#C9A57A"
  sun-ink: "#000000"
  sun-ink-2: "#2B2B2B"
  sun-label: "#FFFFFF"
  sun-label-ink: "#000000"
  sun-label-ink-2: "#333333"
  sun-label-edge: "#000000"
  sun-tape: "#FFC400"
  sun-on-tape: "#000000"
  sun-sticker: "#FF5A00"
  sun-on-sticker: "#000000"
  sun-sticker-edge: "#000000"
  sun-primary: "#000000"
  sun-on-primary: "#FFFFFF"
  sun-done-number: "#555555"
typography:
  stencil-hero:
    fontFamily: "Big Shoulders Stencil Display"
    fontSize: "96px"
    fontWeight: 800
    lineHeight: "84px"
  stencil-count:
    fontFamily: "Big Shoulders Stencil Display"
    fontSize: "72px"
    fontWeight: 800
    lineHeight: "64px"
    letterSpacing: "0"
  stencil-row:
    fontFamily: "Big Shoulders Stencil Display"
    fontSize: "40px"
    fontWeight: 700
    lineHeight: "40px"
  stencil-done:
    fontFamily: "Big Shoulders Stencil Display"
    fontSize: "28px"
    fontWeight: 700
    lineHeight: "30px"
  title-large:
    fontFamily: "system-ui, Roboto, -apple-system, sans-serif"
    fontSize: "22px"
    fontWeight: 600
    lineHeight: "28px"
  title-medium:
    fontFamily: "system-ui, Roboto, -apple-system, sans-serif"
    fontSize: "18px"
    fontWeight: 600
    lineHeight: "24px"
  title-small:
    fontFamily: "system-ui, Roboto, -apple-system, sans-serif"
    fontSize: "16px"
    fontWeight: 600
    lineHeight: "22px"
  body-large:
    fontFamily: "system-ui, Roboto, -apple-system, sans-serif"
    fontSize: "17px"
    fontWeight: 400
    lineHeight: "24px"
  body-medium:
    fontFamily: "system-ui, Roboto, -apple-system, sans-serif"
    fontSize: "15px"
    fontWeight: 400
    lineHeight: "21px"
  label-large:
    fontFamily: "system-ui, Roboto, -apple-system, sans-serif"
    fontSize: "17px"
    fontWeight: 600
    lineHeight: "22px"
  label-medium:
    fontFamily: "system-ui, Roboto, -apple-system, sans-serif"
    fontSize: "13px"
    fontWeight: 500
    lineHeight: "18px"
    letterSpacing: "0.4px"
  barcode:
    fontFamily: "ui-monospace, monospace"
    fontSize: "15px"
    fontWeight: 400
    lineHeight: "21px"
rounded:
  none: "0px"
  sticker-tag: "4px"
  sticker: "6px"
spacing:
  xs: "4px"
  s: "8px"
  m: "12px"
  l: "16px"
  xl: "24px"
  xxl: "32px"
  gutter: "16px"
components:
  block-button:
    backgroundColor: "{colors.kraft-primary}"
    textColor: "{colors.kraft-on-primary}"
    typography: "{typography.label-large}"
    rounded: "{rounded.none}"
    height: "64px"
    width: "100%"
  block-button-disabled:
    backgroundColor: "{colors.kraft-fluting}"
    textColor: "{colors.kraft-ink-2}"
  outline-block-button:
    textColor: "{colors.kraft-ink}"
    typography: "{typography.label-large}"
    rounded: "{rounded.none}"
    height: "56px"
  next-stop-label:
    backgroundColor: "{colors.kraft-label}"
    textColor: "{colors.kraft-label-ink}"
    rounded: "{rounded.none}"
    padding: "16px"
  stop-row:
    backgroundColor: "{colors.kraft-ground}"
    textColor: "{colors.kraft-ink}"
    typography: "{typography.body-large}"
    padding: "12px 16px"
    height: "72px"
  stop-row-done:
    backgroundColor: "{colors.kraft-ground}"
    textColor: "{colors.kraft-ink-2}"
    typography: "{typography.body-medium}"
    padding: "8px 16px"
    height: "56px"
  tape-strip:
    backgroundColor: "{colors.kraft-tape}"
    textColor: "{colors.kraft-on-tape}"
    typography: "{typography.title-small}"
    padding: "12px 16px"
    height: "48px"
  tape-tab:
    backgroundColor: "{colors.kraft-tape}"
    textColor: "{colors.kraft-on-tape}"
    typography: "{typography.label-medium}"
    padding: "4px 12px"
  exception-sticker:
    backgroundColor: "{colors.kraft-sticker}"
    textColor: "{colors.kraft-on-sticker}"
    typography: "{typography.title-small}"
    rounded: "{rounded.sticker}"
    padding: "12px 8px 12px 12px"
  sticker-tag:
    backgroundColor: "{colors.kraft-sticker}"
    textColor: "{colors.kraft-on-sticker}"
    typography: "{typography.label-medium}"
    rounded: "{rounded.sticker-tag}"
    padding: "4px 8px"
  handoff-block:
    backgroundColor: "{colors.kraft-label}"
    textColor: "{colors.kraft-label-ink}"
    typography: "{typography.label-large}"
    rounded: "{rounded.none}"
    padding: "0 12px"
    height: "56px"
  handoff-block-selected:
    backgroundColor: "{colors.kraft-label}"
    textColor: "{colors.kraft-label-ink}"
  parcel-line:
    backgroundColor: "{colors.kraft-label}"
    textColor: "{colors.kraft-label-ink}"
    typography: "{typography.body-large}"
    padding: "8px 16px"
    height: "64px"
  note-field:
    backgroundColor: "{colors.kraft-label}"
    textColor: "{colors.kraft-label-ink}"
    typography: "{typography.body-large}"
    rounded: "{rounded.none}"
  action-dock:
    backgroundColor: "{colors.kraft-label}"
    textColor: "{colors.kraft-label-ink}"
    padding: "12px 16px 0"
---

# Design System: Milkrun

## Overview

**Creative North Star: "The Packing Room"**

A route is a stack of boxes to move, so every screen speaks the marks a packing room already uses: corrugated kraft as the ground, near-black stencil ink, label-white stock for the thing in hand, translucent amber packing tape for work the server has not confirmed, safety-orange exception stickers for the server's rulings, and a thick marker strike for a stop dispatch cancelled. Nothing is decorative; each material carries exactly one meaning, and each state has its own physical mark so nothing depends on color alone.

The system is built for a driver mid-shift: one hand busy, sometimes gloved, in direct sun or a dark garage. That sets the density. Type never drops below 15 (body) on arm's-length surfaces, actions are 56 to 64 tall, and the screen has one thing at full strength (the next stop, or on a stop the Deliver block) while everything else recedes by size and ink. The world deliberately refuses the category default of white list cells with colored status chips.

One world, three schemes. **Kraft** is the day scheme, **Night** the dark scheme for garages and dusk (follows the system dark setting), **Sun** a black-on-white maximum-contrast scheme the driver toggles from the route header for direct sunlight. Platform structure stays native (Material 3 on Android, HIG on iOS); the world is applied through each platform's open theming layer.

**Units.** Token dimensions are written as `px` for tooling. Read 1px as 1dp on Android and 1pt on iOS. Type sizes are sp on Android and scale with font scale; on iOS they map to points and must scale with Dynamic Type (use `Font.custom(_:size:relativeTo:)` and `@ScaledMetric`). Both platforms must hold 1.3x without clipping.

**Key Characteristics:**
- Kraft ground, stencil ink, label stock; materials, not cards.
- Stencil numerals are the headline of every stop; everything else is the platform sans.
- Square corners everywhere except die-cut stickers.
- Tape means waiting, orange means a ruling, a marker strike means cancelled; each also carries words or a mark.
- One lifted label per route view; the rest recedes.
- One ink-filled block per screen, isolated from reversible actions.

## Colors

A warm, low-saturation corrugated palette with two signal materials (amber tape, safety orange) that are rare on purpose. Token names are scheme-prefixed (`kraft-`, `night-`, `sun-`); every role exists in all three schemes and components swap the prefix, never the role.

### Primary
- **Stencil Ink** (`*-ink`, `*-primary`): numbers, addresses, the ink fill of the block button. In Kraft and Sun the block's text is the label/white stock (`*-on-primary`); in Night the block is light ink with dark text. Measured: Kraft ink on ground 7.8:1, Night 13.0:1, Sun 21:1.

### Secondary
- **Packing Tape Amber** (`*-tape`, text `*-on-tape`): drawn at 78% opacity over whatever it is stuck to, with faint horizontal tape grain. Means only "recorded on the phone, not yet confirmed". Measured on the composited tape over ground: Kraft 9.4:1, Night 5.5:1, Sun 14.4:1 (solid tape: 9.8, 8.1, 13.2).

### Tertiary
- **Safety Orange** (`*-sticker`, text `*-on-sticker`, die-cut margin `*-sticker-edge`): server rulings and the delivered-after-cancel flag. The edge stays light in Kraft and Night and turns black in Sun. Measured: Kraft 6.3:1, Night 6.9:1, Sun 6.7:1.

### Neutral
- **Corrugated Kraft** (`*-ground`): the ground of every screen. In Sun it is white.
- **Fluting** (`*-fluting`): the 1-unit corrugation divider; also the disabled block fill. Never text.
- **Secondary Ink** (`*-ink-2`): status lines, section headings, upcoming stop numbers, done-row addresses, outlines at rest. Kraft 5.5:1, Night 7.9:1, Sun 14.2:1.
- **Label Stock** (`*-label`, with `*-label-ink` and `*-label-ink-2`): the next-stop label, parcel sheet, action dock, bottom sheet, note field. Label ink is 17.1:1 in Kraft; secondary label ink 7.7:1 (Kraft), 7.5:1 (Night).
- **Faded Stencil** (`*-done-number`): numbers and stencil marks of finished stops. Kraft 3.7:1, Night 4.0:1, Sun 7.5:1: large text and graphics only.
- **Label Edge** (`sun-label-edge`): a 2-unit ink outline on label stock in Sun only, where label and ground are both white. Absent (transparent) in Kraft and Night.

### Named Rules
**The Tape Is Waiting Rule.** Amber tape means work recorded on the phone and not yet confirmed by the server, and nothing else. Waiting is calm: tape is never paired with error language, red, or alarm motion.

**The Orange Is a Ruling Rule.** Safety orange appears only when the server ruled on something (or a delivery stood over an unseen cancel). It always carries the hazard mark and a plain sentence saying what happened and what to do.

**The Faded Stencil Rule.** `*-done-number` is for 28-and-up stencil numerals and stencil marks only. Text that must be read at body size on a finished row uses `*-ink-2`.

## Typography

**Display Font:** Big Shoulders Stencil Display (variable, weights 700 and 800; OFL, bundled at `design/fonts/`)
**Body Font:** the platform sans (Roboto on Android, SF Pro on iOS)
**Label/Mono Font:** the platform monospace (SF Mono on iOS), for parcel barcode ids only

**Character:** A condensed stencil, cut with bridges like a tote marking, carries the numbers; the platform sans does all the reading, so the world never costs legibility.

### Hierarchy
- **Stencil Hero** (800, 96/84): the next stop's number on the route label and the stop screen's title. Single line.
- **Stencil Count** (800, 72/64): the remaining count in the route header, set beside "left" in Title Large.
- **Stencil Row** (700, 40/40): upcoming stop numbers in the list, in secondary ink.
- **Stencil Done** (700, 28/30): finished stop numbers, in faded stencil; smaller so done rows fall back by size as well as ink.
- **Title Large** (600, 22/28): addresses on the lifted label and the stop screen, "left", sheet and finished titles.
- **Title Medium** (600, 18/24): bottom sheet question.
- **Title Small** (600, 16/22): section headings ("Done · 3", "Parcels · 1 of 3 scanned", "Left at"), tape strip title, sticker title.
- **Body Large** (400, 17/24): upcoming row addresses, status lines on labels, parcel descriptions.
- **Body Medium** (400, 15/21): secondary lines, done-row addresses, sticker body. The floor for anything read.
- **Label Large** (600, 17/22): button and handoff labels; Bold (700) on the block button and on the selected handoff block, Medium (500) on unselected handoff blocks.
- **Label Medium** (500, 13/18, +0.4 tracking): route id, tape-strip detail, "All sent", "Tap to scan"; Bold on the tape tab and sticker tag.
- **Barcode** (mono 400, 15/21): the full parcel label id under each parcel.

### Named Rules
**The Stencil Is For Numbers Rule.** The stencil face sets stop numbers and the remaining count, nothing else: no words, no headings, no buttons.

**The Fifteen Floor Rule.** Nothing a driver reads as a sentence is below 15; 13 is reserved for short labels and ids.

## Layout

A single scrolling column on a phone in portrait, 16 gutter on both sides. Spacing steps are 4, 8, 12, 16, 24, 32; nothing in the build sits off that scale except component-internal geometry.

**Route screen, top to bottom.** Header (route id in Label Medium with the Sun toggle at 48 square; the stencil count with "left" and the delivered / not delivered line), then the sync line, then any exception stickers, then the next stop as the lifted label (16 inset from the ground, 16 inside), then upcoming rows, then a "Done · N" heading over the finished rows. Pull to refresh on the whole column; 32 bottom padding.

**The recede.** The next stop alone is at full strength: label stock, hero stencil, its own block button. Upcoming rows sit on the kraft at 72 minimum height with 40 stencil in secondary ink and addresses in ink. Finished rows drop to 56 minimum, 28 faded stencil, Body Medium addresses in secondary ink, and a stencil check or cross at the right. The number column is a fixed 56 wide, 12 to the text.

**Sync line.** When work is waiting it is the full-width tape strip. When nothing is waiting it collapses to a fluting line with "All sent" in Label Medium.

**Stop screen.** A 48-tall bar with only the back control (the hero number below is the title; never repeat "Stop 05" above it) and the tape tab when unsynced. Then hero number with address and status, an optional sticker, the parcel heading, the parcel sheet on label stock (rows 64 minimum), a 24 gap, the note field, 32. The action dock is pinned to the bottom on label stock: reversible actions ("Arrived", "Can't deliver") side by side at 12 apart, then "Left at" and the 2 x 2 handoff grid, then a 32 gap, then the full-width Deliver block edge to edge.

**Targets.** Primary action 64; every other mid-stop action 56; platform minimum (48 dp / 44 pt) for chrome such as back, the Sun toggle and "Got it".

### Named Rules
**The One Lifted Label Rule.** On the route, exactly one stop is on label stock: the next one. Every other stop is a kraft row.

**The Thumb's Slip Rule.** The irreversible Deliver never sits within a thumb's slip of another control: 32 of empty space above it, and nothing beside it.

## Elevation & Depth

Flat material on flat material. Kraft, tape and stickers have no shadow; tape reads as tape by its translucency and the box seam running under it, stickers by their die-cut light edge and tilt. The only shadow is label stock lifting off the kraft, and its strength says how much the label matters.

### Shadow Vocabulary
- **Label lift** (Android `shadowElevation = 2.dp`; iOS approximately `shadow(color: .black.opacity(0.18), radius: 2, y: 1)`): the next-stop label and the route-done label.
- **Sheet lift** (Android `1.dp`; iOS approximately `radius: 1, y: 0.5`): the parcel sheet on the stop screen.
- **Dock lift** (Android `6.dp`; iOS approximately `shadow(color: .black.opacity(0.22), radius: 6, y: -2)`): the action dock, label stock pinned above the scroll.

In Sun, label and ground are the same white, so label stock also takes the 2-unit `sun-label-edge` outline; the shadow alone would not separate it.

### Named Rules
**The Only Label Lifts Rule.** Shadows belong to label stock. Kraft rows, tape, stickers and buttons are flat.

## Shapes

Square-cut everything: labels, buttons, the note field, handoff blocks and the bottom sheet (top corners included) are 0 radius. Stickers alone are die-cut: the exception sticker at 6 with a 2 light edge, the sticker tag at 4 with a 1.5 edge. Tape has torn zig-zag ends (about six teeth per end, shallow) instead of corners; on the route strip it runs inside the gutter so the torn ends land on the kraft.

Tilts are part of the form: the tape tab is laid at -4 degrees, the exception sticker slapped on at -1.2, the sticker tag at -1.5. Under Reduce Motion the sticker and tag lie straight; the tape tab keeps its tilt.

### Named Rules
**The Fluting Rule.** The 1-unit fluting line is the system's only divider: between rows, under the done heading, between parcels, and as the "All sent" line. Outlines on controls and the box seam under the tape strip are part of those marks, not dividers.

## Components

### Buttons
Blocks, not pills: heavy, square and full of ink.
- **Shape:** square (0).
- **Block (primary):** full width, 64 tall, `*-primary` fill, `*-on-primary` Label Large Bold. Sits flush at the foot of the next-stop label ("Arrived" or "Scan and deliver") and alone at the foot of the dock ("Deliver 2 parcels"). Disabled: fluting fill, secondary ink text.
- **Outline block (secondary):** 56 tall, 2 ink outline, ink Label Large, no fill. Used for every reversible action and for the four failure reasons in the sheet (full width, 8 apart). Disabled: the outline turns to fluting.
- **Pressed / Focus:** platform defaults (Material ripple; iOS highlight). No custom hover states; this is a touch product.

### Navigation
- **Stop bar:** 48 tall, ground-colored, back arrow only, no title text; the tape tab sits at its trailing end when the stop is unsynced. Platform back conventions apply (system back on Android, swipe and chevron on iOS).
- **Icons:** platform system icons for chrome only (Material icons on Android; SF Symbols on iOS: `sun.max` / `sun.max.fill`, `chevron.backward`, `qrcode.viewfinder`, `checkmark`), tinted with the ink of the surface they sit on.

### Chips / Selection (handoff grid)
- **Style:** four label-stock blocks in a 2 x 2 grid, 12 apart, 56 minimum, Label Large centred. A grid rather than a segmented row so every label stays whole at 1.3x.
- **State:** unselected is a 1.5 secondary-label-ink frame with Medium text; selected is a 4 label-ink frame, a 22 check and Bold text. Single selection, exposed as a radio group.

### Cards / Containers (label stock)
- **Corner Style:** square (0).
- **Background:** `*-label`, text in `*-label-ink` / `*-label-ink-2`.
- **Shadow Strategy:** label lift, sheet lift or dock lift (see Elevation & Depth); plus the 2 label edge in Sun.
- **Internal Padding:** 16.

### Inputs / Fields
- **Style:** outlined field, square (0), label-stock fill, label ink text, secondary ink outline at rest; placeholder in secondary label ink. 500-character limit.
- **Focus:** outline and floating label shift to ink.

### Stop row
Kraft row with a fluting line beneath. 56-wide stencil number column; address (max 2 lines) and status line. Cancelled stops take the marker strike through the number. Finished rows take the stencil check (delivered, 28) or cross (not delivered, 26) in faded stencil at the trailing edge. When unsynced, the row reserves 72 of trailing space so the tape tab never covers the address or the check.

### Tape strip (signature)
Full width, 48 minimum. A 2 secondary-ink box seam crosses the screen edge to edge in the strip's lower third (10 from the bottom), and the tape (inset by the gutter, torn ends, 78% opacity, faint grain) lies over it. Title in Title Small at the start ("Sending 2 changes", or "2 waiting for signal" once a send has failed), detail in Label Medium at the end ("Retrying"). Read as one element by assistive tech. The seam must never pass through the words; a line through text reads as a marker strike.

### Tape tab (signature)
A short torn piece of tape reading "Unsent" (Label Medium Bold), padded 4 x 12, tilted -4. Stuck across the top-trailing corner of the next-stop label (half on the label, half on the kraft), at the trailing edge of an unsynced row, and in the stop bar. Motion: slides in from the trailing side over 200 ms with a 120 ms fade when a change is recorded; peels off toward the leading side by half its width over 180 ms with a 180 ms fade when the server confirms. Reduce Motion: 150 ms crossfade both ways.

### Exception sticker (signature)
Safety-orange, die-cut 6, 2 light edge, inset 8 from its container, tilted -1.2. Hazard mark (22, an ink diamond with a cut-out exclamation, so the ruling reads without the orange) then the title in Title Small (exposed as a heading) and the body in Body Medium. Stays until "Got it" (Label Large, 48 minimum, trailing). Motion: slapped on with a 150 ms scale from 1.06 to 1. Reduce Motion: no scale and no tilt. The sticker tag is its one-line, actionless form for a row ("Delivered after cancel · dispatch notified"): Label Medium Bold, padded 4 x 8, die-cut 4, tilted -1.5.

### Stencil marks and marker strike (signature)
The check and cross are drawn as stencil cuts: butt-capped strokes at 16% of the mark's size with bridges (7% gaps) where strokes meet or cross. The marker strike is a 6-unit round-capped ink line rising slightly left to right across the number. Each has its own accessible label ("Delivered", "Not delivered"); the status line repeats the state in words.

## Do's and Don'ts

### Do:
- **Do** set stop numbers and the remaining count in Big Shoulders Stencil Display and everything else in the platform sans.
- **Do** keep exactly one stop on label stock on the route screen, and step the rest down: 40 stencil in secondary ink for upcoming, 28 faded stencil with a stencil check or cross for finished.
- **Do** give every state its own mark and words: tape tab "Unsent", hazard mark on orange, stencil check or cross, marker strike, and the status line.
- **Do** keep the primary action at 64 and every mid-stop action at 56; chrome at the platform minimum (48 dp / 44 pt).
- **Do** leave 32 of empty space above Deliver.
- **Do** show selection as a heavier frame (4) plus a check.
- **Do** ship all three schemes for every new surface, and give label stock the 2 ink edge in Sun.
- **Do** honour Reduce Motion: tape becomes a 150 ms crossfade, stickers lose their scale and tilt.
- **Do** take every sentence from the shared `Copy` so both apps say the same thing.

### Don't:
- **Don't** use amber tape for anything but unconfirmed work, or orange for anything but a server ruling.
- **Don't** fill a second block with ink on the same screen; the filled block is that screen's primary action alone.
- **Don't** round corners on labels, buttons, fields, sheets or blocks; only stickers are die-cut.
- **Don't** add dividers other than the 1-unit fluting line, and never run the tape seam through text.
- **Don't** put shadows on kraft rows, tape, stickers or buttons.
- **Don't** set words in the stencil face, or set body text in faded stencil.
- **Don't** repeat the stop number as a title in the stop bar.
- **Don't** fall back to white list cells with colored status chips.
- **Don't** imitate Amazon's branding, trade dress or app UI.

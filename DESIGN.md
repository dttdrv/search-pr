# Pane design language

Pane is black, white and quiet, after Nothing's design language. There is no glass, no blur, no
gradients, no cards-inside-cards. The page is the interface; everything else is a thin, flat,
confident layer around it.

## Principles

1. **True black and white.** `PaneTheme.colors` roles only. Light is white on black ink; dark is
   true black (OLED, so it also saves battery). Greys come from the `surface`/`fill` roles, never
   invented. The accent is the label colour: a primary button is solid ink.
2. **One red.** `signal` (Nothing's red) is a status light or a destructive action, nothing else:
   the loading dot in the address pill, a private-tab marker, "Clear" / "Delete". Never decoration.
3. **Lines, not boxes.** Group with hairlines (`separator`) and space, not with filled cards.
   Settings and lists are flat full-width rows with a hairline between them. A filled `surface` is
   for a control (search field, segmented control), not for containing content. No shadows except
   on things that hover over the page (see 5).
4. **Dots.** The signature is the dot: `DotText` for the wordmark and one big number per screen,
   `StatusDot` for state, round favicon/engine marks, a dot row for progress. Use it sparingly;
   it is an accent, not a font.
5. **The floating pill stays.** The address pill, its round buttons, menus and sheets float over
   the page as solid surfaces (`Modifier.floating(shape, shadow)`): one fill, a hairline, a short
   soft shadow. They are the only things with a shadow.
6. **Icons, or nothing.** Real, well-drawn glyphs from `PaneIcons`, all one weight, in the label
   colour, with no tile behind them. A row has either a glyph or none; never a letter standing in
   for a logo. Sites are shown by their own favicon (`SiteIcon`), search engines by their logo
   (`EngineIcon`).
7. **Words are few.** Titles of one to three words; a subtitle only when it changes a decision.
   Section headings are `SectionLabel` ("PRIVACY"): small capitals, open tracking.
8. **Easy for everyone, deep for experts.** The top level of every screen is the five things most
   people need, in plain words, 56dp rows. The rest sits under an "Advanced" row that expands in
   place. Nothing is removed, only tiered.
9. **Respect the site.** The page runs truly edge to edge, under the status bar and the floating
   pill. A soft veil in the page's own top colour (strongest at the very top, gone just below the
   icons) keeps the clock and battery legible and lets scrolled content fade out under them, as
   in the native apps. Fixed footers stay clear of the floating pill.
10. **Don't sell safety.** Pane blocks trackers and isolates cookies quietly. No counters, badges or
    slogans about privacy on the start page, in onboarding or at the top of Settings; nobody opens
    a browser for them. Private tabs exist and are labelled, and that is all.
11. **Passwords and passkeys belong to the phone.** Android autofill and Credential Manager do it;
    Pane names no password manager.

## Motion

`Motion.snappy/smooth/bouncy/push/interactive/fade` are the only animation specs (see the table in
`Motion.kt`). Animated values are read in `graphicsLayer`/`offset`/`drawBehind` lambdas, never in
composition. Shapes morph rather than cross-fade: the address pill *becomes* the search field, the
menu grows out of its button, a tab's page flies into its card. Motion is short and exact, not
bouncy for its own sake. Honour `LocalReduceMotion`.

## Performance rules

- The page is a plain `SurfaceView` (GeckoView's default): never put anything that has to read the
  page's pixels (blur, glass) over it.
- No per-frame work in composition; nothing that redraws the whole window while the page scrolls.
- No animated gradients, no blurs, no `graphicsLayer` with `renderEffect`.

## Building blocks

| Need | Use |
| --- | --- |
| Solid floating surface (pill, round button, sheet) | `Modifier.floating(shape, shadow)` |
| Round floating button / text pill | `FloatingCircle` / `FloatingTextButton` |
| Primary / secondary action | `PrimaryButton` (solid ink) / `OutlineButton` (hairline) |
| Section heading | `SectionLabel("PRIVACY")` |
| Wordmark, one big number | `DotText` |
| Status light | `StatusDot` |
| Site / engine mark | `SiteIcon` / `EngineIcon` |
| Fade where content meets an edge | `EdgeFade` |
| Arrive animation | `Modifier.entrance(index)` |
| Press feedback | `Modifier.pressScale`, `Modifier.pressDim` |
| Sheet / menu | `PaneSheet` (a flat floating sheet) |
| Settings lists | `GroupedSection`, `ListRow`, `ToggleRow` (flat rows, hairlines) |

# Pane design language

Pane is a white page, a few quiet greys and one blue. Small calm type, flat tones instead of
outlines, centred layouts, generous space, glyphs instead of words where a glyph is enough, and one
floating pill that is the product. Frost is for the floating bar, the tab overview's switch and the menu sheet, and nowhere else; there is no other glass or blur, no gradient, no card inside a card. The page is the interface; everything else is a thin, flat,
confident layer around it. The reference is the Mac browser *Search* (Drice Roland / Office
Commun): take its restraint and polish, not its logo.

## Principles

1. **A white ground and quiet greys.** `PaneTheme.colors` roles only; nothing is tinted. The ground
   is white (dark: a soft #1C1C1C, not black), the ink is near-black (dark: off-white). Every other
   colour is a grey of the same neutral ramp:

   | Role | Light | Dark | For |
   | --- | --- | --- | --- |
   | `background` | 1.00 | 0.11 | the ground |
   | `label` | 0.09 | 0.93 | ink: titles, body, the primary pill |
   | `secondaryLabel` | 0.55 | 0.58 | muted: every second line, section labels |
   | `faint` | 0.83 | 0.32 | idle outlines, unselected marks |
   | `hairline` / `separator` | 0.91 | 0.20 | lines between list rows |
   | `fill` | 0.937 | 0.175 | the wash behind a selected or live row |
   | `surface` | 0.965 | 0.15 | a quiet control resting on the ground |

   `tertiaryLabel` sits between muted and faint (placeholders, chevrons, timestamps). The accent is
   a clear blue (`#0285FF`, dark `#2A8BF2`): the primary button, the switch, the cursor, the open
   tab. Nothing else is tinted.
2. **Colour means little.** The accent blue marks the one action or selection that matters. An
   amber is for "this connection is not secure" (`warning` / `signal`, never decoration) and a
   restrained red (`destructive`) is for Clear / Delete. A secure padlock stays quiet grey. There is no green. Loading, progress and
   "working" are plain ink (`StatusDot` defaults to `label`).
3. **Lines, not boxes.** Group with hairlines and space, not filled cards. Settings and lists are
   flat full-width rows with a hairline between them. A filled `surface` or `fill` is for a control
   or a selected row, not for containing content. Controls have no outline: a resting control is
   a soft `fill` or a shadow, never a border.
4. **Tiny muted labels.** A heading is a few small words in the muted grey, in sentence case:
   `SectionLabel("Privacy")`. No capitals, no letter-spacing. Secondary text is smaller and muted,
   never lighter or heavier.
5. **The floating pill stays.** The address pill and its round buttons are frosted
   (`Modifier.frosted(shape)`): the page behind them blurred, its colour deepened, under a white veil
   (black over dark pages) and then 10% of the opposite pole (a light bar reads 10% darker than a
   white page, a dark one 10% lighter than a black page, so it never melts into either), with ink
   glyphs, no shadow and no outline. The same frost, over the tab grid, is the tab overview's
   Private / Tabs switch and plus button; over the page, the menu sheet's panel (its cards stay
   solid, a wash of white on dark). Other menus and sheets float as flat solid surfaces
   (`Modifier.floating(shape)`) with one standard platform elevation shadow (`FloatingElevation`,
   Material's menu elevation). Where the blur can't be trusted a frosted surface is the solid
   floating one, never a veil over the sharp page. Only menus and sheets cast a shadow: cards, tiles
   and the start page sit flat on the grey ground, told apart by tone. The blue pill is the only
   solid fill on a screen.
6. **Icons, or nothing.** Real, well-drawn glyphs from `PaneIcons`, all one weight, in the label
   colour, with no tile behind them, and only where everyone already knows them. A row has either
   a glyph or none; never a letter standing in for a logo. Sites are shown by their own favicon
   (`SiteIcon`), search engines by their logo (`EngineIcon`). The start page and the empty states
   have no artwork at all.
7. **Words are few.** Titles of one to three words ("Private", "No tabs", "Locked"); a subtitle
   only when it changes a decision. No taglines.
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

## Type

The phone's own system font; sizes follow the system font-size setting. Calm and small.

| Style | Size / line | Weight | Used for |
| --- | --- | --- | --- |
| `largeTitle` | 30 / 36 | semibold, -0.01em | screen titles, the start page's "Pane" |
| `title1` | 26 / 32 | semibold, -0.01em | one big number |
| `title2` | 21 / 27 | semibold, -0.01em | empty states |
| `title3` | 17 / 23 | semibold, -0.01em | sheet and alert titles |
| `headline` | 15 / 21 | medium | buttons, emphasis |
| `body` | 15 / 21 | regular | rows, fields |
| `callout` | 15 / 20 | regular | segmented control |
| `subheadline` | 14 / 19 | regular | messages |
| `footnote` | 12.5 / 17 | regular | second lines, section labels (medium) |
| `caption` / `caption2` | 12 / 16, 11 / 14 | regular | tiny labels |

Emphasis is medium weight, never bold.

## The mark

A page (a rounded-square outline) with a solid ink pill floating near its bottom edge: the product
in one picture. Ink `#171717` on a white plate; the monochrome layer is the same drawable. It lives
inside the 66dp safe zone of the 108dp adaptive canvas. Shortcut icons are black discs with white
glyphs. The wordmark is just the word "Pane" in `largeTitle` (or `title3` medium in a header).

## Motion

`Motion.snappy/smooth/bouncy/push/interactive/fade` are the only animation specs (see the table in
`Motion.kt`). Animated values are read in `graphicsLayer`/`offset`/`drawBehind` lambdas, never in
composition. Shapes morph rather than cross-fade: the address pill *becomes* the search field, the
menu grows out of its button, a tab's page flies into its card. Motion is short and exact, not
bouncy for its own sake. Honour `LocalReduceMotion`.

## Performance rules

- Only `Modifier.frosted` surfaces read the page's (or the tab grid's) pixels, and only the band under the bar, rendered once per frame; the menu sheet blurs the whole page, but only while it is up. Nothing else blurs the page.
- No per-frame work in composition; nothing that redraws the whole window while the page scrolls.
- No animated gradients, and no `renderEffect` outside `Modifier.frosted`.

## Building blocks

| Need | Use |
| --- | --- |
| Solid floating surface (pill, round button, sheet) | `Modifier.floating(shape, shadow)` |
| Round floating button / text pill | `FloatingCircle` / `FloatingTextButton` |
| Primary / secondary action | `PrimaryButton` (solid blue, red if it destroys; glyph + label) / `QuietButton` (no fill, no outline, glyph + muted label) / `IconPill` (glyph only) |
| Glyph button | `ChromeButton` (check, close, forward, trash: no word needed) |
| Section heading | `SectionLabel("Privacy")` (small, muted, sentence case) |
| Status light (loading, on) | `StatusDot` (ink; amber only for an unsafe connection) |
| Site / engine mark | `SiteIcon` / `EngineIcon` |
| Fade where content meets an edge | `EdgeFade` |
| Arrive animation | `Modifier.entrance(index)` (plays once per screen: a screen's `EntranceMemory` survives a back navigation) |
| Press feedback | `Modifier.pressScale`, `Modifier.pressDim` |
| Sheet / menu | `PaneSheet` (a flat floating sheet) |
| Settings lists | `GroupedSection`, `ListRow`, `ToggleRow` (flat rows, hairlines) |
| Search field | `SearchField` (soft pill, no outline) |

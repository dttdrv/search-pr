# Pane design language

Pane looks like ChatGPT's app in glass: ink on paper, paper on ink, nothing coloured but the page.

## Rules

1. **Monochrome.** Use `PaneTheme.colors` roles only. `accent` is the label colour itself, so a primary
   button is solid ink (paper in dark). No blue, green or violet chrome. Semantic red (`destructive`)
   and amber (`warning`) appear only where they carry meaning.
2. **Glass floats.** Bars, menus and sheets are frosted glass (`Modifier.glass(shape, GlassStrength)`)
   hovering over the page with a margin from the screen edges (≥10dp), never attached to an edge.
   Shapes: `PaneShapes.pill` for bars and buttons, `PaneShapes.floating` for menus and sheets,
   `PaneShapes.large`/`card` for cards.
3. **Icons, or nothing.** Never a half-measure (no letter tiles, no coloured pills). Where an icon
   helps, use a real, well-drawn monochrome glyph from `PaneIcons`, always the same weight: the
   controls everyone reads (back, close ×, plus, check, ellipsis, share, reload, lock), page actions
   in the menu, and one neutral tile per settings row (`SettingsIcon`). Sites are represented by
   their own favicon (`SiteIcon`) and search engines by their bundled logo (`EngineIcon`); when a
   site has none, a neutral globe tile. `IconTile` draws nothing.
4. **Everything moves on springs** (`Motion.*`). Things arrive (`Modifier.entrance(index)`),
   press (`pressScale` / `pressDim`), and hand off from gestures without a seam. Respect
   `LocalReduceMotion`.
5. **Respect the site.** The page runs edge to edge; the status area is painted with the colour
   sampled from the page's top edge (`PageColors`) and the glass tone follows the page behind the
   bar. Never draw a flat chrome-coloured band over a site.
6. **Passwords and passkeys belong to the phone.** Android autofill and Credential Manager do it;
   Pane names no password manager and offers no provider-specific UI.

## Building blocks

| Need | Use |
| --- | --- |
| Frosted surface | `Modifier.glass(shape, GlassStrength.Thin/Regular/Thick)` |
| Round floating button | `GlassCircle` |
| Text pill on glass | `GlassTextButton` (`strong = true` for solid ink) |
| Full-width action | `PrimaryButton` (pill, solid ink) |
| Arrive animation | `Modifier.entrance(index)`, `Modifier.dropIn()` |
| Press feedback | `Modifier.pressScale`, `Modifier.pressDim` |
| Sheet / menu card | `PaneSheet` (a floating glass card) |
| Grouped settings | `GroupedSection`, `ListRow`, `ToggleRow` (plain rows on `surface`) |

### The material

`Modifier.glass` is Haze's refractive Liquid Glass (a lens that bends the page along the rim,
depth blur, a top-left specular highlight, a shadowed lower edge, a press response that deepens
the refraction) with Pane's own frost on top: a milky top-lit veil and fine grain, so it reads as
frosted glass rather than clear plastic. Where scrolling content meets a bar, use `ProgressiveEdge`
so it dissolves into a graduated blur instead of a hard cut.

### Motion

`Motion.snappy/smooth/bouncy/push/interactive/fade` are the only animation specs (see the table in
`Motion.kt`). Animated values are read in `graphicsLayer`/`offset`/`drawBehind` lambdas, never in
composition. Shapes morph rather than cross-fade: the address pill *becomes* the editor field, the
menu grows out of its button, a tab's page flies into its card.

Glass blurs `LocalHazeState`, which the browser screen provides with the live page as source
(GeckoView runs on a TextureView so it can be blurred). Where no state is provided glass falls back
to a near-opaque tint, so it is always readable.

package app.pane.core.browser

/**
 * What the floating bar covers at the bottom of the page, in css pixels. Fixed and sticky bottom ui sits
 * [inset] above the page's bottom edge; the end of the page keeps [room] clear. Both are 0 where the bar is
 * away.
 */
data class BarRoom(val inset: Int, val room: Int) {
    companion object {
        /**
         * [bar], [label] and [find] are the heights the bar takes open, folded into its host label and as the
         * find bar; [nav] is the system's navigation inset under it. The room stays at the open bar's height
         * when it folds, so the end of the page doesn't move, while the lift follows the bar down to its label.
         * While the page has the keyboard the page ends on top of it and the bar is gone, but the find bar
         * floats above that keyboard.
         */
        fun of(onPage: Boolean, fullscreen: Boolean, keyboard: Boolean, finding: Boolean, folded: Boolean, bar: Float, label: Float, find: Float, nav: Float): BarRoom {
            if (!onPage || fullscreen || (keyboard && !finding)) return BarRoom(0, 0)
            val below = if (keyboard) 0f else nav
            val room = (if (finding) find else bar) + below
            return BarRoom(if (folded && !finding) (label + below).toInt() else room.toInt(), room.toInt())
        }
    }
}

package com.vextech.editpro.core.ui.sheet

import androidx.compose.runtime.compositionLocalOf

/**
 * When to show a tool sheet as a side panel instead of a bottom sheet, and how wide. The site does the same on wide screens.
 * Landscape phones count: a bottom sheet limited to 46% of a 360 dp screen leaves almost no room to work.
 */
object SheetPanel {
    const val PANEL_WIDTH_DP = 360

    /** Tablets and big windows (840 dp or wider), and any landscape screen at least 560 dp wide. */
    fun useSidePanel(widthDp: Int, heightDp: Int): Boolean = widthDp >= 840 || (widthDp > heightDp && widthDp >= 560)

    /** 360 dp, but never more than 45% of the screen width, so the page keeps most of the room. */
    fun panelWidthDp(widthDp: Int): Int = minOf(PANEL_WIDTH_DP, widthDp * 45 / 100)
}

/** True while a [ToolSheet] is drawn as a side panel: it then fills the panel's height instead of capping at 46% of the screen. */
val LocalSheetPanel = compositionLocalOf { false }

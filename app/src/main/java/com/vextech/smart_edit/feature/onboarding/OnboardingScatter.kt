package com.vextech.smart_edit.feature.onboarding

import kotlin.math.hypot

enum class DocKind { PDF, DOC, PPT, XLS, TXT, ZIP, IMAGE, FOLDER }

/** Position and look of one decorative file icon. x and y are fractions of the screen (0..1). */
data class ScatterItem(
    val kind: DocKind,
    val x: Float,
    val y: Float,
    val rotation: Float,
    val scale: Float,
    val alpha: Float,
)

data class ZoneRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

/**
 * The scattered document icons behind the onboarding text: larger and brighter near the glow,
 * smaller and dimmer toward the edges, none inside the text zone.
 */
object OnboardingScatter {
    /** Where the pager text sits. No icon centre may fall in here. */
    val textZone = ZoneRect(left = 0.10f, top = 0.50f, right = 0.90f, bottom = 0.80f)
    const val GLOW_X = 0.5f
    const val GLOW_Y = 0.30f

    val items: List<ScatterItem> = listOf(
        ScatterItem(DocKind.PDF, 0.12f, 0.07f, -14f, 0.80f, 0.60f),
        ScatterItem(DocKind.DOC, 0.36f, 0.11f, 10f, 0.95f, 0.80f),
        ScatterItem(DocKind.PPT, 0.62f, 0.06f, -8f, 0.85f, 0.70f),
        ScatterItem(DocKind.XLS, 0.90f, 0.09f, 12f, 0.75f, 0.50f),
        ScatterItem(DocKind.TXT, 0.07f, 0.24f, 9f, 0.85f, 0.60f),
        ScatterItem(DocKind.FOLDER, 0.92f, 0.27f, -10f, 0.80f, 0.55f),
        ScatterItem(DocKind.PDF, 0.26f, 0.22f, -16f, 1.05f, 0.90f),
        ScatterItem(DocKind.XLS, 0.58f, 0.19f, 14f, 1.15f, 1.00f),
        ScatterItem(DocKind.IMAGE, 0.40f, 0.31f, 6f, 1.10f, 0.95f),
        ScatterItem(DocKind.DOC, 0.74f, 0.33f, -12f, 0.95f, 0.85f),
        ScatterItem(DocKind.ZIP, 0.17f, 0.40f, 12f, 0.90f, 0.75f),
        ScatterItem(DocKind.PPT, 0.52f, 0.43f, -6f, 1.00f, 0.90f),
        ScatterItem(DocKind.TXT, 0.84f, 0.45f, 9f, 0.85f, 0.60f),
        ScatterItem(DocKind.PDF, 0.08f, 0.83f, -10f, 0.80f, 0.55f),
        ScatterItem(DocKind.IMAGE, 0.30f, 0.87f, 8f, 0.90f, 0.70f),
        ScatterItem(DocKind.XLS, 0.55f, 0.90f, -12f, 0.85f, 0.65f),
        ScatterItem(DocKind.ZIP, 0.80f, 0.85f, 10f, 0.90f, 0.70f),
        ScatterItem(DocKind.DOC, 0.93f, 0.93f, -8f, 0.75f, 0.45f),
        ScatterItem(DocKind.FOLDER, 0.18f, 0.96f, 12f, 0.75f, 0.45f),
        ScatterItem(DocKind.TXT, 0.68f, 0.97f, -6f, 0.75f, 0.45f),
    )

    fun distanceFromGlow(item: ScatterItem): Float = hypot(item.x - GLOW_X, item.y - GLOW_Y)

    /** Returns a list of problems; empty means the layout satisfies every rule. */
    fun problems(list: List<ScatterItem> = items): List<String> {
        val out = ArrayList<String>()
        list.forEachIndexed { i, it ->
            if (it.x !in 0f..1f || it.y !in 0f..1f) out += "item $i is off screen"
            if (it.rotation !in -20f..20f) out += "item $i rotation ${it.rotation} out of range"
            if (it.scale !in 0.6f..1.3f) out += "item $i scale ${it.scale} out of range"
            if (it.alpha !in 0.3f..1f) out += "item $i alpha ${it.alpha} out of range"
            if (textZone.contains(it.x, it.y)) out += "item $i sits inside the text zone"
            val d = distanceFromGlow(it)
            if (d > 0.55f && it.alpha > 0.70f) out += "item $i is far from the glow but too bright"
            if (d < 0.20f && it.alpha < 0.85f) out += "item $i is near the glow but too dim"
        }
        DocKind.entries.filter { kind -> list.none { it.kind == kind } }.forEach { out += "kind $it is never used" }
        return out
    }
}

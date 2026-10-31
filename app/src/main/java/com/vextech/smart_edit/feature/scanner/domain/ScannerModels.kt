package com.vextech.smart_edit.feature.scanner.domain

data class NormalizedPoint(val x: Float, val y: Float) {
    init { require(x in 0f..1f && y in 0f..1f) }
}

data class CropQuad(val topLeft: NormalizedPoint, val topRight: NormalizedPoint, val bottomRight: NormalizedPoint, val bottomLeft: NormalizedPoint) {
    fun encode() = listOf(topLeft, topRight, bottomRight, bottomLeft).joinToString(";") { "${it.x},${it.y}" }
    companion object {
        val Full = CropQuad(NormalizedPoint(0f,0f), NormalizedPoint(1f,0f), NormalizedPoint(1f,1f), NormalizedPoint(0f,1f))
        fun decode(value: String): CropQuad {
            val p=value.split(';').map { pair -> pair.split(',').let { NormalizedPoint(it[0].toFloat(),it[1].toFloat()) } }
            require(p.size==4); return CropQuad(p[0],p[1],p[2],p[3])
        }
    }
}

enum class ScanFilter { ORIGINAL, GRAYSCALE, BLACK_WHITE, ENHANCE }
enum class ScanStatus { DRAFT, EXPORTED }

data class ScanPageDraft(val id:String, val position:Int, val renderedUri:String, val filter:ScanFilter, val rotationDegrees:Int, val ocrText:String?, val ocrLanguage:String?, val originalUri:String = "", val cropPoints:String = "0,0;1,0;1,1;0,1", val updatedAt:Long = 0L)

data class ScanSessionDraft(val id:String, val title:String, val status:ScanStatus, val pages:List<ScanPageDraft>, val exportedPdfUri:String?)

object ScannerPolicies {
    fun reorderedIds(current: List<String>, from: Int, to: Int): List<String> {
        require(from in current.indices && to in current.indices)
        if (from == to) return current
        return current.toMutableList().apply { add(to, removeAt(from)) }
    }
    fun normalizeRotation(degrees: Int): Int = ((degrees % 360) + 360) % 360
    fun canExport(pageCount: Int) = pageCount > 0
    /** Short date-time label used to auto-name a new scan, e.g. "31 Oct 11:36". */
    fun timestampLabel(timeMillis: Long, locale: java.util.Locale = java.util.Locale.getDefault(), zone: java.util.TimeZone = java.util.TimeZone.getDefault()): String =
        java.text.SimpleDateFormat("d MMM HH:mm", locale).apply { timeZone = zone }.format(java.util.Date(timeMillis))
    /** Pages hidden by a pending (undoable) delete never reach the UI. */
    fun <T> withoutPending(items: List<T>, pendingIds: Set<String>, idOf: (T) -> String): List<T> =
        if (pendingIds.isEmpty()) items else items.filterNot { idOf(it) in pendingIds }
}

/**
 * A page's orientation packed into the existing `rotationDegrees` column (no schema change):
 * `value = rotation (0/90/180/270) + 360 if mirrored`. Old rows (0..270) decode as "not mirrored".
 * Edits apply in this order: crop, then mirror (left-right), then rotate.
 */
object PageOrientation {
    fun encode(rotation: Int, flipped: Boolean): Int = ScannerPolicies.normalizeRotation(rotation) + if (flipped) 360 else 0
    fun rotation(value: Int): Int = ScannerPolicies.normalizeRotation(value % 360)
    fun flipped(value: Int): Boolean = value >= 360
}

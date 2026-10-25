package com.vextech.smart_edit.core.library

object TrashPolicies {
    const val DEFAULT_RETENTION_DAYS = 30
    const val MILLIS_PER_DAY = 86_400_000L

    fun autoDeleteAt(trashedAt: Long, retentionDays: Int = DEFAULT_RETENTION_DAYS): Long =
        trashedAt + retentionDays.coerceAtLeast(1) * MILLIS_PER_DAY

    fun isExpired(now: Long, autoDeleteAt: Long?): Boolean = autoDeleteAt != null && autoDeleteAt <= now

    /** Whole days remaining, rounded up, never negative. Null when the item never expires. */
    fun daysLeft(now: Long, autoDeleteAt: Long?): Int? {
        if (autoDeleteAt == null) return null
        val remaining = autoDeleteAt - now
        if (remaining <= 0) return 0
        return ((remaining + MILLIS_PER_DAY - 1) / MILLIS_PER_DAY).toInt()
    }

    fun noteExpiresAt(deletedAt: Long, retentionDays: Int = DEFAULT_RETENTION_DAYS): Long = autoDeleteAt(deletedAt, retentionDays)

    fun sortNewestFirst(items: List<TrashItem>): List<TrashItem> =
        items.sortedWith(compareByDescending<TrashItem> { it.trashedAt }.thenBy { it.id })
}

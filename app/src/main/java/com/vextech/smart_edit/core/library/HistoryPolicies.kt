package com.vextech.smart_edit.core.library

import com.vextech.smart_edit.core.model.HistoryAction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class HistoryFilter(val actions: Set<HistoryAction>?) {
    ALL(null),
    CREATED_EDITED(setOf(HistoryAction.CREATED, HistoryAction.EDITED, HistoryAction.SCANNED)),
    PROCESSED(setOf(HistoryAction.CONVERTED, HistoryAction.COMPRESSED)),
    FAVORITES(setOf(HistoryAction.FAVORITED)),
    DELETED_RESTORED(setOf(HistoryAction.DELETED, HistoryAction.RESTORED)),
    BACKUPS(setOf(HistoryAction.BACKED_UP)),
}

enum class DayBucket { TODAY, YESTERDAY, OTHER }

data class HistoryDayGroup(val bucket: DayBucket, val date: LocalDate, val entries: List<HistoryEntry>)

object HistoryPolicies {
    fun parseAction(raw: String): HistoryAction? = runCatching { HistoryAction.valueOf(raw) }.getOrNull()

    fun filter(entries: List<HistoryEntry>, filter: HistoryFilter): List<HistoryEntry> {
        val allowed = filter.actions ?: return entries
        return entries.filter { it.action in allowed }
    }

    /** Newest first. Entries inside a day keep newest first. */
    fun groupByDay(entries: List<HistoryEntry>, zone: ZoneId, today: LocalDate): List<HistoryDayGroup> {
        val sorted = entries.sortedWith(compareByDescending<HistoryEntry> { it.timestamp }.thenBy { it.id })
        val byDate = LinkedHashMap<LocalDate, MutableList<HistoryEntry>>()
        for (entry in sorted) {
            val date = Instant.ofEpochMilli(entry.timestamp).atZone(zone).toLocalDate()
            byDate.getOrPut(date) { ArrayList() }.add(entry)
        }
        return byDate.map { (date, list) ->
            val bucket = when (date) {
                today -> DayBucket.TODAY
                today.minusDays(1) -> DayBucket.YESTERDAY
                else -> DayBucket.OTHER
            }
            HistoryDayGroup(bucket, date, list)
        }
    }

    /** Only entries that can still be opened have a target. */
    fun canOpen(entry: HistoryEntry): Boolean =
        entry.uri != null && entry.action != HistoryAction.DELETED
}

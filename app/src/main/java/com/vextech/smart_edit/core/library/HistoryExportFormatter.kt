package com.vextech.smart_edit.core.library

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class HistoryExportFormat(val extension: String, val mimeType: String) {
    PDF("pdf", "application/pdf"),
    CSV("csv", "text/csv"),
    JSON("json", "application/json"),
    TXT("txt", "text/plain"),
}

/** Pure formatting for history export. The PDF writer (Android) only paginates [textLines]. */
object HistoryExportFormatter {
    fun normalize(value: String): HistoryExportFormat = when (value.trim().lowercase()) {
        "csv" -> HistoryExportFormat.CSV
        "json" -> HistoryExportFormat.JSON
        "txt", "text" -> HistoryExportFormat.TXT
        else -> HistoryExportFormat.PDF
    }

    /** Spreadsheet apps run cells that start with these characters as formulas. */
    fun neutralizeFormula(value: String): String =
        if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value

    private fun csvCell(value: String?): String = "\"" + neutralizeFormula(value.orEmpty()).replace("\"", "\"\"") + "\""

    fun csv(entries: List<HistoryEntry>): String = buildString {
        append("timestamp,action,description,name\n")
        entries.forEach { e ->
            append(e.timestamp).append(',').append(csvCell(e.rawAction)).append(',')
                .append(csvCell(e.description)).append(',').append(csvCell(e.displayName)).append('\n')
        }
    }

    fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    private fun jsonOrNull(value: String?): String = if (value == null) "null" else jsonString(value)

    fun json(entries: List<HistoryEntry>): String = buildString {
        append("[")
        entries.forEachIndexed { index, e ->
            if (index > 0) append(",")
            append("\n  {")
            append("\"timestamp\": ").append(e.timestamp).append(", ")
            append("\"action\": ").append(jsonString(e.rawAction)).append(", ")
            append("\"description\": ").append(jsonString(e.description)).append(", ")
            append("\"resourceType\": ").append(jsonOrNull(e.resourceType?.name)).append(", ")
            append("\"resourceId\": ").append(jsonOrNull(e.resourceId)).append(", ")
            append("\"uri\": ").append(jsonOrNull(e.uri)).append(", ")
            append("\"displayName\": ").append(jsonOrNull(e.displayName))
            append("}")
        }
        if (entries.isNotEmpty()) append("\n")
        append("]\n")
    }

    private val lineStamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun textLines(entries: List<HistoryEntry>, zone: ZoneId): List<String> = entries.map { e ->
        "${lineStamp.format(Instant.ofEpochMilli(e.timestamp).atZone(zone))}  ${e.rawAction}  ${e.description}"
    }

    fun text(entries: List<HistoryEntry>, zone: ZoneId): String =
        textLines(entries, zone).joinToString("\n", postfix = if (entries.isEmpty()) "" else "\n")

    /** Wraps at spaces where possible so no PDF line is cut off. */
    fun wrap(lines: List<String>, maxChars: Int): List<String> {
        require(maxChars >= 8) { "maxChars too small" }
        val out = ArrayList<String>()
        for (line in lines) {
            var rest = line
            while (rest.length > maxChars) {
                val cut = rest.lastIndexOf(' ', maxChars).takeIf { it > maxChars / 2 } ?: maxChars
                out += rest.substring(0, cut).trimEnd()
                rest = "  " + rest.substring(cut).trimStart()
            }
            out += rest
        }
        return out
    }
}

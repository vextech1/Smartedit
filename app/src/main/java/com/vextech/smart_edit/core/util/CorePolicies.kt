package com.vextech.smart_edit.core.util

object CorePolicies {
    fun ftsQuery(raw: String): String? {
        val terms = raw.trim()
            .split(Regex("\\s+"))
            .map { it.replace(Regex("[^\\p{L}\\p{N}_-]"), "") }
            .filter { it.isNotBlank() }
        return terms.takeIf { it.isNotEmpty() }?.joinToString(" AND ") { "$it*" }
    }

    fun validatePageOrder(pageCount: Int, pageOrder: List<Int>) {
        require(pageCount >= 0) { "Page count cannot be negative" }
        require(pageOrder.size == pageCount) { "Reorder must contain every page exactly once" }
        require(pageOrder.toSet() == (0 until pageCount).toSet()) { "Invalid page order" }
    }

    fun normalizeExportFormat(value: String): String = when (value.trim().lowercase()) {
        "csv" -> "csv"
        "json" -> "json"
        else -> "pdf"
    }

    fun isSafeArchiveEntry(relativePath: String): Boolean {
        if (relativePath.isBlank() || relativePath.startsWith('/') || relativePath.startsWith('\\')) return false
        val normalized = relativePath.replace('\\', '/')
        return normalized.split('/').all { segment -> segment.isNotBlank() && segment != "." && segment != ".." }
    }

    fun safeManagedName(name: String, fallback: String = "document"): String {
        val cleaned = name.trim()
            .replace(Regex("[\\u0000-\\u001f\\u007f/\\\\:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim('.', ' ')
            .take(180)
        return cleaned.ifBlank { fallback }
    }
}

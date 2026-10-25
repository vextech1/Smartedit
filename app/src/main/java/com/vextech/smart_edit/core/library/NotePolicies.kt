package com.vextech.smart_edit.core.library

object NotePolicies {
    const val MAX_TAGS = 12
    const val MAX_TAG_LENGTH = 32
    const val MAX_TITLE_LENGTH = 200

    /**
     * Parses user tag input. Separators are commas and line breaks. A leading '#' is dropped,
     * inner whitespace is collapsed, duplicates are removed ignoring case (first spelling wins),
     * and the result is capped in count and length.
     */
    fun parseTags(raw: String): List<String> {
        val seen = HashSet<String>()
        val result = ArrayList<String>()
        for (piece in raw.split(',', '\n', '\r')) {
            val tag = piece.trim().trimStart('#').trim()
                .replace(Regex("\\s+"), " ")
                .take(MAX_TAG_LENGTH)
                .trim()
            if (tag.isEmpty()) continue
            if (!seen.add(tag.lowercase())) continue
            result += tag
            if (result.size == MAX_TAGS) break
        }
        return result
    }

    /** Storage format for the `tagsCsv` column. Parsed tags never contain a comma. */
    fun encodeTags(tags: List<String>): String = parseTags(tags.joinToString(",")).joinToString(",")

    fun decodeTags(csv: String): List<String> = csv.split(',').map(String::trim).filter(String::isNotEmpty)

    fun tagsForEditing(tags: List<String>): String = tags.joinToString(", ")

    fun normalizeTitle(raw: String): String = raw.trim().take(MAX_TITLE_LENGTH)

    /** A new note with no title, body or tags is never stored. */
    fun isEmptyNote(title: String, content: String, tagsRaw: String): Boolean =
        title.isBlank() && content.isBlank() && parseTags(tagsRaw).isEmpty()

    fun sort(notes: List<Note>): List<Note> =
        notes.sortedWith(compareByDescending<Note> { it.isPinned }.thenByDescending { it.updatedAt }.thenBy { it.id })

    /** Every whitespace-separated word in [query] must appear in the title, body or a tag. */
    fun filter(notes: List<Note>, query: String, tag: String?): List<Note> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotEmpty)
        return notes.filter { note ->
            val tagOk = tag == null || note.tags.any { it.equals(tag, ignoreCase = true) }
            if (!tagOk) return@filter false
            if (words.isEmpty()) return@filter true
            val haystack = (note.title + "\n" + note.content + "\n" + note.tags.joinToString(" ")).lowercase()
            words.all { haystack.contains(it) }
        }
    }

    /** Distinct tags, most used first, then alphabetical. */
    fun allTags(notes: List<Note>): List<String> {
        val counts = LinkedHashMap<String, Pair<String, Int>>()
        notes.flatMap { it.tags }.forEach { tag ->
            val key = tag.lowercase()
            val current = counts[key]
            counts[key] = (current?.first ?: tag) to ((current?.second ?: 0) + 1)
        }
        return counts.values.sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() }).map { it.first }
    }

    fun preview(content: String, max: Int = 140): String {
        val flat = content.replace(Regex("\\s+"), " ").trim()
        return if (flat.length <= max) flat else flat.take(max).trimEnd() + "…"
    }
}

package com.vextech.smartedit.core.classlibrary

import kotlin.math.ceil

/**
 * Reads the Library's Firestore documents. This is the same format and the same rules as the EditPro site (cloud-library.ts and
 * library-structure.ts): the index is public, so anything unexpected is dropped rather than trusted, and a malformed entry never
 * reaches a Firestore path or the screen.
 */
object ClassLibraryParser {
    const val CHUNK_BYTES = 800_000
    const val MAX_LABEL = 40
    const val MAX_SHARDS = 20
    private const val CLOUD_MAX_BYTES = 20 * 1024 * 1024
    val MAX_PARTS: Int = ceil(CLOUD_MAX_BYTES / CHUNK_BYTES.toDouble()).toInt()

    val ALLOWED_EXT: Set<String> = setOf("pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "zip", "png", "jpg", "jpeg")

    private val ID = Regex("^f[a-z0-9]{8,32}$")
    private val BAD_NAME = Regex("[#?%\\\\/]")
    private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val SPACES = Regex("\\s+")

    private val MIME = mapOf(
        "pdf" to "application/pdf",
        "txt" to "text/plain",
        "zip" to "application/zip",
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "doc" to "application/msword",
        "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "ppt" to "application/vnd.ms-powerpoint",
        "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "xls" to "application/vnd.ms-excel",
        "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    )

    fun mimeOf(ext: String): String = MIME[ext] ?: "application/octet-stream"

    /** `library/index` is the first index document, the others are `library/index-1`, `library/index-2`, ... */
    fun shardDocName(n: Int): String = if (n == 0) "index" else "index-$n"

    /** The pieces of one file are the documents `libraryChunks/<file id>/parts/0000`, `0001`, ... */
    fun partId(i: Int): String = i.toString().padStart(4, '0')

    /** How many index documents the first one says there are. Anything unexpected means 1. */
    fun parseShardCount(first: Map<String, Any?>?): Int {
        val n = (first?.get("shards") as? Number)?.toDouble() ?: return 1
        return if (n % 1.0 == 0.0 && n >= 1 && n <= MAX_SHARDS) n.toInt() else 1
    }

    /** What an admin typed as a label: extra spaces removed and the length capped. Empty when nothing useful was typed. */
    fun cleanLabel(value: Any?): String = (value as? String)?.replace(SPACES, " ")?.trim()?.take(MAX_LABEL).orEmpty()

    /** "week_1-intro.pdf" becomes "Week 1 Intro". */
    fun titleFromFilename(name: String): String {
        val dot = name.lastIndexOf('.')
        val base = (if (dot > 0) name.substring(0, dot) else name).replace(Regex("[_-]+"), " ").replace(SPACES, " ").trim()
        return base.split(' ').joinToString(" ") { word ->
            if (word.isNotEmpty() && word == word.lowercase()) word.replaceFirstChar { it.uppercase() } else word
        }
    }

    /** The catalog: sections, each with classes, each with subjects. Entries with a bad id or name are skipped. */
    fun parseStructure(data: Map<String, Any?>?): List<LibrarySection> {
        val sections = data?.get("sections") as? List<*> ?: return emptyList()
        return sections.mapNotNull { rawSection ->
            val section = rawSection as? Map<*, *> ?: return@mapNotNull null
            val (sectionId, sectionName) = entity(section) ?: return@mapNotNull null
            val classes = (section["classes"] as? List<*>).orEmpty().mapNotNull { rawClass ->
                val cls = rawClass as? Map<*, *> ?: return@mapNotNull null
                val (classId, className) = entity(cls) ?: return@mapNotNull null
                val subjects = (cls["subjects"] as? List<*>).orEmpty().mapNotNull { rawSubject ->
                    val subject = rawSubject as? Map<*, *> ?: return@mapNotNull null
                    entity(subject)?.let { (id, name) -> LibrarySubject(id, name) }
                }
                LibraryClass(classId, className, subjects)
            }
            LibrarySection(sectionId, sectionName, classes)
        }
    }

    private fun entity(map: Map<*, *>): Pair<String, String>? {
        val id = map["id"] as? String ?: return null
        val name = map["name"] as? String ?: return null
        if (!ID.matches(id) || name.isBlank() || name.trim().length > 80 || BAD_NAME.containsMatchIn(name)) return null
        return id to name.trim()
    }

    /** The files of one index document. [shard] is stored on each file so a caller knows where it came from. */
    fun parseIndex(data: Map<String, Any?>?, shard: Int = 0): List<CloudFile> {
        val files = data?.get("files") as? Map<*, *> ?: return emptyList()
        val out = ArrayList<CloudFile>()
        for ((rawId, raw) in files) {
            val id = rawId as? String ?: continue
            if (!ID.matches(id)) continue
            val e = raw as? Map<*, *> ?: continue
            val name = e["name"] as? String ?: continue
            val ext = e["ext"] as? String ?: continue
            if (name.isEmpty() || BAD_NAME.containsMatchIn(name) || ext !in ALLOWED_EXT) continue
            val section = e["section"] as? String
            val cls = e["cls"] as? String
            if (section.isNullOrEmpty() || cls.isNullOrEmpty()) continue
            val category = LibraryCategory.fromId(e["category"] as? String) ?: continue
            val subjectRaw = e["subject"]
            if (subjectRaw != null && subjectRaw !is String) continue
            val size = (e["size"] as? Number)?.toDouble() ?: continue
            if (size.isNaN() || size.isInfinite() || size < 0) continue
            val parts = (e["parts"] as? Number)?.toDouble() ?: continue
            if (parts % 1.0 != 0.0 || parts < 1 || parts > MAX_PARTS + 1) continue
            val title = (e["title"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: titleFromFilename(name)
            val added = (e["added"] as? String)?.takeIf { DATE.matches(it) }.orEmpty()
            val label = if (category == LibraryCategory.OTHER) cleanLabel(e["label"]).takeIf { it.isNotEmpty() } else null
            out += CloudFile(
                id = id, name = name, title = title, size = size.toLong(), ext = ext,
                sectionId = section, classId = cls, subjectId = subjectRaw as? String,
                category = category, label = label, added = added, parts = parts.toInt(), shard = shard,
            )
        }
        return out
    }

    /** Joins the downloaded pieces of a file, in order. */
    fun joinChunks(chunks: List<ByteArray>): ByteArray {
        val out = ByteArray(chunks.sumOf { it.size })
        var at = 0
        for (chunk in chunks) {
            chunk.copyInto(out, at)
            at += chunk.size
        }
        return out
    }
}

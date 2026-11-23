package com.vextech.editpro.core.classlibrary

import java.security.MessageDigest
import java.time.LocalDate
import java.util.Random

/** A study file shared by a signed-in student (`userFiles/<id>`), in the same shape the EditPro site writes and reads. */
data class UserFile(
    val id: String,
    val uploader: String,
    val title: String,
    val name: String,
    val size: Long,
    val ext: String,
    val sectionId: String,
    val classId: String,
    val subjectId: String?,
    val category: LibraryCategory,
    val label: String?,
    /** `yyyy-MM-dd`, or empty. */
    val added: String,
    val parts: Int,
    val sha: String?,
    /** `uploading`, `live` or `pulled`. */
    val status: String,
    val pulledReason: String?,
    val cleaned: Boolean,
)

/** What the uploader chooses. [subjectId] null means "no subject". */
data class UploadMeta(
    val name: String,
    val title: String?,
    val sectionId: String,
    val classId: String,
    val subjectId: String?,
    val category: LibraryCategory,
    val label: String?,
)

/** A message to the uploader that an admin pulled their file (`users/<uid>/notices/<id>`). */
data class Notice(val id: String, val title: String, val reason: String, val at: String)

sealed interface Check {
    data object Ok : Check
    data class Refused(val reason: String) : Check
}

/** The rules for student and admin uploads: the same limits as the site's user-library.ts, cloud-library.ts and moderation.ts. */
object UserLibraryPolicies {
    const val USER_MAX_BYTES = 5 * 1024 * 1024
    const val USER_MIN_BYTES = 10_000
    const val ADMIN_MAX_BYTES = 20 * 1024 * 1024
    const val MAX_TITLE = 120
    const val MAX_REASON = 500
    const val STALE_MS = 60L * 60 * 1000
    const val DAY_MS = 24L * 3600 * 1000
    const val SPARK_STORAGE_BYTES = 1024L * 1024 * 1024
    const val STORAGE_LIMIT_BYTES = (SPARK_STORAGE_BYTES * 9) / 10
    const val PART_OVERHEAD_BYTES = 1024
    const val ENTRY_OVERHEAD_BYTES = 2048
    const val INDEX_SHARD_BYTES = 600_000

    val USER_EXT: List<String> = listOf("pdf", "docx", "pptx", "xlsx", "txt", "png", "jpg", "jpeg")
    private val BAD_NAME = Regex("[#?%\\\\/]")
    private val ID = Regex("^f[a-z0-9]{8,32}$")
    private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val SHA = Regex("^[0-9a-f]{64}$")

    val REASON_CHOICES: List<String> = listOf(
        "It is not yours to share: it appears to be copied from a book or someone else’s work.",
        "It is not study material.",
        "It is a duplicate or spam.",
        "It contains content that is not appropriate for students.",
        "The file is damaged or cannot be opened.",
    )

    fun extOf(name: String): String = name.substringAfterLast('.', "").lowercase()

    fun todayLocal(): String = LocalDate.now().toString()

    /** A fresh id that is safe in a Firestore path: `f` plus 16 letters or digits. */
    fun newFileId(random: Random = Random()): String {
        val bytes = ByteArray(10).also(random::nextBytes)
        return "f" + bytes.joinToString("") { (it.toInt() and 0xff).toString(36).padStart(2, '0').takeLast(2) }.take(16)
    }

    private fun mb(n: Long): String = "%.1f MB".format(n / 1048576.0)

    /** Name and extension rules shared by both kinds of upload. */
    private fun checkName(name: String, allowed: Collection<String>): Check {
        if (name.isBlank()) return Check.Refused("it has no name")
        if (BAD_NAME.containsMatchIn(name)) return Check.Refused("the name has a # ? % / or \\ in it. Rename it first")
        if (name.startsWith(".") || name.startsWith("_")) return Check.Refused("names starting with . or _ are not allowed. Rename it first")
        val ext = extOf(name)
        if (ext !in allowed) return Check.Refused(".${ext.ifEmpty { "?" }} files are not allowed (use ${allowed.joinToString(", ") { ".$it" }})")
        return Check.Ok
    }

    fun checkUserUpload(name: String, size: Long): Check {
        val base = checkName(name, USER_EXT)
        if (base is Check.Refused) return base
        if (size < USER_MIN_BYTES) return Check.Refused("it is too small to be a study document (under 10 KB)")
        if (size > USER_MAX_BYTES) return Check.Refused("${mb(size)} is over the ${mb(USER_MAX_BYTES.toLong())} limit for student uploads. Compress it first")
        return Check.Ok
    }

    fun checkAdminUpload(name: String, size: Long): Check {
        val base = checkName(name, ClassLibraryParser.ALLOWED_EXT)
        if (base is Check.Refused) return base
        if (size <= 0) return Check.Refused("it is empty")
        if (size > ADMIN_MAX_BYTES) return Check.Refused("${mb(size)} is over the ${mb(ADMIN_MAX_BYTES.toLong())} limit for uploads. Put it on Google Drive and add a link instead")
        return Check.Ok
    }

    /** Whether the first bytes of a file match what its extension says (so a renamed file is refused). */
    fun looksLikeExt(ext: String, head: ByteArray): Boolean {
        fun starts(vararg b: Int) = b.indices.all { i -> i < head.size && (head[i].toInt() and 0xff) == b[i] }
        return when (ext) {
            "pdf" -> starts(0x25, 0x50, 0x44, 0x46)
            "png" -> starts(0x89, 0x50, 0x4e, 0x47)
            "jpg", "jpeg" -> starts(0xff, 0xd8, 0xff)
            "docx", "pptx", "xlsx" -> starts(0x50, 0x4b, 0x03, 0x04)
            "txt" -> head.take(512).none { it.toInt() == 0 }
            else -> false
        }
    }

    fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun splitChunks(bytes: ByteArray, size: Int = ClassLibraryParser.CHUNK_BYTES): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        var i = 0
        while (i < bytes.size) {
            out += bytes.copyOfRange(i, minOf(i + size, bytes.size))
            i += size
        }
        return out
    }

    fun userMaxParts(): Int = Math.ceil(USER_MAX_BYTES / ClassLibraryParser.CHUNK_BYTES.toDouble()).toInt()

    /** The document written to `userFiles/<id>`. Only the fields the Firestore rules allow. */
    fun buildUserEntry(uploader: String, meta: UploadMeta, size: Long, parts: Int, sha: String?, added: String = todayLocal()): Map<String, Any> {
        val label = ClassLibraryParser.cleanLabel(meta.label)
        return buildMap {
            put("uploader", uploader)
            put("title", (meta.title?.trim().orEmpty().ifEmpty { ClassLibraryParser.titleFromFilename(meta.name) }).take(MAX_TITLE))
            put("name", meta.name)
            put("size", size)
            put("ext", extOf(meta.name))
            put("section", meta.sectionId)
            put("cls", meta.classId)
            meta.subjectId?.takeIf { it.isNotEmpty() }?.let { put("subject", it) }
            put("category", meta.category.id)
            if (meta.category == LibraryCategory.OTHER && label.isNotEmpty()) put("label", label)
            put("added", added)
            put("parts", parts.toLong())
            if (sha != null) put("sha", sha)
            put("status", "uploading")
        }
    }

    /** The entry written into a library index document for an admin upload. */
    fun buildLibraryEntry(meta: UploadMeta, size: Long, parts: Int, added: String = todayLocal()): Map<String, Any> {
        val label = ClassLibraryParser.cleanLabel(meta.label)
        return buildMap {
            put("name", meta.name)
            put("title", (meta.title?.trim().orEmpty().ifEmpty { ClassLibraryParser.titleFromFilename(meta.name) }))
            put("size", size)
            put("ext", extOf(meta.name))
            put("section", meta.sectionId)
            put("cls", meta.classId)
            meta.subjectId?.takeIf { it.isNotEmpty() }?.let { put("subject", it) }
            put("category", meta.category.id)
            if (meta.category == LibraryCategory.OTHER && label.isNotEmpty()) put("label", label)
            put("added", added)
            put("parts", parts.toLong())
        }
    }

    /** An unfinished upload more than a day old is abandoned: it is cleaned up the next time its owner opens their list. */
    fun staleUploads(files: List<UserFile>, nowMs: Long = System.currentTimeMillis()): List<UserFile> =
        files.filter { f ->
            f.status == "uploading" && f.added.isNotEmpty() &&
                runCatching { LocalDate.parse(f.added).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
                    .getOrNull()?.let { nowMs - it > STALE_MS + DAY_MS } == true
        }

    /** Whether a new file fits under the storage limit once its pieces and entry are counted. */
    fun checkLibraryRoom(adminUsed: Long, studentBytes: Long, studentCount: Long, newSize: Long, limit: Long = STORAGE_LIMIT_BYTES, perFileOverhead: Long = (ENTRY_OVERHEAD_BYTES + 2 * PART_OVERHEAD_BYTES).toLong()): Check {
        val used = adminUsed + studentBytes + studentCount * perFileOverhead
        return if (used + newSize + perFileOverhead <= limit) Check.Ok else Check.Refused("the Library is full right now. Please try again later")
    }

    /** About how much storage the listed files use (their pieces and index entries, counted generously). */
    fun storageUsed(files: List<CloudFile>): Long = files.filter { !it.student }.sumOf { f ->
        f.size + maxOf(1L, Math.ceil(f.size / ClassLibraryParser.CHUNK_BYTES.toDouble()).toLong()) * PART_OVERHEAD_BYTES + ENTRY_OVERHEAD_BYTES
    }

    /** The index document a new entry goes in: the first with room, or the next new one. Null when the index is full. */
    fun chooseShard(sizes: List<Int>, entryBytes: Int): Int? {
        sizes.forEachIndexed { i, size -> if (size + entryBytes <= INDEX_SHARD_BYTES) return i }
        return if (sizes.size >= ClassLibraryParser.MAX_SHARDS) null else sizes.size
    }

    fun entryBytesFor(name: String): Int = 500 + name.length * 3

    fun checkReason(text: String): Pair<Boolean, String> {
        val reason = text.replace(Regex("\\s+"), " ").trim()
        return when {
            reason.length < 5 -> false to "Write a reason (at least a few words), so the student knows why it was removed."
            reason.length > MAX_REASON -> false to "Keep the reason under $MAX_REASON characters."
            else -> true to reason
        }
    }

    /** Reads a `userFiles` document. Anything unexpected returns null, so a malformed record never breaks a screen. */
    fun parseUserFile(id: String, data: Map<String, Any?>?): UserFile? {
        if (!ID.matches(id) || data == null) return null
        val uploader = data["uploader"] as? String ?: return null
        val name = data["name"] as? String ?: return null
        val ext = data["ext"] as? String ?: return null
        if (uploader.isEmpty() || name.isEmpty() || ext !in USER_EXT || extOf(name) != ext) return null
        val section = (data["section"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
        val cls = (data["cls"] as? String)?.takeIf { it.isNotEmpty() } ?: return null
        val category = LibraryCategory.fromId(data["category"] as? String) ?: return null
        val subject = data["subject"]
        if (subject != null && subject !is String) return null
        val size = (data["size"] as? Number)?.toLong()?.takeIf { it >= 0 } ?: return null
        val parts = (data["parts"] as? Number)?.toInt()?.takeIf { it in 1..(userMaxParts() + 1) } ?: return null
        val status = (data["status"] as? String)?.takeIf { it == "uploading" || it == "live" || it == "pulled" } ?: return null
        val label = if (category == LibraryCategory.OTHER) ClassLibraryParser.cleanLabel(data["label"]).takeIf { it.isNotEmpty() } else null
        return UserFile(
            id = id, uploader = uploader,
            title = (data["title"] as? String)?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_TITLE) ?: ClassLibraryParser.titleFromFilename(name),
            name = name, size = size, ext = ext, sectionId = section, classId = cls, subjectId = subject as String?, category = category, label = label,
            added = (data["added"] as? String)?.takeIf { DATE.matches(it) }.orEmpty(), parts = parts,
            sha = (data["sha"] as? String)?.takeIf { SHA.matches(it) }, status = status,
            pulledReason = (data["pulledReason"] as? String)?.take(MAX_REASON), cleaned = data["cleaned"] == true,
        )
    }

    /** A student file as a Library list entry, so it appears next to the admin's files. */
    fun toCloudFile(f: UserFile): CloudFile = CloudFile(
        id = f.id, name = f.name, title = f.title, size = f.size, ext = f.ext, sectionId = f.sectionId, classId = f.classId, subjectId = f.subjectId,
        category = f.category, label = f.label, added = f.added, parts = f.parts, shard = -1, student = true, uploader = f.uploader,
    )

    fun parseNotice(id: String, data: Map<String, Any?>?): Notice? {
        if (data == null || data["kind"] != "pulled") return null
        val title = (data["title"] as? String)?.take(120) ?: return null
        val reason = (data["reason"] as? String)?.take(MAX_REASON) ?: return null
        return Notice(id, title, reason, data["at"] as? String ?: "")
    }
}

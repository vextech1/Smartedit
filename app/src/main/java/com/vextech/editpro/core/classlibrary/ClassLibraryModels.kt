package com.vextech.editpro.core.classlibrary

/** The built-in file categories. An admin's own label is kept with [OTHER] (see [CloudFile.label]). */
enum class LibraryCategory(val id: String) {
    NOTES("notes"),
    ASSIGNMENTS("assignments"),
    PAST_PAPERS("past-papers"),
    OTHER("other"),
    ;

    companion object {
        fun fromId(id: String?): LibraryCategory? = entries.firstOrNull { it.id == id }
    }
}

data class LibrarySubject(val id: String, val name: String)
data class LibraryClass(val id: String, val name: String, val subjects: List<LibrarySubject>)
data class LibrarySection(val id: String, val name: String, val classes: List<LibraryClass>)

/** One file in the Library's index (`library/index`, `library/index-1`, ...). [shard] says which index document lists it. */
data class CloudFile(
    val id: String,
    val name: String,
    val title: String,
    val size: Long,
    val ext: String,
    val sectionId: String,
    val classId: String,
    val subjectId: String?,
    val category: LibraryCategory,
    /** What an admin typed for the "other" category, or null. */
    val label: String?,
    /** `yyyy-MM-dd`, or empty when the entry has no valid date. */
    val added: String,
    val parts: Int,
    val shard: Int,
    /** True for a file shared by a student (`userFiles`); its pieces are in `userChunks`. */
    val student: Boolean = false,
    /** The uploader's account id for a student file, so their own files are never counted against them. */
    val uploader: String? = null,
)

/** [partial] is true when some index documents could not be read, so some files may be missing from [files]. */
data class LibraryData(val sections: List<LibrarySection>, val files: List<CloudFile>, val partial: Boolean)

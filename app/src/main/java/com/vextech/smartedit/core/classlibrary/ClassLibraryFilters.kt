package com.vextech.smartedit.core.classlibrary

/** Choosing, searching and ordering the files shown on the Library screen. */
object ClassLibraryFilters {
    const val ALL = "all"

    /** One chip per category present in a list of files. [category] is null for an admin's own label, which is shown as [label]. */
    data class CategoryChip(val key: String, val category: LibraryCategory?, val label: String?, val count: Int)

    /** The built-in id, or `other:<label>` (lower-case) for each custom label, so "Lab manuals" and "lab manuals" are one chip. */
    fun categoryKey(file: CloudFile): String =
        if (file.category == LibraryCategory.OTHER && !file.label.isNullOrBlank()) "other:${file.label.lowercase()}" else file.category.id

    /** Notes, Assignments and Past papers first, then each custom label A to Z, then plain "Other", counting only what is there. */
    fun chips(files: List<CloudFile>): List<CategoryChip> {
        val counts = LinkedHashMap<String, CategoryChip>()
        for (file in files) {
            val key = categoryKey(file)
            val old = counts[key]
            counts[key] = old?.copy(count = old.count + 1) ?: CategoryChip(
                key = key,
                category = if (key.startsWith("other:")) null else file.category,
                label = if (key.startsWith("other:")) file.label else null,
                count = 1,
            )
        }
        fun rank(chip: CategoryChip): Int = when (chip.category) {
            LibraryCategory.NOTES -> 0
            LibraryCategory.ASSIGNMENTS -> 1
            LibraryCategory.PAST_PAPERS -> 2
            null -> 3
            LibraryCategory.OTHER -> 4
        }
        return counts.values.sortedWith(compareBy<CategoryChip>({ rank(it) }).thenBy { it.label?.lowercase().orEmpty() })
    }

    /** Files of one section and class, optionally one subject. */
    fun inScope(files: List<CloudFile>, sectionId: String?, classId: String?, subjectId: String?): List<CloudFile> =
        files.filter { f ->
            f.sectionId == sectionId && f.classId == classId && (subjectId == null || f.subjectId == subjectId)
        }

    /** Every word typed must appear in the title or file name, ignoring capitals. [categoryKey] of [ALL] keeps every category. */
    fun filter(files: List<CloudFile>, categoryKey: String, query: String): List<CloudFile> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return files.filter { f ->
            if (categoryKey != ALL && categoryKey(f) != categoryKey) return@filter false
            val haystack = "${f.title} ${f.name}".lowercase()
            words.all { haystack.contains(it) }
        }
    }

    /** Newest first, then by title with numbers in order ("Week 2" before "Week 10"), so the list does not shuffle between loads. */
    fun sorted(files: List<CloudFile>): List<CloudFile> =
        files.sortedWith(compareByDescending<CloudFile> { it.added }.then { a, b -> naturalCompare(a.title, b.title) })

    internal fun naturalCompare(a: String, b: String): Int {
        val x = a.lowercase()
        val y = b.lowercase()
        var i = 0
        var j = 0
        while (i < x.length && j < y.length) {
            if (x[i].isDigit() && y[j].isDigit()) {
                var ei = i
                while (ei < x.length && x[ei].isDigit()) ei++
                var ej = j
                while (ej < y.length && y[ej].isDigit()) ej++
                val numA = x.substring(i, ei).trimStart('0')
                val numB = y.substring(j, ej).trimStart('0')
                if (numA.length != numB.length) return numA.length - numB.length
                val c = numA.compareTo(numB)
                if (c != 0) return c
                i = ei
                j = ej
            } else {
                if (x[i] != y[j]) return x[i].compareTo(y[j])
                i++
                j++
            }
        }
        return (x.length - i) - (y.length - j)
    }
}

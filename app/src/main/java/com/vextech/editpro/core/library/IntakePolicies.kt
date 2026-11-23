package com.vextech.editpro.core.library

/** A file that arrived from the picker on Home, or from another app (Share, Open with). */
data class IncomingFile(val uri: String, val name: String, val mimeType: String)

/** Which tool opens first for what arrived. */
enum class IntakeTarget { EDIT_PDF, EDIT_IMAGE, IMAGES_TO_PDF, NONE }

/** What to tell the person, if anything. */
enum class IntakeNote { NONE, MIXED, NOTHING_SUPPORTED, EXTRA_PDFS }

data class IntakePlan(val target: IntakeTarget, val files: List<IncomingFile>, val skipped: List<IncomingFile>, val note: IntakeNote)

/**
 * The same rules as the EditPro site's routeFor / planIncoming: only PDFs and pictures are accepted;
 * a PDF opens the PDF editor, one picture opens the photo editor, several pictures become a PDF;
 * PDFs and pictures together are refused so nothing is silently dropped.
 */
object IntakePolicies {
    fun plan(picked: List<IncomingFile>): IntakePlan {
        val unique = picked.distinctBy { it.uri }
        val pdfs = mutableListOf<IncomingFile>()
        val images = mutableListOf<IncomingFile>()
        val skipped = mutableListOf<IncomingFile>()
        for (file in unique) {
            when (FileActionPolicies.kindOf(file.name, file.mimeType)) {
                FileKind.PDF -> pdfs += file
                FileKind.IMAGE -> images += file
                FileKind.OTHER -> skipped += file
            }
        }
        return when {
            pdfs.isNotEmpty() && images.isNotEmpty() -> IntakePlan(IntakeTarget.NONE, emptyList(), emptyList(), IntakeNote.MIXED)
            pdfs.isNotEmpty() -> IntakePlan(
                IntakeTarget.EDIT_PDF, listOf(pdfs.first()), skipped,
                if (pdfs.size > 1) IntakeNote.EXTRA_PDFS else IntakeNote.NONE,
            )
            images.size == 1 -> IntakePlan(IntakeTarget.EDIT_IMAGE, images, skipped, IntakeNote.NONE)
            images.size > 1 -> IntakePlan(IntakeTarget.IMAGES_TO_PDF, images, skipped, IntakeNote.NONE)
            else -> IntakePlan(IntakeTarget.NONE, emptyList(), skipped, IntakeNote.NOTHING_SUPPORTED)
        }
    }
}

package com.vextech.smartedit.ui.navigation

import androidx.navigation3.runtime.NavKey
import com.vextech.smartedit.R
import com.vextech.smartedit.core.library.IncomingFile
import com.vextech.smartedit.core.library.IntakeNote
import com.vextech.smartedit.core.library.IntakePlan
import com.vextech.smartedit.core.library.IntakeTarget
import com.vextech.smartedit.core.ui.UiText

/** The tool screen that opens first for [this] plan, or null when nothing can be opened. */
fun IntakePlan.toRoute(): NavKey? = when (target) {
    IntakeTarget.EDIT_PDF -> PdfToolsRoute(files.first().uri)
    IntakeTarget.EDIT_IMAGE -> ImageEditorRoute(files.first().uri)
    IntakeTarget.IMAGES_TO_PDF -> ConverterRoute(uris = files.map(IncomingFile::uri))
    IntakeTarget.NONE -> null
}

/** What to tell the person about the plan: turned-away files, a mixed selection, or extra PDFs that were not opened. */
fun IntakePlan.message(): UiText? = when {
    note == IntakeNote.MIXED -> UiText.res(R.string.intake_mixed)
    note == IntakeNote.NOTHING_SUPPORTED && skipped.isEmpty() -> UiText.res(R.string.intake_nothing)
    note == IntakeNote.NOTHING_SUPPORTED -> UiText.res(R.string.intake_nothing_named, skippedNames())
    skipped.isNotEmpty() -> UiText.res(R.string.intake_skipped, skippedNames())
    note == IntakeNote.EXTRA_PDFS -> UiText.res(R.string.intake_extra_pdfs, files.first().name)
    else -> null
}

private fun IntakePlan.skippedNames(): String {
    val names = skipped.take(3).joinToString(", ") { it.name }
    return if (skipped.size > 3) "$names +${skipped.size - 3}" else names
}

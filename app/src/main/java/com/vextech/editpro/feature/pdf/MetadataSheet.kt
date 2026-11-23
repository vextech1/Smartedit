package com.vextech.editpro.feature.pdf

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vextech.editpro.R
import com.vextech.editpro.core.ui.sheet.SheetCheck
import com.vextech.editpro.core.ui.sheet.SheetTextField
import com.vextech.editpro.core.ui.sheet.ToolSheet
import com.vextech.editpro.core.ui.sheet.rememberSheetEdits
import com.vextech.editpro.feature.pdf.domain.PdfDocumentMetadata

/**
 * Document properties as a tool sheet: edits stay unapplied until the tick, Reset puts the original values back,
 * and the top bar's Undo and Redo step through the edits while the sheet is open.
 */
@Composable
fun MetadataSheet(original: PdfDocumentMetadata, onCancel: () -> Unit, onApply: (PdfDocumentMetadata) -> Unit) {
    val edits = rememberSheetEdits(original)
    val draft = edits.value
    ToolSheet(
        title = stringResource(R.string.pdf_properties),
        onCancel = onCancel,
        onReset = edits::reset,
        canReset = edits.changed,
        onApply = { onApply(draft.asApplied()) },
        history = edits,
    ) {
        val off = draft.removeAll
        SheetTextField(stringResource(R.string.pdf_title), draft.title, { edits.editMerged(draft.copy(title = it)) }, enabled = !off)
        SheetTextField(stringResource(R.string.pdf_author), draft.author, { edits.editMerged(draft.copy(author = it)) }, enabled = !off)
        SheetTextField(stringResource(R.string.pdf_subject), draft.subject, { edits.editMerged(draft.copy(subject = it)) }, enabled = !off)
        SheetTextField(stringResource(R.string.pdf_keywords), draft.keywords, { edits.editMerged(draft.copy(keywords = it)) }, enabled = !off)
        SheetTextField(stringResource(R.string.pdf_creator), draft.creator, { edits.editMerged(draft.copy(creator = it)) }, enabled = !off)
        SheetTextField(stringResource(R.string.pdf_producer), draft.producer, { edits.editMerged(draft.copy(producer = it)) }, enabled = !off)
        SheetCheck(stringResource(R.string.pdf_remove_all_metadata), draft.removeAll, { edits.edit(draft.copy(removeAll = it)) })
    }
}

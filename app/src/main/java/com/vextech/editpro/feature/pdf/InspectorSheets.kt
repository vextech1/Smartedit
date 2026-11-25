package com.vextech.editpro.feature.pdf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.vextech.editpro.R
import com.vextech.editpro.core.ui.sheet.SheetCheck
import com.vextech.editpro.core.ui.sheet.SheetHint
import com.vextech.editpro.core.ui.sheet.SheetTextField
import com.vextech.editpro.core.ui.sheet.ToolSheet
import com.vextech.editpro.feature.pdf.domain.PdfBookmark
import com.vextech.editpro.feature.pdf.domain.PdfFormEdits
import com.vextech.editpro.feature.pdf.domain.PdfFormField

/**
 * Fill in the PDF's form fields. Nothing changes until the tick, which sets every edited field in one step, so one Undo
 * takes them all back. "Flatten" turns the fields into fixed text afterwards. Typed text is held in memory only.
 */
@Composable
fun FormsSheet(
    fields: List<PdfFormField>,
    busy: Boolean,
    onCancel: () -> Unit,
    onApply: (changes: Map<String, String>, flatten: Boolean) -> Unit,
) {
    val edits = remember { mutableStateMapOf<String, String>() }
    var flatten by rememberSaveable { mutableStateOf(false) }
    val changes = PdfFormEdits.changed(fields, edits)

    ToolSheet(
        title = stringResource(R.string.pdf_forms),
        onCancel = onCancel,
        onReset = { edits.clear(); flatten = false },
        canReset = changes.isNotEmpty() || flatten,
        onApply = {
            onApply(changes, flatten)
            onCancel()
        },
        busy = busy,
    ) {
        if (fields.isEmpty()) {
            SheetHint(stringResource(R.string.pdf_no_forms))
        } else {
            fields.forEach { field ->
                key(field.name) {
                    SheetTextField(
                        label = field.name,
                        value = edits[field.name] ?: field.value,
                        onChange = { edits[field.name] = it },
                        enabled = !field.readOnly,
                    )
                }
            }
            SheetCheck(stringResource(R.string.pdf_flatten_forms), flatten, { flatten = it })
            SheetHint(stringResource(R.string.pdf_flatten_forms_hint))
        }
    }
}

/**
 * The PDF's bookmarks: add one for the page being viewed, jump to one by tapping it, remove one. Each add or remove
 * takes effect at once; the tick only closes the sheet.
 */
@Composable
fun BookmarksSheet(
    bookmarks: List<PdfBookmark>,
    currentPage: Int,
    busy: Boolean,
    onCancel: () -> Unit,
    onAdd: (title: String, page: Int) -> Unit,
    onRemove: (id: String) -> Unit,
    onJump: (page: Int) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }

    ToolSheet(
        title = stringResource(R.string.pdf_bookmarks),
        onCancel = onCancel,
        onReset = { title = "" },
        canReset = title.isNotEmpty(),
        busy = busy,
    ) {
        SheetHint(stringResource(R.string.pdf_bookmark_adds_to, currentPage + 1))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SheetTextField(
                label = stringResource(R.string.pdf_bookmark_title),
                value = title,
                onChange = { title = it },
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { if (title.isNotBlank()) { onAdd(title.trim(), currentPage); title = "" } }, enabled = title.isNotBlank() && !busy) {
                Text(stringResource(R.string.pdf_add))
            }
        }
        if (bookmarks.isEmpty()) {
            SheetHint(stringResource(R.string.pdf_no_bookmarks))
        } else {
            bookmarks.forEach { item ->
                key(item.id) {
                    ListItem(
                        headlineContent = { Text(item.title) },
                        supportingContent = { Text(stringResource(R.string.pdf_page_number, item.pageIndex + 1)) },
                        trailingContent = { TextButton(onClick = { onRemove(item.id) }) { Text(stringResource(R.string.pdf_remove)) } },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.fillMaxWidth().clickable { onJump(item.pageIndex) },
                    )
                }
            }
        }
    }
}

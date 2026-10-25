package com.vextech.smart_edit.ui.shell

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vextech.smart_edit.R

/** The three views inside the Files tab. */
enum class LibrarySegment { FILES, NOTES, FAVORITES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySegments(selected: LibrarySegment, onSelect: (LibrarySegment) -> Unit) {
    val entries = LibrarySegment.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        entries.forEachIndexed { index, segment ->
            SegmentedButton(
                selected = segment == selected,
                onClick = { onSelect(segment) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = entries.size),
            ) {
                Text(
                    stringResource(
                        when (segment) {
                            LibrarySegment.FILES -> R.string.files
                            LibrarySegment.NOTES -> R.string.notes
                            LibrarySegment.FAVORITES -> R.string.favorites
                        }
                    )
                )
            }
        }
    }
}

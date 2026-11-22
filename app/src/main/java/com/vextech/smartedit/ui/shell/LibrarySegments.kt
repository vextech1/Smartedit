package com.vextech.smartedit.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vextech.smartedit.R
import com.vextech.smartedit.feature.home.editProColors

/** The three views inside the Files tab. */
enum class LibrarySegment { FILES, NOTES, FAVORITES }

/** EditPro's My files tabs: plain labels with a 3dp accent line under the chosen one. */
@Composable
fun LibrarySegments(selected: LibrarySegment, onSelect: (LibrarySegment) -> Unit) {
    val c = editProColors()
    Column(Modifier.fillMaxWidth().background(c.card)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.Start) {
            LibrarySegment.entries.forEach { segment ->
                val on = segment == selected
                Column(Modifier.clickable { onSelect(segment) }.padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(
                            when (segment) {
                                LibrarySegment.FILES -> R.string.files
                                LibrarySegment.NOTES -> R.string.notes
                                LibrarySegment.FAVORITES -> R.string.favorites
                            },
                        ),
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = if (on) c.accent else c.muted,
                        fontSize = 15.sp,
                        fontWeight = if (on) FontWeight(650) else FontWeight.Normal,
                    )
                    Spacer(Modifier.fillMaxWidth().height(3.dp).background(if (on) c.accent else Color.Transparent))
                }
            }
        }
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    }
}

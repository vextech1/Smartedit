package com.vextech.editpro.core.ui.sheet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vextech.editpro.R

/** On phones a sheet never covers more than this share of the screen, so the page stays visible above it. */
const val SHEET_MAX_HEIGHT_FRACTION = 0.46f

/**
 * One tool's settings, as the site's bottom sheet: a handle, then `Reset | Title | tick`.
 * The tick applies ([onApply]); the handle or the Back button closes without applying ([onCancel]).
 * Pass [history] to make the top bar's Undo and Redo step through the sheet's unapplied edits first.
 */
@Composable
fun ToolSheet(
    title: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onReset: (() -> Unit)? = null,
    canReset: Boolean = true,
    onApply: (() -> Unit)? = null,
    busy: Boolean = false,
    history: SheetHistory? = null,
    content: @Composable () -> Unit,
) {
    BackHandler(onBack = onCancel)
    if (history != null) RegisterSheetHistory(history)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * SHEET_MAX_HEIGHT_FRACTION).dp
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(max = maxHeight),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp)) {
            val closeLabel = stringResource(R.string.sheet_close_without_applying, title)
            Box(
                Modifier.fillMaxWidth().height(44.dp).clickable(onClick = onCancel).semantics { contentDescription = closeLabel },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(44.dp).height(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (onReset != null) TextButton(onClick = onReset, enabled = canReset) { Text(stringResource(R.string.sheet_reset)) }
                }
                Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    val applyLabel = stringResource(R.string.sheet_apply, title)
                    IconButton(onClick = { (onApply ?: onCancel)() }, enabled = !busy) {
                        Icon(Icons.Rounded.Check, applyLabel, Modifier.size(26.dp))
                    }
                }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 6.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                content()
            }
        }
    }
}

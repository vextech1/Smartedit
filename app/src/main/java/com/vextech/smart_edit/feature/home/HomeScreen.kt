package com.vextech.smart_edit.feature.home

import android.text.format.DateUtils
import android.webkit.MimeTypeMap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.runtime.NavKey
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.library.HistoryEntry
import com.vextech.smart_edit.core.model.HistoryAction
import com.vextech.smart_edit.core.sharing.ShareService
import com.vextech.smart_edit.core.ui.CollectUiEvents
import com.vextech.smart_edit.core.ui.TileRow
import com.vextech.smart_edit.core.ui.UiEventChannel
import com.vextech.smart_edit.core.ui.UiText
import com.vextech.smart_edit.ui.navigation.CompressorRoute
import com.vextech.smart_edit.ui.navigation.ConverterRoute
import com.vextech.smart_edit.ui.navigation.OcrRoute
import com.vextech.smart_edit.ui.navigation.ScannerRoute
import com.vextech.smart_edit.ui.navigation.SearchRoute
import com.vextech.smart_edit.ui.navigation.SettingsRoute

/** One card on the Home grid. Add an entry to [homeTools] to add a card. */
data class HomeTool(val title: Int, val description: Int, val icon: ImageVector, val route: NavKey)

val homeTools: List<HomeTool> = listOf(
    HomeTool(R.string.scanner_title, R.string.home_scanner_desc, Icons.Rounded.DocumentScanner, ScannerRoute),
    HomeTool(R.string.converter, R.string.home_converter_desc, Icons.Rounded.SwapHoriz, ConverterRoute()),
    HomeTool(R.string.compressor, R.string.home_compressor_desc, Icons.Rounded.Compress, CompressorRoute()),
    HomeTool(R.string.ocr_title, R.string.home_ocr_desc, Icons.Rounded.TextFields, OcrRoute()),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpen: (NavKey) -> Unit, share: ShareService, vm: HomeViewModel = hiltViewModel()) {
    val recent by vm.recent.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val events = remember { UiEventChannel() }
    CollectUiEvents(events.events, snackbarHostState)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { onOpen(SearchRoute) }) { Icon(Icons.Rounded.Search, stringResource(R.string.search)) }
                    IconButton(onClick = { onOpen(SettingsRoute) }) { Icon(Icons.Rounded.Settings, stringResource(R.string.settings)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            homeTools.chunked(2).forEach { rowTools ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowTools.forEach { tool -> ToolCard(tool, Modifier.weight(1f)) { onOpen(tool.route) } }
                }
            }
            if (recent.isNotEmpty()) {
                Text(stringResource(R.string.home_recent_files), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEach { entry ->
                        val label = entry.displayName?.takeIf { it.isNotBlank() } ?: entry.description
                        TileRow(
                            icon = Icons.Rounded.Description,
                            title = label,
                            subtitle = stringResource(R.string.home_recent_detail, stringResource(actionLabel(entry.action)), relativeTime(entry)),
                            onClick = {
                                val uri = entry.uri ?: return@TileRow
                                runCatching { share.openManaged(uri, mimeFor(label)) }
                                    .onFailure { events.message(UiText.res(R.string.common_open_failed)) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCard(tool: HomeTool, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(tool.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
            Text(stringResource(tool.title), modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            // Two lines reserved so every card has the same height.
            Text(stringResource(tool.description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2, maxLines = 2)
        }
    }
}

@Composable
private fun relativeTime(entry: HistoryEntry): String =
    DateUtils.getRelativeTimeSpanString(entry.timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

private fun actionLabel(action: HistoryAction?): Int = when (action) {
    HistoryAction.CONVERTED -> R.string.history_action_converted
    HistoryAction.COMPRESSED -> R.string.history_action_compressed
    HistoryAction.SCANNED -> R.string.history_action_scanned
    HistoryAction.EDITED -> R.string.history_action_edited
    HistoryAction.CREATED -> R.string.history_action_created
    else -> R.string.history_action_other
}

private fun mimeFor(name: String): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "*/*"

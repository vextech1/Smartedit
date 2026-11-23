package com.vextech.editpro.feature.home

import android.text.format.DateUtils
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.runtime.NavKey
import com.vextech.editpro.R
import com.vextech.editpro.core.library.HistoryEntry
import com.vextech.editpro.core.model.HistoryAction
import com.vextech.editpro.core.sharing.ShareService
import com.vextech.editpro.core.ui.CollectUiEvents
import com.vextech.editpro.core.ui.UiEventChannel
import com.vextech.editpro.core.ui.UiText
import com.vextech.editpro.ui.navigation.message
import com.vextech.editpro.ui.navigation.toRoute
import com.vextech.editpro.ui.navigation.ConverterRoute
import com.vextech.editpro.ui.navigation.ImageEditorRoute
import com.vextech.editpro.ui.navigation.PdfToolsRoute
import com.vextech.editpro.ui.navigation.ScannerRoute

/** One tile in "Popular Tools". Add an entry to [popularTools] (or [moreTools]) to add a tile. */
data class HomeTool(val label: Int, val icon: ImageVector, val tint: Color, val route: NavKey)

/** The four tiles EditPro shows. */
private val popularTools: List<HomeTool> = listOf(
    HomeTool(R.string.home_tool_edit_pdf, EditProIcons.EditPdf, EditProTint.Pdf, PdfToolsRoute()),
    HomeTool(R.string.home_tool_edit_image, EditProIcons.EditImage, EditProTint.Image, ImageEditorRoute()),
    HomeTool(R.string.home_tool_scan_document, EditProIcons.ScanDocument, EditProTint.Document, ScannerRoute),
    HomeTool(R.string.home_tool_create_pdf, EditProIcons.CreatePdf, EditProTint.Pdf, ConverterRoute()),
)

/** [onSeeAll] switches to the Tools tab, like "See all →" on EditPro opens /tools. */
@Composable
fun HomeScreen(onOpen: (NavKey) -> Unit, onSeeAll: () -> Unit, share: ShareService, vm: HomeViewModel = hiltViewModel()) {
    val c = editProColors()
    val continueEditing by vm.continueEditing.collectAsState()
    val recent by vm.recent.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val events = remember { UiEventChannel() }
    CollectUiEvents(events.events, snackbarHostState)

    // "Upload Document": several PDFs or pictures can be chosen, and the same rules as the EditPro site decide where they go
    // (a PDF to the PDF editor, one picture to the photo editor, several pictures to Create PDF; a mix is refused with a note).
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.intake(uris) { plan ->
            plan.message()?.let { events.message(it) }
            plan.toRoute()?.let(onOpen)
        }
    }

    fun open(entry: HistoryEntry, label: String) {
        val uri = entry.uri ?: return
        when (kindOf(label)) {
            FileKind.PDF -> onOpen(PdfToolsRoute(uri))
            FileKind.IMAGE -> onOpen(ImageEditorRoute(uri))
            else -> runCatching { share.openManaged(uri, mimeFor(label)) }.onFailure { events.message(UiText.res(R.string.common_open_failed)) }
        }
    }

    Scaffold(
        containerColor = c.paper,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { BrandHeader() },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 920.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 24.dp),
            ) {
                // Hero: "Edit. Create. Organize." and one line about the app
                val heroSize = (LocalConfiguration.current.screenWidthDp * 0.08f).coerceIn(32f, 48f)
                Column(Modifier.padding(top = 12.dp, bottom = 6.dp)) {
                    HeroTitle(stringResource(R.string.home_hero_lead), stringResource(R.string.home_hero_accent), heroSize, Modifier.padding(bottom = 14.dp))
                    Text(
                        stringResource(R.string.home_lede),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                        color = c.muted,
                        fontSize = 17.92.sp,
                        lineHeight = 28.67.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                UploadCard(onClick = { picker.launch(arrayOf("application/pdf", "image/*")) })

                if (continueEditing.isNotEmpty()) {
                    SectionHeading(stringResource(R.string.home_continue_editing))
                    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        continueEditing.forEach { entry ->
                            val label = entry.label()
                            val studio = when (kindOf(label)) {
                                FileKind.PDF -> stringResource(R.string.home_studio_document)
                                FileKind.IMAGE -> stringResource(R.string.home_studio_photo)
                                else -> stringResource(actionLabel(entry.action))
                            }
                            EditProRow(trailing = { PillButton(stringResource(R.string.home_continue), onClick = { open(entry, label) }) }) {
                                Text(label, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 25.6.sp)
                                Text(stringResource(R.string.home_recent_detail, studio, relativeTime(entry)), color = c.muted, fontSize = 13.6.sp, lineHeight = 21.8.sp)
                            }
                        }
                    }
                }

                SectionHeading(stringResource(R.string.home_popular_tools), action = stringResource(R.string.home_see_all), onAction = onSeeAll)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    popularTools.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { t -> ToolTile(stringResource(t.label), t.icon, t.tint, Modifier.weight(1f)) { onOpen(t.route) } }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                if (recent.isNotEmpty()) {
                    SectionHeading(stringResource(R.string.home_section_recent), action = stringResource(R.string.home_clear_all), onAction = vm::clearRecent)
                    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recent.forEach { entry ->
                            val label = entry.label()
                            val (icon, tint) = when (kindOf(label)) {
                                FileKind.PDF -> EditProIcons.FilePdf to EditProTint.Pdf
                                FileKind.IMAGE -> EditProIcons.FileImage to EditProTint.Image
                                FileKind.TEXT -> EditProIcons.FileText to EditProTint.Text
                                FileKind.OTHER -> EditProIcons.FilePdf to EditProTint.Other
                            }
                            EditProRow(
                                onClick = { open(entry, label) },
                                leading = { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp)) },
                            ) {
                                Text(label, color = c.ink, fontSize = 16.sp, lineHeight = 25.6.sp)
                                Text(stringResource(R.string.home_recent_detail, stringResource(actionLabel(entry.action)), relativeTime(entry)), color = c.muted, fontSize = 13.6.sp, lineHeight = 21.8.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The big teal "Upload Document" card. */
@Composable
private fun UploadCard(onClick: () -> Unit) {
    val c = editProColors()
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Brush.linearGradient(listOf(UploadGradientStart, c.accent)), shape).clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(EditProIcons.Upload, contentDescription = null, tint = c.accentInk, modifier = Modifier.size(44.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.home_upload_title), color = c.accentInk, fontSize = 19.2.sp, lineHeight = 30.7.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.home_upload_hint), color = c.accentInk, modifier = Modifier.alpha(0.8f), fontSize = 13.33.sp, lineHeight = 21.3.sp)
        }
        Text("›", color = c.accentInk, fontSize = 28.8.sp)
    }
}

private enum class FileKind { PDF, IMAGE, TEXT, OTHER }

private fun kindOf(name: String): FileKind = when (mimeFor(name)) {
    "application/pdf" -> FileKind.PDF
    else -> when {
        mimeFor(name).startsWith("image/") -> FileKind.IMAGE
        mimeFor(name).startsWith("text/") -> FileKind.TEXT
        else -> FileKind.OTHER
    }
}

private fun HistoryEntry.label(): String = displayName?.takeIf { it.isNotBlank() } ?: description

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

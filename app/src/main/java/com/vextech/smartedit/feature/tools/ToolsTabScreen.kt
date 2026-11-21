package com.vextech.smartedit.feature.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.vextech.smartedit.R
import com.vextech.smartedit.feature.home.EditProChip
import com.vextech.smartedit.feature.home.EditProIcons
import com.vextech.smartedit.feature.home.EditProPage
import com.vextech.smartedit.feature.home.EditProTint
import com.vextech.smartedit.feature.home.SectionHeading
import com.vextech.smartedit.feature.home.ToolTile
import com.vextech.smartedit.feature.home.editProColors
import com.vextech.smartedit.ui.navigation.CompressorRoute
import com.vextech.smartedit.ui.navigation.ConverterRoute
import com.vextech.smartedit.ui.navigation.ImageEditorRoute
import com.vextech.smartedit.ui.navigation.OcrRoute
import com.vextech.smartedit.ui.navigation.PdfToolsRoute
import com.vextech.smartedit.ui.navigation.ScannerRoute

enum class ToolCategory(val label: Int, val section: Int, val tint: Color) {
    PDF(R.string.tools_cat_pdf, R.string.tools_section_pdf, EditProTint.Pdf),
    IMAGE(R.string.tools_cat_image, R.string.tools_section_image, EditProTint.Image),
    DOCUMENT(R.string.tools_cat_document, R.string.tools_section_document, EditProTint.Document),
}

/** One tile on the Tools tab. Add an entry to [allTools] to add a tool. */
data class ToolEntry(val label: Int, val category: ToolCategory, val icon: ImageVector, val route: NavKey)

private val allTools: List<ToolEntry> = listOf(
    ToolEntry(R.string.home_tool_edit_pdf, ToolCategory.PDF, EditProIcons.EditPdf, PdfToolsRoute()),
    ToolEntry(R.string.home_tool_create_pdf, ToolCategory.PDF, EditProIcons.CreatePdf, ConverterRoute()),
    ToolEntry(R.string.compressor, ToolCategory.PDF, EditProIcons.Compress, CompressorRoute()),
    ToolEntry(R.string.home_tool_edit_image, ToolCategory.IMAGE, EditProIcons.EditImage, ImageEditorRoute()),
    ToolEntry(R.string.converter, ToolCategory.IMAGE, EditProIcons.Convert, ConverterRoute()),
    ToolEntry(R.string.home_tool_scan_document, ToolCategory.DOCUMENT, EditProIcons.ScanDocument, ScannerRoute),
    ToolEntry(R.string.ocr_title, ToolCategory.DOCUMENT, EditProIcons.ExtractText, OcrRoute()),
)

/** EditPro's Tools page: title, search box, category chips, then the tools grouped by category in 3-column tiles. */
@Composable
fun ToolsTabScreen(onOpen: (NavKey) -> Unit) {
    val c = editProColors()
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<ToolCategory?>(null) }

    EditProPage {
        Text(stringResource(R.string.tools_title), color = c.ink, fontSize = 32.sp, lineHeight = 34.6.sp, letterSpacing = (-0.64f).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 14.dp))

        // Search
        val shape = RoundedCornerShape(12.dp)
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = c.ink, fontSize = 16.sp),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth().background(c.card, shape).border(BorderStroke(1.dp, c.line), shape).padding(horizontal = 14.dp, vertical = 11.dp)) {
                    if (query.isEmpty()) Text(stringResource(R.string.tools_search), color = c.muted, fontSize = 16.sp)
                    inner()
                }
            },
        )

        // Chips
        Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditProChip(stringResource(R.string.tools_cat_all), selected == null) { selected = null }
            ToolCategory.entries.forEach { cat -> EditProChip(stringResource(cat.label), selected == cat) { selected = cat } }
        }

        val q = query.trim().lowercase()
        val labels = allTools.associateWith { stringResource(it.label) }
        var any = false
        ToolCategory.entries.filter { selected == null || selected == it }.forEach { cat ->
            val list = allTools.filter { it.category == cat && (q.isEmpty() || labels.getValue(it).lowercase().contains(q)) }
            if (list.isEmpty()) return@forEach
            any = true
            // "PDF Tools" in the category colour, with "See all →" while every category is showing
            SectionHeading(stringResource(cat.section), tint = cat.tint, action = if (selected == null) stringResource(R.string.home_see_all) else null, onAction = { selected = cat })
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                list.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { t -> ToolTile(labels.getValue(t), t.icon, cat.tint, Modifier.weight(1f)) { onOpen(t.route) } }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (!any) Text(stringResource(R.string.tools_no_match, query.trim()), color = c.muted, fontSize = 16.sp, modifier = Modifier.padding(top = 16.dp))
    }
}

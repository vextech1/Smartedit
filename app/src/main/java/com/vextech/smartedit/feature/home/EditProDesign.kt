package com.vextech.smartedit.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.ui.res.stringResource
import com.vextech.smartedit.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The EditPro look, used by the Home screen and the bottom bar. These are EditPro's own colours (its app.css variables),
 * kept separate from the Material theme so the rest of the app is not recoloured.
 */
@Immutable
class EditProColors(
    val paper: Color,
    val card: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val accentInk: Color,
    val mark: Color,
    val warn: Color,
)

private val EditProLight = EditProColors(
    paper = Color(0xFFF3F5F4), card = Color(0xFFFFFFFF), ink = Color(0xFF0F1F24), muted = Color(0xFF54666B), line = Color(0xFFD5DEDC),
    accent = Color(0xFF00756F), accentInk = Color(0xFFFFFFFF), mark = Color(0xFFFFD24A), warn = Color(0xFFB3261E),
)

private val EditProDark = EditProColors(
    paper = Color(0xFF0B1416), card = Color(0xFF131E21), ink = Color(0xFFE8F0EF), muted = Color(0xFF9BB0AD), line = Color(0xFF26383B),
    accent = Color(0xFF5FE0D6), accentInk = Color(0xFF04282A), mark = Color(0xFFE6B82E), warn = Color(0xFFFF8A80),
)

val UploadGradientStart = Color(0xFF8AECE4)

/** Follows the app's light/dark choice: the Material background tells us which one is showing. */
@Composable
fun editProColors(): EditProColors = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) EditProDark else EditProLight

/** Category colours for tool tiles and file kinds (EditPro's CATEGORY_COLOR and FILE_COLOR). */
object EditProTint {
    val Pdf = Color(0xFFFF6B6B)
    val Image = Color(0xFF8F86FF)
    val Document = Color(0xFF4CC9F0)
    val Text = Color(0xFF3DDC97)
    val Other = Color(0xFF9BB0AD)
}

/** EditPro draws every icon as a 24x24 stroke path. These are the same paths, so the icons match exactly. */
object EditProIcons {
    private fun stroke(name: String, path: String, width: Float, cap: StrokeCap = StrokeCap.Round): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                pathData = PathParser().parsePathString(path).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = cap,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()

    // Bottom bar (stroke 1.8, square caps)
    val Home = stroke("Home", "M3 11l9-8 9 8v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z", 1.8f, StrokeCap.Butt)
    val Tools = stroke("Tools", "M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z", 1.8f, StrokeCap.Butt)
    val Library = stroke("Library", "M4 5.5A1.5 1.5 0 0 1 5.5 4H11v15H5.5A1.5 1.5 0 0 0 4 20.5zM20 5.5A1.5 1.5 0 0 0 18.5 4H13v15h5.5a1.5 1.5 0 0 1 1.5 1.5z", 1.8f, StrokeCap.Butt)
    val Files = stroke("Files", "M3 6a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", 1.8f, StrokeCap.Butt)
    val Settings = stroke(
        "Settings",
        "M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8zM19.4 13a7.6 7.6 0 0 0 0-2l2-1.5-2-3.5-2.3 1a7.6 7.6 0 0 0-1.7-1L15 3.5h-4L10.6 6a7.6 7.6 0 0 0-1.7 1l-2.3-1-2 3.5 2 1.5a7.6 7.6 0 0 0 0 2l-2 1.5 2 3.5 2.3-1a7.6 7.6 0 0 0 1.7 1l.4 2.5h4l.4-2.5a7.6 7.6 0 0 0 1.7-1l2.3 1 2-3.5z",
        1.8f,
        StrokeCap.Butt,
    )

    // Upload card
    val Upload = stroke("Upload", "M7 18a4.5 4.5 0 0 1-.5-9A6 6 0 0 1 18 9.5 4 4 0 0 1 17.5 18M12 11v8M9 14l3-3 3 3", 1.8f)

    // Tool tiles (stroke 1.7)
    val EditPdf = stroke("EditPdf", "M6 3h8l5 5v13H6zM14 3v5h5M9 14l5-5 2 2-5 5H9z", 1.7f)
    val EditImage = stroke("EditImage", "M7 3v14h14M3 7h14v14", 1.7f)
    val ScanDocument = stroke("ScanDocument", "M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3M7 12h10", 1.7f)
    val CreatePdf = stroke("CreatePdf", "M6 3h8l5 5v13H6zM14 3v5h5M12 11v6M9 14h6", 1.7f)
    val Convert = stroke("Convert", "M20 7a8 8 0 0 0-14-2M4 3v4h4M4 17a8 8 0 0 0 14 2M20 21v-4h-4", 1.7f)
    val Compress = stroke("Compress", "M12 3l9 5-9 5-9-5zM3 13l9 5 9-5M3 17.5l9 5 9-5", 1.7f)
    val ExtractText = stroke("ExtractText", "M4 4h16v16H4zM8 9h8M8 13h8M8 17h5", 1.7f)

    // File kinds in Recent Files
    val FilePdf = stroke("FilePdf", "M6 3h8l5 5v13H6zM14 3v5h5M9 14l5-5 2 2-5 5H9z", 1.7f)
    val FileImage = stroke("FileImage", "M4 5h16v14H4zM4 16l5-5 4 4 2-2 5 5M15 9.5a1 1 0 1 0 0 .01", 1.7f)
    val FileText = stroke("FileText", "M4 4h16v16H4zM8 9h8M8 13h8M8 17h5", 1.7f)
}

/** The page title: "Edit. Create." then the accented "Organize." (EditPro's hero h1). */
@Composable
fun HeroTitle(lead: String, accent: String, size: Float, modifier: Modifier = Modifier) {
    val c = editProColors()
    val text = androidx.compose.ui.text.buildAnnotatedString {
        append("$lead ")
        pushStyle(androidx.compose.ui.text.SpanStyle(color = c.accent))
        append(accent)
        pop()
    }
    Text(
        text,
        modifier = modifier.fillMaxWidth(),
        color = c.ink,
        fontSize = size.sp,
        lineHeight = (size * 1.08f).sp,
        letterSpacing = (-0.02f * size).sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
}

/** A section heading with an optional link on the right ("Popular Tools" ... "See all →"). */
@Composable
fun SectionHeading(title: String, modifier: Modifier = Modifier, tint: Color? = null, action: String? = null, onAction: () -> Unit = {}) {
    val c = editProColors()
    Row(modifier.fillMaxWidth().padding(top = 28.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = tint ?: c.ink, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2f).sp, fontWeight = FontWeight.Bold)
        if (action != null) {
            Text(
                action,
                modifier = Modifier.clickable(onClick = onAction).padding(vertical = 4.dp),
                color = c.accent,
                fontSize = 15.2.sp,
            )
        }
    }
}

/** A bordered white card row: [leading] content that grows, then [trailing] (EditPro's .row). */
@Composable
fun EditProRow(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, leading: @Composable () -> Unit = {}, trailing: @Composable () -> Unit = {}, body: @Composable () -> Unit) {
    val c = editProColors()
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(c.card, shape).border(BorderStroke(1.dp, c.line), shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        leading()
        Column(Modifier.weight(1f)) { body() }
        trailing()
    }
}

/** The small pill button on a row ("Continue"). */
@Composable
fun PillButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = editProColors()
    Text(
        label,
        modifier = modifier.clip(CircleShape).background(c.accent, CircleShape).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 7.dp),
        color = c.accentInk,
        fontSize = 14.7.sp,
        lineHeight = 23.5.sp,
        fontWeight = FontWeight(650),
    )
}

/** A tool tile: coloured stroke icon over a label, in a bordered white card. */
@Composable
fun ToolTile(label: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = editProColors()
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.heightIn(min = 96.dp).clip(shape).background(c.card, shape).border(BorderStroke(1.dp, c.line), shape).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
        Text(label, color = c.ink, fontSize = 15.2.sp, lineHeight = 24.3.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** Phone bottom bar: white, hairline on top, muted labels, the current tab in the accent colour with a 3dp line under it. */
@Composable
fun EditProTabBar(items: List<EditProTabItem>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = editProColors()
    Column(modifier.fillMaxWidth().background(c.card)) {
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(Modifier.fillMaxWidth()) {
            items.forEachIndexed { i, item ->
                val on = i == selectedIndex
                val tint = if (on) c.accent else c.muted
                Column(
                    Modifier.weight(1f).heightIn(min = 64.dp).clickable { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp, bottom = 7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                        Text(item.label, color = tint, fontSize = 11.5.sp, lineHeight = 18.sp)
                    }
                    Spacer(Modifier.fillMaxWidth().height(3.dp).background(if (on) c.accent else Color.Transparent))
                }
            }
        }
    }
}

class EditProTabItem(val label: String, val icon: ImageVector)

/** White bar with the app name in bold, a hairline under it (EditPro's phone header). */
@Composable
fun BrandHeader() {
    val c = editProColors()
    Column(Modifier.fillMaxWidth().background(c.card).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
            Text(stringResource(R.string.app_name), color = c.ink, fontSize = 18.4.sp, letterSpacing = (-0.18f).sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(c.line))
    }
}

/** A page in EditPro's shape: the brand header, then a scrolling column on the paper colour, 20dp side gutters, 920dp at most. */
@Composable
fun EditProPage(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = editProColors()
    Scaffold(containerColor = c.paper, topBar = { BrandHeader() }) { padding ->
        Box(modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 920.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 24.dp),
                content = content,
            )
        }
    }
}

/** A filter chip: pill, bordered, muted; the chosen one is filled with the accent colour. */
@Composable
fun EditProChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = editProColors()
    Text(
        label,
        modifier = Modifier.clip(CircleShape).background(if (selected) c.accent else c.card, CircleShape)
            .border(BorderStroke(1.dp, if (selected) c.accent else c.line), CircleShape).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 8.dp),
        color = if (selected) c.accentInk else c.muted,
        fontSize = 16.sp,
        lineHeight = 25.6.sp,
        fontWeight = if (selected) FontWeight(650) else FontWeight.Normal,
    )
}

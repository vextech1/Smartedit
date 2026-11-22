package com.vextech.smartedit.feature.settings

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.vextech.smartedit.R
import com.vextech.smartedit.core.library.OcrPolicies
import com.vextech.smartedit.core.library.OcrScript
import com.vextech.smartedit.core.model.ThemePreference
import com.vextech.smartedit.feature.home.EditProRow
import com.vextech.smartedit.feature.home.editProColors

/** Language names are shown in their own language so people can always find theirs. */
private val languages = listOf("en" to "English", "fr" to "Français", "es" to "Español", "de" to "Deutsch", "ar" to "العربية", "hi" to "हिन्दी")

/** The order EditPro's Settings page shows the theme choices in. */
private val themeOrder = listOf(ThemePreference.SYSTEM, ThemePreference.DARK, ThemePreference.LIGHT)

/** EditPro's own pages, opened in the browser for help and legal text. */
private const val SITE = "https://editpro.site"

/**
 * Settings laid out like EditPro's Settings page: a heading, then bordered rows, one group at a time.
 * Language and Notifications are the app's own and stay, under "App", where EditPro keeps its install row.
 */
@Composable
fun SettingsScreen(
    onBack: (() -> Unit)?,
    onTrash: () -> Unit,
    onOpenProfile: (() -> Unit)? = null,
    onOpenAdmin: (() -> Unit)? = null,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val c = editProColors()
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    var languageDialog by remember { mutableStateOf(false) }
    var scriptMenu by remember { mutableStateOf(false) }
    val systemLanguageLabel = stringResource(R.string.settings_language_system)
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.notifications(granted) }
    val script = OcrPolicies.scriptFromSetting(settings.ocrScript)

    Scaffold(containerColor = c.paper, topBar = { SettingsTopBar(stringResource(R.string.settings), onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 920.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
                // Smart Edit's own account screens: Profile (sign in, storage, backup, AI assistant, about) and Admin.
                if (onOpenProfile != null || onOpenAdmin != null) {
                    Group(stringResource(R.string.settings_group_account)) {
                        if (onOpenProfile != null) LinkRow(stringResource(R.string.nav_profile), stringResource(R.string.settings_profile_desc), onOpenProfile)
                        if (onOpenAdmin != null) LinkRow(stringResource(R.string.admin_title), stringResource(R.string.settings_admin_desc), onOpenAdmin)
                    }
                }

                Group(stringResource(R.string.settings_appearance)) {
                    Segments(
                        options = themeOrder.map { it to stringResource(themeLabel(it)) },
                        selected = settings.theme,
                        onSelect = vm::theme,
                    )
                }

                Group(stringResource(R.string.settings_group_app)) {
                    ValueRow(
                        title = stringResource(R.string.settings_language),
                        value = if (settings.language.isEmpty()) systemLanguageLabel else languages.firstOrNull { it.first == settings.language }?.second ?: settings.language,
                        onClick = { languageDialog = true },
                    )
                    SwitchRow(stringResource(R.string.settings_notifications), stringResource(R.string.settings_notifications_desc), settings.notificationsEnabled) { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.notifications(enabled)
                    }
                }

                Group(stringResource(R.string.settings_group_ocr)) {
                    Box {
                        ValueRow(stringResource(R.string.settings_ocr_default), stringResource(scriptLabel(script)), onClick = { scriptMenu = true })
                        DropdownMenu(expanded = scriptMenu, onDismissRequest = { scriptMenu = false }) {
                            OcrScript.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(scriptLabel(option))) },
                                    onClick = { scriptMenu = false; vm.ocrScript(option) },
                                )
                            }
                        }
                    }
                }

                Group(stringResource(R.string.settings_group_files)) {
                    SwitchRow(stringResource(R.string.settings_auto_backup), stringResource(R.string.settings_auto_backup_desc), settings.autoBackupEnabled, vm::autoBackup)
                    SwitchRow(stringResource(R.string.settings_confirm_delete), stringResource(R.string.settings_confirm_delete_desc), settings.confirmDeleteEnabled, vm::confirmDelete)
                    Text(stringResource(R.string.settings_export_format), color = c.ink, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
                    Segments(
                        options = listOf("pdf", "csv", "json").map { it to it.uppercase() },
                        selected = settings.defaultExportFormat,
                        onSelect = vm::exportFormat,
                    )
                }

                Group(stringResource(R.string.settings_group_help)) {
                    LinkRow(stringResource(R.string.trash), null, onTrash)
                    LinkRow(stringResource(R.string.settings_faq), stringResource(R.string.settings_faq_desc)) { openPage(context, "/faq") }
                    LinkRow(stringResource(R.string.settings_contact), stringResource(R.string.settings_contact_desc)) { openPage(context, "/contact") }
                    LinkRow(stringResource(R.string.settings_privacy), null) { openPage(context, "/privacy") }
                    LinkRow(stringResource(R.string.settings_terms), null) { openPage(context, "/terms") }
                }
            }
        }
    }

    if (languageDialog) {
        AlertDialog(
            onDismissRequest = { languageDialog = false },
            containerColor = c.card,
            titleContentColor = c.ink,
            textContentColor = c.ink,
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column {
                    (listOf("" to systemLanguageLabel) + languages).forEach { (tag, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                languageDialog = false
                                vm.language(tag)
                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = settings.language == tag, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = c.accent, unselectedColor = c.muted))
                            Spacer(Modifier.width(12.dp))
                            Text(label, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { languageDialog = false }) { Text(stringResource(R.string.close), color = c.accent) } },
        )
    }
}

private fun themeLabel(theme: ThemePreference): Int = when (theme) {
    ThemePreference.SYSTEM -> R.string.settings_theme_system
    ThemePreference.LIGHT -> R.string.settings_theme_light
    ThemePreference.DARK -> R.string.settings_theme_dark
}

private fun scriptLabel(script: OcrScript): Int = when (script) {
    OcrScript.LATIN -> R.string.ocr_script_latin
    OcrScript.CHINESE -> R.string.ocr_script_chinese
    OcrScript.DEVANAGARI -> R.string.ocr_script_devanagari
    OcrScript.JAPANESE -> R.string.ocr_script_japanese
    OcrScript.KOREAN -> R.string.ocr_script_korean
}

private fun openPage(context: Context, path: String) {
    runCatching { CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(SITE + path)) }
        .onFailure { Toast.makeText(context, R.string.settings_open_page_failed, Toast.LENGTH_SHORT).show() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(title: String, onBack: (() -> Unit)?) {
    val c = editProColors()
    TopAppBar(
        title = { Text(title, color = c.ink, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), tint = c.ink) }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = c.paper),
    )
}

/** A group: its muted heading (EditPro's `.set h2`), then its rows with a small gap between them. */
@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = editProColors()
    Text(title, color = c.muted, fontSize = 16.8.sp, fontWeight = FontWeight(650), modifier = Modifier.padding(top = 26.dp, bottom = 8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** A row of equal choices (EditPro's `.seg`): the chosen one is accent-coloured and bold with a line under it. */
@Composable
private fun <T> Segments(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) -> Segment(label, value == selected, { onSelect(value) }, Modifier.weight(1f)) }
    }
}

@Composable
private fun Segment(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = editProColors()
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.heightIn(min = 48.dp).clip(shape).background(c.card, shape)
            .drawBehind { if (on) drawRect(c.accent, topLeft = Offset(0f, size.height - 3.dp.toPx()), size = Size(size.width, 3.dp.toPx())) }
            .border(1.dp, if (on) c.accent else c.line, shape)
            .selectable(selected = on, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (on) c.accent else c.muted, fontWeight = if (on) FontWeight(650) else FontWeight.Normal, fontSize = 15.2.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RowText(title: String, description: String? = null) {
    val c = editProColors()
    Text(title, color = c.ink, fontSize = 16.sp)
    if (description != null) Text(description, color = c.muted, fontSize = 13.6.sp, lineHeight = 19.sp)
}

/** A row whose current value shows on the right, with a chevron: tapping it lets the person change it. */
@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    val c = editProColors()
    EditProRow(onClick = onClick, trailing = {
        Text(value, color = c.muted, fontSize = 15.sp)
        Icon(Icons.Rounded.ChevronRight, null, tint = c.muted)
    }) { RowText(title) }
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    val c = editProColors()
    EditProRow(
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChecked),
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = c.accentInk, checkedTrackColor = c.accent, checkedBorderColor = c.accent,
                    uncheckedThumbColor = c.muted, uncheckedTrackColor = c.card, uncheckedBorderColor = c.line,
                ),
            )
        },
    ) { RowText(title, description) }
}

/** A row that opens somewhere else (EditPro's linked `.row` with a "›"). */
@Composable
private fun LinkRow(title: String, description: String?, onClick: () -> Unit) {
    val c = editProColors()
    EditProRow(onClick = onClick, trailing = { Icon(Icons.Rounded.ChevronRight, null, tint = c.muted) }) { RowText(title, description) }
}

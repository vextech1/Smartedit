package com.vextech.smart_edit.feature.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.model.ThemePreference
import com.vextech.smart_edit.core.ui.FeatureScaffold

/** Language names are shown in their own language so people can always find theirs. */
private val languages = listOf("en" to "English", "fr" to "Français", "es" to "Español", "de" to "Deutsch", "ar" to "العربية", "hi" to "हिन्दी")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsState()
    var languageDialog by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> vm.notifications(granted) }

    FeatureScaffold(title = stringResource(R.string.settings), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionLabel(stringResource(R.string.settings_appearance))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemePreference.entries.forEachIndexed { index, theme ->
                    SegmentedButton(
                        selected = settings.theme == theme,
                        onClick = { vm.theme(theme) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemePreference.entries.size),
                    ) {
                        Text(
                            stringResource(
                                when (theme) {
                                    ThemePreference.SYSTEM -> R.string.settings_theme_system
                                    ThemePreference.LIGHT -> R.string.settings_theme_light
                                    ThemePreference.DARK -> R.string.settings_theme_dark
                                }
                            )
                        )
                    }
                }
            }

            SettingRow(
                title = stringResource(R.string.settings_language),
                description = languages.firstOrNull { it.first == settings.language }?.second ?: settings.language,
                modifier = Modifier.clickable { languageDialog = true },
            ) { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            HorizontalDivider()

            SettingSwitch(stringResource(R.string.settings_notifications), stringResource(R.string.settings_notifications_desc), settings.notificationsEnabled) { enabled ->
                if (enabled && Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.notifications(enabled)
            }
            SettingSwitch(stringResource(R.string.settings_auto_backup), stringResource(R.string.settings_auto_backup_desc), settings.autoBackupEnabled, vm::autoBackup)
            SettingSwitch(stringResource(R.string.settings_confirm_delete), stringResource(R.string.settings_confirm_delete_desc), settings.confirmDeleteEnabled, vm::confirmDelete)
            HorizontalDivider()

            SectionLabel(stringResource(R.string.settings_export_format))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("pdf", "csv", "json").forEach { format ->
                    FilterChip(selected = settings.defaultExportFormat == format, onClick = { vm.exportFormat(format) }, label = { Text(format.uppercase()) })
                }
            }
        }
    }

    if (languageDialog) {
        AlertDialog(
            onDismissRequest = { languageDialog = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column {
                    languages.forEach { (tag, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                languageDialog = false
                                vm.language(tag)
                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = settings.language == tag, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { languageDialog = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun SettingRow(title: String, description: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    SettingRow(title, description, Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChecked)) {
        Switch(checked = checked, onCheckedChange = null)
    }
}

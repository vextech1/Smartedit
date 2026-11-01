package com.vextech.smart_edit.feature.settings
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.vextech.smart_edit.core.model.ThemePreference
import com.vextech.smart_edit.core.ui.FeatureScaffold
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SettingsScreen(onBack:()->Unit,vm:SettingsViewModel= hiltViewModel()){val s by vm.settings.collectAsState();val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->vm.notifications(granted)};FeatureScaffold("Settings",onBack){pad->Column(Modifier.fillMaxSize().padding(pad).padding(16.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){Text("Appearance",style=MaterialTheme.typography.titleMedium);SingleChoiceSegmentedButtonRow{ThemePreference.entries.forEachIndexed{i,t->SegmentedButton(selected=s.theme==t,onClick={vm.theme(t)},shape=SegmentedButtonDefaults.itemShape(i,ThemePreference.entries.size)){Text(t.name.lowercase().replaceFirstChar(Char::uppercase))}}};var langOpen by remember{mutableStateOf(false)};ExposedDropdownMenuBox(expanded=langOpen,onExpandedChange={langOpen=!langOpen}){OutlinedTextField(value=mapOf("en" to "English","fr" to "Français","es" to "Español","de" to "Deutsch","ar" to "العربية","hi" to "हिन्दी")[s.language]?:s.language,onValueChange={},readOnly=true,label={Text("Language")},modifier=Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable));ExposedDropdownMenu(expanded=langOpen,onDismissRequest={langOpen=false}){listOf("en" to "English","fr" to "Français","es" to "Español","de" to "Deutsch","ar" to "العربية","hi" to "हिन्दी").forEach{(tag,label)->DropdownMenuItem(text={Text(label)},onClick={langOpen=false;vm.language(tag);AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))})}}};SettingSwitch("Notifications",s.notificationsEnabled){v->if(v&&Build.VERSION.SDK_INT>=33)permission.launch(Manifest.permission.POST_NOTIFICATIONS)else vm.notifications(v)};SettingSwitch("Automatic daily backup",s.autoBackupEnabled,vm::autoBackup);SettingSwitch("Confirm before deleting",s.confirmDeleteEnabled,vm::confirmDelete);Text("Default export format",style=MaterialTheme.typography.titleMedium);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("pdf","csv","json").forEach{fmt->FilterChip(selected=s.defaultExportFormat==fmt,onClick={vm.exportFormat(fmt)},label={Text(fmt.uppercase())})}}}}}
@Composable private fun SettingSwitch(label:String,checked:Boolean,onChecked:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label,Modifier.weight(1f));Switch(checked,onChecked)}}

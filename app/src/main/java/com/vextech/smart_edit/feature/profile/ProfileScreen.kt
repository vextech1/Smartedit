package com.vextech.smart_edit.feature.profile

import com.vextech.smart_edit.core.ui.TileRow
import androidx.compose.material3.Surface
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.runtime.NavKey
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.model.DashboardSummary
import com.vextech.smart_edit.core.ui.FeatureScaffold
import com.vextech.smart_edit.core.ui.SectionHeader
import com.vextech.smart_edit.core.ui.formatBytes
import com.vextech.smart_edit.ui.navigation.AiAssistRoute
import com.vextech.smart_edit.ui.navigation.BackupRoute

@Composable
fun ProfileScreen(onOpen: (NavKey) -> Unit, vm: ProfileViewModel = hiltViewModel()) {
    val summary by vm.summary.collectAsState()
    var showAbout by remember { mutableStateOf(false) }

    FeatureScaffold(title = stringResource(R.string.nav_profile), onBack = null) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 16.dp)) {
            item(key = "account") { AccountCard(Modifier.padding(16.dp)) }
            item(key = "library_header") { SectionHeader(stringResource(R.string.profile_storage_title)) }
            item(key = "library") { LibraryStats(summary, Modifier.padding(horizontal = 16.dp)) }
            item(key = "more_header") { SectionHeader(stringResource(R.string.profile_more_title)) }
            item(key = "backup") {
                NavRow(Icons.Rounded.Backup, stringResource(R.string.backup), stringResource(R.string.profile_backup_desc)) { onOpen(BackupRoute) }
            }
            item(key = "ai") {
                NavRow(Icons.Rounded.AutoAwesome, stringResource(R.string.ai_assist), stringResource(R.string.profile_ai_desc)) { onOpen(AiAssistRoute) }
            }
            item(key = "about") {
                NavRow(Icons.Rounded.Info, stringResource(R.string.profile_about), null) { showAbout = true }
            }
        }
    }

    if (showAbout) AboutDialog(onDismiss = { showAbout = false })
}

/**
 * Signed-out account card. Accounts are not built yet, so the action is disabled and marked "Coming soon".
 * When accounts arrive, replace this composable's state and keep the rest of the screen as is.
 */
@Composable
fun AccountCard(modifier: Modifier = Modifier) {
    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.profile_account_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.profile_account_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.profile_account_button) + " · " + stringResource(R.string.profile_coming_soon))
            }
        }
    }
}

@Composable
private fun LibraryStats(summary: DashboardSummary, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(summary.totalFiles.toString(), stringResource(R.string.files), Modifier.weight(1f))
            StatTile(summary.totalNotes.toString(), stringResource(R.string.notes), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(summary.favorites.toString(), stringResource(R.string.favorites), Modifier.weight(1f))
            StatTile(formatBytes(summary.storageUsedBytes), stringResource(R.string.profile_stat_storage), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NavRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    TileRow(
        icon = icon, title = title, subtitle = subtitle, onClick = onClick,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        trailing = { Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp)) },
    )
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.profile_about_version, version), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.profile_about_message))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

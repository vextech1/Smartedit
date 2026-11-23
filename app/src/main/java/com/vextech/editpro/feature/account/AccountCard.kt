package com.vextech.editpro.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.vextech.editpro.R

/**
 * The account card on Profile: sign in with Google, or who is signed in with Sign out and Delete my account.
 * Same wording and steps as the site's "My account" page. Admins use the same sign-in; see the Admin screen in Settings.
 */
@Composable
fun AccountCard(modifier: Modifier = Modifier, vm: AccountViewModel = hiltViewModel()) {
    val auth by vm.authState.collectAsState()
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val user = auth.user

    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AccountCircle, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when {
                        !auth.ready -> Text(stringResource(R.string.account_loading), style = MaterialTheme.typography.bodyMedium)
                        !auth.enabled -> {
                            Text(stringResource(R.string.profile_account_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.account_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        user == null -> {
                            Text(stringResource(R.string.account_sign_in_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.account_sign_in_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        else -> {
                            Text(stringResource(R.string.account_signed_in_as, user.name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            user.email?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }

            ui.message?.let { Text(it.asString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }

            if (auth.ready && auth.enabled) {
                if (user == null) {
                    Button(onClick = { vm.signIn(context) }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (ui.busy) R.string.account_signing_in else R.string.account_sign_in))
                    }
                } else {
                    OutlinedButton(onClick = vm::signOut, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.account_sign_out)) }
                    TextButton(
                        onClick = { confirmDelete = true },
                        enabled = !ui.busy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.account_delete)) }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.account_delete)) },
            text = { Text(stringResource(R.string.account_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteAccount() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.account_delete))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

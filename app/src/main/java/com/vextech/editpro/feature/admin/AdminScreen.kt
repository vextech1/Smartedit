package com.vextech.editpro.feature.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vextech.editpro.R
import com.vextech.editpro.core.auth.AdminRole
import com.vextech.editpro.core.ui.FeatureScaffold

/**
 * Settings > Admin, the app's version of the site's admin page: sign in with the Google account that was given access, see what it may do,
 * and, for the owner, manage the helper admins. What an account may do is decided by the server's rules, not by this screen.
 */
@Composable
fun AdminScreen(onBack: (() -> Unit)?, onOpen: (androidx.navigation3.runtime.NavKey) -> Unit = {}, vm: AdminViewModel = hiltViewModel()) {
    val auth by vm.authState.collectAsState()
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(auth.user?.uid, auth.ready) { if (auth.ready) vm.onAccount(auth) }

    FeatureScaffold(title = stringResource(R.string.admin_title), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val user = auth.user
            when {
                !auth.ready -> Text(stringResource(R.string.account_loading))
                !auth.enabled -> Text(stringResource(R.string.account_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
                user == null -> ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.admin_sign_in_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.admin_sign_in_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ui.message?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }
                        Button(onClick = { vm.signIn(context) }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(if (ui.busy) R.string.account_signing_in else R.string.account_sign_in))
                        }
                    }
                }
                else -> {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.admin_signed_in_as, user.email ?: user.name), modifier = Modifier.weight(1f))
                        TextButton(onClick = vm::signOut) { Text(stringResource(R.string.account_sign_out)) }
                    }
                    when {
                        ui.checking -> Text(stringResource(R.string.account_loading))
                        ui.checkFailed -> ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(stringResource(R.string.admin_check_failed))
                                OutlinedButton(onClick = { vm.refresh(auth) }) { Text(stringResource(R.string.admin_retry)) }
                            }
                        }
                        ui.role == AdminRole.NONE -> ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.admin_not_admin), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.admin_not_admin_hint, user.email ?: user.name), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (!user.emailVerified) Text(stringResource(R.string.admin_email_unverified), color = MaterialTheme.colorScheme.error)
                            }
                        }
                        else -> {
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        stringResource(if (ui.role == AdminRole.OWNER) R.string.admin_role_owner else R.string.admin_role_admin),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        stringResource(if (ui.role == AdminRole.OWNER) R.string.admin_role_owner_body else R.string.admin_role_admin_body),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            OutlinedButton(onClick = { onOpen(com.vextech.editpro.ui.navigation.LibraryUploadRoute(admin = true)) }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.admin_library_add))
                            }
                            OutlinedButton(onClick = { onOpen(com.vextech.editpro.ui.navigation.StudentFilesRoute) }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.admin_students_review))
                            }
                            ui.helpers?.let { helpers -> HelpersSection(helpers, ui.busy, vm) }
                        }
                    }
                    ui.message?.let { Text(it.asString(), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun HelpersSection(helpers: List<String>, busy: Boolean, vm: AdminViewModel) {
    var email by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.admin_helpers_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.admin_helpers_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (helpers.isEmpty()) {
            Text(stringResource(R.string.admin_no_helpers), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            helpers.forEach { helper ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(helper, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { removing = helper },
                        enabled = !busy,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(R.string.admin_remove)) }
                }
            }
        }
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.admin_email_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { vm.addHelper(email) { email = "" } }, enabled = !busy && email.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.admin_add))
        }
    }

    removing?.let { helper ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.admin_remove)) },
            text = { Text(stringResource(R.string.admin_remove_confirm, helper)) },
            confirmButton = {
                TextButton(onClick = { removing = null; vm.removeHelper(helper) }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(stringResource(R.string.admin_remove))
                }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

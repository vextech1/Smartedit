package com.vextech.smart_edit.feature.ai

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.model.HistoryAction
import com.vextech.smart_edit.core.repository.SmartEditRepository
import com.vextech.smart_edit.core.ui.FeatureScaffold
import com.vextech.smart_edit.core.ui.TileRow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

data class AiProvider(val name: String, val host: String, val url: String)

private val providers = listOf(
    AiProvider("Claude", "claude.ai", "https://claude.ai/new"),
    AiProvider("Gemini", "gemini.google.com", "https://gemini.google.com/app"),
    AiProvider("ChatGPT", "chatgpt.com", "https://chatgpt.com/"),
    AiProvider("Copilot", "copilot.microsoft.com", "https://copilot.microsoft.com/"),
    AiProvider("DeepSeek", "chat.deepseek.com", "https://chat.deepseek.com/"),
)

@HiltViewModel
class AiAssistViewModel @Inject constructor(private val repo: SmartEditRepository) : ViewModel() {
    fun opened(provider: AiProvider) {
        viewModelScope.launch { repo.recordHistory(HistoryAction.OPENED, "Opened ${provider.name} AI provider") }
    }
}

@Composable
fun AiAssistScreen(onBack: () -> Unit, vm: AiAssistViewModel = hiltViewModel()) {
    val context = LocalContext.current
    FeatureScaffold(title = stringResource(R.string.ai_assist), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Text(stringResource(R.string.ai_notice), Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(providers, key = { it.name }) { provider ->
                TileRow(
                    icon = Icons.Rounded.AutoAwesome,
                    title = provider.name,
                    subtitle = stringResource(R.string.ai_opens_host, provider.host),
                    onClick = {
                        vm.opened(provider)
                        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(provider.url))
                    },
                    trailing = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp)) },
                )
            }
        }
    }
}

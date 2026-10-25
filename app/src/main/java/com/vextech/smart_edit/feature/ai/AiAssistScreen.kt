package com.vextech.smart_edit.feature.ai
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.smart_edit.core.repository.SmartEditRepository
import com.vextech.smart_edit.core.ui.FeatureScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiProvider(val name:String,val url:String)
private val providers=listOf(AiProvider("Claude","https://claude.ai/new"),AiProvider("Gemini","https://gemini.google.com/app"),AiProvider("ChatGPT","https://chatgpt.com/"),AiProvider("Copilot","https://copilot.microsoft.com/"),AiProvider("DeepSeek","https://chat.deepseek.com/"))
@HiltViewModel class AiAssistViewModel @Inject constructor(private val repo:SmartEditRepository):ViewModel(){fun opened(p:AiProvider)=viewModelScope.launch{repo.recordHistory(com.vextech.smart_edit.core.model.HistoryAction.OPENED,"Opened ${p.name} AI provider")}}
@Composable fun AiAssistScreen(onBack:()->Unit,vm:AiAssistViewModel= hiltViewModel()){val ctx=LocalContext.current;FeatureScaffold("AI Assist",onBack){pad->LazyColumn(Modifier.fillMaxSize().padding(pad),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Text("Smart Edit does not impersonate these services or inject JavaScript into their websites. Choose a provider to open its official site in a secure browser tab.",style=MaterialTheme.typography.bodyMedium)};items(providers.size){i->val p=providers[i];ElevatedCard(onClick={vm.opened(p);CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(ctx,Uri.parse(p.url))},modifier=Modifier.fillMaxWidth()){Row(Modifier.padding(18.dp)){Text(p.name,style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f));Icon(Icons.Rounded.OpenInNew,null)}}}}}}

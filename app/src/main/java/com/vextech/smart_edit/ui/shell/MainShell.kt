package com.vextech.smart_edit.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.ads.UnityAdsService
import com.vextech.smart_edit.core.sharing.ShareService
import com.vextech.smart_edit.feature.ai.AiAssistScreen
import com.vextech.smart_edit.feature.backup.BackupScreen
import com.vextech.smart_edit.feature.compressor.CompressorScreen
import com.vextech.smart_edit.feature.converter.ConverterScreen
import com.vextech.smart_edit.feature.history.HistoryScreen
import com.vextech.smart_edit.feature.home.HomeScreen
import com.vextech.smart_edit.feature.imageeditor.ImageEditorScreen
import com.vextech.smart_edit.feature.notes.NoteEditorScreen
import com.vextech.smart_edit.feature.ocr.OcrScreen
import com.vextech.smart_edit.feature.pdf.PdfToolsScreen
import com.vextech.smart_edit.feature.profile.ProfileScreen
import com.vextech.smart_edit.feature.scanner.LiveCameraScreen
import com.vextech.smart_edit.feature.scanner.ScannerScreen
import com.vextech.smart_edit.feature.search.SearchScreen
import com.vextech.smart_edit.feature.settings.SettingsScreen
import com.vextech.smart_edit.feature.trash.TrashScreen
import com.vextech.smart_edit.ui.navigation.ActivityRoute
import com.vextech.smart_edit.ui.navigation.AiAssistRoute
import com.vextech.smart_edit.ui.navigation.BackupRoute
import com.vextech.smart_edit.ui.navigation.CompressorRoute
import com.vextech.smart_edit.ui.navigation.ConverterRoute
import com.vextech.smart_edit.ui.navigation.FilesTabRoute
import com.vextech.smart_edit.ui.navigation.HomeRoute
import com.vextech.smart_edit.ui.navigation.ImageEditorRoute
import com.vextech.smart_edit.ui.navigation.LiveCameraRoute
import com.vextech.smart_edit.ui.navigation.NoteEditorRoute
import com.vextech.smart_edit.ui.navigation.OcrRoute
import com.vextech.smart_edit.ui.navigation.PdfToolsRoute
import com.vextech.smart_edit.ui.navigation.ProfileRoute
import com.vextech.smart_edit.ui.navigation.ScannerRoute
import com.vextech.smart_edit.ui.navigation.SearchRoute
import com.vextech.smart_edit.ui.navigation.SettingsRoute
import com.vextech.smart_edit.ui.navigation.TrashRoute
import com.vextech.smart_edit.ui.navigation.routeFor

enum class MainTab(val label: Int, val icon: ImageVector) {
    HOME(R.string.nav_home, Icons.Rounded.Home),
    FILES(R.string.files, Icons.Rounded.Folder),
    ACTIVITY(R.string.nav_activity, Icons.Rounded.History),
    PROFILE(R.string.nav_profile, Icons.Rounded.AccountCircle),
}

/**
 * Bottom-navigation shell. Every tab keeps its own back stack. Tools open on top of the current tab and hide
 * the bar. Back pops the current stack; from a tab root it returns to Home; from Home it leaves the app.
 */
@Composable
fun MainShell(shareService: ShareService, adsService: UnityAdsService) {
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val homeStack = rememberNavBackStack(HomeRoute)
    val filesStack = rememberNavBackStack(FilesTabRoute)
    val activityStack = rememberNavBackStack(ActivityRoute)
    val profileStack = rememberNavBackStack(ProfileRoute)
    val stack = when (tab) {
        MainTab.HOME -> homeStack
        MainTab.FILES -> filesStack
        MainTab.ACTIVITY -> activityStack
        MainTab.PROFILE -> profileStack
    }
    val atRoot = stack.size <= 1

    fun push(key: NavKey) { stack.add(key) }
    fun back() { if (stack.size > 1) stack.removeLastOrNull() }

    BackHandler(enabled = atRoot && tab != MainTab.HOME) { tab = MainTab.HOME }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (atRoot) {
                AppBottomBar(
                    selected = tab,
                    onSelect = { picked ->
                        if (picked == tab) {
                            while (stack.size > 1) stack.removeLastOrNull()
                        } else {
                            tab = picked
                        }
                    },
                )
            }
        },
    ) { inner ->
        val content = PaddingValues(bottom = inner.calculateBottomPadding())
        NavDisplay(
            backStack = stack,
            modifier = Modifier.padding(content).consumeWindowInsets(content),
            onBack = ::back,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<HomeRoute> { HomeScreen(onOpen = ::push, share = shareService) }
                entry<FilesTabRoute> {
                    FilesTabScreen(
                        share = shareService,
                        onOpenNote = { push(NoteEditorRoute(it)) },
                        onSearch = { push(SearchRoute) },
                        onTrash = { push(TrashRoute) },
                        onTool = { action, uri -> push(action.routeFor(uri)) },
                    )
                }
                entry<ActivityRoute> { HistoryScreen(onBack = null, share = shareService) }
                entry<ProfileRoute> { ProfileScreen(onOpen = ::push) }

                entry<ScannerRoute> { ScannerScreen(::back, { push(LiveCameraRoute(it)) }, shareService) }
                entry<LiveCameraRoute> { route -> LiveCameraScreen(route.sessionId, ::back, shareService) }
                entry<OcrRoute> { route -> OcrScreen(::back, shareService, initialUri = route.uri) }
                entry<PdfToolsRoute> { route -> PdfToolsScreen(::back, shareService, adsService, initialUri = route.uri) }
                entry<ConverterRoute> { route -> ConverterScreen(::back, shareService, adsService, initialUri = route.uri) }
                entry<CompressorRoute> { route -> CompressorScreen(::back, shareService, adsService, initialUri = route.uri) }
                entry<ImageEditorRoute> { route -> ImageEditorScreen(::back, shareService, adsService, initialUri = route.uri) }
                entry<NoteEditorRoute> { route -> NoteEditorScreen(route.noteId, ::back, shareService) }
                entry<SearchRoute> { SearchScreen(::back, { push(NoteEditorRoute(it)) }, shareService) }
                entry<TrashRoute> { TrashScreen(::back) }
                entry<BackupRoute> { BackupScreen(::back, shareService) }
                entry<SettingsRoute> { SettingsScreen(::back) }
                entry<AiAssistRoute> { AiAssistScreen(::back) }
            },
        )
    }
}

@Composable
fun AppBottomBar(selected: MainTab, onSelect: (MainTab) -> Unit) {
    NavigationBar {
        MainTab.entries.forEach { item ->
            NavigationBarItem(
                selected = item == selected,
                onClick = { onSelect(item) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(stringResource(item.label)) },
            )
        }
    }
}

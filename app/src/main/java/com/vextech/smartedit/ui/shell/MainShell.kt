package com.vextech.smartedit.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
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
import com.vextech.smartedit.R
import com.vextech.smartedit.core.ads.UnityAdsService
import com.vextech.smartedit.core.sharing.ShareService
import com.vextech.smartedit.feature.admin.AdminScreen
import com.vextech.smartedit.feature.ai.AiAssistScreen
import com.vextech.smartedit.feature.backup.BackupScreen
import com.vextech.smartedit.feature.compressor.CompressorScreen
import com.vextech.smartedit.feature.converter.ConverterScreen
import com.vextech.smartedit.feature.history.HistoryScreen
import com.vextech.smartedit.feature.home.EditProIcons
import com.vextech.smartedit.feature.home.EditProTabBar
import com.vextech.smartedit.feature.home.EditProTabItem
import com.vextech.smartedit.feature.home.HomeScreen
import com.vextech.smartedit.feature.imageeditor.ImageEditorScreen
import com.vextech.smartedit.feature.notes.NoteEditorScreen
import com.vextech.smartedit.feature.ocr.OcrScreen
import com.vextech.smartedit.feature.pdf.PdfToolsScreen
import com.vextech.smartedit.feature.profile.ProfileScreen
import com.vextech.smartedit.feature.scanner.LiveCameraScreen
import com.vextech.smartedit.feature.scanner.ScannerScreen
import com.vextech.smartedit.feature.search.SearchScreen
import com.vextech.smartedit.feature.settings.SettingsScreen
import com.vextech.smartedit.feature.tools.ToolsTabScreen
import com.vextech.smartedit.feature.trash.TrashScreen
import com.vextech.smartedit.ui.navigation.ActivityRoute
import com.vextech.smartedit.ui.navigation.AdminRoute
import com.vextech.smartedit.ui.navigation.AiAssistRoute
import com.vextech.smartedit.ui.navigation.BackupRoute
import com.vextech.smartedit.ui.navigation.CompressorRoute
import com.vextech.smartedit.ui.navigation.ConverterRoute
import com.vextech.smartedit.ui.navigation.FilesTabRoute
import com.vextech.smartedit.ui.navigation.HomeRoute
import com.vextech.smartedit.ui.navigation.ImageEditorRoute
import com.vextech.smartedit.ui.navigation.LiveCameraRoute
import com.vextech.smartedit.ui.navigation.NoteEditorRoute
import com.vextech.smartedit.ui.navigation.OcrRoute
import com.vextech.smartedit.ui.navigation.PdfToolsRoute
import com.vextech.smartedit.ui.navigation.ProfileRoute
import com.vextech.smartedit.ui.navigation.ScannerRoute
import com.vextech.smartedit.ui.navigation.SearchRoute
import com.vextech.smartedit.ui.navigation.SettingsRoute
import com.vextech.smartedit.ui.navigation.ToolsTabRoute
import com.vextech.smartedit.ui.navigation.TrashRoute
import com.vextech.smartedit.ui.navigation.routeFor

/** The five tabs EditPro has, in its order. Library shows Activity (everything you scanned, converted and edited); My files shows Files, Notes and Favorites. */
enum class MainTab(val label: Int, val icon: ImageVector) {
    HOME(R.string.nav_home, EditProIcons.Home),
    TOOLS(R.string.nav_tools, EditProIcons.Tools),
    LIBRARY(R.string.nav_library, EditProIcons.Library),
    FILES(R.string.nav_my_files, EditProIcons.Files),
    SETTINGS(R.string.settings, EditProIcons.Settings),
}

/**
 * Bottom-navigation shell. Every tab keeps its own back stack. Tools open on top of the current tab and hide
 * the bar. Back pops the current stack; from a tab root it returns to Home; from Home it leaves the app.
 */
@Composable
fun MainShell(shareService: ShareService, adsService: UnityAdsService) {
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val homeStack = rememberNavBackStack(HomeRoute)
    val toolsStack = rememberNavBackStack(ToolsTabRoute)
    val libraryStack = rememberNavBackStack(ActivityRoute)
    val filesStack = rememberNavBackStack(FilesTabRoute)
    val settingsStack = rememberNavBackStack(SettingsRoute)
    val stack = when (tab) {
        MainTab.HOME -> homeStack
        MainTab.TOOLS -> toolsStack
        MainTab.LIBRARY -> libraryStack
        MainTab.FILES -> filesStack
        MainTab.SETTINGS -> settingsStack
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
                entry<HomeRoute> { HomeScreen(onOpen = ::push, onSeeAll = { tab = MainTab.TOOLS }, share = shareService) }
                entry<ToolsTabRoute> { ToolsTabScreen(onOpen = ::push) }
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
                entry<ProfileRoute> { ProfileScreen(onOpen = ::push, onBack = ::back) }

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
                entry<SettingsRoute> { SettingsScreen(onBack = null, onOpenProfile = { push(ProfileRoute) }, onOpenAdmin = { push(AdminRoute) }) }
                entry<AdminRoute> { AdminScreen(::back) }
                entry<AiAssistRoute> { AiAssistScreen(::back) }
            },
        )
    }
}

@Composable
fun AppBottomBar(selected: MainTab, onSelect: (MainTab) -> Unit) {
    val tabs = MainTab.entries
    EditProTabBar(
        items = tabs.map { EditProTabItem(stringResource(it.label), it.icon) },
        selectedIndex = tabs.indexOf(selected),
        onSelect = { onSelect(tabs[it]) },
        modifier = Modifier.navigationBarsPadding(),
    )
}

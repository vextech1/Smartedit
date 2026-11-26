package com.vextech.editpro.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.vextech.editpro.R
import com.vextech.editpro.core.ads.UnityAdsService
import com.vextech.editpro.core.sharing.ShareService
import com.vextech.editpro.feature.admin.AdminScreen
import com.vextech.editpro.feature.backup.BackupScreen
import com.vextech.editpro.feature.classlibrary.ClassLibraryScreen
import com.vextech.editpro.feature.compressor.CompressorScreen
import com.vextech.editpro.feature.converter.ConverterScreen
import com.vextech.editpro.feature.history.HistoryScreen
import com.vextech.editpro.feature.home.EditProIcons
import com.vextech.editpro.feature.home.EditProTabBar
import com.vextech.editpro.feature.home.EditProTabItem
import com.vextech.editpro.feature.home.HomeScreen
import com.vextech.editpro.feature.imageeditor.ImageEditorScreen
import com.vextech.editpro.feature.notes.NoteEditorScreen
import com.vextech.editpro.feature.ocr.OcrScreen
import com.vextech.editpro.feature.pdf.PdfToolsScreen
import com.vextech.editpro.feature.profile.ProfileScreen
import com.vextech.editpro.feature.scanner.LiveCameraScreen
import com.vextech.editpro.feature.scanner.ScannerScreen
import com.vextech.editpro.feature.search.SearchScreen
import com.vextech.editpro.feature.settings.SettingsScreen
import com.vextech.editpro.feature.tools.ToolsTabScreen
import com.vextech.editpro.feature.trash.TrashScreen
import com.vextech.editpro.ui.navigation.ActivityRoute
import com.vextech.editpro.ui.navigation.AdminRoute
import com.vextech.editpro.ui.navigation.LibraryUploadRoute
import com.vextech.editpro.ui.navigation.StudentFilesRoute
import com.vextech.editpro.feature.classlibrary.LibraryUploadScreen
import com.vextech.editpro.feature.classlibrary.StudentFilesScreen
import com.vextech.editpro.ui.navigation.BackupRoute
import com.vextech.editpro.ui.navigation.CompressorRoute
import com.vextech.editpro.ui.navigation.ConverterRoute
import com.vextech.editpro.ui.navigation.FilesTabRoute
import com.vextech.editpro.ui.navigation.HomeRoute
import com.vextech.editpro.ui.navigation.ImageEditorRoute
import com.vextech.editpro.ui.navigation.LibraryRoute
import com.vextech.editpro.ui.navigation.LiveCameraRoute
import com.vextech.editpro.ui.navigation.NoteEditorRoute
import com.vextech.editpro.ui.navigation.OcrRoute
import com.vextech.editpro.ui.navigation.PdfToolsRoute
import com.vextech.editpro.ui.navigation.ProfileRoute
import com.vextech.editpro.ui.navigation.ScannerRoute
import com.vextech.editpro.ui.navigation.SearchRoute
import com.vextech.editpro.ui.navigation.SettingsRoute
import com.vextech.editpro.ui.navigation.ToolsTabRoute
import com.vextech.editpro.ui.navigation.TrashRoute
import com.vextech.editpro.ui.navigation.message
import com.vextech.editpro.ui.navigation.routeFor
import com.vextech.editpro.ui.navigation.toRoute

/** The five tabs EditPro has, in its order. Library is the class library (notes, assignments and past papers); My files shows Files, Notes and Favorites. Activity lives under Profile. */
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
fun MainShell(
    shareService: ShareService,
    adsService: UnityAdsService,
    incoming: List<com.vextech.editpro.core.library.IncomingFile> = emptyList(),
    onIncomingHandled: () -> Unit = {},
) {
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val homeStack = rememberNavBackStack(HomeRoute)
    val toolsStack = rememberNavBackStack(ToolsTabRoute)
    val libraryStack = rememberNavBackStack(LibraryRoute)
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

    // Files shared to EditPro or opened with it: the same rules as the Upload card on Home, starting from the Home tab.
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(incoming) {
        if (incoming.isEmpty()) return@LaunchedEffect
        val plan = com.vextech.editpro.core.library.IntakePolicies.plan(incoming)
        onIncomingHandled()
        tab = MainTab.HOME
        while (homeStack.size > 1) homeStack.removeLastOrNull()
        plan.toRoute()?.let { homeStack.add(it) }
        plan.message()?.let { snackbar.showSnackbar(it.asString(context)) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
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
                entry<LibraryRoute> { ClassLibraryScreen(onUpload = { push(LibraryUploadRoute()) }, onOpenPdf = { uri -> push(PdfToolsRoute(uri)) }) }
                entry<ActivityRoute> { HistoryScreen(onBack = ::back, share = shareService) }
                entry<ProfileRoute> { ProfileScreen(onOpen = ::push, onBack = ::back) }

                entry<ScannerRoute> { ScannerScreen(::back, { push(LiveCameraRoute(it)) }, shareService, onEditMore = { push(ImageEditorRoute(it)) }) }
                entry<LiveCameraRoute> { route -> LiveCameraScreen(route.sessionId, ::back, shareService) }
                entry<OcrRoute> { route -> OcrScreen(::back, shareService, initialUri = route.uri) }
                entry<PdfToolsRoute> { route -> PdfToolsScreen(::back, shareService, adsService, initialUri = route.uri) }
                entry<ConverterRoute> { route -> ConverterScreen(::back, shareService, adsService, initialUri = route.uri, initialUris = route.uris, onOpenTool = ::push) }
                entry<CompressorRoute> { route -> CompressorScreen(::back, shareService, adsService, initialUri = route.uri) }
                entry<ImageEditorRoute> { route -> ImageEditorScreen(::back, shareService, adsService, initialUri = route.uri, onOpenTool = ::push) }
                entry<NoteEditorRoute> { route -> NoteEditorScreen(route.noteId, ::back, shareService) }
                entry<SearchRoute> { SearchScreen(::back, { push(NoteEditorRoute(it)) }, shareService) }
                entry<TrashRoute> { TrashScreen(::back) }
                entry<BackupRoute> { BackupScreen(::back, shareService) }
                entry<SettingsRoute> { SettingsScreen(onBack = null, onTrash = { push(TrashRoute) }, onOpenProfile = { push(ProfileRoute) }, onOpenAdmin = { push(AdminRoute) }) }
                entry<AdminRoute> { AdminScreen(::back, onOpen = ::push) }
                entry<LibraryUploadRoute> { route -> LibraryUploadScreen(route.admin, ::back) }
                entry<StudentFilesRoute> { StudentFilesScreen(::back) }
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

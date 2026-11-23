package com.vextech.editpro.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.vextech.editpro.core.ads.UnityAdsService
import com.vextech.editpro.core.library.IncomingFile
import com.vextech.editpro.core.sharing.ShareService
import com.vextech.editpro.feature.onboarding.OnboardingScreen
import com.vextech.editpro.feature.start.StartViewModel
import com.vextech.editpro.ui.shell.MainShell

/**
 * App level flow: Start decides between Onboarding (first launch only) and the Shell.
 * Everything the user touches after that lives inside [MainShell].
 */
@Composable
fun EditProNavigation(
    shareService: ShareService,
    adsService: UnityAdsService,
    incoming: List<IncomingFile> = emptyList(),
    onIncomingHandled: () -> Unit = {},
) {
    val rootStack = rememberNavBackStack(StartRoute)
    NavDisplay(
        backStack = rootStack,
        onBack = { if (rootStack.size > 1) rootStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<StartRoute> {
                val vm: StartViewModel = hiltViewModel()
                val done by vm.completed.collectAsState()
                LaunchedEffect(done) {
                    done?.let {
                        rootStack.clear()
                        rootStack.add(if (it) ShellRoute else OnboardingRoute)
                    }
                }
            }
            entry<OnboardingRoute> { OnboardingScreen(onComplete = { rootStack.clear(); rootStack.add(ShellRoute) }) }
            entry<ShellRoute> { MainShell(shareService, adsService, incoming, onIncomingHandled) }
        },
    )
}

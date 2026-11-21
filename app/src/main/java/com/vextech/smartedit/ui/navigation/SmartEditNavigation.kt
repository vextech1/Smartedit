package com.vextech.smartedit.ui.navigation

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
import com.vextech.smartedit.core.ads.UnityAdsService
import com.vextech.smartedit.core.sharing.ShareService
import com.vextech.smartedit.feature.onboarding.OnboardingScreen
import com.vextech.smartedit.feature.start.StartViewModel
import com.vextech.smartedit.ui.shell.MainShell

/**
 * App level flow: Start decides between Onboarding (first launch only) and the Shell.
 * Everything the user touches after that lives inside [MainShell].
 */
@Composable
fun SmartEditNavigation(shareService: ShareService, adsService: UnityAdsService) {
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
            entry<ShellRoute> { MainShell(shareService, adsService) }
        },
    )
}

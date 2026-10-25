package com.vextech.smart_edit

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vextech.smart_edit.core.ads.UnityAdsService
import com.vextech.smart_edit.core.model.AppSettings
import com.vextech.smart_edit.core.preferences.SettingsRepository
import com.vextech.smart_edit.core.sharing.ShareService
import com.vextech.smart_edit.ui.navigation.SmartEditNavigation
import com.vextech.smart_edit.ui.theme.SmartEditTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var shareService: ShareService
    @Inject lateinit var adsService: UnityAdsService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
            LaunchedEffect(settings.language) {
                val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                if (current != settings.language) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(settings.language))
                }
            }
            SmartEditTheme(settings.theme) {
                SmartEditNavigation(shareService = shareService, adsService = adsService)
            }
        }
    }
}

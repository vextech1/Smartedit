package com.vextech.smart_edit.core.preferences

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.vextech.smart_edit.core.model.AppSettings
import com.vextech.smart_edit.core.model.ThemePreference
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("smart_edit_settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val language = stringPreferencesKey("language")
        val notifications = booleanPreferencesKey("notifications")
        val autoBackup = booleanPreferencesKey("auto_backup")
        val confirmDelete = booleanPreferencesKey("confirm_delete")
        val exportFormat = stringPreferencesKey("export_format")
        val onboarding = booleanPreferencesKey("onboarding")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            theme = runCatching { ThemePreference.valueOf(p[Keys.theme] ?: "SYSTEM") }.getOrDefault(ThemePreference.SYSTEM),
            language = p[Keys.language] ?: "en",
            notificationsEnabled = p[Keys.notifications] ?: true,
            autoBackupEnabled = p[Keys.autoBackup] ?: false,
            confirmDeleteEnabled = p[Keys.confirmDelete] ?: true,
            defaultExportFormat = p[Keys.exportFormat] ?: "pdf",
            onboardingCompleted = p[Keys.onboarding] ?: false,
        )
    }

    suspend fun replace(settings: AppSettings) = update { settings }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val current = AppSettings(
                theme = runCatching { ThemePreference.valueOf(p[Keys.theme] ?: "SYSTEM") }.getOrDefault(ThemePreference.SYSTEM),
                language = p[Keys.language] ?: "en",
                notificationsEnabled = p[Keys.notifications] ?: true,
                autoBackupEnabled = p[Keys.autoBackup] ?: false,
                confirmDeleteEnabled = p[Keys.confirmDelete] ?: true,
                defaultExportFormat = p[Keys.exportFormat] ?: "pdf",
                onboardingCompleted = p[Keys.onboarding] ?: false,
            )
            val next = transform(current)
            p[Keys.theme] = next.theme.name
            p[Keys.language] = next.language
            p[Keys.notifications] = next.notificationsEnabled
            p[Keys.autoBackup] = next.autoBackupEnabled
            p[Keys.confirmDelete] = next.confirmDeleteEnabled
            p[Keys.exportFormat] = next.defaultExportFormat
            p[Keys.onboarding] = next.onboardingCompleted
        }
    }
}

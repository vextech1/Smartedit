package com.vextech.smart_edit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import com.vextech.smart_edit.core.model.ThemePreference

@Composable
fun SmartEditTheme(preference: ThemePreference = ThemePreference.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (preference) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = Typography(), content = content)
}

package com.vextech.smart_edit.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// App level
@Serializable data object StartRoute : NavKey
@Serializable data object OnboardingRoute : NavKey
@Serializable data object ShellRoute : NavKey

// Tab roots (bottom navigation)
@Serializable data object HomeRoute : NavKey
@Serializable data object FilesTabRoute : NavKey
@Serializable data object ActivityRoute : NavKey
@Serializable data object ProfileRoute : NavKey

// Tools and pushed screens
@Serializable data object ScannerRoute : NavKey
@Serializable data class LiveCameraRoute(val sessionId: String) : NavKey
@Serializable data class OcrRoute(val uri: String? = null) : NavKey
@Serializable data class PdfToolsRoute(val uri: String? = null) : NavKey
@Serializable data class ConverterRoute(val uri: String? = null) : NavKey
@Serializable data class CompressorRoute(val uri: String? = null) : NavKey
@Serializable data class ImageEditorRoute(val uri: String? = null) : NavKey
@Serializable data class NoteEditorRoute(val noteId: String? = null) : NavKey
@Serializable data object SearchRoute : NavKey
@Serializable data object TrashRoute : NavKey
@Serializable data object BackupRoute : NavKey
@Serializable data object SettingsRoute : NavKey
@Serializable data object AiAssistRoute : NavKey

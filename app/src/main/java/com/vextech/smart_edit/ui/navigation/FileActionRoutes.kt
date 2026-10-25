package com.vextech.smart_edit.ui.navigation

import androidx.navigation3.runtime.NavKey
import com.vextech.smart_edit.core.library.FileAction

/** The tool screen that handles a file-menu action, opened with the chosen managed file. */
fun FileAction.routeFor(uri: String): NavKey = when (this) {
    FileAction.EDIT_PDF -> PdfToolsRoute(uri)
    FileAction.EDIT_IMAGE -> ImageEditorRoute(uri)
    FileAction.EXTRACT_TEXT -> OcrRoute(uri)
    FileAction.CONVERT -> ConverterRoute(uri)
    FileAction.COMPRESS -> CompressorRoute(uri)
}

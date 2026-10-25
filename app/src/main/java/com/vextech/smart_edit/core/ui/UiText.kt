package com.vextech.smart_edit.core.ui

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * A user-visible message that ViewModels can emit without touching Android resources.
 * Resolve it at the edge (screen) with [asString].
 */
sealed interface UiText {
    /** Already-localized or externally supplied text (for example a provider error message). */
    data class Dynamic(val value: String) : UiText

    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Plural(@PluralsRes val id: Int, val count: Int, val args: List<Any> = emptyList()) : UiText

    fun asString(context: Context): String = when (this) {
        is Dynamic -> value
        is Res -> context.getString(id, *args.toTypedArray())
        is Plural -> context.resources.getQuantityString(id, count, *args.toTypedArray())
    }

    @Composable
    fun asString(): String = asString(LocalContext.current)

    companion object {
        fun res(@StringRes id: Int, vararg args: Any): UiText = Res(id, args.toList())
        fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): UiText = Plural(id, count, args.toList())
        fun dynamic(value: String): UiText = Dynamic(value)
    }
}

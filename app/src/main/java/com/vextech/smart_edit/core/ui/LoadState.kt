package com.vextech.smart_edit.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Standard shape for data that loads asynchronously. */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Success<T>(val data: T) : LoadState<T>
    data class Failure(val message: UiText) : LoadState<Nothing>
}

/** Renders loading and failure for a [LoadState]; calls [content] only on success. */
@Composable
fun <T> LoadStateContent(
    state: LoadState<T>,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        LoadState.Loading -> LoadingState(modifier)
        is LoadState.Failure -> ErrorState(state.message.asString(), modifier, onRetry)
        is LoadState.Success -> content(state.data)
    }
}

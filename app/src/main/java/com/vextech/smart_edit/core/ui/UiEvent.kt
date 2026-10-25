package com.vextech.smart_edit.core.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** One-off effects. They never live inside UiState, so they are not replayed after rotation. */
sealed interface UiEvent {
    data class Message(val text: UiText, val action: UiAction? = null) : UiEvent
}

data class UiAction(val label: UiText, val id: String)

/** Owned by a ViewModel. Each event is delivered to exactly one collector. */
class UiEventChannel {
    private val channel = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = channel.receiveAsFlow()

    fun send(event: UiEvent) {
        channel.trySend(event)
    }

    fun message(text: UiText, action: UiAction? = null) = send(UiEvent.Message(text, action))
}

/** Shows [UiEvent.Message] events as snackbars, one at a time. */
@Composable
fun CollectUiEvents(
    events: Flow<UiEvent>,
    snackbarHostState: SnackbarHostState,
    onAction: (String) -> Unit = {},
) {
    val context = LocalContext.current
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is UiEvent.Message -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.text.asString(context),
                        actionLabel = event.action?.label?.asString(context),
                    )
                    if (result == SnackbarResult.ActionPerformed) event.action?.let { onAction(it.id) }
                }
            }
        }
    }
}

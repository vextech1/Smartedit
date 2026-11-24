package com.vextech.editpro.feature.pdf

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.vextech.editpro.R

/**
 * Reading mode, as on the site: the whole screen shows only the document, with one Exit button on top.
 * Nothing in the document changes. Exit and the system Back button both leave, and the page you were reading stays selected.
 * The status and navigation bars are hidden while it is open and come back when it closes.
 */
@Composable
fun ReadingScreen(state: PdfEditorUiState, startPage: Int, onPageChanged: (Int) -> Unit, loadPage: (Int) -> Unit, onExit: () -> Unit) {
    BackHandler(onBack = onExit)
    HideSystemBars()

    val list = rememberLazyListState(initialFirstVisibleItemIndex = startPage.coerceIn(0, (state.pages.size - 1).coerceAtLeast(0)))
    val reading by remember { derivedStateOf { list.firstVisibleItemIndex } }
    LaunchedEffect(reading) { onPageChanged(reading) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onExit) {
                    Icon(Icons.Rounded.Close, contentDescription = null)
                    Text(stringResource(R.string.reading_exit), Modifier.padding(start = 6.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        state.session?.sourceName.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.reading_page_of, reading + 1, state.pages.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(state.pages, key = { _, page -> page.index }) { index, page ->
                    LaunchedEffect(state.session?.id, index) { loadPage(index) }
                    val rotated = page.rotation == 90 || page.rotation == 270
                    val ratio = if (rotated) page.heightPoints / page.widthPoints else page.widthPoints / page.heightPoints
                    val description = stringResource(R.string.reading_page_description, index + 1, state.pages.size)
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(ratio.coerceAtLeast(.1f)).background(Color.White)
                            .semantics { contentDescription = description },
                    ) {
                        PdfPreviewImage(state.previews[index], Modifier.fillMaxSize())
                    }
                }
                item { Box(Modifier.navigationBarsPadding()) }
            }
        }
    }
}

/** Hides the system bars while the reader is on screen and puts them back afterwards. */
@Composable
private fun HideSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

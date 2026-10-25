package com.vextech.smart_edit.feature.history

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vextech.smart_edit.R
import com.vextech.smart_edit.core.di.IoDispatcher
import com.vextech.smart_edit.core.files.DocumentStore
import com.vextech.smart_edit.core.library.HistoryEntry
import com.vextech.smart_edit.core.library.HistoryDayGroup
import com.vextech.smart_edit.core.library.HistoryExportFormat
import com.vextech.smart_edit.core.library.HistoryExportFormatter
import com.vextech.smart_edit.core.library.HistoryFilter
import com.vextech.smart_edit.core.library.HistoryPolicies
import com.vextech.smart_edit.core.model.AppSettings
import com.vextech.smart_edit.core.preferences.SettingsRepository
import com.vextech.smart_edit.core.repository.HistoryRepository
import com.vextech.smart_edit.core.ui.UiEventChannel
import com.vextech.smart_edit.core.ui.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class HistoryUiState(
    val loading: Boolean = true,
    val filter: HistoryFilter = HistoryFilter.ALL,
    val groups: List<HistoryDayGroup> = emptyList(),
    val totalCount: Int = 0,
)

data class ExportedHistory(val file: File, val mimeType: String)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: HistoryRepository,
    private val store: DocumentStore,
    settingsRepository: SettingsRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val filter = MutableStateFlow(HistoryFilter.ALL)
    private val events = UiEventChannel()
    private val exportChannel = Channel<ExportedHistory>(Channel.BUFFERED)
    val uiEvents = events.events
    val exports = exportChannel.receiveAsFlow()

    private val entries: StateFlow<List<HistoryEntry>?> =
        repository.entries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<HistoryUiState> = combine(entries, filter) { all, selected ->
        if (all == null) {
            HistoryUiState(filter = selected)
        } else {
            val zone = ZoneId.systemDefault()
            val groups = HistoryPolicies.groupByDay(HistoryPolicies.filter(all, selected), zone, LocalDate.now(zone))
            HistoryUiState(false, selected, groups, all.size)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    private val settings = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /** Export formats with the user's default first. */
    val exportFormats: List<HistoryExportFormat>
        get() {
            val default = HistoryExportFormatter.normalize(settings.value.defaultExportFormat)
            return listOf(default) + HistoryExportFormat.entries.filter { it != default }
        }

    fun onFilter(value: HistoryFilter) { filter.value = value }

    fun clear() {
        viewModelScope.launch {
            try {
                repository.clear()
                events.message(UiText.res(R.string.history_cleared))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                events.message(UiText.res(R.string.history_action_failed))
            }
        }
    }

    /** Exports the whole activity list (not just the active filter). [pdfTitle] is already localized. */
    fun export(format: HistoryExportFormat, pdfTitle: String) {
        val all = entries.value.orEmpty()
        if (all.isEmpty()) {
            events.message(UiText.res(R.string.history_export_empty))
            return
        }
        viewModelScope.launch {
            try {
                val file = withContext(io) { write(format, all, pdfTitle) }
                exportChannel.trySend(ExportedHistory(file, format.mimeType))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                events.message(UiText.res(R.string.history_export_failed))
            }
        }
    }

    private fun write(format: HistoryExportFormat, all: List<HistoryEntry>, pdfTitle: String): File {
        val file = store.temporaryFile("activity_${System.currentTimeMillis()}.${format.extension}")
        val zone = ZoneId.systemDefault()
        when (format) {
            HistoryExportFormat.CSV -> file.writeText(HistoryExportFormatter.csv(all))
            HistoryExportFormat.JSON -> file.writeText(HistoryExportFormatter.json(all))
            HistoryExportFormat.TXT -> file.writeText(HistoryExportFormatter.text(all, zone))
            HistoryExportFormat.PDF -> writePdf(file, HistoryExportFormatter.wrap(HistoryExportFormatter.textLines(all, zone), PDF_LINE_CHARS), pdfTitle)
        }
        return file
    }

    private fun writePdf(file: File, lines: List<String>, title: String) {
        val pdf = PdfDocument()
        try {
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f; isFakeBoldText = true }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f }
            var pageNumber = 1
            var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            var y = MARGIN_TOP
            page.canvas.drawText(title, MARGIN_LEFT, y, titlePaint)
            y += 36f
            for (line in lines) {
                if (y > PAGE_HEIGHT - MARGIN_BOTTOM) {
                    pdf.finishPage(page)
                    pageNumber += 1
                    page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                    y = MARGIN_TOP
                }
                page.canvas.drawText(line, MARGIN_LEFT, y, bodyPaint)
                y += LINE_HEIGHT
            }
            pdf.finishPage(page)
            file.outputStream().use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
    }

    private companion object {
        const val PAGE_WIDTH = 800
        const val PAGE_HEIGHT = 1120
        const val MARGIN_LEFT = 42f
        const val MARGIN_TOP = 52f
        const val MARGIN_BOTTOM = 50f
        const val LINE_HEIGHT = 18f
        const val PDF_LINE_CHARS = 100
    }
}

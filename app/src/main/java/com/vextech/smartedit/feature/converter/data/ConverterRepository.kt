package com.vextech.smartedit.feature.converter.data

import com.vextech.smartedit.feature.converter.domain.ConversionJob
import com.vextech.smartedit.feature.converter.domain.ConversionRequest
import com.vextech.smartedit.feature.converter.domain.ConversionResult
import com.vextech.smartedit.feature.converter.domain.ConverterSource
import com.vextech.smartedit.feature.converter.domain.PdfInspection
import kotlinx.coroutines.flow.Flow

interface ConverterRepository {
    fun observeJobs(): Flow<List<ConversionJob>>
    /** Opens the PDF with [password] (blank = none) to learn its page count and whether it needs a password. */
    suspend fun inspectPdf(source: ConverterSource, password: String): PdfInspection
    suspend fun convert(request: ConversionRequest, onProgress: suspend (Float) -> Unit = {}): ConversionResult
}

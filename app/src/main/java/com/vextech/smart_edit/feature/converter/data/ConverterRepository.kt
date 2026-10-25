package com.vextech.smart_edit.feature.converter.data

import com.vextech.smart_edit.feature.converter.domain.ConversionJob
import com.vextech.smart_edit.feature.converter.domain.ConversionRequest
import com.vextech.smart_edit.feature.converter.domain.ConversionResult
import kotlinx.coroutines.flow.Flow

interface ConverterRepository {
    fun observeJobs(): Flow<List<ConversionJob>>
    suspend fun convert(request: ConversionRequest, onProgress: suspend (Float) -> Unit = {}): ConversionResult
}

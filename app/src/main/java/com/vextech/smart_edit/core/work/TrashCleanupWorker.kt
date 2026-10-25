package com.vextech.smart_edit.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vextech.smart_edit.core.repository.TrashRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class TrashCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val trash: TrashRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        trash.purgeExpired()
        Result.success()
    }.getOrElse { Result.retry() }
}

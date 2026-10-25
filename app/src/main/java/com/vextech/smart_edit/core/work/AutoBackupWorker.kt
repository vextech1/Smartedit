package com.vextech.smart_edit.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vextech.smart_edit.core.backup.BackupService
import com.vextech.smart_edit.core.notifications.NotificationService
import com.vextech.smart_edit.core.preferences.SettingsRepository
import kotlinx.coroutines.flow.first
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backups: BackupService,
    private val settings: SettingsRepository,
    private val notifications: NotificationService,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = runCatching {
        val backup = backups.createBackup()
        if (settings.settings.first().notificationsEnabled) {
            notifications.show(2001, "Smart Edit backup complete", backup.name)
        }
        Result.success()
    }.getOrElse { Result.retry() }
}

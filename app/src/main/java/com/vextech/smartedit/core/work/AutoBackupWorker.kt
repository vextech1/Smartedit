package com.vextech.smartedit.core.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vextech.smartedit.core.backup.BackupService
import com.vextech.smartedit.core.notifications.NotificationService
import com.vextech.smartedit.core.preferences.SettingsRepository
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
            notifications.show(2001, "EditPro backup complete", backup.name)
        }
        Result.success()
    }.getOrElse { Result.retry() }
}

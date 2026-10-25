package com.vextech.smart_edit.core.work

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    fun setAutoBackup(enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) { wm.cancelUniqueWork("smart-edit-auto-backup"); return }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build()).build()
        wm.enqueueUniquePeriodicWork("smart-edit-auto-backup", ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun scheduleMaintenance() {
        val request = PeriodicWorkRequestBuilder<TrashCleanupWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "smart-edit-trash-cleanup",
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}

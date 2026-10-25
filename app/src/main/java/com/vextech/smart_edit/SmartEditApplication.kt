package com.vextech.smart_edit

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.vextech.smart_edit.core.ads.UnityAdsService
import com.vextech.smart_edit.core.notifications.NotificationService
import com.vextech.smart_edit.core.work.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SmartEditApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifications: NotificationService
    @Inject lateinit var ads: UnityAdsService
    @Inject lateinit var workScheduler: WorkScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notifications.createChannels()
        ads.initialize()
        workScheduler.scheduleMaintenance()
    }
}

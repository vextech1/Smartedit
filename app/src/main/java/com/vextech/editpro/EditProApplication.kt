package com.vextech.editpro

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.vextech.editpro.core.ads.UnityAdsService
import com.vextech.editpro.core.notifications.NotificationService
import com.vextech.editpro.core.work.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class EditProApplication : Application(), Configuration.Provider {
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

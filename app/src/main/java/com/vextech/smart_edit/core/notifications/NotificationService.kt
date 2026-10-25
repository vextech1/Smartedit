package com.vextech.smart_edit.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vextech.smart_edit.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationService @Inject constructor(@ApplicationContext private val context: Context) {
    companion object { const val CHANNEL_OPERATIONS = "smart_edit_operations" }
    fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                NotificationChannel(CHANNEL_OPERATIONS, "Document operations", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }
    fun show(id: Int, title: String, text: String) {
        val n = NotificationCompat.Builder(context, CHANNEL_OPERATIONS)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text)
            .setAutoCancel(true).build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }
}

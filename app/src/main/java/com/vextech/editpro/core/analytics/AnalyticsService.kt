package com.vextech.editpro.core.analytics

import android.os.Bundle
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Singleton
class AnalyticsService(private val analytics: FirebaseAnalytics?) {
    constructor(@ApplicationContext context: Context) : this(
        FirebaseApp.getApps(context)
            .firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?.let { FirebaseAnalytics.getInstance(context) }
    )

    fun event(name: String, params: Map<String, String> = emptyMap()) {
        analytics?.logEvent(name.take(40), Bundle().apply { params.forEach { (k, v) -> putString(k.take(40), v.take(100)) } })
    }
}

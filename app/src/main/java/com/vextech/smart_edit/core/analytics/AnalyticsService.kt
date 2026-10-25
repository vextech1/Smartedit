package com.vextech.smart_edit.core.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsService @Inject constructor(private val analytics: FirebaseAnalytics) {
    fun event(name: String, params: Map<String, String> = emptyMap()) {
        analytics.logEvent(name.take(40), Bundle().apply { params.forEach { (k, v) -> putString(k.take(40), v.take(100)) } })
    }
}

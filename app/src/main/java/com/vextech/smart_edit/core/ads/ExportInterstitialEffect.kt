package com.vextech.smart_edit.core.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
fun ExportInterstitialEffect(
    outputKey: String?,
    ads: UnityAdsService,
) {
    val activity = LocalContext.current.findActivity()
    var lastHandled by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(outputKey, activity) {
        val key = outputKey ?: return@LaunchedEffect
        if (key == lastHandled) return@LaunchedEffect
        lastHandled = key
        activity?.let(ads::tryShowInterstitial)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

package com.vextech.smart_edit.core.ads

import android.app.Activity
import android.content.Context
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsLoadOptions
import com.unity3d.ads.UnityAdsShowOptions
import com.vextech.smart_edit.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnityAdsService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile private var initialized = false
    @Volatile private var interstitialReady = false
    @Volatile private var rewardedReady = false

    fun initialize() {
        if (initialized || BuildConfig.UNITY_GAME_ID.isBlank()) return
        UnityAds.initialize(
            context,
            BuildConfig.UNITY_GAME_ID,
            BuildConfig.UNITY_TEST_MODE,
            object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() {
                    initialized = true
                    loadInterstitial()
                    loadRewarded()
                }

                override fun onInitializationFailed(
                    error: UnityAds.UnityAdsInitializationError,
                    message: String,
                ) {
                    initialized = false
                    interstitialReady = false
                    rewardedReady = false
                }
            },
        )
    }

    fun loadInterstitial() {
        if (!initialized || BuildConfig.UNITY_INTERSTITIAL_ID.isBlank()) return
        UnityAds.load(
            BuildConfig.UNITY_INTERSTITIAL_ID,
            UnityAdsLoadOptions(),
            object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    interstitialReady = true
                }

                override fun onUnityAdsFailedToLoad(
                    placementId: String,
                    error: UnityAds.UnityAdsLoadError,
                    message: String,
                ) {
                    interstitialReady = false
                }
            },
        )
    }

    fun tryShowInterstitial(activity: Activity): Boolean {
        if (!initialized || !interstitialReady) {
            loadInterstitial()
            return false
        }
        interstitialReady = false
        UnityAds.show(
            activity,
            BuildConfig.UNITY_INTERSTITIAL_ID,
            UnityAdsShowOptions(),
            object : IUnityAdsShowListener {
                override fun onUnityAdsShowFailure(
                    placementId: String,
                    error: UnityAds.UnityAdsShowError,
                    message: String,
                ) {
                    loadInterstitial()
                }

                override fun onUnityAdsShowStart(placementId: String) = Unit
                override fun onUnityAdsShowClick(placementId: String) = Unit
                override fun onUnityAdsShowComplete(
                    placementId: String,
                    state: UnityAds.UnityAdsShowCompletionState,
                ) {
                    loadInterstitial()
                }
            },
        )
        return true
    }

    fun loadRewarded() {
        if (!initialized || BuildConfig.UNITY_REWARDED_ID.isBlank()) return
        UnityAds.load(
            BuildConfig.UNITY_REWARDED_ID,
            UnityAdsLoadOptions(),
            object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String) {
                    rewardedReady = true
                }

                override fun onUnityAdsFailedToLoad(
                    placementId: String,
                    error: UnityAds.UnityAdsLoadError,
                    message: String,
                ) {
                    rewardedReady = false
                }
            },
        )
    }

    fun tryShowRewarded(activity: Activity, onReward: () -> Unit): Boolean {
        if (!initialized || !rewardedReady) {
            loadRewarded()
            return false
        }
        rewardedReady = false
        UnityAds.show(
            activity,
            BuildConfig.UNITY_REWARDED_ID,
            UnityAdsShowOptions(),
            object : IUnityAdsShowListener {
                override fun onUnityAdsShowFailure(
                    placementId: String,
                    error: UnityAds.UnityAdsShowError,
                    message: String,
                ) {
                    loadRewarded()
                }

                override fun onUnityAdsShowStart(placementId: String) = Unit
                override fun onUnityAdsShowClick(placementId: String) = Unit
                override fun onUnityAdsShowComplete(
                    placementId: String,
                    state: UnityAds.UnityAdsShowCompletionState,
                ) {
                    if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) onReward()
                    loadRewarded()
                }
            },
        )
        return true
    }
}

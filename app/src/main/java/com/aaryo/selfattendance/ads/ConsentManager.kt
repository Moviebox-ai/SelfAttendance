package com.aaryo.selfattendance.ads

import android.app.Activity
import android.util.Log
import com.aaryo.selfattendance.BuildConfig
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

object ConsentManager {

    private const val TAG = "ConsentManager"

    @Volatile
    private var mobileAdsInitialized = false

    fun requestConsentAndInitAds(
        activity: Activity,
        onFinished: () -> Unit
    ) {
        // Amazon Fire devices may not have Google Play Services / UMP runtime.
        // Skip the consent flow and initialize MobileAds directly.
        if (BuildConfig.IS_AMAZON) {
            Log.d(TAG, "Amazon build — skipping UMP, initializing MobileAds directly")
            initMobileAdsIfNeeded(activity) { onFinished() }
            return
        }

        try {
            val consentInfo = UserMessagingPlatform.getConsentInformation(activity)

            val params = ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false)
                .build()

            consentInfo.requestConsentInfoUpdate(
                activity,
                params,
                {
                    Log.d(TAG, "Consent status: ${consentInfo.consentStatus}")
                    try {
                        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                            if (formError != null) {
                                Log.w(TAG, "Consent form error: ${formError.message}")
                            }
                            initMobileAdsIfNeeded(activity) { onFinished() }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Consent form load failed", e)
                        initMobileAdsIfNeeded(activity) { onFinished() }
                    }
                },
                { requestError ->
                    Log.w(TAG, "Consent update failed: ${requestError.message} — proceeding anyway")
                    initMobileAdsIfNeeded(activity) { onFinished() }
                }
            )
        } catch (e: Exception) {
            // UMP threw on init (e.g. missing GMS on Amazon). Fall through gracefully.
            Log.e(TAG, "UMP init exception — falling back to direct MobileAds init", e)
            initMobileAdsIfNeeded(activity) { onFinished() }
        }
    }

    private fun initMobileAdsIfNeeded(
        activity: Activity,
        onDone: () -> Unit
    ) {
        if (mobileAdsInitialized) {
            onDone()
            return
        }

        try {
            com.google.android.gms.ads.MobileAds.initialize(activity.applicationContext) {
                mobileAdsInitialized = true
                // Signal AdsController so all ad load methods are unblocked
                AdsController.onMobileAdsReady(activity.applicationContext)
                // Initialise rewarded ad manager (requires MobileAds ready)
                AdsController.initRewardedAds(activity.applicationContext)
                Log.d(TAG, "MobileAds initialized")
                onDone()
            }
        } catch (e: Exception) {
            // MobileAds.initialize() can throw if GMS is unavailable.
            // Mark as initialized so we don't retry forever; ads simply won't load.
            mobileAdsInitialized = true
            Log.e(TAG, "MobileAds init failed — ads disabled for this session", e)
            // composables don't stay blank forever. Individual ad loads will
            // fail gracefully on their own if GMS is truly unavailable.
            AdsController.onMobileAdsReady(activity.applicationContext)
            onDone()
        }
    }

    fun canShowAds(activity: Activity): Boolean {
        // On Amazon, always allow — consent was not collected via UMP
        if (BuildConfig.IS_AMAZON) return true

        return try {
            val status = UserMessagingPlatform
                .getConsentInformation(activity)
                .consentStatus

            val canShow = status == ConsentInformation.ConsentStatus.OBTAINED ||
                          status == ConsentInformation.ConsentStatus.NOT_REQUIRED ||
                          status == ConsentInformation.ConsentStatus.UNKNOWN

            Log.d(TAG, "canShowAds=$canShow  status=$status")
            canShow
        } catch (e: Exception) {
            Log.e(TAG, "canShowAds check failed — defaulting to true", e)
            true
        }
    }

    fun requestConsent(activity: Activity, onFinished: () -> Unit) {
        requestConsentAndInitAds(activity, onFinished)
    }
}

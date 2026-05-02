package com.aaryo.selfattendance.ads

import android.app.Activity
import android.util.Log
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
        val consentInfo = UserMessagingPlatform.getConsentInformation(activity)

        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInfo.requestConsentInfoUpdate(
            activity,
            params,
            {
                Log.d(TAG, "Consent status: ${consentInfo.consentStatus}")

                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form error: ${formError.message}")
                    }
                    initMobileAdsIfNeeded(activity) { onFinished() }
                }
            },
            { requestError ->
                Log.w(TAG, "Consent update failed: ${requestError.message} — proceeding anyway")
                initMobileAdsIfNeeded(activity) { onFinished() }
            }
        )
    }

    private fun initMobileAdsIfNeeded(
        activity: Activity,
        onDone: () -> Unit
    ) {
        if (mobileAdsInitialized) {
            onDone()
            return
        }

        com.google.android.gms.ads.MobileAds.initialize(activity.applicationContext) {
            mobileAdsInitialized = true
            Log.d(TAG, "MobileAds initialized")
            onDone()
        }
    }

    // UNKNOWN status is allowed — common in India where consent form may not appear.
    fun canShowAds(activity: Activity): Boolean {
        val status = UserMessagingPlatform
            .getConsentInformation(activity)
            .consentStatus

        val canShow = status == ConsentInformation.ConsentStatus.OBTAINED ||
                      status == ConsentInformation.ConsentStatus.NOT_REQUIRED ||
                      status == ConsentInformation.ConsentStatus.UNKNOWN

        Log.d(TAG, "canShowAds=$canShow  status=$status")
        return canShow
    }

    fun requestConsent(activity: Activity, onFinished: () -> Unit) {
        requestConsentAndInitAds(activity, onFinished)
    }
}

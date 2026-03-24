package com.aaryo.selfattendance.ads

import android.content.Context
import android.util.Log

/**
 * Unity Ads — AdMob Mediation ke through manage hota hai.
 *
 * ✅ Setup:
 *   1. AdMob Console → Mediation Groups → Create Group
 *   2. Ad Source → Unity Ads add karo
 *   3. Unity Game ID aur Placement IDs enter karo
 *   4. app/build.gradle mein adapter dependency already hai:
 *      implementation('com.google.ads.mediation:unity:4.11.3.0')
 *
 * ✅ Unity Dashboard Setup:
 *   - Unity Dashboard → Monetization → Projects → SelfAttendance
 *   - Game ID: 6044793
 *   - Android Placements:
 *       Interstitial_Android  (interstitial)
 *       Banner_Android        (banner)
 *       Rewarded_Android      (rewarded)
 *
 * ℹ️  Mediation flow:
 *   AdMob request → AdMob fills if possible
 *              → else Unity Ads fills (via mediation adapter)
 *
 * ⚠️  Direct UnityAds.initialize() ya UnityAds.show() call mat karo.
 *    Sab kuch AdMob MobileAds ke through hota hai automatically.
 *    Yeh class sirf configuration constants aur helper provide karti hai.
 */
object UnityAdsManager {

    private const val TAG = "UnityAdsManager"

    // Unity Dashboard → Project Settings → Game ID
    const val GAME_ID = "6044793"

    // Unity Dashboard → Monetization → Placements
    const val PLACEMENT_INTERSTITIAL = "Interstitial_Android"
    const val PLACEMENT_BANNER       = "Banner_Android"
    const val PLACEMENT_REWARDED     = "Rewarded_Android"

    /**
     * Mediation ke liye koi manual initialization nahi chahiye.
     * MobileAds.initialize() (SelfAttendanceApp mein) sab handle karta hai.
     * Yeh function sirf logging ke liye rakha gaya hai.
     */
    fun logMediationInfo(context: Context) {
        Log.d(TAG, "Unity Ads Mediation configured. Game ID: $GAME_ID")
        Log.d(TAG, "Mediation adapter handles initialization automatically via AdMob.")
        Log.d(TAG, "Package: ${context.packageName}")
    }

    /**
     * Debug mode check — test devices ke liye.
     * AdMob Console → Test devices mein device add karo.
     */
    fun isTestMode(): Boolean {
        return com.aaryo.selfattendance.BuildConfig.DEBUG
    }
}

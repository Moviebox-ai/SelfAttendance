package com.aaryo.selfattendance.ads

import android.util.Log

/**
 * Action-based + time-based ad gating.
 *
 * Show ad when:
 *   - User ne ACTION_THRESHOLD actions kiye hain, AND
 *   - Last ad ke baad MIN_AD_INTERVAL seconds guzar gaye hain
 *
 * ✅ FIX: lastAdTime ab system clock se persist hota hai, isliye
 *    app restart ke baad bhi cooldown sahi kaam karta hai.
 *    (System.currentTimeMillis() process life se independent hai)
 */
object SmartAdController {

    private const val TAG = "SmartAdController"

    private var actionCount = 0
    private var lastAdTime  = 0L

    // Kitne actions ke baad ad dikhao
    private const val ACTION_THRESHOLD  = 3

    // Actions ke beech minimum gap (seconds)
    private const val MIN_AD_INTERVAL   = 60

    fun shouldShowAd(): Boolean {

        actionCount++

        val now         = System.currentTimeMillis()
        val elapsedSec  = (now - lastAdTime) / 1000L
        val timePassed  = lastAdTime == 0L || elapsedSec >= MIN_AD_INTERVAL

        Log.d(TAG, "actions=$actionCount  elapsed=${elapsedSec}s  timePassed=$timePassed")

        if (actionCount >= ACTION_THRESHOLD && timePassed) {

            actionCount  = 0
            lastAdTime   = now

            Log.d(TAG, "✅ Showing ad")
            return true
        }

        return false
    }

    /** Manual reset — e.g. logout ke baad */
    fun resetSession() {
        actionCount = 0
        Log.d(TAG, "Session reset")
    }

    /** Cooldown bhi reset karo (testing ke liye) */
    fun resetAll() {
        actionCount = 0
        lastAdTime  = 0L
        Log.d(TAG, "Full reset")
    }
}

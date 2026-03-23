package com.aaryo.selfattendance.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.aaryo.selfattendance.BuildConfig
import com.aaryo.selfattendance.R
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdManager {

    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false

    private var lastAdShownTime = 0L
    private const val AD_COOLDOWN = 45000L

    // Google Test Ad
    private const val TEST_AD_ID =
        "ca-app-pub-3940256099942544/1033173712"

    // ---------------- LOAD AD ----------------

    fun loadAd(context: Context) {

        if (isLoading || interstitialAd != null) return

        val adUnitId =
            if (BuildConfig.DEBUG) {
                TEST_AD_ID
            } else {
                context.getString(R.string.interstitial_ad_unit_id)
            }

        try {

            isLoading = true

            val adRequest = AdRequest.Builder().build()

            InterstitialAd.load(
                context.applicationContext,
                adUnitId,
                adRequest,

                object : InterstitialAdLoadCallback() {

                    override fun onAdLoaded(ad: InterstitialAd) {

                        Log.d("AdManager", "Interstitial loaded")

                        interstitialAd = ad
                        isLoading = false

                        setCallbacks(context)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {

                        Log.e(
                            "AdManager",
                            "Ad load failed: ${error.message}"
                        )

                        interstitialAd = null
                        isLoading = false
                    }
                }
            )

        } catch (e: Exception) {

            Log.e("AdManager", "Ad load crash", e)

            interstitialAd = null
            isLoading = false
        }
    }

    // ---------------- CALLBACKS ----------------

    private fun setCallbacks(context: Context) {

        interstitialAd?.fullScreenContentCallback =
            object : FullScreenContentCallback() {

                override fun onAdDismissedFullScreenContent() {

                    Log.d("AdManager", "Ad dismissed")

                    interstitialAd = null
                    loadAd(context)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {

                    Log.e(
                        "AdManager",
                        "Ad show failed: ${error.message}"
                    )

                    interstitialAd = null
                    loadAd(context)
                }

                override fun onAdShowedFullScreenContent() {

                    Log.d("AdManager", "Ad shown")

                    lastAdShownTime = System.currentTimeMillis()

                    interstitialAd = null
                }
            }
    }

    // ---------------- SHOW AD ----------------

    fun showAd(activity: Activity) {

        try {

            if (activity.isFinishing || activity.isDestroyed) {

                Log.d("AdManager", "Activity invalid")
                return
            }

            val now = System.currentTimeMillis()

            if (now - lastAdShownTime < AD_COOLDOWN) {

                Log.d("AdManager", "Ad cooldown active")
                return
            }

            if (interstitialAd != null) {

                interstitialAd?.show(activity)

            } else {

                Log.d("AdManager", "Ad not ready, loading")

                loadAd(activity)
            }

        } catch (e: Exception) {

            Log.e("AdManager", "Ad show crash", e)
        }
    }
}
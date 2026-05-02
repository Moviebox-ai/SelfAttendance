package com.aaryo.selfattendance.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.aaryo.selfattendance.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * RewardedAdManager — lifecycle management for Rewarded Ads.
 *
 * KEY FIX: Rewarded ads intentionally do NOT use AdsController.canShowAd()
 * because that gate is for UNSOLICITED full-screen ads (App Open, Interstitial).
 *
 * Rewarded ads are USER-INITIATED — the user taps "Watch Ad · Earn Coins".
 * Blocking them with the global 90s cooldown would make the earn-coins feature
 * completely non-functional whenever any other ad had recently shown.
 *
 * Instead, rewarded ads use their own per-feature cooldown managed by
 * PreferencesManager.canWatchAd() inside CoinRepository.addAdReward().
 *
 * AdsController.recordAdShown() is also NOT called — rewarded ad completions
 * should not delay the next App Open ad (they are separate flows).
 */
class RewardedAdManager(private val context: Context) {

    companion object {
        private const val TAG = "RewardedAdManager"
        private const val DEFAULT_REWARD_COINS = 10

        private val adUnitId: String
            get() = if (BuildConfig.DEBUG)
                "ca-app-pub-3940256099942544/5224354917"
            else
                "ca-app-pub-5703232582358249/8545815487"
    }

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false

    // ── Result types ──────────────────────────────────────────────────────────

    sealed class RewardedAdResult {
        /** Ad watched fully — coins is the amount granted. */
        data class RewardEarned(val coins: Int) : RewardedAdResult()
        /** Ad dismissed before completion — no coins granted. */
        object Dismissed : RewardedAdResult()
        /** Ad was not ready when show() was called. */
        object NotReady : RewardedAdResult()
        /** Ad failed to show after it was ready. */
        data class Failed(val message: String) : RewardedAdResult()
    }

    val isAdReady: Boolean get() = rewardedAd != null && !isLoading

    // ── Load ──────────────────────────────────────────────────────────────────

    fun preload() {
        if (!AdsController.isAdsEnabled) return
        if (isLoading || rewardedAd != null) return
        loadAd()
    }

    private fun loadAd() {
        if (isLoading) return
        isLoading = true
        RewardedAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isLoading  = false
                    Log.d(TAG, "Rewarded ad loaded ✓")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    isLoading  = false
                    Log.e(TAG, "Rewarded ad load failed: ${error.message}")
                }
            }
        )
    }

    // ── Show ──────────────────────────────────────────────────────────────────

    /**
     * Show the rewarded ad.
     *
     * Does NOT check AdsController.canShowAd() — rewarded ads are user-initiated
     * and must not be blocked by the unsolicited-ad global cooldown.
     *
     * [onResult] is always called exactly once after the ad closes:
     *  - RewardEarned(coins) → user watched fully, grant the reward
     *  - Dismissed           → user skipped before completion
     *  - NotReady            → ad wasn't loaded yet (trigger preload)
     *  - Failed(msg)         → show attempt failed
     */
    fun showAd(
        activity: Activity,
        onResult : (RewardedAdResult) -> Unit
    ) {
        // Only check ads-enabled flag — NOT the global cooldown
        if (!AdsController.isAdsEnabled) {
            onResult(RewardedAdResult.NotReady)
            return
        }

        val ad = rewardedAd ?: run {
            Log.d(TAG, "Rewarded ad not ready — triggering preload")
            onResult(RewardedAdResult.NotReady)
            loadAd()
            return
        }

        var rewardEarned = false
        var rewardCoins  = DEFAULT_REWARD_COINS

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                // Do NOT call AdsController.recordAdShown() here —
                // rewarded ads are user-initiated and must not delay App Open ads.
                Log.d(TAG, "Rewarded ad showing")
            }
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                loadAd()  // preload next immediately
                if (rewardEarned) {
                    Log.d(TAG, "Rewarded ad: reward earned ($rewardCoins coins)")
                    onResult(RewardedAdResult.RewardEarned(rewardCoins))
                } else {
                    Log.d(TAG, "Rewarded ad: dismissed without reward")
                    onResult(RewardedAdResult.Dismissed)
                }
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                loadAd()
                Log.e(TAG, "Rewarded ad show failed: ${error.message}")
                onResult(RewardedAdResult.Failed(error.message))
            }
        }

        ad.show(activity) { rewardItem ->
            rewardEarned = true
            rewardCoins  = rewardItem.amount.takeIf { it > 0 } ?: DEFAULT_REWARD_COINS
            Log.d(TAG, "onUserEarnedReward: ${rewardItem.amount} ${rewardItem.type}")
        }
    }
}

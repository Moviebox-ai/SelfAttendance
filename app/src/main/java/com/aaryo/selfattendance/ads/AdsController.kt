package com.aaryo.selfattendance.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.aaryo.selfattendance.BuildConfig
import com.aaryo.selfattendance.R
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.*

/**
 * AdsController — centralised ad orchestration layer.
 *
 * POLICY RULES (Google AdMob):
 *  • Global cooldown of 90 s between ANY full-screen ads.
 *  • Interstitial shown only once every [INTERSTITIAL_FREQUENCY] qualifying events.
 *  • App Open ads respect their own cooldown (AppOpenAdManager).
 *  • canShowAd() is the single gate — call it before every ad display attempt.
 *  • No ad is ever shown during onboarding / login flows.
 */
object AdsController {

    private const val TAG = "AdsController"

    // ── Cooldown & Frequency ───────────────────────────────────────────────
    /** Minimum ms between any full-screen ad (App Open or Interstitial). */
    const val GLOBAL_COOLDOWN_MS = 90_000L          // 90 seconds

    /** Show interstitial once per this many qualifying overtime saves. */
    private const val INTERSTITIAL_FREQUENCY = 3

    // ── State ──────────────────────────────────────────────────────────────
    var isAdsEnabled = true

    /** Set to true while Splash / Auth screens are visible. */
    var isOnboardingActive = false

    /**
     * Shared timestamp updated by AppOpenAdManager and showInterstitialAfterSave alike.
     * Used for the global cooldown check so no two full-screen ads appear back-to-back.
     */
    var lastFullScreenAdShownTime: Long = 0L

    // Interstitial save-event counter (not persisted — resets on cold start).
    private var interstitialEventCounter = 0

    // ── Ad IDs ────────────────────────────────────────────────────────────
    private val interstitialId
        get() = if (BuildConfig.DEBUG)
            "ca-app-pub-3940256099942544/1033173712"
        else
            "ca-app-pub-5703232582358249/4828770637"

    private val nativeId
        get() = if (BuildConfig.DEBUG)
            "ca-app-pub-3940256099942544/2247696110"
        else
            "ca-app-pub-5703232582358249/1044432701"

    private val bannerAdUnitId
        get() = if (BuildConfig.DEBUG)
            "ca-app-pub-3940256099942544/6300978111"
        else
            "ca-app-pub-5703232582358249/2282498349"

    // ── Global Guard ──────────────────────────────────────────────────────

    /**
     * Returns true only when it is safe to show a full-screen ad.
     * Checks: ads enabled, not onboarding, global cooldown elapsed.
     */
    fun canShowAd(): Boolean {
        if (!isAdsEnabled) {
            Log.d(TAG, "canShowAd=false: ads disabled")
            return false
        }
        if (isOnboardingActive) {
            Log.d(TAG, "canShowAd=false: onboarding active")
            return false
        }
        val elapsed = System.currentTimeMillis() - lastFullScreenAdShownTime
        if (elapsed < GLOBAL_COOLDOWN_MS) {
            Log.d(TAG, "canShowAd=false: cooldown (${elapsed}ms / ${GLOBAL_COOLDOWN_MS}ms)")
            return false
        }
        return true
    }

    /** Call this every time a full-screen ad is actually displayed. */
    fun recordAdShown() {
        lastFullScreenAdShownTime = System.currentTimeMillis()
    }

    // ── Interstitial ──────────────────────────────────────────────────────

    private var interstitialAd: InterstitialAd? = null
    private var isLoadingInterstitial = false

    fun loadInterstitial(context: Context) {
        if (!isAdsEnabled || isLoadingInterstitial || interstitialAd != null) return
        isLoadingInterstitial = true
        InterstitialAd.load(
            context,
            interstitialId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoadingInterstitial = false
                    Log.d(TAG, "Interstitial loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Interstitial load failed: ${error.message}")
                    interstitialAd = null
                    isLoadingInterstitial = false
                }
            }
        )
    }

    /**
     * Show interstitial after an overtime save event.
     *
     * Policy:
     *  • Save action is NEVER delayed or blocked by this call.
     *  • Ad is shown only every [INTERSTITIAL_FREQUENCY] events AND only when
     *    global cooldown has elapsed.
     *  • If ad isn't ready we skip gracefully and trigger a preload.
     */
    fun showInterstitialAfterSave(activity: Activity) {
        interstitialEventCounter++
        Log.d(TAG, "Interstitial event counter: $interstitialEventCounter")

        if (interstitialEventCounter % INTERSTITIAL_FREQUENCY != 0) {
            Log.d(TAG, "Skipping interstitial — frequency not met")
            loadInterstitial(activity)
            return
        }

        if (!canShowAd()) {
            loadInterstitial(activity)
            return
        }

        val ad = interstitialAd ?: run {
            Log.d(TAG, "Interstitial not ready — skipping gracefully")
            loadInterstitial(activity)
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                recordAdShown()
                interstitialAd = null
                Log.d(TAG, "Interstitial shown")
            }
            override fun onAdDismissedFullScreenContent() {
                loadInterstitial(activity)
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                loadInterstitial(activity)
                Log.e(TAG, "Interstitial show failed: ${error.message}")
            }
        }

        ad.show(activity)
    }

    // ── Banner ────────────────────────────────────────────────────────────

    @Composable
    fun BannerAd() {
        if (!isAdsEnabled) return
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally),
            factory = { context ->
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = bannerAdUnitId
                    loadAd(AdRequest.Builder().build())
                }
            }
        )
    }

    // ── Native ────────────────────────────────────────────────────────────

    private var nativeAd by mutableStateOf<NativeAd?>(null)
    private var isLoadingNative = false

    fun loadNative(context: Context) {
        if (!isAdsEnabled || isLoadingNative) return
        isLoadingNative = true
        val adLoader = AdLoader.Builder(context, nativeId)
            .forNativeAd { ad ->
                nativeAd?.destroy()
                nativeAd = ad
                isLoadingNative = false
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Native load failed: ${error.message}")
                    nativeAd = null
                    isLoadingNative = false
                }
            })
            .build()
        adLoader.loadAd(AdRequest.Builder().build())
    }

    @Composable
    fun NativeAdView() {
        if (!isAdsEnabled) return
        val context = LocalContext.current
        LaunchedEffect(Unit) { loadNative(context) }
        nativeAd?.let { ad ->
            key(ad) {
                AndroidView(
                    factory = { ctx ->
                        val view = LayoutInflater.from(ctx)
                            .inflate(R.layout.native_ad_layout, null) as NativeAdView
                        populateNativeAdView(ad, view)
                        view
                    }
                )
            }
        }
    }

    private fun populateNativeAdView(ad: NativeAd, adView: NativeAdView) {
        val headline = adView.findViewById<TextView>(R.id.ad_headline)
        val body     = adView.findViewById<TextView>(R.id.ad_body)
        val cta      = adView.findViewById<Button>(R.id.ad_call_to_action)
        val media    = adView.findViewById<MediaView>(R.id.ad_media)

        headline.text = ad.headline
        body.text     = ad.body ?: ""
        cta.text      = ad.callToAction

        adView.headlineView        = headline
        adView.bodyView            = body
        adView.callToActionView    = cta
        adView.mediaView           = media

        adView.setNativeAd(ad)
    }

    // ── Preload all ads ───────────────────────────────────────────────────

    fun preload(context: Context) {
        loadInterstitial(context)
        loadNative(context)
        // App Open is preloaded by AppOpenAdManager via SelfAttendanceApp
    }

    /** Sets enabled flag. MobileAds.initialize() is deferred to ConsentManager. */
    fun init(@Suppress("UNUSED_PARAMETER") context: Context) {
        isAdsEnabled = true
    }
}

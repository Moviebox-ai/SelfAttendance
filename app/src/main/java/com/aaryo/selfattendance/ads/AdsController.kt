package com.aaryo.selfattendance.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.aaryo.selfattendance.BuildConfig
import com.aaryo.selfattendance.R
import com.aaryo.selfattendance.data.remote.RemoteConfigManager
import com.google.android.gms.ads.*
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.*

/**
 * AdsController — centralised ad orchestration layer.
 *
 * POLICY RULES (Google AdMob):
 *  • Global cooldown of 60 s between ANY full-screen ads.
 *  • Interstitial shown only once every [INTERSTITIAL_FREQUENCY] qualifying events.
 *  • App Open ads respect their own cooldown (AppOpenAdManager).
 *  • canShowAd() is the single gate — call it before every ad display attempt.
 *  • No ad is ever shown during onboarding / login flows.
 */
object AdsController {

    private const val TAG = "AdsController"

    // ── Cooldown & Frequency ───────────────────────────────────────────────
    /**
     * Minimum ms between any two full-screen ads (App Open or Interstitial).
     * AdMob policy: never show full-screen ads back-to-back without a pause.
     * 60 seconds is a safe, policy-compliant minimum.
     */
    const val GLOBAL_COOLDOWN_MS = 60_000L          // 60 seconds minimum between full-screen ads

    /**
     * Show interstitial every N qualifying events.
     * AdMob policy: interstitials must not appear too frequently.
     * and could trigger AdMob policy violations / account suspension.
     * Value of 3 means: interstitial shows after every 3rd qualifying user action.
     */
    private const val INTERSTITIAL_FREQUENCY = 3

    // ── Retry timings ─────────────────────────────────────────────────────────
    private const val RETRY_BASE_MS        = 30_000L
    private const val RETRY_MAX_MS         = 30 * 60 * 1_000L   // interstitial cap
    /**
     * Native / Banner cap is much lower than interstitial — these are always
     * visible in the UI so a 30-min blank slot is unacceptable.
     * Cap at 5 minutes so the slot recovers quickly after a bad run.
     */
    private const val INLINE_RETRY_MAX_MS  = 5 * 60 * 1_000L    // 5 min cap for banner+native
    private const val HEALTH_CHECK_MS      = 5 * 60 * 1_000L

    /**
     * AdMob interstitial and native ads expire after ~1 hour.
     * After this window, the cached ad must be discarded and reloaded.
     * App Open Ad expiry (4 h) is managed separately in AppOpenAdManager.
     */
    private const val INTERSTITIAL_EXPIRY_MS = 60 * 60 * 1_000L  // 1 hour
    private const val NATIVE_EXPIRY_MS       = 60 * 60 * 1_000L  // 1 hour

    private val handler = Handler(Looper.getMainLooper())

    // ── State ──────────────────────────────────────────────────────────────
    // mutableStateOf so BannerAd/NativeAdView recompose automatically whenever
    // isAdsEnabled flips (e.g. REMOVE_ADS feature activated/deactivated after
    // first render). Without this, Compose never re-reads a plain var.
    var isAdsEnabled by mutableStateOf(true)

    // ── Screen-change refresh ──────────────────────────────────────────────
    /**
     * Incremented only when the banner is actually due for a refresh (see
     * [BANNER_REFRESH_MIN_MS] below). BannerAdView's LaunchedEffect uses this
     * as a key so it re-fires and calls loadAd() fresh — but only when it's
     * genuinely time to refresh, not on every tab switch.
     */
    private var bannerRefreshTick by mutableIntStateOf(0)

    /** Last route we already processed a screen-change for — prevents
     *  double-processing on recompositions that replay the same route. */
    private var lastRefreshedRoute: String? = null

    /** Epoch ms of the last banner reload — used to throttle refreshes. */
    private var lastBannerLoadTime: Long = 0L

    /**
     * Minimum gap between two banner reloads triggered by navigation.
     * AdMob policy explicitly forbids refreshing a banner faster than this;
     * requesting new ads on every tab switch (which can happen several times
     * per second) trips AdMob's invalid-traffic / excessive-refresh
     * protections and results in "no fill" responses — which is what made
     * the banner and native slots go blank after the very first ad shown.
     */
    private const val BANNER_REFRESH_MIN_MS = 60_000L

    /**
     * Called whenever any full-screen ad (Rewarded, Interstitial, App Open) is dismissed.
     * Triggers immediate refresh/reload of the banner ad so returning users don't see a blank slot.
     */
    fun onFullScreenAdDismissed() {
        lastBannerLoadTime = 0L
        bannerRefreshTick++
    }

    /**
     * Call this every time the visible navigation route changes.
     *
     * FIX: this previously force-destroyed the cached singleton native ad
     * AND reloaded the banner on EVERY route change (i.e. every bottom-nav
     * tab switch). That directly contradicted the "singleton, don't reload
     * on return" design of [NativeAdView] and [BannerAdView]: the very first
     * ad shown was destroyed the moment the user switched tabs, and the
     * replacement request — fired far more often than AdMob's refresh
     * policy allows — routinely came back "no fill" or silently dropped
     * (see the AdLoader GC note in [loadNative]). Net effect: ad shows once,
     * then the slot stays blank ("white") for the rest of the session.
     *
     * Now: the native ad is left alone (its own 1-hour expiry + health-check
     * already keep it fresh) and the banner only reloads if it hasn't been
     * refreshed in the last [BANNER_REFRESH_MIN_MS].
     */
    fun onScreenChanged(route: String) {
        if (route == lastRefreshedRoute) return
        lastRefreshedRoute = route

        val now = System.currentTimeMillis()

        // BUG FIX: When the app first opens, lastBannerLoadTime = 0L so
        // (now - 0L) is always >= BANNER_REFRESH_MIN_MS, which means the VERY
        // FIRST onScreenChanged() call always incremented bannerRefreshTick.
        // BannerAdView's LaunchedEffect(mobileAdsReady) already fires loadAd()
        // on first composition — so this extra increment caused a second loadAd()
        // milliseconds later, aborting the first in-flight request and leaving
        // the banner slot blank until the second request completed (or timed out).
        //
        // Fix: on the first call (lastBannerLoadTime == 0L), just record the
        // timestamp so the 60-second window starts correctly, but do NOT
        // increment bannerRefreshTick — the initial load is already handled
        // by the BannerAdView composable's mobileAdsReady LaunchedEffect.
        if (lastBannerLoadTime == 0L) {
            Log.d(TAG, "Screen changed → $route — first screen, banner handled by LaunchedEffect")
            lastBannerLoadTime = now
            return
        }

        if (now - lastBannerLoadTime >= BANNER_REFRESH_MIN_MS) {
            Log.d(TAG, "Screen changed → $route — banner due for refresh")
            lastBannerLoadTime = now
            bannerRefreshTick++
        } else {
            Log.d(TAG, "Screen changed → $route — banner refresh skipped (too soon)")
        }

        // Native ad is intentionally NOT destroyed here — it stays cached
        // and is reused across screens until it naturally expires
        // (NATIVE_EXPIRY_MS) or the periodic health-check finds it missing.
    }

    /**
     * True once MobileAds.initialize() has completed (signalled by ConsentManager).
     * Any ad load attempt MUST check this first — calling AdMob APIs before
     * initialization throws IllegalStateException and crashes the app.
     *
     * Uses Compose mutableStateOf so BannerAd / NativeAdView recompose automatically
     * when MobileAds becomes ready, triggering the first deferred load.
     */
    var mobileAdsReady by mutableStateOf(false)
        private set

    /**
     * Called by ConsentManager immediately after MobileAds.initialize() succeeds.
     * Triggers the first App Open Ad preload so it is ready when onboarding ends.
     */
    fun onMobileAdsReady(context: Context? = null) {
        mobileAdsReady = true
        Log.d(TAG, "MobileAds ready — ad composables will recompose and load")
        val ctx = context?.applicationContext ?: nativeAppContext ?: interstitialAppContext
        if (ctx != null) {
            loadNative(ctx)
            loadInterstitial(ctx)
            initRewardedAds(ctx)
        }
    }

    /** Set to true while Splash / Auth screens are visible. */
    var isOnboardingActive = false

    /**
     * Shared timestamp updated by AppOpenAdManager and showInterstitialAfterSave alike.
     * Used for the global cooldown check so no two full-screen ads appear back-to-back.
     */
    var lastFullScreenAdShownTime: Long = 0L

    // Interstitial save-event counter (not persisted — resets on cold start).
    private var interstitialEventCounter = 0L

    // Per-type retry counters
    private var interstitialRetryCount = 0
    private var nativeRetryCount       = 0

    // ── Ad IDs ────────────────────────────────────────────────────────────
    private val interstitialId
        get() = if (BuildConfig.DEBUG)
            "ca-app-pub-3940256099942544/1033173712"
        else
            "ca-app-pub-5703232582358249/6823885028"

    private val nativeId
        get() = if (BuildConfig.DEBUG)
            "ca-app-pub-3940256099942544/2247696110"
        else
            "ca-app-pub-5703232582358249/3267783395"


    private val bannerId
        get() = if (BuildConfig.DEBUG)
            "ca-app-pub-3940256099942544/6300978111"   // Test banner (always returns test ad)
        else
            "ca-app-pub-5703232582358249/8520110078"   // ✅ VERIFIED: Production Banner unit ID — confirm in AdMob console

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
    private var isLoadingInterstitial  = false
    private var interstitialLoadTime   = 0L   // epoch ms when ad was last loaded

    /** Returns true when the cached interstitial is older than INTERSTITIAL_EXPIRY_MS. */
    private fun isInterstitialExpired(): Boolean =
        interstitialAd != null &&
        System.currentTimeMillis() - interstitialLoadTime > INTERSTITIAL_EXPIRY_MS

    // Periodic interstitial health-check — discards expired ad and reloads.
    private var interstitialHealthStarted = false
    private val interstitialHealthRunnable = object : Runnable {
        override fun run() {
            if (isAdsEnabled && mobileAdsReady) {
                if (isInterstitialExpired()) {
                    Log.d(TAG, "Interstitial health-check: ad expired — reloading")
                    interstitialAd         = null
                    interstitialLoadTime   = 0L
                    interstitialRetryCount = 0
                    val ctx = interstitialAppContext
                    if (ctx != null) loadInterstitial(ctx)
                } else if (interstitialAd == null && !isLoadingInterstitial) {
                    Log.d(TAG, "Interstitial health-check: slot empty — reloading")
                    val ctx = interstitialAppContext
                    if (ctx != null) loadInterstitial(ctx)
                }
            }
            handler.postDelayed(this, HEALTH_CHECK_MS)
        }
    }

    private var interstitialAppContext: Context? = null

    private fun ensureInterstitialHealthCheck() {
        if (!interstitialHealthStarted) {
            interstitialHealthStarted = true
            handler.postDelayed(interstitialHealthRunnable, HEALTH_CHECK_MS)
        }
    }

    fun loadInterstitial(context: Context) {
        val appCtx = context.applicationContext.also { interstitialAppContext = it }

        // Discard expired ad before checking if slot is already filled
        if (isInterstitialExpired()) {
            Log.d(TAG, "Interstitial expired — discarding cached ad")
            interstitialAd       = null
            interstitialLoadTime = 0L
        }

        if (!isAdsEnabled || !mobileAdsReady || isLoadingInterstitial || interstitialAd != null) return

        ensureInterstitialHealthCheck()
        isLoadingInterstitial = true
        Log.d(TAG, "Loading AdsController interstitial (attempt ${interstitialRetryCount + 1})…")
        InterstitialAd.load(
            appCtx,
            interstitialId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd         = ad
                    interstitialLoadTime   = System.currentTimeMillis()
                    isLoadingInterstitial  = false
                    interstitialRetryCount = 0
                    Log.d(TAG, "Interstitial loaded ✓")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Interstitial load failed (code ${error.code}): ${error.message}")
                    interstitialAd        = null
                    isLoadingInterstitial = false
                    // Exponential backoff retry — never leave the slot permanently empty
                    val delayMs = minOf(RETRY_BASE_MS * (1L shl interstitialRetryCount), RETRY_MAX_MS)
                    interstitialRetryCount++
                    Log.d(TAG, "AdsController interstitial retry #$interstitialRetryCount in ${delayMs / 1000}s")
                    handler.postDelayed({
                        if (interstitialAd == null && !isLoadingInterstitial && isAdsEnabled) {
                            loadInterstitial(appCtx)
                        }
                    }, delayMs)
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

        if (interstitialEventCounter % INTERSTITIAL_FREQUENCY.toLong() != 0L) {
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
                interstitialAd         = null
                interstitialRetryCount = 0
                Log.d(TAG, "Interstitial shown ✓")
            }
            override fun onAdDismissedFullScreenContent() {
                interstitialRetryCount = 0
                loadInterstitial(activity)
                onFullScreenAdDismissed()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                Log.e(TAG, "Interstitial show failed: ${error.message}")
                // Schedule reload with backoff instead of immediate retry
                val delayMs = minOf(RETRY_BASE_MS * (1L shl interstitialRetryCount), RETRY_MAX_MS)
                interstitialRetryCount++
                handler.postDelayed({ loadInterstitial(activity) }, delayMs)
            }
        }

        ad.show(activity)
    }

    /**
     * Show interstitial immediately for a deliberate user action (e.g. PDF export).
     *
     * Policy:
     *  • No frequency gate — PDF export is an infrequent, intentional action.
     *  • Still respects the global 60-second cooldown.
     *  • [onFinished] is called after the ad is dismissed, fails to show, OR when
     *    the ad is not available — so the underlying action (export) always happens.
     *  • The action is NEVER blocked: if no ad is ready, onFinished fires immediately.
     */
    fun showInterstitialNow(activity: Activity, onFinished: () -> Unit = {}) {
        if (!canShowAd()) {
            Log.d(TAG, "showInterstitialNow: canShowAd=false — proceeding without ad")
            loadInterstitial(activity)
            onFinished()
            return
        }

        val ad = interstitialAd ?: run {
            Log.d(TAG, "showInterstitialNow: no ad ready — proceeding without ad")
            loadInterstitial(activity)
            onFinished()
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                recordAdShown()
                interstitialAd         = null
                interstitialRetryCount = 0
                Log.d(TAG, "showInterstitialNow: ad shown ✓")
            }
            override fun onAdDismissedFullScreenContent() {
                interstitialRetryCount = 0
                loadInterstitial(activity)
                Log.d(TAG, "showInterstitialNow: ad dismissed — calling onFinished")
                onFullScreenAdDismissed()
                onFinished()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                Log.e(TAG, "showInterstitialNow: show failed — calling onFinished anyway: ${error.message}")
                val delayMs = minOf(RETRY_BASE_MS * (1L shl interstitialRetryCount), RETRY_MAX_MS)
                interstitialRetryCount++
                handler.postDelayed({ loadInterstitial(activity) }, delayMs)
                onFinished()
            }
        }

        ad.show(activity)
    }

    // ── Native ────────────────────────────────────────────────────────────

    private var nativeAd        by mutableStateOf<NativeAd?>(null)
    private var isLoadingNative  = false
    private var nativeLoadTime   = 0L   // epoch ms when native ad was last loaded
    /** ApplicationContext stored once so retry lambdas never hold a stale Activity ref. */
    private var nativeAppContext: Context? = null

    /**
     * Strong reference to the in-flight [AdLoader].
     * AdLoader does its work asynchronously; if the only reference to it is a
     * local `val` inside [loadNative], the loader can be garbage-collected
     * before its callback fires, so the request silently vanishes — no
     * onAdLoaded, no onAdFailedToLoad, nativeAd just stays null forever.
     * Holding it here until the callback completes prevents that.
     */
    private var pendingNativeAdLoader: AdLoader? = null

    /** Returns true when the cached native ad is older than NATIVE_EXPIRY_MS. */
    private fun isNativeExpired(): Boolean =
        nativeAd != null &&
        System.currentTimeMillis() - nativeLoadTime > NATIVE_EXPIRY_MS

    // ── Native health-check ───────────────────────────────────────────────

    /**
     * Periodic health-check: if native ad is null and we're not loading,
     * proactively reload — prevents a permanently blank native slot after
     * the exponential backoff delay has elapsed but no new trigger arrived.
     */
    private val nativeHealthRunnable = object : Runnable {
        override fun run() {
            val ctx = nativeAppContext
            if (ctx != null && isAdsEnabled && mobileAdsReady) {
                if (isNativeExpired()) {
                    Log.d(TAG, "Native health-check: ad expired — discarding and reloading")
                    nativeAd?.destroy()
                    nativeAd       = null
                    nativeLoadTime = 0L
                    nativeRetryCount = 0
                    loadNative(ctx)
                } else if (nativeAd == null && !isLoadingNative) {
                    Log.d(TAG, "Native health-check: slot empty — reloading")
                    nativeRetryCount = 0   // reset backoff so reload happens quickly
                    loadNative(ctx)
                }
            }
            handler.postDelayed(this, HEALTH_CHECK_MS)
        }
    }

    private var nativeHealthStarted = false

    private fun ensureNativeHealthCheck() {
        if (!nativeHealthStarted) {
            nativeHealthStarted = true
            handler.postDelayed(nativeHealthRunnable, HEALTH_CHECK_MS)
        }
    }

    /**
     * Load or reload the native ad.
     *
     * FIXES vs original:
     *  1. Uses applicationContext stored in [nativeAppContext] for retry lambdas
     *     — avoids stale Activity reference crashes after screen rotation.
     *  2. Retry cap is [INLINE_RETRY_MAX_MS] (5 min) not 30 min — native ads
     *     are always visible in the UI; a 30-min blank is unacceptable.
     *  3. Starts the periodic health-check runnable on first call.
     *  4. Checks expiry before skipping load — expired ad is discarded first.
     */
    fun loadNative(context: Context) {
        // Discard expired native ad before checking if slot is filled
        if (isNativeExpired()) {
            Log.d(TAG, "Native ad expired — discarding cached ad")
            nativeAd?.destroy()
            nativeAd       = null
            nativeLoadTime = 0L
        }

        // Without this guard, loadNative() would start a redundant load request
        // even when the ad slot is already filled.
        if (!isAdsEnabled || !mobileAdsReady || isLoadingNative || nativeAd != null) return
        // Prefer application context for long-lived retry lambdas
        val appCtx = context.applicationContext.also { nativeAppContext = it }
        isLoadingNative = true
        ensureNativeHealthCheck()
        Log.d(TAG, "Loading native ad (attempt ${nativeRetryCount + 1})…")

        val adLoader = AdLoader.Builder(appCtx, nativeId)
            .forNativeAd { ad ->
                nativeAd?.destroy()
                nativeAd         = ad
                nativeLoadTime   = System.currentTimeMillis()
                isLoadingNative  = false
                nativeRetryCount = 0
                pendingNativeAdLoader = null
                Log.d(TAG, "Native ad loaded ✓")
            }
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .setRequestMultipleImages(false)
                    .setReturnUrlsForImageAssets(false)
                    .build()
            )
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Native load failed (code ${error.code}): ${error.message}")
                    isLoadingNative = false
                    pendingNativeAdLoader = null
                    // ── FIX: cap at INLINE_RETRY_MAX_MS (5 min) not 30 min ────
                    val delayMs = minOf(RETRY_BASE_MS * (1L shl nativeRetryCount), INLINE_RETRY_MAX_MS)
                    nativeRetryCount++
                    Log.d(TAG, "Native retry #$nativeRetryCount in ${delayMs / 1000}s")
                    handler.postDelayed({
                        if (nativeAd == null && !isLoadingNative && isAdsEnabled) {
                            loadNative(appCtx)   // use appCtx — safe across rotations
                        }
                    }, delayMs)
                }
            })
            .build()
        // Keep a strong reference until the callback fires — see
        // pendingNativeAdLoader's doc comment for why this matters.
        pendingNativeAdLoader = adLoader
        adLoader.loadAd(AdRequest.Builder().build())
    }

    @Composable
    fun NativeAdView(modifier: Modifier = Modifier) {
        if (!isAdsEnabled || isAdFreeActive || !RemoteConfigManager.getInstance().showNativeAd()) return
        val context = LocalContext.current

        LaunchedEffect(mobileAdsReady, nativeAd) {
            if (mobileAdsReady && nativeAd == null) loadNative(context.applicationContext)
        }

        nativeAd?.let { ad ->
            key(ad) {
                AndroidView(
                    modifier = modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    factory = { ctx ->
                        val view = LayoutInflater.from(ctx)
                            .inflate(R.layout.native_ad_layout, null) as NativeAdView
                        populateNativeAdView(ad, view)
                        view
                    },
                    update = { view ->
                        populateNativeAdView(ad, view)
                    }
                )
            }
        }
    }

    /**
     * FreshNativeAdView — loads its OWN independent native ad every time it
     * enters composition. Unlike [NativeAdView] which uses a global singleton,
     * this composable creates a new [AdLoader] on composition and destroys the
     * ad on disposal, so every screen gets a completely fresh ad.
     *
     * Usage: call inside a remoteConfig.showNativeAd() guard at the call site.
     */
    @Composable
    fun FreshNativeAdView(modifier: Modifier = Modifier) {
        if (!isAdsEnabled || isAdFreeActive || !RemoteConfigManager.getInstance().showNativeAd()) return
        val context = LocalContext.current
        var freshAd by remember { mutableStateOf<NativeAd?>(null) }
        // Retry counter — incrementing triggers LaunchedEffect re-run
        var retryTick by remember { mutableIntStateOf(0) }
        // Strong reference to the in-flight loader — without this the local
        // AdLoader built below has no owner and can be garbage-collected
        // before its callback fires, silently dropping the ad request (same
        // root cause documented on AdsController.pendingNativeAdLoader).
        var pendingLoader by remember { mutableStateOf<AdLoader?>(null) }

        /**
         * Ab exponential backoff ke saath multiple retries hoti hain,
         * INLINE_RETRY_MAX_MS (5 min) pe cap karke — same policy jo NativeAdView pe hai.
         * Har retry ke baad retryTick++ hota hai jo LaunchedEffect re-run karta hai.
         */
        LaunchedEffect(mobileAdsReady, retryTick) {
            if (!mobileAdsReady) return@LaunchedEffect
            val appCtx = context.applicationContext
            val loader = AdLoader.Builder(appCtx, nativeId)
                .forNativeAd { ad ->
                    freshAd?.destroy()
                    freshAd = ad
                    pendingLoader = null
                }
                .withNativeAdOptions(
                    NativeAdOptions.Builder()
                        .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                        .setRequestMultipleImages(false)
                        .setReturnUrlsForImageAssets(false)
                        .build()
                )
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w(TAG, "FreshNative load failed (attempt ${retryTick + 1}): ${error.message}")
                        pendingLoader = null
                        val delayMs = minOf(
                            RETRY_BASE_MS * (1L shl retryTick),
                            INLINE_RETRY_MAX_MS
                        )
                        Log.d(TAG, "FreshNative retry #${retryTick + 1} in ${delayMs / 1000}s")
                        handler.postDelayed({
                            if (freshAd == null && isAdsEnabled && mobileAdsReady) {
                                retryTick++
                            }
                        }, delayMs)
                    }
                })
                .build()
            // Hold a strong reference until the callback fires (see pendingLoader).
            pendingLoader = loader
            loader.loadAd(AdRequest.Builder().build())
        }

        DisposableEffect(Unit) {
            onDispose {
                freshAd?.destroy()
                freshAd = null
            }
        }

        freshAd?.let { ad ->
            key(ad) {
                AndroidView(
                    modifier = modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    factory = { ctx ->
                        val view = LayoutInflater.from(ctx)
                            .inflate(R.layout.native_ad_layout, null) as NativeAdView
                        populateNativeAdView(ad, view)
                        view
                    },
                    update = { view ->
                        populateNativeAdView(ad, view)
                    }
                )
            }
        }
    }

    private fun populateNativeAdView(ad: NativeAd, adView: NativeAdView) {
        val headline    = adView.findViewById<TextView>(R.id.ad_headline)
        val body        = adView.findViewById<TextView>(R.id.ad_body)
        val cta         = adView.findViewById<Button>(R.id.ad_call_to_action)
        val media       = adView.findViewById<MediaView>(R.id.ad_media)
        val iconView    = adView.findViewById<android.widget.ImageView>(R.id.ad_app_icon)
        val advertiser  = adView.findViewById<TextView>(R.id.ad_advertiser)

        // Headline (Mandatory)
        headline?.text = ad.headline ?: ""
        adView.headlineView = headline

        // Body text (Optional)
        if (!ad.body.isNullOrBlank()) {
            body?.text = ad.body
            body?.visibility = android.view.View.VISIBLE
            adView.bodyView = body
        } else {
            body?.visibility = android.view.View.GONE
            adView.bodyView = null
        }

        // Call to action button (Mandatory)
        cta?.text = ad.callToAction ?: "Learn More"
        adView.callToActionView = cta

        // App Icon (Optional)
        val icon = ad.icon
        if (icon?.drawable != null) {
            iconView?.setImageDrawable(icon.drawable)
            iconView?.visibility = android.view.View.VISIBLE
            adView.iconView = iconView
        } else {
            iconView?.visibility = android.view.View.GONE
            adView.iconView = null
        }

        // Advertiser / Store (Optional)
        val advertiserText = ad.advertiser ?: ad.store
        if (!advertiserText.isNullOrBlank()) {
            advertiser?.text = advertiserText
            advertiser?.visibility = android.view.View.VISIBLE
            adView.advertiserView = advertiser
        } else {
            advertiser?.visibility = android.view.View.GONE
            adView.advertiserView = null
        }

        // Media content (Image / Video)
        val mediaContent = ad.mediaContent
        if (mediaContent != null && (mediaContent.hasVideoContent() || mediaContent.mainImage != null)) {
            media?.mediaContent = mediaContent
            media?.visibility   = android.view.View.VISIBLE
            adView.mediaView    = media
        } else {
            media?.visibility   = android.view.View.GONE
            adView.mediaView    = null
        }

        // Register the NativeAd object with the NativeAdView
        adView.setNativeAd(ad)
    }


    // ── Standard Banner Ad ────────────────────────────────────────────────────

    /**
     * Persistent AdMob adaptive banner ad.
     */
    @Composable
    fun BannerAdView(modifier: Modifier = Modifier) {
        if (!isAdsEnabled || !RemoteConfigManager.getInstance().showBannerAd()) return

        val context   = LocalContext.current
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        var isAdLoaded by remember { mutableStateOf(false) }

        val adSize = remember(context) {
            val displayMetrics = context.resources.displayMetrics
            val widthPixels = displayMetrics.widthPixels.toFloat()
            val density = displayMetrics.density
            val adWidthDp = if (density > 0f) (widthPixels / density).toInt() else 320
            val safeWidth = if (adWidthDp > 0) adWidthDp else 320
            try {
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, safeWidth)
            } catch (_: Exception) {
                AdSize.BANNER
            }
        }

        var currentAdView by remember { mutableStateOf<AdView?>(null) }

        DisposableEffect(lifecycle, currentAdView) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        currentAdView?.resume()
                        if (mobileAdsReady && currentAdView != null && !isAdLoaded) {
                            try {
                                currentAdView?.loadAd(AdRequest.Builder().build())
                            } catch (e: Exception) {
                                Log.w(TAG, "Banner loadAd on resume error", e)
                            }
                        }
                    }
                    Lifecycle.Event.ON_PAUSE  -> currentAdView?.pause()
                    else                      -> Unit
                }
            }
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
            }
        }

        LaunchedEffect(mobileAdsReady, bannerRefreshTick, currentAdView) {
            if (mobileAdsReady && currentAdView != null) {
                try {
                    currentAdView?.loadAd(AdRequest.Builder().build())
                } catch (e: Exception) {
                    Log.w(TAG, "Banner loadAd error", e)
                }
            }
        }

        AndroidView(
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(adSize)
                    adUnitId = bannerId
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            isAdLoaded = true
                            Log.d(TAG, "Banner loaded ✓")
                        }
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            isAdLoaded = false
                            Log.w(TAG, "Banner load failed (code ${error.code}): ${error.message}")
                        }
                    }
                    currentAdView = this
                }
            },
            onRelease = { adView ->
                if (currentAdView == adView) {
                    currentAdView = null
                }
                adView.destroy()
            }
        )
    }

    // ── Preload all ads ───────────────────────────────────────────────────

    fun preload(context: Context) {
        val appCtx = context.applicationContext
        nativeAppContext = appCtx
        interstitialAppContext = appCtx
        loadInterstitial(appCtx)
        loadNative(appCtx)
        // App Open is preloaded by AppOpenAdManager via SelfAttendanceApp
        rewardedAdManager?.preload()
    }

    /** Sets enabled flag and initializes MobileAds SDK if needed. */
    fun init(context: Context) {
        isAdsEnabled = true
        val appCtx = context.applicationContext
        nativeAppContext = appCtx
        interstitialAppContext = appCtx
        try {
            if (BuildConfig.DEBUG) {
                val requestConfig = RequestConfiguration.Builder()
                    .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                    .build()
                MobileAds.setRequestConfiguration(requestConfig)
            }
            if (!mobileAdsReady) {
                MobileAds.initialize(appCtx) {
                    onMobileAdsReady(appCtx)
                    initRewardedAds(appCtx)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MobileAds init error in AdsController.init", e)
        }
    }

    // ── Ad-free expiry reset ───────────────────────────────────────────────
    /**
     * the 30-minute window expires. Without this, adFreeUntilMs stays at a past
     * timestamp forever — isAdFreeActive keeps returning true (System.currentTimeMillis()
     * never re-evaluates without a state change) and Compose never recomposes the
     * BannerAdView/NativeAdView guards, so ads stay hidden indefinitely.
     *
     * Using removeCallbacks + postDelayed on each new reward correctly handles the
     * case where the user watches a second rewarded ad before the first window ends
     * — the old runnable is cancelled and a fresh 30-minute countdown is started.
     */
    private val adFreeExpiryRunnable = Runnable {
        adFreeUntilMs = 0L
        Log.d(TAG, "Ad-free window expired — banner/native ads restored")
    }

    // ── Rewarded ──────────────────────────────────────────────────────────

    /**
     * Timestamp (epoch ms) until which the "ad-free" reward is active.
     * Uses mutableStateOf so any Composable reading it recomposes automatically
     * when the reward is granted or expires.
     */
    var adFreeUntilMs by mutableStateOf(0L)
        private set

    /** True while the rewarded ad-free window is still active. */
    val isAdFreeActive: Boolean
        get() = System.currentTimeMillis() < adFreeUntilMs

    /** Singleton RewardedAdManager — created once MobileAds is ready. */
    private var rewardedAdManager: RewardedAdManager? = null

    /**
     * Called by ConsentManager after onMobileAdsReady().
     * Initialises the RewardedAdManager and triggers first preload.
     */
    fun initRewardedAds(context: Context) {
        if (rewardedAdManager == null) {
            rewardedAdManager = RewardedAdManager(context.applicationContext)
        }
        rewardedAdManager?.preload()
    }

    /** True when a rewarded ad is loaded and ready to show immediately. */
    fun isRewardedAdReady(): Boolean = rewardedAdManager?.isReady() == true

    val isAdReady: Boolean
        get() = isRewardedAdReady()

    @JvmName("isAdReadyFunc")
    fun isAdReady(): Boolean = isRewardedAdReady()

    /**
     * Show the rewarded video ad.
     *
     * @param activity     Current foreground Activity.
     * @param onRewarded   Called when user earns reward (ad fully watched).
     *                     Activates ad-free mode for [RewardedAdManager.AD_FREE_DURATION_MS].
     * @param onNotReady   Called when no ad is cached yet.
     */
    fun showRewardedAd(
        activity   : android.app.Activity,
        onRewarded : () -> Unit = {},
        onNotReady : () -> Unit = {}
    ) {
        val manager = rewardedAdManager
        if (manager == null) {
            onNotReady()
            return
        }
        manager.showAd(
            activity   = activity,
            onRewarded = {
                Log.d(TAG, "Reward granted")
                onRewarded()
            },
            onNotReady = onNotReady
        )
    }

    fun showRewardedAd(
        activity : android.app.Activity,
        onResult : (RewardedAdResult) -> Unit
    ) {
        val manager = rewardedAdManager
        if (manager == null) {
            onResult(RewardedAdResult.NOT_READY)
            return
        }
        manager.showAd(activity) { result ->
            if (result == RewardedAdResult.EARNED) {
                Log.d(TAG, "Reward granted")
            }
            onResult(result)
        }
    }
}

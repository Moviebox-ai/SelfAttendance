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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.aaryo.selfattendance.BuildConfig
import com.aaryo.selfattendance.R
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

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
     * FIX: Changed from 1 to 3 — showing every single action was too aggressive
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

    /** Banner retry delay after a failed load. */
    private const val BANNER_RETRY_MS      = 30_000L             // 30 s flat retry

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
    fun onMobileAdsReady() {
        mobileAdsReady = true
        Log.d(TAG, "MobileAds ready — ad composables will recompose and load")
    }

    /** Set to true while Splash / Auth screens are visible. */
    var isOnboardingActive = false

    /**
     * Shared timestamp updated by AppOpenAdManager and showInterstitialAfterSave alike.
     * Used for the global cooldown check so no two full-screen ads appear back-to-back.
     */
    var lastFullScreenAdShownTime: Long = 0L

    // Interstitial save-event counter (not persisted — resets on cold start).
    // FIX: Use Long to avoid Int overflow after ~2 billion saves.
    private var interstitialEventCounter = 0L

    // Per-type retry counters
    private var interstitialRetryCount = 0
    private var nativeRetryCount       = 0

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
                    Log.e(TAG, "Interstitial load failed (code ${error.code}): ${error.message}")
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

    // ── Banner ────────────────────────────────────────────────────────────

    /**
     * BannerAd composable with full lifecycle management.
     *
     * FIXES:
     *  1. AdListener.onAdFailedToLoad → schedules a retry after BANNER_RETRY_MS
     *     so a transient network error never leaves the slot permanently blank.
     *  2. onRelease → AdView.destroy() is called when the composable leaves
     *     composition (screen navigated away). Without this, the old AdView
     *     leaks and its auto-refresh loop keeps running in the background.
     *  3. AdView.resume()/pause() wired via DisposableEffect so the AdMob SDK
     *     correctly accounts for visibility, preventing ghost refresh events.
     */
    @Composable
    fun BannerAd() {
        if (!isAdsEnabled) return
        // MobileAds.initialize() must complete before any AdMob API call.
        // mobileAdsReady is mutableStateOf so this composable recomposes
        // automatically when ConsentManager signals readiness, triggering
        // the deferred loadAd() call without any manual wiring.
        if (!mobileAdsReady) return

        // Keep a stable reference to the AdView so the retry lambda can call
        // loadAd() on the same instance (not a recreated one).
        val adViewRef = remember { mutableStateOf<AdView?>(null) }

        DisposableEffect(Unit) {
            onDispose {
                adViewRef.value?.destroy()
                adViewRef.value = null
            }
        }

        // Pause and resume the AdView with the Activity lifecycle so AdMob
        // correctly tracks visibility. Without this the banner's auto-refresh
        // timer keeps running in the background, can fire stale requests, and
        // sometimes leaves the slot blank when the user returns to the screen.
        // FIX: LocalContext is a ContextWrapper (locale-wrapped), not a LifecycleOwner.
        // LocalLifecycleOwner.current from compose-ui always returns the correct owner.
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME  -> adViewRef.value?.resume()
                    Lifecycle.Event.ON_PAUSE   -> adViewRef.value?.pause()
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            factory = { context ->
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = bannerAdUnitId

                    adListener = object : AdListener() {
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            Log.e(TAG, "Banner load failed (code ${error.code}): ${error.message}")
                            // Retry after flat delay — guard mobileAdsReady in
                            // case the retry fires during a re-init edge case.
                            handler.postDelayed({
                                if (isAdsEnabled && mobileAdsReady) {
                                    Log.d(TAG, "Banner retry triggered")
                                    try {
                                        loadAd(AdRequest.Builder().build())
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Banner retry threw: ${e.message}")
                                    }
                                }
                            }, BANNER_RETRY_MS)
                        }
                        override fun onAdLoaded() {
                            Log.d(TAG, "Banner ad loaded ✓")
                        }
                    }

                    try {
                        loadAd(AdRequest.Builder().build())
                    } catch (e: Exception) {
                        Log.e(TAG, "Banner initial load threw: ${e.message}")
                    }
                    adViewRef.value = this
                }
            },
            onRelease = { adView ->
                adView.destroy()
                adViewRef.value = null
                Log.d(TAG, "Banner AdView released")
            }
        )
    }

    // ── Native ────────────────────────────────────────────────────────────

    private var nativeAd        by mutableStateOf<NativeAd?>(null)
    private var isLoadingNative  = false
    private var nativeLoadTime   = 0L   // epoch ms when native ad was last loaded
    /** ApplicationContext stored once so retry lambdas never hold a stale Activity ref. */
    private var nativeAppContext: Context? = null

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

        if (!isAdsEnabled || !mobileAdsReady || isLoadingNative) return
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
                Log.d(TAG, "Native ad loaded ✓")
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Native load failed (code ${error.code}): ${error.message}")
                    isLoadingNative = false
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
        adLoader.loadAd(AdRequest.Builder().build())
    }

    @Composable
    fun NativeAdView() {
        if (!isAdsEnabled) return
        val context = LocalContext.current

        // Use mobileAdsReady as the key so the effect re-runs once MobileAds
        // finishes initializing. loadNative() is a no-op when mobileAdsReady=false,
        // and fires the real load when the key flips to true.
        LaunchedEffect(mobileAdsReady) {
            if (mobileAdsReady) loadNative(context.applicationContext)
        }

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

    /**
     * FreshNativeAdView — loads its OWN independent native ad every time it
     * enters composition. Unlike [NativeAdView] which uses a global singleton,
     * this composable creates a new [AdLoader] on composition and destroys the
     * ad on disposal, so every screen gets a completely fresh ad.
     *
     * Usage: call inside a remoteConfig.showNativeAd() guard at the call site.
     */
    @Composable
    fun FreshNativeAdView() {
        if (!isAdsEnabled || !mobileAdsReady) return
        val context = LocalContext.current
        var freshAd by remember { mutableStateOf<NativeAd?>(null) }
        // Retry counter — incrementing triggers LaunchedEffect re-run
        var retryTick by remember { mutableIntStateOf(0) }

        LaunchedEffect(retryTick) {
            val appCtx = context.applicationContext
            AdLoader.Builder(appCtx, nativeId)
                .forNativeAd { ad ->
                    freshAd?.destroy()
                    freshAd = ad
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.e(TAG, "FreshNative load failed: ${error.message}")
                        // Retry once after 30 seconds — prevents permanently blank slot
                        if (retryTick == 0) {
                            handler.postDelayed({
                                if (freshAd == null && isAdsEnabled && mobileAdsReady) {
                                    retryTick++
                                }
                            }, BANNER_RETRY_MS)
                        }
                    }
                })
                .build()
                .loadAd(AdRequest.Builder().build())
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
        rewardedAdManager?.preload()
    }

    /** Sets enabled flag. MobileAds.initialize() is deferred to ConsentManager. */
    fun init(@Suppress("UNUSED_PARAMETER") context: Context) {
        isAdsEnabled = true
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
                adFreeUntilMs = System.currentTimeMillis() + RewardedAdManager.AD_FREE_DURATION_MS
                Log.d(TAG, "Reward granted — ad-free until $adFreeUntilMs")
                onRewarded()
            },
            onNotReady = onNotReady
        )
    }
}

package com.aaryo.selfattendance.ads

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.ads.AdLoader            // ← correct import (top-level ads package)
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions

// ═══════════════════════════════════════════════════════════════
//  AdComposables — Thin wrappers that delegate to AdsController.
//  Direct AdLoader usage is centralised in AdsController.kt to
//  avoid duplicate loading and lifecycle issues.
// ═══════════════════════════════════════════════════════════════

/**
 * Preloads a NativeAd using [AdLoader] and delivers it via [onAdLoaded].
 * Call this inside a LaunchedEffect / SideEffect.
 */
fun loadNativeAd(
    context   : Context,
    adUnitId  : String,
    onAdLoaded: (NativeAd) -> Unit,
    onFailed  : ((String) -> Unit)? = null
) {
    val loader = AdLoader.Builder(context, adUnitId)
        .forNativeAd { ad -> onAdLoaded(ad) }
        .withNativeAdOptions(NativeAdOptions.Builder().build())
        .withAdListener(object : com.google.android.gms.ads.AdListener() {
            override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                onFailed?.invoke(error.message)
            }
        })
        .build()

    loader.loadAd(AdRequest.Builder().build())
}

/**
 * Composable that renders the standard banner ad via [AdsController].
 */
@Composable
fun BannerAdComposable() {
    AdsController.BannerAd()
}

/**
 * Composable that renders the standard native ad via [AdsController].
 */
@Composable
fun NativeAdComposable() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { AdsController.loadNative(context) }
    AdsController.NativeAdView()
}

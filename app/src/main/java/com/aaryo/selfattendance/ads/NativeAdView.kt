package com.aaryo.selfattendance.ads

import android.app.Activity
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.aaryo.selfattendance.BuildConfig
import com.aaryo.selfattendance.R
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

// Google's official test native ID
private const val TEST_NATIVE_ID = "ca-app-pub-3940256099942544/2247696110"

@Composable
fun NativeAdComponent() {

    val context = LocalContext.current

    // ✅ CONSENT CHECK — bina consent ke native ad load nahi hoga
    val activity = context as? Activity
    if (activity != null && !ConsentManager.canShowAds(activity)) {
        Log.d("NativeAdComponent", "Consent not obtained — skipping native ad")
        return
    }

    // ✅ FIX: Track NativeAd reference so it can be destroyed to prevent memory leak
    var nativeAdRef by remember { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            nativeAdRef?.destroy()
            nativeAdRef = null
        }
    }

    AndroidView(factory = { ctx ->

        val frameLayout = FrameLayout(ctx)

        // DEBUG → test ID, RELEASE → real ID from strings.xml
        val adUnitId = if (BuildConfig.DEBUG) {
            TEST_NATIVE_ID
        } else {
            ctx.getString(R.string.native_ad_unit_id)
        }

        val adLoader = AdLoader.Builder(ctx, adUnitId)
            .forNativeAd { nativeAd: NativeAd ->

                // Destroy previous ad before replacing
                nativeAdRef?.destroy()
                nativeAdRef = nativeAd

                val adView = NativeAdView(ctx)

                val headline = TextView(ctx)
                headline.text = nativeAd.headline
                headline.textSize = 16f
                headline.setPadding(20, 20, 20, 20)

                adView.headlineView = headline
                adView.addView(headline)

                adView.setNativeAd(nativeAd)

                frameLayout.removeAllViews()

                frameLayout.addView(
                    adView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
            .withNativeAdOptions(
                NativeAdOptions.Builder().build()
            )
            .build()

        adLoader.loadAd(AdRequest.Builder().build())

        frameLayout
    })
}

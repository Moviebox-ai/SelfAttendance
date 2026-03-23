package com.aaryo.selfattendance.ads

import android.util.Log
import android.widget.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.aaryo.selfattendance.BuildConfig
import com.aaryo.selfattendance.R
import com.google.android.gms.ads.*
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

private const val TEST_NATIVE_ID = "ca-app-pub-3940256099942544/2247696110"

@Composable
fun NativeAdCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // ✅ FIX 1: NativeAd ko track karo taaki properly destroy ho sake
    var nativeAdRef by remember { mutableStateOf<NativeAd?>(null) }

    // ✅ FIX 2: Composable destroy hone par NativeAd destroy karo — memory leak rokta hai
    DisposableEffect(Unit) {
        onDispose {
            nativeAdRef?.destroy()
            nativeAdRef = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->

            val container = FrameLayout(ctx)

            val adUnitId = if (BuildConfig.DEBUG) {
                TEST_NATIVE_ID
            } else {
                ctx.getString(R.string.native_ad_unit_id)
            }

            val adLoader = AdLoader.Builder(ctx, adUnitId)

                .forNativeAd { nativeAd: NativeAd ->

                    // ✅ FIX 3: Purana ad destroy karo pehle
                    nativeAdRef?.destroy()
                    nativeAdRef = nativeAd

                    val adView = NativeAdView(ctx)

                    val layout = LinearLayout(ctx)
                    layout.orientation = LinearLayout.VERTICAL
                    layout.setPadding(32, 32, 32, 32)

                    // ✅ FIX 4: MediaView — image/video ke liye
                    val mediaView = MediaView(ctx)
                    mediaView.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        400
                    )

                    val headline = TextView(ctx)
                    headline.textSize = 18f
                    headline.text = nativeAd.headline

                    val body = TextView(ctx)
                    body.text = nativeAd.body ?: ""

                    val button = Button(ctx)
                    button.text = nativeAd.callToAction ?: "Install"

                    layout.addView(mediaView)
                    layout.addView(headline)
                    layout.addView(body)
                    layout.addView(button)

                    adView.addView(layout)

                    // ✅ FIX 5: Sabhi views NativeAdView se bind karo
                    adView.mediaView  = mediaView
                    adView.headlineView = headline
                    adView.bodyView   = body
                    adView.callToActionView = button

                    adView.setNativeAd(nativeAd)

                    container.removeAllViews()
                    container.addView(adView)
                }

                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.e("NativeAdCard", "❌ Failed: [${error.code}] ${error.message}")
                    }
                    override fun onAdLoaded() {
                        Log.d("NativeAdCard", "✅ Native ad loaded")
                    }
                })

                .withNativeAdOptions(
                    NativeAdOptions.Builder()
                        .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                        .build()
                )

                .build()

            adLoader.loadAd(AdRequest.Builder().build())

            container
        }
    )
}

package com.aaryo.selfattendance.ads

import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aaryo.selfattendance.BuildConfig
import com.aaryo.selfattendance.R
import com.google.android.gms.ads.*

private const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"

@Composable
fun BannerAd(
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // ✅ FIX 1: remember use kiya — recomposition par naya AdView nahi banega
    val adView = remember {
        AdView(context).apply {

            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )

            val displayMetrics = context.resources.displayMetrics
            val adWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt()
            setAdSize(
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth)
            )

            adUnitId = if (BuildConfig.DEBUG) {
                TEST_BANNER_ID
            } else {
                context.getString(R.string.banner_ad_unit_id)
            }

            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.d("BannerAd", "✅ Banner loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("BannerAd", "❌ Failed: [${error.code}] ${error.message}")
                }
                override fun onAdImpression() {
                    Log.d("BannerAd", "📊 Impression")
                }
            }

            loadAd(AdRequest.Builder().build())
        }
    }

    // ✅ FIX 2: Lifecycle se resume/pause handle karo — ad properly pause/resume hogi
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> adView.resume()
                Lifecycle.Event.ON_PAUSE  -> adView.pause()
                Lifecycle.Event.ON_DESTROY -> adView.destroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adView.destroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { adView }
    )
}

package com.aaryo.selfattendance.ads

object AdConfig {

    const val AD_COOLDOWN = 45000L

    // ─── Google Test Ad Unit IDs ──────────────────────────────────────────────
    // Sirf DEBUG builds mein use hote hain (BuildConfig.DEBUG check se)
    const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    const val TEST_APP_OPEN     = "ca-app-pub-3940256099942544/9257395921"
    const val TEST_BANNER       = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_NATIVE       = "ca-app-pub-3940256099942544/2247696110"
    const val TEST_REWARDED     = "ca-app-pub-3940256099942544/5224354917"

    // ─── Unity Ads Mediation ──────────────────────────────────────────────────
    // AdMob Console → Mediation Groups mein configure karo
    // Unity Dashboard Game ID: 6044793
    // Placements: Interstitial_Android | Banner_Android | Rewarded_Android
    //
    // Flow: AdMob request → AdMob fill kare → fail hone par Unity Ads fill kare
    //
    // ⚠️  Koi manual UnityAds.initialize() call mat karo.
    //     MobileAds.initialize() mediation adapter ko automatically activate karta hai.
}

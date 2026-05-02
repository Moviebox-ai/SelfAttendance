package com.aaryo.selfattendance.ui.wallet

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aaryo.selfattendance.ads.RewardedAdManager
import com.aaryo.selfattendance.coin.CoinRepository
import com.aaryo.selfattendance.coin.DailyRewardManager
import com.aaryo.selfattendance.coin.WeeklyPrizeManager
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.aaryo.selfattendance.ui.premium.PremiumManager

class WalletViewModelFactory(private val context: Context) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WalletViewModel::class.java)) {
            val prefs              = PreferencesManager(context.applicationContext)
            val coinRepo           = CoinRepository(preferencesManager = prefs)
            val rewardedAdManager  = RewardedAdManager(context.applicationContext)
            val dailyRewardManager = DailyRewardManager(coinRepo)
            val premiumManager     = PremiumManager(coinRepo)
            val weeklyPrizeManager = WeeklyPrizeManager(coinRepo)

            return WalletViewModel(
                coinRepository     = coinRepo,
                rewardedAdManager  = rewardedAdManager,
                dailyRewardManager = dailyRewardManager,
                premiumManager     = premiumManager,
                weeklyPrizeManager = weeklyPrizeManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

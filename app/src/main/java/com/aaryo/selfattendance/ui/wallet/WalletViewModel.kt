package com.aaryo.selfattendance.ui.wallet

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aaryo.selfattendance.ads.RewardedAdManager
import com.aaryo.selfattendance.coin.CoinRepository
import com.aaryo.selfattendance.coin.CoinTransaction
import com.aaryo.selfattendance.coin.DailyRewardManager
import com.aaryo.selfattendance.coin.LeaderboardEntry
import com.aaryo.selfattendance.coin.PremiumFeature
import com.aaryo.selfattendance.coin.WeeklyPrizeManager
import com.aaryo.selfattendance.notifications.AppNotificationManager
import com.aaryo.selfattendance.ui.premium.PremiumManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ─── UI State ────────────────────────────────────────────────────────────────

data class WalletUiState(
    val isLoading              : Boolean                      = true,
    val isWalletEnabled        : Boolean                      = true,
    val isAdRewardEnabled      : Boolean                      = true,
    val balance                : Int                          = 0,
    val currentStreak          : Int                          = 0,
    val longestStreak          : Int                          = 0,
    val adCoinsEarnedToday     : Int                          = 0,
    val dailyLimit             : Int                          = 50,
    val transactions           : List<CoinTransaction>        = emptyList(),
    val unlockedFeatures       : Map<PremiumFeature, Boolean> = emptyMap(),
    val adminLockedFeatures    : Map<PremiumFeature, Boolean> = emptyMap(),
    val isAdReady              : Boolean                      = false,
    val isAdLoading            : Boolean                      = false,
    val toastMessage           : String?                      = null,
    val showDailyRewardDialog  : Boolean                      = false,
    val dailyRewardResult      : DailyRewardManager.DailyRewardResult? = null,
    val rewardEarned           : Int?                         = null,
    val adCooldownSeconds      : Long                         = 0L,

    // ── Weekly Prize State ────────────────────────────────────────────────
    val weeklyCoins            : Int                          = 0,
    val leaderboard            : List<LeaderboardEntry>       = emptyList(),
    val currentUserEntry       : LeaderboardEntry?            = null,
    val daysRemainingInWeek    : Int                          = 7,
    val isLeaderboardLoading   : Boolean                      = true,
    val showGrandPrizeDialog   : Boolean                      = false,
) {
    val canEarnNow: Boolean
        get() = isWalletEnabled
                && isAdRewardEnabled
                && adCoinsEarnedToday < dailyLimit
                && adCooldownSeconds == 0L
                && !isAdLoading
                && isAdReady
}

// ─── ViewModel ───────────────────────────────────────────────────────────────

class WalletViewModel(
    private val coinRepository    : CoinRepository,
    private val rewardedAdManager : RewardedAdManager,
    private val dailyRewardManager: DailyRewardManager,
    private val premiumManager    : PremiumManager,
    private val weeklyPrizeManager: WeeklyPrizeManager = WeeklyPrizeManager()
) : ViewModel() {

    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    private var cooldownTickJob: Job? = null

    init {
        viewModelScope.launch {
            coinRepository.fetchAdminSettings()
            loadWalletData()
            processDailyLogin()
            rewardedAdManager.preload()
            startCooldownTicker()
            loadLeaderboard()
        }
        // Separately watch for the ad becoming ready and update UI state.
        // This ensures the "Watch Ad" button enables as soon as the ad loads,
        // even if the cooldown ticker has already stopped.
        viewModelScope.launch {
            repeat(30) { // poll for up to 30 seconds
                delay(1_000)
                if (rewardedAdManager.isAdReady) {
                    _uiState.update { it.copy(isAdReady = true) }
                    return@launch
                }
            }
        }
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private suspend fun loadWalletData() {
        try {
            val balance        = coinRepository.getBalance()
            val (streak, longestStreak) = coinRepository.getStreakInfo()
            val earnedToday    = coinRepository.getAdCoinsEarnedToday()
            val txs            = coinRepository.getRecentTransactions()
            val featureStatus  = premiumManager.getAllFeatureStatus()
            val adminLocked    = PremiumFeature.values()
                .associateWith { premiumManager.isAdminLocked(it) }

            _uiState.update {
                it.copy(
                    isLoading           = false,
                    isWalletEnabled     = coinRepository.isWalletEnabled,
                    isAdRewardEnabled   = coinRepository.isAdRewardEnabled,
                    balance             = balance,
                    currentStreak       = streak,
                    longestStreak       = longestStreak,
                    adCoinsEarnedToday  = earnedToday,
                    dailyLimit          = coinRepository.dailyLimit,
                    transactions        = txs,
                    unlockedFeatures    = featureStatus,
                    adminLockedFeatures = adminLocked,
                    isAdReady           = rewardedAdManager.isAdReady,
                    daysRemainingInWeek = weeklyPrizeManager.daysRemainingInWeek()
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun loadLeaderboard() {
        _uiState.update { it.copy(isLeaderboardLoading = true) }
        try {
            val entries   = weeklyPrizeManager.getLeaderboard()
            val userEntry = weeklyPrizeManager.getCurrentUserEntry()
            _uiState.update {
                it.copy(
                    leaderboard          = entries,
                    currentUserEntry     = userEntry,
                    weeklyCoins          = userEntry?.weeklyCoins ?: 0,
                    isLeaderboardLoading = false
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLeaderboardLoading = false) }
        }
    }

    private suspend fun processDailyLogin() {
        val result = dailyRewardManager.processDailyLogin()
        result?.let {
            // BUG FIX: Previously did `state.balance + result.coinsEarned` which
            // adds to the CURRENT local balance. But if loadWalletData() and
            // processDailyLogin() run concurrently, state.balance may not yet be
            // the latest Firestore value, causing the displayed balance to be wrong.
            // Fix: use result.newBalance (the authoritative post-transaction balance
            // returned by CoinRepository directly from Firestore transaction).
            _uiState.update { state ->
                state.copy(
                    balance               = result.newBalance,
                    showDailyRewardDialog = true,
                    dailyRewardResult     = result
                )
            }
            weeklyPrizeManager.syncWeeklyCoins(result.coinsEarned)
        }
    }

    // ── Earn Coins (Ad) ───────────────────────────────────────────────────────

    fun onEarnCoinsClicked(activity: Activity) {
        if (!_uiState.value.canEarnNow) return
        _uiState.update { it.copy(isAdLoading = true) }

        rewardedAdManager.showAd(activity) { result ->
            viewModelScope.launch {
                when (result) {

                    is RewardedAdManager.RewardedAdResult.RewardEarned -> {
                        val rewardResult = coinRepository.addAdReward()

                        rewardResult.onSuccess { newBalance ->
                            val earned   = coinRepository.adRewardAmount
                            val newToday = _uiState.value.adCoinsEarnedToday + earned
                            val cooldown = coinRepository.adCooldownRemainingSeconds()

                            _uiState.update {
                                it.copy(
                                    isAdLoading        = false,
                                    balance            = newBalance,
                                    adCoinsEarnedToday = newToday,
                                    isAdReady          = rewardedAdManager.isAdReady,
                                    rewardEarned       = earned,
                                    adCooldownSeconds  = cooldown
                                )
                            }

                            // Sync to weekly prize leaderboard
                            weeklyPrizeManager.syncWeeklyCoins(earned)
                            loadLeaderboard()

                            AppNotificationManager.showRewardNotification(
                                activity.applicationContext,
                                earned
                            )

                            showToast("+$earned coins earned! 🎉")
                            refreshTransactions()
                            startCooldownTicker()
                            rewardedAdManager.preload()
                            // Watch for next ad to load and re-enable button
                            watchForAdReady()
                        }

                        rewardResult.onFailure { e ->
                            _uiState.update { it.copy(isAdLoading = false) }
                            showToast(e.message ?: "Could not save reward. Try again.")
                            loadWalletData()
                        }
                    }

                    is RewardedAdManager.RewardedAdResult.Dismissed -> {
                        showToast("Watch the full ad to earn coins!")
                        _uiState.update {
                            it.copy(isAdLoading = false, isAdReady = rewardedAdManager.isAdReady)
                        }
                        rewardedAdManager.preload()
                    }

                    is RewardedAdManager.RewardedAdResult.NotReady -> {
                        showToast("Ad loading… try again in a few seconds.")
                        _uiState.update { it.copy(isAdLoading = false, isAdReady = false) }
                        rewardedAdManager.preload()
                    }

                    is RewardedAdManager.RewardedAdResult.Failed -> {
                        _uiState.update { it.copy(isAdLoading = false) }
                        showToast("Ad failed to load. Try again later.")
                        rewardedAdManager.preload()
                    }
                }
            }
        }
    }

    // ── Premium Unlock ────────────────────────────────────────────────────────

    fun onUnlockFeature(feature: PremiumFeature) {
        viewModelScope.launch {
            val result = premiumManager.unlockWithCoins(feature)
            result.onSuccess { newBalance ->
                showToast("${feature.displayName} unlocked! 🔓")
                _uiState.update {
                    it.copy(
                        balance          = newBalance,
                        unlockedFeatures = it.unlockedFeatures + (feature to true)
                    )
                }
                refreshTransactions()
            }
            result.onFailure { e ->
                showToast(e.message ?: "Could not unlock feature.")
            }
        }
    }

    // ── Grand Prize Dialog ────────────────────────────────────────────────────

    fun showGrandPrizeInfo()     { _uiState.update { it.copy(showGrandPrizeDialog = true)  } }
    fun dismissGrandPrizeDialog(){ _uiState.update { it.copy(showGrandPrizeDialog = false) } }

    // ── Dialog / Toast helpers ────────────────────────────────────────────────

    fun dismissDailyRewardDialog() {
        _uiState.update { it.copy(showDailyRewardDialog = false, dailyRewardResult = null) }
    }
    fun clearReward() { _uiState.update { it.copy(rewardEarned = null) } }
    fun clearToast()  { _uiState.update { it.copy(toastMessage = null) } }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Polls until rewarded ad is ready, then updates UI state. */
    private fun watchForAdReady() {
        viewModelScope.launch {
            repeat(30) {
                delay(1_000)
                if (rewardedAdManager.isAdReady) {
                    _uiState.update { it.copy(isAdReady = true) }
                    return@launch
                }
            }
        }
    }

    private fun showToast(message: String) {
        _uiState.update { it.copy(toastMessage = message) }
    }

    private suspend fun refreshTransactions() {
        val txs = coinRepository.getRecentTransactions()
        _uiState.update { it.copy(transactions = txs) }
    }

    private fun startCooldownTicker() {
        cooldownTickJob?.cancel()
        cooldownTickJob = viewModelScope.launch {
            // Poll every second while cooldown is active AND for a short window
            // after it ends so the button re-enables as soon as the ad is ready.
            var idleTicks = 0
            while (true) {
                val remaining = coinRepository.adCooldownRemainingSeconds()
                val adReady   = rewardedAdManager.isAdReady
                _uiState.update {
                    it.copy(
                        adCooldownSeconds = remaining,
                        isAdReady         = adReady
                    )
                }
                // Keep ticking for up to 30s after cooldown ends to catch ad load
                if (remaining <= 0) {
                    if (adReady) break          // ad ready → stop
                    if (idleTicks >= 30) break  // give up after 30s
                    idleTicks++
                } else {
                    idleTicks = 0
                }
                delay(1_000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cooldownTickJob?.cancel()
    }
}

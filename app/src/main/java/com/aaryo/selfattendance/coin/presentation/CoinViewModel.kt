package com.aaryo.selfattendance.coin.presentation

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aaryo.selfattendance.ads.RewardedAdManager
import com.aaryo.selfattendance.coin.CoinRepository
import com.aaryo.selfattendance.coin.CoinTransaction
import com.aaryo.selfattendance.notifications.AppNotificationManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ─── UI State ─────────────────────────────────────────────────────────────────

data class CoinUiState(
    val balance           : Int                    = 0,
    val transactions      : List<CoinTransaction>  = emptyList(),
    val isAdLoading       : Boolean                = false,
    val isAdReady         : Boolean                = false,
    val toastMessage      : String?                = null,
    val isLoading         : Boolean                = true,
    /** Non-null triggers the reward popup */
    val rewardEarned      : Int?                   = null,
    /** Coins earned from ads today (synced from repo on load) */
    val adCoinsEarnedToday: Int                    = 0,
    /** Daily limit from admin settings */
    val dailyLimit        : Int                    = 50,
    /** Seconds remaining in ad cooldown (0 = ready) */
    val adCooldownSeconds : Long                   = 0L
) {
    /** True only when ALL conditions allow earning. Drives button enabled state. */
    val canEarnNow: Boolean
        get() = !isAdLoading && isAdReady
                && adCoinsEarnedToday < dailyLimit
                && adCooldownSeconds == 0L
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class CoinViewModel(
    private val coinRepository   : CoinRepository,
    private val rewardedAdManager: RewardedAdManager
) : ViewModel() {

    companion object {
        private const val TAG = "CoinViewModel"
    }

    private val _uiState = MutableStateFlow(CoinUiState())
    val uiState: StateFlow<CoinUiState> = _uiState.asStateFlow()

    private var cooldownTickJob: Job? = null

    init {
        loadData()
        rewardedAdManager.preload()
        startCooldownTicker()
    }

    // ── Load Data ────────────────────────────────────────────────────

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val balance      = coinRepository.getBalance()
            val transactions = coinRepository.getRecentTransactions()
            val todayCoins   = coinRepository.getAdCoinsEarnedToday()
            val cooldown     = coinRepository.adCooldownRemainingSeconds()

            _uiState.update {
                it.copy(
                    isLoading            = false,
                    balance              = balance,
                    transactions         = transactions,
                    isAdReady            = rewardedAdManager.isAdReady,
                    adCoinsEarnedToday   = todayCoins,
                    dailyLimit           = coinRepository.dailyLimit,
                    adCooldownSeconds    = cooldown
                )
            }
            // BUG FIX: If cooldown is active, keep polling so button
            // re-enables automatically when cooldown + ad load both complete.
            if (cooldown > 0 || !rewardedAdManager.isAdReady) {
                startCooldownTicker()
            }
        }
    }

    // ── Cooldown ticker ──────────────────────────────────────────────

    private fun startCooldownTicker() {
        cooldownTickJob?.cancel()
        cooldownTickJob = viewModelScope.launch {
            // BUG FIX: Previously broke out of loop when remaining==0 without
            // updating isAdReady, so the "Watch Ad" button stayed permanently
            // disabled after a cooldown period. Now we keep ticking for up to
            // 30 extra seconds after cooldown ends, updating isAdReady each tick
            // so the button re-enables as soon as the next ad finishes loading.
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
                if (remaining <= 0) {
                    if (adReady) break          // ad ready → stop polling
                    if (idleTicks >= 30) break  // give up after 30s
                    idleTicks++
                } else {
                    idleTicks = 0
                }
                delay(1_000L)
            }
        }
    }

    // ── ViewModel-level gate (synchronous) ───────────────────────────

    private fun vmCanEarnCoins(): Pair<Boolean, String?> {
        val state = _uiState.value
        return when {
            state.adCooldownSeconds > 0 ->
                false to "Please wait ${state.adCooldownSeconds}s before the next ad."
            state.adCoinsEarnedToday >= state.dailyLimit ->
                false to "Daily limit reached! Come back tomorrow 🌙"
            !state.isAdReady   -> false to "Ad not ready yet, please wait…"
            state.isAdLoading  -> false to null  // button already disabled
            else               -> true to null
        }
    }

    // ── Watch Ad → Earn Coins ────────────────────────────────────────

    fun onWatchAdClicked(activity: Activity) {
        // ── ViewModel-level gate ──────────────────────────────────────
        val (allowed, reason) = vmCanEarnCoins()
        if (!allowed) {
            reason?.let { showToast(it) }
            return
        }

        _uiState.update { it.copy(isAdLoading = true) }

        rewardedAdManager.showAd(activity) { result ->
            viewModelScope.launch {
                _uiState.update { it.copy(isAdLoading = false) }

                when (result) {

                    // ── SUCCESS ───────────────────────────────────────
                    is RewardedAdManager.RewardedAdResult.RewardEarned -> {
                        // Repository-level gate (authoritative Firestore check)
                        val adResult = coinRepository.addAdReward()

                        adResult.onSuccess { newBalance ->
                            val rewardCoins = coinRepository.adRewardAmount
                            val newToday    = _uiState.value.adCoinsEarnedToday + rewardCoins
                            val cooldown    = coinRepository.adCooldownRemainingSeconds()

                            _uiState.update {
                                it.copy(
                                    balance            = newBalance,
                                    rewardEarned       = rewardCoins,
                                    isAdReady          = rewardedAdManager.isAdReady,
                                    adCoinsEarnedToday = newToday,
                                    adCooldownSeconds  = cooldown
                                )
                            }

                            AppNotificationManager.showRewardNotification(
                                activity.applicationContext,
                                rewardCoins
                            )

                            showToast("+$rewardCoins coins earned! 🎉")
                            refreshTransactions()
                            startCooldownTicker()
                            rewardedAdManager.preload()

                            Log.d(TAG, "Reward saved: +$rewardCoins → balance=$newBalance today=$newToday")
                        }

                        adResult.onFailure { e ->
                            showToast(e.message ?: "Failed to save reward. Try again.")
                            Log.w(TAG, "addAdReward failed: ${e.message}")
                        }
                    }

                    // ── USER SKIPPED ──────────────────────────────────
                    is RewardedAdManager.RewardedAdResult.Dismissed -> {
                        showToast("Watch the full ad to earn coins")
                        rewardedAdManager.preload()
                    }

                    // ── NOT READY ─────────────────────────────────────
                    is RewardedAdManager.RewardedAdResult.NotReady -> {
                        showToast("Ad not ready, try again in a moment")
                        _uiState.update { it.copy(isAdReady = false) }
                        rewardedAdManager.preload()
                    }

                    // ── FAILED ────────────────────────────────────────
                    is RewardedAdManager.RewardedAdResult.Failed -> {
                        showToast("Ad failed to load. Try again later.")
                        rewardedAdManager.preload()
                    }
                }

                _uiState.update { it.copy(isAdReady = rewardedAdManager.isAdReady) }
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun clearReward() {
        _uiState.update { it.copy(rewardEarned = null) }
    }

    private fun showToast(msg: String) {
        _uiState.update { it.copy(toastMessage = msg) }
    }

    private suspend fun refreshTransactions() {
        val txs = coinRepository.getRecentTransactions()
        _uiState.update { it.copy(transactions = txs) }
    }

    override fun onCleared() {
        super.onCleared()
        cooldownTickJob?.cancel()
    }
}

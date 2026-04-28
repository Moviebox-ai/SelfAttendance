package com.aaryo.selfattendance.ui.premium

import com.aaryo.selfattendance.coin.CoinRepository
import com.aaryo.selfattendance.coin.PremiumFeature

/**
 * Central access point for premium feature checks.
 *
 * Features can be unlocked via:
 *  1. Coins (earned through ads/daily rewards)
 *  2. Premium subscription (Google Play Billing)
 *
 * Usage in any screen:
 *   val canExport = PremiumManager(coinRepository).canUseFeature(PremiumFeature.PDF_EXPORT)
 */
class PremiumManager(private val coinRepository: CoinRepository) {

    /**
     * Returns true if user can use the given feature.
     * FALSE if admin has force-locked the feature (even if user paid).
     * TRUE only if user has unlocked it AND admin hasn't locked it.
     */
    suspend fun canUseFeature(feature: PremiumFeature): Boolean {
        if (coinRepository.isAdminLocked(feature)) return false
        return coinRepository.isFeatureUnlocked(feature)
    }

    /**
     * Attempts to unlock feature using coins.
     * Blocked if admin has locked the feature.
     */
    suspend fun unlockWithCoins(feature: PremiumFeature): Result<Int> {
        if (coinRepository.isAdminLocked(feature)) {
            return Result.failure(Exception("This feature is currently disabled by admin."))
        }
        return coinRepository.spendCoinsForFeature(feature)
    }

    /**
     * Returns map of all features and their effective unlock status.
     * Admin-locked features show as locked regardless of user's coins.
     */
    suspend fun getAllFeatureStatus(): Map<PremiumFeature, Boolean> {
        val unlocked = coinRepository.getUnlockedFeatures()
        return PremiumFeature.values().associateWith { feature ->
            if (coinRepository.isAdminLocked(feature)) false
            else unlocked[feature.firestoreKey] == true
        }
    }

    /** Whether admin has force-locked this feature (to show different UI) */
    fun isAdminLocked(feature: PremiumFeature): Boolean =
        coinRepository.isAdminLocked(feature)
}

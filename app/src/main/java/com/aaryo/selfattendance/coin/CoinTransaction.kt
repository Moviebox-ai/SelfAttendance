package com.aaryo.selfattendance.coin

import com.google.firebase.Timestamp

/**
 * Represents a single coin transaction in Firestore.
 *
 * Policy-compliant: virtual currency only.
 * No real money, no misleading claims.
 */
data class CoinTransaction(
    val id: String = "",
    val type: TransactionType = TransactionType.AD_REWARD,
    val amount: Int = 0,
    val timestamp: Timestamp = Timestamp.now(),
    val description: String = ""
) {
    constructor() : this("", TransactionType.AD_REWARD, 0, Timestamp.now(), "")
}

enum class TransactionType {
    AD_REWARD,          // Watched rewarded ad
    DAILY_LOGIN,        // Daily login bonus
    STREAK_BONUS,       // Streak milestone bonus
    FEATURE_UNLOCK,     // Spent coins to unlock feature (negative)
    SUBSCRIPTION        // Premium subscription (negative)
}

// -------- Feature definitions --------

enum class PremiumFeature(
    val displayName  : String,
    val defaultCost  : Int,
    val firestoreKey : String
) {
    PDF_EXPORT  ("PDF Export",           defaultCost = 100, firestoreKey = "pdf_export"),
    ANALYTICS   ("Analytics & Reports",  defaultCost = 150, firestoreKey = "analytics"),
    BACKUP      ("Cloud Backup",         defaultCost = 200, firestoreKey = "backup"),
    SALARY_SLIP ("Salary Slip Export",   defaultCost = 120, firestoreKey = "salary_slip"),
    REMOVE_ADS  ("Remove Ads (7 days)", defaultCost = 300, firestoreKey = "remove_ads");

    // Mutable at runtime — admin can override price from Firestore adminSettings
    var coinCost: Int = defaultCost
}

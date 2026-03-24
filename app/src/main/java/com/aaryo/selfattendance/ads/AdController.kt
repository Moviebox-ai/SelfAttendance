package com.aaryo.selfattendance.ads

/**
 * @deprecated Use SmartAdController which includes time-based cooldown.
 * Kept for backward compatibility.
 */
@Deprecated("Use SmartAdController instead", ReplaceWith("SmartAdController.shouldShowAd()"))
object AdController {

    fun shouldShowAd(): Boolean = SmartAdController.shouldShowAd()

    fun reset() = SmartAdController.resetSession()
}

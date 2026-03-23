package com.aaryo.selfattendance.security

/**
 * @deprecated Use IntegrityCheck instead.
 * Kept for backward compatibility — will be removed in next version.
 */
@Deprecated("Use IntegrityCheck instead", ReplaceWith("IntegrityCheck(context).check()"))
class IntegrityManager(private val context: android.content.Context) {

    fun checkIntegrity() {
        IntegrityCheck(context).check()
    }
}

package com.aaryo.selfattendance.admin

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

// ═══════════════════════════════════════════════════════════════
//  AdminAuditLogger
//
//  Every admin action is written to:
//  Firestore: adminAuditLog/{autoId}
//  {
//    adminUid    : String   — who did it (Firebase UID)
//    action      : String   — action type enum name
//    targetUserId: String   — affected user
//    details     : String   — human-readable summary
//    timestamp   : Timestamp
//    deviceInfo  : String   — Android model / SDK
//  }
//
//  Firestore Rules should restrict this collection to
//  authenticated users with admin custom claim only.
// ═══════════════════════════════════════════════════════════════

enum class AdminAction {
    VIEW_USER,
    ADD_COINS,
    DEDUCT_COINS,
    RESET_WALLET,
    VIEW_TRANSACTIONS,
    ADMIN_LOGIN,
    ADMIN_LOGOUT,
    PIN_FAILED
}

object AdminAuditLogger {

    private const val TAG        = "AdminAuditLogger"
    private const val COLLECTION = "adminAuditLog"

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    suspend fun log(
        action      : AdminAction,
        targetUserId: String = "",
        details     : String = ""
    ) {
        try {
            val adminUid = auth.currentUser?.uid ?: "unauthenticated"
            val record = hashMapOf(
                "adminUid"     to adminUid,
                "action"       to action.name,
                "targetUserId" to targetUserId,
                "details"      to details,
                "timestamp"    to Timestamp.now(),
                "deviceInfo"   to "${android.os.Build.MODEL} / SDK ${android.os.Build.VERSION.SDK_INT}"
            )
            db.collection(COLLECTION).add(record).await()
        } catch (e: Exception) {
            // Audit logging failure should never crash the app
            Log.e(TAG, "Audit log failed: ${e.message}")
        }
    }
}

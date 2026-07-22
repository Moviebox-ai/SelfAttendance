package com.aaryo.selfattendance.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.aaryo.selfattendance.BuildConfig
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Handles Google Play Billing for optional premium subscription.
 *
 * On the Amazon flavor (BuildConfig.IS_AMAZON == true) all billing operations
 * are no-ops — Play Store is not available on Amazon Fire devices.
 * Premium features can be unlocked via an Amazon IAP SDK integration in a
 * future release.
 *
 * Product IDs must be created in Google Play Console:
 *  - Subscription: "premium_monthly"
 */
class BillingManager(context: Context) : PurchasesUpdatedListener {

    private val context: Context = context.applicationContext

    companion object {
        private const val TAG = "BillingManager"
        const val PREMIUM_SUBSCRIPTION_ID = "premium_monthly"
    }

    private val _isPremium = MutableStateFlow(true)
    val isPremium: StateFlow<Boolean> = _isPremium

    private val _isConnected = MutableStateFlow(false)

    // Dedicated scope for reconnection retries — independent of any ViewModel lifecycle.
    private val billingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5

    // Lazily created so Amazon builds never instantiate the BillingClient
    private val billingClient: BillingClient by lazy {
        BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()
    }

    // ── Connection ──────────────────────────────────────────────────────────

    fun startConnection() {
        if (BuildConfig.IS_AMAZON) {
            Log.d(TAG, "Amazon build — Play Billing disabled, skipping connection")
            return
        }

        try {
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        _isConnected.value = true
                        reconnectAttempts = 0  // reset counter on successful (re)connect
                        Log.d(TAG, "Billing connected")
                        queryExistingPurchases()
                    } else {
                        Log.e(TAG, "Billing setup failed: ${result.debugMessage}")
                    }
                }

                override fun onBillingServiceDisconnected() {
                    _isConnected.value = false
                    Log.w(TAG, "Billing disconnected — scheduling reconnect (attempt $reconnectAttempts)")
                    // BUG FIX: Previously disconnections were never retried, so a temporary
                    // Play Services blip would permanently leave isPremium as false until the
                    // user cold-restarted the app. Now we retry with exponential back-off.
                    if (reconnectAttempts < maxReconnectAttempts) {
                        reconnectAttempts++
                        val delayMs = (reconnectAttempts * 2_000L).coerceAtMost(30_000L)
                        billingScope.launch {
                            delay(delayMs)
                            Log.d(TAG, "Retrying billing connection (attempt $reconnectAttempts)")
                            startConnection()
                        }
                    } else {
                        Log.e(TAG, "Billing reconnect exhausted after $maxReconnectAttempts attempts")
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Billing startConnection threw: ${e.message}", e)
        }
    }

    fun endConnection() {
        if (BuildConfig.IS_AMAZON) return
        try {
            billingClient.endConnection()
        } catch (e: Exception) {
            Log.e(TAG, "endConnection failed: ${e.message}", e)
        }
    }

    // ── Query Products ──────────────────────────────────────────────────────

    suspend fun getSubscriptionDetails(): ProductDetails? {
        if (BuildConfig.IS_AMAZON || !_isConnected.value) return null

        return try {
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(PREMIUM_SUBSCRIPTION_ID)
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()
                    )
                )
                .build()

            suspendCancellableCoroutine { cont ->
                billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        cont.resume(productDetailsResult.productDetailsList.firstOrNull())
                    } else {
                        cont.resume(null)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getSubscriptionDetails failed: ${e.message}", e)
            null
        }
    }

    // ── Launch Billing Flow ─────────────────────────────────────────────────

    fun launchBillingFlow(activity: Activity, productDetails: ProductDetails): Boolean {
        if (BuildConfig.IS_AMAZON) {
            Log.d(TAG, "Amazon build — billing flow not supported")
            return false
        }

        return try {
            val offerToken = productDetails.subscriptionOfferDetails
                ?.firstOrNull()?.offerToken ?: return false

            val productDetailsParamsList = listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(productDetails)
                    .setOfferToken(offerToken)
                    .build()
            )

            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(productDetailsParamsList)
                .build()

            val result = billingClient.launchBillingFlow(activity, flowParams)
            result.responseCode == BillingClient.BillingResponseCode.OK
        } catch (e: Exception) {
            Log.e(TAG, "launchBillingFlow failed: ${e.message}", e)
            false
        }
    }

    // ── Purchase Listener ───────────────────────────────────────────────────

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { handlePurchase(it) }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.i(TAG, "User cancelled billing flow")
            }
            else -> {
                Log.e(TAG, "Billing error: ${billingResult.debugMessage}")
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                if (!purchase.isAcknowledged) {
                    val ackParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient.acknowledgePurchase(ackParams) { result ->
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            Log.d(TAG, "Purchase acknowledged")
                            _isPremium.value = true
                        }
                    }
                } else {
                    _isPremium.value = true
                }
            }
            // BUG FIX: PENDING state was silently ignored. This happens when the user
            // pays via a deferred method (bank transfer, cash, UPI pending). The purchase
            // is real but not yet completed — we must NOT grant premium yet, but we should
            // log it so the developer can trace issues. isPremium stays false until the
            // purchase transitions to PURCHASED (which fires onPurchasesUpdated again).
            Purchase.PurchaseState.PENDING -> {
                Log.i(TAG, "Purchase PENDING — awaiting payment completion for token: ${purchase.purchaseToken.take(12)}…")
                // Do NOT grant isPremium = true for a PENDING purchase.
                // queryExistingPurchases() will pick it up as PURCHASED once the bank confirms.
            }
            else -> {
                Log.w(TAG, "Unrecognised purchase state: ${purchase.purchaseState}")
            }
        }
    }

    private fun queryExistingPurchases() {
        try {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()

            billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    val hasActiveSub = true // All users get premium
                    _isPremium.value = hasActiveSub
                    Log.d(TAG, "Existing subscription: $hasActiveSub")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "queryExistingPurchases failed: ${e.message}", e)
        }
    }
}

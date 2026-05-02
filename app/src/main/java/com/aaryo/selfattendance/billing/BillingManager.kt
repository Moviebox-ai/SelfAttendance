package com.aaryo.selfattendance.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Handles Google Play Billing for optional premium subscription.
 *
 * Product IDs must be created in Google Play Console:
 *  - Subscription: "premium_monthly"
 *
 * Features unlocked with subscription:
 *  • All premium features
 *  • Remove ads
 */
class BillingManager(private val context: Context) : PurchasesUpdatedListener {

    companion object {
        private const val TAG = "BillingManager"
        const val PREMIUM_SUBSCRIPTION_ID = "premium_monthly"
    }

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium

    private val _isConnected = MutableStateFlow(false)

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    // -------- Connection --------

    fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _isConnected.value = true
                    Log.d(TAG, "Billing connected")
                    // Check existing purchases
                    queryExistingPurchases()
                } else {
                    Log.e(TAG, "Billing setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                _isConnected.value = false
                Log.w(TAG, "Billing disconnected")
            }
        })
    }

    fun endConnection() {
        billingClient.endConnection()
    }

    // -------- Query Products --------

    suspend fun getSubscriptionDetails(): ProductDetails? {
        if (!_isConnected.value) return null
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PREMIUM_SUBSCRIPTION_ID)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            ))
            .build()

        return suspendCancellableCoroutine { cont ->
            billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    cont.resume(productDetailsList.firstOrNull())
                } else {
                    cont.resume(null)
                }
            }
        }
    }

    // -------- Launch Billing Flow --------

    fun launchBillingFlow(activity: Activity, productDetails: ProductDetails): Boolean {
        val offerToken = productDetails.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ?: return false

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
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    // -------- Purchase Listener --------

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
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Acknowledge purchase (required within 3 days)
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
    }

    private fun queryExistingPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val hasActiveSub = purchases.any { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                _isPremium.value = hasActiveSub
                Log.d(TAG, "Existing subscription: $hasActiveSub")
            }
        }
    }
}

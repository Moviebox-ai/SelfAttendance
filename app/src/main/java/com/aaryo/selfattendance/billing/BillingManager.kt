package com.aaryo.selfattendance.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.aaryo.selfattendance.BuildConfig
import com.android.billingclient.api.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Handles Google Play Billing for Employee Premium and Business Mode Pro subscriptions.
 */
class BillingManager private constructor(context: Context) : PurchasesUpdatedListener {

    private val context: Context = context.applicationContext

    companion object {
        private const val TAG = "BillingManager"
        const val PREMIUM_SUBSCRIPTION_ID = "premium_monthly"

        // Business Mode Pro Subscriptions
        const val PRODUCT_ID_BUSINESS_MONTHLY = "business_pro_monthly"
        const val PRODUCT_ID_BUSINESS_6MONTH = "business_pro_6month"
        const val PRODUCT_ID_BUSINESS_YEARLY = "business_pro_yearly"

        private const val PREFS_NAME = "app_billing_prefs"
        private const val KEY_IS_BUSINESS_PRO = "is_business_pro_unlocked"
        private const val KEY_IS_PREMIUM = "is_premium_unlocked"
        private const val KEY_ACTIVE_SKU = "active_subscription_sku"
        private const val KEY_PURCHASE_TIME = "subscription_purchase_time"

        @Volatile
        private var INSTANCE: BillingManager? = null

        fun getInstance(context: Context): BillingManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BillingManager(context.applicationContext).also {
                    INSTANCE = it
                    it.startConnection()
                }
            }
        }

        fun openGooglePlaySubscriptions(context: Context, sku: String? = null) {
            val packageName = context.packageName
            val url = if (!sku.isNullOrEmpty()) {
                "https://play.google.com/store/account/subscriptions?sku=$sku&package=$packageName"
            } else {
                "https://play.google.com/store/account/subscriptions?package=$packageName"
            }
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback to general play store subscriptions url
                try {
                    val fallbackIntent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://play.google.com/store/account/subscriptions")
                    ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(fallbackIntent)
                } catch (_: Exception) {}
            }
        }

        fun openGooglePlayPaymentMethods(context: Context) {
            try {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://play.google.com/store/paymentmethods")
                ).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val fallbackIntent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://play.google.com/store/account")
                    ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(fallbackIntent)
                } catch (_: Exception) {}
            }
        }
    }

    private val prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isPremium = MutableStateFlow(prefs.getBoolean(KEY_IS_PREMIUM, false))
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    private val _isBusinessPro = MutableStateFlow(prefs.getBoolean(KEY_IS_BUSINESS_PRO, false))
    val isBusinessPro: StateFlow<Boolean> = _isBusinessPro.asStateFlow()

    private val _activeSku = MutableStateFlow(prefs.getString(KEY_ACTIVE_SKU, null))
    val activeSku: StateFlow<String?> = _activeSku.asStateFlow()

    private val _purchaseCelebrationEvents = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 5,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val purchaseCelebrationEvents: SharedFlow<String> = _purchaseCelebrationEvents.asSharedFlow()

    fun triggerCelebration(sku: String) {
        _purchaseCelebrationEvents.tryEmit(sku)
    }

    fun getActiveSku(): String? = prefs.getString(KEY_ACTIVE_SKU, null)
    fun getPurchaseTimeMs(): Long = prefs.getLong(KEY_PURCHASE_TIME, 0L)

    private val _businessMonthlyPrice = MutableStateFlow("₹299")
    val businessMonthlyPrice: StateFlow<String> = _businessMonthlyPrice.asStateFlow()

    private val _business6MonthPrice = MutableStateFlow("₹999")
    val business6MonthPrice: StateFlow<String> = _business6MonthPrice.asStateFlow()

    private val _businessYearlyPrice = MutableStateFlow("₹1,499")
    val businessYearlyPrice: StateFlow<String> = _businessYearlyPrice.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val productDetailsMap = mutableMapOf<String, ProductDetails>()

    private val billingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5

    private val billingClient: BillingClient by lazy {
        BillingClient.newBuilder(this.context)
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
                        reconnectAttempts = 0
                        Log.d(TAG, "Billing connected successfully")
                        queryAllProductDetails()
                        queryExistingPurchases()
                    } else {
                        Log.d(TAG, "Billing setup response (code ${result.responseCode}): ${result.debugMessage}")
                    }
                }

                override fun onBillingServiceDisconnected() {
                    _isConnected.value = false
                    if (reconnectAttempts < maxReconnectAttempts) {
                        reconnectAttempts++
                        Log.d(TAG, "Billing disconnected — retry $reconnectAttempts/$maxReconnectAttempts")
                        val delayMs = (reconnectAttempts * 3_000L).coerceAtMost(30_000L)
                        billingScope.launch {
                            delay(delayMs)
                            startConnection()
                        }
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
            billingScope.cancel()
            billingClient.endConnection()
        } catch (e: Exception) {
            Log.e(TAG, "endConnection failed: ${e.message}", e)
        }
    }

    // ── Query Product Details ───────────────────────────────────────────────

    private fun sanitizePrice(productId: String, raw: String?): String {
        if (raw.isNullOrBlank()) {
            return when (productId) {
                PRODUCT_ID_BUSINESS_MONTHLY -> "₹299"
                PRODUCT_ID_BUSINESS_6MONTH -> "₹999"
                PRODUCT_ID_BUSINESS_YEARLY -> "₹1,499"
                else -> "₹299"
            }
        }
        val clean = raw.trim()
        val withoutDecimals = clean.replace(Regex("""\.(00|0)$"""), "")
            .replace(Regex(""",(00|0)$"""), "")
            .replace(".00", "")

        return when (productId) {
            PRODUCT_ID_BUSINESS_MONTHLY -> {
                if (withoutDecimals.contains("300") || withoutDecimals.contains("299")) "₹299"
                else withoutDecimals
            }
            PRODUCT_ID_BUSINESS_6MONTH -> {
                if (withoutDecimals.contains("1000") || withoutDecimals.contains("1,000") || withoutDecimals.contains("999")) "₹999"
                else withoutDecimals
            }
            PRODUCT_ID_BUSINESS_YEARLY -> {
                if (withoutDecimals.contains("1500") || withoutDecimals.contains("1,500") || withoutDecimals.contains("1499") || withoutDecimals.contains("1,499")) "₹1,499"
                else withoutDecimals
            }
            else -> withoutDecimals
        }
    }

    fun queryAllProductDetails() {
        if (BuildConfig.IS_AMAZON || !_isConnected.value) return

        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PREMIUM_SUBSCRIPTION_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID_BUSINESS_MONTHLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID_BUSINESS_6MONTH)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID_BUSINESS_YEARLY)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, queryResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                queryResult.productDetailsList.forEach { details ->
                    productDetailsMap[details.productId] = details
                    val formattedPrice = details.subscriptionOfferDetails
                        ?.firstOrNull()?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice

                    val sanitizedPrice = sanitizePrice(details.productId, formattedPrice)
                    when (details.productId) {
                        PRODUCT_ID_BUSINESS_MONTHLY -> _businessMonthlyPrice.value = sanitizedPrice
                        PRODUCT_ID_BUSINESS_6MONTH -> _business6MonthPrice.value = sanitizedPrice
                        PRODUCT_ID_BUSINESS_YEARLY -> _businessYearlyPrice.value = sanitizedPrice
                    }
                }
                Log.d(TAG, "Product details cached: ${productDetailsMap.keys}")
            }
        }
    }

    suspend fun getSubscriptionDetails(): ProductDetails? {
        if (BuildConfig.IS_AMAZON || !_isConnected.value) return null

        productDetailsMap[PREMIUM_SUBSCRIPTION_ID]?.let { return it }

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
                        val product = productDetailsResult.productDetailsList.firstOrNull()
                        product?.let { productDetailsMap[it.productId] = it }
                        cont.resume(product)
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

    // ── Launch Billing Flows ────────────────────────────────────────────────

    fun launchBillingFlow(activity: Activity, productDetails: ProductDetails): Boolean {
        if (BuildConfig.IS_AMAZON) return false

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

    fun launchBusinessSubscription(activity: Activity, productId: String): Boolean {
        val details = productDetailsMap[productId]
        if (details != null) {
            return launchBillingFlow(activity, details)
        }

        // If not cached yet, query asynchronously and launch
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(params) { result, detailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val found = detailsList.productDetailsList.firstOrNull()
                if (found != null) {
                    productDetailsMap[found.productId] = found
                    activity.runOnUiThread {
                        launchBillingFlow(activity, found)
                    }
                }
            }
        }
        return true
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
                            applyPurchaseToState(purchase)
                        }
                    }
                } else {
                    applyPurchaseToState(purchase)
                }
            }
            Purchase.PurchaseState.PENDING -> {
                Log.i(TAG, "Purchase PENDING: ${purchase.purchaseToken.take(12)}…")
            }
            else -> {
                Log.w(TAG, "Unrecognised purchase state: ${purchase.purchaseState}")
            }
        }
    }

    private fun applyPurchaseToState(purchase: Purchase) {
        val products = purchase.products
        val purchaseTime = purchase.purchaseTime
        var matchedSku: String? = null

        if (products.contains(PREMIUM_SUBSCRIPTION_ID)) {
            _isPremium.value = true
            matchedSku = PREMIUM_SUBSCRIPTION_ID
            prefs.edit().putBoolean(KEY_IS_PREMIUM, true).apply()
        }
        if (products.contains(PRODUCT_ID_BUSINESS_MONTHLY) ||
            products.contains(PRODUCT_ID_BUSINESS_6MONTH) ||
            products.contains(PRODUCT_ID_BUSINESS_YEARLY)) {
            _isBusinessPro.value = true
            matchedSku = when {
                products.contains(PRODUCT_ID_BUSINESS_YEARLY) -> PRODUCT_ID_BUSINESS_YEARLY
                products.contains(PRODUCT_ID_BUSINESS_6MONTH) -> PRODUCT_ID_BUSINESS_6MONTH
                else -> PRODUCT_ID_BUSINESS_MONTHLY
            }
            prefs.edit().putBoolean(KEY_IS_BUSINESS_PRO, true).apply()
        }

        if (matchedSku != null) {
            _activeSku.value = matchedSku
            prefs.edit()
                .putString(KEY_ACTIVE_SKU, matchedSku)
                .putLong(KEY_PURCHASE_TIME, if (purchaseTime > 0) purchaseTime else System.currentTimeMillis())
                .apply()
            _purchaseCelebrationEvents.tryEmit(matchedSku)
        }
    }

    fun queryExistingPurchases(onComplete: ((Boolean) -> Unit)? = null) {
        try {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()

            billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    var hasPremium = false
                    var hasBusiness = false
                    var matchedSku: String? = null
                    var latestPurchaseTime = 0L

                    purchases.forEach { purchase ->
                        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            if (purchase.products.contains(PREMIUM_SUBSCRIPTION_ID)) {
                                hasPremium = true
                                matchedSku = PREMIUM_SUBSCRIPTION_ID
                                latestPurchaseTime = purchase.purchaseTime
                            }
                            if (purchase.products.contains(PRODUCT_ID_BUSINESS_MONTHLY) ||
                                purchase.products.contains(PRODUCT_ID_BUSINESS_6MONTH) ||
                                purchase.products.contains(PRODUCT_ID_BUSINESS_YEARLY)) {
                                hasBusiness = true
                                matchedSku = when {
                                    purchase.products.contains(PRODUCT_ID_BUSINESS_YEARLY) -> PRODUCT_ID_BUSINESS_YEARLY
                                    purchase.products.contains(PRODUCT_ID_BUSINESS_6MONTH) -> PRODUCT_ID_BUSINESS_6MONTH
                                    else -> PRODUCT_ID_BUSINESS_MONTHLY
                                }
                                latestPurchaseTime = purchase.purchaseTime
                            }
                        }
                    }

                    _isPremium.value = hasPremium
                    _isBusinessPro.value = hasBusiness
                    _activeSku.value = matchedSku

                    val editor = prefs.edit()
                        .putBoolean(KEY_IS_PREMIUM, hasPremium)
                        .putBoolean(KEY_IS_BUSINESS_PRO, hasBusiness)

                    if (matchedSku != null) {
                        editor.putString(KEY_ACTIVE_SKU, matchedSku)
                        if (latestPurchaseTime > 0) {
                            editor.putLong(KEY_PURCHASE_TIME, latestPurchaseTime)
                        }
                    } else if (!hasPremium && !hasBusiness) {
                        editor.remove(KEY_ACTIVE_SKU)
                    }
                    editor.apply()

                    Log.d(TAG, "Existing purchases checked — Premium: $hasPremium, Business Pro: $hasBusiness, SKU: $matchedSku")
                    onComplete?.invoke(hasPremium || hasBusiness)
                } else {
                    onComplete?.invoke(false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "queryExistingPurchases failed: ${e.message}", e)
            onComplete?.invoke(false)
        }
    }
}

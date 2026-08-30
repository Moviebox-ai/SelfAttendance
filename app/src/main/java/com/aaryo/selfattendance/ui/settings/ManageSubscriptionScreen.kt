package com.aaryo.selfattendance.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.aaryo.selfattendance.billing.BillingManager
import com.aaryo.selfattendance.billing.BusinessTrialManager
import com.aaryo.selfattendance.data.local.PreferencesManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSubscriptionScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val prefManager = remember { PreferencesManager(context) }
    val currentMode = prefManager.appMode
    val isEmployerMode = currentMode == PreferencesManager.MODE_EMPLOYER

    val trialManager = remember { BusinessTrialManager(context) }
    var trialSyncVersion by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        trialManager.syncWithServer()
        trialSyncVersion++
    }
    val isTrialActive = remember(trialSyncVersion) { trialManager.isTrialActive() }
    val remainingTrialDays = remember(trialSyncVersion) { trialManager.getRemainingDays() }
    val trialExpiryDateStr = remember(trialSyncVersion) { trialManager.getFormattedExpiryDate() }
    val trialStartDateStr = remember(trialSyncVersion) { trialManager.getFormattedStartDate() }

    val billingManager = remember { BillingManager.getInstance(context) }
    val isBusinessPro by billingManager.isBusinessPro.collectAsState()
    val isEmployeePremium by billingManager.isPremium.collectAsState()
    val activeSku by billingManager.activeSku.collectAsState()
    val monthlyPrice by billingManager.businessMonthlyPrice.collectAsState()
    val sixMonthPrice by billingManager.business6MonthPrice.collectAsState()
    val yearlyPrice by billingManager.businessYearlyPrice.collectAsState()

    var isRestoring by remember { mutableStateOf(false) }
    var selectedPlanToUpgrade by remember { mutableStateOf(BillingManager.PRODUCT_ID_BUSINESS_YEARLY) }

    // Calculate active subscription details & expiry date
    val purchaseTimeMs = remember(isBusinessPro, isEmployeePremium) { billingManager.getPurchaseTimeMs() }
    val formattedPurchaseDate = remember(purchaseTimeMs) {
        if (purchaseTimeMs > 0) {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(purchaseTimeMs))
        } else {
            "Active"
        }
    }
    val formattedNextBillingDate = remember(purchaseTimeMs, activeSku) {
        if (purchaseTimeMs > 0) {
            val cal = Calendar.getInstance().apply { timeInMillis = purchaseTimeMs }
            when (activeSku) {
                BillingManager.PRODUCT_ID_BUSINESS_YEARLY -> cal.add(Calendar.YEAR, 1)
                BillingManager.PRODUCT_ID_BUSINESS_6MONTH -> cal.add(Calendar.MONTH, 6)
                else -> cal.add(Calendar.MONTH, 1)
            }
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(cal.time)
        } else {
            "Auto-renews periodically"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Manage Subscription",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            isRestoring = true
                            billingManager.queryExistingPurchases { success ->
                                isRestoring = false
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (success) "✅ Subscription purchases successfully restored!"
                                        else "No new active subscriptions found on this Google account."
                                    )
                                }
                            }
                        }
                    ) {
                        if (isRestoring) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.Default.Sync, contentDescription = "Restore Purchases")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. Current Active Plan Hero Card ──────────────────────────────────────
            CurrentPlanHeroCard(
                isEmployerMode = isEmployerMode,
                isBusinessPro = isBusinessPro,
                isTrialActive = isTrialActive,
                remainingTrialDays = remainingTrialDays,
                trialExpiryDate = trialExpiryDateStr,
                trialStartDate = trialStartDateStr,
                activeSku = activeSku,
                nextBillingDate = formattedNextBillingDate,
                purchaseDate = formattedPurchaseDate,
                monthlyPrice = monthlyPrice,
                sixMonthPrice = sixMonthPrice,
                yearlyPrice = yearlyPrice,
                onShowCelebration = {
                    billingManager.triggerCelebration(activeSku ?: BillingManager.PRODUCT_ID_BUSINESS_YEARLY)
                }
            )

            // ── 2. Payment Method & Google Play Billing Hub ────────────────────────────
            PaymentMethodManagementCard(
                context = context,
                activeSku = activeSku,
                onUpdatePayment = {
                    BillingManager.openGooglePlayPaymentMethods(context)
                },
                onManageInPlayStore = {
                    BillingManager.openGooglePlaySubscriptions(context, activeSku)
                }
            )

            // ── 3. Upgrade / Change Subscription Plan (if in Trial or Free) ───────────
            if (!isBusinessPro) {
                UpgradeOrSwitchPlanCard(
                    selectedPlan = selectedPlanToUpgrade,
                    monthlyPrice = monthlyPrice,
                    sixMonthPrice = sixMonthPrice,
                    yearlyPrice = yearlyPrice,
                    onPlanSelect = { selectedPlanToUpgrade = it },
                    onSubscribe = {
                        activity?.let { act ->
                            billingManager.launchBusinessSubscription(act, selectedPlanToUpgrade)
                        } ?: run {
                            Toast.makeText(context, "Activity not available", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // ── 4. Included Subscription Benefits ────────────────────────────────────
            SubscriptionBenefitsCard(isEmployerMode = isEmployerMode)

            // ── 5. Subscription FAQs & Policies ───────────────────────────────────────
            SubscriptionFaqCard(context = context)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CurrentPlanHeroCard(
    isEmployerMode: Boolean,
    isBusinessPro: Boolean,
    isTrialActive: Boolean,
    remainingTrialDays: Int,
    trialExpiryDate: String,
    trialStartDate: String,
    activeSku: String?,
    nextBillingDate: String,
    purchaseDate: String,
    monthlyPrice: String,
    sixMonthPrice: String,
    yearlyPrice: String,
    onShowCelebration: (() -> Unit)? = null
) {
    val gradientColors = if (isBusinessPro) {
        listOf(Color(0xFF1565C0), Color(0xFF0D47A1))
    } else if (isTrialActive) {
        listOf(Color(0xFF2E7D32), Color(0xFF1B5E20))
    } else {
        listOf(Color(0xFF455A64), Color(0xFF263238))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradientColors))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status Badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isBusinessPro) Icons.Default.Verified else if (isTrialActive) Icons.Default.Timer else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (isBusinessPro || isTrialActive) Color(0xFFFFD54F) else Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isBusinessPro) "ACTIVE PRO" else if (isTrialActive) "7-DAY FREE TRIAL" else "TRIAL EXPIRED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 10.5.sp
                            )
                        }
                    }

                    Text(
                        text = if (isEmployerMode) "Business Mode" else "Self Attendance",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                }

                Column {
                    Text(
                        text = if (isBusinessPro) {
                            when (activeSku) {
                                BillingManager.PRODUCT_ID_BUSINESS_YEARLY -> "Business Pro (Annual Plan)"
                                BillingManager.PRODUCT_ID_BUSINESS_6MONTH -> "Business Pro (6 Months Plan)"
                                else -> "Business Pro (Monthly Plan)"
                            }
                        } else if (isTrialActive) {
                            "Business 7-Day Free Trial"
                        } else {
                            "Free Starter Tier"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )

                    Text(
                        text = if (isBusinessPro) {
                            when (activeSku) {
                                BillingManager.PRODUCT_ID_BUSINESS_YEARLY -> "$yearlyPrice / year"
                                BillingManager.PRODUCT_ID_BUSINESS_6MONTH -> "$sixMonthPrice / 6 months"
                                else -> "$monthlyPrice / month"
                            }
                        } else if (isTrialActive) {
                            "₹0 for 7 days (Unlimited access to all features)"
                        } else {
                            "Upgrade to unlock staff & payroll management"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.25f))

                // Date and Expiration Info Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isBusinessPro) {
                        Column {
                            Text(
                                text = "Next Renewal Date",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = nextBillingDate,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Auto-Renew",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "Enabled via Google Play",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFFD54F)
                            )
                        }
                    } else if (isTrialActive) {
                        Column {
                            Text(
                                text = "Trial Expiration Date",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = trialExpiryDate,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Remaining Time",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "$remainingTrialDays Days Left",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFD54F)
                            )
                        }
                    } else {
                        Column {
                            Text(
                                text = "Status",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "Trial Ended ($trialExpiryDate)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF8A80)
                            )
                        }
                    }
                }

                if (isBusinessPro && onShowCelebration != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.18f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onShowCelebration() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Celebration,
                                contentDescription = null,
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Play Pro Celebration Animation",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentMethodManagementCard(
    context: Context,
    activeSku: String?,
    onUpdatePayment: () -> Unit,
    onManageInPlayStore: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE8F0FE)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CreditCard,
                        contentDescription = null,
                        tint = Color(0xFF1A73E8),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Payment Method & Billing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Secured & processed by Google Play Store",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Google Play payment methods summary pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingBag,
                        contentDescription = null,
                        tint = Color(0xFF34A853),
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Google Play Billing",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Supports UPI (GPay/PhonePe/Paytm), Cards, Net Banking & Play Balance",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }

            // Actions row: Update Payment Method & Manage Subscriptions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onUpdatePayment,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Payment,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Update Payment",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onManageInPlayStore,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Manage in Play",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun UpgradeOrSwitchPlanCard(
    selectedPlan: String,
    monthlyPrice: String,
    sixMonthPrice: String,
    yearlyPrice: String,
    onPlanSelect: (String) -> Unit,
    onSubscribe: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFF3E0)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Stars,
                        contentDescription = null,
                        tint = Color(0xFFF57C00),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = "Subscribe to Business Pro",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Select a plan for uninterrupted staff management",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Plan Selector Cards
            PlanOptionCard(
                title = "Yearly Plan (12 Months)",
                price = "$yearlyPrice / year",
                subtitle = "Only ~₹125/month • Save 58% vs monthly",
                badge = "BEST VALUE - SAVE 58%",
                isSelected = selectedPlan == BillingManager.PRODUCT_ID_BUSINESS_YEARLY,
                onClick = { onPlanSelect(BillingManager.PRODUCT_ID_BUSINESS_YEARLY) }
            )

            PlanOptionCard(
                title = "6 Months Plan",
                price = "$sixMonthPrice / 6 months",
                subtitle = "Only ~₹166/month • Save 45% vs monthly",
                badge = "POPULAR - SAVE 45%",
                isSelected = selectedPlan == BillingManager.PRODUCT_ID_BUSINESS_6MONTH,
                onClick = { onPlanSelect(BillingManager.PRODUCT_ID_BUSINESS_6MONTH) }
            )

            PlanOptionCard(
                title = "1 Month Plan",
                price = "$monthlyPrice / month",
                subtitle = "Billed monthly • Cancel anytime in Google Play",
                badge = null,
                isSelected = selectedPlan == BillingManager.PRODUCT_ID_BUSINESS_MONTHLY,
                onClick = { onPlanSelect(BillingManager.PRODUCT_ID_BUSINESS_MONTHLY) }
            )

            Button(
                onClick = onSubscribe,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = when (selectedPlan) {
                            BillingManager.PRODUCT_ID_BUSINESS_YEARLY -> "Subscribe Yearly ($yearlyPrice)"
                            BillingManager.PRODUCT_ID_BUSINESS_6MONTH -> "Subscribe 6 Months ($sixMonthPrice)"
                            else -> "Subscribe Monthly ($monthlyPrice)"
                        },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanOptionCard(
    title: String,
    price: String,
    subtitle: String,
    badge: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            badge?.let {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFFF8F00),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 9.5.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }

                RadioButton(
                    selected = isSelected,
                    onClick = onClick
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = price,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SubscriptionBenefitsCard(isEmployerMode: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Included Pro Benefits",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            BenefitRowItem(
                icon = Icons.Default.Groups,
                title = "Unlimited Staff Members",
                desc = "Add, edit and organize all staff without any profile limits"
            )
            BenefitRowItem(
                icon = Icons.Default.AccessTime,
                title = "Daily Attendance & Overtime Tracker",
                desc = "Track Present, Absent, Half-Day & Overtime hours daily"
            )
            BenefitRowItem(
                icon = Icons.Default.AccountBalanceWallet,
                title = "Advance Khata & Salary Deductions",
                desc = "Keep precise records of salary advances, loans and payouts"
            )
            BenefitRowItem(
                icon = Icons.Default.PictureAsPdf,
                title = "1-Click PDF Salary Slips (WhatsApp)",
                desc = "Generate branded salary slips and share with employees instantly"
            )
            BenefitRowItem(
                icon = Icons.Default.CloudSync,
                title = "Multi-Device Real-Time Cloud Sync",
                desc = "Never lose employee records with secure automatic cloud backups"
            )
            BenefitRowItem(
                icon = Icons.Default.Block,
                title = "100% Ad-Free Business Operations",
                desc = "Clean, distraction-free environment for daily work"
            )
        }
    }
}

@Composable
private fun BenefitRowItem(
    icon: ImageVector,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFFE8F5E9)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(17.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun SubscriptionFaqCard(context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Subscription & Billing FAQs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            FaqItem(
                question = "How do I cancel or pause my subscription?",
                answer = "You can cancel or pause anytime directly in the Google Play Store under Account > Subscriptions. You will retain Pro access until the end of your current billing period."
            )

            FaqItem(
                question = "What happens to my staff data if my subscription ends?",
                answer = "All your staff attendance records, khata balances, and history remain 100% safe in your secure cloud backup. Nothing is deleted."
            )

            FaqItem(
                question = "Can I change my payment method (e.g., UPI / Card)?",
                answer = "Yes! Tap 'Update Payment' above to open Google Play's payment methods page where you can add or change your primary UPI ID, credit/debit card, or bank account."
            )
        }
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded }
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = question,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text = answer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                lineHeight = 16.sp
            )
        }
    }
}

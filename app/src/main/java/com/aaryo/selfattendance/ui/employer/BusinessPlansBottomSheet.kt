package com.aaryo.selfattendance.ui.employer

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import com.aaryo.selfattendance.billing.BillingManager
import com.aaryo.selfattendance.billing.BusinessTrialManager

enum class BusinessPlanSelection {
    MONTHLY,
    SIX_MONTHS,
    YEARLY
}

private val NavyDark = Color(0xFF0F172A)
private val EmeraldGreen = Color(0xFF059669)
private val AmberGold = Color(0xFFD97706)
private val RoyalBlue = Color(0xFF2563EB)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessPlansBottomSheet(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val billingManager = remember { BillingManager.getInstance(context) }
    val trialManager = remember { BusinessTrialManager(context) }

    val isBusinessPro by billingManager.isBusinessPro.collectAsState()
    val monthlyPrice by billingManager.businessMonthlyPrice.collectAsState()
    val sixMonthPrice by billingManager.business6MonthPrice.collectAsState()
    val yearlyPrice by billingManager.businessYearlyPrice.collectAsState()

    var selectedPlan by remember { mutableStateOf(BusinessPlanSelection.YEARLY) }
    var trialSyncVersion by remember { mutableStateOf(0) }
    val isTrialActive = remember(trialSyncVersion) { trialManager.isTrialActive() }
    val remainingDays = remember(trialSyncVersion) { trialManager.getRemainingDays() }

    LaunchedEffect(Unit) {
        trialManager.syncWithServer()
        trialSyncVersion++
    }

    // Auto-dismiss bottom sheet when subscription is unlocked so the celebration dialog is front and center
    LaunchedEffect(isBusinessPro) {
        if (isBusinessPro) {
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Executive Header ──────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFFF59E0B), Color(0xFFD97706))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Business Pro Suite",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isBusinessPro) "Active Enterprise Subscription"
                            else if (isTrialActive) "7-Day Free Trial ($remainingDays days remaining)"
                            else "Trial Expired • Upgrade to Continue",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isBusinessPro) EmeraldGreen else if (isTrialActive) AmberGold else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Trial / Value Hero Card ───────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isBusinessPro) EmeraldGreen.copy(alpha = 0.08f)
                else if (isTrialActive) RoyalBlue.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                border = BorderStroke(
                    1.dp,
                    if (isBusinessPro) EmeraldGreen.copy(alpha = 0.3f)
                    else if (isTrialActive) RoyalBlue.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isBusinessPro) Icons.Default.Verified
                        else if (isTrialActive) Icons.Default.AutoAwesome
                        else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isBusinessPro) EmeraldGreen else if (isTrialActive) RoyalBlue else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = if (isBusinessPro) {
                            "You have unlimited access to all Business Pro features with automated backup."
                        } else if (isTrialActive) {
                            "Your 7-day trial gives you full access. Subscribe today to lock in your business data."
                        } else {
                            "Your trial has ended. Subscribe now to unlock unlimited staff, attendance & salary slips."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ── Plan 1: YEARLY (Recommended) ──────────────────────────────────
            ModernPlanCard(
                title = "Yearly Plan (12 Months)",
                price = "$yearlyPrice / year",
                pricePerMonth = "Only ~₹125 / month",
                subtitle = "Billed annually • Save 58% discount",
                badge = "MOST POPULAR • SAVE 58%",
                badgeColor = Color(0xFFF59E0B),
                isSelected = selectedPlan == BusinessPlanSelection.YEARLY,
                onClick = { selectedPlan = BusinessPlanSelection.YEARLY }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // ── Plan 2: 6 MONTHS ──────────────────────────────────────────────
            ModernPlanCard(
                title = "6 Months Plan",
                price = "$sixMonthPrice / 6 mo",
                pricePerMonth = "Only ~₹166 / month",
                subtitle = "Billed half-yearly • Save 45% discount",
                badge = "POPULAR • SAVE 45%",
                badgeColor = EmeraldGreen,
                isSelected = selectedPlan == BusinessPlanSelection.SIX_MONTHS,
                onClick = { selectedPlan = BusinessPlanSelection.SIX_MONTHS }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // ── Plan 3: MONTHLY ───────────────────────────────────────────────
            ModernPlanCard(
                title = "1 Month Plan",
                price = "$monthlyPrice / month",
                pricePerMonth = "Flexible recurring billing",
                subtitle = "Pay monthly • Cancel anytime on Google Play",
                badge = "STANDARD",
                badgeColor = RoyalBlue,
                isSelected = selectedPlan == BusinessPlanSelection.MONTHLY,
                onClick = { selectedPlan = BusinessPlanSelection.MONTHLY }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Features Breakdown Card ───────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Everything Included in Business Pro:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    BusinessFeatureRow(
                        icon = Icons.Default.Groups,
                        title = "Unlimited Staff & Employee Profiles",
                        subtitle = "No limits on staff count or worker categories"
                    )
                    BusinessFeatureRow(
                        icon = Icons.Default.HowToReg,
                        title = "Daily Attendance & Overtime Tracker",
                        subtitle = "Present, Half-Day, Absent, Leave & Overtime calculation"
                    )
                    BusinessFeatureRow(
                        icon = Icons.Default.MonetizationOn,
                        title = "Advance Salary Khata & Payment Ledger",
                        subtitle = "Track staff borrowings, loans & auto-deductions"
                    )
                    BusinessFeatureRow(
                        icon = Icons.Default.PictureAsPdf,
                        title = "1-Click PDF Salary Slips & WhatsApp Share",
                        subtitle = "Generate professional payslips with company branding"
                    )
                    BusinessFeatureRow(
                        icon = Icons.Default.CloudSync,
                        title = "Automatic Cloud Backup & Sync",
                        subtitle = "Zero data loss even when switching or resetting phone"
                    )
                    BusinessFeatureRow(
                        icon = Icons.Default.Block,
                        title = "100% Ad-Free Clean Experience",
                        subtitle = "Blazing fast workflow without interruptions"
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // ── Subscribe CTA Button ──────────────────────────────────────────
            Button(
                onClick = {
                    if (activity != null) {
                        val productId = when (selectedPlan) {
                            BusinessPlanSelection.MONTHLY -> BillingManager.PRODUCT_ID_BUSINESS_MONTHLY
                            BusinessPlanSelection.SIX_MONTHS -> BillingManager.PRODUCT_ID_BUSINESS_6MONTH
                            BusinessPlanSelection.YEARLY -> BillingManager.PRODUCT_ID_BUSINESS_YEARLY
                        }
                        billingManager.launchBusinessSubscription(activity, productId)
                    } else {
                        Toast.makeText(context, "Opening Google Play checkout...", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoyalBlue,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = when (selectedPlan) {
                            BusinessPlanSelection.MONTHLY -> "Subscribe Monthly ($monthlyPrice / mo)"
                            BusinessPlanSelection.SIX_MONTHS -> "Subscribe 6 Months ($sixMonthPrice / 6 mo)"
                            BusinessPlanSelection.YEARLY -> "Subscribe Yearly ($yearlyPrice / yr)"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Trust info & Restore purchases
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Secured by Google Play Billing • Cancel Anytime",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            TextButton(
                onClick = {
                    billingManager.queryExistingPurchases()
                    Toast.makeText(context, "Checking subscription status...", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text(
                    text = "Already Subscribed? Restore Purchases",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ModernPlanCard(
    title: String,
    price: String,
    pricePerMonth: String,
    subtitle: String,
    badge: String?,
    badgeColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) RoyalBlue else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        shadowElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (badge != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor,
                        contentColor = Color.White
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                // Radio Indicator Circle
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) RoyalBlue else Color.Transparent)
                        .border(
                            2.dp,
                            if (isSelected) RoyalBlue else MaterialTheme.colorScheme.outline,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = price,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSelected) RoyalBlue else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = pricePerMonth,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = EmeraldGreen
                    )
                }
            }
        }
    }
}

@Composable
private fun BusinessFeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(EmeraldGreen.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = EmeraldGreen,
                modifier = Modifier.size(16.dp)
            )
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

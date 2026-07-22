package com.aaryo.selfattendance.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.aaryo.selfattendance.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.aaryo.selfattendance.ads.AdsController
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.aaryo.selfattendance.utils.CurrencyManager
import com.aaryo.selfattendance.data.repository.ReferralRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.SetOptions
import com.aaryo.selfattendance.data.repository.RewardRepository
import com.aaryo.selfattendance.ui.navigation.Routes
import com.aaryo.selfattendance.ui.rewards.CoinFlipDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.YearMonth
import java.util.Calendar

@Composable
fun DashboardScreen(navController: NavController) {

    val viewModel: DashboardViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    val context  = LocalContext.current

    // Refresh on return from EditProfile
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(navBackStackEntry) {
        viewModel.refresh()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val animatedSalary by animateFloatAsState(
        targetValue = state.salary.toFloat(),
        animationSpec = tween(800),
        label = "salaryAnimation"
    )

    val greeting = remember { getGreeting(context) }

    // ── Daily Login Coins ─────────────────────────────────────────────────────
    val prefs = remember { PreferencesManager(context) }
    val currencySymbol = remember { CurrencyManager.getSymbol(prefs.selectedCurrency) }
    var dailyCoinsEarned by remember { mutableIntStateOf(0) }
    var showDailyLoginDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // BUG FIX (Coins Reset on Reinstall):
        // syncFromFirebase() ko PEHLE await karo coins award karne se pehle.
        // Yeh safety net hai — primary fix AuthViewModel.checkUserProfile() mein hai.
        // Lekin agar koi edge case ho (e.g. network delay, race condition) toh
        // yeh ensure karta hai ki prefs.coinBalance correct Firebase value se
        // populate ho jaaye BEFORE we do (prefs.coinBalance + new_coins).
        // Bina is fix ke: prefs=0 (fresh install) → 0+coins → Firestore pe write
        // → user ka purana balance permanently destroy ho jaata tha.
        runCatching {
            RewardRepository.syncFromFirebase(prefs)
        }

        val today = LocalDate.now().toString()
        if (prefs.lastDailyLoginDate != today) {
            val coins = (1..50).random()
            prefs.lastDailyLoginDate    = today
            prefs.coinBalance           = prefs.coinBalance + coins
            prefs.totalCoinsEarned      = prefs.totalCoinsEarned + coins
            dailyCoinsEarned            = coins
            showDailyLoginDialog        = true
            // Sync new balance to Firestore in the background.
            // BUG FIX (race condition): coinBalance/totalCoinsEarned now use
            // FieldValue.increment(coins) — an atomic server-side delta — instead
            // of writing the absolute local value. Previously, if the same
            // account was open on two devices, whichever write landed second
            // would overwrite the first and silently lose coins.
            scope.launch {
                try {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                    FirebaseFirestore.getInstance()
                        .collection("users").document(uid)
                        .set(mapOf("rewards" to mapOf(
                            "coinBalance"          to FieldValue.increment(coins.toLong()),
                            "totalCoinsEarned"     to FieldValue.increment(coins.toLong()),
                            "lastDailyLoginDate"   to prefs.lastDailyLoginDate,
                            "updatedAt"            to System.currentTimeMillis()
                        )), SetOptions.merge()).await()
                } catch (e: Exception) {
                    FirebaseCrashlytics.getInstance().recordException(e)
                }
            }
        }
        // Register this user's referral code (idempotent; safe to call every day)
        scope.launch {
            try { ReferralRepository.registerReferralCode() } catch (e: Exception) { FirebaseCrashlytics.getInstance().recordException(e) }
        }
        // Track referral daily visit if this user was referred by someone
        if (prefs.referredByUid.isNotBlank()) {
            if (prefs.lastReferralVisitDate != today) {
                prefs.lastReferralVisitDate = today
                scope.launch {
                    try { ReferralRepository.recordDailyVisit() } catch (e: Exception) { FirebaseCrashlytics.getInstance().recordException(e) }
                }
            }
        }
    }

    val totalDays = state.present + state.half + state.absent

    // BUG FIX: Previously `progress = state.present / totalDays` which treated
    // half-days as absent (0 contribution). A user with 10 present + 10 half + 10
    // absent would show 33% progress instead of the correct ~58% (10 + 5 out of 26
    // effective days). Half days should count as 0.5 — consistent with SalaryCalculator.
    val progress = remember(totalDays, state.present, state.half) {
        if (totalDays == 0) 0f
        else (state.present + state.half * 0.5f) / totalDays
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {

            // HEADER
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Column {

                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )

                    Spacer(Modifier.height(4.dp))

                    val name = state.profile.name.uppercase()

                    Text(
                        text = if (name.isBlank()) "USER" else name,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "${state.selectedMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${state.selectedMonth.year}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {

                    val initial =
                        state.profile.name
                            .takeIf { it.isNotBlank() }
                            ?.first()
                            ?.uppercase() ?: "U"

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {

                        Text(
                            text = initial.toString(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    IconButton(
                        onClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar("Opening profile editor")
                            }
                            navController.navigate(Routes.EDIT_PROFILE)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit Profile",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // SALARY CARD
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.dashboard_estimated_salary),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "$currencySymbol ${animatedSalary.formatMoney()}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = "Salary",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // PROGRESS
            Text(
                text = stringResource(R.string.dashboard_attendance_progress),
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )

            Spacer(Modifier.height(24.dp))

            // STATS
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatCard(
                        title = stringResource(R.string.dashboard_present),
                        value = state.present.toString(),
                        accent = Color(0xFF00C853),
                        icon = Icons.Default.CheckCircle,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = stringResource(R.string.dashboard_half_day),
                        value = state.half.toString(),
                        accent = Color(0xFFFFB300),
                        icon = Icons.Default.Schedule,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatCard(
                        title = stringResource(R.string.dashboard_absent),
                        value = state.absent.toString(),
                        accent = Color(0xFFE53935),
                        icon = Icons.Default.Cancel,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = stringResource(R.string.dashboard_overtime),
                        value = state.overtime.formatMoney(),
                        accent = Color(0xFF1565C0),
                        icon = Icons.Default.TrendingUp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.dashboard_sponsored),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )

            Spacer(Modifier.height(6.dp))

            AdsController.FreshNativeAdView()

            Spacer(Modifier.height(16.dp))
        }
    }

    // ── Daily Login Coin Dialog ───────────────────────────────────────────────
    if (showDailyLoginDialog && dailyCoinsEarned > 0) {
        CoinFlipDialog(
            coinsWon = dailyCoinsEarned,
            label    = "Daily login bonus — kal bhi aao!",
            onClaim  = { showDailyLoginDialog = false }
        )
    }
}

// GREETING — plain fun, uses context.getString (safe outside @Composable)
fun getGreeting(context: android.content.Context): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> context.getString(R.string.dashboard_greeting_morning)
        hour < 17 -> context.getString(R.string.dashboard_greeting_afternoon)
        else      -> context.getString(R.string.dashboard_greeting_evening)
    }
}

// STAT CARD — with icon + accent color circle background
@Composable
fun StatCard(
    title: String,
    value: String,
    accent: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {

        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Text(
                    text = stringResource(R.string.dashboard_this_month),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                )
            }
        }
    }
}

// FORMAT
fun Double.formatMoney(): String {
    if (this.isNaN() || this.isInfinite()) return "0.00"

    val isNegative = this < 0
    val abs = Math.abs(this)

    val intPart = abs.toLong()
    val decPart = Math.round((abs - intPart) * 100)

    val intStr = intPart.toString()
    val grouped = applyIndianGrouping(intStr)

    val decStr = decPart.toString().padStart(2, '0')

    return "${if (isNegative) "-" else ""}$grouped.$decStr"
}

fun Float.formatMoney(): String = this.toDouble().formatMoney()

private fun applyIndianGrouping(digits: String): String {
    if (digits.length <= 3) return digits

    val last3 = digits.takeLast(3)
    val rest = digits.dropLast(3)

    val grouped = StringBuilder()
    var i = rest.length

    while (i > 0) {
        val start = maxOf(0, i - 2)
        if (grouped.isNotEmpty()) grouped.insert(0, ",")
        grouped.insert(0, rest.substring(start, i))
        i = start
    }

    return "$grouped,$last3"
}

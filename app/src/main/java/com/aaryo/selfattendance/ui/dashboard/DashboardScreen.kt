package com.aaryo.selfattendance.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.aaryo.selfattendance.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.aaryo.selfattendance.ads.AdsController
import com.aaryo.selfattendance.data.remote.RemoteConfigManager
import com.aaryo.selfattendance.ui.navigation.Routes
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(navController: NavController) {

    val viewModel: DashboardViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    // ── Hidden admin trigger: 5 taps within 3s on the avatar circle ──
    var adminTapCount      by remember { mutableIntStateOf(0) }
    var adminFirstTapAt    by remember { mutableLongStateOf(0L) }
    val adminTriggerWindow = 3_000L   // ms
    val adminTapsRequired  = 5

    // Refresh on return from EditProfile
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(navBackStackEntry) {
        viewModel.refresh()
    }

    val remoteConfig = remember { RemoteConfigManager.getInstance() }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val animatedSalary by animateFloatAsState(
        targetValue = state.salary.toFloat(),
        animationSpec = tween(800),
        label = "salaryAnimation"
    )

    val context = androidx.compose.ui.platform.LocalContext.current
    val greeting = remember { getGreeting(context) }

    val totalDays = state.present + state.half + state.absent

    val progress = remember(totalDays, state.present) {
        if (totalDays == 0) 0f
        else state.present.toFloat() / totalDays
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (remoteConfig.showBannerAd()) {
                AdsController.BannerAd()
            }
        },
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
                            )
                            // ── Hidden admin trigger: 5 taps OR long-press ──
                            .combinedClickable(
                                onClick = {
                                    val now = System.currentTimeMillis()
                                    if (now - adminFirstTapAt > adminTriggerWindow) {
                                        adminTapCount   = 1
                                        adminFirstTapAt = now
                                    } else {
                                        adminTapCount++
                                    }
                                    if (adminTapCount >= adminTapsRequired) {
                                        adminTapCount   = 0
                                        adminFirstTapAt = 0L
                                        navController.navigate(Routes.ADMIN_PIN)
                                    }
                                },
                                onLongClick = {
                                    adminTapCount   = 0
                                    adminFirstTapAt = 0L
                                    navController.navigate(Routes.ADMIN_PIN)
                                }
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

                Column(modifier = Modifier.padding(24.dp)) {

                    Text(
                        text = stringResource(R.string.dashboard_estimated_salary),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "₹ ${animatedSalary.formatMoney()}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
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
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(24.dp))

            // STATS
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {

                    StatCard(stringResource(R.string.dashboard_present),  state.present.toString(),   Color(0xFF00C853), Modifier.weight(1f))  // EmeraldGreen
                    StatCard(stringResource(R.string.dashboard_half_day), state.half.toString(),       Color(0xFFFFB300), Modifier.weight(1f))  // CoinGold
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {

                    StatCard(stringResource(R.string.dashboard_absent),   state.absent.toString(),     Color(0xFFE53935), Modifier.weight(1f))  // CoralRed
                    StatCard(stringResource(R.string.dashboard_overtime), state.overtime.formatMoney(),Color(0xFF1565C0), Modifier.weight(1f))  // SapphireBlue
                }
            }

            Spacer(Modifier.height(28.dp))

            if (remoteConfig.showNativeAd()) {

                Text(
                    text = stringResource(R.string.dashboard_sponsored),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )

                Spacer(Modifier.height(6.dp))

                AdsController.NativeAdView()
            }

            Spacer(Modifier.height(16.dp))
        }
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

// STAT CARD
@Composable
fun StatCard(
    title: String,
    value: String,
    accent: Color,
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

        Column(modifier = Modifier.padding(18.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent)
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    text = title,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
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
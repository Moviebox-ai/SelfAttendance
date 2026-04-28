package com.aaryo.selfattendance.ui.wallet

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aaryo.selfattendance.ads.AdsController
import com.aaryo.selfattendance.coin.CoinTransaction
import com.aaryo.selfattendance.coin.DailyRewardManager
import com.aaryo.selfattendance.coin.LeaderboardEntry
import com.aaryo.selfattendance.coin.PrizeTier
import com.aaryo.selfattendance.coin.TransactionType
import com.aaryo.selfattendance.coin.WeeklyPrizeManager
import com.aaryo.selfattendance.data.remote.RemoteConfigManager
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

// ─── Palette ─────────────────────────────────────────────────────────────────
private val Gold          = Color(0xFFFFB300)
private val GoldLight     = Color(0xFFFFE082)
private val GoldDark      = Color(0xFFE65100)
private val Emerald       = Color(0xFF00C853)
private val CoralRed      = Color(0xFFE53935)
private val PrizeBlue     = Color(0xFF0D47A1)
private val PrizePurple   = Color(0xFF6A1B9A)
private val PrizeCyan     = Color(0xFF00BCD4)
private val GlassBorder   = Color.White.copy(alpha = 0.22f)
private val DeepDark      = Color(0xFF080818)

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun WalletScreen() {
    val context      = LocalContext.current
    val activity     = context as? Activity
    val viewModel: WalletViewModel = viewModel(factory = WalletViewModelFactory(context))
    val uiState      by viewModel.uiState.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }
    val remoteConfig = remember { RemoteConfigManager.getInstance() }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            snackbarHost.showSnackbar(msg, duration = SnackbarDuration.Short)
            viewModel.clearToast()
        }
    }

    // Reward popup
    uiState.rewardEarned?.let { coins ->
        RewardEarnedDialog(coins = coins, onDismiss = { viewModel.clearReward() })
    }

    // Daily reward dialog
    if (uiState.showDailyRewardDialog) {
        uiState.dailyRewardResult?.let { result ->
            DailyRewardDialog(result = result, onDismiss = { viewModel.dismissDailyRewardDialog() })
        }
    }

    // Grand Prize info dialog
    if (uiState.showGrandPrizeDialog) {
        GrandPrizeInfoDialog(onDismiss = { viewModel.dismissGrandPrizeDialog() })
    }

    Scaffold(
        topBar        = { WalletTopBar() },
        bottomBar     = { if (remoteConfig.showBannerAd()) AdsController.BannerAd() },
        snackbarHost  = { SnackbarHost(snackbarHost) }
    ) { padding ->

        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator(color = Gold)
            }
            return@Scaffold
        }

        if (!uiState.isWalletEnabled) {
            WalletDisabledPlaceholder(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding      = PaddingValues(bottom = 32.dp)
        ) {
            // Balance card
            item { BalanceGlassCard(uiState.balance, uiState.currentStreak, uiState.longestStreak) }

            // Daily progress
            item { DailyProgressCard(uiState.adCoinsEarnedToday, uiState.dailyLimit) }

            // Earn coins
            if (uiState.isAdRewardEnabled) {
                item {
                    EarnCoinsSection(
                        canEarnNow      = uiState.canEarnNow,
                        isLoading       = uiState.isAdLoading,
                        remainingToday  = uiState.dailyLimit - uiState.adCoinsEarnedToday,
                        cooldownSeconds = uiState.adCooldownSeconds,
                        onEarnClicked   = { activity?.let { viewModel.onEarnCoinsClicked(it) } }
                    )
                }
            }

            // ── BIG PRIZE SECTION ──────────────────────────────────────────
            item {
                WeeklyGrandPrizeBanner(
                    daysRemaining  = uiState.daysRemainingInWeek,
                    userWeekCoins  = uiState.weeklyCoins,
                    currentTier    = uiState.currentUserEntry?.tier
                        ?: WeeklyPrizeManager.PRIZE_TIERS.last(),
                    onInfoClick    = { viewModel.showGrandPrizeInfo() }
                )
            }

            item {
                LeaderboardSection(
                    entries     = uiState.leaderboard,
                    isLoading   = uiState.isLeaderboardLoading,
                    userEntry   = uiState.currentUserEntry
                )
            }

            // Transaction history
            if (uiState.transactions.isNotEmpty()) {
                item {
                    Text(
                        "📋 Coin History",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier   = Modifier.padding(horizontal = 16.dp)
                    )
                }
                items(uiState.transactions) { tx -> TransactionItem(tx) }
            }
        }
    }
}

// ─── Top Bar ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletTopBar() {
    TopAppBar(
        title = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("💰", fontSize = 22.sp)
                Text("Wallet", fontWeight = FontWeight.Bold)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor    = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

// ─── Balance Glass Card ───────────────────────────────────────────────────────

@Composable
private fun BalanceGlassCard(balance: Int, currentStreak: Int, longestStreak: Int) {
    val animatedBalance by animateIntAsState(
        targetValue   = balance,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label         = "balanceAnim"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue  = 0.6f,
        targetValue   = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(16.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1A237E), Color(0xFF6A1B9A), Color(0xFF4A148C))
                )
            )
            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier.fillMaxWidth()
        ) {
            Text(
                "Total Balance",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("🪙", fontSize = 36.sp, color = Gold.copy(alpha = glowAlpha))
                Spacer(Modifier.width(8.dp))
                Text(
                    "$animatedBalance",
                    fontSize   = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = Color.White
                )
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = GlassBorder, thickness = 1.dp)
            Spacer(Modifier.height(16.dp))
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StreakStat("🔥 Streak", "${currentStreak}d")
                StreakStat("🏆 Best",   "${longestStreak}d")
            }
        }
    }
}

@Composable
private fun StreakStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Text(value, color = GoldLight, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

// ─── Daily Progress Card ──────────────────────────────────────────────────────

@Composable
private fun DailyProgressCard(earnedToday: Int, maxDaily: Int) {
    val progress by animateFloatAsState(
        targetValue   = if (maxDaily > 0) earnedToday.toFloat() / maxDaily else 0f,
        animationSpec = tween(600),
        label         = "progressAnim"
    )
    val remaining = maxDaily - earnedToday

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier            = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("Daily Earnings", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Text(
                    "$earnedToday / $maxDaily coins",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinearProgressIndicator(
                progress      = { progress },
                modifier      = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)),
                color         = if (progress >= 1f) CoralRed else Emerald,
                trackColor    = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
            )
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (remaining > 0) "🎯 $remaining coins remaining today" else "✅ Daily limit reached!",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (remaining > 0) MaterialTheme.colorScheme.primary else CoralRed
                )
                Text(
                    "${(progress * 100).toInt()}%",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color      = if (progress >= 1f) CoralRed else Emerald
                )
            }
        }
    }
}

// ─── Earn Coins Section ───────────────────────────────────────────────────────

@Composable
private fun EarnCoinsSection(
    canEarnNow     : Boolean,
    isLoading      : Boolean,
    remainingToday : Int,
    cooldownSeconds: Long,
    onEarnClicked  : () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "btnPulse")
    val btnScale by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = if (canEarnNow) 1.04f else 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "btnScale"
    )

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier              = Modifier.padding(20.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
            verticalArrangement   = Arrangement.spacedBy(12.dp)
        ) {
            Text("Earn Coins", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)

            val chipText = when {
                isLoading           -> "Ad loading…"
                cooldownSeconds > 0 -> "⏳ ${cooldownSeconds}s cooldown"
                remainingToday <= 0 -> "Daily limit reached"
                else                -> "🎬 Watch Ad · +10 coins"
            }
            val chipColor = when {
                isLoading           -> MaterialTheme.colorScheme.outline
                cooldownSeconds > 0 -> Color(0xFFF57F17)
                remainingToday <= 0 -> CoralRed
                else                -> Emerald
            }

            Surface(shape = RoundedCornerShape(50), color = chipColor.copy(alpha = 0.15f)) {
                Text(
                    chipText,
                    modifier   = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    color      = chipColor,
                    style      = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }

            Button(
                onClick  = onEarnClicked,
                enabled  = canEarnNow && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .graphicsLayer { scaleX = btnScale; scaleY = btnScale },
                shape  = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor         = Gold,
                    contentColor           = Color.Black,
                    disabledContainerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    disabledContentColor   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.Black, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (remainingToday > 0) "Watch Ad · Earn Coins" else "Come Back Tomorrow",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (!isLoading && remainingToday > 0 && cooldownSeconds == 0L) {
                Text(
                    "You can earn up to $remainingToday more coins today",
                    style     = MaterialTheme.typography.labelSmall,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  WEEKLY GRAND PRIZE BANNER — Full animated hero card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun WeeklyGrandPrizeBanner(
    daysRemaining: Int,
    userWeekCoins: Int,
    currentTier  : PrizeTier,
    onInfoClick  : () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "prizeBanner")

    // Rotating halo
    val haloAngle by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label         = "haloAngle"
    )
    // Floating trophy
    val trophyFloat by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 10f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "trophyFloat"
    )
    // Sparkle pulse
    val sparklePulse by infiniteTransition.animateFloat(
        initialValue  = 0.5f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label         = "sparklePulse"
    )
    // Text shimmer
    val shimmerX by infiniteTransition.animateFloat(
        initialValue  = -300f,
        targetValue   = 600f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label         = "shimmerX"
    )

    val nextTierCoins = WeeklyPrizeManager.PRIZE_TIERS
        .lastOrNull { it.minCoins > userWeekCoins }?.minCoins ?: currentTier.minCoins
    val tierProgress = if (nextTierCoins > 0 && nextTierCoins != currentTier.minCoins)
        (userWeekCoins.toFloat() / nextTierCoins).coerceIn(0f, 1f)
    else 1f

    val animatedProgress by animateFloatAsState(
        targetValue   = tierProgress,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label         = "tierProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Outer glow shadow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .shadow(
                    elevation         = 24.dp,
                    shape             = RoundedCornerShape(28.dp),
                    ambientColor      = Gold.copy(alpha = 0.4f),
                    spotColor         = Gold.copy(alpha = 0.6f)
                )
        )

        // Main card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1A1060),
                            Color(0xFF0D0830),
                            DeepDark
                        ),
                        radius = 900f
                    )
                )
                .border(
                    width  = 1.5.dp,
                    brush  = Brush.linearGradient(listOf(Gold.copy(0.8f), PrizePurple.copy(0.4f), PrizeCyan.copy(0.3f))),
                    shape  = RoundedCornerShape(28.dp)
                )
                .padding(20.dp)
        ) {

            // Canvas: orbiting particles + sparkles
            Canvas(
                modifier = Modifier.fillMaxWidth().height(320.dp)
            ) {
                drawPrizeParticles(haloAngle, sparklePulse)
            }

            Column(
                modifier            = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // Header row
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "WEEKLY",
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = Gold.copy(alpha = 0.8f),
                            letterSpacing = 4.sp
                        )
                        // Shimmer "BIG PRIZE" text
                        Box {
                            Text(
                                "BIG PRIZE",
                                fontSize   = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color      = Color.White.copy(alpha = 0.15f)
                            )
                            Text(
                                "BIG PRIZE",
                                fontSize   = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                style      = LocalTextStyle.current.copy(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color.White, Gold, GoldLight, Color.White),
                                        start  = Offset(shimmerX - 200, 0f),
                                        end    = Offset(shimmerX + 100, 60f)
                                    )
                                )
                            )
                        }
                    }

                    // Info button
                    IconButton(
                        onClick  = onInfoClick,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = "Prize Info",
                            tint               = Color.White.copy(alpha = 0.7f),
                            modifier           = Modifier.size(18.dp)
                        )
                    }
                }

                // Trophy with floating animation
                Box(
                    modifier         = Modifier.size(90.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Halo ring
                    Canvas(Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2, size.height / 2)
                        val r      = size.minDimension / 2 - 4.dp.toPx()
                        drawCircle(
                            brush  = Brush.sweepGradient(
                                listOf(Gold.copy(0.9f), GoldLight.copy(0.3f), Gold.copy(0.9f)),
                                center = center
                            ),
                            radius = r,
                            style  = Stroke(width = 2.5.dp.toPx())
                        )
                        // Rotating dot on ring
                        val rad = Math.toRadians(haloAngle.toDouble())
                        drawCircle(
                            color  = Gold,
                            radius = 5.dp.toPx(),
                            center = Offset(
                                center.x + r * cos(rad).toFloat(),
                                center.y + r * sin(rad).toFloat()
                            )
                        )
                    }
                    Text(
                        "🏆",
                        fontSize = 48.sp,
                        modifier = Modifier.offset(y = (-trophyFloat).dp)
                    )
                }

                // Countdown
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier              = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Filled.Timer,
                            contentDescription = null,
                            tint     = PrizeCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "Resets in  $daysRemaining  ${if (daysRemaining == 1) "day" else "days"}",
                            color      = Color.White,
                            style      = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Current tier badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.07f),
                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier            = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "YOUR RANK THIS WEEK",
                            fontSize      = 9.sp,
                            fontWeight    = FontWeight.ExtraBold,
                            color         = Gold.copy(alpha = 0.7f),
                            letterSpacing = 3.sp
                        )
                        Text(
                            currentTier.label,
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = Gold
                        )
                        Text(
                            "🪙 $userWeekCoins weekly coins",
                            style  = MaterialTheme.typography.bodySmall,
                            color  = Color.White.copy(alpha = 0.75f)
                        )

                        // Progress bar to next tier
                        Spacer(Modifier.height(4.dp))
                        val coinsToNext = WeeklyPrizeManager.PRIZE_TIERS
                            .lastOrNull { it.minCoins > userWeekCoins }
                        if (coinsToNext != null) {
                            Text(
                                "${coinsToNext.minCoins - userWeekCoins} coins to ${coinsToNext.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = PrizeCyan.copy(alpha = 0.9f)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.1f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedProgress)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(Gold, GoldLight, PrizeCyan)
                                        )
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Canvas drawing helper ────────────────────────────────────────────────────

private fun DrawScope.drawPrizeParticles(rotationAngle: Float, sparklePulse: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f

    // Outer orbit dots
    val orbitParticles = 12
    repeat(orbitParticles) { i ->
        val angle  = Math.toRadians((i * 360.0 / orbitParticles) + rotationAngle.toDouble())
        val radius = size.minDimension * 0.45f
        val x      = cx + radius * cos(angle).toFloat()
        val y      = cy + radius * sin(angle).toFloat()
        val alpha  = if (i % 3 == 0) sparklePulse else 0.3f
        val ptSize = if (i % 4 == 0) 4.dp.toPx() else 2.5.dp.toPx()
        drawCircle(color = Gold.copy(alpha = alpha), radius = ptSize, center = Offset(x, y))
    }

    // Inner sparkle ring (counter-rotating)
    val innerParticles = 8
    repeat(innerParticles) { i ->
        val angle  = Math.toRadians((i * 360.0 / innerParticles) - rotationAngle.toDouble() * 0.6)
        val radius = size.minDimension * 0.28f
        val x      = cx + radius * cos(angle).toFloat()
        val y      = cy + radius * sin(angle).toFloat()
        val alpha  = if (i % 2 == 0) sparklePulse * 0.8f else 0.2f
        drawCircle(color = PrizeCyan.copy(alpha = alpha), radius = 2.dp.toPx(), center = Offset(x, y))
    }

    // Corner star sparkles
    val corners = listOf(
        Offset(size.width * 0.1f, size.height * 0.1f),
        Offset(size.width * 0.9f, size.height * 0.1f),
        Offset(size.width * 0.15f, size.height * 0.88f),
        Offset(size.width * 0.85f, size.height * 0.88f),
    )
    corners.forEachIndexed { i, pt ->
        val pulse = if (i % 2 == 0) sparklePulse else 1f - sparklePulse
        drawCircle(color = GoldLight.copy(alpha = pulse * 0.6f), radius = 3.dp.toPx(), center = pt)
    }
}

// ─── Leaderboard Section ─────────────────────────────────────────────────────

@Composable
fun LeaderboardSection(
    entries  : List<LeaderboardEntry>,
    isLoading: Boolean,
    userEntry: LeaderboardEntry?
) {
    Column(
        modifier            = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                "🏅 Weekly Leaderboard",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = Gold.copy(alpha = 0.12f)
            ) {
                Text(
                    "LIVE",
                    modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    color      = Gold,
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxWidth().height(80.dp), Alignment.Center) {
                CircularProgressIndicator(color = Gold, modifier = Modifier.size(28.dp))
            }
            return@Column
        }

        if (entries.isEmpty()) {
            // Empty state
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier            = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🌟", fontSize = 36.sp)
                    Text(
                        "Be the first to earn coins\nthis week!",
                        textAlign  = TextAlign.Center,
                        style      = MaterialTheme.typography.bodyMedium,
                        color      = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Column
        }

        // Top 3 podium
        if (entries.size >= 3) {
            PodiumRow(entries.take(3))
            Spacer(Modifier.height(4.dp))
        }

        // Remaining entries (4–10)
        entries.drop(3).forEachIndexed { idx, entry ->
            LeaderboardRow(rank = idx + 4, entry = entry)
        }

        // Current user's position (if not in top list)
        userEntry?.let { user ->
            val alreadyShown = entries.any { it.uid == user.uid }
            if (!alreadyShown) {
                HorizontalDivider(
                    modifier  = Modifier.padding(vertical = 4.dp),
                    color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
                Text(
                    "YOUR POSITION",
                    fontSize      = 9.sp,
                    fontWeight    = FontWeight.ExtraBold,
                    color         = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 2.sp,
                    modifier      = Modifier.padding(horizontal = 4.dp)
                )
                LeaderboardRow(rank = 0, entry = user, isHighlighted = true)
            }
        }
    }
}

// ─── Podium Row (Top 3) ───────────────────────────────────────────────────────

@Composable
private fun PodiumRow(top3: List<LeaderboardEntry>) {
    val podiumOrder = if (top3.size >= 3)
        listOf(top3[1], top3[0], top3[2]) // 2nd, 1st, 3rd arrangement
    else top3

    val heights = listOf(68.dp, 88.dp, 52.dp)

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment     = Alignment.Bottom
    ) {
        podiumOrder.forEachIndexed { visualIdx, entry ->
            val isCenter      = visualIdx == 1 // actual rank 1 in center
            val actualRank    = if (isCenter) 1 else if (visualIdx == 0) 2 else 3
            val podiumHeight  = heights[visualIdx]
            val medalEmoji    = when (actualRank) { 1 -> "🥇"; 2 -> "🥈"; else -> "🥉" }
            val podiumColor   = when (actualRank) {
                1 -> Brush.verticalGradient(listOf(Color(0xFFFFD700), Color(0xFFFF8F00)))
                2 -> Brush.verticalGradient(listOf(Color(0xFFB0BEC5), Color(0xFF78909C)))
                else -> Brush.verticalGradient(listOf(Color(0xFFBF8C60), Color(0xFF8D6E63)))
            }

            // Entrance animation for each podium entry
            val scale by animateFloatAsState(
                targetValue   = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMedium
                ),
                label = "podiumScale$actualRank"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
                modifier            = Modifier.weight(1f).graphicsLayer { scaleX = scale; scaleY = scale }
            ) {
                // Avatar + name
                Text(medalEmoji, fontSize = if (isCenter) 28.sp else 22.sp)
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier         = Modifier
                        .size(if (isCenter) 48.dp else 40.dp)
                        .clip(CircleShape)
                        .background(podiumColor)
                        .border(
                            width  = if (entry.isCurrentUser) 2.dp else 0.dp,
                            color  = PrizeCyan,
                            shape  = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        entry.displayName.take(1).uppercase(),
                        fontSize   = if (isCenter) 20.sp else 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = Color.White
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    entry.displayName.take(8),
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = if (entry.isCurrentUser) FontWeight.ExtraBold else FontWeight.Normal,
                    color      = if (entry.isCurrentUser) PrizeCyan else MaterialTheme.colorScheme.onSurface,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Text(
                    "🪙 ${entry.weeklyCoins}",
                    style  = MaterialTheme.typography.labelSmall,
                    color  = Gold,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))

                // Podium block
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(podiumHeight)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(podiumColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "#$actualRank",
                        fontWeight = FontWeight.ExtraBold,
                        color      = Color.White.copy(alpha = 0.9f),
                        fontSize   = if (isCenter) 18.sp else 14.sp
                    )
                }
            }
        }
    }
}

// ─── Leaderboard Row ──────────────────────────────────────────────────────────

@Composable
private fun LeaderboardRow(
    rank         : Int,
    entry        : LeaderboardEntry,
    isHighlighted: Boolean = entry.isCurrentUser
) {
    val bgColor = when {
        isHighlighted -> Gold.copy(alpha = 0.08f)
        else          -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when {
        isHighlighted -> Gold.copy(alpha = 0.4f)
        else          -> Color.Transparent
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = bgColor),
        border   = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier              = Modifier.padding(12.dp, 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Rank badge
            Box(
                modifier         = Modifier
                    .size(32.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (rank > 0) "#$rank" else "—",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 11.sp,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Avatar
            Box(
                modifier         = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(PrizeBlue, PrizePurple))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    entry.displayName.take(1).uppercase(),
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = Color.White
                )
            }

            // Name + tier
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isHighlighted) "${entry.displayName} (You)" else entry.displayName,
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isHighlighted) FontWeight.ExtraBold else FontWeight.Medium,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Text(
                    entry.tier.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Gold.copy(alpha = 0.85f)
                )
            }

            // Coin count
            Text(
                "🪙 ${entry.weeklyCoins}",
                fontWeight = FontWeight.Bold,
                color      = if (isHighlighted) Gold else MaterialTheme.colorScheme.onSurface,
                style      = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// ─── Grand Prize Info Dialog ──────────────────────────────────────────────────

@Composable
fun GrandPrizeInfoDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "dialogAnim")
        val shimmerX by infiniteTransition.animateFloat(
            initialValue  = -400f,
            targetValue   = 700f,
            animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
            label         = "dialogShimmer"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF13102A), Color(0xFF0A0818))
                    )
                )
                .border(
                    1.5.dp,
                    Brush.linearGradient(listOf(Gold.copy(0.7f), PrizePurple.copy(0.5f), PrizeCyan.copy(0.4f))),
                    RoundedCornerShape(28.dp)
                )
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Title
                Text("🏆", fontSize = 52.sp)
                Box {
                    Text(
                        "HOW BIG PRIZE WORKS",
                        fontSize      = 18.sp,
                        fontWeight    = FontWeight.ExtraBold,
                        color         = Color.White.copy(0.1f),
                        letterSpacing = 1.sp
                    )
                    Text(
                        "HOW BIG PRIZE WORKS",
                        fontSize      = 18.sp,
                        fontWeight    = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        style         = LocalTextStyle.current.copy(
                            brush = Brush.linearGradient(
                                colors = listOf(Color.White, Gold, GoldLight, Color.White),
                                start  = Offset(shimmerX - 200, 0f),
                                end    = Offset(shimmerX + 100, 50f)
                            )
                        )
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                // How it works steps
                PrizeStep("🪙", "Collect Coins",
                    "Watch ads & login daily to earn coins throughout the week.")
                PrizeStep("📈", "Climb Tiers",
                    "More coins = higher tier. Reach Grand Champion for maximum bonus rewards!")
                PrizeStep("📅", "Weekly Reset",
                    "Every Sunday the leaderboard resets. New week, new chance to win big!")
                PrizeStep("🎁", "Prize Tiers",
                    "🏆 Grand Champion (800+) · 💎 Elite (500+) · 🥉 Champion (300+) · ⭐ Achiever (100+)")

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                Text(
                    "Top earners get bonus coins credited automatically at week-end!",
                    style     = MaterialTheme.typography.bodySmall,
                    color     = PrizeCyan.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold
                )

                Button(
                    onClick  = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = Gold,
                        contentColor   = Color.Black
                    )
                ) {
                    Text("Let's Go! 🚀", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun PrizeStep(icon: String, title: String, desc: String) {
    Row(
        verticalAlignment     = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier              = Modifier.fillMaxWidth()
    ) {
        Text(icon, fontSize = 20.sp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Text(desc, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ─── Transaction Item ─────────────────────────────────────────────────────────

@Composable
private fun TransactionItem(tx: CoinTransaction) {
    val isPositive  = tx.amount >= 0
    val amountColor = if (isPositive) Emerald else CoralRed
    val icon: ImageVector = when (tx.type) {
        TransactionType.AD_REWARD      -> Icons.Filled.PlayArrow
        TransactionType.DAILY_LOGIN    -> Icons.Filled.Star
        TransactionType.STREAK_BONUS   -> Icons.Filled.LocalFireDepartment
        TransactionType.FEATURE_UNLOCK -> Icons.Filled.Lock
        TransactionType.SUBSCRIPTION   -> Icons.Filled.CardMembership
    }
    val sdf = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier              = Modifier.padding(14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier         = Modifier.size(40.dp).background(amountColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = amountColor, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    tx.description,
                    style    = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    try { sdf.format(tx.timestamp.toDate()) } catch (e: Exception) { "—" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "${if (isPositive) "+" else ""}${tx.amount} 🪙",
                fontWeight = FontWeight.Bold,
                color      = amountColor,
                style      = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// ─── Reward Earned Dialog ─────────────────────────────────────────────────────

@Composable
fun RewardEarnedDialog(coins: Int, onDismiss: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue   = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label         = "dialogScale"
    )
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(dismissOnClickOutside = true)) {
        Box(
            modifier = Modifier
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1A237E), Color(0xFF6A1B9A))))
                .border(1.dp, GlassBorder, RoundedCornerShape(28.dp))
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("🎉", fontSize = 56.sp)
                Text("Reward Earned!", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Color.White)
                Text("+$coins coins", fontWeight = FontWeight.Bold, fontSize = 36.sp, color = Gold)
                Text(
                    "Added to your wallet instantly",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick  = onDismiss,
                    shape    = RoundedCornerShape(50),
                    colors   = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Awesome! 🚀", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Daily Reward Dialog ──────────────────────────────────────────────────────

@Composable
private fun DailyRewardDialog(result: DailyRewardManager.DailyRewardResult, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Text(if (result.isStreakBonus) "🔥" else "🌅", fontSize = 40.sp) },
        title = {
            Text(
                if (result.isStreakBonus) "${result.currentStreak}-Day Streak Bonus!" else "Daily Login Bonus",
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("+${result.coinsEarned} coins added to your wallet", textAlign = TextAlign.Center)
                if (result.isStreakBonus) {
                    Text(
                        "🎊 Streak bonus: +${result.streakBonusCoins} coins",
                        color = Gold, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Claim", fontWeight = FontWeight.Bold) }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

// ─── Wallet Disabled ──────────────────────────────────────────────────────────

@Composable
private fun WalletDisabledPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("🔒", fontSize = 64.sp)
            Text("Wallet Temporarily Unavailable", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            Text(
                "The wallet system is currently disabled by admin. Please check back later.",
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

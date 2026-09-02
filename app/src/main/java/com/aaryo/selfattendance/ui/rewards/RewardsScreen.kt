package com.aaryo.selfattendance.ui.rewards

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.aaryo.selfattendance.ads.AdsController
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.aaryo.selfattendance.data.remote.RemoteConfigManager
import com.aaryo.selfattendance.data.repository.RewardRepository
import com.aaryo.selfattendance.security.CoinSecurityEngine
import com.aaryo.selfattendance.utils.SpinSoundManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.aaryo.selfattendance.utils.LocaleManager
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
//  Constants
// ─────────────────────────────────────────────────────────────────────────────

private const val MAX_DAILY_SPINS  = 5
private const val COOLDOWN_MS      = 60_000L   // 1 minute
const val MIN_COINS_FOR_REWARDS_CENTRE = 1500

// ─────────────────────────────────────────────────────────────────────────────
//  Brand Colors
// ─────────────────────────────────────────────────────────────────────────────

private val NavyBg         = Color(0xFF0D1B2A)
private val DarkSlate      = Color(0xFF1B263B)
private val DarkSlate2     = Color(0xFF162030)
private val RoyalGold      = Color(0xFFFFD700)
private val RoyalGoldDark  = Color(0xFFB8860B)
private val RoyalGoldLight = Color(0xFFFFF176)
private val PremiumBlue    = Color(0xFF3A86FF)
private val SuccessGreen   = Color(0xFF06D6A0)
private val TextWhite      = Color(0xFFFFFFFF)
private val TextMuted      = Color(0xFF8899AA)
private val GlassEdge      = Color(0x33FFFFFF)
private val GlassFill      = Color(0x14FFFFFF)

// ─────────────────────────────────────────────────────────────────────────────
//  Wheel Segments  (12 total, 4 = "Better Luck")
// ─────────────────────────────────────────────────────────────────────────────

data class SpinSegment(
    val label    : String,
    val subLabel : String,
    val axCoins  : Int,
    val emoji    : String,
    val outerClr : Color,
    val innerClr : Color,
    val isGold   : Boolean = false,
    val isBad    : Boolean = false    // Better Luck segment
)

private val SEGMENTS = listOf(
    SpinSegment("5",      "AX",          5,   "🪙", Color(0xFF1E3F7A), Color(0xFF2A5BB8)),
    SpinSegment("BETTER", "LUCK",        0,   "😔", Color(0xFF2C2C3E), Color(0xFF3D3D55), isBad = true),
    SpinSegment("10",     "AX",          10,  "💰", Color(0xFF162F5A), Color(0xFF1E4490)),
    SpinSegment("20",     "AX",          20,  "🏅", Color(0xFF1A3A70), Color(0xFF2657AB)),
    SpinSegment("BETTER", "LUCK",        0,   "😔", Color(0xFF2C2C3E), Color(0xFF3D3D55), isBad = true),
    SpinSegment("50",     "AX",          50,  "⭐", Color(0xFF0F2A55), Color(0xFF1A4080)),
    SpinSegment("15",     "AX",          15,  "🪙", Color(0xFF1E3F7A), Color(0xFF2A5BB8)),
    SpinSegment("BETTER", "LUCK",        0,   "😔", Color(0xFF2C2C3E), Color(0xFF3D3D55), isBad = true),
    SpinSegment("100",    "AX",          100, "💎", Color(0xFF5C4200), Color(0xFFA07800), isGold = true),
    SpinSegment("30",     "AX",          30,  "🌟", Color(0xFF1A3A70), Color(0xFF2A5BB8)),
    SpinSegment("BETTER", "LUCK",        0,   "😔", Color(0xFF2C2C3E), Color(0xFF3D3D55), isBad = true),
    SpinSegment("200",    "AX",          200, "🏆", Color(0xFF6A0A0A), Color(0xFFB01515)),
)

// ─────────────────────────────────────────────────────────────────────────────
//  Confetti
// ─────────────────────────────────────────────────────────────────────────────

private val confettiColors = listOf(
    RoyalGold, RoyalGoldLight, PremiumBlue, SuccessGreen,
    Color(0xFFFF6B6B), Color(0xFFFFE66D), TextWhite
)

// ─────────────────────────────────────────────────────────────────────────────
//  RewardsScreen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RewardsScreen(navController: NavController) {
    val context  = LocalContext.current
    // findActivity() returns Activity? — resolve once; fall back gracefully if null
    val activity = with(LocaleManager) { context.findActivity() }
    val prefs    = remember { PreferencesManager(context) }
    val scope    = rememberCoroutineScope()
    val snack    = remember { SnackbarHostState() }
    val soundMgr = remember { SpinSoundManager(context) }

    DisposableEffect(Unit) { onDispose { soundMgr.release() } }

    val today    = LocalDate.now().toString()
    val isNewDay = prefs.lastSpinDate != today

    // ── Daily spin state ──────────────────────────────────────────────────────
    var spinsUsed by remember {
        mutableIntStateOf(
            if (isNewDay) 0.also {
                prefs.dailySpinsUsed        = 0
                prefs.lastSpinTimestampMs   = 0L
                prefs.lastSpinDate          = today
                // Reset daily better luck schedule for a fresh randomized sequence
                prefs.winningSpinIndices    = ""
            } else prefs.dailySpinsUsed
        )
    }
    var lastSpinMs by remember {
        mutableLongStateOf(if (isNewDay) 0L else prefs.lastSpinTimestampMs)
    }

    // Live 1-minute cooldown counter (ms)
    var cooldownMs by remember {
        mutableLongStateOf(
            if (lastSpinMs == 0L) 0L
            else maxOf(0L, COOLDOWN_MS - (System.currentTimeMillis() - lastSpinMs))
        )
    }
    LaunchedEffect(lastSpinMs) {
        while (true) {
            val rem = if (lastSpinMs == 0L) 0L
                      else maxOf(0L, COOLDOWN_MS - (System.currentTimeMillis() - lastSpinMs))
            cooldownMs = rem
            if (rem <= 0L) break
            delay(500L)
        }
    }

    val spinsLeft = MAX_DAILY_SPINS - spinsUsed

    var axBalance    by remember { mutableIntStateOf(prefs.coinBalance) }

    // after reinstall or on a second device. Local prefs are the fallback.
    LaunchedEffect(Unit) {
        // Firebase, not only when Firebase has strictly more coins.
        // SplashViewModel already did the primary sync; this is a safety net
        // for direct navigation to RewardsScreen.
        try {
            val remote = RewardRepository.loadRewards()
            if (remote != null && remote.coinBalance >= prefs.coinBalance) {
                prefs.coinBalance         = remote.coinBalance
                prefs.totalCoinsEarned    = remote.totalCoinsEarned
                prefs.lastSpinDate        = remote.lastSpinDate
                prefs.dailySpinsUsed      = remote.dailySpinsUsed
                prefs.lastSpinTimestampMs = remote.lastSpinTimestampMs
                prefs.lastSpinIndex       = remote.lastSpinIndex
                axBalance = remote.coinBalance
            }
        } catch (_: Exception) { /* offline — use local prefs */ }
    }
    var spinning     by remember { mutableStateOf(false) }
    var adLoading    by remember { mutableStateOf(false) }
    var showDialog   by remember { mutableStateOf(false) }
    var wonSegment   by remember { mutableStateOf<SpinSegment?>(null) }
    var showConfetti by remember { mutableStateOf(false) }
    var lastWonLabel by remember {
        mutableStateOf(
            if (prefs.lastSpinIndex in SEGMENTS.indices)
                SEGMENTS[prefs.lastSpinIndex].let {
                    if (it.isBad) "Better Luck" else "${it.label} ${it.subLabel}"
                }
            else ""
        )
    }

    val rotation = remember { Animatable(0f) }

    val canSpin = spinsLeft > 0 && cooldownMs <= 0L && !spinning && !adLoading

    // ── Execute spin (called after ad reward) ────────────────────────────────
    fun executeSpin() {
        spinning  = true
        adLoading = false

        // ── Probability Engine:
        // 1) Out of 5 spins per day, randomly pick exactly 2 spins that reward coins and 3 spins that land on "Better Luck".
        //    The winning schedule is randomized per day (e.g. spins [1, 4] win, while spins [0, 2, 3] give Better Luck).
        val dailyWinners: Set<Int> = run {
            val saved = prefs.winningSpinIndices
            if (saved.isNotBlank()) {
                saved.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
            } else {
                // Pick 2 distinct random spin numbers out of 0..4 as the daily winners (3 will be Better Luck)
                val pickedWinners = (0 until MAX_DAILY_SPINS).shuffled().take(2).toSet()
                prefs.winningSpinIndices = pickedWinners.joinToString(",")
                pickedWinners
            }
        }

        val currentSpinNumber = spinsUsed // 0-based: 0, 1, 2, 3, 4
        val isWinningSpin = currentSpinNumber in dailyWinners

        val targetIdx = if (!isWinningSpin) {
            // Land on one of the 4 "Better Luck" segments (indices: 1, 4, 7, 10)
            val badIndices = SEGMENTS.indices.filter { SEGMENTS[it].isBad }
            badIndices.random()
        } else {
            // Winning spin: Weight 100 and 200 coins as ultra-rare!
            // SEGMENTS coin options:
            // 5 AX (idx 0), 10 AX (idx 2), 20 AX (idx 3), 50 AX (idx 5), 15 AX (idx 6), 100 AX (idx 8), 30 AX (idx 9), 200 AX (idx 11)
            // Weighted probabilities:
            // 5 AX: 25%, 10 AX: 25%, 15 AX: 20%, 20 AX: 15%, 30 AX: 8%, 50 AX: 5%, 100 AX: 1.5%, 200 AX: 0.5%
            val roll = Random.nextDouble(100.0)
            when {
                roll < 25.0 -> 0   // 5 AX
                roll < 50.0 -> 2   // 10 AX
                roll < 70.0 -> 6   // 15 AX
                roll < 85.0 -> 3   // 20 AX
                roll < 93.0 -> 9   // 30 AX
                roll < 98.0 -> 5   // 50 AX
                roll < 99.5 -> 8   // 100 AX (Ultra rare 1.5%)
                else        -> 11  // 200 AX (Ultra rare 0.5%)
            }
        }

        val segDeg    = 360f / SEGMENTS.size
        val targetAngle = 360f - (targetIdx * segDeg + segDeg / 2f)
        val landAngle   = ((targetAngle - rotation.value % 360f) + 360f) % 360f
        val totalSpin = 1440f + landAngle

        // Start ticking sounds — mirrors the 4 s wheel animation duration
        soundMgr.startSpinSound(scope, 4000L)

        scope.launch {
            rotation.animateTo(
                targetValue   = rotation.value + totalSpin,
                animationSpec = tween(4000, easing = CubicBezierEasing(0.2f, 0.8f, 0.3f, 1.0f))
            )
            val seg   = SEGMENTS[targetIdx]
            val coins = seg.axCoins
            val now   = System.currentTimeMillis()

            // Stop ticking, play result sound
            soundMgr.stopSpinSound()
            if (seg.isBad) soundMgr.playLoseSound() else soundMgr.playWinSound()

            spinning              = false
            wonSegment            = seg
            showDialog            = true
            showConfetti          = coins > 0
            lastWonLabel          = if (seg.isBad) "Better Luck" else "${seg.label} ${seg.subLabel}"
            spinsUsed             += 1
            lastSpinMs            = now
            prefs.dailySpinsUsed  = spinsUsed
            prefs.lastSpinDate    = today
            prefs.lastSpinIndex   = targetIdx
            prefs.lastSpinTimestampMs = now

            if (coins > 0) {
                CoinSecurityEngine.secureCreditCoins(prefs, coins, "rewards_wheel")
                axBalance = prefs.coinBalance
            }

            // coins are not lost on reinstall or when switching devices.
            // Uses an atomic delta (coins) rather than an absolute balance so
            // concurrent writes from another device can't overwrite each other.
            RewardRepository.saveRewards(
                coinsDelta          = coins,
                lastSpinDate        = today,
                dailySpinsUsed      = spinsUsed,
                lastSpinTimestampMs = now,
                lastSpinIndex       = targetIdx
            )

            delay(3500)
            showConfetti = false
        }
    }

    // ── Watch Ad → then spin ─────────────────────────────────────────────────
    fun onWatchAdAndSpin() {
        if (!canSpin) return
        adLoading = true

        if (!AdsController.isAdsEnabled || !AdsController.mobileAdsReady) {
            // Ads not available — spin directly as fallback
            executeSpin()
            return
        }

        // activity is Activity? — if null (rare edge case), skip ad and spin directly
        val act = activity ?: run { executeSpin(); return }
        AdsController.showRewardedAd(
            activity   = act,
            onRewarded = { executeSpin() },
            onNotReady = {
                adLoading = false
                scope.launch {
                    snack.showSnackbar("Ad load ho rahi hai, 10 second baad dobara try karo 🔄")
                }
            }
        )
    }

    // ── UI Root ───────────────────────────────────────────────────────────────
    Scaffold(
        snackbarHost        = { SnackbarHost(snack) },
        containerColor      = NavyBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        // BannerAd hata diya — ab MainScreen ke Scaffold mein hai taaki tab switch par reload na ho
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NavyBg)
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // ── Header ───────────────────────────────────────────────────
                PremiumHeader(axBalance = axBalance, spinsLeft = spinsLeft, maxSpins = MAX_DAILY_SPINS)

                Spacer(Modifier.height(20.dp))

                // ── Wheel ────────────────────────────────────────────────────
                WheelSection(
                    rotationDeg = rotation.value,
                    spinning    = spinning,
                    adLoading   = adLoading,
                    canSpin     = canSpin,
                    cooldownMs  = cooldownMs,
                    spinsLeft   = spinsLeft,
                    onSpin      = ::onWatchAdAndSpin
                )

                Spacer(Modifier.height(20.dp))

                // ── Cooldown / info card ──────────────────────────────────────
                when {
                    spinsLeft <= 0 -> AllSpinsDoneCard()
                    cooldownMs > 0 -> CooldownCard(cooldownMs = cooldownMs)
                    else           -> SpinInfoCard()
                }

                Spacer(Modifier.height(12.dp))

                if (lastWonLabel.isNotBlank()) {
                    LastRewardCard(label = lastWonLabel)
                }

                Spacer(Modifier.height(20.dp))

                // ── Rewards Centre Button (Unlocks automatically at 1500 coins) ──
                val isRewardsCentreUnlocked = axBalance >= MIN_COINS_FOR_REWARDS_CENTRE
                val remainingCoins = (MIN_COINS_FOR_REWARDS_CENTRE - axBalance).coerceAtLeast(0)
                val unlockProgress = (axBalance.toFloat() / MIN_COINS_FOR_REWARDS_CENTRE.toFloat()).coerceIn(0f, 1f)

                if (isRewardsCentreUnlocked) {
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://moviebox-ai.github.io/Self-Attendance-Pro/withdrawal.html")
                                )
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Rewards Centre open nahi ho paya", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(RoyalGold, Color(0xFFFF9800))
                                ),
                                shape = RoundedCornerShape(14.dp)
                            )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CardGiftcard,
                                contentDescription = null,
                                tint = NavyBg,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Rewards Centre (Unlocked)",
                                color = NavyBg,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = NavyBg,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    // Locked State Card with progress
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Toast.makeText(
                                    context,
                                    "Rewards Centre unlock karne ke liye $remainingCoins aur coins chahiye! (Target: $MIN_COINS_FOR_REWARDS_CENTRE)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSlate),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Locked",
                                        tint = Color(0xFFFFB703),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Rewards Centre",
                                        color = TextWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                                Surface(
                                    color = Color(0xFFFFB703).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "🔒 $axBalance / $MIN_COINS_FOR_REWARDS_CENTRE AX",
                                        color = Color(0xFFFFB703),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            LinearProgressIndicator(
                                progress = { unlockProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = RoyalGold,
                                trackColor = Color.White.copy(alpha = 0.1f)
                            )

                            Spacer(Modifier.height(6.dp))

                            Text(
                                text = "$remainingCoins coins aur collect karein unlock karne ke liye",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    // A dedicated Reward History screen doesn't exist yet in this
                    // codebase, so give the user clear feedback instead of a dead
                    // tap until that screen is built.
                    onClick  = {
                        Toast.makeText(
                            context,
                            "Reward History jald aa raha hai!",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    shape    = RoundedCornerShape(14.dp),
                    border   = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                        width = 1.dp,
                        brush = Brush.linearGradient(listOf(RoyalGold.copy(0.5f), PremiumBlue.copy(0.5f)))
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Default.History, null, tint = RoyalGold, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Reward History", color = TextMuted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }

                // Native ad — FIX: Remote Config guard add kiya
                if (RemoteConfigManager.getInstance().showNativeAd()) {
                    Spacer(Modifier.height(16.dp))
                    // NativeAdView singleton use karo taaki wapas aane par ad reload na ho
                    AdsController.NativeAdView()
                }

                Spacer(Modifier.height(24.dp))
            }

            // Confetti
            if (showConfetti) ConfettiOverlay()

            // Reward dialog — coin flip for wins, simple dialog for losses
            if (showDialog && wonSegment != null) {
                val seg = wonSegment!!
                if (!seg.isBad) {
                    CoinFlipDialog(
                        coinsWon = seg.axCoins,
                        label    = "${seg.label} ${seg.subLabel} aapke balance mein add ho gaye!",
                        onClaim  = { showDialog = false }
                    )
                } else {
                    RewardWonDialog(segment = seg, onClaim = { showDialog = false })
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Premium Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PremiumHeader(axBalance: Int, spinsLeft: Int, maxSpins: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Brush.radialGradient(listOf(RoyalGold.copy(0.3f), Color.Transparent)), CircleShape)
                    .border(1.dp, RoyalGold.copy(0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter            = painterResource(com.aaryo.selfattendance.R.drawable.ax_coin),
                    contentDescription = "AX Coin",
                    modifier           = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Daily Reward Spin", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = TextWhite)
                Text("Ad dekho, spin karo, coins kamao!", fontSize = 12.sp, color = TextMuted)
            }
        }

        Spacer(Modifier.height(14.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text("AX Balance", fontSize = 12.sp, color = TextMuted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter            = painterResource(com.aaryo.selfattendance.R.drawable.ax_coin),
                            contentDescription = "AX Coin",
                            modifier           = Modifier.size(30.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("$axBalance", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = RoyalGold)
                        Spacer(Modifier.width(4.dp))
                        Text("AX", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RoyalGoldDark, modifier = Modifier.padding(bottom = 2.dp))
                    }
                }
                // Spin dots
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        repeat(maxSpins) { i ->
                            val used = i >= spinsLeft
                            Box(
                                modifier = Modifier
                                    .size(if (used) 8.dp else 12.dp)
                                    .background(
                                        if (used) TextMuted.copy(0.3f) else RoyalGold,
                                        CircleShape
                                    )
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text("$spinsLeft spins left", fontSize = 10.sp, color = TextMuted)
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().height(2.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, RoyalGold.copy(0.7f), Color.Transparent)))
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Wheel Section
//  ⚠️  POINTER is drawn LAST so it appears ON TOP of the wheel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WheelSection(
    rotationDeg : Float,
    spinning    : Boolean,
    adLoading   : Boolean,
    canSpin     : Boolean,
    cooldownMs  : Long,
    spinsLeft   : Int,
    onSpin      : () -> Unit
) {
    val wheelSizeDp = 310

    Column(horizontalAlignment = Alignment.CenterHorizontally) {

        // Outer container — wheel + pointer stacked
        Box(
            modifier         = Modifier.size((wheelSizeDp + 40).dp),
            contentAlignment = Alignment.Center
        ) {
            // 1. Outer glow ring
            Box(
                modifier = Modifier
                    .size((wheelSizeDp + 40).dp)
                    .background(
                        Brush.radialGradient(listOf(RoyalGold.copy(0.18f), Color.Transparent)),
                        CircleShape
                    )
            )

            // 2. Shadow ring
            Box(
                modifier = Modifier
                    .size((wheelSizeDp + 6).dp)
                    .shadow(24.dp, CircleShape, spotColor = RoyalGold.copy(0.35f))
            )

            // 3. Rotating wheel
            Box(
                modifier         = Modifier
                    .size(wheelSizeDp.dp)
                    .rotate(rotationDeg),
                contentAlignment = Alignment.Center
            ) {
                PremiumWheelCanvas(segments = SEGMENTS)

                // Center SPIN button (rotates WITH wheel — stays visually centered)
                SpinButton(
                    spinning  = spinning,
                    adLoading = adLoading,
                    canSpin   = canSpin,
                    onSpin    = onSpin
                )
            }

            // 4. ⭐ POINTER — drawn LAST = on top of wheel (z-order fix)
            Box(
                modifier         = Modifier.size((wheelSizeDp + 40).dp),
                contentAlignment = Alignment.TopCenter
            ) {
                GoldPointer(modifier = Modifier.padding(top = 4.dp))
            }
        }

        Spacer(Modifier.height(16.dp))

        // Spin action button (below wheel)
        Button(
            onClick  = onSpin,
            enabled  = canSpin,
            modifier = Modifier.fillMaxWidth(0.85f).height(54.dp),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(
                containerColor         = Color(0xFF7C4DFF),
                disabledContainerColor = DarkSlate
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
        ) {
            when {
                spinning || adLoading -> {
                    Box(
                        modifier         = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = TextWhite, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    }
                }
                cooldownMs > 0 -> {
                    Icon(Icons.Default.Timer, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Next spin ${formatCooldown(cooldownMs)} mein",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp,
                        color      = TextMuted
                    )
                }
                spinsLeft <= 0 -> {
                    Text("✅ Kal Wapas Aao!", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                else -> {
                    Text("📺  Ad Dekho & Spin Karo", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
            }
        }

        Spacer(Modifier.height(5.dp))
        Text(
            "Har spin ke liye ek rewarded ad dekhna zaroori hai",
            fontSize  = 11.sp,
            color     = TextMuted.copy(0.6f),
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Gold Pointer  (drawn ON TOP of wheel — z-order fix)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GoldPointer(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(34.dp, 28.dp)) {
        val w = size.width
        val h = size.height

        // Drop shadow
        val shadow = Path().apply {
            moveTo(w / 2f, h + 4f)
            lineTo(-4f, -2f)
            lineTo(w + 4f, -2f)
            close()
        }
        drawPath(shadow, Color.Black.copy(0.6f))

        // Gold body (triangle pointing DOWN ▼)
        val tri = Path().apply {
            moveTo(w / 2f, h)
            lineTo(2f, 2f)
            lineTo(w - 2f, 2f)
            close()
        }
        drawPath(
            path  = tri,
            brush = Brush.verticalGradient(
                listOf(Color(0xFFFFF9C4), Color(0xFFFFD700), Color(0xFF8B6914))
            )
        )
        // White sheen
        drawPath(tri, Color.White.copy(0.45f), style = Stroke(1.8f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Premium Wheel Canvas
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PremiumWheelCanvas(segments: List<SpinSegment>) {
    val count    = segments.size
    val sweepDeg = 360f / count

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

        Canvas(modifier = Modifier.fillMaxSize()) {
            val outerR = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val ringW  = outerR * 0.07f
            val fillR  = outerR - ringW

            segments.forEachIndexed { i, seg ->
                val startAngle = i * sweepDeg - 90f

                // Outer fill
                drawArc(seg.outerClr, startAngle, sweepDeg - 0.5f, true,
                    Offset(center.x - fillR, center.y - fillR), Size(fillR * 2f, fillR * 2f))

                // Inner panel
                val panelR = fillR * 0.82f
                drawArc(seg.innerClr, startAngle, sweepDeg - 0.5f, true,
                    Offset(center.x - panelR, center.y - panelR), Size(panelR * 2f, panelR * 2f))

                // Gold shimmer
                if (seg.isGold) {
                    val hlR = fillR * 0.72f
                    drawArc(RoyalGold.copy(0.14f), startAngle, sweepDeg - 0.5f, true,
                        Offset(center.x - hlR, center.y - hlR), Size(hlR * 2f, hlR * 2f))
                }
                // Better-luck grey overlay
                if (seg.isBad) {
                    val hlR = fillR * 0.72f
                    drawArc(Color.White.copy(0.04f), startAngle, sweepDeg - 0.5f, true,
                        Offset(center.x - hlR, center.y - hlR), Size(hlR * 2f, hlR * 2f))
                }

                // Divider spoke
                val rad = Math.toRadians(startAngle.toDouble())
                drawLine(Color.White.copy(0.18f), center,
                    Offset(center.x + (fillR * cos(rad)).toFloat(), center.y + (fillR * sin(rad)).toFloat()),
                    strokeWidth = 1.5f)
            }

            // Gold outer ring (3-layer 3D)
            drawCircle(RoyalGoldDark, outerR - ringW * 0.15f, center, style = Stroke(ringW * 1.3f))
            drawCircle(
                brush  = Brush.radialGradient(listOf(RoyalGoldLight, RoyalGold, RoyalGoldDark), center, outerR),
                radius = outerR - ringW * 0.55f, center = center, style = Stroke(ringW * 0.8f)
            )
            drawCircle(Color.White.copy(0.22f), outerR - ringW, center, style = Stroke(1.5f))

            // Inner hub ring
            val hubR = outerR * 0.18f
            drawCircle(NavyBg, hubR + 4f, center)
            drawCircle(
                brush  = Brush.radialGradient(listOf(RoyalGold, RoyalGoldDark), center, hubR + 4f),
                radius = hubR + 4f, center = center, style = Stroke(3f)
            )
        }

        // Segment labels
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val outerR   = minOf(maxWidth, maxHeight) / 2
            val ringFrac = 0.07f
            val fillR    = outerR * (1f - ringFrac)
            val textR    = fillR * 0.60f

            segments.forEachIndexed { i, seg ->
                val mid  = i * sweepDeg - 90f + sweepDeg / 2f
                val rad  = Math.toRadians(mid.toDouble())
                val ox   = (textR.value * cos(rad)).toFloat()
                val oy   = (textR.value * sin(rad)).toFloat()

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .offset(x = outerR + ox.dp - 21.dp, y = outerR + oy.dp - 21.dp)
                        .rotate(mid + 90f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(seg.emoji, fontSize = 11.sp)
                        Text(
                            seg.label,
                            fontSize   = if (seg.label.length > 4) 6.sp else 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = if (seg.isGold) RoyalGold else if (seg.isBad) TextMuted else TextWhite,
                            textAlign  = TextAlign.Center,
                            lineHeight = 9.sp
                        )
                        Text(
                            seg.subLabel,
                            fontSize  = 5.5.sp,
                            color     = if (seg.isBad) TextMuted.copy(0.7f) else TextWhite.copy(0.65f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Center Spin Button  (inside the wheel — visual element only, spins with wheel)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SpinButton(spinning: Boolean, adLoading: Boolean, canSpin: Boolean, onSpin: () -> Unit) {
    val busy = spinning || adLoading

    Box(
        modifier         = Modifier
            .size(62.dp)
            .shadow(12.dp, CircleShape, spotColor = if (canSpin) RoyalGold else Color.Transparent)
            .background(
                Brush.radialGradient(
                    if (canSpin) listOf(RoyalGoldLight, RoyalGold, RoyalGoldDark)
                    else         listOf(DarkSlate, DarkSlate2)
                ),
                CircleShape
            )
            .border(
                2.dp,
                Brush.linearGradient(
                    if (canSpin) listOf(TextWhite.copy(0.6f), RoyalGold)
                    else         listOf(TextMuted.copy(0.2f), TextMuted.copy(0.1f))
                ),
                CircleShape
            )
            .then(if (canSpin && !busy) Modifier.clickable(onClick = onSpin) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (busy) {
            CircularProgressIndicator(color = NavyBg, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⚡", fontSize = 20.sp)
                Text(
                    "SPIN",
                    fontSize      = 8.sp,
                    fontWeight    = FontWeight.ExtraBold,
                    color         = if (canSpin) NavyBg else TextMuted,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Info cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SpinInfoCard() {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Star, null, tint = RoyalGold, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Spin ke liye tayyar!", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                Text("Ad dekho aur coins kamao", fontSize = 12.sp, color = TextMuted)
            }
        }
    }
}

@Composable
private fun CooldownCard(cooldownMs: Long) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, null, tint = PremiumBlue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Next spin available in", fontSize = 13.sp, color = TextMuted)
            }
            Spacer(Modifier.height(10.dp))

            // 1-minute countdown MM:SS
            val sec   = (cooldownMs / 1000L).toInt()
            val mm    = sec / 60
            val ss    = sec % 60

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                CdUnit(value = "%02d".format(mm), label = "MIN")
                Text(":", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextMuted,
                    modifier = Modifier.padding(horizontal = 4.dp))
                CdUnit(value = "%02d".format(ss), label = "SEC")
            }

            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress          = { 1f - (cooldownMs.toFloat() / COOLDOWN_MS) },
                modifier          = Modifier.fillMaxWidth().height(4.dp),
                color             = PremiumBlue,
                trackColor        = DarkSlate2,
                strokeCap         = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun CdUnit(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier         = Modifier.background(DarkSlate2, RoundedCornerShape(8.dp)).padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) { Text(value, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = PremiumBlue) }
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 10.sp, color = TextMuted, letterSpacing = 1.sp)
    }
}

@Composable
private fun AllSpinsDoneCard() {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("✅", fontSize = 32.sp)
            Spacer(Modifier.height(4.dp))
            Text("Aaj ke $MAX_DAILY_SPINS spins khatam!", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextWhite)
            Text("Kal subah phir se $MAX_DAILY_SPINS naye spins milenge", fontSize = 12.sp, color = TextMuted)
        }
    }
}

@Composable
private fun LastRewardCard(label: String) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp)
                    .background(RoyalGold.copy(0.15f), CircleShape)
                    .border(1.dp, RoyalGold.copy(0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter            = painterResource(com.aaryo.selfattendance.R.drawable.ax_coin),
                    contentDescription = null,
                    modifier           = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Last Reward Earned", fontSize = 11.sp, color = TextMuted)
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = RoyalGold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Reward Won Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RewardWonDialog(segment: SpinSegment, onClaim: () -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties       = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSlate.copy(0.97f), RoundedCornerShape(28.dp))
                .border(1.dp,
                    Brush.linearGradient(
                        if (segment.isBad)
                            listOf(TextMuted.copy(0.3f), TextMuted.copy(0.2f))
                        else
                            listOf(RoyalGold.copy(0.6f), PremiumBlue.copy(0.4f))
                    ),
                    RoundedCornerShape(28.dp))
                .padding(28.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(
                            Brush.radialGradient(
                                if (segment.isBad) listOf(TextMuted.copy(0.1f), Color.Transparent)
                                else               listOf(RoyalGold.copy(0.3f), Color.Transparent)
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (segment.isBad) {
                        Text(segment.emoji, fontSize = 52.sp)
                    } else {
                        Image(
                            painter            = painterResource(com.aaryo.selfattendance.R.drawable.ax_coin),
                            contentDescription = "AX Coin Won",
                            modifier           = Modifier.size(72.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    if (segment.isBad) "Better Luck Next Time!" else "Congratulations! 🎉",
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = if (segment.isBad) TextMuted else TextWhite,
                    textAlign  = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (segment.isBad) "Is baar coins nahi mile, agle spin mein try karo!"
                    else               "Aapne earn kiya",
                    fontSize  = 13.sp,
                    color     = TextMuted,
                    textAlign = TextAlign.Center
                )

                if (!segment.isBad) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .background(RoyalGold.copy(0.12f), RoundedCornerShape(16.dp))
                            .border(1.dp, RoyalGold.copy(0.5f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 24.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter            = painterResource(com.aaryo.selfattendance.R.drawable.ax_coin),
                                contentDescription = null,
                                modifier           = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${segment.label} ${segment.subLabel}",
                                fontSize   = 26.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color      = RoyalGold
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("+${segment.axCoins} AX added to your balance",
                        fontSize = 12.sp, color = SuccessGreen, textAlign = TextAlign.Center)
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick        = onClaim,
                    modifier       = Modifier.fillMaxWidth().height(50.dp),
                    shape          = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors         = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (segment.isBad)
                                    Brush.horizontalGradient(listOf(DarkSlate, Color(0xFF2D3A4A)))
                                else
                                    Brush.horizontalGradient(listOf(RoyalGoldDark, RoyalGold, RoyalGoldLight)),
                                RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (segment.isBad) "OK, Dobara Try Karunga" else "✓  CLAIM REWARD",
                            fontSize      = 14.sp,
                            fontWeight    = FontWeight.ExtraBold,
                            color         = if (segment.isBad) TextMuted else NavyBg,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Confetti Overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ConfettiOverlay() {
    val particles = remember {
        List(60) {
            floatArrayOf(
                Random.nextFloat() * 1080f,
                Random.nextFloat() * -200f,
                Random.nextFloat() * 6f - 3f,
                Random.nextFloat() * 10f + 4f,
                (Random.nextFloat() * 12f + 5f)
            )
        }
    }
    val tick = remember { Animatable(0f) }
    LaunchedEffect(Unit) { tick.animateTo(1f, tween(3000, easing = LinearEasing)) }

    Canvas(modifier = Modifier.fillMaxSize()) {
        particles.forEachIndexed { idx, p ->
            val col  = confettiColors[idx % confettiColors.size].copy(alpha = 0.8f)
            val x    = (p[0] + p[2] * tick.value * 80f) % size.width
            val yRaw = p[1] + p[3] * tick.value * 80f
            if (yRaw < size.height) {
                drawCircle(color = col, radius = p[4] / 2f, center = Offset(x, yRaw))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Glass Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .background(GlassFill, RoundedCornerShape(18.dp))
            .border(1.dp, GlassEdge, RoundedCornerShape(18.dp))
    ) { content() }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun formatCooldown(ms: Long): String {
    val sec = (ms / 1000L).toInt()
    val m   = sec / 60
    val s   = sec % 60
    return if (m > 0) "${m}m ${"%02d".format(s)}s" else "${s}s"
}

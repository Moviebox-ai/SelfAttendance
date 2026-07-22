package com.aaryo.selfattendance.ui.spin

import android.app.Activity
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aaryo.selfattendance.ads.AdsController
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.aaryo.selfattendance.data.repository.RewardRepository
import com.aaryo.selfattendance.utils.SpinSoundManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.aaryo.selfattendance.utils.LocaleManager
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
//  Constants
// ─────────────────────────────────────────────────────────────────────────────

private const val MAX_DAILY_SPINS   = 5
private const val SPIN_COOLDOWN_MS  = 60_000L   // 1 minute between spins

// ─────────────────────────────────────────────────────────────────────────────
//  Data
// ─────────────────────────────────────────────────────────────────────────────

data class SpinReward(
    val emoji: String,
    val label: String,
    val color: Color
)

private val REWARDS = listOf(
    SpinReward("🏆", "Best Performer!", Color(0xFFFFD700)),
    SpinReward("🔥", "+1 Bonus Streak", Color(0xFFFF6D00)),
    SpinReward("⭐", "Star Employee",   Color(0xFF9C27B0)),
    SpinReward("💪", "Hard Worker",     Color(0xFF2196F3)),
    SpinReward("🎯", "On Target!",      Color(0xFF00BCD4)),
    SpinReward("🌟", "Superstar!",      Color(0xFFE91E63)),
    SpinReward("✅", "Punctual Pro",    Color(0xFF4CAF50)),
    SpinReward("🎖️", "Dedication+",    Color(0xFFFF5722)),
)

// ─────────────────────────────────────────────────────────────────────────────
//  DailySpinDialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun DailySpinDialog(onDismiss: () -> Unit) {
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

    // Reset counts at day change
    var spinsUsed by remember {
        mutableIntStateOf(
            if (isNewDay) 0.also {
                prefs.dailySpinsUsed        = 0
                prefs.lastSpinTimestampMs   = 0L
                prefs.lastSpinDate          = today
            } else prefs.dailySpinsUsed
        )
    }
    var lastSpinMs by remember {
        mutableLongStateOf(if (isNewDay) 0L else prefs.lastSpinTimestampMs)
    }

    // Live cooldown counter (ms remaining)
    var cooldownMs by remember {
        mutableLongStateOf(
            if (lastSpinMs == 0L) 0L
            else maxOf(0L, SPIN_COOLDOWN_MS - (System.currentTimeMillis() - lastSpinMs))
        )
    }
    LaunchedEffect(lastSpinMs) {
        while (true) {
            val remaining = if (lastSpinMs == 0L) 0L
            else maxOf(0L, SPIN_COOLDOWN_MS - (System.currentTimeMillis() - lastSpinMs))
            cooldownMs = remaining
            if (remaining <= 0L) break
            delay(500L)
        }
    }

    val spinsLeft = MAX_DAILY_SPINS - spinsUsed
    var spinning  by remember { mutableStateOf(false) }
    var adLoading by remember { mutableStateOf(false) }
    var wonReward by remember { mutableStateOf<SpinReward?>(null) }

    val canSpin   = spinsLeft > 0 && cooldownMs <= 0L && !spinning && !adLoading

    val rotation = remember { Animatable(0f) }

    // ── Execute spin (called AFTER ad reward) ────────────────────────────────
    fun executeSpin() {
        spinning   = true
        adLoading  = false
        val targetIndex = REWARDS.indices.random()
        val segDeg      = 360f / REWARDS.size
        val targetAngle = 360f - (targetIndex * segDeg + segDeg / 2f)
        val landAngle   = ((targetAngle - rotation.value % 360f) + 360f) % 360f
        val totalSpin   = 1440f + landAngle

        // Start ticking sounds — mirrors the 3.5 s wheel animation duration
        soundMgr.startSpinSound(scope, 3500L)

        scope.launch {
            rotation.animateTo(
                targetValue   = rotation.value + totalSpin,
                animationSpec = tween(3500, easing = FastOutSlowInEasing)
            )
            val now              = System.currentTimeMillis()

            // Stop ticking, play win jingle (all rewards here are positive)
            soundMgr.stopSpinSound()
            soundMgr.playWinSound()

            spinning             = false
            wonReward            = REWARDS[targetIndex]
            spinsUsed            += 1
            lastSpinMs           = now
            prefs.dailySpinsUsed = spinsUsed
            prefs.lastSpinDate   = today
            prefs.lastSpinIndex  = targetIndex
            prefs.lastSpinTimestampMs = now

            // BUG FIX: Sync spin metadata to Firebase so it persists across
            // device reinstalls and multi-device logins.
            RewardRepository.saveSpinMetadata(
                lastSpinDate        = today,
                dailySpinsUsed      = spinsUsed,
                lastSpinTimestampMs = now,
                lastSpinIndex       = targetIndex
            )
        }
    }

    // ── Watch ad then spin ───────────────────────────────────────────────────
    fun onWatchAdAndSpin() {
        if (!canSpin) return
        adLoading = true

        if (!AdsController.isAdsEnabled || !AdsController.mobileAdsReady) {
            // Ads disabled/not ready — spin directly (graceful fallback)
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
                    snack.showSnackbar("Ad abhi load ho rahi hai, thodi der baad try karo 🔄")
                }
            }
        )
    }

    // ── UI ───────────────────────────────────────────────────────────────────
    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            containerColor = Color.Transparent
        ) { _ ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.93f)
                    .background(Color(0xFF0D0D1A), RoundedCornerShape(28.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    // Title
                    Text(
                        "🎰 Daily Spin",
                        fontSize   = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = Color.White
                    )
                    Text(
                        "Ad dekho, spin karo aur reward pao!",
                        color    = Color.White.copy(0.6f),
                        fontSize = 13.sp
                    )

                    Spacer(Modifier.height(14.dp))

                    // Spin counter dots
                    SpinDots(total = MAX_DAILY_SPINS, used = spinsUsed)

                    Spacer(Modifier.height(16.dp))

                    // ── Wheel + pointer stack ────────────────────────────────
                    Box(
                        modifier         = Modifier.size(280.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Rotating wheel
                        Box(
                            modifier = Modifier
                                .size(260.dp)
                                .rotate(rotation.value),
                            contentAlignment = Alignment.Center
                        ) {
                            SpinWheel(segments = REWARDS)

                            // Center hub
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(0xFF0D0D1A), CircleShape)
                                    .clip(CircleShape),
                                contentAlignment = Alignment.Center
                            ) { Text("⚡", fontSize = 18.sp) }
                        }

                        // ── POINTER AT TOP (does NOT rotate with wheel) ──────
                        Box(
                            modifier         = Modifier
                                .size(280.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            GoldPointer(modifier = Modifier.offset(y = 2.dp))
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── Result / cooldown / spin button ──────────────────────
                    when {
                        wonReward != null -> {
                            wonReward?.let { RewardCard(it) }
                            Spacer(Modifier.height(14.dp))

                            if (spinsLeft > 0) {
                                CooldownInfo(cooldownMs = cooldownMs)
                                Spacer(Modifier.height(10.dp))
                            }

                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (spinsLeft > 0) {
                                    // Spin again button
                                    Button(
                                        onClick  = { wonReward = null },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape    = RoundedCornerShape(12.dp),
                                        colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF)),
                                        enabled  = cooldownMs <= 0L
                                    ) {
                                        Text(
                                            if (cooldownMs > 0L) "⏳ Wait" else "🔄 Spin Again",
                                            fontWeight = FontWeight.Bold, fontSize = 14.sp
                                        )
                                    }
                                }
                                Button(
                                    onClick  = onDismiss,
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape    = RoundedCornerShape(12.dp),
                                    colors   = ButtonDefaults.buttonColors(containerColor = wonReward?.color ?: Color.Transparent)
                                ) {
                                    Text("Claim 🎉", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }

                        spinsLeft <= 0 -> {
                            AllSpinsDoneCard()
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick  = onDismiss,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape    = RoundedCornerShape(12.dp)
                            ) { Text("Kal Wapas Aao ✅", color = Color.White.copy(0.7f)) }
                        }

                        else -> {
                            // Normal spin available / cooldown active
                            if (cooldownMs > 0L) {
                                CooldownInfo(cooldownMs = cooldownMs)
                                Spacer(Modifier.height(10.dp))
                            }

                            Button(
                                onClick  = { onWatchAdAndSpin() },
                                enabled  = canSpin,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape    = RoundedCornerShape(14.dp),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor         = Color(0xFF7C4DFF),
                                    disabledContainerColor = Color(0xFF3D3D5C)
                                )
                            ) {
                                when {
                                    spinning || adLoading -> {
                                        Box(
                                            modifier         = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                color       = Color.White,
                                                modifier    = Modifier.size(22.dp),
                                                strokeWidth = 2.5.dp
                                            )
                                        }
                                    }
                                    cooldownMs > 0L -> {
                                        Text(
                                            "⏳ ${formatCooldown(cooldownMs)} mein unlock",
                                            fontWeight = FontWeight.Bold,
                                            fontSize   = 15.sp
                                        )
                                    }
                                    else -> {
                                        Text("📺  Ad Dekho & Spin Karo", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                }
                            }

                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Ek rewarded ad dekhne ke baad spin unlock hoga",
                                fontSize  = 11.sp,
                                color     = Color.White.copy(0.35f),
                                textAlign = TextAlign.Center,
                                modifier  = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(4.dp))
                            TextButton(onClick = onDismiss) {
                                Text("Baad mein", color = Color.White.copy(0.35f), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Gold Pointer  (triangle pointing DOWN, sits at top of wheel)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GoldPointer(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier.size(32.dp, 26.dp)
    ) {
        val w = size.width
        val h = size.height

        // Drop shadow
        val shadow = Path().apply {
            moveTo(w / 2f, h + 3f)
            lineTo(-3f, -3f)
            lineTo(w + 3f, -3f)
            close()
        }
        drawPath(shadow, Color.Black.copy(0.5f))

        // Gold triangle body (pointing down ▼)
        val tri = Path().apply {
            moveTo(w / 2f, h)        // tip at bottom center
            lineTo(2f, 2f)            // top-left corner
            lineTo(w - 2f, 2f)        // top-right corner
            close()
        }
        drawPath(
            path  = tri,
            brush = Brush.verticalGradient(
                listOf(Color(0xFFFFF176), Color(0xFFFFD700), Color(0xFFB8860B))
            )
        )
        // White sheen edge
        drawPath(tri, Color.White.copy(0.4f), style = Stroke(1.5f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Spin Dot Counter
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SpinDots(total: Int, used: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(total) { i ->
                val done = i < used
                Box(
                    modifier = Modifier
                        .size(if (done) 10.dp else 14.dp)
                        .background(
                            if (done) Color.White.copy(0.25f) else Color(0xFF7C4DFF),
                            CircleShape
                        )
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "$used / $total spins used today",
            fontSize = 11.sp,
            color    = Color.White.copy(0.4f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Cooldown info bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CooldownInfo(cooldownMs: Long) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(0.06f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text("⏳", fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            "Agla spin ${formatCooldown(cooldownMs)} mein",
            fontSize   = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color      = Color(0xFFFFD700)
        )
    }
}

private fun formatCooldown(ms: Long): String {
    val sec = (ms / 1000L).toInt()
    val m   = sec / 60
    val s   = sec % 60
    return if (m > 0) "${m}m ${"%02d".format(s)}s" else "${s}s"
}

// ─────────────────────────────────────────────────────────────────────────────
//  All spins done card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AllSpinsDoneCard() {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1B263B), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("✅", fontSize = 36.sp)
        Spacer(Modifier.height(6.dp))
        Text("Aaj ke saare 5 spins khatam!", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Kal subah phir se 5 naye spins milenge", fontSize = 12.sp, color = Color.White.copy(0.5f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  SpinWheel  (same arc-based drawing, no changes needed here)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SpinWheel(segments: List<SpinReward>) {
    val count = segments.size
    val sweep = 360f / count

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            segments.forEachIndexed { i, reward ->
                val startAngle = i * sweep - 90f
                drawArc(
                    color      = reward.color.copy(alpha = 0.85f),
                    startAngle = startAngle,
                    sweepAngle = sweep - 1f,
                    useCenter  = true,
                    topLeft    = Offset(center.x - radius, center.y - radius),
                    size       = Size(radius * 2, radius * 2)
                )
                val lineRad = Math.toRadians(startAngle.toDouble())
                drawLine(
                    color       = Color.White.copy(alpha = 0.25f),
                    start       = center,
                    end         = Offset(
                        center.x + (radius * cos(lineRad)).toFloat(),
                        center.y + (radius * sin(lineRad)).toFloat()
                    ),
                    strokeWidth = 2f
                )
            }
            drawCircle(color = Color.White.copy(0.25f), radius = radius, center = center, style = Stroke(3f))
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val radius = minOf(maxWidth, maxHeight) / 2
            val textR  = radius * 0.65f

            segments.forEachIndexed { i, reward ->
                val midAngle = i * sweep - 90f + sweep / 2f
                val rad      = Math.toRadians(midAngle.toDouble())
                val ox       = (textR.value * cos(rad)).toFloat()
                val oy       = (textR.value * sin(rad)).toFloat()

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .offset(x = radius + ox.dp - 18.dp, y = radius + oy.dp - 18.dp)
                        .rotate(midAngle + 90f),
                    contentAlignment = Alignment.Center
                ) { Text(reward.emoji, fontSize = 20.sp) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Reward card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RewardCard(reward: SpinReward) {
    Card(
        shape    = RoundedCornerShape(18.dp),
        colors   = CardDefaults.cardColors(reward.color.copy(0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(reward.emoji, fontSize = 44.sp)
            Spacer(Modifier.height(4.dp))
            Text(reward.label, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = reward.color)
            Text("Aaj ka title earn kiya! 🎉", color = Color.White.copy(0.6f), fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

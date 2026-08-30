package com.aaryo.selfattendance.ui.rewards

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aaryo.selfattendance.R
import com.aaryo.selfattendance.utils.SpinSoundManager
import kotlinx.coroutines.delay

// Local color constants (matching RewardsScreen palette)
private val RoyalGold      = Color(0xFFFFD700)
private val RoyalGoldDark  = Color(0xFFB8860B)
private val RoyalGoldLight = Color(0xFFFFF176)
private val NavyBg         = Color(0xFF0D1B2A)
private val TextWhite      = Color(0xFFFFFFFF)
private val TextMuted      = Color(0xFF8899AA)

// ─────────────────────────────────────────────────────────────────────────────
//  CoinFlipDialog — animated popup shown when user earns AX coins
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CoinFlipDialog(
    coinsWon : Int,
    label    : String,
    onClaim  : () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // ── Coin collect sound + haptic — plays as soon as the dialog appears ────
    LaunchedEffect(Unit) {
        SpinSoundManager.playCoinCollectSound()
        SpinSoundManager.playCoinCollectHaptic(context)
    }

    // ── 3-D coin flip on Y-axis: 0 → 1080° (3 full spins) ───────────────────
    val flipAngle = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        flipAngle.animateTo(
            targetValue   = 1080f,
            animationSpec = tween(1600, easing = CubicBezierEasing(0.2f, 0.8f, 0.3f, 1.0f))
        )
    }

    // ── Animated counter 0 → coinsWon ────────────────────────────────────────
    var displayedCoins by remember { mutableStateOf(0) }
    LaunchedEffect(coinsWon) {
        delay(400L)
        val steps = 20
        repeat(steps) { i ->
            displayedCoins = ((i + 1) * coinsWon / steps)
            delay(40L)
        }
        displayedCoins = coinsWon
    }

    // ── Pulsing glow behind coin ──────────────────────────────────────────────
    val glowPulse = rememberInfiniteTransition(label = "coinGlow")
    val glowAlpha by glowPulse.animateFloat(
        initialValue  = 0.45f,
        targetValue   = 0.90f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // ── Dialog scale-in bounce ────────────────────────────────────────────────
    val dialogScale = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        dialogScale.animateTo(
            targetValue   = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness    = Spring.StiffnessMedium
            )
        )
    }

    // ── Claim button appears after animation ends ─────────────────────────────
    var showButton by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1900L)
        showButton = true
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Box(
            modifier = Modifier
                .scale(dialogScale.value)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF1A1040), Color(0xFF0D0D2B))),
                    RoundedCornerShape(32.dp)
                )
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        listOf(RoyalGold.copy(0.9f), Color(0xFF9B59B6).copy(0.6f), RoyalGold.copy(0.9f))
                    ),
                    shape = RoundedCornerShape(32.dp)
                )
                .padding(28.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier.fillMaxWidth()
            ) {

                // ── Header ────────────────────────────────────────────────────
                Text(
                    "🎉  Congratulations!",
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = TextWhite,
                    textAlign  = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Aapne AX Coins earn kiye!",
                    fontSize  = 13.sp,
                    color     = TextMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))

                // ── Coin + glow ───────────────────────────────────────────────
                Box(
                    contentAlignment = Alignment.Center,
                    modifier         = Modifier.size(160.dp)
                ) {
                    // Radial glow drawn on canvas behind the coin
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFFD700).copy(alpha = glowAlpha * 0.6f),
                                    Color(0xFF9B59B6).copy(alpha = glowAlpha * 0.4f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = size.minDimension * 0.72f
                            ),
                            radius = size.minDimension * 0.72f
                        )
                    }

                    // AX Coin with 3-D Y-axis rotation (coin flip effect)
                    Image(
                        painter            = painterResource(R.drawable.ax_coin),
                        contentDescription = "AX Coin",
                        modifier           = Modifier
                            .size(128.dp)
                            .graphicsLayer {
                                rotationY      = flipAngle.value
                                cameraDistance = 12f * density.density
                            }
                    )
                }

                Spacer(Modifier.height(16.dp))

                // ── Coin amount badge ─────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .background(
                            Brush.horizontalGradient(
                                listOf(RoyalGold.copy(0.15f), Color(0xFF9B59B6).copy(0.15f))
                            ),
                            RoundedCornerShape(20.dp)
                        )
                        .border(
                            1.dp,
                            Brush.horizontalGradient(
                                listOf(RoyalGold.copy(0.6f), Color(0xFF9B59B6).copy(0.4f))
                            ),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 28.dp, vertical = 14.dp)
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter            = painterResource(R.drawable.ax_coin),
                            contentDescription = null,
                            modifier           = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "+$displayedCoins",
                            fontSize   = 34.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color      = RoyalGold
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "AX",
                            fontSize   = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color      = RoyalGold.copy(0.7f)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    label,
                    fontSize  = 13.sp,
                    color     = TextMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(24.dp))

                // ── Claim button (fades in after animation finishes) ───────────
                if (showButton) {
                    Button(
                        onClick        = onClaim,
                        modifier       = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape          = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors         = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(listOf(RoyalGoldDark, RoyalGold, RoyalGoldLight)),
                                    RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "✓  CLAIM REWARD",
                                fontSize      = 15.sp,
                                fontWeight    = FontWeight.ExtraBold,
                                color         = NavyBg,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(52.dp))
                }
            }
        }
    }
}

package com.aaryo.selfattendance.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aaryo.selfattendance.R
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.aaryo.selfattendance.ui.navigation.Routes
import com.aaryo.selfattendance.ui.theme.AppTheme
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

// Splash palette is now read from AppTheme — see SplashContent()
private val ProGold  = Color(0xFFFFD700)
private val ProGold2 = Color(0xFFFFA500)

@Composable
fun SplashScreen(
    navController: NavController,
    notificationStartScreen: String? = null
) {

    val viewModel: SplashViewModel = viewModel()
    val route   by viewModel.route.collectAsStateWithLifecycle()
    val blocked by viewModel.blocked.collectAsStateWithLifecycle()

    var navigated by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.checkAppState() }

    LaunchedEffect(route) {
        if (!navigated && route != null) {
            navigated = true

            // aur notification se aaye hain, toh route mein extra info
            // AppNavGraph tak pahunchti hai notificationStartScreen ke zariye
            navController.navigate(route!!) {
                popUpTo(Routes.SPLASH) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    if (blocked) {
        BlockedDialog()
        return
    }

    val context = LocalContext.current
    val theme   = AppTheme.fromKey(PreferencesManager(context).selectedTheme)
    SplashContent(theme = theme)
}

// ─────────────────────────────────────────────────────────────────
//  Blocked Dialog
// ─────────────────────────────────────────────────────────────────
@Composable
private fun BlockedDialog() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)),
        contentAlignment = Alignment.Center
    ) {
        AlertDialog(
            onDismissRequest  = {},
            confirmButton     = {},
            containerColor    = Color(0xFF1A1A1A),
            titleContentColor = Color.White,
            textContentColor  = Color(0xFFB0B0B0),
            title = { Text("Service Unavailable", fontWeight = FontWeight.Bold) },
            text  = {
                Text(
                    "The app is temporarily unavailable.\nPlease try again later.",
                    textAlign = TextAlign.Center
                )
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────
//  Main Splash Content
// ─────────────────────────────────────────────────────────────────
@Composable
private fun SplashContent(theme: AppTheme) {

    // Theme-derived splash colors
    val SplashBg1     = theme.splashBg1
    val SplashBg2     = theme.splashBg2
    val SplashBg3     = theme.splashBg3
    val SplashAccent  = theme.splashAccent
    val SplashAccent2 = theme.splashAccent2
    val SplashGlow    = theme.splashGlow

    val bgAnim = rememberInfiniteTransition(label = "bg")

    // Background gradient shift
    val bgOffset by bgAnim.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse),
        label = "bgOffset"
    )

    // Particles
    val p1 by bgAnim.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), "p1")
    val p2 by bgAnim.animateFloat(0f, 1f, infiniteRepeatable(tween(6500, easing = LinearEasing)), "p2")
    val p3 by bgAnim.animateFloat(0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing)), "p3")

    // Arc rings
    val arcRot by bgAnim.animateFloat(
        0f, 360f, infiniteRepeatable(tween(3000, easing = LinearEasing)), "arc"
    )
    val arcRot2 by bgAnim.animateFloat(
        360f, 0f, infiniteRepeatable(tween(4500, easing = LinearEasing)), "arc2"
    )

    // Pulse glow around logo
    val pulse by bgAnim.animateFloat(
        initialValue = 0.85f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    // Logo entry animation
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(100); entered = true }

    val logoScale by animateFloatAsState(
        targetValue   = if (entered) 1f else 0.4f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "logoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue   = if (entered) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "logoAlpha"
    )

    // Text entry
    var textReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(450); textReady = true }

    val textAlpha by animateFloatAsState(
        targetValue   = if (textReady) 1f else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "textAlpha"
    )
    val textOffset by animateFloatAsState(
        targetValue   = if (textReady) 0f else 32f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "textOffset"
    )

    // Tagline stagger
    var tagReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(750); tagReady = true }
    val tagAlpha by animateFloatAsState(
        targetValue   = if (tagReady) 1f else 0f,
        animationSpec = tween(600),
        label = "tagAlpha"
    )

    // Shimmer bar
    val shimmer by bgAnim.animateFloat(
        -1f, 2f, infiniteRepeatable(tween(1800, easing = LinearEasing)), "shimmer"
    )

    // ── Layout ─────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBg1)
    ) {

        // Layer 1 — animated gradient background
        Canvas(Modifier.fillMaxSize()) {
            drawAnimatedBackground(bgOffset, SplashBg1, SplashBg2, SplashBg3, SplashAccent, SplashAccent2)
        }

        // Layer 2 — floating particle orbs
        Canvas(Modifier.fillMaxSize()) {
            drawParticle(p1, size.width * 0.12f, size.height * 0.22f, 100f, SplashAccent.copy(0.13f),  65f)
            drawParticle(p2, size.width * 0.82f, size.height * 0.15f, 140f, SplashAccent2.copy(0.09f), 85f)
            drawParticle(p3, size.width * 0.62f, size.height * 0.74f, 110f, SplashAccent.copy(0.11f),  55f)
            drawParticle(p1, size.width * 0.88f, size.height * 0.58f,  80f, SplashAccent2.copy(0.08f), 45f)
        }

        // Layer 3 — center content
        Column(
            modifier            = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // ── Logo box ─────────────────────────────────────
            Box(
                modifier        = Modifier.size(180.dp),
                contentAlignment= Alignment.Center
            ) {
                // Outer pulse glow
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(pulse * logoScale)
                        .alpha(logoAlpha * 0.5f)
                ) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(SplashGlow.copy(0.4f), Color.Transparent),
                            radius = size.minDimension / 1.5f
                        ),
                        radius = size.minDimension / 1.5f
                    )
                }

                // Rotating arc 1
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(logoScale)
                        .alpha(logoAlpha * 0.9f)
                ) {
                    rotate(arcRot) {
                        drawArc(
                            brush      = Brush.sweepGradient(listOf(SplashAccent, Color.Transparent, SplashAccent2)),
                            startAngle = 0f, sweepAngle = 270f, useCenter = false,
                            topLeft    = Offset(6f, 6f),
                            size       = androidx.compose.ui.geometry.Size(size.width - 12f, size.height - 12f),
                            style      = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.5f, cap = StrokeCap.Round)
                        )
                    }
                }

                // Rotating arc 2 (counter)
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(logoScale * 0.80f)
                        .alpha(logoAlpha * 0.55f)
                ) {
                    rotate(arcRot2) {
                        drawArc(
                            brush      = Brush.sweepGradient(listOf(ProGold.copy(0.8f), Color.Transparent, ProGold2.copy(0.6f))),
                            startAngle = 60f, sweepAngle = 210f, useCenter = false,
                            topLeft    = Offset(6f, 6f),
                            size       = androidx.compose.ui.geometry.Size(size.width - 12f, size.height - 12f),
                            style      = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f, cap = StrokeCap.Round)
                        )
                    }
                }

                // ── Actual app logo image ──────────────────
                Image(
                    painter           = painterResource(id = R.drawable.splash_logo),
                    contentDescription= "Self Attendance Pro Logo",
                    modifier          = Modifier
                        .size(120.dp)
                        .scale(logoScale)
                        .alpha(logoAlpha)
                        .clip(RoundedCornerShape(28.dp))
                )
            }

            Spacer(Modifier.height(32.dp))

            // ── App name row ─────────────────────────────────
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement= Arrangement.Center,
                modifier             = Modifier
                    .alpha(textAlpha)
                    .offset(y = textOffset.dp)
            ) {
                Text(
                    text          = "Self Attendance",
                    fontSize      = 30.sp,
                    fontWeight    = FontWeight.Bold,
                    color         = Color.White,
                    letterSpacing = 0.8.sp
                )

                Spacer(Modifier.width(10.dp))

                // PRO badge
                Box(
                    modifier         = Modifier
                        .background(
                            brush  = Brush.linearGradient(listOf(ProGold, ProGold2)),
                            shape  = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = "PRO",
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = theme.splashBg1,
                        letterSpacing = 1.5.sp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Tagline ───────────────────────────────────────
            Text(
                text          = "Track your work, own your time",
                fontSize      = 14.sp,
                color         = theme.splashTagline,
                letterSpacing = 0.5.sp,
                modifier      = Modifier.alpha(tagAlpha)
            )

            Spacer(Modifier.height(6.dp))

            // ── Subtle divider line ───────────────────────────
            Canvas(
                modifier = Modifier
                    .width(120.dp)
                    .height(1.dp)
                    .alpha(tagAlpha * 0.5f)
            ) {
                drawLine(
                    brush      = Brush.horizontalGradient(listOf(Color.Transparent, SplashAccent, Color.Transparent)),
                    start      = Offset(0f, 0f),
                    end        = Offset(size.width, 0f),
                    strokeWidth= 1f
                )
            }
        }

        // Layer 4 — shimmer progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
                .padding(horizontal = 52.dp)
        ) {
            ShimmerBar(shimmer = shimmer, alpha = tagAlpha, accentColor = SplashAccent, trackColor = theme.splashTrack)
        }

        // Version
        Text(
            text     = "v${com.aaryo.selfattendance.BuildConfig.VERSION_NAME}",
            fontSize = 11.sp,
            color    = theme.splashVersion,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .alpha(tagAlpha)
        )
    }
}

// ─────────────────────────────────────────────────────────────────
//  Shimmer progress bar
// ─────────────────────────────────────────────────────────────────
@Composable
private fun ShimmerBar(shimmer: Float, alpha: Float, accentColor: Color, trackColor: Color) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .alpha(alpha)
    ) {
        drawRoundRect(
            color        = trackColor,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f)
        )
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, accentColor.copy(0.9f), ProGold.copy(0.5f), Color.Transparent),
                startX = size.width * (shimmer - 0.4f),
                endX   = size.width * (shimmer + 0.4f)
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────
//  Canvas helpers
// ─────────────────────────────────────────────────────────────────

private fun DrawScope.drawAnimatedBackground(
    t: Float, bg1: Color, bg2: Color, bg3: Color, accent: Color, accent2: Color
) {
    drawRect(brush = Brush.verticalGradient(listOf(bg1, bg2, bg3)))
    drawCircle(
        brush  = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.15f + t * 0.08f), Color.Transparent),
            center = Offset(size.width * 0.3f, size.height * (0.3f + t * 0.1f)),
            radius = size.width * 0.65f
        ),
        radius = size.width * 0.65f,
        center = Offset(size.width * 0.3f, size.height * (0.3f + t * 0.1f))
    )
    drawCircle(
        brush  = Brush.radialGradient(
            colors = listOf(accent2.copy(alpha = 0.10f + (1f - t) * 0.06f), Color.Transparent),
            center = Offset(size.width * 0.75f, size.height * (0.7f - t * 0.1f)),
            radius = size.width * 0.55f
        ),
        radius = size.width * 0.55f,
        center = Offset(size.width * 0.75f, size.height * (0.7f - t * 0.1f))
    )
}

private fun DrawScope.drawParticle(t: Float, baseX: Float, baseY: Float, radius: Float, color: Color, travel: Float) {
    val y = baseY + kotlin.math.sin(t * 2 * Math.PI.toFloat()) * travel
    drawCircle(
        brush  = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = Offset(baseX, y),
            radius = radius
        ),
        radius = radius,
        center = Offset(baseX, y)
    )
}

package com.aaryo.selfattendance.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.aaryo.selfattendance.data.repository.RewardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
//  Data
// ─────────────────────────────────────────────────────────────────────────────

data class PremiumFeature(
    val id      : String,
    val name    : String,
    val emoji   : String,
    val desc    : String
)

// is now its own individual premium unlock, purchased separately from the
// Theme Picker dialog in SettingsScreen.kt (see ThemePickerDialog).
val ALL_PREMIUM_FEATURES = listOf(
    PremiumFeature("restore", "Restore Data",          "☁️", "Cloud backup se data restore karo"),
    PremiumFeature("reset",   "Reset Attendance",      "🗑️", "Saara attendance data clear karo"),
    PremiumFeature("pdf",     "Export PDF Report",     "📄", "Full attendance PDF download karo"),
    PremiumFeature("salary",  "Generate Salary Slip",  "💼", "Monthly salary PDF generate karo"),
)

// Random unlock duration: 1, 2, 3, 4, or 5 days
private val DURATION_OPTIONS_MS = listOf(1L, 2L, 3L, 4L, 5L).map { it * 24L * 3600L * 1000L }

private fun randomDurationMs() = DURATION_OPTIONS_MS[Random.nextInt(DURATION_OPTIONS_MS.size)]

// Each unlock (and every re-unlock afterwards) costs a fresh random amount,
// so the price is never fixed and can't be memorized/gamed.
private fun randomCostAx() = Random.nextInt(450, 1001) // 450..1000 inclusive

// ─────────────────────────────────────────────────────────────────────────────
//  Colors
// ─────────────────────────────────────────────────────────────────────────────

private val Gold       = Color(0xFFFFD700)
private val GoldDark   = Color(0xFFB8860B)
private val GoldLight  = Color(0xFFFFF176)
private val NavyBg     = Color(0xFF0D1B2A)
private val DarkSlate  = Color(0xFF1B263B)
private val LockedGray = Color(0xFF4A5568)

// ─────────────────────────────────────────────────────────────────────────────
//  Countdown helper
// ─────────────────────────────────────────────────────────────────────────────

private fun formatRemaining(ms: Long): String {
    if (ms <= 0L) return "Expired"
    val totalSec = ms / 1000L
    val days     = totalSec / 86400L
    val hours    = (totalSec % 86400L) / 3600L
    val mins     = (totalSec % 3600L) / 60L
    val secs     = totalSec % 60L
    return if (days > 0)
        "${days}d ${"%02d".format(hours)}:${"%02d".format(mins)}:${"%02d".format(secs)}"
    else
        "${"%02d".format(hours)}:${"%02d".format(mins)}:${"%02d".format(secs)}"
}

// ─────────────────────────────────────────────────────────────────────────────
//  Main composable — called from SettingsScreen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PremiumFeaturesSection(
    restoreUntilMs     : Long,
    resetUntilMs       : Long,
    pdfUntilMs         : Long,
    salaryUntilMs      : Long,
    coinBalance        : Int,
    prefManager        : PreferencesManager,
    onUnlock           : (featureId: String, newUntilMs: Long, newCoinBalance: Int) -> Unit,
    onNavigateToRewards: () -> Unit = {}
) {
    // inside the composable. A raw CoroutineScope is never cancelled when the composable
    // leaves composition, causing a coroutine leak on every unlock button press.
    val firebaseScope = rememberCoroutineScope()

    // Tick every second → forces countdown recompose
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) { delay(1000L); tick++ }
    }

    val nowMs  = System.currentTimeMillis() + tick * 0L
    val exMap  = mapOf(
        "restore" to restoreUntilMs,
        "reset"   to resetUntilMs,
        "pdf"     to pdfUntilMs,
        "salary"  to salaryUntilMs,
    )

    var unlockTarget by remember { mutableStateOf<PremiumFeature?>(null) }
    // Freshly rolled each time a feature's unlock dialog is opened (initial
    // unlock or any subsequent re-unlock) — never a fixed price.
    var unlockCost   by remember { mutableIntStateOf(0) }

    // ── Unlock dialog ─────────────────────────────────────────────────────────
    unlockTarget?.let { feature ->
        val now    = System.currentTimeMillis()
        val alreadyUnlocked = (exMap[feature.id] ?: 0L) > now
        val canAfford = coinBalance >= unlockCost

        Dialog(
            onDismissRequest = { unlockTarget = null },
            properties       = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSlate, RoundedCornerShape(24.dp))
                    .border(
                        1.dp,
                        Brush.linearGradient(listOf(Gold.copy(0.7f), GoldDark.copy(0.4f))),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    Text(feature.emoji, fontSize = 44.sp)
                    Spacer(Modifier.height(8.dp))

                    Text(
                        feature.name,
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = Color.White,
                        textAlign  = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        feature.desc,
                        fontSize  = 12.sp,
                        color     = Color.White.copy(0.55f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(14.dp))

                    // Info box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NavyBg, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Cost", fontSize = 12.sp, color = Color.White.copy(0.55f))
                                Text("$unlockCost AX", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Gold)
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Your Balance", fontSize = 12.sp, color = Color.White.copy(0.55f))
                                Text("$coinBalance AX", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    color = if (canAfford) Color(0xFF06D6A0) else Color(0xFFEF233C))
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Duration", fontSize = 12.sp, color = Color.White.copy(0.55f))
                                Text("Random 1–5 days 🎲", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(0.8f))
                            }
                        }
                    }

                    if (alreadyUnlocked) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "✅ Ye feature already unlocked hai!\nFir se unlock karne par timer extend ho jayega.",
                            fontSize  = 12.sp,
                            color     = Color(0xFF06D6A0),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    if (canAfford) {
                        Button(
                            onClick = {
                                val dur    = randomDurationMs()
                                val newMs  = System.currentTimeMillis() + dur
                                val newBal = coinBalance - unlockCost
                                when (feature.id) {
                                    "restore" -> prefManager.premRestoreUnlockUntilMs     = newMs
                                    "reset"   -> prefManager.premResetUnlockUntilMs       = newMs
                                    "pdf"     -> prefManager.premPdfExportUnlockUntilMs   = newMs
                                    "salary"  -> prefManager.premSalarySlipUnlockUntilMs  = newMs
                                }
                                prefManager.coinBalance = newBal
                                onUnlock(feature.id, newMs, newBal)
                                unlockTarget = null
                                // atomic delta (-costAx) instead of an absolute balance, so a
                                // concurrent write from another device/session can't be lost.
                                firebaseScope.launch(Dispatchers.IO) {
                                    runCatching {
                                        RewardRepository.savePremiumUnlocks(
                                            coinsDelta     = -unlockCost,
                                            restoreUntilMs = prefManager.premRestoreUnlockUntilMs,
                                            resetUntilMs   = prefManager.premResetUnlockUntilMs,
                                            pdfUntilMs     = prefManager.premPdfExportUnlockUntilMs,
                                            salaryUntilMs  = prefManager.premSalarySlipUnlockUntilMs
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape    = RoundedCornerShape(14.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = Gold)
                        ) {
                            Text(
                                "🔓 Unlock  $unlockCost AX",
                                fontWeight = FontWeight.ExtraBold,
                                color      = NavyBg,
                                fontSize   = 14.sp
                            )
                        }
                    } else {
                        Column(
                            modifier            = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Coins kam hain! Pehle spin wheel se coins kamao.",
                                fontSize  = 12.sp,
                                color     = Color(0xFFEF233C),
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    unlockTarget = null
                                    onNavigateToRewards()
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape    = RoundedCornerShape(14.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A86FF))
                            ) {
                                Text(
                                    "🎰  Spin Wheel Pe Jao",
                                    fontWeight = FontWeight.ExtraBold,
                                    color      = Color.White,
                                    fontSize   = 14.sp
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick  = { unlockTarget = null },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp)
                    ) { Text("Cancel", color = Color.White.copy(0.7f)) }
                }
            }
        }
    }

    // ── Section UI ────────────────────────────────────────────────────────────
    Column(modifier = Modifier.fillMaxWidth()) {

        // Header row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        Brush.radialGradient(listOf(Gold.copy(0.4f), Color.Transparent)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) { Text("⭐", fontSize = 15.sp) }
            Spacer(Modifier.width(8.dp))
            Text(
                "Premium Features",
                fontSize   = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = Gold
            )
            Spacer(Modifier.weight(1f))
            // Coin balance chip
            Box(
                modifier = Modifier
                    .background(Gold.copy(0.12f), RoundedCornerShape(50.dp))
                    .border(1.dp, Gold.copy(0.5f), RoundedCornerShape(50.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text("⚡ $coinBalance AX", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Gold)
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "Spin wheel se coins kamao → features unlock karo",
            fontSize = 11.sp,
            color    = Color.White.copy(0.45f)
        )

        Spacer(Modifier.height(10.dp))

        // Feature cards
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ALL_PREMIUM_FEATURES.forEach { feature ->
                val untilMs  = exMap[feature.id] ?: 0L
                val isActive = untilMs > nowMs
                val remMs    = if (isActive) untilMs - nowMs else 0L

                PremiumFeatureRow(
                    feature   = feature,
                    isActive  = isActive,
                    remMs     = remMs,
                    onUnlock  = {
                        // Roll a fresh random price (450–1000 AX) every time —
                        // including re-unlocks of an already-unlocked feature.
                        unlockCost   = randomCostAx()
                        unlockTarget = feature
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Single feature row card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PremiumFeatureRow(
    feature  : PremiumFeature,
    isActive : Boolean,
    remMs    : Long,
    onUnlock : () -> Unit
) {
    val bgColor     = if (isActive) Color(0xFF0D2B1A) else Color(0xFF1A1A2E)
    val borderColor = if (isActive) Color(0xFF06D6A0).copy(0.5f) else Color(0xFF2D3748)
    val iconTint    = if (isActive) Color(0xFF06D6A0) else LockedGray

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(14.dp))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Emoji + lock indicator
        Box(
            modifier         = Modifier.size(42.dp)
                .background(
                    if (isActive) Color(0xFF06D6A0).copy(0.15f) else LockedGray.copy(0.1f),
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(feature.emoji, fontSize = 20.sp)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                feature.name,
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color      = if (isActive) Color.White else Color.White.copy(0.7f)
            )
            if (isActive) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LockOpen, null, tint = Color(0xFF06D6A0), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "Unlocked · ${formatRemaining(remMs)} bacha",
                        fontSize = 11.sp,
                        color    = Color(0xFF06D6A0)
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        // Unlock / Extend button
        SmallButton(
            text    = if (isActive) "Extend" else "Unlock",
            isGold  = !isActive,
            onClick = onUnlock
        )
    }
}

@Composable
private fun SmallButton(text: String, isGold: Boolean, onClick: () -> Unit) {
    Button(
        onClick        = onClick,
        shape          = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier       = Modifier.height(34.dp),
        colors         = ButtonDefaults.buttonColors(
            containerColor = if (isGold) Gold else Color(0xFF2D3748)
        )
    ) {
        Text(
            text,
            fontSize   = 12.sp,
            fontWeight = FontWeight.Bold,
            color      = if (isGold) NavyBg else Color(0xFF06D6A0)
        )
    }
}

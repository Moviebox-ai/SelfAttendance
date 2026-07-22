package com.aaryo.selfattendance.ui.referral

import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.aaryo.selfattendance.data.repository.ReferralEntry
import com.aaryo.selfattendance.data.repository.ReferralRepository
import com.aaryo.selfattendance.data.repository.RewardRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

private val NavyBg        = Color(0xFF0D1B2A)
private val DarkSlate     = Color(0xFF1B263B)
private val RoyalGold     = Color(0xFFFFD700)
private val RoyalGoldDark = Color(0xFFB8860B)
private val PremiumBlue   = Color(0xFF3A86FF)
private val SuccessGreen  = Color(0xFF06D6A0)
private val TextWhite     = Color(0xFFFFFFFF)
private val TextMuted     = Color(0xFF8899AA)
private val GlassFill     = Color(0x14FFFFFF)
private val GlassEdge     = Color(0x33FFFFFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferAndEarnScreen(navController: NavController) {
    val context   = LocalContext.current
    val prefs     = remember { PreferencesManager(context) }
    val scope     = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val snack     = remember { SnackbarHostState() }

    val myUid      = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val myCode     = myUid.take(8).uppercase()
    val isLoggedIn = myUid.isNotBlank()

    var referrals       by remember { mutableStateOf<List<ReferralEntry>>(emptyList()) }
    var loading         by remember { mutableStateOf(true) }
    var referralInput   by remember { mutableStateOf("") }
    var submitting      by remember { mutableStateOf(false) }
    var alreadyReferred by remember { mutableStateOf(prefs.hasEnteredReferralCode) }
    var collectLoading  by remember { mutableStateOf(false) }
    var pendingRewards  by remember { mutableStateOf(0) }

    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val shimmerX by shimmer.animateFloat(
        -1f, 2f,
        infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "sx"
    )

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) { loading = false; return@LaunchedEffect }
        loading = true
        try {
            referrals = ReferralRepository.loadMyReferrals()
            alreadyReferred = prefs.hasEnteredReferralCode || ReferralRepository.isAlreadyReferred()

            val pending = referrals.count { !it.rewardPaid && it.consecutiveDays >= 5 }
            pendingRewards = pending * 450
        } catch (e: Exception) {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e)
        }
        loading = false
    }

    Scaffold(
        containerColor    = NavyBg,
        snackbarHost      = { SnackbarHost(snack) },
        topBar            = {
            TopAppBar(
                title = {
                    Text(
                        "Refer & Earn",
                        fontWeight = FontWeight.Bold,
                        color      = TextWhite
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, null, tint = TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBg)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NavyBg)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Hero Banner ───────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF1A237E), Color(0xFF4A148C), Color(0xFF880E4F)))
                    )
                    .border(1.dp, GlassEdge, RoundedCornerShape(20.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.EmojiEvents,
                        null,
                        tint     = RoyalGold,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Friend Refer Karo,\nCoins Kamao!",
                        fontSize    = 22.sp,
                        fontWeight  = FontWeight.ExtraBold,
                        color       = TextWhite,
                        textAlign   = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Jab aapka friend 5 din lagatar app use kare\nto aapko milenge 450 AX Coins!",
                        fontSize  = 13.sp,
                        color     = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── How it works ─────────────────────────────────────────────────
            HowItWorksCard()

            Spacer(Modifier.height(20.dp))

            // ── My Referral Code ─────────────────────────────────────────────
            if (isLoggedIn) {
                GlassCard {
                    Column(
                        modifier            = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Aapka Referral Code",
                            fontSize   = 13.sp,
                            color      = TextMuted,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            RoyalGold.copy(0.12f),
                                            PremiumBlue.copy(0.12f)
                                        ),
                                        startX = shimmerX * 400f,
                                        endX   = (shimmerX + 0.5f) * 400f
                                    )
                                )
                                .border(1.dp, RoyalGold.copy(0.5f), RoundedCornerShape(12.dp))
                                .padding(vertical = 16.dp, horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                myCode,
                                fontSize      = 32.sp,
                                fontWeight    = FontWeight.ExtraBold,
                                color         = RoyalGold,
                                letterSpacing = 6.sp
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(myCode))
                                    scope.launch { snack.showSnackbar("Code copy ho gaya!") }
                                },
                                modifier = Modifier.weight(1f),
                                shape    = RoundedCornerShape(10.dp),
                                border   = ButtonDefaults.outlinedButtonBorder.copy(
                                    width = 1.dp,
                                    brush = Brush.linearGradient(listOf(RoyalGold.copy(0.6f), PremiumBlue.copy(0.4f)))
                                )
                            ) {
                                Icon(Icons.Default.ContentCopy, null, tint = RoyalGold, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Copy", color = TextWhite, fontSize = 13.sp)
                            }

                            Button(
                                onClick = {
                                    val shareText = "Self Attendance Pro app download karo aur apni attendance track karo!\n\n" +
                                            "Mera referral code use karo: $myCode\n\n" +
                                            "Download: https://play.google.com/store/apps/details?id=com.aaryo.selfattendance"
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share via"))
                                },
                                modifier = Modifier.weight(1f),
                                shape    = RoundedCornerShape(10.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = RoyalGold)
                            ) {
                                Icon(Icons.Default.Share, null, tint = NavyBg, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Share", color = NavyBg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Enter a referral code (if not already done) ───────────────
                if (!alreadyReferred) {
                    GlassCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Text(
                                "Kisi ne aapko refer kiya?",
                                fontSize   = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color      = TextWhite
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Apne dost ka referral code daliye — usse coins milenge!",
                                fontSize = 12.sp,
                                color    = TextMuted
                            )
                            Spacer(Modifier.height(12.dp))

                            OutlinedTextField(
                                value         = referralInput,
                                onValueChange = { referralInput = it.uppercase().take(8) },
                                label         = { Text("Referral Code", color = TextMuted) },
                                singleLine    = true,
                                modifier      = Modifier.fillMaxWidth(),
                                colors        = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor   = RoyalGold,
                                    unfocusedBorderColor = GlassEdge,
                                    focusedTextColor     = TextWhite,
                                    unfocusedTextColor   = TextWhite,
                                    cursorColor          = RoyalGold
                                )
                            )

                            Spacer(Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    val code = referralInput.trim()
                                    if (code.length != 8) {
                                        scope.launch { snack.showSnackbar("Code exactly 8 characters ka hona chahiye") }
                                        return@Button
                                    }
                                    if (code == myCode) {
                                        scope.launch { snack.showSnackbar("Apna khud ka code use nahi kar sakte") }
                                        return@Button
                                    }
                                    submitting = true
                                    scope.launch {
                                        try {
                                            val referrerUid = ReferralRepository.resolveCode(code)
                                            if (referrerUid == null) {
                                                snack.showSnackbar("Code nahi mila. Sahi code check karein.")
                                                submitting = false
                                                return@launch
                                            }
                                            val success = ReferralRepository.linkReferral(referrerUid)
                                            if (success) {
                                                prefs.referredByUid          = referrerUid
                                                prefs.hasEnteredReferralCode = true
                                                alreadyReferred              = true
                                                snack.showSnackbar("Referral link ho gaya! Ab app use karte raho.")
                                            } else {
                                                snack.showSnackbar("Code link nahi ho saka. Dobara try karo.")
                                            }
                                        } catch (_: Exception) {
                                            snack.showSnackbar("Error. Internet check karo.")
                                        }
                                        submitting = false
                                    }
                                },
                                enabled  = referralInput.trim().length == 8 && !submitting,
                                modifier = Modifier.fillMaxWidth(),
                                shape    = RoundedCornerShape(10.dp),
                                colors   = ButtonDefaults.buttonColors(
                                    containerColor         = SuccessGreen,
                                    disabledContainerColor = DarkSlate
                                )
                            ) {
                                if (submitting) {
                                    CircularProgressIndicator(
                                        color    = NavyBg,
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    if (submitting) "Link ho raha hai..." else "Referral Code Submit Karo",
                                    color      = NavyBg,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                } else {
                    // Already referred — show confirmation
                    GlassCard {
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                tint     = SuccessGreen,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Referral linked hai!",
                                    fontWeight = FontWeight.Bold,
                                    color      = TextWhite,
                                    fontSize   = 14.sp
                                )
                                Text(
                                    "App 5 din lagatar use karo to dost ko coins milenge",
                                    color    = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                }

                // ── My Referral Stats ─────────────────────────────────────────
                if (loading) {
                    CircularProgressIndicator(color = RoyalGold, modifier = Modifier.padding(16.dp))
                } else {
                    // Pending reward collect button
                    if (pendingRewards > 0) {
                        Button(
                            onClick = {
                                collectLoading = true
                                scope.launch {
                                    try {
                                        val collected = ReferralRepository.collectPendingReferralRewards()
                                        if (collected > 0) {
                                            val prefs2 = PreferencesManager(context)
                                            // BUG FIX: collectPendingReferralRewards() already updated Firebase.
                                            // Read the authoritative Firebase balance back rather than adding to a
                                            // stale local value (which is 0 after app data clear).
                                            val remote = runCatching { RewardRepository.loadRewards() }.getOrNull()
                                            if (remote != null && remote.coinBalance > 0) {
                                                prefs2.coinBalance      = remote.coinBalance
                                                prefs2.totalCoinsEarned = remote.totalCoinsEarned
                                            } else {
                                                prefs2.coinBalance      = prefs2.coinBalance + collected
                                                prefs2.totalCoinsEarned = prefs2.totalCoinsEarned + collected
                                            }
                                            pendingRewards          = 0
                                            referrals = ReferralRepository.loadMyReferrals()
                                            snack.showSnackbar("$collected AX Coins aapke balance mein add ho gaye!")
                                        }
                                    } catch (_: Exception) {
                                        snack.showSnackbar("Reward collect nahi ho saka. Dobara try karo.")
                                    }
                                    collectLoading = false
                                }
                            },
                            enabled  = !collectLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape    = RoundedCornerShape(14.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = RoyalGold)
                        ) {
                            if (collectLoading) {
                                CircularProgressIndicator(color = NavyBg, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                "🎉 Collect $pendingRewards AX Coins",
                                color      = NavyBg,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize   = 15.sp
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // Referral list
                    GlassCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.People, null, tint = PremiumBlue, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Mere Referrals (${referrals.size})",
                                    fontWeight = FontWeight.Bold,
                                    color      = TextWhite,
                                    fontSize   = 15.sp
                                )
                            }

                            if (referrals.isEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Abhi koi referral nahi hai.\nApna code share karo!",
                                    color     = TextMuted,
                                    fontSize  = 13.sp,
                                    textAlign = TextAlign.Center,
                                    modifier  = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                )
                            } else {
                                Spacer(Modifier.height(12.dp))
                                referrals.forEachIndexed { index, entry ->
                                    ReferralEntryRow(entry = entry, index = index + 1)
                                    if (index < referrals.lastIndex) {
                                        HorizontalDivider(
                                            modifier  = Modifier.padding(vertical = 8.dp),
                                            color     = GlassEdge,
                                            thickness = 0.5.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    "Referral feature ke liye login karein.",
                    color    = TextMuted,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.padding(top = 32.dp)
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ReferralEntryRow(entry: ReferralEntry, index: Int) {
    val isComplete = entry.consecutiveDays >= 5
    val streakColor = when {
        isComplete          -> SuccessGreen
        entry.consecutiveDays >= 3 -> RoyalGold
        else                -> TextMuted
    }

    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier         = Modifier
                .size(36.dp)
                .background(
                    if (isComplete) SuccessGreen.copy(0.2f) else PremiumBlue.copy(0.15f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isComplete && entry.rewardPaid) {
                Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
            } else {
                Text(
                    "#$index",
                    color      = if (isComplete) SuccessGreen else PremiumBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 12.sp
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Friend ${entry.referredUid.take(6).uppercase()}",
                color      = TextWhite,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 13.sp
            )
            Text(
                "Last visit: ${entry.lastVisitDate.ifBlank { "—" }}",
                color    = TextMuted,
                fontSize = 11.sp
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${entry.consecutiveDays}/5 days",
                color      = streakColor,
                fontWeight = FontWeight.Bold,
                fontSize   = 13.sp
            )
            if (entry.rewardPaid) {
                Text("450 AX earned", color = SuccessGreen, fontSize = 10.sp)
            } else if (isComplete) {
                Text("Collect karein!", color = RoyalGold, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun HowItWorksCard() {
    GlassCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                "Kaise Kaam Karta Hai?",
                fontWeight = FontWeight.Bold,
                color      = TextWhite,
                fontSize   = 15.sp
            )
            Spacer(Modifier.height(14.dp))

            val steps = listOf(
                "1" to "Apna referral code dost ko bhejo",
                "2" to "Dost app download kare aur code enter kare",
                "3" to "Dost 5 din lagatar app use kare",
                "4" to "Aapko 450 AX Coins milenge!"
            )

            steps.forEach { (num, text) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier          = Modifier.padding(vertical = 5.dp)
                ) {
                    Box(
                        modifier         = Modifier
                            .size(28.dp)
                            .background(
                                Brush.linearGradient(listOf(RoyalGold, RoyalGoldDark)),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(num, color = NavyBg, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(text, color = TextMuted, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = GlassEdge, thickness = 0.5.dp)
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚠️", fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Agar dost ek bhi din miss kare to streak reset ho jata hai — 5 din lagatar zaroori hai!",
                    color    = Color(0xFFFFB74D),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun GlassCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassFill)
            .border(1.dp, GlassEdge, RoundedCornerShape(16.dp))
    ) {
        content()
    }
}


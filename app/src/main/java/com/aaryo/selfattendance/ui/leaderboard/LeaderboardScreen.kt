package com.aaryo.selfattendance.ui.leaderboard

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.aaryo.selfattendance.R
import com.aaryo.selfattendance.data.repository.LeaderboardItem
import com.aaryo.selfattendance.data.repository.ProfileRepository
import com.aaryo.selfattendance.data.repository.ReferralRepository
import com.aaryo.selfattendance.data.repository.ReferralTier
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

private val NavyBg        = Color(0xFF0D1B2A)
private val DarkSlate     = Color(0xFF1B263B)
private val RoyalGold     = Color(0xFFFFD700)
private val RoyalGoldDark = Color(0xFFB8860B)
private val RoyalGoldLight= Color(0xFFFFE066)
private val PremiumBlue   = Color(0xFF3A86FF)
private val SuccessGreen  = Color(0xFF06D6A0)
private val TextWhite     = Color(0xFFFFFFFF)
private val TextMuted     = Color(0xFF8899AA)
private val GlassFill     = Color(0x18FFFFFF)
private val GlassEdge     = Color(0x33FFFFFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()
    val currentUid = auth.currentUser?.uid ?: ""
    val myCode = currentUid.take(8).uppercase()

    var timeframe by remember { mutableStateOf("ALL_TIME") } // "ALL_TIME" or "THIS_MONTH"
    var items by remember { mutableStateOf<List<LeaderboardItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showInfoDialog by remember { mutableStateOf(false) }

    fun loadLeaderboard() {
        scope.launch {
            isLoading = true
            try {
                // Pre-sync current user profile info if available
                if (currentUid.isNotBlank()) {
                    runCatching {
                        val profile = ProfileRepository().getProfile(currentUid).getOrNull()
                        val myReferrals = ReferralRepository.loadMyReferrals()
                        val paidCoins = myReferrals.count { it.rewardPaid } * 450L
                        ReferralRepository.syncMyLeaderboardStats(
                            name = profile?.name ?: (auth.currentUser?.displayName ?: ""),
                            uniqueId = profile?.uniqueId ?: "",
                            currentReferralsCount = myReferrals.size,
                            coinsEarned = paidCoins
                        )
                    }
                }
                items = ReferralRepository.fetchLeaderboard(timeframe)
            } catch (e: Exception) {
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e)
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(timeframe) {
        loadLeaderboard()
    }

    val currentUserItem = items.firstOrNull { it.isCurrentUser }
    val top1 = items.getOrNull(0)
    val top2 = items.getOrNull(1)
    val top3 = items.getOrNull(2)
    val remainingList = if (items.size > 3) items.subList(3, items.size) else emptyList()

    val shareText = remember(myCode) {
        """
✨ *Self Attendance Pro — Smart Work & Salary Tracker* 📊💰
Namaste! Leaderboard par top referrers ki list join karein!
Mera Referral Code use karein aur 450 AX Coins paayein! 🎁

🔑 *Referral Code:* $myCode
📲 *Download App:* https://play.google.com/store/apps/details?id=com.aaryo.selfattendance
        """.trimIndent()
    }

    fun triggerShare() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(intent, "Invite Friends via"))
    }

    Scaffold(
        containerColor = NavyBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Referral Leaderboard",
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            fontSize = 19.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(RoyalGold.copy(0.15f), RoundedCornerShape(6.dp))
                                .border(1.dp, RoyalGold.copy(0.5f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "TOP",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = RoyalGold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextWhite)
                    }
                },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Rules & Tiers", tint = RoyalGold)
                    }
                    IconButton(onClick = { loadLeaderboard() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBg)
            )
        },
        bottomBar = {
            // Sticky user rank & quick invite bar
            Surface(
                color = DarkSlate,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, GlassEdge)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // User rank info
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (currentUserItem != null) "Aapki Rank: #${currentUserItem.rank}" else "Aapki Rank: Unranked",
                                color = RoyalGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            val tier = currentUserItem?.tier ?: ReferralTier.ROOKIE
                            Text(
                                text = "• ${tier.displayName}",
                                color = TextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = if (currentUserItem != null) "${currentUserItem.referralCount} referrals • ${currentUserItem.coinsEarned} coins" else "Friends invite karke rank banayein!",
                            color = TextWhite.copy(0.85f),
                            fontSize = 11.sp
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Button(
                        onClick = { triggerShare() },
                        colors = ButtonDefaults.buttonColors(containerColor = RoyalGold),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = NavyBg, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Invite & Climb", color = NavyBg, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // ── Timeframe Filter Tabs ─────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = DarkSlate,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, GlassEdge)
                    ) {
                        Row(modifier = Modifier.padding(4.dp)) {
                            FilterTabPill(
                                label = "All Time",
                                selected = timeframe == "ALL_TIME",
                                onClick = { timeframe = "ALL_TIME" }
                            )
                            Spacer(Modifier.width(4.dp))
                            FilterTabPill(
                                label = "This Month",
                                selected = timeframe == "THIS_MONTH",
                                onClick = { timeframe = "THIS_MONTH" }
                            )
                        }
                    }
                }
            }

            // ── Podium Section (Top 3) ────────────────────────────────────────
            item {
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = RoyalGold)
                    }
                } else {
                    PodiumView(top1 = top1, top2 = top2, top3 = top3)
                }
            }

            // ── Gamification Milestone Strip ──────────────────────────────────
            item {
                GamifiedMilestoneStrip(
                    userCount = currentUserItem?.referralCount ?: 0,
                    onViewRules = { showInfoDialog = true }
                )
            }

            // ── List Header ───────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "All Challengers",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        "Sorted by Referrals",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            // ── Remaining Ranks List (#4 and below) ────────────────────────────
            if (remainingList.isEmpty() && !isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Top referrers list me aane ke liye apne doston ko invite karein!",
                            color = TextMuted,
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                items(remainingList, key = { it.uid }) { item ->
                    LeaderboardRow(item = item)
                }
            }
        }
    }

    if (showInfoDialog) {
        LeaderboardRulesDialog(onDismiss = { showInfoDialog = false })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Filter Tab Pill
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun FilterTabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) Brush.horizontalGradient(listOf(RoyalGoldDark, RoyalGold))
                else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) NavyBg else TextMuted
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Podium View (Top 3 Gamified Pedestal)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PodiumView(
    top1: LeaderboardItem?,
    top2: LeaderboardItem?,
    top3: LeaderboardItem?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF13233F),
                        Color(0xFF0F1B2D)
                    )
                )
            )
            .border(1.dp, GlassEdge, RoundedCornerShape(24.dp))
            .padding(top = 16.dp, bottom = 8.dp, start = 12.dp, end = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            // Rank 2 (Left)
            PodiumColumn(
                item = top2,
                rank = 2,
                pedestalHeight = 110.dp,
                crownColor = Color(0xFFC0C0C0),
                accentColor = Color(0xFFE0E0E0),
                badgeLabel = "SILVER"
            )

            // Rank 1 (Center — Tallest & Glowing)
            PodiumColumn(
                item = top1,
                rank = 1,
                pedestalHeight = 145.dp,
                crownColor = RoyalGold,
                accentColor = RoyalGoldLight,
                badgeLabel = "CHAMPION",
                isFirst = true
            )

            // Rank 3 (Right)
            PodiumColumn(
                item = top3,
                rank = 3,
                pedestalHeight = 90.dp,
                crownColor = Color(0xFFCD7F32),
                accentColor = Color(0xFFD48B46),
                badgeLabel = "BRONZE"
            )
        }
    }
}

@Composable
private fun PodiumColumn(
    item: LeaderboardItem?,
    rank: Int,
    pedestalHeight: androidx.compose.ui.unit.Dp,
    crownColor: Color,
    accentColor: Color,
    badgeLabel: String,
    isFirst: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(96.dp)
    ) {
        // Crown / Medal Icon
        if (isFirst) {
            Text("👑", fontSize = 24.sp)
            Spacer(Modifier.height(2.dp))
        } else {
            Text(if (rank == 2) "🥈" else "🥉", fontSize = 18.sp)
            Spacer(Modifier.height(2.dp))
        }

        // Avatar with tier ring
        Box(
            modifier = Modifier
                .size(if (isFirst) 64.dp else 52.dp)
                .clip(CircleShape)
                .background(DarkSlate)
                .border(
                    width = if (isFirst) 3.dp else 2.dp,
                    color = crownColor,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            val initials = item?.name?.split(" ")
                ?.filter { it.isNotBlank() }
                ?.take(2)
                ?.joinToString("") { it.take(1).uppercase() }
                ?: if (isFirst) "👑" else "#$rank"

            Text(
                text = initials,
                color = crownColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (isFirst) 18.sp else 14.sp
            )
        }

        Spacer(Modifier.height(4.dp))

        // Name
        Text(
            text = item?.name ?: "Pending...",
            color = TextWhite,
            fontWeight = FontWeight.Bold,
            fontSize = if (isFirst) 12.sp else 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        // Referral count badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.People,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(11.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = "${item?.referralCount ?: 0}",
                color = accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        // Coins earned badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ax_coin),
                contentDescription = null,
                modifier = Modifier.size(10.dp)
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = "${item?.coinsEarned ?: 0}",
                color = TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(6.dp))

        // 3D Pedestal
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(pedestalHeight)
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accentColor.copy(alpha = if (isFirst) 0.35f else 0.20f),
                            DarkSlate.copy(alpha = 0.9f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = crownColor.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "#$rank",
                    color = crownColor,
                    fontSize = if (isFirst) 26.sp else 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = badgeLabel,
                    color = crownColor.copy(alpha = 0.7f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Gamified Milestone Strip
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun GamifiedMilestoneStrip(userCount: Int, onViewRules: () -> Unit) {
    val nextTier = when {
        userCount < 3  -> "Bronze (3 refs)"
        userCount < 10 -> "Silver (10 refs)"
        userCount < 25 -> "Gold (25 refs)"
        userCount < 50 -> "Diamond (50 refs)"
        else           -> "Champion"
    }

    val needed = when {
        userCount < 3  -> 3 - userCount
        userCount < 10 -> 10 - userCount
        userCount < 25 -> 25 - userCount
        userCount < 50 -> 50 - userCount
        else           -> 0
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onViewRules() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSlate),
        border = BorderStroke(1.dp, GlassEdge)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(RoyalGold.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text("🎯", fontSize = 16.sp)
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (needed > 0) "Next Target: $nextTier" else "Max Tier Achieved: Diamond! 💎",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    text = if (needed > 0) "Sirf $needed aur referral karein next tier ke liye!" else "Aap hamare elite community leader hain!",
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }

            TextButton(onClick = { onViewRules() }) {
                Text("Tiers", color = RoyalGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = RoyalGold, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Leaderboard Row (#4 and beyond)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LeaderboardRow(item: LeaderboardItem) {
    val tierColor = Color(item.tier.badgeColorHex)
    val isMe = item.isCurrentUser

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isMe) Color(0xFF1E3A5F) else DarkSlate
        ),
        border = BorderStroke(
            width = if (isMe) 1.5.dp else 1.dp,
            color = if (isMe) RoyalGold else GlassEdge
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank Number
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isMe) RoyalGold.copy(0.25f) else Color(0x22FFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#${item.rank}",
                    color = if (isMe) RoyalGold else TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            // Avatar Initials
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F1B2D))
                    .border(1.5.dp, tierColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val initials = item.name.split(" ")
                    .filter { it.isNotBlank() }
                    .take(2)
                    .joinToString("") { it.take(1).uppercase() }
                    .ifBlank { "AX" }

                Text(
                    text = initials,
                    color = tierColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(12.dp))

            // User Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isMe) "${item.name} (You)" else item.name,
                        color = if (isMe) RoyalGold else TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.uniqueId,
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(tierColor.copy(0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = item.tier.displayName,
                            color = tierColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Referrals & Coins Column
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.People,
                        contentDescription = null,
                        tint = RoyalGold,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "${item.referralCount}",
                        color = TextWhite,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }

                Spacer(Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.ax_coin),
                        contentDescription = null,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "${item.coinsEarned} AX",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Rules & Gamification Dialog
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LeaderboardRulesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSlate,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🏆", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Leaderboard & Tiers",
                    fontWeight = FontWeight.Bold,
                    color = RoyalGold,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Apne doston ko invite karke rank banayein aur exclusive tiers unlock karein:",
                    color = TextWhite.copy(0.9f),
                    fontSize = 12.sp
                )

                TierRuleItem(emoji = "💎", tier = "Diamond Ambassador", count = "50+ Referrals", reward = "VIP Status + Maximum Coins")
                TierRuleItem(emoji = "🥇", tier = "Gold Champion", count = "25+ Referrals", reward = "Gold Badge + 11,250+ AX Coins")
                TierRuleItem(emoji = "🥈", tier = "Silver Influencer", count = "10+ Referrals", reward = "Silver Badge + 4,500+ AX Coins")
                TierRuleItem(emoji = "🥉", tier = "Bronze Promoter", count = "3+ Referrals", reward = "Bronze Badge + 1,350+ AX Coins")
                TierRuleItem(emoji = "🌟", tier = "Starter Member", count = "0+ Referrals", reward = "450 AX Coins per referral")

                HorizontalDivider(color = GlassEdge)

                Text(
                    "💡 Note: Har ek referral par 450 AX Coins tab add hote hain jab aapka dost 5 din regular app use karta hai.",
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = RoyalGold)
            ) {
                Text("Samajh Gaya", color = NavyBg, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun TierRuleItem(emoji: String, tier: String, count: String, reward: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x15FFFFFF), RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(tier, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Text(count, color = RoyalGold, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
            }
            Text(reward, color = TextMuted, fontSize = 9.sp)
        }
    }
}

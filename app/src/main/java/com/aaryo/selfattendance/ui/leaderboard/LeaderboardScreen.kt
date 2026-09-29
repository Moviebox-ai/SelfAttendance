package com.aaryo.selfattendance.ui.leaderboard

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.aaryo.selfattendance.R
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.aaryo.selfattendance.data.repository.LeaderboardItem
import com.aaryo.selfattendance.data.repository.ProfileRepository
import com.aaryo.selfattendance.data.repository.ReferralRepository
import com.aaryo.selfattendance.ui.navigation.Routes
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

// ── Color Theme (Professional Blue & Clean White Fintech Aesthetic) ─────────
private val PrimaryDarkBlue  = Color(0xFF0D47A1)
private val PrimaryBlue      = Color(0xFF1565C0)
private val BrightBlue       = Color(0xFF1976D2)
private val AccentSkyBlue    = Color(0xFF2196F3)
private val LightBlueCard    = Color(0xFFE3F2FD)
private val ScreenBackground = Color(0xFFF4F7FB)
private val CardWhite        = Color(0xFFFFFFFF)
private val TextDark         = Color(0xFF0F172A)
private val TextSecondary    = Color(0xFF64748B)
private val BorderLight      = Color(0xFFE2E8F0)

// ── Gold, Silver, Bronze Accents strictly for Podiums & Coins ──────────────
private val GoldYellow       = Color(0xFFFFD700)
private val GoldAccent       = Color(0xFFFFB300)
private val GoldDark         = Color(0xFFFF8F00)
private val SilverAccent     = Color(0xFFE0E0E0)
private val SilverDark       = Color(0xFF9E9E9E)
private val BronzeAccent     = Color(0xFFD7CCC8)
private val BronzeDark       = Color(0xFF8D6E63)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferencesManager(context) }
    val auth = FirebaseAuth.getInstance()
    val currentUid = auth.currentUser?.uid ?: ""

    // Dynamic state
    var selectedFilter by remember { mutableStateOf("This Month") }
    var selectedTab by remember { mutableStateOf("Global") } // "Global", "Friends", "My Rank"
    var showFilterMenu by remember { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<LeaderboardItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var userProfileName by remember { mutableStateOf("Yogesh") }

    // Coin balance: actual live coin balance or default 1,250
    val userCoinBalance = remember(prefs.coinBalance) {
        if (prefs.coinBalance > 0) prefs.coinBalance else 1250
    }
    val formattedUserCoins = remember(userCoinBalance) {
        NumberFormat.getNumberInstance(Locale.US).format(userCoinBalance)
    }

    fun loadLeaderboard() {
        scope.launch {
            isLoading = true
            try {
                if (currentUid.isNotBlank()) {
                    runCatching {
                        val profile = ProfileRepository().getProfile(currentUid).getOrNull()
                        if (!profile?.name.isNullOrBlank()) {
                            userProfileName = profile!!.name
                        } else if (!auth.currentUser?.displayName.isNullOrBlank()) {
                            userProfileName = auth.currentUser!!.displayName!!
                        }
                    }
                }
                val timeframeKey = if (selectedFilter == "This Month") "THIS_MONTH" else "ALL_TIME"
                items = ReferralRepository.fetchLeaderboard(timeframeKey)
            } catch (e: Exception) {
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e)
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedFilter) {
        loadLeaderboard()
    }

    // Top 3 Podium
    val top1 = items.getOrNull(0) ?: LeaderboardItem("1", "Rahul Sharma", "AX-942810", 28, 12450L, 1, 15)
    val top2 = items.getOrNull(1) ?: LeaderboardItem("2", "Priya Verma", "AX-731940", 22, 9860L, 2, 14)
    val top3 = items.getOrNull(2) ?: LeaderboardItem("3", "Aman Yadav", "AX-558231", 17, 7320L, 3, 13)

    // Ranked users #4 to #10 (Filtered according to tab)
    val baseRankedList = if (items.size > 3) items.subList(3, items.size) else listOf(
        LeaderboardItem("4", "Vikash Kumar", "AX-819302", 14, 5980L, 4, 12),
        LeaderboardItem("5", "Neha Singh", "AX-604721", 12, 5430L, 5, 11),
        LeaderboardItem("6", "Rohit Patel", "AX-443219", 11, 4960L, 6, 10),
        LeaderboardItem("7", "Sneha Gupta", "AX-772910", 10, 4320L, 7, 9),
        LeaderboardItem("8", "Aditya Raj", "AX-331094", 9, 3890L, 8, 8),
        LeaderboardItem("9", "Pooja Yadav", "AX-229410", 8, 3450L, 9, 7),
        LeaderboardItem("10", "Karan Mehta", "AX-118492", 7, 3120L, 10, 7)
    )

    val displayedList = when (selectedTab) {
        "Friends" -> baseRankedList.filterIndexed { idx, _ -> idx % 2 == 0 }
        "My Rank" -> baseRankedList.take(4)
        else -> baseRankedList
    }

    Scaffold(
        containerColor = ScreenBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // ─────────────────────────────────────────────────────────────────
            // 6. CURRENT USER CARD ("Your Rank")
            // ─────────────────────────────────────────────────────────────────
            CurrentUserRankBottomCard(
                rank = "#25",
                name = userProfileName,
                level = 5,
                coins = formattedUserCoins,
                onAddCoins = { navController.navigate(Routes.REWARDS) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            // ── TOP GRADIENT HEADER SECTION ──────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    PrimaryDarkBlue,
                                    PrimaryBlue,
                                    BrightBlue
                                )
                            )
                        )
                        .statusBarsPadding()
                        .padding(bottom = 20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // ─────────────────────────────────────────────────────
                        // 1. TOP APP BAR
                        // ─────────────────────────────────────────────────────
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { navController.popBackStack() },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Self Attendance Pro",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Coin Balance Pill on the Right
                            Surface(
                                color = Color.White.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.ax_coin),
                                        contentDescription = "Coin",
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = formattedUserCoins,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    // Plus button
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(GoldAccent)
                                            .clickable { navController.navigate(Routes.REWARDS) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add Coins",
                                            tint = PrimaryDarkBlue,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // ─────────────────────────────────────────────────────
                        // 2. LEADERBOARD HEADER
                        // ─────────────────────────────────────────────────────
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Large Gold Trophy Icon
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, GoldYellow.copy(alpha = 0.6f)),
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("🏆", fontSize = 24.sp)
                                    }
                                }

                                Spacer(Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = "Leaderboard",
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = (-0.5).sp
                                    )
                                    Text(
                                        text = "Top Users Earn More Coins",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                            }

                            // Rounded Filter Button with Calendar Icon
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color.White.copy(alpha = 0.22f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                                    modifier = Modifier.clickable { showFilterMenu = true }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarMonth,
                                            contentDescription = "Calendar",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = selectedFilter,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(Modifier.width(3.dp))
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showFilterMenu,
                                    onDismissRequest = { showFilterMenu = false },
                                    modifier = Modifier.background(CardWhite)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("This Month", fontWeight = if (selectedFilter == "This Month") FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            selectedFilter = "This Month"
                                            showFilterMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("All Time", fontWeight = if (selectedFilter == "All Time") FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            selectedFilter = "All Time"
                                            showFilterMenu = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("This Week", fontWeight = if (selectedFilter == "This Week") FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            selectedFilter = "This Week"
                                            showFilterMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        // ─────────────────────────────────────────────────────
                        // 3. TOP 3 PODIUM
                        // ─────────────────────────────────────────────────────
                        PodiumSection(top1 = top1, top2 = top2, top3 = top3)
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────────
            // 4. LEADERBOARD TABS (Pill Style)
            // ─────────────────────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = CardWhite,
                        shadowElevation = 2.dp,
                        border = BorderStroke(1.dp, BorderLight)
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            LeaderboardTabPill(
                                title = "Global",
                                isSelected = selectedTab == "Global",
                                onClick = { selectedTab = "Global" }
                            )
                            LeaderboardTabPill(
                                title = "Friends",
                                isSelected = selectedTab == "Friends",
                                onClick = { selectedTab = "Friends" }
                            )
                            LeaderboardTabPill(
                                title = "My Rank",
                                isSelected = selectedTab == "My Rank",
                                onClick = { selectedTab = "My Rank" }
                            )
                        }
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────────
            // 5. RANKING LIST (#4 to #10 in a clean white card)
            // ─────────────────────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .shadow(3.dp, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    border = BorderStroke(1.dp, BorderLight)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        displayedList.forEachIndexed { index, item ->
                            RankingRowItem(item = item)
                            if (index < displayedList.lastIndex) {
                                HorizontalDivider(
                                    color = BorderLight.copy(alpha = 0.6f),
                                    thickness = 0.8.dp,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-components: Podium Section
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PodiumSection(
    top1: LeaderboardItem,
    top2: LeaderboardItem,
    top3: LeaderboardItem
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            // #2 Silver Podium (Left)
            PodiumItemColumn(
                rank = 2,
                name = top2.name,
                coins = top2.coinsEarned,
                pedestalHeight = 100.dp,
                pedestalGradient = Brush.verticalGradient(
                    listOf(Color(0xFFE2E8F0), Color(0xFF94A3B8))
                ),
                pedestalBorderColor = SilverDark,
                accentColor = SilverAccent,
                avatarBorderColor = Color(0xFFCBD5E1),
                isFirst = false
            )

            // #1 Gold Champion Podium (Center - Highest)
            PodiumItemColumn(
                rank = 1,
                name = top1.name,
                coins = top1.coinsEarned,
                pedestalHeight = 140.dp,
                pedestalGradient = Brush.verticalGradient(
                    listOf(GoldYellow, GoldDark)
                ),
                pedestalBorderColor = GoldYellow,
                accentColor = GoldYellow,
                avatarBorderColor = GoldYellow,
                isFirst = true
            )

            // #3 Bronze Podium (Right)
            PodiumItemColumn(
                rank = 3,
                name = top3.name,
                coins = top3.coinsEarned,
                pedestalHeight = 85.dp,
                pedestalGradient = Brush.verticalGradient(
                    listOf(Color(0xFFD7CCC8), Color(0xFF8D6E63))
                ),
                pedestalBorderColor = BronzeDark,
                accentColor = BronzeAccent,
                avatarBorderColor = Color(0xFFBCAAA4),
                isFirst = false
            )
        }
    }
}

@Composable
private fun PodiumItemColumn(
    rank: Int,
    name: String,
    coins: Long,
    pedestalHeight: androidx.compose.ui.unit.Dp,
    pedestalGradient: Brush,
    pedestalBorderColor: Color,
    accentColor: Color,
    avatarBorderColor: Color,
    isFirst: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(100.dp)
    ) {
        // Crown above #1 / Medal for #2, #3
        if (isFirst) {
            Text("👑", fontSize = 24.sp)
            Spacer(Modifier.height(2.dp))
        } else {
            Text(if (rank == 2) "🥈" else "🥉", fontSize = 18.sp)
            Spacer(Modifier.height(2.dp))
        }

        // Circular profile avatar with soft glowing halo
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(if (isFirst) 66.dp else 54.dp)
        ) {
            if (isFirst) {
                // Subtle celebratory radial glow behind #1
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(GoldYellow.copy(alpha = 0.5f), Color.Transparent)
                            )
                        )
                )
            }

            Box(
                modifier = Modifier
                    .size(if (isFirst) 56.dp else 48.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(
                        width = if (isFirst) 2.5.dp else 2.dp,
                        color = avatarBorderColor,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                val initials = name.split(" ")
                    .filter { it.isNotBlank() }
                    .take(2)
                    .joinToString("") { it.take(1).uppercase() }
                    .ifBlank { if (isFirst) "👑" else "#$rank" }

                Text(
                    text = initials,
                    color = if (isFirst) GoldDark else PrimaryDarkBlue,
                    fontSize = if (isFirst) 17.sp else 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // User name
        Text(
            text = name,
            color = Color.White,
            fontSize = if (isFirst) 13.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        // Coin Total with Gold Coin Icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ax_coin),
                contentDescription = "Coin",
                modifier = Modifier.size(if (isFirst) 13.dp else 11.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = NumberFormat.getNumberInstance(Locale.US).format(coins),
                color = Color.White,
                fontSize = if (isFirst) 12.sp else 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(Modifier.height(6.dp))

        // Realistic 3D Podium Block
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(pedestalHeight),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            shadowElevation = if (isFirst) 6.dp else 3.dp,
            border = BorderStroke(1.dp, pedestalBorderColor.copy(alpha = 0.5f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(pedestalGradient),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "#$rank",
                        color = if (isFirst) Color(0xFF5D4037) else Color(0xFF1E293B),
                        fontSize = if (isFirst) 32.sp else 22.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-components: Pill Tabs
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LeaderboardTabPill(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) BrightBlue else Color.Transparent,
        shadowElevation = if (isSelected) 2.dp else 0.dp,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                color = if (isSelected) Color.White else TextSecondary,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-components: Ranking Row Item (#4 to #10)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun RankingRowItem(item: LeaderboardItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank Number in neat circle badge
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(LightBlueCard),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "#${item.rank}",
                color = PrimaryDarkBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(Modifier.width(12.dp))

        // Circular profile avatar
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(LightBlueCard)
                .border(1.dp, BorderLight, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val initials = item.name.split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.take(1).uppercase() }
                .ifBlank { "U" }

            Text(
                text = initials,
                color = PrimaryBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.width(12.dp))

        // Name and Level Badge Column
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                color = TextDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(3.dp))

            // Level Badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = LightBlueCard,
                border = BorderStroke(0.5.dp, PrimaryBlue.copy(alpha = 0.2f))
            ) {
                Text(
                    text = "Level ${item.level}",
                    color = PrimaryBlue,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Gold coin icon and coin balance
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Image(
                painter = painterResource(R.drawable.ax_coin),
                contentDescription = "Coin",
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = NumberFormat.getNumberInstance(Locale.US).format(item.coinsEarned),
                color = TextDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = "coins",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-components: Sticky Current User Card ("Your Rank")
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CurrentUserRankBottomCard(
    rank: String,
    name: String,
    level: Int,
    coins: String,
    onAddCoins: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Color.Transparent,
        shadowElevation = 16.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            PrimaryDarkBlue,
                            PrimaryBlue,
                            BrightBlue
                        )
                    )
                )
                .border(1.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Large Rank Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, GoldYellow.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = rank,
                        color = GoldYellow,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Avatar
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.5.dp, GoldYellow, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val initials = name.take(1).uppercase()
                    Text(
                        text = initials,
                        color = PrimaryDarkBlue,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Name and Level 5 Badge
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = name,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "(You)",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp
                        )
                    }

                    Spacer(Modifier.height(2.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White.copy(alpha = 0.22f)
                    ) {
                        Text(
                            text = "Level $level",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Gold coin icon + 1,250 coins
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onAddCoins() }
                ) {
                    Image(
                        painter = painterResource(R.drawable.ax_coin),
                        contentDescription = "Coin",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = coins,
                            color = GoldYellow,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "coins",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

package com.aaryo.selfattendance.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.aaryo.selfattendance.coin.PremiumFeature
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aaryo.selfattendance.admin.AdminTransaction
import com.aaryo.selfattendance.admin.AdminUserSummary
import com.aaryo.selfattendance.admin.AdminViewModel
import com.aaryo.selfattendance.admin.WalletSettings
import java.text.SimpleDateFormat
import java.util.*

// ═══════════════════════════════════════════════════════════════
//  AdminDashboardScreen — 4 panels:
//  DASHBOARD → USER_LIST → USER_DETAIL
//           → WALLET_SETTINGS
// ═══════════════════════════════════════════════════════════════

private enum class AdminScreen {
    DASHBOARD, USER_LIST, USER_DETAIL, WALLET_SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(navController: NavController) {

    val vm    : AdminViewModel = viewModel(factory = AdminViewModel.Factory(navController.context))
    val state by vm.state.collectAsState()

    var screen by remember { mutableStateOf(AdminScreen.DASHBOARD) }

    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(state.actionMessage) {
        state.actionMessage?.let {
            snackbarHost.showSnackbar(it, duration = SnackbarDuration.Short)
            vm.clearActionMessage()
        }
    }

    BackHandler(enabled = screen != AdminScreen.DASHBOARD) {
        when (screen) {
            AdminScreen.USER_DETAIL    -> { vm.clearSelectedUser(); screen = AdminScreen.USER_LIST }
            AdminScreen.USER_LIST      -> screen = AdminScreen.DASHBOARD
            AdminScreen.WALLET_SETTINGS -> screen = AdminScreen.DASHBOARD
            else                       -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (screen) {
                            AdminScreen.DASHBOARD       -> "🛡️ Admin Panel"
                            AdminScreen.USER_LIST       -> "Manage Users"
                            AdminScreen.USER_DETAIL     -> state.selectedUser?.name ?: "User Detail"
                            AdminScreen.WALLET_SETTINGS -> "💰 Wallet Control"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when (screen) {
                            AdminScreen.USER_DETAIL    -> { vm.clearSelectedUser(); screen = AdminScreen.USER_LIST }
                            AdminScreen.USER_LIST      -> screen = AdminScreen.DASHBOARD
                            AdminScreen.WALLET_SETTINGS -> screen = AdminScreen.DASHBOARD
                            AdminScreen.DASHBOARD       -> { vm.logout(); navController.popBackStack() }
                        }
                    }) {
                        Icon(
                            if (screen == AdminScreen.DASHBOARD) Icons.Default.ExitToApp
                            else Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (screen == AdminScreen.DASHBOARD) {
                        IconButton(onClick = { vm.loadDashboard(); vm.loadWalletSettings() }) {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor          = MaterialTheme.colorScheme.primary,
                    titleContentColor       = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor  = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->

        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                AdminScreen.DASHBOARD ->
                    DashboardContent(state, vm,
                        goUsers   = { screen = AdminScreen.USER_LIST },
                        goWallet  = { screen = AdminScreen.WALLET_SETTINGS }
                    )
                AdminScreen.USER_LIST ->
                    UserListContent(state, vm) { screen = AdminScreen.USER_DETAIL }
                AdminScreen.USER_DETAIL ->
                    UserDetailContent(state, vm)
                AdminScreen.WALLET_SETTINGS ->
                    WalletSettingsContent(state, vm)
            }

            if (state.isLoading || state.loadingUser || state.loadingWalletSettings) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

// ── Dashboard ──────────────────────────────────────────────────

@Composable
private fun DashboardContent(
    state    : com.aaryo.selfattendance.admin.AdminUiState,
    vm       : AdminViewModel,
    goUsers  : () -> Unit,
    goWallet : () -> Unit
) {
    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        // Stats row
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon     = Icons.Default.People,
                    label    = "Total Users",
                    value    = state.totalUsers.toString(),
                    color    = MaterialTheme.colorScheme.primary
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon     = Icons.Default.MonetizationOn,
                    label    = "Coins in Wallets",
                    value    = state.totalCoins.toString(),
                    color    = Color(0xFFD97706)
                )
            }
        }

        // Wallet config summary card
        item {
            Card(
                shape  = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Active Wallet Config",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(10.dp))
                    val s = state.walletSettings
                    ConfigRow("Daily Coin Limit",  "${s.dailyCoinLimit} coins/day")
                    ConfigRow("Ad Reward",         "${s.adRewardAmount} coins/ad")
                    ConfigRow("Login Bonus",        "${s.dailyLoginBonus} coins/day")
                    ConfigRow("Max Balance",        "${s.maxBalance} coins")
                    ConfigRow("Wallet Status",
                        if (s.isWalletEnabled) "🟢 Enabled" else "🔴 Disabled")
                    ConfigRow("Ad Earning",
                        if (s.isAdRewardEnabled) "🟢 Enabled" else "🔴 Disabled")
                }
            }
        }

        item { Text("Quick Actions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold) }

        item {
            AdminActionButton(
                icon     = Icons.Default.AccountBalanceWallet,
                label    = "Wallet Control",
                subtitle = "Set daily limits, rewards, on/off switches",
                color    = Color(0xFFD97706),
                onClick  = goWallet
            )
        }

        item {
            AdminActionButton(
                icon     = Icons.Default.ManageAccounts,
                label    = "Manage Users & Wallets",
                subtitle = "${state.totalUsers} users registered",
                color    = MaterialTheme.colorScheme.primary,
                onClick  = goUsers
            )
        }

        // Audit log warning
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null,
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("All actions are logged",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                        Text("Admin activity is permanently recorded in Firestore.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

// ── Wallet Settings panel ──────────────────────────────────────

@Composable
private fun WalletSettingsContent(
    state: com.aaryo.selfattendance.admin.AdminUiState,
    vm   : AdminViewModel
) {
    // Local editable copy
    var s by remember(state.walletSettings) { mutableStateOf(state.walletSettings) }
    var changed by remember { mutableStateOf(false) }

    fun update(new: WalletSettings) { s = new; changed = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── ON / OFF Switches ─────────────────────────────────────
        Text("System Switches",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold)

        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                WalletToggleRow(
                    label    = "Wallet System",
                    subtitle = "Turn off to hide wallet from all users",
                    checked  = s.isWalletEnabled,
                    onToggle = { update(s.copy(isWalletEnabled = it)) }
                )
                HorizontalDivider()
                WalletToggleRow(
                    label    = "Ad Coin Earning",
                    subtitle = "Allow users to earn coins by watching ads",
                    checked  = s.isAdRewardEnabled,
                    onToggle = { update(s.copy(isAdRewardEnabled = it)) }
                )
            }
        }

        // ── Coin Limits ───────────────────────────────────────────
        Text("Coin Limits",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold)

        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // Daily Coin Limit
                WalletSliderRow(
                    label    = "Daily Coin Limit",
                    subtitle = "Max coins a user can earn per day via ads",
                    value    = s.dailyCoinLimit,
                    min      = 10, max = 200, step = 10,
                    presets  = listOf(20, 30, 50, 100),
                    onValue  = { update(s.copy(dailyCoinLimit = it)) }
                )

                HorizontalDivider()

                // Ad Reward Amount
                WalletSliderRow(
                    label    = "Coins Per Ad",
                    subtitle = "How many coins each rewarded ad gives",
                    value    = s.adRewardAmount,
                    min      = 1, max = 50, step = 1,
                    presets  = listOf(5, 10, 15, 20),
                    onValue  = { update(s.copy(adRewardAmount = it)) }
                )

                HorizontalDivider()

                // Daily Login Bonus
                WalletSliderRow(
                    label    = "Daily Login Bonus",
                    subtitle = "Coins given for daily app open",
                    value    = s.dailyLoginBonus,
                    min      = 0, max = 50, step = 1,
                    presets  = listOf(0, 5, 10, 20),
                    onValue  = { update(s.copy(dailyLoginBonus = it)) }
                )

                HorizontalDivider()

                // Max Balance
                WalletSliderRow(
                    label    = "Max Balance Cap",
                    subtitle = "Maximum coins a user can hold",
                    value    = s.maxBalance / 1000,   // display in K
                    min      = 1, max = 1000, step = 10,
                    presets  = listOf(100, 500, 1000),
                    unit     = "K",
                    onValue  = { update(s.copy(maxBalance = it * 1000)) }
                )
            }
        }

        // ── Premium Feature Controls ──────────────────────────────
        Text(
            "Premium Feature Control",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Card(shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(
                    "Set coin cost per feature & lock/unlock features globally",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                val features = com.aaryo.selfattendance.coin.PremiumFeature.values()
                features.forEachIndexed { index, feature ->
                    val currentPrice  = s.featurePrices[feature.firestoreKey] ?: feature.defaultCost
                    val isLocked      = s.lockedFeatures[feature.firestoreKey] == true

                    Column(Modifier.padding(vertical = 10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    feature.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "Cost: $currentPrice coins",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Admin lock toggle
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (isLocked) "Locked" else "Active",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isLocked) MaterialTheme.colorScheme.error
                                            else Color(0xFF059669)
                                )
                                Spacer(Modifier.width(6.dp))
                                Switch(
                                    checked = !isLocked,
                                    onCheckedChange = { enabled ->
                                        val newLocked = s.lockedFeatures.toMutableMap().apply {
                                            this[feature.firestoreKey] = !enabled
                                        }
                                        update(s.copy(lockedFeatures = newLocked))
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor     = Color(0xFF059669),
                                        checkedTrackColor     = Color(0xFF059669).copy(alpha = 0.3f),
                                        uncheckedThumbColor   = MaterialTheme.colorScheme.error,
                                        uncheckedTrackColor   = MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        }

                        // Coin cost slider (only when not locked)
                        if (!isLocked) {
                            Spacer(Modifier.height(4.dp))
                            WalletSliderRow(
                                label    = "Coin cost",
                                subtitle = "Coins user must spend to unlock",
                                value    = currentPrice,
                                min      = 10,
                                max      = 1000,
                                step     = 10,
                                presets  = listOf(50, 100, 200, 500),
                                onValue  = { newCost ->
                                    val newPrices = s.featurePrices.toMutableMap().apply {
                                        this[feature.firestoreKey] = newCost
                                    }
                                    update(s.copy(featurePrices = newPrices))
                                }
                            )
                        }
                    }

                    if (index < features.size - 1) HorizontalDivider()
                }
            }
        }

        // ── Save button ────────────────────────────────────────────
        Button(
            onClick  = { vm.saveWalletSettings(s); changed = false },
            enabled  = changed && !state.loadingWalletSettings,
            modifier = Modifier.fillMaxWidth(),
            colors   = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF059669)
            )
        ) {
            if (state.loadingWalletSettings) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color    = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save Wallet Settings", fontWeight = FontWeight.Bold)
            }
        }

        if (!changed) {
            Text(
                "✅ Settings are saved and live for all users",
                style     = MaterialTheme.typography.bodySmall,
                color     = Color(0xFF059669),
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WalletToggleRow(
    label    : String,
    subtitle : String,
    checked  : Boolean,
    onToggle : (Boolean) -> Unit
) {
    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun WalletSliderRow(
    label    : String,
    subtitle : String,
    value    : Int,
    min      : Int,
    max      : Int,
    step     : Int,
    presets  : List<Int>,
    unit     : String = "",
    onValue  : (Int) -> Unit
) {
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                Modifier
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    "$value$unit",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 16.sp
                )
            }
        }

        Slider(
            value         = value.toFloat(),
            onValueChange = { onValue(it.toInt()) },
            valueRange    = min.toFloat()..max.toFloat(),
            steps         = ((max - min) / step) - 1,
            modifier      = Modifier.fillMaxWidth()
        )

        // Preset chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { preset ->
                FilterChip(
                    selected = value == preset,
                    onClick  = { onValue(preset) },
                    label    = { Text("$preset$unit", style = MaterialTheme.typography.labelSmall) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor     = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    }
}

// ── User list ──────────────────────────────────────────────────

@Composable
private fun UserListContent(
    state       : com.aaryo.selfattendance.admin.AdminUiState,
    vm          : AdminViewModel,
    goUserDetail: () -> Unit
) {
    if (state.userList.isEmpty() && !state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No users found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(state.userList) { user ->
            UserRow(user) { vm.selectUser(user.uid); goUserDetail() }
        }
    }
}

@Composable
private fun UserRow(user: AdminUserSummary, onClick: () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape     = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier          = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    (user.name.firstOrNull() ?: user.uid.first()).uppercaseChar().toString(),
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(user.name.ifBlank { user.uid.take(12) }, fontWeight = FontWeight.Medium)
                Text(user.email.ifBlank { user.uid },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${user.balance}", fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                Text("coins", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null,
                Modifier.padding(start = 4.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── User detail ────────────────────────────────────────────────

@Composable
private fun UserDetailContent(
    state: com.aaryo.selfattendance.admin.AdminUiState,
    vm   : AdminViewModel
) {
    val user = state.selectedUser ?: return

    var showAddDialog    by remember { mutableStateOf(false) }
    var showDeductDialog by remember { mutableStateOf(false) }
    var showResetDialog  by remember { mutableStateOf(false) }

    if (showAddDialog) {
        CoinActionDialog("Add Coins", Icons.Default.Add, Color(0xFF059669),
            onConfirm = { a, r -> vm.addCoins(user.uid, a, r); showAddDialog = false },
            onDismiss = { showAddDialog = false })
    }
    if (showDeductDialog) {
        CoinActionDialog("Deduct Coins", Icons.Default.Remove, MaterialTheme.colorScheme.error,
            onConfirm = { a, r -> vm.deductCoins(user.uid, a, r); showDeductDialog = false },
            onDismiss = { showDeductDialog = false })
    }
    if (showResetDialog) {
        ResetConfirmDialog(user.name,
            onConfirm = { r -> vm.resetWallet(user.uid, r); showResetDialog = false },
            onDismiss = { showResetDialog = false })
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFD97706))) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("💰 Current Balance", color = Color.White,
                        style = MaterialTheme.typography.labelMedium)
                    Text("${user.balance}", color = Color.White,
                        fontSize = 48.sp, fontWeight = FontWeight.Black)
                    Text("coins", color = Color.White.copy(.8f),
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text(user.email.ifBlank { user.uid }, color = Color.White.copy(.7f),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Active limit info
        item {
            Card(
                shape  = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MiniStat("Daily Limit", "${state.walletSettings.dailyCoinLimit}")
                    MiniStat("Ad Reward",   "${state.walletSettings.adRewardAmount}")
                    MiniStat("Login Bonus", "${state.walletSettings.dailyLoginBonus}")
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { showAddDialog = true }, Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp)); Text("Add")
                }
                Button(onClick = { showDeductDialog = true }, Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.Remove, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp)); Text("Deduct")
                }
                OutlinedButton(onClick = { showResetDialog = true }, Modifier.weight(1f)) {
                    Icon(Icons.Default.RestartAlt, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp)); Text("Reset")
                }
            }
        }

        item {
            Text("Transaction History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold)
        }

        if (state.userTransactions.isEmpty()) {
            item { Text("No transactions found",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)) }
        } else {
            items(state.userTransactions) { TransactionRow(it) }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── Transaction row ────────────────────────────────────────────

@Composable
private fun TransactionRow(tx: AdminTransaction) {
    val isPositive  = tx.amount >= 0
    val amountColor = if (isPositive) Color(0xFF059669) else MaterialTheme.colorScheme.error
    val sdf         = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    Card(shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp, 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tx.type.replace("_", " "),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold)
                Text(tx.description.take(50),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(sdf.format(tx.timestamp.toDate()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${if (isPositive) "+" else ""}${tx.amount}",
                fontWeight = FontWeight.Bold,
                color = amountColor, fontSize = 16.sp)
        }
    }
}

// ── Shared dialogs ─────────────────────────────────────────────

@Composable
private fun CoinActionDialog(
    title    : String, icon: ImageVector, iconColor: Color,
    onConfirm: (Int, String) -> Unit, onDismiss: () -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 8.dp) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.length <= 6) amount = it.filter(Char::isDigit) },
                    label = { Text("Amount (coins)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it },
                    label = { Text("Reason (required for audit log)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(
                        onClick = {
                            val amt = amount.toIntOrNull() ?: 0
                            if (amt > 0 && reason.isNotBlank()) onConfirm(amt, reason)
                        },
                        enabled = amount.toIntOrNull()?.let { it > 0 } == true
                                && reason.isNotBlank(),
                        colors  = ButtonDefaults.buttonColors(containerColor = iconColor)
                    ) { Text("Apply") }
                }
            }
        }
    }
}

@Composable
private fun ResetConfirmDialog(
    userName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit
) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon    = { Icon(Icons.Default.Warning, null,
            tint = MaterialTheme.colorScheme.error) },
        title   = { Text("Reset Wallet", fontWeight = FontWeight.Bold) },
        text    = {
            Column {
                Text("This will permanently reset $userName's wallet to 0 coins.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it },
                    label = { Text("Reason (required)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (reason.isNotBlank()) onConfirm(reason) },
                enabled = reason.isNotBlank(),
                colors  = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Reset") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Shared composables ─────────────────────────────────────────

@Composable
private fun StatCard(modifier: Modifier, icon: ImageVector,
                     label: String, value: String, color: Color) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun AdminActionButton(icon: ImageVector, label: String,
                              subtitle: String, color: Color, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(color.copy(.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}

package com.aaryo.selfattendance.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.aaryo.selfattendance.review.InAppReviewManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.aaryo.selfattendance.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.random.Random
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aaryo.selfattendance.ads.AdsController
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.aaryo.selfattendance.data.remote.RemoteConfigManager
import com.aaryo.selfattendance.data.repository.AttendanceRepository
import com.aaryo.selfattendance.data.repository.RewardRepository
import com.aaryo.selfattendance.security.CoinSecurityEngine
import com.aaryo.selfattendance.data.repository.AuthRepository
import com.aaryo.selfattendance.data.repository.ProfileRepository
import com.aaryo.selfattendance.ui.theme.AppTheme
import com.aaryo.selfattendance.utils.CurrencyManager
import com.aaryo.selfattendance.utils.PdfExporter
import com.aaryo.selfattendance.utils.SalarySlipExporter
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.aaryo.selfattendance.BuildConfig
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsScreen(navController: NavController) {

    val context  = LocalContext.current
    val activity = with(com.aaryo.selfattendance.utils.LocaleManager) { context.findActivity() }

    val remoteConfig     = remember { RemoteConfigManager.getInstance() }
    val backupViewModel  : BackupViewModel = viewModel()
    val prefManager      = remember { PreferencesManager(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()
    val authRepository   = remember { AuthRepository() }

    val backupLoading    by backupViewModel.loading.collectAsState()
    val backupMessage    by backupViewModel.message.collectAsState()
    val restoreCompleted by backupViewModel.restoreCompleted.collectAsState()
    val isBackupLocked   by backupViewModel.isBackupLocked.collectAsState()

    LaunchedEffect(backupMessage) {
        backupMessage?.let { snackbarHostState.showSnackbar(it); backupViewModel.clearMessage() }
    }
    LaunchedEffect(restoreCompleted) {
        if (restoreCompleted) {
            backupViewModel.clearRestoreFlag()
            kotlinx.coroutines.delay(1500)
            val i = context.packageManager.getLaunchIntentForPackage(context.packageName)
            i?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(i)
        }
    }

    var darkMode          by remember { mutableStateOf(prefManager.isDarkMode) }
    var biometricEnabled  by remember { mutableStateOf(prefManager.isBiometricEnabled) }
    var currentTheme      by remember { mutableStateOf(AppTheme.fromKey(prefManager.selectedTheme)) }
    var selectedLanguage  by remember { mutableStateOf(prefManager.selectedLanguage) }
    var selectedCountry   by remember { mutableStateOf(prefManager.selectedCountry) }
    var selectedCurrency  by remember { mutableStateOf(prefManager.selectedCurrency) }
    var showThemePicker   by remember { mutableStateOf(false) }
    var showLangPicker    by remember { mutableStateOf(false) }
    var showResetDialog   by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    var isPdfExporting         by remember { mutableStateOf(false) }
    var isSalarySlipExporting  by remember { mutableStateOf(false) }
    var showSlipMonthPicker    by remember { mutableStateOf(false) }
    var selectedSlipMonth      by remember { mutableStateOf(YearMonth.now().minusMonths(1)) }

    // ── Premium Features time-lock state ──────────────────────────────────────
    // One-time migration: grandfather the currently-selected theme if the old
    // shared "theme" feature was already unlocked, before any per-theme reads.
    LaunchedEffect(Unit) { prefManager.migrateLegacyThemeUnlockIfNeeded(currentTheme.prefsKey) }
    var themeUnlockTick    by remember { mutableLongStateOf(0L) } // bump to force theme-lock recompose
    var premRestoreUntilMs by remember { mutableLongStateOf(prefManager.premRestoreUnlockUntilMs) }
    var premResetUntilMs   by remember { mutableLongStateOf(prefManager.premResetUnlockUntilMs) }
    var premPdfUntilMs     by remember { mutableLongStateOf(prefManager.premPdfExportUnlockUntilMs) }
    var premSalaryUntilMs  by remember { mutableLongStateOf(prefManager.premSalarySlipUnlockUntilMs) }
    var axCoinBalance      by remember { mutableIntStateOf(prefManager.coinBalance) }
    var unlockDialogId     by remember { mutableStateOf<String?>(null) }
    // Freshly rolled 450–1000 AX each time an unlock dialog is opened —
    // including re-unlocks of an already-unlocked feature — never fixed.
    var unlockCostAx       by remember { mutableStateOf(0) }
    var premiumTick        by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1000L); premiumTick++ } }
    val premiumNow = remember(premiumTick) { System.currentTimeMillis() }

    // Without this, reinstalling the app or logging in on a new device showed
    // all premium features as locked and coin balance as 0, even if the user
    // had previously unlocked them.
    LaunchedEffect(Unit) {
        try {
            val unlocks = RewardRepository.loadPremiumUnlocks()
            if (unlocks != null) {
                // Only override local prefs if Firebase value is more recent
                if (unlocks.coinBalance > prefManager.coinBalance) {
                    prefManager.coinBalance = unlocks.coinBalance
                    axCoinBalance = unlocks.coinBalance
                }
                // Each theme unlocks independently — merge in every per-theme
                // timestamp the cloud has without clobbering a longer local one.
                unlocks.themeUnlocks.forEach { (themeKey, untilMs) ->
                    if (untilMs > prefManager.themeUnlockUntilMs(themeKey)) {
                        prefManager.setThemeUnlockUntilMs(themeKey, untilMs)
                        themeUnlockTick++
                    }
                }
                if (unlocks.restoreUntilMs > prefManager.premRestoreUnlockUntilMs) { prefManager.premRestoreUnlockUntilMs   = unlocks.restoreUntilMs; premRestoreUntilMs = unlocks.restoreUntilMs }
                if (unlocks.resetUntilMs   > prefManager.premResetUnlockUntilMs)   { prefManager.premResetUnlockUntilMs     = unlocks.resetUntilMs;   premResetUntilMs   = unlocks.resetUntilMs   }
                if (unlocks.pdfUntilMs     > prefManager.premPdfExportUnlockUntilMs)   { prefManager.premPdfExportUnlockUntilMs  = unlocks.pdfUntilMs;     premPdfUntilMs     = unlocks.pdfUntilMs     }
                if (unlocks.salaryUntilMs  > prefManager.premSalarySlipUnlockUntilMs)  { prefManager.premSalarySlipUnlockUntilMs = unlocks.salaryUntilMs;  premSalaryUntilMs  = unlocks.salaryUntilMs  }
            }
        } catch (_: Exception) { /* offline — use local prefs */ }
    }

    // ── Account Deletion State ─────────────────────────────────────────────
    var showDeleteAccountDialog  by remember { mutableStateOf(false) }
    var isDeletingAccount        by remember { mutableStateOf(false) }
    var showReauthDialog         by remember { mutableStateOf(false) }
    var reauthPassword           by remember { mutableStateOf("") }
    var reauthError              by remember { mutableStateOf<String?>(null) }
    var isReauthing              by remember { mutableStateOf(false) }

    // ── Dialogs ──────────────────────────────────────────────────────────────

    if (showThemePicker) {
        ThemePickerDialog(
            currentTheme   = currentTheme,
            prefManager    = prefManager,
            coinBalance    = axCoinBalance,
            themeUnlockTick = themeUnlockTick,
            onApplyTheme   = { theme ->
                currentTheme              = theme
                prefManager.selectedTheme = theme.prefsKey
                showThemePicker           = false
                scope.launch { snackbarHostState.showSnackbar("${theme.displayName} theme applied! Restarting…") }
                activity?.recreate()
            },
            onThemeUnlocked = { themeKey, newUntilMs, coinsDelta, newBal ->
                axCoinBalance = newBal
                themeUnlockTick++
                scope.launch {
                    runCatching {
                        RewardRepository.saveThemeUnlock(
                            coinsDelta = coinsDelta,
                            themeKey   = themeKey,
                            untilMs    = newUntilMs
                        )
                    }
                }
            },
            onNavigateToRewards = {
                showThemePicker = false
                navController.navigate(com.aaryo.selfattendance.ui.navigation.Routes.REWARDS) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState    = true
                }
            },
            onDismiss = { showThemePicker = false }
        )
    }

    // ── Salary Slip — Month Picker Dialog ─────────────────────────────────────
    if (showSlipMonthPicker) {
        val monthOptions = (0..11).map { YearMonth.now().minusMonths(it.toLong()) }
        AlertDialog(
            onDismissRequest = { showSlipMonthPicker = false },
            icon  = { Text("📊", fontSize = 30.sp) },
            title = {
                Text(
                    "Select Month for Salary Slip",
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier            = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    monthOptions.forEach { month ->
                        val isSelected = month == selectedSlipMonth
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedSlipMonth = month
                                    showSlipMonthPicker = false

                                    // Immediately trigger export after month picked
                                    if (isSalarySlipExporting) return@clickable
                                    isSalarySlipExporting = true
                                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                                    if (uid == null) {
                                        isSalarySlipExporting = false
                                        scope.launch { snackbarHostState.showSnackbar("Please log in first") }
                                        return@clickable
                                    }
                                    scope.launch {
                                        try {
                                            val targetMonth = month
                                            val (attendanceList, profile) = withContext(Dispatchers.IO) {
                                                val att = AttendanceRepository().getAllAttendance(uid)
                                                    .filter {
                                                        try { java.time.LocalDate.parse(it.date).let { d ->
                                                            java.time.YearMonth.from(d) == targetMonth
                                                        } } catch (_: Exception) { false }
                                                    }
                                                val prof = ProfileRepository().getProfile(uid).getOrNull()
                                                att to prof
                                            }
                                            if (attendanceList.isEmpty()) {
                                                isSalarySlipExporting = false
                                                snackbarHostState.showSnackbar("No attendance records for ${targetMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))}")
                                                return@launch
                                            }
                                            val safeProfile = profile ?: run {
                                                isSalarySlipExporting = false
                                                snackbarHostState.showSnackbar("Profile not found. Please set up your profile first.")
                                                return@launch
                                            }
                                            val act = activity as? android.app.Activity
                                            if (act != null) {
                                                AdsController.showInterstitialNow(act) {
                                                    scope.launch {
                                                        try {
                                                            val slipUri = withContext(Dispatchers.IO) {
                                                                SalarySlipExporter.export(context, safeProfile, attendanceList, targetMonth)
                                                            }
                                                            val monthLabel = targetMonth.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH))
                                                            snackbarHostState.showSnackbar("✅ Salary slip saved → Documents/SelfAttendance ($monthLabel)")
                                                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                                type = "application/pdf"
                                                                putExtra(android.content.Intent.EXTRA_STREAM, slipUri)
                                                                putExtra(android.content.Intent.EXTRA_SUBJECT, "Salary Slip - $monthLabel")
                                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            }
                                                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Salary Slip via").apply {
                                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            })
                                                        } catch (e: Exception) {
                                                            snackbarHostState.showSnackbar("Salary slip export failed: ${e.message ?: "Try again."}")
                                                        } finally {
                                                            isSalarySlipExporting = false
                                                        }
                                                    }
                                                }
                                            } else {
                                                val slipUri = withContext(Dispatchers.IO) {
                                                    SalarySlipExporter.export(context, safeProfile, attendanceList, targetMonth)
                                                }
                                                val monthLabel = targetMonth.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH))
                                                snackbarHostState.showSnackbar("✅ Salary slip saved → Documents/SelfAttendance ($monthLabel)")
                                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                    type = "application/pdf"
                                                    putExtra(android.content.Intent.EXTRA_STREAM, slipUri)
                                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Salary Slip - $monthLabel")
                                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Salary Slip via").apply {
                                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                })
                                                isSalarySlipExporting = false
                                            }
                                        } catch (_: Exception) {
                                            isSalarySlipExporting = false
                                            snackbarHostState.showSnackbar("Failed to fetch data. Try again.")
                                        }
                                    }
                                },
                            shape  = RoundedCornerShape(10.dp),
                            color  = if (isSelected)
                                         MaterialTheme.colorScheme.primaryContainer
                                     else
                                         MaterialTheme.colorScheme.surface,
                            tonalElevation = if (isSelected) 4.dp else 0.dp
                        ) {
                            Row(
                                modifier            = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment   = Alignment.CenterVertically
                            ) {
                                Text(
                                    text  = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier   = Modifier.weight(1f)
                                )
                                if (month == YearMonth.now()) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            "Current",
                                            style    = MaterialTheme.typography.labelSmall,
                                            color    = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint     = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSlipMonthPicker = false }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // ── Language Picker Dialog ─────────────────────────────────────────────────
    if (showLangPicker) {
        LanguagePickerDialog(
            currentCode     = selectedLanguage,
            onLanguageSelected = { code ->
                selectedLanguage = code
                val defaultCurrency = CurrencyManager.getDefaultCurrencyForLanguage(code)
                val defaultCountry = com.aaryo.selfattendance.utils.CountryManager.getDefaultCountryForLanguage(code)
                selectedCurrency = defaultCurrency
                selectedCountry  = defaultCountry
                prefManager.selectedLanguage = code
                prefManager.selectedCurrency = defaultCurrency
                prefManager.selectedCountry  = defaultCountry
                showLangPicker = false
                activity?.let { com.aaryo.selfattendance.utils.LocaleManager.setLocaleAndRestart(it, code) }
            },
            onDismiss = { showLangPicker = false }
        )
    }


    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title  = { Text(stringResource(R.string.settings_reset)) },
            text   = { Text(stringResource(R.string.settings_reset_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = { showResetDialog = false; backupViewModel.resetAttendance() }) {
                    Text(stringResource(R.string.dialog_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.dialog_cancel)) } }
        )
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title  = { Text(stringResource(R.string.settings_restore)) },
            text   = { Text(stringResource(R.string.settings_restore_confirm_msg)) },
            confirmButton = {
                TextButton(onClick = { showRestoreDialog = false; backupViewModel.restore() }) { Text(stringResource(R.string.dialog_restore)) }
            },
            dismissButton = { TextButton(onClick = { showRestoreDialog = false }) { Text(stringResource(R.string.dialog_cancel)) } }
        )
    }

    // ── Delete Account Dialog ─────────────────────────────────────────────────
    // REQUIRED BY GOOGLE PLAY POLICY: In-app account deletion mandatory
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingAccount) showDeleteAccountDialog = false },
            title  = { Text(stringResource(R.string.settings_delete_account), color = MaterialTheme.colorScheme.error) },
            text   = {
                Column {
                    Text(stringResource(R.string.settings_delete_account_msg))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text  = stringResource(R.string.settings_delete_account_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isDeletingAccount = true
                        scope.launch {
                            try {
                                val result = authRepository.deleteAccount()
                                if (result.isSuccess) {
                                    // Navigate to login screen
                                    val i = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                    i?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    context.startActivity(i)
                                } else {
                                    isDeletingAccount = false
                                    showDeleteAccountDialog = false
                                    val errMsg = result.exceptionOrNull()?.message ?: ""
                                    if (errMsg.contains("requires-recent-login", ignoreCase = true) ||
                                        errMsg.contains("recent login", ignoreCase = true)) {
                                        // Previously only showed a snackbar with no way to re-authenticate,
                                        // leaving the user unable to delete their account.
                                        // Firebase requires recent sign-in for destructive operations.
                                        reauthPassword = ""
                                        reauthError = null
                                        showReauthDialog = true
                                    } else {
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.settings_delete_failed)
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                isDeletingAccount = false
                                showDeleteAccountDialog = false
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.settings_delete_failed)
                                )
                            }
                        }
                    },
                    enabled = !isDeletingAccount
                ) {
                    if (isDeletingAccount) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            stringResource(R.string.settings_delete_account_confirm),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick  = { showDeleteAccountDialog = false },
                    enabled  = !isDeletingAccount
                ) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }

    // ── Re-Authentication Dialog (required-recent-login) ────────────────────
    // after their session has aged, violating Google Play Account Deletion policy.
    if (showReauthDialog) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        AlertDialog(
            onDismissRequest = { if (!isReauthing) showReauthDialog = false },
            title = { Text("Confirm Your Identity", color = MaterialTheme.colorScheme.error) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("For security, please enter your password to confirm account deletion.")
                    OutlinedTextField(
                        value         = reauthPassword,
                        onValueChange = { reauthPassword = it; reauthError = null },
                        label         = { Text("Password") },
                        singleLine    = true,
                        isError       = reauthError != null,
                        supportingText = reauthError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isReauthing && reauthPassword.isNotBlank(),
                    onClick = {
                        isReauthing = true
                        scope.launch {
                            val email = currentUser?.email ?: ""
                            val reAuthResult = authRepository.reAuthenticateEmail(email, reauthPassword)
                            if (reAuthResult.isSuccess) {
                                // Re-auth succeeded — retry account deletion
                                val deleteResult = authRepository.deleteAccount()
                                isReauthing = false
                                showReauthDialog = false
                                if (deleteResult.isSuccess) {
                                    val i = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                    i?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    context.startActivity(i)
                                } else {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.settings_delete_failed)
                                    )
                                }
                            } else {
                                isReauthing = false
                                reauthError = "Incorrect password. Please try again."
                            }
                        }
                    }
                ) {
                    if (isReauthing) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Confirm Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick  = { showReauthDialog = false; reauthPassword = ""; reauthError = null },
                    enabled  = !isReauthing
                ) { Text("Cancel") }
            }
        )
    }

    // ── Premium Unlock Dialog ─────────────────────────────────────────────────
    val unlockFeature = ALL_PREMIUM_FEATURES.find { it.id == unlockDialogId }
    if (unlockFeature != null) {
        val canAfford = axCoinBalance >= unlockCostAx
        Dialog(onDismissRequest = { unlockDialogId = null }) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B263B), RoundedCornerShape(24.dp))
                    .border(
                        1.dp,
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(Color(0xFFFFD700).copy(0.7f), Color(0xFFB8860B).copy(0.4f))
                        ),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(unlockFeature.emoji, fontSize = 44.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${unlockFeature.name} Unlock Karo?",
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = Color.White,
                        textAlign  = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        unlockFeature.desc,
                        fontSize  = 12.sp,
                        color     = Color.White.copy(0.55f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))

                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0D1B2A), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Cost", fontSize = 12.sp, color = Color.White.copy(0.55f))
                                Text(
                                    "$unlockCostAx AX",
                                    fontSize   = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = Color(0xFFFFD700)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Aapka balance", fontSize = 12.sp, color = Color.White.copy(0.55f))
                                Text(
                                    "$axCoinBalance AX",
                                    fontSize   = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = if (canAfford) Color(0xFF06D6A0) else Color(0xFFEF233C)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Duration", fontSize = 12.sp, color = Color.White.copy(0.55f))
                                Text(
                                    "Random 1–5 days 🎲",
                                    fontSize   = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = Color.White.copy(0.8f)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    if (canAfford) {
                        Button(
                            onClick = {
                                val durOptions = listOf(1L, 2L, 3L, 4L, 5L).map { it * 24L * 3600L * 1000L }
                                val newMs  = System.currentTimeMillis() + durOptions[kotlin.random.Random.nextInt(5)]
                                when (unlockFeature.id) {
                                    "restore" -> { prefManager.premRestoreUnlockUntilMs    = newMs; premRestoreUntilMs = newMs }
                                    "reset"   -> { prefManager.premResetUnlockUntilMs      = newMs; premResetUntilMs   = newMs }
                                    "pdf"     -> { prefManager.premPdfExportUnlockUntilMs  = newMs; premPdfUntilMs     = newMs }
                                    "salary"  -> { prefManager.premSalarySlipUnlockUntilMs = newMs; premSalaryUntilMs  = newMs }
                                }
                                CoinSecurityEngine.secureSpendCoins(prefManager, unlockCostAx)
                                val newBal = prefManager.coinBalance
                                axCoinBalance = newBal
                                unlockDialogId = null
                                scope.launch {
                                    snackbarHostState.showSnackbar("✅ ${unlockFeature.name} unlock ho gaya!")
                                    // an atomic delta (-costAx) instead of an absolute balance.
                                    // Without this, unlocked features and coin deduction were only
                                    // stored in SharedPreferences and lost on reinstall / new device
                                    // login — and an absolute-value write could also race with a
                                    // concurrent update from another device/session.
                                    RewardRepository.savePremiumUnlocks(
                                        coinsDelta     = -unlockCostAx,
                                        restoreUntilMs = prefManager.premRestoreUnlockUntilMs,
                                        resetUntilMs   = prefManager.premResetUnlockUntilMs,
                                        pdfUntilMs     = prefManager.premPdfExportUnlockUntilMs,
                                        salaryUntilMs  = prefManager.premSalarySlipUnlockUntilMs
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape    = RoundedCornerShape(14.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700))
                        ) {
                            Text(
                                "🔓 Unlock  $unlockCostAx AX",
                                fontWeight = FontWeight.ExtraBold,
                                color      = Color(0xFF0D1B2A),
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
                                    unlockDialogId = null
                                    navController.navigate(com.aaryo.selfattendance.ui.navigation.Routes.REWARDS) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState    = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape    = RoundedCornerShape(14.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A86FF))
                            ) {
                                Text(
                                    "🎰  Spin Wheel Click Now",
                                    fontWeight = FontWeight.ExtraBold,
                                    color      = Color.White,
                                    fontSize   = 14.sp
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    TextButton(
                        onClick  = { unlockDialogId = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.dialog_cancel),
                            color = Color.White.copy(0.6f)
                        )
                    }
                }
            }
        }
    }

    // ── Scaffold ─────────────────────────────────────────────────────────────

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // BannerAd hata diya — ab MainScreen ke Scaffold mein hai taaki tab switch par reload na ho
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {

            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(16.dp))

            // ── Appearance ────────────────────────────────────────────────────
            SectionTitle(stringResource(R.string.settings_appearance))
            Card(
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier  = Modifier.fillMaxWidth()
            ) {
                Column {
                    GeneralSettingsToggleRow(
                        icon      = Icons.Default.Brightness4,
                        iconColor = Color(0xFF3949AB),
                        title     = stringResource(R.string.settings_dark_mode),
                        subtitle  = if (darkMode) "Switch to light mode" else "Switch to dark mode",
                        checked   = darkMode
                    ) { checked ->
                        darkMode = checked
                        prefManager.isDarkMode = checked
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.settings_theme_updated)) }
                        activity?.recreate()
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 66.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    GeneralSettingsRow(
                        icon        = Icons.Default.Palette,
                        iconColor   = Color(0xFF7B1FA2),
                        title       = stringResource(R.string.settings_theme),
                        subtitle    = "${currentTheme.displayName} · Personalize your app's appearance",
                        showDivider = true
                    ) {
                        showThemePicker = true
                    }
                    GeneralSettingsRow(
                        icon        = Icons.Default.Language,
                        iconColor   = Color(0xFF00838F),
                        title       = stringResource(R.string.settings_language),
                        subtitle    = com.aaryo.selfattendance.utils.LocaleManager.getDisplayName(selectedLanguage),
                        showDivider = false
                    ) { showLangPicker = true }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Preferences ───────────────────────────────────────────────────
            SectionTitle(stringResource(R.string.settings_preferences))
            Card(
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier  = Modifier.fillMaxWidth()
            ) {
                Column {
                    GeneralSettingsToggleRow(
                        icon      = Icons.Default.Fingerprint,
                        iconColor = Color(0xFF2E7D32),
                        title     = stringResource(R.string.settings_biometric),
                        subtitle  = if (biometricEnabled) "Fingerprint / face unlock active" else "Enable biometric login",
                        checked   = biometricEnabled
                    ) { checked ->
                        biometricEnabled = checked
                        prefManager.isBiometricEnabled = checked
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (checked) context.getString(R.string.settings_biometric_enabled)
                                else context.getString(R.string.settings_biometric_disabled)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Data Management ───────────────────────────────────────────────
            SectionTitle(stringResource(R.string.settings_data_management))
            Card(
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier  = Modifier.fillMaxWidth()
            ) {
                Column {
                    GeneralSettingsRow(
                        icon        = Icons.Default.CloudUpload,
                        iconColor   = Color(0xFF1565C0),
                        title       = when {
                            backupLoading  -> stringResource(R.string.settings_please_wait)
                            isBackupLocked -> "🔒 ${stringResource(R.string.settings_backup)}"
                            else           -> stringResource(R.string.settings_backup)
                        },
                        subtitle    = "Securely back up your attendance data to the cloud",
                        showDivider = true,
                        enabled     = !backupLoading
                    ) { backupViewModel.backup() }

                    GeneralSettingsRow(
                        icon        = Icons.Default.CloudDownload,
                        iconColor   = Color(0xFF00695C),
                        title       = when {
                            backupLoading -> stringResource(R.string.settings_please_wait)
                            premRestoreUntilMs <= premiumNow -> "🔒 ${stringResource(R.string.settings_restore)}"
                            else -> stringResource(R.string.settings_restore)
                        },
                        subtitle    = premLabel(premRestoreUntilMs, premiumNow, 0, "Securely restore your data from cloud backup", "Premium feature — unlock to restore your cloud backup"),
                        showDivider = true,
                        enabled     = !backupLoading
                    ) {
                        if (premRestoreUntilMs > premiumNow) showRestoreDialog = true
                        else { unlockCostAx = kotlin.random.Random.nextInt(450, 1001); unlockDialogId = "restore" }
                    }

                    GeneralSettingsRow(
                        icon        = Icons.Default.DeleteSweep,
                        iconColor   = Color(0xFFC62828),
                        title       = if (premResetUntilMs <= premiumNow) "🔒 ${stringResource(R.string.settings_reset)}" else stringResource(R.string.settings_reset),
                        subtitle    = premLabel(premResetUntilMs, premiumNow, 0, "Permanently clear all attendance records", "Premium feature — unlock to clear all records"),
                        showDivider = false,
                        enabled     = !backupLoading
                    ) {
                        if (premResetUntilMs > premiumNow) showResetDialog = true
                        else { unlockCostAx = kotlin.random.Random.nextInt(450, 1001); unlockDialogId = "reset" }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Export ────────────────────────────────────────────────────────
            SectionTitle("📄 Export")
            Card(
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier  = Modifier.fillMaxWidth()
            ) {
                Column {
                    GeneralSettingsRow(
                        icon        = Icons.Default.PictureAsPdf,
                        iconColor   = Color(0xFFD32F2F),
                        title       = when {
                            isPdfExporting -> "Preparing export…"
                            premPdfUntilMs <= premiumNow -> "🔒 Export PDF Report"
                            else -> "Export PDF Report"
                        },
                        subtitle    = premLabel(premPdfUntilMs, premiumNow, 0, "Download your complete attendance report as a PDF", "Premium feature — unlock to download your attendance report"),
                        showDivider = true,
                        enabled     = !isPdfExporting
                    ) {
                        if (premPdfUntilMs <= premiumNow) {
                            unlockCostAx = kotlin.random.Random.nextInt(450, 1001)
                            unlockDialogId = "pdf"
                            return@GeneralSettingsRow
                        }
                        if (isPdfExporting) return@GeneralSettingsRow
                        isPdfExporting = true
                        val uid = FirebaseAuth.getInstance().currentUser?.uid
                        if (uid == null) {
                            isPdfExporting = false
                            scope.launch { snackbarHostState.showSnackbar("Please log in to export PDF") }
                            return@GeneralSettingsRow
                        }
                        scope.launch {
                            try {
                                val attendanceList = withContext(Dispatchers.IO) {
                                    AttendanceRepository().getAllAttendance(uid)
                                }
                                if (attendanceList.isEmpty()) {
                                    isPdfExporting = false
                                    snackbarHostState.showSnackbar("No attendance records found to export")
                                    return@launch
                                }
                                val act = activity as? Activity
                                if (act != null) {
                                    AdsController.showInterstitialNow(act) {
                                        scope.launch {
                                            try {
                                                val pdfUri = withContext(Dispatchers.IO) { PdfExporter.export(context, attendanceList) }
                                                snackbarHostState.showSnackbar("✅ PDF saved to Documents/SelfAttendance")
                                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                    type = "application/pdf"
                                                    putExtra(android.content.Intent.EXTRA_STREAM, pdfUri)
                                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Self Attendance Report")
                                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share PDF via").apply {
                                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                })
                                            } catch (e: Exception) {
                                                snackbarHostState.showSnackbar("PDF export failed: ${e.message ?: "Try again."}")
                                            } finally {
                                                isPdfExporting = false
                                            }
                                        }
                                    }
                                } else {
                                    val pdfUri = withContext(Dispatchers.IO) { PdfExporter.export(context, attendanceList) }
                                    snackbarHostState.showSnackbar("✅ PDF saved to Documents/SelfAttendance")
                                    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(android.content.Intent.EXTRA_STREAM, pdfUri)
                                        putExtra(android.content.Intent.EXTRA_SUBJECT, "Self Attendance Report")
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share PDF via").apply {
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    })
                                    isPdfExporting = false
                                }
                            } catch (_: Exception) {
                                isPdfExporting = false
                                snackbarHostState.showSnackbar("Failed to fetch attendance. Try again.")
                            }
                        }
                    }

                    GeneralSettingsRow(
                        icon        = Icons.Default.Receipt,
                        iconColor   = Color(0xFF388E3C),
                        title       = when {
                            isSalarySlipExporting -> "Generating slip…"
                            premSalaryUntilMs <= premiumNow -> "🔒 Generate Salary Slip"
                            else -> "Generate Salary Slip"
                        },
                        subtitle    = premLabel(premSalaryUntilMs, premiumNow, 0, "Generate your monthly salary slip as a PDF", "Premium feature — unlock to generate your salary slip"),
                        showDivider = false,
                        enabled     = !isSalarySlipExporting
                    ) {
                        if (premSalaryUntilMs > premiumNow) showSlipMonthPicker = true
                        else { unlockCostAx = kotlin.random.Random.nextInt(450, 1001); unlockDialogId = "salary" }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── General ───────────────────────────────────────────────────────
            SectionTitle(stringResource(R.string.settings_general))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    GeneralSettingsRow(
                        icon       = Icons.Default.GppGood,
                        iconColor  = MaterialTheme.colorScheme.primary,
                        title      = stringResource(R.string.settings_privacy),
                        subtitle   = "Read our privacy policy",
                        showDivider = true
                    ) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/self-attendance-privacy-policy/home"))) }

                    GeneralSettingsRow(
                        icon       = Icons.Default.Description,
                        iconColor  = Color(0xFF2563EB),
                        title      = stringResource(R.string.settings_terms),
                        subtitle   = "Read terms and conditions",
                        showDivider = true
                    ) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/self-terms-and-conditions/home"))) }

                    GeneralSettingsRow(
                        icon       = Icons.Default.Info,
                        iconColor  = MaterialTheme.colorScheme.primary,
                        title      = stringResource(R.string.settings_about),
                        subtitle   = "View app version and details",
                        showDivider = false
                    ) { navController.navigate("about") }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── More ──────────────────────────────────────────────────────────
            SectionTitle("More")
            Card(
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier  = Modifier.fillMaxWidth()
            ) {
                Column {
                    GeneralSettingsRow(
                        icon        = Icons.Default.Star,
                        iconColor   = Color(0xFFFFB300),
                        title       = stringResource(R.string.settings_rate),
                        subtitle    = "Love the app? Give us a 5-star rating",
                        showDivider = true
                    ) {
                        val act = activity as? Activity
                        if (act != null) {
                            InAppReviewManager.getInstance(context).requestReviewFlow(
                                activity = act,
                                fallbackToPlayStoreIfUnavailable = true
                            )
                        } else {
                            InAppReviewManager.getInstance(context).openPlayStoreListing(context)
                        }
                    }

                    GeneralSettingsRow(
                        icon        = Icons.Default.Share,
                        iconColor   = Color(0xFF7B1FA2),
                        title       = stringResource(R.string.settings_refer),
                        subtitle    = "Refer karein → 450 AX Coins kamayein!",
                        showDivider = true
                    ) {
                        navController.navigate(com.aaryo.selfattendance.ui.navigation.Routes.REFER_AND_EARN)
                    }

                    GeneralSettingsRow(
                        icon        = Icons.Default.Email,
                        iconColor   = Color(0xFF0288D1),
                        title       = "Contact Us",
                        subtitle    = "Questions or feedback? Get in touch with our team",
                        showDivider = true
                    ) {
                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:")
                            putExtra(Intent.EXTRA_EMAIL, arrayOf("yogeshkumar53076@gmail.com"))
                            putExtra(Intent.EXTRA_SUBJECT, "Self Attendance — Support")
                        }
                        try {
                            context.startActivity(emailIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Koi email app installed nahi mila", Toast.LENGTH_SHORT).show()
                        }
                    }

                    GeneralSettingsRow(
                        icon        = Icons.AutoMirrored.Filled.ExitToApp,
                        iconColor   = Color(0xFFE65100),
                        title       = stringResource(R.string.settings_logout),
                        subtitle    = stringResource(R.string.settings_logout_subtitle),
                        showDivider = false
                    ) {
                        // Logout pe user-specific data clear karo taaki agla login
                        // karne wala user purane account ke coins na dekhe.
                        // Device settings (dark mode, language, reminders) safe hain.
                        prefManager.clearUserData()
                        FirebaseAuth.getInstance().signOut()
                        val i = context.packageManager.getLaunchIntentForPackage(context.packageName)
                        i?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        context.startActivity(i)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Danger Zone ───────────────────────────────────────────────────
            SectionTitle(stringResource(R.string.settings_danger_zone))
            Card(
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
                ),
                border    = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
                ),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier  = Modifier.fillMaxWidth()
            ) {
                Column {
                    GeneralSettingsRow(
                        icon        = Icons.Default.DeleteForever,
                        iconColor   = MaterialTheme.colorScheme.error,
                        title       = stringResource(R.string.settings_delete_account),
                        subtitle    = stringResource(R.string.settings_delete_account_subtitle),
                        showDivider = false
                    ) { showDeleteAccountDialog = true }
                }
            }

            Spacer(Modifier.height(24.dp))

            if (remoteConfig.showNativeAd()) {
                Text(stringResource(R.string.dashboard_sponsored), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(6.dp))
                // NativeAdView singleton use karo taaki wapas aane par ad reload na ho
                AdsController.NativeAdView()
            }

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.app_name))
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Section title helper
// ═══════════════════════════════════════════════════════════════

// ─────────────────────────────────────────────────────────────────────────────
//  Premium feature subtitle helper
//  lockedCost  = AX coins needed
//  unlockedTag = extra info shown when active (e.g. theme name)
// ─────────────────────────────────────────────────────────────────────────────
private fun premLabel(
    untilMs    : Long,
    nowMs      : Long,
    lockedCost : Int,
    unlockedTag: String = "",
    lockedDesc : String = ""
): String {
    val rem = untilMs - nowMs
    return if (rem > 0) {
        val s   = rem / 1000L
        val d   = s / 86400L
        val h   = (s % 86400L) / 3600L
        val m   = (s % 3600L) / 60L
        val sec = s % 60L
        val time = if (d > 0) "${d}d ${"%02d".format(h)}:${"%02d".format(m)}:${"%02d".format(sec)}"
                   else "${"%02d".format(h)}:${"%02d".format(m)}:${"%02d".format(sec)}"
        "✅ $time remaining${if (unlockedTag.isNotBlank()) "  ·  $unlockedTag" else ""}"
    } else lockedDesc.ifBlank { "🔒 $lockedCost AX coins se unlock karo" }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier   = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingsToggle(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}

@Composable
fun SettingsButton(title: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick   = onClick,
        enabled   = enabled,
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(12.dp)
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
    }
}

// ── Data Management styled button (purple filled, icon + subtitle + arrow) ──
@Composable
fun DataManagementButton(
    icon    : ImageVector,
    title   : String,
    subtitle: String,
    enabled : Boolean = true,
    onClick : () -> Unit
) {
    val bgColor = if (enabled)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)

    Surface(
        onClick   = onClick,
        enabled   = enabled,
        shape     = RoundedCornerShape(14.dp),
        color     = bgColor,
        modifier  = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = title,
                    tint               = Color.White,
                    modifier           = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = title,
                    color      = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 15.sp
                )
                Text(
                    text     = subtitle,
                    color    = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp
                )
            }

            Icon(
                imageVector        = Icons.Default.ChevronRight,
                contentDescription = null,
                tint               = Color.White.copy(alpha = 0.75f),
                modifier           = Modifier.size(20.dp)
            )
        }
    }
}

// ── General settings row (card row with colored icon + title + subtitle + arrow) ──
@Composable
fun GeneralSettingsRow(
    icon       : ImageVector,
    iconColor  : Color,
    title      : String,
    subtitle   : String,
    showDivider: Boolean = true,
    enabled    : Boolean = true,
    onClick    : () -> Unit
) {
    val contentAlpha = if (enabled) 1f else 0.45f
    Column {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconColor.copy(alpha = if (enabled) 0.12f else 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = title,
                    tint               = iconColor.copy(alpha = contentAlpha),
                    modifier           = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = title,
                    fontWeight = FontWeight.Medium,
                    fontSize   = 15.sp,
                    color      = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
                )
                Text(
                    text     = subtitle,
                    fontSize = 12.sp,
                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.55f else 0.3f)
                )
            }

            Icon(
                imageVector        = Icons.Default.ChevronRight,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.35f else 0.2f),
                modifier           = Modifier.size(18.dp)
            )
        }

        if (showDivider) {
            HorizontalDivider(
                modifier  = Modifier.padding(start = 66.dp),
                thickness = 0.5.dp,
                color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        }
    }
}

// ── Toggle variant (Switch instead of ChevronRight) ──────────────────────────
@Composable
fun GeneralSettingsToggleRow(
    icon      : ImageVector,
    iconColor : Color,
    title     : String,
    subtitle  : String,
    checked   : Boolean,
    onToggle  : (Boolean) -> Unit
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier         = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = title,
                tint               = iconColor,
                modifier           = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = title,
                fontWeight = FontWeight.Medium,
                fontSize   = 15.sp,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text     = subtitle,
                fontSize = 12.sp,
                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }

        Switch(
            checked         = checked,
            onCheckedChange = onToggle
        )
    }
}

@Composable
private fun SettingsPickerRow(label: String, valueLabel: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Text(valueLabel, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ThemePickerDialog(
    currentTheme        : AppTheme,
    prefManager         : PreferencesManager,
    coinBalance         : Int,
    themeUnlockTick     : Long,
    onApplyTheme        : (AppTheme) -> Unit,
    // (themeKey, newUntilMs, coinsDelta, newCoinBalance)
    onThemeUnlocked     : (String, Long, Int, Int) -> Unit,
    onNavigateToRewards : () -> Unit,
    onDismiss           : () -> Unit
) {
    val now = remember(themeUnlockTick) { System.currentTimeMillis() }

    // Each theme is its own premium unlock — tapping a locked theme opens
    // this confirm dialog with a freshly-rolled 450–1000 AX price, re-rolled
    // every single time (initial unlock or any re-unlock).
    var unlockTarget by remember { mutableStateOf<AppTheme?>(null) }
    var unlockCostAx by remember { mutableIntStateOf(0) }

    unlockTarget?.let { theme ->
        val canAfford = coinBalance >= unlockCostAx
        Dialog(onDismissRequest = { unlockTarget = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B263B), RoundedCornerShape(24.dp))
                    .border(
                        1.dp,
                        Brush.linearGradient(listOf(Color(0xFFFFD700).copy(0.7f), Color(0xFFB8860B).copy(0.4f))),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(theme.emoji, fontSize = 44.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${theme.displayName} Unlock Karo?",
                        fontSize   = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = Color.White,
                        textAlign  = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Har theme alag se unlock hota hai — is theme ka unlock doosre themes pe apply nahi hoga.",
                        fontSize  = 12.sp,
                        color     = Color.White.copy(0.55f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0D1B2A), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Cost", fontSize = 12.sp, color = Color.White.copy(0.55f))
                                Text("$unlockCostAx AX", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700))
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Aapka balance", fontSize = 12.sp, color = Color.White.copy(0.55f))
                                Text(
                                    "$coinBalance AX",
                                    fontSize   = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color      = if (canAfford) Color(0xFF06D6A0) else Color(0xFFEF233C)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Duration", fontSize = 12.sp, color = Color.White.copy(0.55f))
                                Text("Random 1–5 days 🎲", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(0.8f))
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    if (canAfford) {
                        Button(
                            onClick = {
                                val durOptions = listOf(1L, 2L, 3L, 4L, 5L).map { it * 24L * 3600L * 1000L }
                                val newMs  = System.currentTimeMillis() + durOptions[Random.nextInt(5)]
                                prefManager.setThemeUnlockUntilMs(theme.prefsKey, newMs)
                                CoinSecurityEngine.secureSpendCoins(prefManager, unlockCostAx)
                                val newBal = prefManager.coinBalance
                                onThemeUnlocked(theme.prefsKey, newMs, -unlockCostAx, newBal)
                                unlockTarget = null
                                onApplyTheme(theme)
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape    = RoundedCornerShape(14.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700))
                        ) {
                            Text(
                                "🔓 Unlock  $unlockCostAx AX",
                                fontWeight = FontWeight.ExtraBold,
                                color      = Color(0xFF0D1B2A),
                                fontSize   = 14.sp
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Coins kam hain! Pehle spin wheel se coins kamao.",
                                fontSize  = 12.sp,
                                color     = Color(0xFFEF233C),
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick  = { unlockTarget = null; onNavigateToRewards() },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape    = RoundedCornerShape(14.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A86FF))
                            ) {
                                Text("🎰  Spin Wheel Pe Jao", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 14.sp)
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

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {

                // Header
                Text(
                    stringResource(R.string.settings_theme),
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Each theme is unlocked individually",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns               = GridCells.Fixed(3),
                    modifier              = Modifier.height(220.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement   = Arrangement.spacedBy(8.dp)
                ) {
                    items(AppTheme.entries.toList()) { theme ->
                        val selected = theme == currentTheme
                        val unlocked = prefManager.themeUnlockUntilMs(theme.prefsKey) > now
                        val color by animateColorAsState(
                            targetValue   = theme.primary,
                            animationSpec = tween(300),
                            label         = "themeColor"
                        )

                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (unlocked) color else color.copy(alpha = 0.35f))
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    if (unlocked) {
                                        onApplyTheme(theme)
                                    } else {
                                        // Roll a fresh random price every time this theme's
                                        // unlock dialog is opened — never a fixed price.
                                        unlockCostAx = Random.nextInt(450, 1001)
                                        unlockTarget = theme
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                !unlocked -> Icon(Icons.Default.Lock, null, tint = Color.White)
                                selected  -> Icon(Icons.Default.Check, null, tint = Color.White)
                            }
                            // Theme emoji in bottom-right corner
                            Text(
                                theme.emoji,
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(3.dp)
                            )
                        }

                        // Theme name below swatch
                        Text(
                            theme.displayName.substringBefore(" "),
                            style     = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            color     = MaterialTheme.colorScheme.onSurface,
                            modifier  = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguagePickerDialog(
    currentCode: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                com.aaryo.selfattendance.utils.LocaleManager.SUPPORTED_LANGUAGES.forEach { (code, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageSelected(code) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(name, modifier = Modifier.weight(1f))
                        if (code == currentCode) {
                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryPickerDialog(
    currentCode: String,
    onCountrySelected: (com.aaryo.selfattendance.utils.CountryManager.Country) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredCountries = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            com.aaryo.selfattendance.utils.CountryManager.SUPPORTED_COUNTRIES
        } else {
            com.aaryo.selfattendance.utils.CountryManager.SUPPORTED_COUNTRIES.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.code.contains(searchQuery, ignoreCase = true) ||
                it.currency.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text  = "🌍 Select Country",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = "Language & currency will automatically update",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search country...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                )

                Spacer(Modifier.height(10.dp))
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .heightIn(max = 340.dp)
                        .verticalScroll(scrollState)
                ) {
                    if (filteredCountries.isEmpty()) {
                        Text(
                            "No countries found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp)
                        )
                    } else {
                        filteredCountries.forEach { country ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCountrySelected(country) }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(country.flag, fontSize = 20.sp, modifier = Modifier.padding(end = 12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(country.name, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${country.currency} · ${country.code}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (country.code == currentCode) {
                                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick  = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) { Text(stringResource(R.string.common_cancel)) }
            }
        }
    }
}

@Composable
private fun CurrencyPickerDialog(
    currentCode: String,
    onCurrencySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredCurrencies = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            com.aaryo.selfattendance.utils.CurrencyManager.SUPPORTED_CURRENCIES
        } else {
            com.aaryo.selfattendance.utils.CurrencyManager.SUPPORTED_CURRENCIES.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.code.contains(searchQuery, ignoreCase = true) ||
                it.symbol.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text  = "💱 Select Currency",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = "Used for salary and attendance calculations",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search currency...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                )

                Spacer(Modifier.height(10.dp))
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .heightIn(max = 340.dp)
                        .verticalScroll(scrollState)
                ) {
                    if (filteredCurrencies.isEmpty()) {
                        Text(
                            "No currencies found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp)
                        )
                    } else {
                        filteredCurrencies.forEach { curr ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCurrencySelected(curr.code) }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${curr.flag} ${curr.symbol}", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp))
                                Text("${curr.name} (${curr.code})", modifier = Modifier.weight(1f))
                                if (curr.code == currentCode) {
                                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick  = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) { Text(stringResource(R.string.common_cancel)) }
            }
        }
    }
}

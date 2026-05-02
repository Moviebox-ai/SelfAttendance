package com.aaryo.selfattendance.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.aaryo.selfattendance.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aaryo.selfattendance.ads.AdsController
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.aaryo.selfattendance.data.remote.RemoteConfigManager
import com.aaryo.selfattendance.data.repository.AuthRepository
import com.aaryo.selfattendance.ui.theme.AppTheme
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(navController: NavController) {

    val context  = LocalContext.current
    val activity = context as? Activity

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
    var notifications     by remember { mutableStateOf(prefManager.isReminderEnabled) }
    var biometricEnabled  by remember { mutableStateOf(prefManager.isBiometricEnabled) }
    var currentTheme      by remember { mutableStateOf(AppTheme.fromKey(prefManager.selectedTheme)) }
    var selectedLanguage  by remember { mutableStateOf(prefManager.selectedLanguage) }
    var showThemePicker   by remember { mutableStateOf(false) }
    var showLangPicker    by remember { mutableStateOf(false) }
    var showResetDialog   by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    // ── Account Deletion State ─────────────────────────────────────────────
    var showDeleteAccountDialog  by remember { mutableStateOf(false) }
    var isDeletingAccount        by remember { mutableStateOf(false) }
    // BUG FIX: Re-auth dialog for expired session (required-recent-login error)
    var showReauthDialog         by remember { mutableStateOf(false) }
    var reauthPassword           by remember { mutableStateOf("") }
    var reauthError              by remember { mutableStateOf<String?>(null) }
    var isReauthing              by remember { mutableStateOf(false) }

    // ── Dialogs ──────────────────────────────────────────────────────────────

    if (showThemePicker) {
        ThemePickerDialog(
            currentTheme    = currentTheme,
            onThemeSelected = { theme ->
                currentTheme           = theme
                prefManager.selectedTheme = theme.prefsKey
                showThemePicker        = false
                scope.launch { snackbarHostState.showSnackbar("${theme.displayName} theme applied! Restarting…") }
                activity?.recreate()
            },
            onDismiss = { showThemePicker = false }
        )
    }

    // ── Language Picker Dialog ─────────────────────────────────────────────────
    if (showLangPicker) {
        LanguagePickerDialog(
            currentCode     = selectedLanguage,
            onLanguageSelected = { code ->
                selectedLanguage = code
                prefManager.selectedLanguage = code
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
                                        // BUG FIX: Show re-auth dialog instead of just a snackbar
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
    // BUG FIX: Previously missing. Without this, email users cannot delete account
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

    // ── Scaffold ─────────────────────────────────────────────────────────────

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar  = { if (remoteConfig.showBannerAd()) AdsController.BannerAd() },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {

            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(24.dp))

            // ── Appearance ────────────────────────────────────────────────────
            SectionTitle(stringResource(R.string.settings_appearance))

            SettingsToggle(stringResource(R.string.settings_dark_mode), darkMode) {
                darkMode = it
                prefManager.isDarkMode = it
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.settings_theme_updated)) }
                activity?.recreate()
            }

            Spacer(Modifier.height(16.dp))

            // ── Theme Picker Row ──────────────────────────────────────────────
            ThemePickerRow(currentTheme = currentTheme, onClick = { showThemePicker = true })

            Spacer(Modifier.height(12.dp))

            // ── Language Picker Row ───────────────────────────────────────────
            SettingsPickerRow(
                label       = stringResource(R.string.settings_language),
                valueLabel  = com.aaryo.selfattendance.utils.LocaleManager
                    .SUPPORTED_LANGUAGES[selectedLanguage] ?: "English",
                onClick     = { showLangPicker = true }
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ── Preferences ───────────────────────────────────────────────────
            SectionTitle(stringResource(R.string.settings_preferences))

            SettingsToggle(stringResource(R.string.settings_daily_reminder), notifications) {
                notifications = it
                prefManager.isReminderEnabled = it
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (it) context.getString(R.string.settings_reminder_enabled)
                        else context.getString(R.string.settings_reminder_disabled)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            SettingsToggle(stringResource(R.string.settings_biometric), biometricEnabled) {
                biometricEnabled = it
                prefManager.isBiometricEnabled = it
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (it) context.getString(R.string.settings_biometric_enabled)
                        else context.getString(R.string.settings_biometric_disabled)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ── Data Management ───────────────────────────────────────────────
            SectionTitle(stringResource(R.string.settings_data_management))

            Spacer(Modifier.height(12.dp))

            SettingsButton(
                title   = when {
                    backupLoading  -> stringResource(R.string.settings_please_wait)
                    isBackupLocked -> "🔒 ${stringResource(R.string.settings_backup)}"
                    else           -> stringResource(R.string.settings_backup)
                },
                enabled = !backupLoading
            ) {
                activity?.let { AdsController.showInterstitialAfterSave(it) }
                backupViewModel.backup()
            }
            Spacer(Modifier.height(12.dp))
            SettingsButton(
                title   = when {
                    backupLoading  -> stringResource(R.string.settings_please_wait)
                    isBackupLocked -> "🔒 ${stringResource(R.string.settings_restore)}"
                    else           -> stringResource(R.string.settings_restore)
                },
                enabled = !backupLoading
            ) {
                activity?.let { AdsController.showInterstitialAfterSave(it) }
                showRestoreDialog = true
            }
            Spacer(Modifier.height(12.dp))
            SettingsButton(stringResource(R.string.settings_reset), !backupLoading) {
                activity?.let { AdsController.showInterstitialAfterSave(it) }
                showResetDialog = true
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ── General ───────────────────────────────────────────────────────
            SectionTitle(stringResource(R.string.settings_general))

            SettingsButton(stringResource(R.string.settings_privacy)) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/self-attendance-privacy-policy/home")))
            }
            Spacer(Modifier.height(10.dp))
            SettingsButton(stringResource(R.string.settings_terms)) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/self-terms-and-conditions/home")))
            }
            Spacer(Modifier.height(10.dp))
            SettingsButton(stringResource(R.string.settings_about)) { navController.navigate("about") }
            Spacer(Modifier.height(10.dp))
            SettingsButton(stringResource(R.string.settings_rate)) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.aaryo.selfattendance")))
            }
            Spacer(Modifier.height(10.dp))
            SettingsButton(stringResource(R.string.settings_refer)) {
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, context.getString(R.string.settings_refer_text))
                }
                context.startActivity(Intent.createChooser(i, context.getString(R.string.settings_share_via)))
            }
            Spacer(Modifier.height(16.dp))
            SettingsButton(stringResource(R.string.settings_logout)) {
                FirebaseAuth.getInstance().signOut()
                val i = context.packageManager.getLaunchIntentForPackage(context.packageName)
                i?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context.startActivity(i)
            }

            Spacer(Modifier.height(12.dp))

            // ── Delete Account (Play Store Mandatory) ─────────────────────────
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            SectionTitle(stringResource(R.string.settings_danger_zone))
            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick  = { showDeleteAccountDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                )
            ) {
                Text(stringResource(R.string.settings_delete_account))
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text  = stringResource(R.string.settings_delete_account_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Spacer(Modifier.height(24.dp))

            if (remoteConfig.showNativeAd()) {
                Text(stringResource(R.string.dashboard_sponsored), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(6.dp))
                AdsController.NativeAdView()
            }

            Spacer(Modifier.height(20.dp))
            Text("Self Attendance Pro")
            Text(stringResource(R.string.settings_version), style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Section title helper
// ═══════════════════════════════════════════════════════════════

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
        modifier  = Modifier.fillMaxWidth()
    ) {
        Text(title)
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
private fun ThemePickerRow(currentTheme: AppTheme, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.settings_theme), modifier = Modifier.weight(1f))
        Text(currentTheme.displayName, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ThemePickerDialog(
    currentTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.height(160.dp)) {
                    items(AppTheme.values().toList()) { theme ->
                        val selected = theme == currentTheme
                        val color by animateColorAsState(
                            targetValue = theme.primary,
                            animationSpec = tween(300),
                            label = "themeColor"
                        )
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { onThemeSelected(theme) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) Icon(Icons.Default.Check, null, tint = Color.White)
                        }
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

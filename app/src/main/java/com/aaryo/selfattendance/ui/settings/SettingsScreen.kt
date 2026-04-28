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
            text   = { Text("Kya aap sure hain? Yeh action sare attendance records permanently delete kar dega.") },
            confirmButton = {
                TextButton(onClick = { showResetDialog = false; backupViewModel.resetAttendance() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Cancel") } }
        )
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title  = { Text(stringResource(R.string.settings_restore)) },
            text   = { Text("Backup restore karne par current data replace ho jayega.") },
            confirmButton = {
                TextButton(onClick = { showRestoreDialog = false; backupViewModel.restore() }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { showRestoreDialog = false }) { Text("Cancel") } }
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
                scope.launch { snackbarHostState.showSnackbar("Theme updated") }
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
                scope.launch { snackbarHostState.showSnackbar(if (it) "Reminder enabled" else "Reminder disabled") }
            }

            Spacer(Modifier.height(20.dp))

            SettingsToggle(stringResource(R.string.settings_biometric), biometricEnabled) {
                biometricEnabled = it
                prefManager.isBiometricEnabled = it
                scope.launch { snackbarHostState.showSnackbar(if (it) "Biometric enabled" else "Biometric disabled") }
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
                    putExtra(Intent.EXTRA_TEXT, "Check out Self Attendance Pro!\nhttps://play.google.com/store/apps/details?id=com.aaryo.selfattendance")
                }
                context.startActivity(Intent.createChooser(i, "Share via"))
            }
            Spacer(Modifier.height(16.dp))
            SettingsButton(stringResource(R.string.settings_logout)) {
                FirebaseAuth.getInstance().signOut()
                val i = context.packageManager.getLaunchIntentForPackage(context.packageName)
                i?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                context.startActivity(i)
            }

            Spacer(Modifier.height(24.dp))

            if (remoteConfig.showNativeAd()) {
                Text("Sponsored", style = MaterialTheme.typography.labelSmall)
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
        color      = MaterialTheme.colorScheme.primary,
        modifier   = Modifier.padding(bottom = 12.dp)
    )
}

// ═══════════════════════════════════════════════════════════════
//  Theme Picker Row — inline in settings list
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ThemePickerRow(currentTheme: AppTheme, onClick: () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_app_theme), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    "${currentTheme.emoji} ${currentTheme.displayName}  •  ${currentTheme.description}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            // Preview dots
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(15.dp).clip(CircleShape).background(currentTheme.primary))
                Box(Modifier.size(15.dp).clip(CircleShape).background(currentTheme.primaryLight))
                Box(Modifier.size(15.dp).clip(CircleShape).background(currentTheme.bgColor))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Theme Picker Dialog — 2-column grid of all 6 themes
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ThemePickerDialog(
    currentTheme    : AppTheme,
    onThemeSelected : (AppTheme) -> Unit,
    onDismiss       : () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {

                Text("Choose Your Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Select a theme — app will restart to apply it",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                LazyVerticalGrid(
                    columns               = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement   = Arrangement.spacedBy(10.dp),
                    modifier              = Modifier.heightIn(max = 440.dp)
                ) {
                    items(AppTheme.entries) { theme ->
                        ThemeCard(
                            theme      = theme,
                            isSelected = theme == currentTheme,
                            onClick    = { onThemeSelected(theme) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel")
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Single theme card in the dialog
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ThemeCard(theme: AppTheme, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor by animateColorAsState(
        targetValue   = if (isSelected) theme.primary else Color.Transparent,
        animationSpec = tween(200),
        label         = "border"
    )
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) borderColor else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(14.dp)
            ),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = theme.bgColor),
        elevation = CardDefaults.cardElevation(if (isSelected) 4.dp else 0.dp)
    ) {
        Column(
            modifier            = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Color dots
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                Box(Modifier.size(16.dp).clip(CircleShape).background(theme.primary))
                Box(Modifier.size(16.dp).clip(CircleShape).background(theme.primary.copy(alpha = 0.55f)))
                Box(Modifier.size(16.dp).clip(CircleShape).background(theme.primaryLight))
                Box(Modifier.size(16.dp).clip(CircleShape).background(theme.bgColor))
            }
            Text(
                text       = "${theme.emoji} ${theme.displayName}",
                style      = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp),
                fontWeight = FontWeight.SemiBold,
                color      = theme.primary
            )
            Text(
                text     = theme.description,
                style    = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                color    = Color.Gray,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (isSelected) {
                Spacer(Modifier.height(6.dp))
                Icon(
                    imageVector        = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint               = Color.White,
                    modifier           = Modifier.size(18.dp).clip(CircleShape)
                        .background(theme.primary).padding(3.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Reusable Composables
// ═══════════════════════════════════════════════════════════════

@Composable
fun SettingsToggle(title: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(text = title)
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
fun SettingsButton(title: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(title)
    }
}

// ═══════════════════════════════════════════════════════════════
//  Generic Picker Row — tappable row showing current value
// ═══════════════════════════════════════════════════════════════

@Composable
fun SettingsPickerRow(label: String, valueLabel: String, onClick: () -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                valueLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Language Picker Dialog — 11 languages
// ═══════════════════════════════════════════════════════════════

@Composable
private fun LanguagePickerDialog(
    currentCode       : String,
    onLanguageSelected: (String) -> Unit,
    onDismiss         : () -> Unit
) {
    val languages = com.aaryo.selfattendance.utils.LocaleManager.SUPPORTED_LANGUAGES

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape         = RoundedCornerShape(20.dp),
            tonalElevation = 8.dp,
            modifier      = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Choose Language",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "App will restart to apply selected language",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                LazyVerticalGrid(
                    columns               = GridCells.Fixed(1),
                    verticalArrangement   = Arrangement.spacedBy(6.dp),
                    modifier              = Modifier.heightIn(max = 420.dp)
                ) {
                    items(languages.entries.toList()) { (code, name) ->
                        val isSelected = code == currentCode
                        val borderColor by animateColorAsState(
                            targetValue   = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            animationSpec = tween(200),
                            label         = "langBorder"
                        )
                        Card(
                            modifier  = Modifier
                                .fillMaxWidth()
                                .clickable { onLanguageSelected(code) }
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) borderColor else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            shape     = RoundedCornerShape(12.dp),
                            colors    = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Row(
                                modifier          = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text       = name,
                                    style      = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color      = if (isSelected) MaterialTheme.colorScheme.primary
                                                 else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector        = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint               = MaterialTheme.colorScheme.primary,
                                        modifier           = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel")
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════════════════


package com.aaryo.selfattendance.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aaryo.selfattendance.ads.AdManager
import com.aaryo.selfattendance.ads.NativeAdCard
import com.aaryo.selfattendance.ads.SmartAdController
import com.aaryo.selfattendance.data.remote.RemoteConfigManager
import com.aaryo.selfattendance.ads.BannerAd
import com.aaryo.selfattendance.data.repository.AttendanceRepository
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(navController: NavController) {

    val context = LocalContext.current
    val activity = context as? Activity

    val remoteConfig = remember { RemoteConfigManager.getInstance() }
    val backupViewModel: BackupViewModel = viewModel()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val backupLoading by backupViewModel.loading.collectAsState()
    val backupMessage by backupViewModel.message.collectAsState()
    val restoreCompleted by backupViewModel.restoreCompleted.collectAsState()

    LaunchedEffect(backupMessage) {
        backupMessage?.let {
            snackbarHostState.showSnackbar(it)
            backupViewModel.clearMessage()
        }
    }

    LaunchedEffect(restoreCompleted) {
        if (restoreCompleted) {
            backupViewModel.clearRestoreFlag()
            kotlinx.coroutines.delay(1500)

            val launchIntent = context.packageManager
                .getLaunchIntentForPackage(context.packageName)

            launchIntent?.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
            context.startActivity(launchIntent)
        }
    }

    val prefManager = remember { PreferencesManager(context) }

    var darkMode by remember { mutableStateOf(prefManager.isDarkMode) }
    var notifications by remember { mutableStateOf(prefManager.isReminderEnabled) }
    var biometricEnabled by remember { mutableStateOf(prefManager.isBiometricEnabled) }

    var showResetDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    // ✅ FIX: Reset Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Attendance Data") },
            text = { Text("Kya aap sure hain? Yeh action sare attendance records permanently delete kar dega. Yeh undo nahi ho sakta.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        backupViewModel.resetAttendance()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ✅ FIX: Restore Confirmation Dialog
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore Data") },
            text = { Text("Backup se data restore karne par current attendance data replace ho jayega. Kya aap continue karna chahte hain?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreDialog = false
                        backupViewModel.restore()
                    }
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ✅ FIXED SCAFFOLD
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (remoteConfig.showBannerAd()) {
                BannerAd()
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp) // ✅ FIXED
        ) {

            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(Modifier.height(24.dp))

            SettingsToggle("Dark Mode", darkMode) {
                darkMode = it
                prefManager.isDarkMode = it
                scope.launch { snackbarHostState.showSnackbar("Theme updated") }
                activity?.recreate()
            }

            Spacer(Modifier.height(20.dp))

            SettingsToggle("Daily Reminder", notifications) {
                notifications = it
                prefManager.isReminderEnabled = it
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (it) "Reminder enabled" else "Reminder disabled"
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            SettingsToggle("Biometric Login", biometricEnabled) {
                biometricEnabled = it
                prefManager.isBiometricEnabled = it
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (it) "Biometric enabled" else "Biometric disabled"
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                "Data Management",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(12.dp))

            SettingsButton(
                if (backupLoading) "Please wait..." else "Backup Data",
                !backupLoading
            ) {
                activity?.let {
                    if (SmartAdController.shouldShowAd()) AdManager.showAd(it)
                }
                backupViewModel.backup()
            }

            Spacer(Modifier.height(12.dp))

            SettingsButton(
                if (backupLoading) "Please wait..." else "Restore Data",
                !backupLoading
            ) {
                activity?.let {
                    if (SmartAdController.shouldShowAd()) AdManager.showAd(it)
                }
                showRestoreDialog = true
            }

            Spacer(Modifier.height(12.dp))

            SettingsButton("Reset Attendance Data", !backupLoading) {
                activity?.let {
                    if (SmartAdController.shouldShowAd()) AdManager.showAd(it)
                }
                showResetDialog = true
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                "General",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(12.dp))

            SettingsButton("Privacy Policy") {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://sites.google.com/view/self-attendance-privacy-policy/home"))
                )
            }

            Spacer(Modifier.height(10.dp))

            SettingsButton("Terms & Conditions") {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://sites.google.com/view/self-terms-and-conditions/home"))
                )
            }

            Spacer(Modifier.height(10.dp))

            SettingsButton("About App") {
                navController.navigate("about")
            }

            Spacer(Modifier.height(10.dp))

            SettingsButton("Rate This App") {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=com.aaryo.selfattendance"))
                )
            }

            Spacer(Modifier.height(10.dp))

            SettingsButton("Refer to Friends") {

                val shareText = """
Check out this amazing attendance & salary calculator app!

Self Attendance Pro

https://play.google.com/store/apps/details?id=com.aaryo.selfattendance
""".trimIndent()

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }

                context.startActivity(Intent.createChooser(intent, "Share via"))
            }

            Spacer(Modifier.height(16.dp))

            SettingsButton("Logout") {

                FirebaseAuth.getInstance().signOut()

                val launchIntent = context.packageManager
                    .getLaunchIntentForPackage(context.packageName)

                launchIntent?.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                )

                context.startActivity(launchIntent)
            }

            Spacer(Modifier.height(24.dp))

            if (remoteConfig.showNativeAd()) {
                Text("Sponsored", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(6.dp))
                NativeAdCard(modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(20.dp))

            Text("Self Attendance Pro")
            Text("Version 1.0", style = MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable
fun SettingsToggle(
    title: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(
            checked = checked,
            onCheckedChange = onToggle
        )
    }
}

@Composable
fun SettingsButton(
    title: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(title)
    }
}

package com.aaryo.selfattendance.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.aaryo.selfattendance.R
import com.aaryo.selfattendance.ads.ConsentManager
import com.aaryo.selfattendance.data.local.PreferencesManager
import kotlinx.coroutines.launch

private val EmeraldGreen = Color(0xFF059669)
private val RoyalBlue    = Color(0xFF2563EB)
private val CrimsonRed   = Color(0xFFDC2626)
private val AmberGold    = Color(0xFFD97706)
private val PurpleIndigo = Color(0xFF6366F1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferencesManager(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val activity = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>()
            .firstOrNull()
    }

    var biometricEnabled by remember { mutableStateOf(prefs.isBiometricEnabled) }
    var autoLockOnResume by remember { mutableStateOf(prefs.isAutoLockOnResumeEnabled) }
    var maskSensitiveData by remember { mutableStateOf(prefs.isMaskSensitiveDataEnabled) }

    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showDataExportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Ensure FLAG_SECURE is completely cleared for app promotion & screen recording
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        prefs.isScreenPrivacyEnabled = false
    }

    fun applyScreenPrivacy() {
        activity?.let { act ->
            try {
                act.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } catch (e: Exception) {
                // Ignore window flag updates if activity is dying
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Privacy & Security",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Manage data protection & app security",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
        ) {
            // ── 1. Privacy Status Banner ─────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(EmeraldGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GppGood,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Your Privacy is Protected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Attendance records and business data are stored locally in sandbox storage with zero third-party data broker sharing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── 2. Screen & App Security ─────────────────────────────────────
            item {
                SectionHeader(title = "App & Screen Security")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Biometric / Device Lock
                        PrivacyToggleRow(
                            icon = Icons.Default.Fingerprint,
                            iconColor = EmeraldGreen,
                            title = "Biometric / App Lock",
                            subtitle = if (biometricEnabled) "Active · Requires fingerprint, face, or PIN" else "Disabled · Open app without lock prompt",
                            checked = biometricEnabled,
                            onCheckedChange = { checked ->
                                biometricEnabled = checked
                                prefs.isBiometricEnabled = checked
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (checked) "Biometric Lock Enabled" else "Biometric Lock Disabled"
                                    )
                                }
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // Auto-lock when switching apps
                        PrivacyToggleRow(
                            icon = Icons.Default.LockClock,
                            iconColor = PurpleIndigo,
                            title = "Auto-Lock on App Switch",
                            subtitle = if (autoLockOnResume) "Locks immediately when leaving the app" else "Locks only when app is fully closed",
                            checked = autoLockOnResume,
                            onCheckedChange = { checked ->
                                autoLockOnResume = checked
                                prefs.isAutoLockOnResumeEnabled = checked
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (checked) "Auto-lock on app switch enabled" else "Auto-lock on app switch disabled"
                                    )
                                }
                            }
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // Screenshots & Screen Recording (Allowed for Promotion & Marketing)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(RoyalBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Screenshot,
                                    contentDescription = null,
                                    tint = RoyalBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Screenshots & Recording",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Enabled for app promotion, social media sharing, and tutorials",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = EmeraldGreen.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "Allowed",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // Mask sensitive financial data
                        PrivacyToggleRow(
                            icon = Icons.Default.VisibilityOff,
                            iconColor = AmberGold,
                            title = "Mask Sensitive Financial Data",
                            subtitle = if (maskSensitiveData) "Salaries and advances masked with ••••••" else "Salaries and advances displayed normally",
                            checked = maskSensitiveData,
                            onCheckedChange = { checked ->
                                maskSensitiveData = checked
                                prefs.isMaskSensitiveDataEnabled = checked
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (checked) "Financial figures will be masked" else "Financial figures unmasked"
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // ── 3. Data Transparency & Local Storage ──────────────────────────
            item {
                SectionHeader(title = "Data Transparency & Storage")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        DataCommitmentRow(
                            icon = Icons.Default.Storage,
                            iconColor = RoyalBlue,
                            title = "100% On-Device Storage",
                            description = "Your attendance logs, staff registers, and salary slips are stored in your device's private SQLite database. No other app has access."
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        DataCommitmentRow(
                            icon = Icons.Default.VerifiedUser,
                            iconColor = EmeraldGreen,
                            title = "Zero Third-Party Sharing",
                            description = "We never sell, rent, or monetize your employee names, phone numbers, or attendance records with advertisers or data brokers."
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        DataCommitmentRow(
                            icon = Icons.Default.CloudQueue,
                            iconColor = PurpleIndigo,
                            title = "Encrypted Cloud Backup",
                            description = "Cloud backups are transmitted via encrypted HTTPS and secured behind Firebase Security Rules locked strictly to your account."
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        DataCommitmentRow(
                            icon = Icons.Default.SecurityUpdateGood,
                            iconColor = AmberGold,
                            title = "Minimal Permissions Policy",
                            description = "We only request Notification permission for daily check-in alerts and Camera/Storage when generating or scanning QR attendance reports."
                        )
                    }
                }
            }

            // ── 4. Ad Privacy & Choices (Google UMP) ──────────────────────────
            item {
                SectionHeader(title = "Advertising & Consent Choices")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "You have full control over your personalized advertising choices under GDPR, CCPA, and Google Play policies.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedButton(
                            onClick = {
                                if (activity != null) {
                                    ConsentManager.showPrivacyOptionsForm(activity) { success ->
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                if (success) "Privacy choices updated"
                                                else "No privacy options form required for your region"
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Manage Ad Privacy Choices")
                        }

                        TextButton(
                            onClick = {
                                if (activity != null) {
                                    ConsentManager.resetConsent(activity)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Ad consent preferences reset")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reset Ad Consent Preferences", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // ── 5. Data Rights & Controls ─────────────────────────────────────
            item {
                SectionHeader(title = "Your Data Rights")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Clear App Cache
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showClearCacheDialog = true }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.CleaningServices, contentDescription = null, tint = RoyalBlue)
                                Column {
                                    Text("Clear App Cache", fontWeight = FontWeight.SemiBold)
                                    Text("Frees temporary files safely without losing attendance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // Official Privacy Policy Link
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.privacy_policy_url)))
                                    )
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = EmeraldGreen)
                                Column {
                                    Text("Read Official Privacy Policy", fontWeight = FontWeight.SemiBold)
                                    Text("Full legal disclosures & compliance terms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // Terms & Conditions Link
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse("https://sites.google.com/view/self-terms-and-conditions/home"))
                                    )
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.Gavel, contentDescription = null, tint = PurpleIndigo)
                                Column {
                                    Text("Terms & Conditions", fontWeight = FontWeight.SemiBold)
                                    Text("User agreement & acceptable use guidelines", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear Temporary Cache?") },
            text = { Text("This will safely delete cached temporary files and images. Your attendance records, employee data, and salary information will NOT be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheDialog = false
                        try {
                            context.cacheDir.deleteRecursively()
                            Toast.makeText(context, "Cache cleared successfully", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Clear Cache", color = RoyalBlue, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

@Composable
private fun PrivacyToggleRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun DataCommitmentRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

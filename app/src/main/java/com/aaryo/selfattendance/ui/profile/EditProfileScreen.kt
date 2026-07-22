package com.aaryo.selfattendance.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import android.content.ClipData
import android.content.ClipboardManager
import android.util.Log
import android.widget.Toast
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.aaryo.selfattendance.utils.CurrencyManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aaryo.selfattendance.R
import com.aaryo.selfattendance.ads.AdsController
import com.aaryo.selfattendance.data.model.UserProfile
import com.aaryo.selfattendance.data.remote.RemoteConfigManager
import com.aaryo.selfattendance.data.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@Composable
fun EditProfileScreen(navController: NavController) {

    val viewModel: ProfileViewModel = viewModel(factory = ProfileViewModelFactory())

    val profile by viewModel.profile.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error   by viewModel.error.collectAsState()

    val context           = LocalContext.current
    val currencySymbol    = remember { CurrencyManager.getSymbol(PreferencesManager(context).selectedCurrency) }
    val remoteConfig      = remember { RemoteConfigManager.getInstance() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()

    var name        by remember { mutableStateOf("") }
    var salary      by remember { mutableStateOf("") }
    var workingDays by remember { mutableStateOf("") }
    var hours       by remember { mutableStateOf("") }
    var overtime    by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }
    var overtimeManuallyEdited by remember { mutableStateOf(false) }

    // ── Unique User ID (6-digit, for support/server lookups) ────────────
    //
    // BUG FIX: previously a plain `String?` that stayed null forever on
    // failure, showing "Generating your account ID…" with no error and no
    // way to retry. Now an explicit state so failures are visible and
    // retryable instead of hanging silently.
    var uniqueIdState by remember { mutableStateOf<UniqueIdState>(UniqueIdState.Loading) }
    var idRetryTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(idRetryTrigger) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            uniqueIdState = UniqueIdState.Failed("Not logged in")
            return@LaunchedEffect
        }
        uniqueIdState = UniqueIdState.Loading
        runCatching { AuthRepository().ensureUniqueIdOrThrow(uid) }
            .onSuccess { uniqueIdState = UniqueIdState.Loaded(it) }
            .onFailure { e ->
                Log.e("EditProfileScreen", "Unique ID generation failed", e)
                uniqueIdState = UniqueIdState.Failed(e.message ?: "Couldn't generate ID")
            }
    }

    // ── Auto Overtime Formula: Per Hour = (Salary ÷ 30) ÷ 8 ──────────────
    val perHourAuto: Double? = remember(salary) {
        salary.toDoubleOrNull()?.takeIf { it > 0 }?.let { it / 30.0 / 8.0 * OVERTIME_MULTIPLIER }
    }
    val autoOvertimeStr: String? = remember(perHourAuto) { perHourAuto?.let { "%.2f".format(it) } }

    LaunchedEffect(autoOvertimeStr) {
        if (!overtimeManuallyEdited && autoOvertimeStr != null) overtime = autoOvertimeStr
    }
    LaunchedEffect(Unit) { viewModel.loadProfile() }
    LaunchedEffect(Unit) {
        viewModel.profileSaved.collect {
            snackbarHostState.showSnackbar(context.getString(R.string.profile_updated_success))
            navController.popBackStack()
        }
    }
    LaunchedEffect(profile) {
        if (!initialized) {
            profile?.let {
                name = it.name; salary = it.monthlySalary.toString()
                workingDays = it.workingDays.toString(); hours = it.standardHours.toString()
                overtime = it.overtimeRate.toString(); initialized = true
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar    = { if (remoteConfig.showBannerAd()) AdsController.BannerAd() },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Text(
                stringResource(R.string.profile_edit_title),
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Update your work details below",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            // ── My ID Card (6-digit, for support/server lookups) ──────────
            Card(
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier  = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF00897B).copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Fingerprint, contentDescription = null, tint = Color(0xFF00897B))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("My ID", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        when (val state = uniqueIdState) {
                            is UniqueIdState.Loaded -> Text(
                                state.id,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            is UniqueIdState.Failed -> Text(
                                "Couldn't generate ID: ${state.message}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            UniqueIdState.Loading -> Text(
                                "Generating your account ID…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    when (uniqueIdState) {
                        is UniqueIdState.Loaded -> TextButton(onClick = {
                            val idToCopy = (uniqueIdState as? UniqueIdState.Loaded)?.id
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard?.setPrimaryClip(ClipData.newPlainText("User ID", idToCopy))
                            Toast.makeText(context, "ID copied: $idToCopy", Toast.LENGTH_SHORT).show()
                        }) { Text("Copy") }
                        is UniqueIdState.Failed -> TextButton(onClick = { idRetryTrigger++ }) { Text("Retry") }
                        UniqueIdState.Loading -> {}
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Profile Fields Card (Settings-style) ──────────────────────
            Card(
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier  = Modifier.fillMaxWidth()
            ) {
                Column {
                    ProfileInputRow(
                        icon          = Icons.Outlined.Badge,
                        iconColor     = MaterialTheme.colorScheme.primary,
                        label         = stringResource(R.string.profile_name),
                        value         = name,
                        onValueChange = { name = it },
                        keyboard      = KeyboardType.Text,
                        showDivider   = true,
                        enabled       = !loading
                    )
                    ProfileInputRow(
                        icon          = Icons.Outlined.MonetizationOn,
                        iconColor     = Color(0xFF2E7D32),
                        label         = stringResource(R.string.profile_salary),
                        value         = salary,
                        onValueChange = { salary = it; overtimeManuallyEdited = false },
                        keyboard      = KeyboardType.Number,
                        showDivider   = true,
                        enabled       = !loading,
                        hint          = if (salary.toDoubleOrNull() != null && salary.toDouble() > 0)
                                            "Per Day: $currencySymbol${"%.2f".format(salary.toDouble() / 30)}"
                                        else null
                    )
                    ProfileInputRow(
                        icon          = Icons.Outlined.Work,
                        iconColor     = Color(0xFF1565C0),
                        label         = "Working Days",
                        value         = workingDays,
                        onValueChange = { workingDays = it },
                        keyboard      = KeyboardType.Number,
                        showDivider   = true,
                        enabled       = !loading
                    )
                    ProfileInputRow(
                        icon          = Icons.Outlined.Schedule,
                        iconColor     = Color(0xFF6A1B9A),
                        label         = stringResource(R.string.profile_hours),
                        value         = hours,
                        onValueChange = { hours = it },
                        keyboard      = KeyboardType.Number,
                        showDivider   = true,
                        enabled       = !loading
                    )
                    ProfileInputRow(
                        icon          = Icons.Outlined.Bolt,
                        iconColor     = Color(0xFFF57F17),
                        label         = stringResource(R.string.profile_overtime_rate),
                        value         = overtime,
                        onValueChange = { overtime = it; overtimeManuallyEdited = true },
                        keyboard      = KeyboardType.Number,
                        showDivider   = false,
                        enabled       = !loading,
                        hint          = if (!overtimeManuallyEdited && perHourAuto != null)
                                            "Auto: $currencySymbol${"%.2f".format(perHourAuto)}/hr"
                                        else if (overtimeManuallyEdited) "Manual" else null,
                        trailingContent = if (overtimeManuallyEdited) {
                            {
                                TextButton(onClick = {
                                    overtimeManuallyEdited = false
                                    autoOvertimeStr?.let { overtime = it }
                                }) { Text("Auto", style = MaterialTheme.typography.labelSmall) }
                            }
                        } else null
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    val err = validateInputs(name, salary, workingDays, hours, overtime)
                    if (err != null) { scope.launch { snackbarHostState.showSnackbar(err) }; return@Button }
                    viewModel.updateProfile(UserProfile(
                        name          = name,
                        monthlySalary = salary.toDoubleOrNull() ?: 0.0,
                        workingDays   = workingDays.toIntOrNull() ?: 0,
                        standardHours = hours.toDoubleOrNull() ?: 0.0,
                        overtimeRate  = overtime.toDoubleOrNull() ?: 0.0
                    ))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                enabled  = !loading
            ) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp))
                else Text(stringResource(R.string.profile_update), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }

            Spacer(Modifier.height(20.dp))
            if (remoteConfig.showNativeAd()) {
                Text("Sponsored", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                AdsController.FreshNativeAdView()
            }
        }
    }
}

// ── Unique ID load state for the "My ID" card ─────────────────────────
// Separated from a plain nullable String so a permission/network failure
// is visibly different from "still loading" — with a Retry action.
private sealed class UniqueIdState {
    object Loading : UniqueIdState()
    data class Loaded(val id: String) : UniqueIdState()
    data class Failed(val message: String) : UniqueIdState()
}

package com.aaryo.selfattendance.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.aaryo.selfattendance.utils.CurrencyManager
import com.aaryo.selfattendance.utils.LocaleManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aaryo.selfattendance.R
import com.aaryo.selfattendance.ads.AdsController
import com.aaryo.selfattendance.data.model.UserProfile
import com.aaryo.selfattendance.data.remote.RemoteConfigManager
import com.aaryo.selfattendance.ui.navigation.Routes
import kotlinx.coroutines.launch

@Composable
fun SetupProfileScreen(navController: NavController) {

    val viewModel: ProfileViewModel = viewModel(factory = ProfileViewModelFactory())

    val loading       by viewModel.loading.collectAsState()
    val error         by viewModel.error.collectAsState()
    val profileExists by viewModel.profileExists.collectAsState()

    val context           = LocalContext.current
    val prefs             = remember { PreferencesManager(context) }
    val currencySymbol    = remember { CurrencyManager.getSymbol(prefs.selectedCurrency) }
    val remoteConfig      = remember { RemoteConfigManager.getInstance() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()

    var showCountryPicker by remember { mutableStateOf(!prefs.languagePickerShown) }

    var name        by remember { mutableStateOf("") }
    var salary      by remember { mutableStateOf("") }
    var workingDays by remember { mutableStateOf("") }
    var hours       by remember { mutableStateOf("") }
    var overtime    by remember { mutableStateOf("") }
    var overtimeManuallyEdited by remember { mutableStateOf(false) }

    // ── Auto Overtime Formula: Per Hour = (Salary ÷ 30) ÷ 8 ──────────────
    val perHourAuto: Double? = remember(salary) {
        salary.toDoubleOrNull()?.takeIf { it > 0 }?.let { it / 30.0 / 8.0 * OVERTIME_MULTIPLIER }
    }
    val autoOvertimeStr: String? = remember(perHourAuto) { perHourAuto?.let { "%.2f".format(it) } }

    LaunchedEffect(autoOvertimeStr) {
        if (!overtimeManuallyEdited && autoOvertimeStr != null) overtime = autoOvertimeStr
    }
    LaunchedEffect(Unit) { viewModel.loadProfile() }
    LaunchedEffect(profileExists) {
        if (profileExists) navController.navigate(Routes.MAIN) {
            popUpTo(Routes.PROFILE) { inclusive = true }; launchSingleTop = true
        }
    }
    LaunchedEffect(Unit) {
        viewModel.profileSaved.collect {
            navController.navigate(Routes.MAIN) {
                popUpTo(Routes.PROFILE) { inclusive = true }; launchSingleTop = true
            }
        }
    }

    if (showCountryPicker) {
        FirstRunCountryPickerDialog(
            onCountrySelected = { country ->
                prefs.selectedCountry  = country.code
                prefs.selectedLanguage = country.language
                prefs.selectedCurrency = country.currency
                prefs.languagePickerShown = true
                showCountryPicker = false
                with(LocaleManager) { context.findActivity() }
                    ?.let { LocaleManager.setLocaleAndRestart(it, country.language) }
            },
            onDismiss = {
                prefs.languagePickerShown = true
                showCountryPicker = false
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { if (remoteConfig.showBannerAd()) AdsController.BannerAd() }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Text(
                stringResource(R.string.profile_setup_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Enter your work details to get started",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            // ── Profile Fields Card ────────────────────────────────────────
            Card(
                shape     = RoundedCornerShape(16.dp),
                colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier  = Modifier.fillMaxWidth()
            ) {
                Column {
                    ProfileInputRow(
                        icon       = Icons.Outlined.Badge,
                        iconColor  = MaterialTheme.colorScheme.primary,
                        label      = stringResource(R.string.profile_name),
                        value      = name,
                        onValueChange = { name = it },
                        keyboard   = KeyboardType.Text,
                        showDivider = true,
                        enabled    = !loading
                    )
                    ProfileInputRow(
                        icon       = Icons.Outlined.MonetizationOn,
                        iconColor  = Color(0xFF2E7D32),
                        label      = stringResource(R.string.profile_salary),
                        value      = salary,
                        onValueChange = { salary = it; overtimeManuallyEdited = false },
                        keyboard   = KeyboardType.Number,
                        showDivider = true,
                        enabled    = !loading,
                        hint       = if (salary.toDoubleOrNull() != null && salary.toDouble() > 0)
                                         "Per Day: $currencySymbol${"%.2f".format(salary.toDouble() / 30)}"
                                     else null
                    )
                    ProfileInputRow(
                        icon       = Icons.Outlined.Work,
                        iconColor  = Color(0xFF1565C0),
                        label      = stringResource(R.string.profile_working_days),
                        value      = workingDays,
                        onValueChange = { workingDays = it },
                        keyboard   = KeyboardType.Number,
                        showDivider = true,
                        enabled    = !loading
                    )
                    ProfileInputRow(
                        icon       = Icons.Outlined.Schedule,
                        iconColor  = Color(0xFF6A1B9A),
                        label      = stringResource(R.string.profile_hours),
                        value      = hours,
                        onValueChange = { hours = it },
                        keyboard   = KeyboardType.Number,
                        showDivider = true,
                        enabled    = !loading
                    )
                    ProfileInputRow(
                        icon       = Icons.Outlined.Bolt,
                        iconColor  = Color(0xFFF57F17),
                        label      = stringResource(R.string.profile_overtime_rate),
                        value      = overtime,
                        onValueChange = { overtime = it; overtimeManuallyEdited = true },
                        keyboard   = KeyboardType.Number,
                        showDivider = false,
                        enabled    = !loading,
                        hint       = if (!overtimeManuallyEdited && perHourAuto != null)
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
                    viewModel.saveProfile(UserProfile(
                        name          = name.trim(),
                        monthlySalary = salary.toDouble(),
                        workingDays   = workingDays.toInt(),
                        standardHours = hours.toDouble(),
                        overtimeRate  = overtime.toDouble()
                    ))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(14.dp),
                enabled  = !loading
            ) {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.profile_save), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }

            Spacer(Modifier.height(28.dp))

            if (remoteConfig.showNativeAd()) {
                Text("Sponsored", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                AdsController.NativeAdView()
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable Settings-style input row (shared with EditProfileScreen)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ProfileInputRow(
    icon            : ImageVector,
    iconColor       : Color,
    label           : String,
    value           : String,
    onValueChange   : (String) -> Unit,
    keyboard        : KeyboardType       = KeyboardType.Text,
    showDivider     : Boolean            = true,
    enabled         : Boolean            = true,
    hint            : String?            = null,
    trailingContent : (@Composable () -> Unit)? = null
) {
    Column {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colored icon circle — same as Settings screen
            Box(
                modifier         = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.width(12.dp))

            // Label + TextField stacked
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                OutlinedTextField(
                    value         = value,
                    onValueChange = onValueChange,
                    enabled       = enabled,
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = iconColor,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor  = Color.Transparent,
                        focusedContainerColor   = iconColor.copy(alpha = 0.04f),
                        unfocusedContainerColor = Color.Transparent
                    ),
                    textStyle     = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    shape         = RoundedCornerShape(10.dp),
                    trailingIcon  = trailingContent?.let { { it() } },
                    supportingText = hint?.let { h ->
                        {
                            Text(
                                h,
                                style = MaterialTheme.typography.labelSmall,
                                color = iconColor.copy(alpha = 0.8f)
                            )
                        }
                    },
                    modifier      = Modifier.fillMaxWidth()
                )
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier  = Modifier.padding(start = 66.dp, end = 16.dp),
                color     = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun FirstRunCountryPickerDialog(
    onCountrySelected: (com.aaryo.selfattendance.utils.CountryManager.Country) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = androidx.compose.ui.Modifier.padding(20.dp)) {
                Text(
                    text  = "🌍 Select Your Country",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(androidx.compose.ui.Modifier.height(4.dp))
                Text(
                    text  = "Language & currency will be set automatically",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(androidx.compose.ui.Modifier.height(12.dp))
                val scrollState = rememberScrollState()
                Column(
                    modifier = androidx.compose.ui.Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(scrollState)
                ) {
                    com.aaryo.selfattendance.utils.CountryManager.SUPPORTED_COUNTRIES
                        .forEach { country ->
                            Row(
                                modifier = androidx.compose.ui.Modifier
                                    .fillMaxWidth()
                                    .clickable { onCountrySelected(country) }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(country.flag, modifier = androidx.compose.ui.Modifier.padding(end = 12.dp))
                                Text(country.name, modifier = androidx.compose.ui.Modifier.weight(1f))
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                }
                Spacer(androidx.compose.ui.Modifier.height(8.dp))
                TextButton(
                    onClick  = onDismiss,
                    modifier = androidx.compose.ui.Modifier.align(Alignment.End)
                ) { Text("Skip") }
            }
        }
    }
}

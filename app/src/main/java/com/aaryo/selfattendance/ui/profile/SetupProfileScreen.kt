package com.aaryo.selfattendance.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aaryo.selfattendance.ads.NativeAdCard
import com.aaryo.selfattendance.data.model.UserProfile
import com.aaryo.selfattendance.data.remote.RemoteConfigManager
import com.aaryo.selfattendance.domain.SalaryCalculator   // FIX: was missing → "Unresolved reference: SalaryCalculator"
import com.aaryo.selfattendance.ui.navigation.Routes
import kotlinx.coroutines.launch
import com.aaryo.selfattendance.ui.profile.SalaryBreakdownCard

// ─────────────────────────────────────────────────────────────────
//  FIX: OVERTIME_MULTIPLIER, validateInputs(), and range constants
//  are now in ProfileValidation.kt (internal, same package).
//  SalaryBreakdownCard is now internal in EditProfileScreen.kt.
//  No duplicate declarations needed here.
// ─────────────────────────────────────────────────────────────────

@Composable
fun SetupProfileScreen(navController: NavController) {

    // FIX: ProfileViewModel requires constructor args (repo, authRepo).
    // viewModel() with no factory crashes — use ProfileViewModelFactory.
    val viewModel: ProfileViewModel = viewModel(factory = ProfileViewModelFactory())

    val loading       by viewModel.loading.collectAsState()
    val error         by viewModel.error.collectAsState()
    // FIX: profileSaved is a SharedFlow<Unit>, not StateFlow<Boolean>.
    // collectAsState() does not work on SharedFlow — caused
    // "No value passed for parameter 'initial'" and "Type mismatch" errors.
    // Replaced with LaunchedEffect + collect() below.
    val profileExists by viewModel.profileExists.collectAsState()

    val remoteConfig      = remember { RemoteConfigManager.getInstance() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope             = rememberCoroutineScope()

    // ── Form fields ───────────────────────────────────────────
    var name        by remember { mutableStateOf("") }
    var salary      by remember { mutableStateOf("") }
    var workingDays by remember { mutableStateOf("") }
    var hours       by remember { mutableStateOf("") }
    var overtime    by remember { mutableStateOf("") }

    // ── Auto calculation (30-days formula) ───────────────────
    // Per Day  = Salary ÷ 30  (HR/Payroll standard)
    val perDayAmount: Double? = remember(salary) {
        val s = salary.toDoubleOrNull()
        if (s != null && s > 0) s / SalaryCalculator.CALENDAR_DAYS else null
    }

    // Per Hour = Per Day ÷ standardHours
    val perHourAmount: Double? = remember(perDayAmount, hours) {
        val h = hours.toDoubleOrNull()
        if (perDayAmount != null && h != null && h > 0) perDayAmount / h else null
    }

    val autoOvertimeRate: Double? = remember(perHourAmount) {
        if (perHourAmount != null) perHourAmount * OVERTIME_MULTIPLIER
        else null
    }

    // earnedPercent: configured working days vs 30-day month
    val earnedPercent: Double? = remember(workingDays) {
        val d = workingDays.toIntOrNull()
        if (d == null || d <= 0) null
        else (d.toDouble() / SalaryCalculator.CALENDAR_DAYS) * 100
    }

    var overtimeManuallyEdited by remember { mutableStateOf(false) }

    LaunchedEffect(autoOvertimeRate) {
        if (!overtimeManuallyEdited && autoOvertimeRate != null) {
            overtime = autoOvertimeRate.toBigDecimal()
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .toPlainString()
        }
    }

    LaunchedEffect(Unit) { viewModel.loadProfile() }

    LaunchedEffect(profileExists) {
        if (profileExists) {
            navController.navigate(Routes.MAIN) {
                popUpTo(Routes.PROFILE) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // FIX: Collect SharedFlow<Unit> — fires exactly once per save event.
    LaunchedEffect(Unit) {
        viewModel.profileSaved.collect {
            snackbarHostState.showSnackbar("Profile setup completed successfully")
            navController.navigate(Routes.MAIN) {
                popUpTo(Routes.PROFILE) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val fieldsEnabled = !loading

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { paddingValues ->

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {

            Text("Setup Your Profile", style = MaterialTheme.typography.headlineLarge)

            Spacer(Modifier.height(24.dp))

            ProfileField(
                value         = name,
                onValueChange = { name = it; overtimeManuallyEdited = false },
                label         = "Full Name",
                icon          = Icons.Outlined.Badge,
                enabled       = fieldsEnabled
            )

            Spacer(Modifier.height(12.dp))

            ProfileField(
                value         = salary,
                onValueChange = { salary = it; overtimeManuallyEdited = false },
                label         = "Monthly Salary (₹)",
                icon          = Icons.Outlined.AttachMoney,
                keyboardType  = KeyboardType.Number,
                enabled       = fieldsEnabled,
                perDayHint    = perDayAmount?.let { "Per Day: ₹${"%.0f".format(it)}" },
                perHourHint   = perHourAmount?.let { "Per Hour: ₹${"%.2f".format(it)}" }
            )

            Spacer(Modifier.height(12.dp))

            ProfileField(
                value         = workingDays,
                onValueChange = { workingDays = it; overtimeManuallyEdited = false },
                label         = "Working Days per Month",
                icon          = Icons.Outlined.Work,
                keyboardType  = KeyboardType.Number,
                enabled       = fieldsEnabled
            )

            Spacer(Modifier.height(12.dp))

            ProfileField(
                value         = hours,
                onValueChange = { hours = it; overtimeManuallyEdited = false },
                label         = "Hours Per Day",
                icon          = Icons.Outlined.Schedule,
                keyboardType  = KeyboardType.Number,
                enabled       = fieldsEnabled
            )

            Spacer(Modifier.height(12.dp))

            ProfileField(
                value         = overtime,
                onValueChange = { overtime = it; overtimeManuallyEdited = true },
                label         = "Overtime Rate (₹/hour)",
                icon          = Icons.Outlined.AttachMoney,
                keyboardType  = KeyboardType.Number,
                enabled       = fieldsEnabled,
                supportingText = when {
                    !overtimeManuallyEdited && autoOvertimeRate != null -> "Auto: 1.5× per-hour rate"
                    overtimeManuallyEdited                              -> "Custom rate set manually"
                    else                                                -> null
                }
            )

            Spacer(Modifier.height(20.dp))

            AnimatedVisibility(
                visible = perDayAmount != null,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically()
            ) {
                perDayAmount?.let {
                    // FIX: SalaryBreakdownCard is now internal (not private)
                    // in EditProfileScreen.kt so it's accessible here.
                    SalaryBreakdownCard(
                        perDay        = it,
                        perHour       = perHourAmount,
                        overtimeRate  = autoOvertimeRate,
                        earnedPercent = earnedPercent
                    )
                }
            }

            if (perDayAmount != null) Spacer(Modifier.height(20.dp))

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    // FIX: validateInputs() now from ProfileValidation.kt (internal)
                    val validationError = validateInputs(
                        name, salary, workingDays, hours, overtime
                    )
                    if (validationError != null) {
                        scope.launch { snackbarHostState.showSnackbar(validationError) }
                        return@Button
                    }
                    viewModel.saveProfile(
                        UserProfile(
                            name          = name.trim(),
                            monthlySalary = salary.toDouble(),
                            workingDays   = workingDays.toInt(),
                            standardHours = hours.toDouble(),
                            overtimeRate  = overtime.toDouble()
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled  = fieldsEnabled
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save & Continue")
                }
            }

            Spacer(Modifier.height(28.dp))

            if (remoteConfig.showNativeAd()) {
                Text("Sponsored", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(6.dp))
                NativeAdCard(modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

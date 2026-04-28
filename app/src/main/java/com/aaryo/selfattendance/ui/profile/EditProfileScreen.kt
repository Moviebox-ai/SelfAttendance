package com.aaryo.selfattendance.ui.profile

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import com.aaryo.selfattendance.R
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aaryo.selfattendance.ads.AdsController
import com.aaryo.selfattendance.data.model.UserProfile
import com.aaryo.selfattendance.data.remote.RemoteConfigManager
import com.aaryo.selfattendance.domain.SalaryCalculator
import com.aaryo.selfattendance.ui.dashboard.formatMoney
import kotlinx.coroutines.launch

@Composable
fun EditProfileScreen(navController: NavController) {

    val viewModel: ProfileViewModel = viewModel(factory = ProfileViewModelFactory())

    val profile by viewModel.profile.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    val remoteConfig = remember { RemoteConfigManager.getInstance() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var salary by remember { mutableStateOf("") }
    var workingDays by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("") }
    var overtime by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }

    var overtimeManuallyEdited by remember { mutableStateOf(false) }

    val autoOvertimeRate: String? = remember(salary, hours) {
        val s = salary.toDoubleOrNull() ?: return@remember null
        val h = hours.toDoubleOrNull()?.takeIf { it > 0 } ?: return@remember null
        if (s <= 0) return@remember null

        val perDay  = s / SalaryCalculator.CALENDAR_DAYS
        val perHour = perDay / h
        val rate    = perHour * 1.5

        String.format("%.2f", rate)
    }

    LaunchedEffect(autoOvertimeRate) {
        if (!overtimeManuallyEdited && autoOvertimeRate != null) {
            overtime = autoOvertimeRate
        }
    }

    LaunchedEffect(Unit) { viewModel.loadProfile() }

    LaunchedEffect(Unit) {
        viewModel.profileSaved.collect {
            snackbarHostState.showSnackbar("Profile updated successfully")
            navController.popBackStack()
        }
    }

    LaunchedEffect(profile) {
        if (!initialized) {
            profile?.let {
                name        = it.name
                salary      = it.monthlySalary.toString()
                workingDays = it.workingDays.toString()
                hours       = it.standardHours.toString()
                overtime    = it.overtimeRate.toString()
                initialized = true
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (remoteConfig.showBannerAd()) {
                AdsController.BannerAd()
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {

            Text(stringResource(R.string.profile_edit_title), style = MaterialTheme.typography.headlineLarge)

            Spacer(Modifier.height(20.dp))

            ProfileField(name, { name = it }, stringResource(R.string.profile_name), Icons.Outlined.Badge)

            Spacer(Modifier.height(12.dp))

            ProfileField(
                salary,
                {
                    salary = it
                    if (overtimeManuallyEdited) overtimeManuallyEdited = false
                },
                stringResource(R.string.profile_salary),
                Icons.Outlined.AttachMoney,
                KeyboardType.Number
            )

            Spacer(Modifier.height(12.dp))

            ProfileField(
                workingDays,
                { workingDays = it },
                "Working Days",
                Icons.Outlined.Work,
                KeyboardType.Number
            )

            Spacer(Modifier.height(12.dp))

            ProfileField(
                hours,
                {
                    hours = it
                    if (overtimeManuallyEdited) overtimeManuallyEdited = false
                },
                stringResource(R.string.profile_hours),
                Icons.Outlined.Schedule,
                KeyboardType.Number
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = overtime,
                onValueChange = {
                    overtime = it
                    overtimeManuallyEdited = true
                },
                label = {
                    Text(
                        if (overtimeManuallyEdited)
                            "Overtime Rate (Manual)"
                        else
                            "Overtime Rate (Auto)"
                    )
                },
                leadingIcon = { Icon(Icons.Outlined.AttachMoney, null) },
                trailingIcon = {
                    if (overtimeManuallyEdited) {
                        TextButton(
                            onClick = {
                                overtimeManuallyEdited = false
                                autoOvertimeRate?.let { overtime = it }
                            }
                        ) {
                            Text("Auto", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (!overtimeManuallyEdited && autoOvertimeRate != null) {
                Text(
                    text = "₹$autoOvertimeRate/hr (Salary ÷ Days ÷ Hours × 1.5)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    viewModel.updateProfile(
                        UserProfile(
                            name = name,
                            monthlySalary = salary.toDoubleOrNull() ?: 0.0,
                            workingDays = workingDays.toIntOrNull() ?: 0,
                            standardHours = hours.toDoubleOrNull() ?: 0.0,
                            overtimeRate = overtime.toDoubleOrNull() ?: 0.0
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text(stringResource(R.string.profile_update))
                }
            }

            Spacer(Modifier.height(20.dp))

            if (remoteConfig.showNativeAd()) {
                Text("Sponsored", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(6.dp))
                AdsController.NativeAdView()
            }
        }
    }
}

// ProfileField composable is defined in ProfileField.kt (same package)
// Removing duplicate definition — use the richer version from ProfileField.kt

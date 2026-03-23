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
import com.aaryo.selfattendance.ads.NativeAdCard
import com.aaryo.selfattendance.data.model.UserProfile
import com.aaryo.selfattendance.data.remote.RemoteConfigManager
import com.aaryo.selfattendance.ads.BannerAd
import com.aaryo.selfattendance.domain.SalaryCalculator
import com.aaryo.selfattendance.ui.dashboard.formatMoney
import kotlinx.coroutines.launch
import java.math.RoundingMode
import java.util.Calendar
import com.aaryo.selfattendance.ui.profile.SalaryBreakdownCard

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

    val perDayAmount = salary.toDoubleOrNull()?.let {
        if (it > 0) it / SalaryCalculator.CALENDAR_DAYS else null
    }

    val perHourAmount = perDayAmount?.let { p ->
        hours.toDoubleOrNull()?.takeIf { it > 0 }?.let { p / it }
    }

    val autoOvertimeRate = perHourAmount?.let { it * 1.5 }

    val earnedPercent = workingDays.toIntOrNull()?.let {
        if (it > 0) (it.toDouble() / SalaryCalculator.CALENDAR_DAYS) * 100 else null
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
                name = it.name
                salary = it.monthlySalary.toString()
                workingDays = it.workingDays.toString()
                hours = it.standardHours.toString()
                overtime = it.overtimeRate.toString()
                initialized = true
            }
        }
    }

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
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {

            Text("Edit Profile", style = MaterialTheme.typography.headlineLarge)

            Spacer(Modifier.height(20.dp))

            ProfileField(name, { name = it }, "Full Name", Icons.Outlined.Badge)

            Spacer(Modifier.height(12.dp))

            ProfileField(
                salary,
                { salary = it },
                "Monthly Salary (₹)",
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
                { hours = it },
                "Hours Per Day",
                Icons.Outlined.Schedule,
                KeyboardType.Number
            )

            Spacer(Modifier.height(12.dp))

            ProfileField(
                overtime,
                { overtime = it },
                "Overtime Rate",
                Icons.Outlined.AttachMoney,
                KeyboardType.Number
            )

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
                    Text("Update Profile")
                }
            }

            Spacer(Modifier.height(20.dp))

            if (remoteConfig.showNativeAd()) {
                Text("Sponsored", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(6.dp))
                NativeAdCard(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// --------- Reusable ---------

@Composable
fun ProfileField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

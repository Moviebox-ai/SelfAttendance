package com.aaryo.selfattendance.ui.salaryslip

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaryo.selfattendance.R
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aaryo.selfattendance.ads.AdsController
import com.aaryo.selfattendance.data.model.Attendance
import com.aaryo.selfattendance.data.remote.RemoteConfigManager
import com.aaryo.selfattendance.data.repository.AttendanceRepository
import com.aaryo.selfattendance.data.repository.AuthRepository
import com.aaryo.selfattendance.ui.dashboard.DashboardViewModel
import com.aaryo.selfattendance.ui.shared.MonthViewModel
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.aaryo.selfattendance.utils.CurrencyManager
import com.aaryo.selfattendance.utils.SalarySlipGenerator
import kotlinx.coroutines.launch
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalarySlipScreen(navController: NavController) {

    val context      = LocalContext.current
    // locale-wrapped ContextWrapper, not the Activity instance. Use findActivity() to
    // walk the ContextWrapper chain (see LocaleManager.kt for details).
    val activity     = with(com.aaryo.selfattendance.utils.LocaleManager) { context.findActivity() }
    val currencySymbol = remember(PreferencesManager(context).selectedCurrency) { CurrencyManager.getSymbol(PreferencesManager(context).selectedCurrency) }
    val scope        = rememberCoroutineScope()

    val dashViewModel: DashboardViewModel = viewModel()
    val monthViewModel: MonthViewModel    = viewModel()

    val state        by dashViewModel.state.collectAsState()
    val currentMonth by monthViewModel.selectedMonth.collectAsState()

    val remoteConfig   = remember { RemoteConfigManager.getInstance() }

    val attendanceRepo = remember { AttendanceRepository() }
    val authRepo       = remember { AuthRepository() }

    var allLogs        by remember { mutableStateOf<List<Attendance>>(emptyList()) }
    var isLoadingLogs  by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val uid = authRepo.currentUser()?.uid ?: return@LaunchedEffect
            attendanceRepo.observeAttendance(uid).collect { logs ->
                allLogs = logs
                isLoadingLogs = false
            }
        } catch (_: Exception) {
            isLoadingLogs = false
        }
    }

    val monthLogs = remember(allLogs, currentMonth) {
        allLogs.filter { att ->
            try {
                att.date.startsWith(currentMonth.toString())
            } catch (_: Exception) { false }
        }
    }

    var bonusText     by remember { mutableStateOf("") }
    var deductionText by remember { mutableStateOf("") }
    var isGenerating  by remember { mutableStateOf(false) }

    val profile     = state.profile
    val present     = monthLogs.count { it.status == "PRESENT" }
    val half        = monthLogs.count { it.status == "HALF" || it.status == "HALF_DAY" }
    val holiday     = monthLogs.count { it.status == "HOLIDAY" }
    val absent      = monthLogs.count { it.status == "ABSENT" }
    val overtime    = monthLogs.sumOf { it.overtimeHours }

    val perDay      = if (profile.monthlySalary > 0) profile.monthlySalary / 30.0 else 0.0
    val basicPay    = (present + holiday) * perDay
    val halfPay     = half * (perDay / 2)
    val overtimePay = overtime * profile.overtimeRate
    val bonus       = bonusText.toDoubleOrNull() ?: 0.0
    val deductions  = deductionText.toDoubleOrNull() ?: 0.0
    // SalaryCalculator.calculate() computes salary based on days PRESENT + HALF only;
    // it does NOT add a separate absent deduction — salary is earned, not deducted.
    // Showing an "Absent Deduction" line here that doesn't exist in the canonical
    // SalaryCalculator created a discrepancy: SalarySlipScreen showed a lower net
    // salary than DashboardScreen for the same month. Both screens must agree.
    // The absence is already reflected in the lower basicPay (you only earn for days present).
    val gross       = basicPay + halfPay + overtimePay + bonus
    val totalDed    = deductions
    val net         = gross - totalDed

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.salary_slip_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = { AdsController.BannerAdView() },
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {

            // Month selector
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        monthViewModel.setMonth(currentMonth.minusMonths(1))
                    }) {
                        Icon(Icons.Rounded.ChevronLeft, null)
                    }

                    Text(
                        text = buildString {
                            append(currentMonth.month.name
                                .lowercase()
                                .replaceFirstChar { it.uppercase() })
                            append(" ${currentMonth.year}")
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    IconButton(
                        onClick = {
                            // Only allow navigation to current month or past — not future
                            if (currentMonth < YearMonth.now()) {
                                monthViewModel.setMonth(currentMonth.plusMonths(1))
                            }
                        },
                        enabled = currentMonth < YearMonth.now()
                    ) {
                        Icon(Icons.Rounded.ChevronRight, null)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Employee info
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.salary_slip_employee_details),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        profile.name.ifBlank { "Employee" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        InfoChip("CTC", "$currencySymbol${profile.monthlySalary.toLong()}")
                        InfoChip("Days", "${profile.workingDays}")
                        InfoChip("Hours", "${profile.standardHours}h/day")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Attendance stats
            Text(
                "Attendance — ${currentMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))

            if (isLoadingLogs) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AttendanceStat("Present", "$present", Color(0xFF00C853), Modifier.weight(1f))
                    AttendanceStat("Half",    "$half",    Color(0xFFFFB300), Modifier.weight(1f))
                    AttendanceStat("Holiday", "$holiday", Color(0xFF7C4DFF), Modifier.weight(1f))
                    AttendanceStat("Absent",  "$absent",  Color(0xFFE53935), Modifier.weight(1f))
                    AttendanceStat("OT", "${String.format(java.util.Locale.US, "%.1f", overtime)}h", Color(0xFF1565C0), Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(20.dp))

            // Bonus & Deductions
            Text(
                "Adjustments (optional)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = bonusText,
                    onValueChange = { bonusText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Bonus ($currencySymbol)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = deductionText,
                    onValueChange = { deductionText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Extra deduction ($currencySymbol)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(20.dp))

            // Salary preview
            Text(
                "Salary Preview",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    SalaryRow("Basic Pay",     basicPay,    Color(0xFF00C853), currencySymbol = currencySymbol)
                    SalaryRow("Half Day Pay",  halfPay,     Color(0xFF00C853), currencySymbol = currencySymbol)
                    SalaryRow("Overtime Pay",  overtimePay, Color(0xFF00C853), currencySymbol = currencySymbol)
                    if (bonus > 0) SalaryRow("Bonus", bonus, Color(0xFF00C853), currencySymbol = currencySymbol)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SalaryRow("Gross Earnings", gross, Color(0xFF00C853), bold = true, currencySymbol = currencySymbol)

                    Spacer(Modifier.height(8.dp))

                    // because salary is calculated on days-worked basis (not deduction
                    // from full salary). This matches SalaryCalculator.calculate().
                    if (deductions > 0) SalaryRow("Other Deductions", -deductions, Color(0xFFE53935), currencySymbol = currencySymbol)

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(10.dp)
                            )
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Net Salary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            formatMoney(net, currencySymbol),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            run {
                // Generate button
                Button(
                    onClick = {
                        isGenerating = true
                        SalarySlipGenerator.generate(
                            context        = context,
                            profile        = profile,
                            attendanceList = monthLogs,
                            month          = currentMonth,
                            bonus          = bonus,
                            deductions     = deductions,
                            onDone = {
                                isGenerating = false
                                // Show interstitial after successful PDF generation
                                // Policy-safe: save/action already done, ad comes after
                                activity?.let { AdsController.showInterstitialAfterSave(it) }
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape   = RoundedCornerShape(14.dp),
                    enabled = !isGenerating && !isLoadingLogs && profile.monthlySalary > 0
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(20.dp),
                            color       = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Outlined.PictureAsPdf, null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Generate & Save Salary Slip PDF",
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (profile.monthlySalary == 0.0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.salary_slip_setup_profile),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            // Native ad
            if (remoteConfig.showNativeAd()) {
                Spacer(Modifier.height(16.dp))
                AdsController.FreshNativeAdView()
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
}

@Composable
private fun AttendanceStat(label: String, value: String, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, color = color, fontSize = 18.sp)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun SalaryRow(label: String, amount: Double, color: Color, bold: Boolean = false,
                      currencySymbol: String = "₹") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = if (bold) MaterialTheme.typography.bodyLarge
                    else MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        Text(
            formatMoney(amount, currencySymbol),
            style = if (bold) MaterialTheme.typography.bodyLarge
                    else MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = color
        )
    }
}

private fun formatMoney(amount: Double, currencySymbol: String = "₹"): String {
    if (amount.isNaN() || amount.isInfinite()) return "${currencySymbol}0.00"
    val prefix = if (amount < 0) "-$currencySymbol" else currencySymbol
    val abs = Math.abs(amount)
    val intPart = abs.toLong()
    val decPart = Math.round((abs - intPart) * 100)
    return "$prefix$intPart.${decPart.toString().padStart(2, '0')}"
}

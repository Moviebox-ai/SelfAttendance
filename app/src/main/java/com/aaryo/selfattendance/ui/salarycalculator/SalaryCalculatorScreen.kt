package com.aaryo.selfattendance.ui.salarycalculator

import androidx.compose.animation.*
import androidx.compose.ui.platform.LocalContext
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.aaryo.selfattendance.utils.CurrencyManager
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aaryo.selfattendance.R
import com.aaryo.selfattendance.ads.AdsController
import com.aaryo.selfattendance.data.remote.RemoteConfigManager
import java.text.NumberFormat
import java.util.Locale

// ── Money formatter (currency-aware) ─────────────────────────────────────────
private fun fmt(amount: Double, currencySymbol: String = "₹"): String {
    val nf = NumberFormat.getNumberInstance(Locale.getDefault())
    nf.minimumFractionDigits = 2
    nf.maximumFractionDigits = 2
    return "$currencySymbol${nf.format(amount)}"
}

// ─────────────────────────────────────────────────────────────────────────────
// Root Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalaryCalculatorScreen(
    navController: NavController,
    viewModel: SalaryCalculatorViewModel = viewModel()
) {
    val state        by viewModel.state.collectAsStateWithLifecycle()
    val focusManager  = LocalFocusManager.current
    val scrollState   = rememberScrollState()
    val remoteConfig  = remember { RemoteConfigManager.getInstance() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector        = Icons.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint               = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector        = Icons.Default.Calculate,
                                contentDescription = null,
                                tint               = MaterialTheme.colorScheme.primary,
                                modifier           = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text       = "Salary Calculator",
                                style      = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text  = "30-Day Deduction · 8-Hour Day",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.reset() }) {
                        Icon(
                            imageVector        = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            tint               = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (remoteConfig.showBannerAd()) AdsController.BannerAd()
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FormulaBanner()

            InputCard(
                state           = state,
                onSalaryChange  = viewModel::onMonthlySalaryChange,
                onPresentChange = viewModel::onPresentDaysChange,
                onHalfChange    = viewModel::onHalfDaysChange,
                onAbsentChange  = viewModel::onAbsentDaysChange,
                onCalculate     = { focusManager.clearFocus(); viewModel.calculate() },
                focusManager    = focusManager
            )

            AnimatedVisibility(
                visible = state.isCalculated,
                enter   = fadeIn(tween(300)) + expandVertically(tween(300)),
                exit    = fadeOut(tween(200)) + shrinkVertically(tween(200))
            ) {
                if (state.isCalculated && state.finalSalary != null) {
                    ResultSection(state = state)
                }
            }

            val remoteConfigNative = remoteConfig
            if (remoteConfigNative.showNativeAd()) {
                Text(
                    text  = "Sponsored",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                AdsController.FreshNativeAdView()
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Formula Banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FormulaBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Outlined.Info,
                    contentDescription = null,
                    tint               = Color.White,
                    modifier           = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text       = stringResource(R.string.salary_calc_formula_title),
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White
                )
            }
            Spacer(Modifier.height(10.dp))
            FormulaLine(stringResource(R.string.salary_calc_formula_per_day_label), stringResource(R.string.salary_calc_formula_per_day))
            FormulaLine(stringResource(R.string.salary_calc_formula_per_hour_label), stringResource(R.string.salary_calc_formula_per_hour))
            FormulaLine(stringResource(R.string.salary_calc_formula_half_day_label), stringResource(R.string.salary_calc_formula_half_day))
            FormulaLine(stringResource(R.string.salary_calc_formula_deduction_label), stringResource(R.string.salary_calc_formula_deduction))
            FormulaLine(stringResource(R.string.salary_calc_formula_final_pay_label), stringResource(R.string.salary_calc_formula_final_pay))
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.3f))
            Spacer(Modifier.height(8.dp))
            Text(
                text  = stringResource(R.string.salary_calc_formula_example),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun FormulaLine(label: String, formula: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text       = label,
            style      = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color      = Color.White,
            modifier   = Modifier.width(82.dp)
        )
        Text(
            text  = formula,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.88f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Input Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InputCard(
    state           : SalaryCalculatorState,
    onSalaryChange  : (String) -> Unit,
    onPresentChange : (String) -> Unit,
    onHalfChange    : (String) -> Unit,
    onAbsentChange  : (String) -> Unit,
    onCalculate     : () -> Unit,
    focusManager    : androidx.compose.ui.focus.FocusManager
) {
    Card(
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier            = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(icon = Icons.Outlined.Payments, title = stringResource(R.string.salary_calc_monthly_salary))

            OutlinedTextField(
                value         = state.monthlySalaryInput,
                onValueChange = { if (it.length <= 10) onSalaryChange(it) },
                label         = { Text(stringResource(R.string.salary_calc_enter_monthly_salary)) },
                placeholder   = {
                    Text(
                        "e.g. 18000",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                },
                prefix        = {
                    val ctx = LocalContext.current
                    val sym = remember { CurrencyManager.getSymbol(PreferencesManager(ctx).selectedCurrency) }
                    Text(sym, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                },
                isError        = state.salaryError != null,
                supportingText = state.salaryError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                singleLine    = true,
                shape         = RoundedCornerShape(14.dp),
                modifier      = Modifier.fillMaxWidth(),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    errorBorderColor     = MaterialTheme.colorScheme.error
                )
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            SectionHeader(icon = Icons.Outlined.CalendarMonth, title = stringResource(R.string.salary_calc_attendance_30))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                DayInputField(
                    value           = state.presentDaysInput,
                    onValueChange   = onPresentChange,
                    label           = stringResource(R.string.dashboard_present),
                    errorText       = state.presentDaysError,
                    accentColor     = Color(0xFF2E7D32),
                    icon            = Icons.Outlined.CheckCircle,
                    imeAction       = ImeAction.Next,
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Right) }),
                    modifier        = Modifier.weight(1f)
                )
                DayInputField(
                    value           = state.halfDaysInput,
                    onValueChange   = onHalfChange,
                    label           = stringResource(R.string.dashboard_half_day),
                    errorText       = state.halfDaysError,
                    accentColor     = Color(0xFFF57F17),
                    icon            = Icons.Outlined.WbSunny,
                    imeAction       = ImeAction.Next,
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Right) }),
                    modifier        = Modifier.weight(1f)
                )
                DayInputField(
                    value           = state.absentDaysInput,
                    onValueChange   = onAbsentChange,
                    label           = stringResource(R.string.dashboard_absent),
                    errorText       = state.absentDaysError,
                    accentColor     = MaterialTheme.colorScheme.error,
                    icon            = Icons.Outlined.Cancel,
                    imeAction       = ImeAction.Done,
                    keyboardActions = KeyboardActions(onDone = { onCalculate() }),
                    modifier        = Modifier.weight(1f)
                )
            }

            if (state.absentDaysError?.startsWith("Total") == true) {
                Text(
                    text  = state.absentDaysError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick  = onCalculate,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(imageVector = Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.salary_calc_calculate_salary), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Result Section — 6 summary cards + breakdown + final hero
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ResultSection(state: SalaryCalculatorState) {
    val context        = LocalContext.current
    val currencySymbol = remember { CurrencyManager.getSymbol(PreferencesManager(context).selectedCurrency) }
    val monthlySalary = state.monthlySalaryInput.toDoubleOrNull() ?: 0.0
    val perDay        = state.perDaySalary    ?: 0.0
    val perHour       = state.perHourSalary   ?: 0.0
    val halfDay       = state.halfDaySalary   ?: 0.0
    val deduction     = state.totalDeduction  ?: 0.0
    val finalSalary   = state.finalSalary     ?: 0.0

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        SectionHeader(icon = Icons.Outlined.Summarize, title = stringResource(R.string.salary_calc_salary_summary))

        // ── Row 1: Monthly Salary · Per Day ──────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryCard(
                label    = stringResource(R.string.salary_calc_monthly_salary),
                amount   = fmt(monthlySalary, currencySymbol),
                subLabel = stringResource(R.string.salary_calc_total_30_days),
                icon     = Icons.Outlined.Payments,
                iconTint = MaterialTheme.colorScheme.primary,
                bgColor  = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                label    = stringResource(R.string.summary_per_day),
                amount   = fmt(perDay, currencySymbol),
                subLabel = stringResource(R.string.salary_calc_monthly_div_30),
                icon     = Icons.Outlined.Today,
                iconTint = Color(0xFF1565C0),
                bgColor  = Color(0xFF1565C0).copy(alpha = 0.08f),
                modifier = Modifier.weight(1f)
            )
        }

        // ── Row 2: Per Hour · Half Day ────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SummaryCard(
                label    = stringResource(R.string.summary_per_hour),
                amount   = fmt(perHour, currencySymbol),
                subLabel = stringResource(R.string.salary_calc_per_day_div_8),
                icon     = Icons.Outlined.Schedule,
                iconTint = Color(0xFF6A1B9A),
                bgColor  = Color(0xFF6A1B9A).copy(alpha = 0.08f),
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                label    = stringResource(R.string.dashboard_half_day),
                amount   = fmt(halfDay, currencySymbol),
                subLabel = stringResource(R.string.salary_calc_per_day_div_2),
                icon     = Icons.Outlined.WbTwilight,
                iconTint = Color(0xFFF57F17),
                bgColor  = Color(0xFFF57F17).copy(alpha = 0.08f),
                modifier = Modifier.weight(1f)
            )
        }

        // ── Row 3: Total Deduction (full width) ───────────────────────────────
        SummaryCard(
            label    = stringResource(R.string.salary_calc_total_deduction),
            amount   = "− ${fmt(deduction, currencySymbol)}",
            subLabel = stringResource(R.string.salary_calc_deduction_formula),
            icon     = Icons.Outlined.RemoveCircle,
            iconTint = MaterialTheme.colorScheme.error,
            bgColor  = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
        )

        // ── Breakdown ─────────────────────────────────────────────────────────
        BreakdownCard(state = state)

        // ── Final Salary Hero ─────────────────────────────────────────────────
        FinalSalaryCard(finalSalary = finalSalary)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Breakdown Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BreakdownCard(state: SalaryCalculatorState) {
    val context        = LocalContext.current
    val currencySymbol = remember { CurrencyManager.getSymbol(PreferencesManager(context).selectedCurrency) }
    val monthlySalary = state.monthlySalaryInput.toDoubleOrNull() ?: 0.0
    val perDay        = state.perDaySalary   ?: 0.0
    val perHour       = state.perHourSalary  ?: 0.0
    val halfDay       = state.halfDaySalary  ?: 0.0
    val deduction     = state.totalDeduction ?: 0.0
    val finalSalary   = state.finalSalary    ?: 0.0
    val absentDays    = state.absentDaysInput.toIntOrNull() ?: 0
    val halfDays      = state.halfDaysInput.toIntOrNull()   ?: 0

    Card(
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier  = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            Text(
                text       = stringResource(R.string.salary_calc_breakdown),
                style      = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(14.dp))

            // Base rates
            BreakdownRow(stringResource(R.string.salary_calc_monthly_salary),            fmt(monthlySalary, currencySymbol), MaterialTheme.colorScheme.onSurface)
            BreakdownRow(stringResource(R.string.salary_calc_per_day_div_30),            fmt(perDay, currencySymbol),        MaterialTheme.colorScheme.primary)
            BreakdownRow(stringResource(R.string.salary_calc_per_hour_div_8),     fmt(perHour, currencySymbol),       Color(0xFF6A1B9A))
            BreakdownRow(stringResource(R.string.salary_calc_half_day_div_2),             fmt(halfDay, currencySymbol),       Color(0xFFF57F17))

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)

            // Deduction lines
            if (absentDays > 0) {
                BreakdownRow(
                    label = stringResource(R.string.salary_calc_absent_deduction, absentDays, fmt(perDay, currencySymbol)),
                    value = "− ${fmt(absentDays * perDay, currencySymbol)}",
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (halfDays > 0) {
                BreakdownRow(
                    label = stringResource(R.string.salary_calc_half_days_deduction, halfDays, fmt(halfDay, currencySymbol)),
                    value = "− ${fmt(halfDays * halfDay, currencySymbol)}",
                    color = Color(0xFFF57F17)
                )
            }
            if (absentDays == 0 && halfDays == 0) {
                BreakdownRow(
                    label = stringResource(R.string.salary_calc_no_deduction),
                    value = "${currencySymbol}0.00",
                    color = Color(0xFF2E7D32)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)

            BreakdownRow(
                label = stringResource(R.string.salary_calc_total_deduction),
                value = "− ${fmt(deduction, currencySymbol)}",
                color = MaterialTheme.colorScheme.error,
                bold  = true
            )
            Spacer(Modifier.height(2.dp))
            BreakdownRow(
                label = stringResource(R.string.salary_calc_final_payable),
                value = fmt(finalSalary, currencySymbol),
                color = Color(0xFF2E7D32),
                bold  = true
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Final Salary Hero Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FinalSalaryCard(finalSalary: Double) {
    val context        = LocalContext.current
    val currencySymbol = remember { CurrencyManager.getSymbol(PreferencesManager(context).selectedCurrency) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF43A047))
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 24.dp, vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Outlined.CurrencyRupee,
                    contentDescription = null,
                    tint               = Color(0xFFFFD700),
                    modifier           = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text       = "Final Salary Payable",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = Color.White.copy(alpha = 0.9f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text          = fmt(finalSalary, currencySymbol),
                fontSize      = 38.sp,
                fontWeight    = FontWeight.ExtraBold,
                color         = Color(0xFFFFD700),
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text  = "Net take-home · 30-day deduction basis",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable UI Components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.primary,
            modifier           = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text       = title,
            style      = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SummaryCard(
    label    : String,
    amount   : String,
    subLabel : String,
    icon     : ImageVector,
    iconTint : Color,
    bgColor  : Color,
    modifier : Modifier = Modifier
) {
    Card(
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier  = modifier
    ) {
        Row(
            modifier          = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = iconTint,
                    modifier           = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text  = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text       = amount,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = iconTint,
                    maxLines   = 1
                )
                Text(
                    text  = subLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun BreakdownRow(
    label : String,
    value : String,
    color : Color,
    bold  : Boolean = false
) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            text       = label,
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier   = Modifier.weight(1f)
        )
        Text(
            text       = value,
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color      = color,
            textAlign  = TextAlign.End
        )
    }
}

@Composable
private fun DayInputField(
    value           : String,
    onValueChange   : (String) -> Unit,
    label           : String,
    errorText       : String?,
    accentColor     : Color,
    icon            : ImageVector,
    imeAction       : ImeAction,
    keyboardActions : KeyboardActions,
    modifier        : Modifier = Modifier
) {
    val isError = errorText != null && !errorText.startsWith("Total")
    Column(modifier = modifier) {
        OutlinedTextField(
            value         = value,
            onValueChange = { if (it.length <= 2) onValueChange(it) },
            label         = { Text(label, style = MaterialTheme.typography.labelSmall) },
            leadingIcon   = {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = if (isError) MaterialTheme.colorScheme.error else accentColor,
                    modifier           = Modifier.size(18.dp)
                )
            },
            isError         = isError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
            keyboardActions = keyboardActions,
            singleLine      = true,
            shape           = RoundedCornerShape(12.dp),
            colors          = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = accentColor,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                errorBorderColor     = MaterialTheme.colorScheme.error
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                textAlign  = TextAlign.Center
            )
        )
        if (isError && errorText != null) {
            Text(
                text     = errorText,
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}

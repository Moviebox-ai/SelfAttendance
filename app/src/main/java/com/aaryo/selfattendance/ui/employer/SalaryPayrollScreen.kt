package com.aaryo.selfattendance.ui.employer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aaryo.selfattendance.data.model.Employee
import com.aaryo.selfattendance.data.model.StaffSalaryPayout
import com.aaryo.selfattendance.utils.StaffReportGenerator
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalaryPayrollScreen(
    navController: NavController,
    viewModel: EmployerViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val employees by viewModel.allEmployees.collectAsState()
    val payouts by viewModel.monthPayouts.collectAsState()
    val businessName by viewModel.businessName.collectAsState()

    val parsedMonth = remember(selectedMonth) {
        runCatching { YearMonth.parse(selectedMonth) }.getOrDefault(YearMonth.now())
    }

    val displayMonth = remember(parsedMonth) {
        "${parsedMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${parsedMonth.year}"
    }

    var calculatedPayouts by remember { mutableStateOf<Map<Long, StaffSalaryPayout>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(false) }
    var payingPayout by remember { mutableStateOf<Pair<Employee, StaffSalaryPayout>?>(null) }

    // Auto-calculate for all active employees when month changes
    LaunchedEffect(selectedMonth, employees, payouts) {
        isLoading = true
        val map = mutableMapOf<Long, StaffSalaryPayout>()
        employees.forEach { emp ->
            val existing = payouts.find { it.employeeId == emp.id }
            if (existing != null) {
                map[emp.id] = existing
            } else {
                val calculated = viewModel.getCalculatedPayout(emp.id, selectedMonth)
                map[emp.id] = calculated
            }
        }
        calculatedPayouts = map
        isLoading = false
    }

    val totalNetPayable = remember(calculatedPayouts) {
        calculatedPayouts.values.sumOf { it.netPayable }
    }
    val totalPaid = remember(calculatedPayouts) {
        calculatedPayouts.values.sumOf { it.paidAmount }
    }
    val totalPending = (totalNetPayable - totalPaid).coerceAtLeast(0.0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Salary & Payroll", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            StaffReportGenerator.exportStaffMonthlyReportCsv(
                                context = context,
                                businessName = businessName,
                                monthYear = selectedMonth,
                                employees = employees,
                                payouts = calculatedPayouts
                            )
                        }
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export Excel/CSV", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Month Selector Bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        val prev = parsedMonth.minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"))
                        viewModel.setMonth(prev)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month")
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = displayMonth,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Auto-Calculated Payroll",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = {
                        val next = parsedMonth.plusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"))
                        viewModel.setMonth(next)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Summary Totals
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PayrollStatTile(
                    title = "Total Net Payroll",
                    amount = "₹${totalNetPayable.toInt()}",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                PayrollStatTile(
                    title = "Paid Amount",
                    amount = "₹${totalPaid.toInt()}",
                    color = Color(0xFF2E7D32),
                    modifier = Modifier.weight(1f)
                )
                PayrollStatTile(
                    title = "Pending",
                    amount = "₹${totalPending.toInt()}",
                    color = Color(0xFFC62828),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Staff Payroll List (${employees.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            StaffReportGenerator.generateCompanyPayrollPdf(
                                context = context,
                                businessName = businessName,
                                monthYear = selectedMonth,
                                employees = employees,
                                payouts = calculatedPayouts
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Master PDF", fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = {
                            StaffReportGenerator.exportStaffMonthlyReportCsv(
                                context = context,
                                businessName = businessName,
                                monthYear = selectedMonth,
                                employees = employees,
                                payouts = calculatedPayouts
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("Excel / CSV", fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (employees.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No staff added yet. Add staff from Staff List.")
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(employees, key = { it.id }) { emp ->
                        val payout = calculatedPayouts[emp.id]
                        if (payout != null) {
                            PayrollEmployeeCard(
                                employee = emp,
                                payout = payout,
                                onPayClick = { payingPayout = Pair(emp, payout) },
                                onSharePdf = {
                                    scope.launch {
                                        val attList = viewModel.getEmployeeMonthAttendance(emp.id, selectedMonth)
                                        val advList = viewModel.getEmployeeAdvances(emp.id)
                                        StaffReportGenerator.generateSalarySlipPdf(
                                            context = context,
                                            businessName = businessName,
                                            employee = emp,
                                            payout = payout,
                                            monthYear = selectedMonth,
                                            attendanceList = attList,
                                            advances = advList
                                        )
                                    }
                                }
                            )
                        }
                    }
                    item {
                        Spacer(Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    // Pay Salary Dialog
    payingPayout?.let { (emp, payout) ->
        var allowanceAmt by remember { mutableStateOf(if (payout.allowance > 0) payout.allowance.toInt().toString() else if (emp.fixedAllowance > 0) emp.fixedAllowance.toInt().toString() else "0") }
        var bonusAmt by remember { mutableStateOf(if (payout.bonus > 0) payout.bonus.toInt().toString() else "0") }
        var otherDeductionAmt by remember { mutableStateOf(if (payout.otherDeductions > 0) payout.otherDeductions.toInt().toString() else if (emp.pfDeduction + emp.esiDeduction > 0) (emp.pfDeduction + emp.esiDeduction).toInt().toString() else "0") }
        
        // Base Earned without allowances
        val baseEarnedOnly = remember(payout) { (payout.grossSalary - payout.allowance).coerceAtLeast(0.0) }
        
        val currentAllowance = allowanceAmt.toDoubleOrNull() ?: 0.0
        val currentBonus = bonusAmt.toDoubleOrNull() ?: 0.0
        val currentDeductions = otherDeductionAmt.toDoubleOrNull() ?: 0.0
        val dynamicGross = baseEarnedOnly + currentAllowance
        val dynamicNet = (dynamicGross + currentBonus - payout.totalAdvancesDeducted - currentDeductions).coerceAtLeast(0.0)
        
        var payAmount by remember { mutableStateOf(if (payout.paidAmount > 0) payout.paidAmount.toInt().toString() else payout.netPayable.toInt().toString()) }
        var paymentMode by remember { mutableStateOf(payout.paymentMode) }
        var notes by remember { mutableStateOf(payout.notes) }

        AlertDialog(
            onDismissRequest = { payingPayout = null },
            title = { Text("Disburse Salary: ${emp.name}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Base + OT Earned: ₹${baseEarnedOnly.toInt()}  |  Advance: -₹${payout.totalAdvancesDeducted.toInt()}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(2.dp))
                            Text("Estimated Net Payable: ₹${dynamicNet.toInt()}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                        }
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = allowanceAmt,
                            onValueChange = { allowanceAmt = it },
                            label = { Text("Allowance (+)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = bonusAmt,
                            onValueChange = { bonusAmt = it },
                            label = { Text("Bonus (+)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = otherDeductionAmt,
                        onValueChange = { otherDeductionAmt = it },
                        label = { Text("PF / ESI / Other Deductions (-)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = payAmount,
                        onValueChange = { payAmount = it },
                        label = { Text("Actual Paid Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Payment Mode", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = paymentMode == "CASH",
                            onClick = { paymentMode = "CASH" },
                            label = { Text("Cash") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = paymentMode == "UPI",
                            onClick = { paymentMode = "UPI" },
                            label = { Text("UPI") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = paymentMode == "BANK_TRANSFER",
                            onClick = { paymentMode = "BANK_TRANSFER" },
                            label = { Text("Bank") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Transaction ID / Note (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = payAmount.toDoubleOrNull() ?: 0.0
                    val allw = allowanceAmt.toDoubleOrNull() ?: 0.0
                    val bns = bonusAmt.toDoubleOrNull() ?: 0.0
                    val ded = otherDeductionAmt.toDoubleOrNull() ?: 0.0
                    val newGross = baseEarnedOnly + allw
                    val newNet = (newGross + bns - payout.totalAdvancesDeducted - ded).coerceAtLeast(0.0)
                    val updatedPayout = payout.copy(
                        grossSalary = Math.round(newGross * 100.0) / 100.0,
                        allowance = allw,
                        bonus = bns,
                        otherDeductions = ded,
                        netPayable = Math.round(newNet * 100.0) / 100.0
                    )
                    viewModel.recordSalaryPayment(updatedPayout, amt, paymentMode, notes) {
                        payingPayout = null
                    }
                }) {
                    Text("Save & Disburse")
                }
            },
            dismissButton = {
                TextButton(onClick = { payingPayout = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PayrollStatTile(
    title: String,
    amount: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = amount,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun PayrollEmployeeCard(
    employee: Employee,
    payout: StaffSalaryPayout,
    onPayClick: () -> Unit,
    onSharePdf: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = employee.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${employee.designation} • ${payout.totalPresentDays.toInt()}P, ${payout.totalHalfDays.toInt()}HD, ${payout.totalAbsentDays.toInt()}A" + (if (payout.totalOvertimeHours > 0) " • ${payout.totalOvertimeHours}h OT" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (payout.paymentStatus) {
                        StaffSalaryPayout.STATUS_PAID -> Color(0xFF2E7D32).copy(alpha = 0.15f)
                        StaffSalaryPayout.STATUS_PARTIAL -> Color(0xFFF57C00).copy(alpha = 0.15f)
                        else -> Color(0xFFC62828).copy(alpha = 0.15f)
                    }
                ) {
                    Text(
                        text = payout.paymentStatus,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (payout.paymentStatus) {
                            StaffSalaryPayout.STATUS_PAID -> Color(0xFF2E7D32)
                            StaffSalaryPayout.STATUS_PARTIAL -> Color(0xFFF57C00)
                            else -> Color(0xFFC62828)
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(8.dp))

            // Breakdown Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Gross Earned", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("₹${payout.grossSalary.toInt()}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                if (payout.totalAdvancesDeducted > 0) {
                    Column {
                        Text("Advance Deducted", fontSize = 11.sp, color = Color(0xFFE65100))
                        Text("-₹${payout.totalAdvancesDeducted.toInt()}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFFE65100))
                    }
                }
                Column {
                    Text("Net Payable", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    Text("₹${payout.netPayable.toInt()}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSharePdf,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PDF Slip", fontSize = 12.sp)
                }
                Button(
                    onClick = onPayClick,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (payout.paymentStatus == StaffSalaryPayout.STATUS_PAID) "Edit Pay" else "Pay Salary", fontSize = 12.sp)
                }
            }
        }
    }
}

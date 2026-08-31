package com.aaryo.selfattendance.ui.employer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aaryo.selfattendance.data.model.Employee
import com.aaryo.selfattendance.data.model.StaffAdvance
import com.aaryo.selfattendance.data.model.StaffAttendance
import com.aaryo.selfattendance.data.model.StaffSalaryPayout
import com.aaryo.selfattendance.utils.StaffReportGenerator
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffDetailScreen(
    employeeId: Long,
    navController: NavController,
    viewModel: EmployerViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allEmployees by viewModel.allEmployees.collectAsState()
    val employee = allEmployees.find { it.id == employeeId }
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val businessName by viewModel.businessName.collectAsState()

    val advances by viewModel.allAdvances.collectAsState()
    val employeeAdvances = remember(advances, employeeId) {
        advances.filter { it.employeeId == employeeId }
    }

    var calculatedPayout by remember { mutableStateOf<StaffSalaryPayout?>(null) }
    var showEditStaffDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(employeeId, selectedMonth, advances) {
        if (employee != null) {
            calculatedPayout = viewModel.getCalculatedPayout(employeeId, selectedMonth)
        }
    }

    if (employee == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Employee Details") },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Employee not found")
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(employee.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditStaffDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Profile")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Staff", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Profile Header Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = employee.name.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = employee.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${employee.designation} • Joined: ${employee.joiningDate.ifBlank { "N/A" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Salary Rate", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("₹${employee.baseSalary.toInt()} / ${if (employee.salaryType == Employee.SALARY_TYPE_MONTHLY) "Month" else if (employee.salaryType == Employee.SALARY_TYPE_DAILY) "Day" else "Hour"}", fontWeight = FontWeight.Bold)
                            }
                            if (employee.overtimeRatePerHour > 0) {
                                Column {
                                    Text("OT Rate", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("₹${employee.overtimeRatePerHour.toInt()} / Hr", fontWeight = FontWeight.Bold)
                                }
                            }
                            if (employee.phone.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${employee.phone}"))
                                        context.startActivity(intent)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Call")
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(Modifier.height(10.dp))

                        // Shift & Allowance summary
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Assigned Shift", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${employee.shiftName} (${employee.shiftStartTime} - ${employee.shiftEndTime})", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                            Column {
                                Text("Shift Duration", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${employee.standardShiftHours} hrs / day", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }

                        if (employee.fixedAllowance > 0 || employee.pfDeduction > 0 || employee.esiDeduction > 0) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (employee.fixedAllowance > 0) {
                                    Text("+₹${employee.fixedAllowance.toInt()} Fixed Allw", fontSize = 11.sp, color = Color(0xFF059669), fontWeight = FontWeight.SemiBold)
                                }
                                if (employee.pfDeduction > 0) {
                                    Text("-₹${employee.pfDeduction.toInt()} PF", fontSize = 11.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold)
                                }
                                if (employee.esiDeduction > 0) {
                                    Text("-₹${employee.esiDeduction.toInt()} ESI", fontSize = 11.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }

            // Monthly Salary Breakdown for this employee
            calculatedPayout?.let { payout ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Salary Breakdown ($selectedMonth)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "₹${payout.netPayable.toInt()}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Present: ${payout.totalPresentDays.toInt()} days", fontSize = 12.sp)
                                Text("Half: ${payout.totalHalfDays.toInt()} days", fontSize = 12.sp)
                                Text("Absent: ${payout.totalAbsentDays.toInt()} days", fontSize = 12.sp)
                                Text("OT: ${payout.totalOvertimeHours} hrs", fontSize = 12.sp)
                            }

                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Gross Earned:", fontSize = 12.sp)
                                Text("₹${payout.grossSalary.toInt()}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                            if (payout.totalAdvancesDeducted > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Advance Deductions:", fontSize = 12.sp, color = Color(0xFFE65100))
                                    Text("-₹${payout.totalAdvancesDeducted.toInt()}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFFE65100))
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    scope.launch {
                                        val attList = viewModel.getEmployeeMonthAttendance(employee.id, selectedMonth)
                                        val advList = viewModel.getEmployeeAdvances(employee.id)
                                        StaffReportGenerator.generateSalarySlipPdf(
                                            context = context,
                                            businessName = businessName,
                                            employee = employee,
                                            payout = payout,
                                            monthYear = selectedMonth,
                                            attendanceList = attList,
                                            advances = advList
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Download / Share Salary Slip")
                            }
                        }
                    }
                }
            }

            // Advances / Loan History for this staff
            item {
                Text(
                    text = "Advance History (${employeeAdvances.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (employeeAdvances.isEmpty()) {
                item {
                    Text(
                        "No advance taken by ${employee.name}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(employeeAdvances, key = { it.id }) { adv ->
                    AdvanceRowItem(
                        advance = adv,
                        employeeName = employee.name,
                        onToggleStatus = {
                            viewModel.toggleAdvanceStatus(adv.id, !adv.isDeducted, if (!adv.isDeducted) LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM")) else null)
                        }
                    )
                }
            }

            item {
                Spacer(Modifier.height(30.dp))
            }
        }
    }

    if (showEditStaffDialog) {
        AddEditStaffDialog(
            initialEmployee = employee,
            onDismiss = { showEditStaffDialog = false },
            onSave = { updated ->
                viewModel.saveEmployee(updated) {
                    showEditStaffDialog = false
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Employee?") },
            text = { Text("Are you sure you want to remove ${employee.name}? All attendance and advance records for this employee will be deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteEmployee(employee.id)
                        showDeleteConfirm = false
                        navController.navigateUp()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

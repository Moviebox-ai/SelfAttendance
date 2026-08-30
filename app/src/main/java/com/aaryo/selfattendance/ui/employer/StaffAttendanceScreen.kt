package com.aaryo.selfattendance.ui.employer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aaryo.selfattendance.data.model.Employee
import com.aaryo.selfattendance.data.model.StaffAttendance
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffAttendanceScreen(
    navController: NavController,
    viewModel: EmployerViewModel = viewModel()
) {
    val selectedDate by viewModel.selectedDate.collectAsState()
    val employees by viewModel.activeEmployees.collectAsState()
    val attendanceList by viewModel.dateAttendance.collectAsState()

    val attendanceMap = remember(attendanceList) {
        attendanceList.associateBy { it.employeeId }
    }

    val parsedDate = remember(selectedDate) {
        runCatching { LocalDate.parse(selectedDate) }.getOrDefault(LocalDate.now())
    }

    val formattedDate = remember(parsedDate) {
        parsedDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showOtDialogForEmp by remember { mutableStateOf<Employee?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Staff Attendance", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        viewModel.setDate(todayStr)
                    }) {
                        Icon(Icons.Default.Today, contentDescription = "Go to Today")
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
            // Date Navigation Row
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        val prevDay = parsedDate.minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        viewModel.setDate(prevDay)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Day")
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { showDatePicker = true }
                    ) {
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (parsedDate == LocalDate.now()) "TODAY" else "Tap to change date",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = {
                        val nextDay = parsedDate.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                        viewModel.setDate(nextDay)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Day")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Bulk Mark Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Employees (${employees.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(
                        onClick = { viewModel.markAllPresent() },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text("All Present", fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (employees.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No staff added yet. Add staff from Staff List first.")
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(employees, key = { it.id }) { emp ->
                        val attendance = attendanceMap[emp.id]
                        ModernStaffAttendanceCard(
                            employee = emp,
                            attendance = attendance,
                            onMarkPresent = { viewModel.markAttendance(emp.id, StaffAttendance.STATUS_PRESENT) },
                            onMarkHalfDay = { viewModel.markAttendance(emp.id, StaffAttendance.STATUS_HALF_DAY) },
                            onMarkAbsent = { viewModel.markAttendance(emp.id, StaffAttendance.STATUS_ABSENT) },
                            onOvertimeClick = { showOtDialogForEmp = emp },
                            onAdvanceClick = { navController.navigate("staff_detail/${emp.id}") },
                            onCardClick = { navController.navigate("staff_detail/${emp.id}") }
                        )
                    }
                    item {
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    // Overtime Dialog
    showOtDialogForEmp?.let { emp ->
        var otHours by remember { mutableStateOf("2") }
        AlertDialog(
            onDismissRequest = { showOtDialogForEmp = null },
            title = { Text("Overtime: ${emp.name}") },
            text = {
                Column {
                    Text("Enter overtime hours worked on $selectedDate:")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = otHours,
                        onValueChange = { otHours = it },
                        label = { Text("Hours") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val hours = otHours.toDoubleOrNull() ?: 0.0
                    viewModel.markAttendance(emp.id, StaffAttendance.STATUS_OVERTIME, overtimeHours = hours)
                    showOtDialogForEmp = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOtDialogForEmp = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = parsedDate.toEpochDay() * 86400000L
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val localDate = LocalDate.ofEpochDay(millis / 86400000L)
                        viewModel.setDate(localDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

package com.aaryo.selfattendance.ui.employer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aaryo.selfattendance.data.model.Employee
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffListScreen(
    navController: NavController,
    viewModel: EmployerViewModel = viewModel()
) {
    val context = LocalContext.current
    val allEmployees by viewModel.allEmployees.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAddStaffDialog by remember { mutableStateOf(false) }
    var editingEmployee by remember { mutableStateOf<Employee?>(null) }

    val filteredEmployees = remember(allEmployees, searchQuery) {
        if (searchQuery.isBlank()) allEmployees
        else allEmployees.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.designation.contains(searchQuery, ignoreCase = true) ||
            it.phone.contains(searchQuery)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Staff Directory (${allEmployees.size})", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingEmployee = null
                    showAddStaffDialog = true
                },
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text("Add Staff") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search by name, role or phone") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            if (filteredEmployees.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PeopleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "No staff added yet" else "No matching staff found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredEmployees, key = { it.id }) { emp ->
                        StaffRowCard(
                            employee = emp,
                            onClick = { navController.navigate("staff_detail/${emp.id}") },
                            onEdit = {
                                editingEmployee = emp
                                showAddStaffDialog = true
                            },
                            onCall = {
                                if (emp.phone.isNotBlank()) {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${emp.phone}"))
                                    context.startActivity(intent)
                                }
                            }
                        )
                    }
                    item {
                        Spacer(Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    if (showAddStaffDialog) {
        AddEditStaffDialog(
            initialEmployee = editingEmployee,
            onDismiss = { showAddStaffDialog = false },
            onSave = { emp ->
                viewModel.saveEmployee(emp) {
                    showAddStaffDialog = false
                }
            }
        )
    }
}

@Composable
fun StaffRowCard(
    employee: Employee,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onCall: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (employee.isActive) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (employee.isActive) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = employee.name.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = employee.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (!employee.isActive) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.Gray.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    "Inactive",
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "${employee.designation} • ₹${employee.baseSalary.toInt()}/${if (employee.salaryType == Employee.SALARY_TYPE_MONTHLY) "mo" else if (employee.salaryType == Employee.SALARY_TYPE_DAILY) "day" else "hr"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (employee.phone.isNotBlank()) {
                        Text(
                            text = employee.phone,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Row {
                if (employee.phone.isNotBlank()) {
                    IconButton(onClick = onCall) {
                        Icon(Icons.Default.Phone, contentDescription = "Call", tint = Color(0xFF2E7D32))
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditStaffDialog(
    initialEmployee: Employee?,
    onDismiss: () -> Unit,
    onSave: (Employee) -> Unit
) {
    var name by remember { mutableStateOf(initialEmployee?.name ?: "") }
    var phone by remember { mutableStateOf(initialEmployee?.phone ?: "") }
    var designation by remember { mutableStateOf(initialEmployee?.designation ?: "Staff") }
    var salaryType by remember { mutableStateOf(initialEmployee?.salaryType ?: Employee.SALARY_TYPE_MONTHLY) }
    var baseSalary by remember { mutableStateOf(if (initialEmployee != null && initialEmployee.baseSalary > 0) initialEmployee.baseSalary.toInt().toString() else "") }
    var overtimeRate by remember { mutableStateOf(if (initialEmployee != null && initialEmployee.overtimeRatePerHour > 0) initialEmployee.overtimeRatePerHour.toInt().toString() else "") }
    var joiningDate by remember { mutableStateOf(initialEmployee?.joiningDate ?: LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))) }
    var isActive by remember { mutableStateOf(initialEmployee?.isActive ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialEmployee == null) "Add New Employee" else "Edit Employee", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Employee Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = designation,
                        onValueChange = { designation = it },
                        label = { Text("Designation / Role (e.g. Worker, Cook, Driver)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text("Salary Type", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = salaryType == Employee.SALARY_TYPE_MONTHLY,
                            onClick = { salaryType = Employee.SALARY_TYPE_MONTHLY },
                            label = { Text("Monthly") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = salaryType == Employee.SALARY_TYPE_DAILY,
                            onClick = { salaryType = Employee.SALARY_TYPE_DAILY },
                            label = { Text("Daily") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = salaryType == Employee.SALARY_TYPE_HOURLY,
                            onClick = { salaryType = Employee.SALARY_TYPE_HOURLY },
                            label = { Text("Hourly") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = baseSalary,
                        onValueChange = { baseSalary = it },
                        label = { Text(if (salaryType == Employee.SALARY_TYPE_MONTHLY) "Monthly Salary (₹)" else if (salaryType == Employee.SALARY_TYPE_DAILY) "Per Day Rate (₹)" else "Hourly Rate (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = overtimeRate,
                        onValueChange = { overtimeRate = it },
                        label = { Text("Overtime Rate Per Hour (₹) (Optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = joiningDate,
                        onValueChange = { joiningDate = it },
                        label = { Text("Joining Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (initialEmployee != null) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Active Status")
                            Switch(checked = isActive, onCheckedChange = { isActive = it })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val emp = (initialEmployee ?: Employee(name = name)).copy(
                            name = name.trim(),
                            phone = phone.trim(),
                            designation = designation.trim(),
                            salaryType = salaryType,
                            baseSalary = baseSalary.toDoubleOrNull() ?: 0.0,
                            overtimeRatePerHour = overtimeRate.toDoubleOrNull() ?: 0.0,
                            joiningDate = joiningDate.trim(),
                            isActive = isActive
                        )
                        onSave(emp)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

package com.aaryo.selfattendance.ui.employer

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
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aaryo.selfattendance.data.model.Employee
import com.aaryo.selfattendance.data.model.StaffAdvance
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvanceKhataScreen(
    navController: NavController,
    viewModel: EmployerViewModel = viewModel()
) {
    val advances by viewModel.allAdvances.collectAsState()
    val totalPending by viewModel.totalPendingAdvances.collectAsState()
    val employees by viewModel.activeEmployees.collectAsState()

    var showAddAdvanceDialog by remember { mutableStateOf(false) }
    var deletingAdvance by remember { mutableStateOf<StaffAdvance?>(null) }
    val empMap = remember(employees) { employees.associateBy { it.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advance / Loan Khata", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddAdvanceDialog = true },
                icon = { Icon(Icons.Default.AddCard, contentDescription = null) },
                text = { Text("Give Advance") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // Outstanding Summary Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE65100).copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Total Pending Advance",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "₹${totalPending.toInt()}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                    }
                    Icon(
                        Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = Color(0xFFE65100),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Transaction History (${advances.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            if (advances.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("No advance records yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Record advances given to staff to automatically deduct during monthly salary.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(advances, key = { it.id }) { advance ->
                        val emp = empMap[advance.employeeId]
                        AdvanceRowItem(
                            advance = advance,
                            employeeName = emp?.name ?: "Staff #${advance.employeeId}",
                            onToggleStatus = {
                                viewModel.toggleAdvanceStatus(advance.id, !advance.isDeducted, if (!advance.isDeducted) LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM")) else null)
                            },
                            onDelete = {
                                deletingAdvance = advance
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

    if (showAddAdvanceDialog) {
        GiveAdvanceDialog(
            employees = employees,
            onDismiss = { showAddAdvanceDialog = false },
            onSave = { empId, amt, reason, date ->
                viewModel.addAdvance(empId, amt, reason, date) {
                    showAddAdvanceDialog = false
                }
            }
        )
    }

    deletingAdvance?.let { adv ->
        AlertDialog(
            onDismissRequest = { deletingAdvance = null },
            title = { Text("Delete Advance Record?") },
            text = { Text("Are you sure you want to remove this advance record of ₹${adv.amount.toInt()}?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAdvance(adv)
                        deletingAdvance = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingAdvance = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AdvanceRowItem(
    advance: StaffAdvance,
    employeeName: String,
    onToggleStatus: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (advance.isDeducted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = employeeName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${advance.reason} • ${advance.date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (advance.isDeducted) {
                    Text(
                        text = "Settled / Deducted (${advance.deductionMonthYear ?: "Paid"})",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2E7D32)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "₹${advance.amount.toInt()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (advance.isDeducted) Color.Gray else Color(0xFFE65100)
                    )
                    Spacer(Modifier.height(4.dp))
                    FilterChip(
                        selected = advance.isDeducted,
                        onClick = onToggleStatus,
                        label = { Text(if (advance.isDeducted) "Settled" else "Pending", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF2E7D32).copy(alpha = 0.15f),
                            selectedLabelColor = Color(0xFF2E7D32)
                        )
                    )
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Delete Advance",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiveAdvanceDialog(
    employees: List<Employee>,
    onDismiss: () -> Unit,
    onSave: (Long, Double, String, String) -> Unit
) {
    if (employees.isEmpty()) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("No Employees") },
            text = { Text("Please add employees first before recording advance.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
        )
        return
    }

    var selectedEmpId by remember { mutableStateOf(employees.first().id) }
    var amount by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("Salary Advance") }
    var date by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Give Advance / Loan", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("Select Employee", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Column {
                        employees.forEach { emp ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedEmpId = emp.id }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = selectedEmpId == emp.id,
                                    onClick = { selectedEmpId = emp.id }
                                )
                                Text("${emp.name} (${emp.designation})")
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Advance Amount (₹) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason / Note") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull() ?: 0.0
                    if (amt > 0) {
                        onSave(selectedEmpId, amt, reason.trim(), date.trim())
                    }
                }
            ) {
                Text("Save Advance")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

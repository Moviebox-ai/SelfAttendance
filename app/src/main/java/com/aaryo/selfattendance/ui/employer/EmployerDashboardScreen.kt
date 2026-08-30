package com.aaryo.selfattendance.ui.employer

import android.app.DatePickerDialog
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aaryo.selfattendance.billing.BillingManager
import com.aaryo.selfattendance.billing.BusinessTrialManager
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.aaryo.selfattendance.data.model.Employee
import com.aaryo.selfattendance.data.model.StaffAttendance
import com.aaryo.selfattendance.ui.navigation.Routes
import com.google.firebase.auth.FirebaseAuth
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Calendar

private val NavyDark = Color(0xFF0F172A)
private val NavyCard = Color(0xFF1E293B)
private val EmeraldGreen = Color(0xFF059669)
private val AmberGold = Color(0xFFD97706)
private val CrimsonRed = Color(0xFFDC2626)
private val VioletPurple = Color(0xFF7C3AED)
private val RoyalBlue = Color(0xFF2563EB)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployerDashboardScreen(
    navController: NavController,
    viewModel: EmployerViewModel = viewModel()
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }
    val billingManager = remember { BillingManager.getInstance(context) }
    val trialManager = remember { BusinessTrialManager(context) }
    val isBusinessPro by billingManager.isBusinessPro.collectAsState()
    val monthlyPrice by billingManager.businessMonthlyPrice.collectAsState()
    val yearlyPrice by billingManager.businessYearlyPrice.collectAsState()
    var trialSyncVersion by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        trialManager.syncWithServer()
        trialSyncVersion++
    }
    val isTrialActive = remember(isBusinessPro, trialSyncVersion) { trialManager.isTrialActive() }
    val remainingDays = remember(isBusinessPro, trialSyncVersion) { trialManager.getRemainingDays() }

    val businessName by viewModel.businessName.collectAsState()
    val employees by viewModel.activeEmployees.collectAsState()
    val todayDate by viewModel.selectedDate.collectAsState()
    val todayAttendance by viewModel.dateAttendance.collectAsState()
    val totalAdvances by viewModel.totalPendingAdvances.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showEditBusinessDialog by remember { mutableStateOf(false) }
    var tempBusinessName by remember { mutableStateOf(businessName) }
    var showAddAdvanceDialog by remember { mutableStateOf<Employee?>(null) }
    var showOvertimeDialog by remember { mutableStateOf<Employee?>(null) }
    var showBusinessPlansSheet by remember { mutableStateOf(false) }
    var attendanceFilter by remember { mutableStateOf("ALL") }

    if (showBusinessPlansSheet) {
        BusinessPlansBottomSheet(
            onDismiss = { showBusinessPlansSheet = false }
        )
    }

    LaunchedEffect(viewModel.snackBarMessage) {
        viewModel.snackBarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val attendanceMap = remember(todayAttendance) {
        todayAttendance.associateBy { it.employeeId }
    }

    val presentCount = remember(todayAttendance) {
        todayAttendance.count { it.status == StaffAttendance.STATUS_PRESENT || it.status == StaffAttendance.STATUS_OVERTIME }
    }
    val halfDayCount = remember(todayAttendance) {
        todayAttendance.count { it.status == StaffAttendance.STATUS_HALF_DAY }
    }
    val absentCount = remember(todayAttendance) {
        todayAttendance.count { it.status == StaffAttendance.STATUS_ABSENT }
    }
    val markedCount = presentCount + halfDayCount + absentCount
    val pendingCount = (employees.size - markedCount).coerceAtLeast(0)

    val currentLocalDate = remember(todayDate) {
        runCatching { LocalDate.parse(todayDate) }.getOrDefault(LocalDate.now())
    }

    val formattedDate = remember(currentLocalDate) {
        currentLocalDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }

    val isToday = remember(currentLocalDate) {
        currentLocalDate.isEqual(LocalDate.now())
    }

    // Filter employees based on selection
    val filteredEmployees = remember(employees, attendanceMap, attendanceFilter) {
        when (attendanceFilter) {
            "PRESENT" -> employees.filter {
                val s = attendanceMap[it.id]?.status
                s == StaffAttendance.STATUS_PRESENT || s == StaffAttendance.STATUS_OVERTIME
            }
            "HALF_DAY" -> employees.filter { attendanceMap[it.id]?.status == StaffAttendance.STATUS_HALF_DAY }
            "ABSENT" -> employees.filter { attendanceMap[it.id]?.status == StaffAttendance.STATUS_ABSENT }
            "PENDING" -> employees.filter { attendanceMap[it.id] == null }
            else -> employees
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    // Row 1: Brand & Switch Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Business Identity
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(RoyalBlue, Color(0xFF1D4ED8))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Business,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        tempBusinessName = businessName
                                        showEditBusinessDialog = true
                                    }
                                ) {
                                    Text(
                                        text = businessName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit Name",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = "Business Attendance & Payroll",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Right actions: Plan Pill + Security/Biometric + Logout + Switch Mode
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Pro / Trial Pill
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isBusinessPro) EmeraldGreen.copy(alpha = 0.12f)
                                else if (isTrialActive) AmberGold.copy(alpha = 0.12f)
                                else CrimsonRed.copy(alpha = 0.12f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isBusinessPro) EmeraldGreen.copy(alpha = 0.4f)
                                    else if (isTrialActive) AmberGold.copy(alpha = 0.4f)
                                    else CrimsonRed.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.clickable { showBusinessPlansSheet = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isBusinessPro) Icons.Default.Verified else Icons.Default.Star,
                                        contentDescription = null,
                                        tint = if (isBusinessPro) EmeraldGreen else if (isTrialActive) AmberGold else CrimsonRed,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = if (isBusinessPro) "PRO" else if (isTrialActive) "TRIAL (${remainingDays}d)" else "UPGRADE",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isBusinessPro) EmeraldGreen else if (isTrialActive) AmberGold else CrimsonRed
                                    )
                                }
                            }

                            // Business Settings Button
                            IconButton(
                                onClick = { navController.navigate(Routes.EMPLOYER_SETTINGS) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(RoyalBlue.copy(alpha = 0.12f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Business Settings",
                                    tint = RoyalBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(Modifier.height(2.dp))
            }

            // ── 1. Business Pro & Trial Status Hero Card ──────────────────────
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showBusinessPlansSheet = true },
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isBusinessPro) NavyDark else NavyDark
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = if (isBusinessPro) {
                                        listOf(Color(0xFF064E3B), Color(0xFF0F172A))
                                    } else if (isTrialActive) {
                                        listOf(Color(0xFF1E3A8A), Color(0xFF0F172A))
                                    } else {
                                        listOf(Color(0xFF881337), Color(0xFF0F172A))
                                    }
                                )
                            )
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isBusinessPro) EmeraldGreen.copy(alpha = 0.3f)
                                                else if (isTrialActive) AmberGold.copy(alpha = 0.3f)
                                                else CrimsonRed.copy(alpha = 0.3f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isBusinessPro) Icons.Default.Verified
                                            else if (isTrialActive) Icons.Default.WorkspacePremium
                                            else Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = if (isBusinessPro) Color(0xFF34D399)
                                            else if (isTrialActive) Color(0xFFFBBF24)
                                            else Color(0xFFF87171),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = if (isBusinessPro) "Business Pro Enterprise"
                                            else if (isTrialActive) "Business Pro (7-Day Free Trial)"
                                            else "Business Pro Expired",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = if (isBusinessPro) "All advanced business tools active"
                                            else if (isTrialActive) "$remainingDays days remaining in free trial"
                                            else "Renew now for staff & payroll access",
                                            color = Color.White.copy(alpha = 0.75f),
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isBusinessPro) EmeraldGreen else Color(0xFFF59E0B),
                                    modifier = Modifier.clickable { showBusinessPlansSheet = true }
                                ) {
                                    Text(
                                        text = if (isBusinessPro) "Manage Plan" else "View Plans",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isBusinessPro) Color.White else Color(0xFF1E293B),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            // Trial progress bar if in trial
                            if (!isBusinessPro && isTrialActive) {
                                val progress = (remainingDays / 7f).coerceIn(0f, 1f)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(3.dp)),
                                        color = Color(0xFFF59E0B),
                                        trackColor = Color.White.copy(alpha = 0.2f),
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Unlimited Staff & Salary Slips Active",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                        Text(
                                            text = "Plans start @ $monthlyPrice/mo",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFFBBF24)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 2. Date Selector Bar ──────────────────────────────────────────
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = {
                                val prevDate = currentLocalDate.minusDays(1)
                                viewModel.setDate(prevDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                            }
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Day")
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val c = Calendar.getInstance()
                                    c.set(currentLocalDate.year, currentLocalDate.monthValue - 1, currentLocalDate.dayOfMonth)
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            val picked = LocalDate.of(y, m + 1, d)
                                            viewModel.setDate(picked.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                                        },
                                        c.get(Calendar.YEAR),
                                        c.get(Calendar.MONTH),
                                        c.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarToday,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isToday) "Today, $formattedDate" else formattedDate,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = {
                                val nextDate = currentLocalDate.plusDays(1)
                                viewModel.setDate(nextDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                            }
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next Day")
                        }
                    }
                }
            }

            // ── 3. Executive KPI Metric Grid ──────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ModernKpiCard(
                            title = "Total Staff",
                            value = "${employees.size}",
                            subtitle = if (employees.isNotEmpty()) "${activeStaffCount(employees)} Active" else "Add staff",
                            icon = Icons.Default.Groups,
                            accentColor = RoyalBlue,
                            modifier = Modifier.weight(1f),
                            onClick = { navController.navigate(Routes.STAFF_LIST) }
                        )
                        ModernKpiCard(
                            title = "Present Today",
                            value = "$presentCount",
                            subtitle = if (halfDayCount > 0) "+$halfDayCount Half Day" else "${((presentCount.toFloat() / employees.size.coerceAtLeast(1)) * 100).toInt()}% marked",
                            icon = Icons.Default.CheckCircle,
                            accentColor = EmeraldGreen,
                            modifier = Modifier.weight(1f),
                            onClick = { attendanceFilter = "PRESENT" }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ModernKpiCard(
                            title = "Absent Today",
                            value = "$absentCount",
                            subtitle = if (pendingCount > 0) "$pendingCount Unmarked" else "All marked",
                            icon = Icons.Default.Cancel,
                            accentColor = CrimsonRed,
                            modifier = Modifier.weight(1f),
                            onClick = { attendanceFilter = "ABSENT" }
                        )
                        ModernKpiCard(
                            title = "Advance Khata",
                            value = "₹${totalAdvances.toInt()}",
                            subtitle = "Pending recovery",
                            icon = Icons.Default.AccountBalanceWallet,
                            accentColor = AmberGold,
                            modifier = Modifier.weight(1f),
                            onClick = { navController.navigate(Routes.ADVANCE_KHATA) }
                        )
                    }
                }
            }

            // ── 4. Quick Actions Hub (4 Main Pillars) ─────────────────────────
            item {
                Column {
                    Text(
                        text = "Business Modules",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BusinessActionCard(
                            title = "Attendance",
                            subtitle = "Register",
                            icon = Icons.Default.HowToReg,
                            color = RoyalBlue,
                            onClick = { navController.navigate(Routes.STAFF_ATTENDANCE) },
                            modifier = Modifier.weight(1f)
                        )
                        BusinessActionCard(
                            title = "Staff List",
                            subtitle = "Profiles",
                            icon = Icons.Default.PersonAdd,
                            color = EmeraldGreen,
                            onClick = { navController.navigate(Routes.STAFF_LIST) },
                            modifier = Modifier.weight(1f)
                        )
                        BusinessActionCard(
                            title = "Advance",
                            subtitle = "Khata",
                            icon = Icons.Default.MonetizationOn,
                            color = AmberGold,
                            onClick = { navController.navigate(Routes.ADVANCE_KHATA) },
                            modifier = Modifier.weight(1f)
                        )
                        BusinessActionCard(
                            title = "Payroll",
                            subtitle = "PDF Slips",
                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                            color = VioletPurple,
                            onClick = { navController.navigate(Routes.SALARY_PAYROLL) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── 5. Live Attendance Roster Header & Filter Chips ───────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Daily Attendance Roster",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${filteredEmployees.size} staff members • Tap status to mark",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (employees.isNotEmpty()) {
                            Button(
                                onClick = { viewModel.markAllPresent() },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                            ) {
                                Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("All Present", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Filter row
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            AttendanceFilterChip(
                                label = "All (${employees.size})",
                                isSelected = attendanceFilter == "ALL",
                                onClick = { attendanceFilter = "ALL" }
                            )
                        }
                        item {
                            AttendanceFilterChip(
                                label = "Present ($presentCount)",
                                isSelected = attendanceFilter == "PRESENT",
                                onClick = { attendanceFilter = "PRESENT" }
                            )
                        }
                        item {
                            AttendanceFilterChip(
                                label = "Half Day ($halfDayCount)",
                                isSelected = attendanceFilter == "HALF_DAY",
                                onClick = { attendanceFilter = "HALF_DAY" }
                            )
                        }
                        item {
                            AttendanceFilterChip(
                                label = "Absent ($absentCount)",
                                isSelected = attendanceFilter == "ABSENT",
                                onClick = { attendanceFilter = "ABSENT" }
                            )
                        }
                        item {
                            AttendanceFilterChip(
                                label = "Unmarked ($pendingCount)",
                                isSelected = attendanceFilter == "PENDING",
                                onClick = { attendanceFilter = "PENDING" }
                            )
                        }
                    }
                }
            }

            // ── 6. Employee List / Attendance Cards ────────────────────────────
            if (employees.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(RoyalBlue.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = RoyalBlue
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "No staff added yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Add your workers, staff or employees to start recording attendance, overtime & advance salary khata.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { navController.navigate(Routes.STAFF_LIST) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Add First Staff Member")
                            }
                        }
                    }
                }
            } else if (filteredEmployees.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No staff in '$attendanceFilter' category today.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredEmployees, key = { it.id }) { employee ->
                    val currentAttendance = attendanceMap[employee.id]
                    ModernStaffAttendanceCard(
                        employee = employee,
                        attendance = currentAttendance,
                        onMarkPresent = { viewModel.markAttendance(employee.id, StaffAttendance.STATUS_PRESENT) },
                        onMarkHalfDay = { viewModel.markAttendance(employee.id, StaffAttendance.STATUS_HALF_DAY) },
                        onMarkAbsent = { viewModel.markAttendance(employee.id, StaffAttendance.STATUS_ABSENT) },
                        onOvertimeClick = { showOvertimeDialog = employee },
                        onAdvanceClick = { showAddAdvanceDialog = employee },
                        onCardClick = { navController.navigate("staff_detail/${employee.id}") }
                    )
                }
            }

            item {
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    // ── Edit Business Name Dialog ─────────────────────────────────────────────
    if (showEditBusinessDialog) {
        AlertDialog(
            onDismissRequest = { showEditBusinessDialog = false },
            title = {
                Text(
                    text = "Business / Company Name",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = tempBusinessName,
                    onValueChange = { tempBusinessName = it },
                    label = { Text("Company / Store Name") },
                    placeholder = { Text("e.g. Sharma Traders, Aaryo Corp") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempBusinessName.isNotBlank()) {
                            viewModel.updateBusinessName(tempBusinessName.trim())
                        }
                        showEditBusinessDialog = false
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditBusinessDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Overtime Dialog ───────────────────────────────────────────────────────
    showOvertimeDialog?.let { emp ->
        var otHours by remember { mutableStateOf("2") }
        AlertDialog(
            onDismissRequest = { showOvertimeDialog = null },
            title = { Text("Add Overtime: ${emp.name}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter overtime hours worked on $formattedDate:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = otHours,
                        onValueChange = { otHours = it },
                        label = { Text("Overtime Hours") },
                        placeholder = { Text("e.g. 1.5, 2, 3") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val hours = otHours.toDoubleOrNull() ?: 0.0
                        viewModel.markAttendance(emp.id, StaffAttendance.STATUS_OVERTIME, overtimeHours = hours)
                        showOvertimeDialog = null
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VioletPurple)
                ) {
                    Text("Save Overtime")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOvertimeDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Quick Advance Dialog ──────────────────────────────────────────────────
    showAddAdvanceDialog?.let { emp ->
        var advAmount by remember { mutableStateOf("") }
        var advReason by remember { mutableStateOf("Salary Advance") }
        AlertDialog(
            onDismissRequest = { showAddAdvanceDialog = null },
            title = { Text("Give Advance: ${emp.name}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = advAmount,
                        onValueChange = { advAmount = it },
                        label = { Text("Amount (₹)") },
                        placeholder = { Text("e.g. 2000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = advReason,
                        onValueChange = { advReason = it },
                        label = { Text("Reason / Note") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = advAmount.toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            viewModel.addAdvance(emp.id, amt, advReason, todayDate) {
                                showAddAdvanceDialog = null
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGold)
                ) {
                    Text("Record Advance")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAdvanceDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun activeStaffCount(employees: List<Employee>): Int =
    employees.count { it.isActive }

// ── Components ────────────────────────────────────────────────────────────────

@Composable
private fun ModernKpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = accentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun BusinessActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AttendanceFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun ModernStaffAttendanceCard(
    employee: Employee,
    attendance: StaffAttendance?,
    onMarkPresent: () -> Unit,
    onMarkHalfDay: () -> Unit,
    onMarkAbsent: () -> Unit,
    onOvertimeClick: () -> Unit,
    onAdvanceClick: () -> Unit,
    onCardClick: () -> Unit
) {
    val status = attendance?.status

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(RoyalBlue, Color(0xFF1D4ED8))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = employee.name.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                    }

                    Column {
                        Text(
                            text = employee.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = employee.designation.ifBlank { "Staff" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "•",
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "₹${employee.baseSalary.toInt()}/${if (employee.salaryType == Employee.SALARY_TYPE_MONTHLY) "mo" else if (employee.salaryType == Employee.SALARY_TYPE_DAILY) "day" else "hr"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Quick Advance Icon Button
                IconButton(
                    onClick = onAdvanceClick,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(AmberGold.copy(alpha = 0.1f))
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCard,
                        contentDescription = "Give Advance",
                        tint = AmberGold,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Attendance Action Status Selector Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ExecutiveStatusPill(
                    label = "Present",
                    isSelected = status == StaffAttendance.STATUS_PRESENT || status == StaffAttendance.STATUS_OVERTIME,
                    activeColor = EmeraldGreen,
                    onClick = onMarkPresent,
                    modifier = Modifier.weight(1.1f)
                )
                ExecutiveStatusPill(
                    label = "Half Day",
                    isSelected = status == StaffAttendance.STATUS_HALF_DAY,
                    activeColor = AmberGold,
                    onClick = onMarkHalfDay,
                    modifier = Modifier.weight(1.1f)
                )
                ExecutiveStatusPill(
                    label = "Absent",
                    isSelected = status == StaffAttendance.STATUS_ABSENT,
                    activeColor = CrimsonRed,
                    onClick = onMarkAbsent,
                    modifier = Modifier.weight(1f)
                )
                ExecutiveStatusPill(
                    label = if ((attendance?.overtimeHours ?: 0.0) > 0.0) "+${attendance?.overtimeHours}h OT" else "+OT",
                    isSelected = (attendance?.overtimeHours ?: 0.0) > 0.0,
                    activeColor = VioletPurple,
                    onClick = onOvertimeClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ExecutiveStatusPill(
    label: String,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(38.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) activeColor else activeColor.copy(alpha = 0.08f),
        border = BorderStroke(
            1.dp,
            if (isSelected) activeColor else activeColor.copy(alpha = 0.25f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else activeColor
                )
            }
        }
    }
}

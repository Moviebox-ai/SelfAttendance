package com.aaryo.selfattendance.ui.calendar

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.aaryo.selfattendance.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.aaryo.selfattendance.utils.CurrencyManager
import com.aaryo.selfattendance.utils.HolidayManager
import com.aaryo.selfattendance.data.model.Attendance
import com.aaryo.selfattendance.domain.SalaryCalculator
import com.aaryo.selfattendance.ads.AdsController
import com.aaryo.selfattendance.data.remote.RemoteConfigManager
import com.aaryo.selfattendance.ui.dashboard.DashboardViewModel
import com.aaryo.selfattendance.ui.rewards.CoinFlipDialog
import com.aaryo.selfattendance.ui.shared.MonthViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.YearMonth

@OptIn(
    ExperimentalAnimationApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun CalendarScreen(navController: NavController) {

    val calendarViewModel: CalendarViewModel = viewModel()
    val dashboardViewModel: DashboardViewModel = viewModel()
    val monthViewModel: MonthViewModel = viewModel()

    val attendanceMap by calendarViewModel.attendanceMap.collectAsState()
    val currentMonth by monthViewModel.selectedMonth.collectAsState()
    val dashboardState by dashboardViewModel.state.collectAsState()

    val context = LocalContext.current
    // locale-wrapped ContextWrapper, not the Activity instance. Use findActivity() to
    // walk the ContextWrapper chain (see LocaleManager.kt for details).
    val activity = with(com.aaryo.selfattendance.utils.LocaleManager) { context.findActivity() }
    val currencySymbol = remember(PreferencesManager(context).selectedCurrency) { CurrencyManager.getSymbol(PreferencesManager(context).selectedCurrency) }

    val today = LocalDate.now()

    var showDialog by remember { mutableStateOf(false) }
    var selectedAttendance by remember { mutableStateOf<Attendance?>(null) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteDate by remember { mutableStateOf<String?>(null) }

    // ── Attendance Present Coins ──────────────────────────────────────────────
    var attendanceCoinsEarned by remember { mutableIntStateOf(0) }
    var showAttendanceCoinDialog by remember { mutableStateOf(false) }

    var showHolidaySheet by remember { mutableStateOf(false) }
    val holidaysInMonth = remember(currentMonth) {
        HolidayManager.getHolidaysForMonth(currentMonth.year, currentMonth.monthValue)
    }

    LaunchedEffect(currentMonth) {
        dashboardViewModel.setMonth(currentMonth)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val saveError by calendarViewModel.saveError.collectAsState()
    LaunchedEffect(saveError) {
        val msg = saveError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Long)
        calendarViewModel.clearSaveError()
    }

    // Scroll state for the outer Column — enables full monthly summary to be
    // visible by scrolling up on small screens.
    val scrollState = rememberScrollState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { scaffoldPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(scaffoldPadding)
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {

            // HEADER
            Card(shape = RoundedCornerShape(20.dp)) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        IconButton(
                            onClick = {
                                monthViewModel.setMonth(
                                    currentMonth.minusMonths(1)
                                )
                            }
                        ) {
                            Icon(Icons.Rounded.ChevronLeft, null)
                        }

                        Text(
                            text = "${currentMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${currentMonth.year}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(
                            onClick = {
                                monthViewModel.setMonth(
                                    currentMonth.plusMonths(1)
                                )
                            }
                        ) {
                            Icon(Icons.Rounded.ChevronRight, null)
                        }
                    }

                    if (holidaysInMonth.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        AssistChip(
                            onClick = { showHolidaySheet = true },
                            label = {
                                Text(
                                    text = "🎉 ${holidaysInMonth.size} ${stringResource(R.string.calendar_view_holidays)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF651FFF)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color(0xFF7C4DFF).copy(alpha = 0.12f)
                            ),
                            border = AssistChipDefaults.assistChipBorder(
                                enabled = true,
                                borderColor = Color(0xFF7C4DFF).copy(alpha = 0.35f)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val weekDays = listOf("S","M","T","W","T","F","S")

            Row(modifier = Modifier.fillMaxWidth()) {
                weekDays.forEach {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(it, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            AnimatedContent(
                targetState = currentMonth,
                label = "monthAnimation",
                transitionSpec = {
                    slideInHorizontally { it } togetherWith
                            slideOutHorizontally { -it }
                }
            ) { month ->

                val firstDay = month.atDay(1)
                val startOffset = firstDay.dayOfWeek.value % 7
                val totalDays = month.lengthOfMonth()

                val cells: List<Int?> =
                    List(startOffset) { null } + List(totalDays) { it + 1 }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    cells.chunked(7).forEach { week ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            week.forEach { day ->
                                if (day == null) {
                                    Spacer(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                    )
                                } else {
                                    val dateObj = month.atDay(day)
                                    val date = dateObj.toString()

                                    val attendance = attendanceMap[date]
                                    val isFuture = dateObj.isAfter(today)
                                    val isToday = dateObj == today
                                    val holidayInfo = HolidayManager.getHoliday(date)

                                    val cellColor = when (attendance?.status) {
                                        "PRESENT"            -> Color(0xFF00C853)   // EmeraldGreen
                                        "HALF", "HALF_DAY"   -> Color(0xFFFFB300)   // Amber
                                        "HOLIDAY"            -> Color(0xFF7C4DFF)   // Vivid Purple
                                        "ABSENT"             -> Color(0xFFE53935)   // CoralRed
                                        else                 -> if (holidayInfo != null) Color(0xFF7C4DFF).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
                                    }

                                    val textColor = when {
                                        attendance != null  -> Color.White
                                        holidayInfo != null -> Color(0xFF651FFF)
                                        else                -> MaterialTheme.colorScheme.onSurface
                                    }

                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(cellColor),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .alpha(if (isFuture) 0.4f else 1f)
                                            .border(
                                                if (isToday) 2.dp else if (holidayInfo != null && attendance == null) 1.5.dp else 1.dp,
                                                if (isToday) MaterialTheme.colorScheme.primary else if (holidayInfo != null && attendance == null) Color(0xFF7C4DFF).copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .combinedClickable(
                                                enabled = !isFuture,
                                                onClick = {
                                                    calendarViewModel.selectDate(date)
                                                    selectedAttendance = attendance
                                                    showDialog = true
                                                },
                                                onLongClick = {
                                                    if (attendance != null) {
                                                        deleteDate = date
                                                        showDeleteDialog = true
                                                    }
                                                }
                                            )
                                    ) {

                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {

                                            Text(
                                                text = day.toString(),
                                                fontWeight = FontWeight.Bold,
                                                color = textColor
                                            )

                                            val overtime = attendance?.overtimeHours ?: 0.0

                                            if (overtime > 0) {
                                                Text(
                                                    text = "${overtime.toInt()}h",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF1565C0),
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(2.dp)
                                                )
                                            } else if (holidayInfo != null && attendance == null) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(5.dp)
                                                        .background(Color(0xFF7C4DFF), CircleShape)
                                                        .align(Alignment.BottomCenter)
                                                        .padding(bottom = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            // Pad the last (possibly short) row so the cells keep equal width
                            repeat(7 - week.size) {
                                Box(modifier = Modifier.weight(1f).height(44.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (currentMonth != YearMonth.now()) {

                val logs = attendanceMap.values.filter {
                    try {
                        YearMonth.from(LocalDate.parse(it.date)) == currentMonth
                    } catch (e: Exception) {
                        false
                    }
                }

                val present = logs.count { it.status == "PRESENT" }
                val half = logs.count { it.status == "HALF" || it.status == "HALF_DAY" }
                val holiday = logs.count { it.status == "HOLIDAY" }
                val absent = logs.count { it.status == "ABSENT" }
                val overtime = logs.sumOf { it.overtimeHours }

                val profile = dashboardState.profile

                val salary = SalaryCalculator.calculate(profile, logs)

                val perDay  = SalaryCalculator.perDaySalary(profile.monthlySalary, profile.workingDays)
                val perHour = SalaryCalculator.perHourSalary(
                    profile.monthlySalary,
                    profile.standardHours,
                    profile.workingDays
                )

                MonthlySummaryCard(
                    present        = present,
                    half           = half,
                    holiday        = holiday,
                    absent         = absent,
                    overtime       = overtime,
                    salary         = salary,
                    perDay         = perDay,
                    perHour        = perHour,
                    currencySymbol = currencySymbol
                )

                Spacer(modifier = Modifier.height(20.dp))
            }

            // FIX: Remote Config guard add kiya — ads_enabled/show_native_ad ko respect karta hai
            if (RemoteConfigManager.getInstance().showNativeAd()) {
                AdsController.NativeAdView()
            }
        }
    }

    // ---------------- DIALOG ----------------

    if (showDialog) {

        AttendanceDialog(
            date = calendarViewModel.selectedDate,
            existingAttendance = selectedAttendance,
            onDismiss = { showDialog = false },
            onSave = { status, overtime ->

                val prefs    = PreferencesManager(context)
                val todayStr = LocalDate.now().toString()
                val targetDate = calendarViewModel.selectedDate ?: selectedAttendance?.date
                calendarViewModel.saveAttendance(
                    status       = status,
                    overtime     = overtime,
                    dateOverride = targetDate,
                    activity     = activity,
                    onTodayMarked = {
                        prefs.lastMarkedDate = todayStr
                        // Award random coins (1-50) for marking PRESENT on today, once per day
                        if (status == "PRESENT" && prefs.lastAttendanceCoinDate != todayStr) {
                            val coins = (1..50).random()
                            prefs.lastAttendanceCoinDate = todayStr
                            prefs.coinBalance            = prefs.coinBalance + coins
                            prefs.totalCoinsEarned       = prefs.totalCoinsEarned + coins
                            attendanceCoinsEarned        = coins
                            showAttendanceCoinDialog     = true
                            // Sync to Firestore in background.
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                                    FirebaseFirestore.getInstance()
                                        .collection("users").document(uid)
                                        .set(mapOf("rewards" to mapOf(
                                            "coinBalance"            to FieldValue.increment(coins.toLong()),
                                            "totalCoinsEarned"       to FieldValue.increment(coins.toLong()),
                                            "lastAttendanceCoinDate" to prefs.lastAttendanceCoinDate,
                                            "updatedAt"              to System.currentTimeMillis()
                                        )), SetOptions.merge()).await()
                                } catch (e: Exception) {
                                    FirebaseCrashlytics.getInstance().recordException(e)
                                }
                            }
                        }
                    }
                )

                showDialog = false
            }
        )
    }

    // ---------------- HOLIDAYS LIST DIALOG ----------------

    if (showHolidaySheet) {
        AlertDialog(
            onDismissRequest = { showHolidaySheet = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Celebration,
                        contentDescription = "Holidays",
                        tint = Color(0xFF7C4DFF)
                    )
                    Text(
                        text = "${currentMonth.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${stringResource(R.string.calendar_view_holidays)}"
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (holidaysInMonth.isEmpty()) {
                        Text("No official public holidays in this month.")
                    } else {
                        holidaysInMonth.forEach { holiday ->
                            val parsedDate = runCatching { LocalDate.parse(holiday.date) }.getOrNull()
                            val dayNumber = parsedDate?.dayOfMonth?.toString() ?: ""
                            val dayOfWeek = parsedDate?.dayOfWeek?.name?.take(3) ?: ""

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF7C4DFF).copy(alpha = 0.08f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF7C4DFF).copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(Color(0xFF7C4DFF), RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = dayNumber,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = dayOfWeek,
                                                color = Color.White.copy(alpha = 0.8f),
                                                fontSize = 9.sp
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = holiday.nameEn,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = holiday.nameHi,
                                            fontSize = 12.sp,
                                            color = Color(0xFF7C4DFF)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHolidaySheet = false }) {
                    Text("OK")
                }
            }
        )
    }

    // ---------------- DELETE DIALOG ----------------

    if (showDeleteDialog) {

        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.calendar_delete_title)) },
            text = { Text(stringResource(R.string.calendar_delete_body, deleteDate ?: "")) },

            confirmButton = {
                TextButton(
                    onClick = {
                        deleteDate?.let {
                            calendarViewModel.deleteAttendance(it)
                        }
                        showDeleteDialog = false
                    }
                ) {
                    Text(stringResource(R.string.calendar_delete_confirm))
                }
            },

            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false }
                ) {
                    Text(stringResource(R.string.calendar_cancel))
                }
            }
        )
    }

    // ---------------- ATTENDANCE COIN DIALOG ----------------

    if (showAttendanceCoinDialog && attendanceCoinsEarned > 0) {
        CoinFlipDialog(
            coinsWon = attendanceCoinsEarned,
            label    = "Aaj Present mark karne ka bonus!",
            onClaim  = { showAttendanceCoinDialog = false }
        )
    }
}

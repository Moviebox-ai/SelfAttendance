package com.aaryo.selfattendance.ui.calendar

import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aaryo.selfattendance.ads.AdManager
import com.aaryo.selfattendance.ads.NativeAdCard
import com.aaryo.selfattendance.ads.SmartAdController
import com.aaryo.selfattendance.data.model.Attendance
import com.aaryo.selfattendance.data.remote.RemoteConfigManager
import com.aaryo.selfattendance.ads.BannerAd
import com.aaryo.selfattendance.domain.SalaryCalculator
import com.aaryo.selfattendance.ui.dashboard.DashboardViewModel
import com.aaryo.selfattendance.ui.shared.MonthViewModel
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

    val context = LocalContext.current
    val activity = context as? Activity

    val today = LocalDate.now()

    var showDialog by remember { mutableStateOf(false) }
    var selectedAttendance by remember { mutableStateOf<Attendance?>(null) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteDate by remember { mutableStateOf<String?>(null) }

    val remoteConfig = remember { RemoteConfigManager.getInstance() }

    LaunchedEffect(currentMonth) {
        dashboardViewModel.setMonth(currentMonth)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (remoteConfig.showBannerAd()) {
                BannerAd()
            }
        },
    ) { scaffoldPadding ->

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(scaffoldPadding)
            .padding(16.dp)
    ) {

        // HEADER

        Card(shape = RoundedCornerShape(20.dp)) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
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

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                items(startOffset) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    )
                }

                items(totalDays) { index ->

                    val day = index + 1
                    val dateObj = month.atDay(day)
                    val date = dateObj.toString()

                    val attendance = attendanceMap[date]
                    val isFuture = dateObj.isAfter(today)
                    val isToday = dateObj == today

                    val cellColor = when (attendance?.status) {
                        "PRESENT" -> Color(0xFF22C55E)
                        "HALF","HALF_DAY" -> Color(0xFFFACC15)
                        "ABSENT" -> Color(0xFFEF4444)
                        else -> MaterialTheme.colorScheme.surface
                    }

                    val textColor =
                        if (attendance == null) MaterialTheme.colorScheme.onSurface
                        else Color.White

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(cellColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .alpha(if (isFuture) 0.4f else 1f)
                            .border(
                                if (isToday) 2.dp else 1.dp,
                                if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
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

                            val overtime =
                                attendance?.overtimeHours ?: 0.0

                            if (overtime > 0) {

                                Text(
                                    text = "${overtime.toInt()}h",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Blue,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(2.dp)
                                )
                            }
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
            val absent = logs.count { it.status == "ABSENT" }
            val overtime = logs.sumOf { it.overtimeHours }

            val profile = dashboardViewModel.state.value.profile

            val salary = SalaryCalculator.calculate(profile, logs)

            val perDay  = SalaryCalculator.perDaySalary(profile.monthlySalary)
            val perHour = SalaryCalculator.perHourSalary(
                profile.monthlySalary,
                profile.standardHours
            )

            MonthlySummaryCard(
                present,
                half,
                absent,
                overtime,
                salary,
                perDay,
                perHour
            )

            Spacer(modifier = Modifier.height(20.dp))
        }

        if (remoteConfig.showNativeAd()) {

            NativeAdCard()
        }
    } // end Column

    } // end Scaffold

    if (showDialog) {

        AttendanceDialog(
            existingAttendance = selectedAttendance,
            onDismiss = { showDialog = false },
            onSave = { status, overtime ->

                calendarViewModel.saveAttendance(
                    status = status,
                    overtime = overtime,
                    onTodayMarked = {
                        com.aaryo.selfattendance.data.local.PreferencesManager(context)
                            .lastMarkedDate = java.time.LocalDate.now().toString()
                    }
                )

                showDialog = false

                activity?.let {
                    // ✅ CHANGE: Overtime save hone par seedha interstitial dikhao
                    // Normal save par SmartAdController cooldown check karo
                    if (overtime > 0.0) {
                        AdManager.showAd(it)
                    } else if (SmartAdController.shouldShowAd()) {
                        AdManager.showAd(it)
                    }
                }
            }
        )
    }

    if (showDeleteDialog) {

        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Remove Attendance") },
            text = { Text("Delete attendance for ${deleteDate ?: ""}?") },

            confirmButton = {

                TextButton(
                    onClick = {

                        deleteDate?.let {

                            calendarViewModel.deleteAttendance(it)
                        }

                        showDeleteDialog = false
                    }
                ) {

                    Text("Delete")
                }
            },

            dismissButton = {

                TextButton(
                    onClick = { showDeleteDialog = false }
                ) {

                    Text("Cancel")
                }
            }
        )
    }
}

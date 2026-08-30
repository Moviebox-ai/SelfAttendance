package com.aaryo.selfattendance.ui.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import com.aaryo.selfattendance.R
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaryo.selfattendance.data.model.Attendance
import com.aaryo.selfattendance.utils.HolidayManager

@Composable
fun AttendanceDialog(
    date: String? = null,
    existingAttendance: Attendance? = null,
    onDismiss: () -> Unit,
    onSave: (String, Double) -> Unit
) {

    val holidayInfo = remember(date, existingAttendance) {
        val targetDate = date ?: existingAttendance?.date
        targetDate?.let { HolidayManager.getHoliday(it) }
    }

    var overtimeText by remember {
        mutableStateOf(
            existingAttendance?.overtimeHours
                ?.takeIf { it > 0 }
                ?.toString() ?: ""
        )
    }

    // AUTO-SELECT FIX: overtime type karte hi selectedStatus "PRESENT" ho jata hai
    var selectedStatus by remember {
        mutableStateOf(
            // If editing an existing record, pre-fill the previous status
            existingAttendance?.status?.let {
                when (it) {
                    "HALF", "HALF_DAY" -> "HALF"
                    "ABSENT"           -> "ABSENT"
                    "HOLIDAY"          -> "HOLIDAY"
                    else               -> null   // PRESENT shown as unselected until overtime is entered
                }
            } ?: if (holidayInfo != null && existingAttendance == null) "HOLIDAY" else null
        )
    }

    val overtimeValue = overtimeText.toDoubleOrNull() ?: 0.0
    val showSaveButton = overtimeText.isNotBlank() && overtimeValue > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = {
            Text(
                text = if (existingAttendance != null)
                    stringResource(R.string.calendar_edit_attendance)
                else
                    stringResource(R.string.calendar_mark_attendance)
            )
        },
        text = {

            Column(modifier = Modifier.fillMaxWidth()) {

                // ── Public Holiday Banner (if date is a recognized holiday) ────
                if (holidayInfo != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF7C4DFF).copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF7C4DFF).copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Celebration,
                                contentDescription = "Holiday",
                                tint = Color(0xFF7C4DFF),
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = holidayInfo.nameEn,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF651FFF)
                                )
                                Text(
                                    text = "${holidayInfo.nameHi} • ${stringResource(R.string.calendar_public_holiday)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // ── Present Button ────────────────────────────────────────────
                Button(
                    onClick = {
                        if (showSaveButton) {
                            selectedStatus = "PRESENT"
                        } else {
                            onSave("PRESENT", 0.0)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedStatus == "PRESENT")
                            Color(0xFF00963D)   // selected — darker
                        else
                            Color(0xFF00C853)
                    )
                ) {
                    Text(
                        if (selectedStatus == "PRESENT") stringResource(R.string.calendar_present_selected)
                        else stringResource(R.string.calendar_present_btn)
                    )
                }

                Spacer(Modifier.height(8.dp))

                // ── Half Day Button ───────────────────────────────────────────
                Button(
                    onClick = {
                        if (showSaveButton) {
                            selectedStatus = "HALF"
                        } else {
                            onSave("HALF", 0.0)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedStatus == "HALF")
                            Color(0xFFE6A100)
                        else
                            Color(0xFFFFB300)
                    )
                ) {
                    Text(
                        if (selectedStatus == "HALF") stringResource(R.string.calendar_half_day_selected)
                        else stringResource(R.string.calendar_half_day_btn)
                    )
                }

                Spacer(Modifier.height(8.dp))

                // ── Holiday Button ────────────────────────────────────────────
                Button(
                    onClick = {
                        if (showSaveButton) {
                            selectedStatus = "HOLIDAY"
                        } else {
                            onSave("HOLIDAY", 0.0)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedStatus == "HOLIDAY")
                            Color(0xFF5E35B1)   // selected — darker purple
                        else
                            Color(0xFF7C4DFF)   // vivid purple
                    )
                ) {
                    Text(
                        if (selectedStatus == "HOLIDAY") stringResource(R.string.calendar_holiday_selected)
                        else stringResource(R.string.calendar_holiday_btn)
                    )
                }

                Spacer(Modifier.height(8.dp))

                // ── Absent Button ─────────────────────────────────────────────
                Button(
                    onClick = {
                        if (showSaveButton) {
                            selectedStatus = "ABSENT"
                        } else {
                            onSave("ABSENT", 0.0)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedStatus == "ABSENT")
                            Color(0xFFCC2A2A)
                        else
                            Color(0xFFE53935)
                    )
                ) {
                    Text(
                        if (selectedStatus == "ABSENT") stringResource(R.string.calendar_absent_selected)
                        else stringResource(R.string.calendar_absent_btn)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // ── Overtime Input ────────────────────────────────────────────
                OutlinedTextField(
                    value = overtimeText,
                    onValueChange = { input ->
                        overtimeText = input.filter { ch -> ch.isDigit() || ch == '.' }

                        val numVal = input.toDoubleOrNull() ?: 0.0
                        when {
                            input.isNotBlank() && numVal > 0.0 && selectedStatus == null -> {
                                selectedStatus = "PRESENT"
                            }
                            input.isBlank() -> selectedStatus = null
                        }
                    },
                    label = { Text(stringResource(R.string.calendar_overtime_hours)) },
                    placeholder = { Text(stringResource(R.string.calendar_overtime_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        if (overtimeValue > 0.0 && selectedStatus == "PRESENT") {
                            Text(
                                stringResource(R.string.calendar_overtime_autoselect),
                                color = Color(0xFF00963D)
                            )
                        }
                    }
                )

                AnimatedVisibility(
                    visible = showSaveButton,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    Column {
                        Spacer(Modifier.height(10.dp))

                        Button(
                            onClick = {
                                val status = selectedStatus ?: "PRESENT"
                                val effectiveOvertime = if (status == "PRESENT") overtimeValue else 0.0
                                onSave(status, effectiveOvertime)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            enabled = selectedStatus != null
                        ) {
                            val label = when (selectedStatus) {
                                "PRESENT" -> stringResource(R.string.calendar_save_present_ot, "%.1f".format(overtimeValue))
                                "HALF"    -> stringResource(R.string.calendar_save_half_ot)
                                "HOLIDAY" -> stringResource(R.string.calendar_save_holiday_ot)
                                "ABSENT"  -> stringResource(R.string.calendar_save_absent_ot)
                                else      -> stringResource(R.string.calendar_select_status_first)
                            }
                            Text(label)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.calendar_cancel))
                }
            }
        }
    )
}

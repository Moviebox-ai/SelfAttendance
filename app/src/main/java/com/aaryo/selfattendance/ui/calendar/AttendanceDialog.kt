package com.aaryo.selfattendance.ui.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import com.aaryo.selfattendance.R
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aaryo.selfattendance.data.model.Attendance

@Composable
fun AttendanceDialog(
    existingAttendance: Attendance? = null,
    onDismiss: () -> Unit,
    onSave: (String, Double) -> Unit
) {

    var overtimeText by remember {
        mutableStateOf(
            existingAttendance?.overtimeHours
                ?.takeIf { it > 0 }
                ?.toString() ?: ""
        )
    }

    var selectedStatus by remember { mutableStateOf<String?>(null) }

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

                // ── Present Button ────────────────────────────────────────────
                Button(
                    onClick = {
                        if (showSaveButton) {
                            // Overtime hai — status select karo, Save se save hoga
                            selectedStatus = "PRESENT"
                        } else {
                            // Overtime nahi — seedha save
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
                        if (selectedStatus == "PRESENT") "✓ Present selected"
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
                        if (selectedStatus == "HALF") "✓ Half Day selected"
                        else stringResource(R.string.calendar_half_day_btn)
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
                        if (selectedStatus == "ABSENT") "✓ Absent selected"
                        else stringResource(R.string.calendar_absent_btn)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // ── Overtime Input ────────────────────────────────────────────
                OutlinedTextField(
                    value = overtimeText,
                    onValueChange = { input ->
                        overtimeText = input.filter { ch -> ch.isDigit() || ch == '.' }
                        // Overtime clear hone par selection reset karo
                        if (input.isBlank()) selectedStatus = null
                    },
                    label = { Text("Overtime Hours (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
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
                                onSave(status, overtimeValue)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            enabled = selectedStatus != null
                        ) {
                            val label = when (selectedStatus) {
                                "PRESENT" -> "Save — Present + ${overtimeValue}h OT"
                                "HALF"    -> "Save — Half Day + ${overtimeValue}h OT"
                                "ABSENT"  -> "Save — Absent + ${overtimeValue}h OT"
                                else      -> "Pehle status select karo ↑"
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

package com.aaryo.selfattendance.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun MonthlySummaryCard(
    present: Int,
    half: Int,
    absent: Int,
    overtime: Double,
    salary: Double,
    perDay: Double = 0.0,
    perHour: Double = 0.0
) {

    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
    ) {

        Column(
            modifier = Modifier.padding(20.dp)
        ) {

            Text(
                text = "Monthly Summary",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Attendance row

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                SummaryItem(
                    title = "Present",
                    value = present.toString(),
                    color = Color(0xFF22C55E)
                )

                SummaryItem(
                    title = "Half",
                    value = half.toString(),
                    color = Color(0xFFFACC15)
                )

                SummaryItem(
                    title = "Absent",
                    value = absent.toString(),
                    color = Color(0xFFEF4444)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Salary + Overtime row

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                SummaryItem(
                    title = "Overtime",
                    value = "${overtime.toInt()}h",
                    color = Color(0xFF3B82F6)
                )

                SummaryItem(
                    title = "Salary",
                    value = "₹ ${salary.toInt()}",
                    color = Color(0xFF6366F1)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Per Day + Per Hour row (30-days formula)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                SummaryItem(
                    title = "Per Day",
                    value = "₹ ${"%.0f".format(perDay)}",
                    color = Color(0xFF10B981)
                )

                SummaryItem(
                    title = "Per Hour",
                    value = "₹ ${"%.2f".format(perHour)}",
                    color = Color(0xFFF59E0B)
                )
            }
        }
    }
}

@Composable
fun SummaryItem(
    title: String,
    value: String,
    color: Color
) {

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
package com.aaryo.selfattendance.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import com.aaryo.selfattendance.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaryo.selfattendance.ui.dashboard.formatMoney

@Composable
fun SalaryBreakdownCard(
    perDay: Double,
    perHour: Double? = null,
    overtimeRate: Double?,
    earnedPercent: Double?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        )
    ) {

        Column(modifier = Modifier.padding(16.dp)) {

            Text(
                text = "Salary Breakdown",
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Text("Per Day: ₹${perDay.formatMoney()}")

            perHour?.let {
                Text("Per Hour: ₹${it.formatMoney()}")
            }

            overtimeRate?.let {
                Text("Overtime: ₹${it.formatMoney()}")
            }

            earnedPercent?.let {
                Text("Working %: ${it.toInt()}%")
            }
        }
    }
}

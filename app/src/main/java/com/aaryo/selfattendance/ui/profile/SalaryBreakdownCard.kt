package com.aaryo.selfattendance.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaryo.selfattendance.ui.dashboard.formatMoney

// ─────────────────────────────────────────────────────────────────────────────
// Salary Breakdown Card — shown on both Setup & Edit Profile screens.
//
// Formula (Fixed 30-Day basis):
//   Per Day     = Monthly Salary ÷ 30
//   Per Hour    = Per Day ÷ 8   (8 working hours/day)
//   Half Day    = Per Day ÷ 2
//   Overtime/hr = Per Hour × 1  (same as per-hour rate)
//     e.g. ₹18,000 ÷ 30 = ₹600/day → ₹75/hr → Overtime ₹75/hr
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SalaryBreakdownCard(
    monthlySalary  : Double,
    perDay         : Double,
    perHour        : Double? = null,
    halfDay        : Double? = null,
    overtimeRate   : Double? = null,
    earnedPercent  : Double? = null,
    currencySymbol : String  = "₹"
) {
    Column(
        modifier            = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        // ── Header ────────────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector        = Icons.Outlined.Calculate,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text       = "Auto Salary Breakdown",
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color      = MaterialTheme.colorScheme.onSurface
            )
        }

        // ── Info Banner ───────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f)
                        )
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier              = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text  = "Monthly Salary",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Text(
                        text       = "$currencySymbol${monthlySalary.formatMoney()}",
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color      = Color.White
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text  = "30-Day Fixed Formula",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                    Text(
                        text       = "8 hrs/day",
                        style      = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color      = Color.White
                    )
                }
            }
        }

        // ── Summary Cards Grid ────────────────────────────────────────────────
        // Row 1: Per Day + Per Hour
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier              = Modifier.fillMaxWidth()
        ) {
            BreakdownMiniCard(
                label    = "Per Day",
                formula  = "÷ 30 days",
                amount   = "$currencySymbol${perDay.formatMoney()}",
                icon     = Icons.Outlined.Today,
                iconTint = MaterialTheme.colorScheme.primary,
                bgColor  = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.weight(1f)
            )
            if (perHour != null) {
                BreakdownMiniCard(
                    label    = "Per Hour",
                    formula  = "÷ 8 hrs/din",
                    amount   = "$currencySymbol${perHour.formatMoney()}",
                    icon     = Icons.Outlined.Schedule,
                    iconTint = Color(0xFF6A1B9A),
                    bgColor  = Color(0xFF6A1B9A).copy(alpha = 0.07f),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Row 2: Half Day + Overtime
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier              = Modifier.fillMaxWidth()
        ) {
            if (halfDay != null) {
                BreakdownMiniCard(
                    label    = "Half Day",
                    formula  = "÷ 2",
                    amount   = "$currencySymbol${halfDay.formatMoney()}",
                    icon     = Icons.Outlined.WbTwilight,
                    iconTint = Color(0xFFF57F17),
                    bgColor  = Color(0xFFF57F17).copy(alpha = 0.07f),
                    modifier = Modifier.weight(1f)
                )
            }
            if (overtimeRate != null) {
                BreakdownMiniCard(
                    label    = "Overtime/hr",
                    formula  = "= Per Hour",
                    amount   = "$currencySymbol${overtimeRate.formatMoney()}",
                    icon     = Icons.Outlined.Bolt,
                    iconTint = Color(0xFF2E7D32),
                    bgColor  = Color(0xFF2E7D32).copy(alpha = 0.07f),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── Detail Breakdown ──────────────────────────────────────────────────
        Card(
            shape     = RoundedCornerShape(14.dp),
            colors    = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier  = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text       = "Formula Details",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))

                DetailRow(
                    label = "Monthly Salary",
                    value = "$currencySymbol${monthlySalary.formatMoney()}",
                    color = MaterialTheme.colorScheme.onSurface
                )
                DetailRow(
                    label = "Per Day  (Monthly ÷ 30)",
                    value = "$currencySymbol${perDay.formatMoney()}",
                    color = MaterialTheme.colorScheme.primary
                )
                perHour?.let {
                    DetailRow(
                        label = "Per Hour  (Per Day ÷ 8)",
                        value = "$currencySymbol${it.formatMoney()}",
                        color = Color(0xFF6A1B9A)
                    )
                }
                halfDay?.let {
                    DetailRow(
                        label = "Half Day  (Per Day ÷ 2)",
                        value = "$currencySymbol${it.formatMoney()}",
                        color = Color(0xFFF57F17)
                    )
                }
                overtimeRate?.let {
                    DetailRow(
                        label = "Overtime/hr  (= Per Hour rate)",
                        value = "$currencySymbol${it.formatMoney()}",
                        color = Color(0xFF2E7D32)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mini Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BreakdownMiniCard(
    label    : String,
    formula  : String,
    amount   : String,
    icon     : ImageVector,
    iconTint : Color,
    bgColor  : Color,
    modifier : Modifier = Modifier
) {
    Card(
        shape     = RoundedCornerShape(14.dp),
        colors    = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier  = modifier
    ) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = iconTint,
                    modifier           = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text  = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text       = amount,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color      = iconTint
                )
                Text(
                    text  = formula,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Detail Row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DetailRow(label: String, value: String, color: Color) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text       = value,
            style      = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color      = color
        )
    }
}

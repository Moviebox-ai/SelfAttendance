package com.aaryo.selfattendance.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Feature data class representing an exclusive Business Mode feature.
 */
data class BusinessExclusiveFeature(
    val icon: ImageVector,
    val iconBgColor: Color,
    val iconTint: Color,
    val title: String,
    val description: String,
    val tag: String? = null
)

/**
 * A clean, visually appealing Compose dialog component displaying exclusive
 * Business Mode features (Advanced Analytics, Cloud Syncing, Multi-device support, etc.)
 * shown when a user registers or logs in for a Business account.
 */
@Composable
fun BusinessFeaturesDialog(
    isRegistration: Boolean = true,
    monthlyPrice: String = "₹299",
    sixMonthPrice: String = "₹999",
    yearlyPrice: String = "₹1,499",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val features = listOf(
        BusinessExclusiveFeature(
            icon = Icons.Default.Analytics,
            iconBgColor = Color(0xFFE3F2FD),
            iconTint = Color(0xFF1976D2),
            title = "Advanced Business Analytics",
            description = "Detailed visual charts for attendance trends, overtime hours, salary payouts, and monthly expenditure summaries.",
            tag = "PRO"
        ),
        BusinessExclusiveFeature(
            icon = Icons.Default.CloudSync,
            iconBgColor = Color(0xFFE8F5E9),
            iconTint = Color(0xFF2E7D32),
            title = "Real-Time Cloud Syncing",
            description = "Automated instant cloud backup with 100% secure storage so your staff data and records are never lost.",
            tag = "CLOUD"
        ),
        BusinessExclusiveFeature(
            icon = Icons.Default.Devices,
            iconBgColor = Color(0xFFEDE7F6),
            iconTint = Color(0xFF5E35B1),
            title = "Seamless Multi-Device Support",
            description = "Manage attendance, khata, and employee details simultaneously across multiple phones, tablets, and devices.",
            tag = "SYNC"
        ),
        BusinessExclusiveFeature(
            icon = Icons.Default.Groups,
            iconBgColor = Color(0xFFFFF3E0),
            iconTint = Color(0xFFE65100),
            title = "Unlimited Staff & Khata Management",
            description = "Add unlimited staff members, record salary advances, loan deductions, and daily overtime tracking."
        ),
        BusinessExclusiveFeature(
            icon = Icons.Default.PictureAsPdf,
            iconBgColor = Color(0xFFFCE4EC),
            iconTint = Color(0xFFC2185B),
            title = "1-Click PDF Salary Slips & Sharing",
            description = "Generate professional salary slips with your business name and share directly on WhatsApp in one click."
        ),
        BusinessExclusiveFeature(
            icon = Icons.Default.Block,
            iconBgColor = Color(0xFFE0F7FA),
            iconTint = Color(0xFF00838F),
            title = "100% Ad-Free Clean Experience",
            description = "Completely ad-free workflow to keep your daily business operations distraction-free."
        )
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.93f)
                .fillMaxHeight(0.88f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Badge & Title
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF1565C0), Color(0xFF0D47A1))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(30.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (isRegistration) "Exclusive Business Features" else "Welcome to Business Mode",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Everything you need to manage your staff, payroll, and business seamlessly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 7-Day Free Trial Chip
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFE8F5E9),
                    border = BorderStroke(1.dp, Color(0xFFA5D6A7))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "7-Day Full Access Free Trial Included",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B5E20)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Feature List
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    features.forEach { feature ->
                        BusinessFeatureCard(feature = feature)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Transparent Pricing Footer Note
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                        border = BorderStroke(1.dp, Color(0xFFFFE082))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFFE65100),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Transparent Pricing After Free Trial",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFBF360C)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "7 days ke baad Business features continue rakhne ke liye monthly ($monthlyPrice), 6-month ($sixMonthPrice) ya yearly ($yearlyPrice) plan choose kar sakte hain.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF5D4037),
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RocketLaunch,
                            contentDescription = null,
                            tint = Color(0xFFFFD54F),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Start 7-Day Free Trial",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Not Now / Cancel",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BusinessFeatureCard(feature: BusinessExclusiveFeature) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(feature.iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = feature.icon,
                    contentDescription = null,
                    tint = feature.iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Text info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = feature.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    feature.tag?.let { tagText ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = feature.iconTint.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = tagText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = feature.iconTint,
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                Text(
                    text = feature.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp,
                    fontSize = 12.sp
                )
            }
        }
    }
}

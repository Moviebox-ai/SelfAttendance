package com.aaryo.selfattendance.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * RewardPopup — animated dialog shown after a rewarded ad completes.
 *
 * Shows a coin bounce animation and the earned amount.
 * Calls [onDismiss] when the user taps "Awesome 🎉" or outside the dialog.
 *
 * Usage (in a Composable that observes ViewModel state):
 * ```
 * if (showReward) {
 *     RewardPopup(coins = earnedCoins) { viewModel.dismissReward() }
 * }
 * ```
 */
@Composable
fun RewardPopup(
    coins: Int,
    onDismiss: () -> Unit
) {
    // Coin bounce animation
    val infiniteTransition = rememberInfiniteTransition(label = "coin_bounce")
    val coinScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.15f,
        animationSpec = infiniteRepeatable(
            animation  = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "coinScale"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            Card(
                shape  = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF1A1A2E),
                                    Color(0xFF16213E)
                                )
                            )
                        )
                        .padding(vertical = 32.dp, horizontal = 24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Animated coin emoji
                        Text(
                            text     = "🪙",
                            fontSize = 64.sp,
                            modifier = Modifier.scale(coinScale)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text       = "🎉 Reward Earned!",
                            style      = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color      = Color(0xFFFFD700),
                            textAlign  = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text      = "You earned $coins coins!",
                            style     = MaterialTheme.typography.bodyLarge,
                            color     = Color.White,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text      = "Keep your streak going 🔥",
                            style     = MaterialTheme.typography.bodyMedium,
                            color     = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            shape  = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFD700),
                                contentColor   = Color(0xFF1A1A2E)
                            )
                        ) {
                            Text(
                                text       = "Awesome 🎉",
                                fontWeight = FontWeight.Bold,
                                fontSize   = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

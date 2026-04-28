package com.aaryo.selfattendance.ui.admin

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.aaryo.selfattendance.admin.AdminViewModel
import com.aaryo.selfattendance.ui.navigation.Routes

// ═══════════════════════════════════════════════════════════════
//  AdminPinScreen — 3 modes:
//    1. VERIFY  — Enter existing PIN
//    2. SETUP   — Set PIN for first time
//    3. CONFIRM — Confirm new PIN (re-enter)
// ═══════════════════════════════════════════════════════════════

private enum class PinMode { VERIFY, SETUP, CONFIRM }

@Composable
fun AdminPinScreen(navController: NavController) {

    val vm: AdminViewModel = viewModel(
        factory = AdminViewModel.Factory(navController.context)
    )
    val state by vm.state.collectAsState()

    // Navigate to dashboard once authenticated
    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated) {
            navController.navigate(Routes.ADMIN_DASHBOARD) {
                popUpTo(Routes.ADMIN_PIN) { inclusive = true }
            }
        }
    }

    var mode        by remember { mutableStateOf(
        if (vm.isFirstTimeSetup()) PinMode.SETUP else PinMode.VERIFY
    ) }
    var pin         by remember { mutableStateOf("") }
    var firstPin    by remember { mutableStateOf("") }  // stored during CONFIRM step
    var setupError  by remember { mutableStateOf<String?>(null) }

    val isLocked = state.lockoutSeconds > 0

    // Auto-clear PIN on error
    LaunchedEffect(state.pinError) {
        if (state.pinError != null) pin = ""
    }

    Box(
        modifier          = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment  = Alignment.Center
    ) {

        // Back button
        IconButton(
            onClick  = { navController.popBackStack() },
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {

            // Icon
            Icon(
                imageVector        = if (mode == PinMode.VERIFY) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(56.dp)
            )

            Spacer(Modifier.height(12.dp))

            // Title
            Text(
                text       = when (mode) {
                    PinMode.VERIFY  -> "Admin Access"
                    PinMode.SETUP   -> "Set Admin PIN"
                    PinMode.CONFIRM -> "Confirm PIN"
                },
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // Subtitle
            Text(
                text     = when (mode) {
                    PinMode.VERIFY  -> "Enter your 6-digit admin PIN"
                    PinMode.SETUP   -> "Choose a 6-digit PIN for admin access"
                    PinMode.CONFIRM -> "Re-enter PIN to confirm"
                },
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
            )

            // PIN dots
            PinDots(
                length   = pin.length,
                hasError = state.pinError != null || setupError != null
            )

            Spacer(Modifier.height(16.dp))

            // Status message
            val statusMsg = when {
                isLocked -> {
                    val m = state.lockoutSeconds / 60
                    val s = state.lockoutSeconds % 60
                    "🔒 Locked — %d:%02d remaining".format(m, s)
                }
                state.pinError != null -> state.pinError!!
                setupError != null     -> setupError!!
                mode == PinMode.SETUP   -> "Default PIN is 000000 — change it now"
                else                    -> ""
            }
            if (statusMsg.isNotEmpty()) {
                Text(
                    text      = statusMsg,
                    color     = if (isLocked || state.pinError != null || setupError != null)
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                    style     = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(24.dp))

            // Keypad
            PinKeypad(
                enabled  = !isLocked,
                onDigit  = { digit ->
                    if (pin.length < 6) {
                        pin += digit
                        setupError = null

                        if (pin.length == 6) {
                            when (mode) {

                                PinMode.VERIFY -> {
                                    vm.submitPin(pin)
                                    // pin cleared by LaunchedEffect on error
                                }

                                PinMode.SETUP -> {
                                    firstPin = pin
                                    pin      = ""
                                    mode     = PinMode.CONFIRM
                                }

                                PinMode.CONFIRM -> {
                                    if (pin == firstPin) {
                                        vm.saveNewPin(pin)
                                        vm.submitPin(pin)   // auto-login after setup
                                    } else {
                                        setupError = "PINs don't match. Try again."
                                        pin        = ""
                                        firstPin   = ""
                                        mode       = PinMode.SETUP
                                    }
                                }
                            }
                        }
                    }
                },
                onDelete = {
                    if (pin.isNotEmpty()) pin = pin.dropLast(1)
                    setupError = null
                }
            )

            // In VERIFY mode — offer to reset if first-time (default PIN hint)
            if (mode == PinMode.VERIFY) {
                Spacer(Modifier.height(20.dp))
                TextButton(onClick = {
                    pin        = ""
                    firstPin   = ""
                    setupError = null
                    mode       = PinMode.SETUP
                }) {
                    Text("Change PIN", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// ── PIN dot indicators ─────────────────────────────────────────

@Composable
private fun PinDots(length: Int, hasError: Boolean) {
    val errorColor    = MaterialTheme.colorScheme.error
    val activeColor   = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.outline

    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(6) { i ->
            val filled = i < length
            val color by animateColorAsState(
                targetValue   = when {
                    hasError -> errorColor
                    filled   -> activeColor
                    else     -> inactiveColor
                },
                animationSpec = tween(150),
                label         = "dot_$i"
            )
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (filled) color else Color.Transparent)
                    .border(2.dp, color, CircleShape)
            )
        }
    }
}

// ── Numeric keypad ─────────────────────────────────────────────

@Composable
private fun PinKeypad(enabled: Boolean, onDigit: (String) -> Unit, onDelete: () -> Unit) {
    val rows = listOf(
        listOf("1","2","3"),
        listOf("4","5","6"),
        listOf("7","8","9"),
        listOf("","0","⌫")
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    when (key) {
                        ""  -> Spacer(Modifier.size(76.dp))
                        "⌫" -> PinKey(label = null, icon = Icons.Default.Backspace, enabled = enabled, onClick = onDelete)
                        else -> PinKey(label = key, enabled = enabled, onClick = { onDigit(key) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PinKey(
    label   : String?,
    icon    : ImageVector? = null,
    enabled : Boolean,
    onClick : () -> Unit
) {
    FilledTonalButton(
        onClick         = onClick,
        enabled         = enabled,
        shape           = RoundedCornerShape(18.dp),
        modifier        = Modifier.size(76.dp),
        contentPadding  = PaddingValues(0.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = "Delete", modifier = Modifier.size(22.dp))
        } else {
            Text(label ?: "", fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }
    }
}

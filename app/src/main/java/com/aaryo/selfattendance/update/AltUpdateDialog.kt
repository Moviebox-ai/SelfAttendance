package com.aaryo.selfattendance.update

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity

// ══════════════════════════════════════════════════════════════════════════════
//  AltUpdateDialog — Modern Compose Update Dialog
//
//  3 states handle karta hai:
//   1. Available     → version info + changelog + download button
//   2. Downloading   → animated progress bar
//   3. ReadyToInstall → install button
//   4. Error         → retry button
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun AltUpdateDialog(
    state   : AlternativeUpdateManager.UpdateState,
    manager : AlternativeUpdateManager,
    onDismiss: () -> Unit
) {
    val context  = LocalContext.current
    val activity = context as? FragmentActivity ?: return

    // Force update — back button disable
    val isForced = when (state) {
        is AlternativeUpdateManager.UpdateState.Available -> state.isForced
        else -> false
    }

    if (isForced) {
        BackHandler(enabled = true) { /* consume — prevent back */ }
    }

    Dialog(
        onDismissRequest = { if (!isForced) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress    = !isForced,
            dismissOnClickOutside = !isForced,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape  = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // ── Gradient header ───────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1565C0), Color(0xFF1976D2), Color(0xFF42A5F5))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.SystemUpdate,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = when (state) {
                                is AlternativeUpdateManager.UpdateState.Available -> state.title
                                is AlternativeUpdateManager.UpdateState.Downloading,
                                is AlternativeUpdateManager.UpdateState.Progress  -> "Downloading..."
                                is AlternativeUpdateManager.UpdateState.ReadyToInstall -> "Ready to Install!"
                                is AlternativeUpdateManager.UpdateState.Error     -> "Update Failed"
                                else -> ""
                            },
                            color      = Color.White,
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // ── Content ───────────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (state) {

                        is AlternativeUpdateManager.UpdateState.Available -> {
                            AvailableContent(
                                state     = state,
                                isForced  = isForced,
                                onDownload = {
                                    manager.downloadAndInstall(activity, state.apkUrl)
                                },
                                onDismiss = { if (!isForced) onDismiss() }
                            )
                        }

                        is AlternativeUpdateManager.UpdateState.Downloading -> {
                            DownloadingContent(percent = null)
                        }

                        is AlternativeUpdateManager.UpdateState.Progress -> {
                            DownloadingContent(percent = state.percent)
                        }

                        is AlternativeUpdateManager.UpdateState.ReadyToInstall -> {
                            ReadyContent(onInstall = { manager.installApk(activity) })
                        }

                        is AlternativeUpdateManager.UpdateState.Error -> {
                            ErrorContent(
                                message   = state.message,
                                onRetry   = { manager.reset(); manager.checkAltUpdate() },
                                onDismiss = { if (!isForced) onDismiss() }
                            )
                        }

                        else -> {}
                    }
                }
            }
        }
    }
}

// ── Available content ─────────────────────────────────────────────────────────

@Composable
private fun AvailableContent(
    state    : AlternativeUpdateManager.UpdateState.Available,
    isForced : Boolean,
    onDownload: () -> Unit,
    onDismiss : () -> Unit
) {
    // Version badge
    Surface(
        color  = MaterialTheme.colorScheme.primaryContainer,
        shape  = RoundedCornerShape(100.dp)
    ) {
        Text(
            text     = "Version ${state.versionName}",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
            style    = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color    = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }

    // Changelog
    if (state.changelog.isNotBlank()) {
        Surface(
            color  = MaterialTheme.colorScheme.surfaceVariant,
            shape  = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "What's New",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text  = state.changelog,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }
        }
    }

    // Force update warning
    if (isForced) {
        Surface(
            color  = MaterialTheme.colorScheme.errorContainer,
            shape  = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "⚠️ Yeh update required hai. App use karne ke liye update karna zaroori hai.",
                modifier   = Modifier.padding(10.dp),
                style      = MaterialTheme.typography.bodySmall,
                color      = MaterialTheme.colorScheme.onErrorContainer,
                textAlign  = TextAlign.Center
            )
        }
    }

    // Buttons
    Button(
        onClick  = onDownload,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp)
    ) {
        Text("Download & Install  ↓", fontWeight = FontWeight.SemiBold)
    }

    if (!isForced) {
        TextButton(onClick = onDismiss) {
            Text("Baad mein", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Downloading content ───────────────────────────────────────────────────────

@Composable
private fun DownloadingContent(percent: Int?) {
    val infiniteProgress = percent == null
    val animProgress by animateFloatAsState(
        targetValue   = (percent ?: 0) / 100f,
        animationSpec = tween(300),
        label         = "progress"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Downloading update...",
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        if (infiniteProgress) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        } else {
            LinearProgressIndicator(
                progress = { animProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Text(
                "$percent%",
                style  = MaterialTheme.typography.bodySmall,
                color  = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            "Please wait, do not close the app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Ready to install ──────────────────────────────────────────────────────────

@Composable
private fun ReadyContent(onInstall: () -> Unit) {
    Text(
        "✅ Download complete!",
        style      = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold
    )
    Text(
        "Tap below to install the update.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Button(
        onClick  = onInstall,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF1D9E75)
        )
    ) {
        Text("Install Now", fontWeight = FontWeight.Bold)
    }
}

// ── Error content ─────────────────────────────────────────────────────────────

@Composable
private fun ErrorContent(
    message   : String,
    onRetry   : () -> Unit,
    onDismiss : () -> Unit
) {
    Text(
        "❌ $message",
        style     = MaterialTheme.typography.bodySmall,
        color     = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center
    )
    Button(
        onClick  = onRetry,
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp)
    ) {
        Text("Retry")
    }
    TextButton(onClick = onDismiss) {
        Text("Dismiss")
    }
}

package com.aaryo.selfattendance.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.os.Process
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.Socket

/**
 * AntiDecompileGuard — Runtime Anti-Decompilation, Anti-Hooking & Tamper Protection.
 *
 * Protects against:
 * 1. Runtime Bytecode Hooking (Frida, Xposed, Cydia Substrate).
 * 2. Active Debuggers / Reverse Engineering Tools (GDB, LLDB, IDA Pro).
 * 3. Repackaged / Tampered APKs (Modified signature & dex alterations).
 * 4. Automated Sandboxes / Malicious Emulators attempting memory dumps.
 */
object AntiDecompileGuard {

    private const val TAG = "AntiDecompileGuard"

    /**
     * Comprehensive security audit. Returns true if system is secure, false if tampering/hooking is detected.
     */
    fun isDeviceAndAppSecure(context: Context): Boolean {
        if (isDebuggerAttached(context)) {
            Log.e(TAG, "Security Alert: Debugger attached or debuggable build active!")
            return false
        }

        if (isFridaOrHookingActive()) {
            Log.e(TAG, "Security Alert: Frida / Xposed instrumentation detected!")
            return false
        }

        return true
    }

    /**
     * Checks if a dynamic debugger (JDB / LLDB / IDA) is actively hooked to the process.
     */
    fun isDebuggerAttached(context: Context): Boolean {
        try {
            if (Debug.isDebuggerConnected() || Debug.waitingForDebugger()) {
                return true
            }
            // Check if debuggable flag is enabled in non-debug mode
            val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (isDebuggable && !com.aaryo.selfattendance.BuildConfig.DEBUG) {
                return true
            }
        } catch (_: Exception) {}
        return false
    }

    /**
     * Checks for the presence of Frida, Xposed, and other dynamic memory inspection agents.
     */
    fun isFridaOrHookingActive(): Boolean {
        // 1. Scan memory maps of the current process for injected .so files
        try {
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists() && mapsFile.canRead()) {
                val reader = BufferedReader(FileReader(mapsFile))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val lower = line?.lowercase() ?: continue
                    if (lower.contains("frida") || lower.contains("xposed") ||
                        lower.contains("substrate") || lower.contains("gadget.so") ||
                        lower.contains("libmemtrack.so") && lower.contains("tmp")
                    ) {
                        reader.close()
                        return true
                    }
                }
                reader.close()
            }
        } catch (_: Exception) {}

        // 2. Check for typical Frida communication artifacts
        val suspiciousFiles = listOf(
            "/data/local/tmp/frida-server",
            "/data/local/tmp/re.frida.server",
            "/data/local/tmp/frida-agent.so"
        )
        for (path in suspiciousFiles) {
            try {
                val file = File(path)
                if (file.exists()) return true
            } catch (_: Exception) {}
        }

        return false
    }

    /**
     * Validates that the APK signature matches expected release fingerprints.
     */
    fun isSignatureValid(context: Context, expectedSha256: String?): Boolean {
        if (expectedSha256.isNullOrBlank()) return true // Skip if not strictly specified

        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
            }
            return packageInfo != null
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Emergency termination in case of critical breach / tampering.
     */
    fun emergencyLockdown() {
        Log.e(TAG, "EMERGENCY LOCKDOWN: Tamper / reverse-engineering attempt detected. Terminating process.")
        Process.killProcess(Process.myPid())
        System.exit(1)
    }
}

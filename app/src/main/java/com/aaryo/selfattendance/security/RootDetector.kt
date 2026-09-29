package com.aaryo.selfattendance.security

import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Enterprise-grade Device Root and Tamper Detection Engine.
 *
 * Performs multi-vector checks:
 * 1. Known SU and SuperUser binaries
 * 2. Magisk, KernelSU, and APatch signatures
 * 3. Busybox binary presence
 * 4. Test-keys build tags
 * 5. Native command execution (which su)
 * 6. Dangerous root-associated system properties (ro.debuggable, ro.secure)
 * 7. Read-Write mount status of protected system partitions
 */
object RootDetector {

    private const val TAG = "RootDetector"

    fun isDeviceRooted(): Boolean {
        return checkRootFiles() ||
                checkMagiskAndKernelSU() ||
                checkBusybox() ||
                checkTestKeys() ||
                checkSuCommand() ||
                checkDangerousProperties() ||
                checkRwMounts()
    }

    private fun checkRootFiles(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/system/xbin/su",
            "/system/bin/su",
            "/sbin/su",
            "/system/bin/.ext/.su",
            "/system/usr/we-need-root/su",
            "/system/app/SuperSU",
            "/system/app/Magisk.apk",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su",
            "/system/xbin/daemonsu"
        )

        for (path in paths) {
            try {
                if (File(path).exists()) {
                    Log.w(TAG, "Root binary detected at: $path")
                    return true
                }
            } catch (_: Exception) {}
        }
        return false
    }

    private fun checkMagiskAndKernelSU(): Boolean {
        val paths = arrayOf(
            "/sbin/magisk",
            "/system/bin/magisk",
            "/system/xbin/magisk",
            "/data/adb/magisk",
            "/data/adb/ksu",           // KernelSU detection
            "/data/adb/ap",            // APatch detection
            "/data/adb/modules",
            "/cache/magisk.log"
        )

        for (path in paths) {
            try {
                if (File(path).exists()) {
                    Log.w(TAG, "Magisk/KernelSU artifact detected at: $path")
                    return true
                }
            } catch (_: Exception) {}
        }
        return false
    }

    private fun checkBusybox(): Boolean {
        val busyboxPaths = arrayOf(
            "/system/bin/busybox",
            "/system/xbin/busybox",
            "/sbin/busybox",
            "/vendor/bin/busybox"
        )

        for (path in busyboxPaths) {
            try {
                if (File(path).exists()) {
                    Log.w(TAG, "Busybox detected at: $path")
                    return true
                }
            } catch (_: Exception) {}
        }
        return false
    }

    private fun checkTestKeys(): Boolean {
        val tags = Build.TAGS
        return tags != null && tags.contains("test-keys")
    }

    private fun checkSuCommand(): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            line != null && line.isNotBlank()
        } catch (_: Exception) {
            try {
                process = Runtime.getRuntime().exec(arrayOf("which", "su"))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val line = reader.readLine()
                line != null && line.isNotBlank()
            } catch (_: Exception) {
                false
            }
        } finally {
            process?.destroy()
        }
    }

    private fun checkDangerousProperties(): Boolean {
        val dangerousProps = mapOf(
            "ro.debuggable" to "1",
            "ro.secure" to "0"
        )

        for ((propName, dangerousVal) in dangerousProps) {
            var process: Process? = null
            try {
                process = Runtime.getRuntime().exec(arrayOf("getprop", propName))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val value = reader.readLine()?.trim()
                if (value == dangerousVal) {
                    Log.w(TAG, "Dangerous property found: $propName=$value")
                    return true
                }
            } catch (_: Exception) {
            } finally {
                process?.destroy()
            }
        }
        return false
    }

    private fun checkRwMounts(): Boolean {
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec("mount")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line?.lowercase() ?: continue
                if (l.contains("/system") || l.contains("/vendor") || l.contains("/system_root")) {
                    val flags = l.split(" ")
                    for (flag in flags) {
                        if (flag.startsWith("(") || flag.endsWith(")")) {
                            val clean = flag.replace("(", "").replace(")", "")
                            val parts = clean.split(",")
                            if ("rw" in parts) {
                                Log.w(TAG, "Writable system partition detected: $l")
                                return true
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            process?.destroy()
        }
        return false
    }
}

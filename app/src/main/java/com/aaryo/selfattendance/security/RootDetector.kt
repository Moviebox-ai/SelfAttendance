package com.aaryo.selfattendance.security

import android.os.Build
import java.io.File

object RootDetector {

    fun isDeviceRooted(): Boolean {
        return checkRootFiles() || checkMagisk() || checkBusybox() || checkTestKeys()
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
            "/system/app/Magisk.apk"
        )

        for (path in paths) {
            if (File(path).exists()) {
                return true
            }
        }

        return false
    }

    private fun checkMagisk(): Boolean {

        val magiskPaths = arrayOf(
            "/sbin/magisk",
            "/system/bin/magisk",
            "/system/xbin/magisk"
        )

        for (path in magiskPaths) {
            if (File(path).exists()) {
                return true
            }
        }

        return false
    }

    private fun checkBusybox(): Boolean {

        val busyboxPaths = arrayOf(
            "/system/bin/busybox",
            "/system/xbin/busybox"
        )

        for (path in busyboxPaths) {
            if (File(path).exists()) {
                return true
            }
        }

        return false
    }

    private fun checkTestKeys(): Boolean {
        return Build.TAGS != null && Build.TAGS.contains("test-keys")
    }
}
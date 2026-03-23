package com.aaryo.selfattendance.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log

object AppSecurity {

    private const val PLAY_STORE_PACKAGE = "com.android.vending"

    /**
     * Check if app is installed from Google Play Store
     */
    fun isInstalledFromPlayStore(context: Context): Boolean {

        return try {

            val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

                context.packageManager
                    .getInstallSourceInfo(context.packageName)
                    .installingPackageName

            } else {

                context.packageManager
                    .getInstallerPackageName(context.packageName)

            }

            installer == PLAY_STORE_PACKAGE

        } catch (e: Exception) {

            Log.e("AppSecurity", "Installer check failed", e)
            false
        }
    }

    /**
     * Check if app is debuggable
     */
    fun isDebuggable(context: Context): Boolean {

        return try {

            (context.applicationInfo.flags and
                    ApplicationInfo.FLAG_DEBUGGABLE) != 0

        } catch (e: Exception) {

            Log.e("AppSecurity", "Debuggable check failed", e)
            false
        }
    }

    /**
     * Check if app is running in emulator
     */
    fun isRunningOnEmulator(): Boolean {

        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.contains("vbox")
                || Build.FINGERPRINT.contains("test-keys")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.BRAND.startsWith("generic")
                || Build.DEVICE.startsWith("generic"))
    }

}
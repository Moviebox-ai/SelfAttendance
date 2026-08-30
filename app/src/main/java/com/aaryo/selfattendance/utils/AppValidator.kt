package com.aaryo.selfattendance.utils

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.aaryo.selfattendance.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

/**
 * AppValidator — startup diagnostics and runtime validation.
 *
 * Call AppValidator.logDiagnostics(context) from SelfAttendanceApp or
 * a debug screen to get a structured log of the device and app environment.
 * All output is tagged "AppValidator" for easy filtering.
 *
 * This class is safe to call on any flavor and any device — all checks use
 * try/catch to prevent startup crashes.
 */
object AppValidator {

    private const val TAG = "AppValidator"

    fun logDiagnostics(context: Context) {
        if (!BuildConfig.DEBUG && !BuildConfig.IS_AMAZON) return

        Log.i(TAG, "═══════════════════════════════════════")
        Log.i(TAG, "  Self Attendance Pro — Diagnostics")
        Log.i(TAG, "═══════════════════════════════════════")
        logAppInfo()
        logDeviceInfo()
        logFirebaseStatus(context)
        logNetworkStatus(context)
        logPlayServicesStatus(context)
        Log.i(TAG, "═══════════════════════════════════════")
    }

    private fun logAppInfo() {
        Log.i(TAG, "[App] version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        Log.i(TAG, "[App] flavor=${BuildConfig.STORE_NAME}  is_amazon=${BuildConfig.IS_AMAZON}")
        Log.i(TAG, "[App] debug=${BuildConfig.DEBUG}")
    }

    private fun logDeviceInfo() {
        Log.i(TAG, "[Device] model=${Build.MODEL}  brand=${Build.BRAND}")
        Log.i(TAG, "[Device] sdk=${Build.VERSION.SDK_INT}  release=${Build.VERSION.RELEASE}")
        Log.i(TAG, "[Device] abi=${Build.SUPPORTED_ABIS.joinToString()}")
        Log.i(TAG, "[Device] manufacturer=${Build.MANUFACTURER}")
    }

    private fun logFirebaseStatus(context: Context) {
        try {
            val apps = FirebaseApp.getApps(context.applicationContext)
            Log.i(TAG, "[Firebase] initialized=${apps.isNotEmpty()}")
            val user = FirebaseAuth.getInstance().currentUser
            Log.i(TAG, "[Auth] logged_in=${user != null}  uid=${user?.uid?.take(8) ?: "none"}")
        } catch (e: Exception) {
            Log.w(TAG, "[Firebase] status check failed: ${e.message}")
        }
    }

    private fun logNetworkStatus(context: Context) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(network)
            val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            val hasValidated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            Log.i(TAG, "[Network] internet=$hasInternet  validated=$hasValidated")
        } catch (e: Exception) {
            Log.w(TAG, "[Network] status check failed: ${e.message}")
        }
    }

    private fun logPlayServicesStatus(context: Context) {
        try {
            val pm = context.packageManager
            val gmsInfo = pm.getPackageInfo("com.google.android.gms", 0)
            Log.i(TAG, "[GMS] available=true  version=${gmsInfo.versionName}")
        } catch (e: PackageManager.NameNotFoundException) {
            Log.i(TAG, "[GMS] available=false — expected on Amazon Fire")
        } catch (e: Exception) {
            Log.w(TAG, "[GMS] status check failed: ${e.message}")
        }

        try {
            val pm = context.packageManager
            pm.getPackageInfo("com.amazon.mshop.android.shopping", 0)
            Log.i(TAG, "[Appstore] Amazon Appstore detected")
        } catch (_: PackageManager.NameNotFoundException) {
            Log.d(TAG, "[Appstore] Amazon Appstore not found (expected on Play builds)")
        } catch (e: Exception) {
            Log.w(TAG, "[Appstore] check failed: ${e.message}")
        }
    }
}

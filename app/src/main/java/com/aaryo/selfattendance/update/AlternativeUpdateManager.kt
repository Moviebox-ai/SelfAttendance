package com.aaryo.selfattendance.update

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.aaryo.selfattendance.BuildConfig
import com.aaryo.selfattendance.data.remote.RemoteConfigManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

// ══════════════════════════════════════════════════════════════════════════════
//  AlternativeUpdateManager
//
//  Google Play update ke saath saath yeh bhi chalega.
//  Purpose: Admin Firebase Remote Config se APK URL set karke directly users
//  ko update push kar sakta hai — bina Play Store ke.
//
//  Flow:
//   1. App start pe RemoteConfig fetch hota hai (MainActivity)
//   2. MainActivity → checkAltUpdate() call karta hai
//   3. Agar remote version > current versionCode → UpdateState.Available
//   4. User "Download & Install" tap kare → DownloadManager se APK download
//   5. Download complete → APK install prompt auto-open
//
//  Security:
//   • APK URL sirf Firebase Remote Config se aata hai (admin-controlled)
//   • APK ko cache dir mein download kiya jata hai, baad mein delete hota hai
//   • Install ke liye REQUEST_INSTALL_PACKAGES permission chahiye (Manifest mein)
//   • Force update mode mein back button / skip disabled hota hai
//
//  Admin Setup (Firebase Console → Remote Config):
//   alt_update_enabled      = true
//   alt_update_version_code = 25           ← new build number
//   alt_update_version_name = "2.1.0"
//   alt_update_apk_url      = "https://your-cdn.com/app-release.apk"
//   alt_update_changelog    = "• Bug fixes\n• New feature X"
//   alt_update_force        = false        ← true = user cannot skip
//   alt_update_title        = "Naya Update Available!"
// ══════════════════════════════════════════════════════════════════════════════

class AlternativeUpdateManager(private val context: Context) {

    sealed class UpdateState {
        object None         : UpdateState()   // koi update nahi
        data class Available(                 // update available
            val versionName : String,
            val changelog   : String,
            val apkUrl      : String,
            val isForced    : Boolean,
            val title       : String
        ) : UpdateState()
        object Downloading  : UpdateState()   // download chal raha hai
        data class Progress(val percent: Int) : UpdateState()  // download %
        object ReadyToInstall : UpdateState() // download complete, install karo
        data class Error(val message: String) : UpdateState()
    }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.None)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val remoteConfig = RemoteConfigManager.getInstance()
    private var downloadId   = -1L
    private var apkFile      : File? = null

    // ── Check if alternative update is available ──────────────────────────────

    fun checkAltUpdate() {
        try {
            if (!remoteConfig.isAltUpdateEnabled()) {
                Log.d(TAG, "Alt update disabled via Remote Config")
                return
            }

            val remoteVersionCode = remoteConfig.altUpdateVersionCode()
            val currentVersionCode = BuildConfig.VERSION_CODE

            Log.d(TAG, "Alt update check: remote=$remoteVersionCode current=$currentVersionCode")

            if (remoteVersionCode <= currentVersionCode) {
                Log.d(TAG, "Already up to date")
                return
            }

            val apkUrl = remoteConfig.altUpdateApkUrl()
            if (apkUrl.isBlank()) {
                Log.w(TAG, "Alt update enabled but APK URL is empty — skipping")
                return
            }

            _state.value = UpdateState.Available(
                versionName = remoteConfig.altUpdateVersionName(),
                changelog   = remoteConfig.altUpdateChangelog(),
                apkUrl      = apkUrl,
                isForced    = remoteConfig.isAltUpdateForced(),
                title       = remoteConfig.altUpdateTitle()
                    .ifBlank { "Naya Update Available!" }
            )

            Log.d(TAG, "Alt update available: v${remoteConfig.altUpdateVersionName()}")

        } catch (e: Exception) {
            Log.e(TAG, "checkAltUpdate failed", e)
        }
    }

    // ── Download APK using Android DownloadManager ────────────────────────────

    fun downloadAndInstall(activity: Activity, apkUrl: String) {
        try {
            _state.value = UpdateState.Downloading

            // Save to app-specific cache (no external storage permission needed)
            val cacheDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: activity.cacheDir

            val outFile = File(cacheDir, "selfattendance_update.apk")
            if (outFile.exists()) outFile.delete()
            apkFile = outFile

            val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle("SelfAttendance Update")
                setDescription("Downloading latest update...")
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setDestinationUri(Uri.fromFile(outFile))
                setAllowedOverMetered(true)
                setAllowedOverRoaming(false)
            }

            val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = dm.enqueue(request)

            // Register receiver for completion
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        ctx.unregisterReceiver(this)
                        onDownloadComplete(activity, dm, id)
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                activity.registerReceiver(
                    receiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                activity.registerReceiver(
                    receiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }

            // Poll progress in background
            pollDownloadProgress(dm)

        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            _state.value = UpdateState.Error("Download failed: ${e.message}")
        }
    }

    // ── Poll download progress ─────────────────────────────────────────────────

    private fun pollDownloadProgress(dm: DownloadManager) {
        Thread {
            while (_state.value is UpdateState.Downloading ||
                   _state.value is UpdateState.Progress) {
                try {
                    val cursor = dm.query(
                        DownloadManager.Query().setFilterById(downloadId)
                    )
                    if (cursor.moveToFirst()) {
                        val total  = cursor.getLong(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val done   = cursor.getLong(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        if (total > 0) {
                            val pct = ((done * 100) / total).toInt()
                            _state.value = UpdateState.Progress(pct)
                        }
                    }
                    cursor.close()
                    Thread.sleep(500)
                } catch (_: Exception) { break }
            }
        }.start()
    }

    // ── Handle download completion ────────────────────────────────────────────

    private fun onDownloadComplete(
        activity   : Activity,
        dm         : DownloadManager,
        id         : Long
    ) {
        val cursor = dm.query(DownloadManager.Query().setFilterById(id))
        if (cursor.moveToFirst()) {
            val status = cursor.getInt(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                Log.d(TAG, "Download complete — launching install")
                _state.value = UpdateState.ReadyToInstall
                installApk(activity)
            } else {
                val reason = cursor.getInt(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                Log.e(TAG, "Download failed, reason=$reason")
                _state.value = UpdateState.Error("Download failed (reason $reason). Please retry.")
            }
        }
        cursor.close()
    }

    // ── Launch APK install ────────────────────────────────────────────────────

    fun installApk(activity: Activity) {
        val file = apkFile ?: run {
            _state.value = UpdateState.Error("APK file not found. Please download again.")
            return
        }

        if (!file.exists()) {
            _state.value = UpdateState.Error("APK file missing. Please download again.")
            return
        }

        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    activity,
                    "${activity.packageName}.provider",
                    file
                )
            } else {
                Uri.fromFile(file)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            activity.startActivity(intent)

        } catch (e: Exception) {
            Log.e(TAG, "Install failed", e)
            _state.value = UpdateState.Error("Install failed: ${e.message}")
        }
    }

    // ── Reset state ───────────────────────────────────────────────────────────
    fun reset() { _state.value = UpdateState.None }

    companion object {
        private const val TAG = "AltUpdateManager"
    }
}

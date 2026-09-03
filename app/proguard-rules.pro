# ═══════════════════════════════════════════════════════════
#  Self Attendance Pro — ProGuard / R8 Rules (Anti-Decompile & Obfuscation)
#  Covers both play and amazon product flavors.
# ═══════════════════════════════════════════════════════════

# R8 Optimizations & Anti-Decompilation Obfuscation
-optimizationpasses 2
-allowaccessmodification
-repackageclasses

# Strip logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# ── Kotlin ──────────────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Lazy { *; }

# ── Firebase ────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firestore data model classes (accessed via reflection)
-keep class com.aaryo.selfattendance.data.model.** { *; }


# ── AdMob / Ads ─────────────────────────────────────────
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# UMP Consent SDK (Play flavor; dontwarn covers Amazon where it is absent)
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.ump.**

# ── Play Core (play flavor only) ─────────────────────────
# The amazon flavor does not link these libraries; dontwarn prevents
# R8 from failing on missing references in the shrunk output.
-keep class com.google.android.play.core.integrity.** { *; }
-keep class com.google.android.play.core.appupdate.** { *; }
-keep class com.google.android.play.core.install.** { *; }
-keep class com.google.android.play.core.ktx.** { *; }
-dontwarn com.google.android.play.core.**

# ── WorkManager ─────────────────────────────────────────
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Jetpack Compose ─────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── Room Database ────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.**

# ── Coroutines ───────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ── Lottie ───────────────────────────────────────────────
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# ── Billing (play flavor only) ───────────────────────────
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# ── Security Crypto ──────────────────────────────────────
-keep class androidx.security.crypto.** { *; }

# ── General Android ─────────────────────────────────────
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── BuildConfig (required for IS_AMAZON checks at runtime) ──
-keep class com.aaryo.selfattendance.BuildConfig { *; }

# -- ZXing QR Code --
-keep class com.google.zxing.** { *; }
-keep class com.journeyapps.** { *; }
-dontwarn com.google.zxing.**
-dontwarn com.journeyapps.**

# -- Biometric --
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# ═══════════════════════════════════════════════════════════
#  Self Attendance Pro — ProGuard / R8 Rules (Anti-Decompile & Obfuscation)
#  Covers both play and amazon product flavors.
# ═══════════════════════════════════════════════════════════

# R8 Optimizations & Anti-Decompilation Obfuscation
-optimizationpasses 5
-allowaccessmodification
-repackageclasses ''
-overloadaggressively

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
# Keep only essential reflection metadata if required
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Lazy { *; }

# ── Firebase ────────────────────────────────────────────
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Firestore data model classes (accessed via reflection / serialization)
-keepclassmembers class com.aaryo.selfattendance.data.model.** {
    <init>(...);
    <fields>;
    public <methods>;
}

# ── AdMob / Ads ─────────────────────────────────────────
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# UMP Consent SDK (Play flavor; dontwarn covers Amazon where it is absent)
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.ump.**

# ── Play Core (play flavor only) ─────────────────────────
-dontwarn com.google.android.play.core.**

# ── WorkManager ─────────────────────────────────────────
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Jetpack Compose ─────────────────────────────────────
-dontwarn androidx.compose.**

# ── Room Database ────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { <fields>; <init>(...); }
-keep @androidx.room.Dao interface * { *; }
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
-dontwarn com.google.zxing.**
-dontwarn com.journeyapps.**

# -- Biometric --
-dontwarn androidx.biometric.**

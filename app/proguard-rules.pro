# ═══════════════════════════════════════════════════════════
#  Self Attendance Pro — ProGuard Rules
#  FIXED: Dead rules removed (Unity Ads, iText7 — not in dependencies)
# ═══════════════════════════════════════════════════════════

# Keep line numbers for crash reporting (Crashlytics)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

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

# Firestore data model classes (reflection se use hoti hain)
-keep class com.aaryo.selfattendance.data.model.** { *; }

# ── AdMob / Ads ─────────────────────────────────────────
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# UMP Consent SDK
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.ump.**

# ── Play Core ────────────────────────────────────────────
-keep class com.google.android.play.core.integrity.** { *; }
-keep class com.google.android.play.core.appupdate.** { *; }
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

# ── Billing ──────────────────────────────────────────────
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

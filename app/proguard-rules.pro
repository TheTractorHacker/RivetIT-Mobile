# ── Retrofit & OkHttp ────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclassmembernames interface * {
    @retrofit2.http.* <methods>;
}

# ── Gson & API models ─────────────────────────────────────────────────────────
# Keep all data classes in the api package so Gson can deserialize them
-keep class com.foleyit.itflow.data.api.** { *; }
-keepclassmembers class com.foleyit.itflow.data.api.** { *; }

# ── AndroidX Security (EncryptedSharedPreferences) ───────────────────────────
-keep class androidx.security.crypto.** { *; }

# ── Biometric ─────────────────────────────────────────────────────────────────
-keep class androidx.biometric.** { *; }

# ── Kotlin coroutines ─────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ── Compose ──────────────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── Room / WorkManager ───────────────────────────────────────────────────────
# The release build resolves room-runtime 2.2.5, whose own rule keeps RoomDatabase subclasses but not their no-arg
# constructor. R8 full mode then strips WorkDatabase_Impl.<init>() and WorkManager's startup initialiser crashes the
# app at launch ("Failed to create an instance of androidx.work.impl.WorkDatabase"). Room creates these reflectively.
-keep class * extends androidx.room.RoomDatabase { <init>(); }

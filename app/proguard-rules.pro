# Production ProGuard & R8 Rules for Rupoop

# ── Attributes & Metadata ───────────────────────────────────────────────────
-keepattributes Signature, InnerClasses, EnclosingMethod, AnnotationDefault, *Annotation*, SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# ── Custom App Code Safety ──────────────────────────────────────────────────
# (Removed greedy rule that kept all app code, as it defeats obfuscation)

# ── Kotlin Serialization ────────────────────────────────────────────────────
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    *** Companion;
}
-keepclassmembers class * {
    *** $serializer;
}
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# ── Retrofit & OkHttp ────────────────────────────────────────────────────────
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keep interface com.santiago43rus.rupoop.network.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# ── WorkManager ──────────────────────────────────────────────────────────────
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── AppAuth ─────────────────────────────────────────────────────────────────
-keep class net.openid.appauth.** { *; }

# ── Media3 / ExoPlayer ──────────────────────────────────────────────────────
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ── Coil ────────────────────────────────────────────────────────────────────
-keep class coil.** { *; }
-dontwarn coil.**

# ── Coroutines ──────────────────────────────────────────────────────────────
-keep class kotlinx.coroutines.** { *; }

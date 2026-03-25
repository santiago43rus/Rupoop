# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Retrofit
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,allowoptimization class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}

# AppAuth
-keep class net.openid.appauth.** { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }

# Keep App Classes just in case to prevent UI crashes while minifying standard libs
-keep class com.example.rupoop.** { *; }
-keep class com.santiago43rus.rupoop.** { *; }

# Keep Compose and Coroutines
-keep class androidx.compose.** { *; }
-keep class kotlinx.coroutines.** { *; }

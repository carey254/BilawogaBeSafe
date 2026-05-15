# Release hardening for BilaWoga
-optimizations !code/simplification/arithmetic
-dontnote javax.annotation.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**

# Keep Firebase and GMS models
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Keep AndroidX Security crypto
-keep class androidx.security.crypto.** { *; }

# Keep EncryptedSharedPreferences and MasterKey via reflection
-keepclassmembers class androidx.security.crypto.* {
    *;
}

# Keep your utils used by reflection (if any)
-keep class com.example.bilawoga.utils.** { *; }

# Keep Activities/Services/BroadcastReceivers
-keep class com.example.bilawoga.** extends android.app.Activity { *; }
-keep class com.example.bilawoga.** extends android.app.Service { *; }
-keep class com.example.bilawoga.** extends android.content.BroadcastReceiver { *; }

# Keep parcelables
-keep class * implements android.os.Parcelable { *; }

# SECURITY: Keep security-related classes and methods
-keep class com.example.bilawoga.utils.SecureStorageManager { *; }
-keep class com.example.bilawoga.utils.SecurityUtils { *; }
-keep class com.example.bilawoga.utils.AIBiasDetectionManager { *; }
-keep class com.example.bilawoga.utils.AbusePreventionManager { *; }
-keep class com.example.bilawoga.utils.MultilingualSupportManager { *; }

# Keep security manager inner classes
-keep class com.example.bilawoga.utils.AIBiasDetectionManager$* { *; }
-keep class com.example.bilawoga.utils.AbusePreventionManager$* { *; }
-keep class com.example.bilawoga.utils.MultilingualSupportManager$* { *; }

# Keep BuildConfig for signature verification
-keep class com.example.bilawoga.BuildConfig { *; }

# SECURITY: Obfuscate but keep security-critical method names
-keepclassmembers class com.example.bilawoga.utils.SecureStorageManager {
    public static *;
}

# Keep TTS and language managers
-keep class com.example.bilawoga.utils.TTSLanguageManager { *; }
-keep class com.example.bilawoga.utils.MultilingualSupportManager { *; }

# Strip logs in release for privacy
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# SECURITY: Remove debug information
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
}

# SECURITY: Remove stack traces in release (optional - may affect debugging)
# -assumenosideeffects class java.lang.Throwable {
#     public void printStackTrace();
# }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep R class
-keepclassmembers class **.R$* {
    public static <fields>;
}

# SECURITY: Keep annotation classes for security features
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
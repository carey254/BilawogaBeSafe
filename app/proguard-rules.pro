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

# Strip logs in release for privacy
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
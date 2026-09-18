# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# ---------- Media3 / ExoPlayer ----------
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ---------- Coil ----------
-keep class coil.** { *; }
-dontwarn coil.**

# ---------- Android NSD ----------
-keep class android.nsd.** { *; }
-dontwarn android.nsd.**

# ---------- MediaItem (Serializable) ----------
-keep class com.hmv.server.MediaItem { *; }

# ---------- JSON ----------
-keep class org.json.** { *; }
-dontwarn org.json.**

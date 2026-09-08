# MediaPro release rules. Minification is off by default; these apply only if
# you enable isMinifyEnabled. FFmpegKit uses reflection to dispatch commands,
# so keep its public API.
-keep class com.arthenica.ffmpegkit.** { *; }
-dontwarn com.arthenica.ffmpegkit.**
-keep class com.arthenica.smartcpp.** { *; }
-keep class com.arthenica.smartexception.** { *; }
-dontwarn com.arthenica.smartexception.**

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Add project specific ProGuard rules here.

# Keep mediasoup JNI classes
-keep class org.mediasoup.** { *; }
-keepclassmembers class org.mediasoup.** { *; }

# Keep WebRTC classes
-keep class org.webrtc.** { *; }
-keepclassmembers class org.webrtc.** { *; }

# Keep Gson models (for JSON serialization)
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.voiceping.android.data.model.** { *; }

# Keep domain models
-keep class com.voiceping.android.domain.model.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Retrofit
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

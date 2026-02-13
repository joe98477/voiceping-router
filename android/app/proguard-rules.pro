# VoicePing Android - ProGuard/R8 Rules
# Updated: 2026-02-13 for Phase 15 release build validation
# Libraries: libmediasoup-android 0.21.0, AGP 9.0.0, WebRTC (bundled)

# ===== WebRTC (org.webrtc) =====
# WebRTC contains extensive JNI bindings to native C++ code.
# R8 cannot detect method calls from C++, so we must preserve all classes.

-keep class org.webrtc.** { *; }
-keepclassmembers class org.webrtc.** { *; }
-keep interface org.webrtc.** { *; }

# Keep methods annotated with @CalledByNative (JNI upcalls from C++ to Java/Kotlin)
-keepclassmembers class * {
    @org.webrtc.CalledByNative <methods>;
}

# ===== mediasoup (io.github.crow_misia) =====
# crow-misia wrapper library for mediasoup, uses JNI bindings to WebRTC.
# Library does not provide consumer-rules.pro, so we preserve manually.

-keep class io.github.crow_misia.mediasoup.** { *; }
-keepclassmembers class io.github.crow_misia.mediasoup.** { *; }

# crow-misia WebRTC extensions (transitive dependency, used by MediasoupClient.initialize)
-keep class io.github.crow_misia.webrtc.** { *; }

# Alternative package name (org.mediasoup) - keep for safety
-keep class org.mediasoup.** { *; }
-keepclassmembers class org.mediasoup.** { *; }

# ===== Native Methods (JNI) =====
# Preserve all native method signatures (called from Java/Kotlin to C++)

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# ===== Hilt Dependency Injection =====
# Hilt uses code generation that R8 may strip

-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ===== Gson Serialization =====
# Gson uses reflection to read @SerializedName annotations at runtime.
# R8 must preserve Gson's annotation classes AND annotated fields/enums.

-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.voiceping.android.data.model.** { *; }
-keep class com.voiceping.android.data.network.dto.** { *; }
-keep class com.voiceping.android.domain.model.** { *; }

# Preserve @SerializedName on enum constants (Gson enum deserialization)
-keepclassmembers enum * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ===== OkHttp/Okio =====
# Networking layer for signaling

-dontwarn okhttp3.**
-dontwarn okio.**

# ===== Retrofit =====
# API client annotations

-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

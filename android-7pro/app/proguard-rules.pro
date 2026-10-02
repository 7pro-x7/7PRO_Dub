# 7PRO release rules.
# R8 shrinking/obfuscation is enabled for release (isMinifyEnabled = true) so the AAB includes a
# mapping file. These rules protect the reflective / JNI parts of the stack: kotlinx.serialization
# models, Ktor, Supabase, and the native-backed libraries below.

# --- kotlinx.serialization ---------------------------------------------------
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations, AnnotationDefault
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Every @Serializable model in the app keeps its generated serializer.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.rork.pro.data.**$$serializer { *; }
-keepclassmembers class com.rork.pro.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.rork.pro.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Ktor / OkHttp -----------------------------------------------------------
-keepclassmembers class io.ktor.** { volatile <fields>; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-dontwarn okhttp3.**
-dontwarn okio.**

# --- Coroutines --------------------------------------------------------------
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# --- Supabase ----------------------------------------------------------------
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**

# --- Compose -----------------------------------------------------------------
-dontwarn androidx.compose.**

# --- Filament (AI Tutor 3D avatar) -------------------------------------------
# Native JNI code calls back into these classes by name.
-keep class com.google.android.filament.** { *; }
-dontwarn com.google.android.filament.**

# --- Native-backed / reflective third-party SDKs ------------------------------
# JNI and reflection look these up by name; keep them intact (R8 still shrinks/obfuscates the
# app's own code, which is what produces the mapping file).
-keepclasseswithmembernames class * { native <methods>; }

# Jitsi Meet + React Native + WebRTC
-keep class org.jitsi.** { *; }
-keep class org.webrtc.** { *; }
-keep class com.facebook.** { *; }
-keep class com.oney.** { *; }
-keep class com.swmansion.** { *; }
-keep class com.reactnativecommunity.** { *; }
-keep class com.horcrux.** { *; }
-keep class io.invertase.** { *; }
-keep class com.brentvatne.** { *; }
-keep class com.giphyreactnativesdk.** { *; }
-dontwarn org.jitsi.**
-dontwarn org.webrtc.**
-dontwarn com.facebook.**
-dontwarn com.giphy.**

# ONNX Runtime (background removal) — JNI
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# MediaPipe (face landmarks) — JNI + protobuf-lite reflection
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**

# Firebase Messaging / AdMob ship their own consumer rules; only silence optional references.
-dontwarn com.google.android.gms.**

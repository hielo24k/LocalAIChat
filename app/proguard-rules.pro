# Default ProGuard rules for MediaPipe and OkHttp

# MediaPipe LLM Inference
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# DataStore
-keep class androidx.datastore.** { *; }

// Top-level build file.
// AGP 9.0+ has Kotlin built-in — do NOT apply kotlin-android separately (causes conflict).
// kotlin.plugin.compose IS still required for Compose with Kotlin 2.x.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

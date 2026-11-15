plugins {
    alias(libs.plugins.android.application) apply false
    // kotlin-android is auto-applied by AGP 9.x per-module; the version is
    // still declared here so the resolution catalog entry stays alive.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

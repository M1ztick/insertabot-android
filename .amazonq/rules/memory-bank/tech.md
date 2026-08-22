# InsertaBot Android — Tech

## Language & Runtime
- Kotlin 2.0.21
- JVM target: 17 (source + target compatibility)
- Android minSdk 26 (Android 8.0), targetSdk/compileSdk 35

## Build System
- Gradle 8.11.1 with Kotlin DSL (`build.gradle.kts`)
- Android Gradle Plugin 8.7.3
- Version catalog: `gradle/libs.versions.toml`
- Single module: `:app`

## Key Dependencies

| Library | Version | Purpose |
|---|---|---|
| Compose BOM | 2024.12.01 | Compose UI, Material3, tooling |
| Activity Compose | 1.10.0 | `ComponentActivity` + Compose integration |
| Lifecycle (ViewModel + Runtime) | 2.8.7 | `ViewModel`, `collectAsStateWithLifecycle` |
| Navigation Compose | 2.8.5 | (available; nav currently handled by enum state) |
| DataStore Preferences | 1.1.1 | On-device key-value persistence |
| Material Icons Extended | BOM | Outlined icon set |
| AndroidX Biometric | 1.2.0-alpha05 | Optional auth gate |
| OkHttp | 4.12.0 | HTTP client + WebSocket |
| kotlinx.serialization JSON | 1.7.3 | JSON encode/decode for DTOs |
| kotlinx.coroutines Android | 1.9.0 | Coroutine dispatcher + Flow |

## Kotlin Compiler Plugins
- `kotlin.plugin.compose` — Compose compiler
- `kotlin.plugin.serialization` — `@Serializable` support

## Build Features
- `compose = true`
- `buildConfig = true`

## Common Commands
```bash
# Debug build
./gradlew :app:assembleDebug

# Install on connected device
adb install app/build/outputs/apk/debug/app-debug.apk

# Run all checks
./gradlew check

# Clean
./gradlew clean
```

## CI
GitHub Actions workflow at `.github/workflows/android.yml` runs on push/PR.

## Packaging Exclusions
`/META-INF/AL2.0` and `/META-INF/LGPL2.1` excluded to avoid duplicate license file conflicts.

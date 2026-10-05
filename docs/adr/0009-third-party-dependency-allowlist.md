# ADR-0009: third-party dependency allowlist

## Status

Proposed

## Date

2026-10-05

## Context

ADR-0001 established that wakey-sitizen should minimize third-party libraries, using only reputable, secure, and maintained libraries that support Kotlin 2.4. This ADR provides the definitive allowlist with pinned versions, justifications, and hashing requirements.

## Decision Drivers

- **Minimize dependency surface**: Only add a third-party library if self-made code is long, complicated, or high-maintenance.
- **Jetpack ecosystem preferred**: When a library is needed, prefer Jetpack/AndroidX libraries over non-Google alternatives.
- **Pinned versions**: All versions are pinned with digests/hashes per project policy.
- **No GMS**: No Google Play Services dependencies.
- **Kotlin 2.4 compatibility**: All libraries must support Kotlin 2.4.20.

## Decision

### Production dependencies

| Dependency | Group ID | Version | Justification |
|------------|----------|---------|---------------|
| Hilt (Android) | `androidx.hilt` | `hilt-android:1.2.0`, `hilt-compiler:1.2.0` | Dependency injection. Keeps wiring DRY across 15+ classes. Manual DI would be ~500 lines of boilerplate. First-party Jetpack library. |
| Hilt Navigation Compose | `androidx.hilt` | `hilt-navigation-compose:1.2.0` | Integrates Hilt with Jetpack Compose Navigation. Avoids manual navigation graph injection. First-party Jetpack library. |
| DataStore Preferences | `androidx.datastore` | `datastore-preferences:1.1.0` | User config persistence. Handles coroutine-based async serialization, corruption recovery, and migration. ~50 lines of DataStore code is straightforward but DataStore handles edge cases that raw SharedPreferences does not. First-party Jetpack library. |
| WorkManager Runtime | `androidx.work` | `work-runtime-ktx:2.9.0` | Background sync (required by requirements). |
| Navigation Compose | `androidx.navigation` | `navigation-compose:2.8.0` | Navigation between screens. ~100 lines of manual nav is error-prone; Compose Navigation is well-tested and minimal. First-party Jetpack library. |
| Kotlinx Serialization JSON | `org.jetbrains.kotlinx` | `kotlinx-serialization-json:1.7.0` | JSON serialization for the wakey-sitizen API response. First-party Jet/Kotlin ecosystem library. |

### Test dependencies

| Dependency | Group ID | Version | Justification |
|------------|----------|---------|---------------|
| JUnit 4 | `junit` | `junit:4.13.2` | Unit test framework. |
| Kotlin Test | `org.jetbrains.kotlin` | `kotlin-test:2.4.20` | Kotlin-specific test assertions. |
| Kotlinx Coroutines Test | `org.jetbrains.kotlinx` | `kotlinx-coroutines-test:1.9.0` | Coroutine testing utilities (runTest, TestDispatcher). |
| WorkManager Testing | `androidx.work` | `work-testing:2.9.0` | WorkManager test utilities (TestWorkerBuilder, ListenableFutureRule). |
| Arch Core Testing | `androidx.arch.core` | `core-testing:2.2.0` | Lifecycle and Room testing utilities. |
| Turbine | `app.cash.turbine` | `turbine:1.1.0` | Flow testing — verifies emissions in order with assertions. |

### Self-made (zero dependencies)

The following components are self-made, requiring no third-party libraries:

| Component | Approx. lines | Justification for self-made |
|-----------|---------------|----------------------------|
| HTTP client (`java.net.http.HttpClient` wrapper) | ~50 | `java.net.http.HttpClient` is built into Android API 36+. Simple GET requests only. OkHttp or Retrofit would add significant dependency surface for minimal benefit. |
| AlarmManager wrapper | ~100 | Straightforward platform API. No abstraction layer needed. |
| Timezone/date math | ~50 | `java.time` and `android.icu` are built into the platform. |
| Device calendar queries | ~150 | `CalendarContract` is well-documented. Queries are simple projections. |
| Ringtone picker integration | ~30 | `RingtoneManager` is a platform API. |
| JSON response deserialization | ~50 | `kotlinx.serialization` data classes (see above). |
| Wakey-sitizen JSON endpoint (SIT-ICS-Go) | ~30 | Go struct + handler (see ADR-0002). |

### Not allowed (and justified as unnecessary)

| Library | Reason |
|---------|--------|
| Retrofit | `java.net.http.HttpClient` is sufficient for simple GET requests. Retrofit adds ~200KB of dependency for features (interceptors, retry, complex serialization) this app does not need. |
| OkHttp | Same rationale as Retrofit. `java.net.http.HttpClient` is built into the platform. |
| Moshi / Gson | `kotlinx.serialization` is the preferred Kotlin JSON library and integrates with the Kotlin compiler plugin. Moshi and Gson are Java-focused and add unnecessary dependency surface. |
| RxJava | Coroutines are the preferred async model in modern Android. RxJava adds ~500KB for reactive streams that this app does not need. |
| Room | Override state is a handful of key-value pairs. DataStore Preferences is sufficient. Room is overkill. |
| Coil / Glide | No image loading is needed. Material 3 icons and simple composables are used for the UI. |
| LeakCanary | Not included in production builds. Can be added as a debug-only dependency if needed during development. |
| Timber / SLF4J | `android.util.Log` is sufficient. No structured logging is needed. |

### Version pinning

All versions are pinned in `gradle/libs.versions.toml` using the version catalog convention. Per project policy, versions are verified against the official release channels (Maven Central, Google Maven) and pinned with checksums where available.

Example `libs.versions.toml` snippet:

```toml
[versions]
hilt = "1.2.0"
datastore = "1.1.0"
work = "2.9.0"
navigation = "2.8.0"
serialization = "1.7.0"
junit = "4.13.2"
kotlin-test = "2.4.20"
coroutines-test = "1.9.0"
turbine = "1.1.0"

[libraries]
hilt-android = { group = "androidx.hilt", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "androidx.hilt", name = "hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hilt" }
datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work" }
work-testing = { group = "androidx.work", name = "work-testing", version.ref = "work" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "serialization" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
kotlin-test = { group = "org.jetbrains.kotlin", name = "kotlin-test", version.ref = "kotlin-test" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines-test" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
```

## Rationale

### Why Hilt over manual DI

Hilt adds ~200 lines of boilerplate (annotations, modules, components) but keeps the codebase DRY across 15+ classes. Manual DI would require ~500 lines of explicit constructor wiring and factory methods, which is more error-prone and harder to maintain. Hilt is a first-party Jetpack library recommended by Android developer documentation.

### Why kotlinx.serialization over Moshi/Gson

`kotlinx.serialization` is the official Kotlin JSON library, integrates with the Kotlin compiler plugin for compile-time checks, and produces smaller binaries than Gson or Moshi. It is a first-party Jet/Kotlin ecosystem library.

### Why java.net.http.HttpClient over OkHttp/Retrofit

The app makes only 2 simple GET requests per sync (one per discovery mode). `java.net.http.HttpClient` is built into Android API 36+ and handles these requests without any dependency. OkHttp or Retrofit would add significant dependency surface for features (connection pooling, interceptors, complex serialization) this app does not need.

### Why no Room

Override state is a handful of key-value pairs (type, value, expiresAt). DataStore Preferences handles this use case cleanly with ~20 lines of code per serializer. Room would require entity definitions, DAOs, and a database class for data that fits in a few DataStore keys.

## Consequences

### Positive

- **Minimal surface**: 6 production dependencies, all first-party Jetpack or Jet/Kotlin ecosystem libraries.
- **No GMS**: Zero Google Play Services dependencies.
- **Pinned versions**: All versions are pinned in the version catalog, verifiable against official release channels.
- **Self-made where possible**: 6 components are self-made with zero dependencies, totaling ~410 lines of code.

### Negative

- **Limited flexibility**: Using `java.net.http.HttpClient` means no connection pooling, no custom interceptors, and no built-in retry logic. If the app needs these features in the future, a library like OkHttp would need to be added.
- **Hilt compile time**: Hilt's annotation processing adds ~10-20 seconds to build times. This is an accepted tradeoff for the DI benefits.

### Neutral / operational

- **Dependency updates**: When updating a dependency, verify compatibility with Kotlin 2.4.20 and Android API 36+. Update the version in `libs.versions.toml` and run the full test suite.
- **Debug dependencies**: LeakCanary or other development-only tools can be added as `debugImplementation` dependencies without affecting the production allowlist.

## References

- [Hilt](https://developer.android.com/training/dependency-injection/hilt-android) — Jetpack Hilt dependency injection
- [DataStore](https://developer.android.com/topic/arc/data-store) — Jetpack DataStore
- [kotlinx.serialization](https://kotlinlang.org/docs/serialization.html) — Kotlin JSON serialization
- [java.net.http.HttpClient](https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpClient.html) — HTTP client available on Android API 36+

# wakey-sitizen — Agent Instructions

## Project facts
- **Single-module Android app**: `app/` is the only module. Root `build.gradle.kts` declares plugins with `apply false`.
- **Kotlin 2.4.20 / AGP 9.4.1 / Compose BOM 2026.01.00**. All versions are pinned in `gradle/libs.versions.toml` — never hardcode a version in code.
- **minSdk 36, targetSdk 37** (Android 16/17 only). No `Build.VERSION` compatibility branches needed.
- **Java 17** via `mise.toml` (`temurin-17`). The Gradle daemon needs `Xmx3072m` (already set in `gradle.properties` — do not lower it; `mergeExtDexDebug` OOMs at 512m).

## Quick commands
| Command | What it does |
|---------|-------------|
| `./gradlew test` | JVM unit tests (`app/src/test/`) |
| `./gradlew connectedAndroidTest` | Instrumented tests (`app/src/androidTest/`) on connected emulator/device |
| `./gradlew app:lint` | Lint check |
| `./gradlew app:build` | Full build (compile + test + lint) |
| `./gradlew app:assembleDebug` | Build debug APK |
| `./gradlew app:installDebug` | Install debug APK on connected device |

## Architecture at a glance
```
app/src/main/java/com/cielshieru/wakey/
  App.kt                          — @HiltAndroidApp, WorkManager config, notification channels
  ui/MainActivity.kt              — single Activity, @AndroidEntryPoint
  ui/navigation/WakeyNavHost.kt   — NavHost (routes in Route.kt)
  ui/home/HomeViewModel.kt        — Home screen VM (StateFlow + Turbine tests)
  ui/settings/SettingsViewModel.kt
  domain/                         — pure business logic, no Android deps
    port/                         — interfaces (Clock, AlarmRepository, TimetableRepository, etc.)
    AlarmComputer.kt              — first-event-of-day + offset + repeat → AlarmSchedule
    BedtimeComputer.kt            — bedtime reminder from first alarm
    AdjustForExternalEvents.kt    — device-calendar pre-event adjustment
    OverrideResolver.kt           — snooze/skip/custom overrides
    model/                        — AlarmConfig, AlarmSchedule, Override, etc.
  usecase/                        — orchestration layer (ComputeAlarmsUseCase, SyncUseCase, ApplyOverrideUseCase)
  data/
    repository/                   — AlarmRepositoryImpl, TimetableRepositoryImpl (DataStore-backed)
    network/TimetableClient.kt    — fetches JSON from configured SIT-ICS-Go base URL
    store/                        — AlarmConfigStore, AlarmStore, TimetableStore, OverrideStore (all DataStore)
    model/WakeyResponse.kt        — deserialized timetable JSON
  android/                        — Android platform bridge
    AlarmService.kt               — setExactAndAllowWhileIdle / cancel via AlarmManager
    AlarmReceiver.kt              — BroadcastReceiver that fires alarms
    BootReceiver.kt               — restores alarms after reboot (ADR-0007)
    NotificationService.kt        — notification channels + bedtime reminders
  worker/SyncWorker.kt            — 12h WorkManager periodic sync
  di/AppModule.kt                 — Hilt @Provides (singleton scope)
```

## Key conventions
- **Dependency Injection**: All dependencies enter via Hilt `@Module @Provides` in `di/AppModule.kt`. Domain interfaces live in `domain/port/`. Implementations in `data/repository/` and `data/store/`.
- **KSP**: Hilt compiler (`ksp(libs.hilt.compiler)`) must run after KSP plugin is applied. If adding new `@Inject`/`@Module` code, verify KSP is configured.
- **Testing**:
  - JVM unit tests go in `app/src/test/kotlin/`. Use `FakeClock` (in `fakes/`) for time-dependent code — never use `System.currentTimeMillis()`.
  - Instrumented tests go in `app/src/androidTest/kotlin/`.
  - Flow testing uses Turbine (`app.cash.turbine:turbine`).
  - Coroutine testing uses `kotlinx.coroutines.test` `runTest`.
- **ADRs**: All architectural decisions are in `docs/adr/`. They are the source of truth when code is ambiguous. New decisions get a new numbered ADR.
- **Network**: The only allowed network destination is the SIT-ICS-Go base URL configured by the user. Do not add other hosts or endpoints outside that server. Status integration calls only `/status` and `/refresh` on the validated saved URL, disables redirects, and is shown only when the persisted discovery mode is `RemoteServer`.
- **No GMS**: The app must never depend on Google Play Services.

## Gotchas
- **`gradle.properties` JVM args are mandatory**: `org.gradle.jvmargs=-Xmx3072m -XX:MaxMetaspaceSize=1024m`. Lowering this causes `mergeExtDexDebug` OOMs with Hilt + Compose + Room + Work + DataStore.
- **`FAIL_ON_PROJECT_REPOS`**: `settings.gradle.kts` sets `repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)`. Do not add `maven { ... }` to module-level `build.gradle.kts` — use the version catalog.
- **Release builds have minify enabled**: ProGuard rules live in `app/proguard-rules.pro`. New code that uses reflection or keeps classes must be added there.
- **DataStore is the persistence layer**: All config and alarm state flows through `androidx.datastore.preferences.core.Preferences`. There is no SQLite/Room.

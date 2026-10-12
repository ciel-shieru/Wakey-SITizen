# ADR-0017: Centralized debug logging for debug mode

## Status

Proposed

## Date

2026-10-12

## Context

ADR-0012 established Debug Mode with a "Debug Mode" toggle and "Target Date Selector" in Settings. It also specified that `SyncWorker` should emit diagnostic logs (course codes, event counts, computed schedules, bedtime results) using `Log.d()` with the tag `"WakeyDebug"` when Debug Mode is enabled.

However, the current logging scope is limited to `SyncWorker` only. When Debug Mode is active, there is insufficient logging across other critical paths:

- **AlarmService**: No visibility into which alarms are set, cancelled, or reprogrammed.
- **Bedtime computation**: No logging of bedtime calculation inputs and outputs.
- **Home screen**: No visibility into the configuration values that `HomeViewModel` is operating with.
- **Event discovery**: No structured logging of discovered events (source, title, course code, kind, time, location).

This creates a gap: developers and power users cannot fully trace the app's behavior during debug sessions. For example, if alarms are not firing as expected in debug mode, there is no way to see whether `AlarmService` received the correct trigger times or whether `AlarmComputer` produced the expected schedule.

The requirement is to introduce a centralized logging mechanism that provides comprehensive, debug-mode-gated logging across all critical app paths — discovery, events, alarm computation, alarm lifecycle, bedtime computation, and configuration snapshots.

## Decision Drivers

- **Privacy by default**: Debug logs contain course codes, event details, and potentially identifiable timetable data. They must never appear when Debug Mode is off.
- **Zero overhead when disabled**: When Debug Mode is off, the logging methods must be completely optimized away — no method calls, no branch evaluation.
- **Consistent log tag**: All debug logs must use the `"WakeyDebug"` tag established in ADR-0012 for `logcat` filtering.
- **No third-party dependencies**: ADR-0009 explicitly rejected Timber and SLF4J in favor of `android.util.Log`. This decision must be preserved.
- **Centralized log format**: Log message format should be centralized rather than duplicated at each call site, making it easier to update or filter logs consistently.
- **Gated by a single source of truth**: The debug mode state should be read from a single interface (`DebugConfigProvider`) that wraps the `DebugConfigRepository`, keeping the logging infrastructure decoupled from the data layer.
- **Minimal code changes**: The logging infrastructure should require minimal changes to existing code paths — ideally just adding method calls with structured parameters rather than scattering inline `Log.d()` calls.

## Considered Options

### Option A: Centralized `DebugLogger` class + `DebugConfigProvider` interface (Selected)

Create a `DebugLogger` class in the `domain/` package with static-style logging methods (e.g., `logDiscoveryMode()`, `logEvent()`, `logAlarmSet()`, `logBedtimeComputed()`). Each method begins with `if (!debugModeEnabled) return`, ensuring the Kotlin compiler optimizes away the entire method body when debug mode is off. A `DebugConfigProvider` interface in `domain/port/` provides `debugModeEnabled: Boolean` read from `DebugConfigRepository`. `DebugConfigProvider` is provided as `@Singleton` via Hilt. `DebugLogger` is injected into `SyncWorker`, `AlarmService`, `App.kt`, and `HomeViewModel`.

### Option B: Inline `Log.d()` calls at each call site

Add `if (debugModeEnabled) Log.d("WakeyDebug", "...")` at every call site that needs logging. This avoids introducing new classes but duplicates the debug-gate check everywhere and scatters log format logic across the codebase.

### Option C: ADR-style logger with log levels

Create a logger with levels (DEBUG, INFO, WARN, ERROR) and use `android.util.Log` internally. This provides more structure but adds complexity (level checking, message formatting based on level) that is unnecessary for a debug-mode-only logger where all output is at DEBUG level.

### Option D: Rely on Android's built-in debug logging

Use Android's native debugging facilities (e.g., `Debug.startMethodTracing()`, `StrictMode`) without custom logging. These tools do not provide the structured, categorized output needed to trace specific app behaviors like alarm computation or event discovery.

## Decision

We will choose Option A.

A `DebugLogger` class in the `domain/` package will provide centralized logging methods, each gated by an early `if (!debugModeEnabled) return` guard. A `DebugConfigProvider` interface in `domain/port/` will expose `debugModeEnabled: Boolean` and be provided as a `@Singleton` via Hilt in `di/AppModule.kt`.

`DebugLogger` will be injected (via constructor injection) into the following components:
- `SyncWorker` — logs discovery mode, events, alarm calculations, repeat generation, alarms set/cancelled, bedtime calculations.
- `AlarmService` — logs alarms set and alarms cancelled.
- `App.kt` — logs full app configuration (AlarmConfig + DebugConfig) when the app opens in the foreground.
- `HomeViewModel` — logs full app configuration on init (complementary to `App.kt`'s foreground detection).

The `AlarmService` and `SyncWorker` constructors will gain a `DebugConfigProvider` parameter (in addition to any existing dependencies). The `DebugLogger` itself will also receive `DebugConfigProvider` to read the debug mode flag.

## Rationale

### Why centralized `DebugLogger` over inline `Log.d()` calls (reject Option B)

Inline logging at each call site duplicates the `if (debugModeEnabled)` guard in 15+ locations. It also scatters log message formatting across the codebase, making it difficult to maintain consistent output or update the format later. A centralized logger:
- Centralizes the debug-gate check in one place (the logger constructor method).
- Centralizes log message formatting, making it easy to update or add fields.
- Reduces the risk of forgetting the debug-gate check at a call site.
- Makes it trivial to add new log categories (e.g., `logOverrideApplied()`) without touching existing call sites.

### Why early-return guard over log-level checking (reject Option C)

ADR-0009 already rejected ADR-style loggers with levels in favor of plain `android.util.Log`. This ADR must not reverse that decision. An early `if (!debugModeEnabled) return` is simpler than a level-checking system and provides the same optimization benefit — when debug mode is off, the entire method body is unreachable code that the Kotlin compiler optimizes away. There is no need for log levels in a debug-mode-only logger where all output is at DEBUG level.

### Why `DebugConfigProvider` interface over direct `DebugConfigRepository` dependency

Injecting `DebugConfigRepository` directly into `DebugLogger` would couple the logging infrastructure to the data layer. By introducing a thin `DebugConfigProvider` interface in `domain/port/`, the domain layer (where `DebugLogger` lives) remains decoupled from the data layer. This is consistent with the existing architecture where domain interfaces (`Clock`, `AlarmRepository`, etc.) live in `domain/port/` and implementations live in `data/`.

### Why `DebugLogger` in `domain/` package

The logging methods log domain-level concepts (events, alarm schedules, bedtime calculations) rather than Android-specific or data-layer concepts. Placing `DebugLogger` in `domain/` keeps it aligned with other domain utilities like `AlarmComputer` and `BedtimeComputer`. It also avoids coupling the logging infrastructure to Android APIs — `DebugLogger` only depends on `DebugConfigProvider` (a domain interface) and uses `android.util.Log` which is available on the Android platform.

### Why inject into `App.kt` and `HomeViewModel` for full config logging

Logging the complete `AlarmConfig` + `DebugConfig` values provides a snapshot of the app's state at key moments:
- `App.kt.onCreate()`: Logs when the app process starts and comes to the foreground. This is useful for understanding the initial state after a reboot (ADR-0007) or after the app was killed by the system.
- `HomeViewModel` init block: Logs when the Home screen is first displayed, which is the user's primary interaction point. This helps correlate UI behavior with configuration.

Both locations use foreground app open detection to avoid logging on every configuration change (which would produce excessive log volume).

### Why no logging in `AlarmReceiver`

`AlarmReceiver` is a `BroadcastReceiver` that fires alarms. At this point, the alarm has already been set by `AlarmService` and the trigger time is determined by `AlarmManager`. Logging here would not provide additional diagnostic value beyond what `AlarmService.logAlarmSet()` already records. The `AlarmReceiver`'s job is minimal (show notification), and any issues there would be visible in the notification itself or in `logcat` via the notification system.

### Why no logging in `OverrideResolver` or override-related classes

Override application is a pure computation (given an alarm and an override, compute the adjusted trigger time). The result of override computation is already captured in the alarm set/cancelled logs from `AlarmService`. Adding separate override logs would duplicate information without adding new diagnostic value.

## Consequences

### Positive

- **Comprehensive visibility**: Developers and power users get full visibility into app behavior when Debug Mode is on — discovery, events, calculations, alarm lifecycle, and configuration.
- **Zero overhead when disabled**: Early-return guards ensure the Kotlin compiler optimizes away all logging method bodies when debug mode is off. No method calls, no branches, no overhead.
- **Consistent log format**: All debug logs use the same `"WakeyDebug"` tag and centralized formatting, making them easy to filter and read in `logcat`.
- **Extensible**: New log categories can be added by adding methods to `DebugLogger` without touching existing call sites.
- **Domain-layer alignment**: `DebugLogger` lives in `domain/` alongside other domain utilities, following the existing architectural convention.

### Negative

- **Slightly larger binary**: Debug logging methods exist in the compiled class file even when they are no-ops at runtime. The size impact is negligible (a few hundred bytes per method).
- **High log volume in debug mode**: When Debug Mode is on, the app will emit many log lines per sync cycle. This is intentional for debugging but may overwhelm `logcat` buffers. Users should filter by the `"WakeyDebug"` tag.
- **New DI binding**: `DebugConfigProvider` requires a new `@Module @Provides` binding in `AppModule.kt`, adding one line to the module.
- **Constructor signature changes**: `AlarmService` and `SyncWorker` constructors gain a `DebugConfigProvider` parameter, requiring updates to their instantiation sites.

### Neutral / operational

- **No new dependencies**: `DebugLogger` uses only `android.util.Log` (platform API) and `DebugConfigProvider` (domain interface). No third-party libraries added.
- **No ProGuard changes**: No reflection or class-keeping rules needed. `DebugLogger` is not used via reflection.
- **No DataStore schema changes**: `DebugConfigProvider` reads from the existing `wakey_debug_config` DataStore established in ADR-0012.
- **Testing**: Existing `FakeClock` and test infrastructure can be reused. `DebugConfigProvider` can be faked by returning a fixed `debugModeEnabled` value for test cases.

## Alternatives Considered

### Option B: Inline `Log.d()` calls at each call site

Adding `if (debugModeEnabled) Log.d("WakeyDebug", "...")` at every call site is the simplest approach in terms of code surface — no new classes needed. However, it:
- Duplicates the debug-gate check in 15+ locations.
- Scatters log message formatting across the codebase.
- Makes it easy to forget the debug-gate check at a call site (privacy risk).
- Makes it harder to update log format consistently.

Rejected in favor of Option A for centralized control and reduced duplication.

### Option C: ADR-style logger with log levels

A logger with levels (DEBUG, INFO, WARN, ERROR) would provide more structure and allow runtime log level configuration. However:
- ADR-0009 already rejected structured logging frameworks in favor of plain `android.util.Log`.
- This ADR must not reverse that decision.
- A debug-mode-only logger does not need levels — all output is at DEBUG level.
- Level checking adds complexity (level comparison, message formatting based on level) without clear benefit.

Rejected for simplicity and consistency with ADR-0009.

### Option D: Rely on Android's built-in debug logging

Android provides `Debug.startMethodTracing()`, `StrictMode`, and other debugging facilities. However:
- These tools do not provide structured, categorized log output.
- They cannot log domain-level concepts (events, alarm schedules, bedtime calculations).
- Method tracing produces binary trace files that are difficult to read and not filterable by tag.

Rejected because it does not provide the structured, categorized output needed for debugging this app.

## Follow-Ups

- Create `DebugConfigProvider` interface in `domain/port/` with `debugModeEnabled: Boolean` property.
- Create `DebugLogger` class in `domain/` with logging methods for all categories (discovery, events, alarms, bedtime, config).
- Add `@Module @Provides` binding for `DebugConfigProvider` in `di/AppModule.kt`.
- Inject `DebugLogger` into `SyncWorker`, `AlarmService`, `App.kt`, and `HomeViewModel`.
- Add `DebugConfigProvider` parameter to `AlarmService` and `SyncWorker` constructors.
- Add logging calls to all critical paths (discovery, events, alarm computation, alarm lifecycle, bedtime, config).
- Add unit tests for `DebugLogger` verifying that log methods are no-ops when debug mode is off.
- Add unit tests for `DebugConfigProvider` verifying correct delegation to `DebugConfigRepository`.

## References

- ADR-0003: alarm scheduling policy — defines `AlarmComputer.computeSchedule()` and the alarm pipeline logged by `DebugLogger`.
- ADR-0004: bedtime reminder algorithm — defines `BedtimeComputer.compute()` and bedtime logging.
- ADR-0007: background sync mechanism and boot recovery — defines `SyncWorker.doWork()` pipeline, the primary target for debug logging.
- ADR-0009: third-party dependency allowlist — explicitly rejects Timber and SLF4J; mandates `android.util.Log`.
- ADR-0012: debug mode and target date override — establishes `"WakeyDebug"` log tag, `DebugConfigRepository`, and initial `SyncWorker` diagnostic logging scope.
- ADR-0006: manual override model — defines override computation (no separate logging needed; results captured in alarm set/cancelled logs).

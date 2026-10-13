# ADR-0012: Debug mode and target date override for alarm simulation

## Status

Proposed

## Date

2026-10-11

## Context

wakey-sitizen computes alarms based on the current date's timetable events. When a user changes their timetable or wants to test how alarms behave for a different day, they must wait for the real day to arrive. This creates a gap: developers cannot easily verify alarm computation logic, and power users cannot preview alarms for days they cannot yet experience (e.g., "what alarms would fire tomorrow if my schedule had these classes?").

ADR-0003 established the alarm scheduling policy, ADR-0004 the bedtime reminder algorithm, and ADR-0007 the background sync mechanism. None of these address time-based simulation or diagnostic logging.

The requirement is to add a developer/debugger tool that:
1. Provides a "Debug Mode" toggle in Settings (off by default).
2. When enabled, emits verbose diagnostic logging in `SyncWorker` (course codes, event counts, schedule details).
3. Adds a "Target Date Selector" date picker in Settings (visible only when Debug Mode is on).
4. Overrides the "current date" used for alarm computation to simulate alarms for a different day.
5. Makes bedtime notifications use the target date's night rather than the current night.

This is a development/debugging aid, not a user-facing feature. It must be opt-in and clearly gated.

## Decision Drivers

- **Privacy by default**: Debug logs contain course codes, event details, and potentially identifiable timetable data. They must never appear when Debug Mode is off.
- **No network impact**: The target date override must NOT affect timetable fetching — the app can only fetch events that already exist in the timetable JSON or calendar. Fetching "future events" is impossible because they may not yet be published.
- **Isolation from production config**: Debug state must be stored separately from production config (`AlarmConfig`) to avoid polluting user settings with debug artifacts.
- **Clear audit trail**: All debug logging must use a distinct log tag so it can be filtered independently in `logcat`.
- **Minimal surface area**: Debug features should be discoverable only by users who explicitly enable Debug Mode. They must not clutter the Settings UI for regular users.

## Considered Options

### Option A: Separate DebugConfigRepository + DataStore (Selected)

Create a new `DebugConfigRepository` interface and `DebugConfigStore` backed by a separate DataStore (`wakey_debug_config`). The repository exposes `isDebugEnabled: Flow<Boolean>` and `targetDateOverrideMillis: Flow<Long?>`. Debug Mode defaults to `false`. Target date is stored as `Long` (epoch millis). `AlarmComputer.computeSchedule()` accepts an optional `targetDateOverride: LocalDate?` parameter. `SyncWorker` reads debug config at the start of `doWork()` and passes the override through the pipeline. All debug logging uses `Log.d()` with tag `"WakeyDebug"`.

### Option B: Extend AlarmConfig with debug fields

Add `debugMode: Boolean` and `targetDateMillis: Long?` fields to `AlarmConfig`. Store them in the same DataStore as production config. Pass through `SettingsViewModel` and `AlarmRepository`.

### Option C: Build-time flag + hardcoded log level

Use a Gradle build variant (e.g., `debug`) that enables verbose logging and target date simulation at compile time. No runtime toggle.

### Option D: Hidden debug activity with ADB trigger

Add a hidden Activity that is only accessible via ADB intent. Debug Mode is enabled by sending a broadcast via `am broadcast`. No Settings UI changes.

## Decision

We will choose Option A.

A dedicated `DebugConfigRepository` interface and `DebugConfigStore` backed by a separate DataStore instance (`wakey_debug_config`) will manage debug state independently from production config. The repository exposes two flows: `isDebugEnabled: Flow<Boolean>` (default `false`) and `targetDateOverrideMillis: Flow<Long?>` (default `null`).

`AlarmComputer.computeSchedule()` will accept an optional `targetDateOverride: LocalDate?` parameter. When non-null, it replaces the date derived from `clock.now()` for alarm schedule computation (determining which day's events to use as "today" and "tomorrow").

`SyncWorker.doWork()` will read debug config at the start of execution. When Debug Mode is enabled, it will emit diagnostic logs (course codes, event counts, computed schedules, bedtime results) using `Log.d()` with tag `"WakeyDebug"`.

The target date override affects schedule computation and bedtime computation but does NOT affect timetable fetching (calendar queries or JSON fetches always use the real current date).

The target date override does NOT affect manual override expiration logic — overrides expire based on real time, not the target date.

The Settings UI will conditionally render a "Debug Mode" toggle and a "Target Date" date picker (using Material3 `DatePickerDialog`) only when Debug Mode is enabled.

## Rationale

### Why separate DebugConfigRepository instead of extending AlarmConfig (reject Option B)

`AlarmConfig` represents user-facing, production settings that are synced across the app's features (alarm computation, bedtime, external events, overrides). Mixing debug state into this model would:
- Pollute the production data schema with debug-only fields.
- Risk debug state leaking into sync, backup, or migration workflows.
- Make it harder to audit what data is "production" vs "debug."

A separate DataStore (`wakey_debug_config`) keeps debug state isolated. It is never synced, never exported, and never included in crash reports or diagnostics sent to developers (unless explicitly enabled).

### Why Long (millis) for target date storage instead of LocalDate serialization (Option B alternative)

DataStore preferences store values as strings, primitives, or byte arrays. `LocalDate` would require custom serialization (e.g., ISO-8601 string `"2026-10-15"`). Storing as `Long` (epoch millis) avoids serialization complexity and is consistent with how other date values are stored in the app (e.g., `last_sync_time` in `SyncWorker`, override expiry timestamps in `ManualOverride`). The value is converted to `LocalDate` at read time using the device's timezone.

### Why Debug Mode defaults to OFF (Option A detail)

Debug logs contain course codes, event titles, locations, and timetable structure — information that could be considered private. Defaulting to OFF ensures that:
- No debug logs are emitted by accident.
- Users who enable Debug Mode explicitly acknowledge the logging behavior.
- The feature is discoverable only through the Settings UI toggle.

### Why target date does NOT affect calendar fetching (Option A constraint)

Calendar events for future days may not yet exist in the device calendar (e.g., a professor has not yet added next week's lectures). Fetching with a target date override would produce incomplete or stale data, making the alarm simulation unreliable. By keeping calendar fetching on the real current date, the app always uses the most complete data available. The target date only shifts which day's events are treated as "today" for alarm computation purposes.

### Why target date does NOT affect override expiration (Option A constraint)

Manual overrides (ADR-0006) are real-world actions that expire based on wall-clock time. If an override was set for "snooze until 8:30 AM" and the user simulates "tomorrow" in Debug Mode, the override should still expire at its real-world expiry time. Using the target date for expiration would create a disconnect between what the user sees (simulated alarms) and what actually happens (real alarms firing or not).

### Why separate log tag "WakeyDebug" (Option A detail)

The app already uses `"SyncWorker"` for production logging (ADR-0007). Using a distinct `"WakeyDebug"` tag allows developers to filter debug logs independently:
```
adb shell logcat -s WakeyDebug
```
This prevents debug logs from mixing with production logs and makes it easy to silence them when needed.

### Why not Option C (Build-time flag)

Build-time flags cannot be toggled at runtime without rebuilding. This is inconvenient for testing different configurations on the same device instance. A runtime toggle allows quick iteration without redeployment.

### Why not Option D (Hidden ADB trigger)

While this approach is secure (no Settings UI changes), it is not discoverable and requires ADB access. Most developers and power users do not have ADB readily available. A Settings UI toggle is more accessible while still being gated behind an explicit opt-in.

## Consequences

### Positive

- **Isolated debug state**: Debug config is stored separately from production config, preventing accidental leakage.
- **Privacy-preserving by default**: Debug Mode is off by default; logs are never emitted unless explicitly enabled.
- **Filterable logs**: The `"WakeyDebug"` log tag allows targeted filtering in `logcat`.
- **Reliable simulation**: Target date affects computation but not fetching, ensuring data completeness.
- **Real-world override correctness**: Override expiration uses real time, preventing disconnects between simulated and actual behavior.
- **Discoverable but gated**: Debug features are visible in Settings only when Debug Mode is enabled, avoiding clutter for regular users.

### Negative

- **Additional code surface**: `DebugConfigRepository`, `DebugConfigStore`, `DebugConfigViewModel`, and Settings UI additions increase the codebase size.
- **Separate DataStore instance**: Requires an additional `DataStore<Preferences>` provider in `AppModule.kt`, increasing DI complexity.
- **Potential for confusion**: Users may not understand that target date does not affect fetching or override expiration. Clear UI labels and tooltips are required.
- **Testing complexity**: Debug Mode paths must be tested to ensure they do not affect production behavior when Debug Mode is off.

### Neutral / operational

- **DataStore key strings**: `"debug_enabled"` (Boolean) and `"target_date_millis"` (Long, nullable) in the `wakey_debug_config` DataStore.
- **No migration needed**: New DataStore — no existing data to migrate.
- **ProGuard**: No reflection or keep rules needed for `DebugConfigRepository` since it is not used via reflection.
- **`AlarmComputer` signature change**: Adding `targetDateOverride: LocalDate? = null` to `computeSchedule()` is backward-compatible (default parameter).

## Alternatives Considered

### Option B: Extend AlarmConfig with debug fields

Adding `debugMode` and `targetDateMillis` to `AlarmConfig` would be simpler to implement (no new repository or DataStore needed). However, it mixes debug state into the production config model, risking leakage into sync/backup/migration workflows and polluting the user-visible settings schema. Rejected in favor of Option A.

### Option C: Build-time flag + hardcoded log level

A `debug` Gradle build variant would enable verbose logging at compile time. This is secure (no runtime toggle) but inconvenient for testing — every configuration change requires a rebuild. Rejected in favor of Option A's runtime toggle.

### Option D: Hidden debug activity with ADB trigger

A hidden Activity accessible only via `am broadcast` intent is the most secure approach (no Settings UI changes, no persistent debug state). However, it is not discoverable and requires ADB access. Rejected in favor of Option A's Settings UI toggle, which is more accessible while still being gated behind an explicit opt-in.

## Follow-Ups

- Implementation of `DebugConfigRepository`, `DebugConfigStore`, and Hilt `@Module @Provides` bindings.
- Settings UI: Debug Mode toggle section and Target Date date picker (gated by debug mode).
- `SyncWorker` diagnostic logging additions (course codes, event counts, schedule details, bedtime results).
- `AlarmComputer.computeSchedule()` parameter addition and target-date logic.
- `BedtimeComputer.compute()` target-date awareness (pass computed "current date" through the pipeline).
- Unit tests for Debug Mode paths (ensure production behavior is unaffected when Debug Mode is off).
- ProGuard rules if any debug classes are referenced via reflection (unlikely, but verify).
- ADR-0019 narrowed the constraint that "target date does NOT affect timetable fetching" for DeviceCalendar discovery mode — in debug mode with DeviceCalendar, the fetch window now anchors to the target date to avoid date mismatch between fetched events and alarm computation.

## References

- ADR-0003: alarm scheduling policy — defines `AlarmComputer.computeSchedule()` and the alarm pipeline.
- ADR-0004: bedtime reminder algorithm — defines `BedtimeComputer.compute()` and bedtime notification behavior.
- ADR-0006: manual override model — defines override expiration logic (must use real time, not target date).
- ADR-0007: background sync mechanism and boot recovery — defines `SyncWorker.doWork()` pipeline.
- ADR-0008: permissions and privacy manifest — establishes minimal privilege surface; debug logging must respect this principle.
- `app/src/main/java/com/cielshieru/wakey/domain/AlarmComputer.kt` — current `computeSchedule()` signature (no target date parameter yet).
- `app/src/main/java/com/cielshieru/wakey/worker/SyncWorker.kt` — current `doWork()` pipeline (no debug logging yet).
- `app/src/main/java/com/cielshieru/wakey/domain/BedtimeComputer.kt` — current `compute()` signature (uses `now` from caller).
- `app/src/main/java/com/cielshieru/wakey/domain/model/AlarmConfig.kt` — production config model (no debug fields).
- `app/src/main/java/com/cielshieru/wakey/ui/settings/SettingsViewModel.kt` — current settings state (no debug state yet).

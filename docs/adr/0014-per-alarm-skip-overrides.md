# ADR-0014: Per-alarm skip overrides with scheduled alarm list on Home screen

## Status

Accepted

## Date

2026-10-11

## Context

ADR-0006 established three manual override types (SNOOZE, SKIP_TOMORROW, CUSTOM_TIME) that allow users to temporarily modify or suppress upcoming alarms. ADR-0003 defined the alarm scheduling policy using clear-then-set reconciliation. ADR-0007 defined the 12-hour background sync cycle that recomputes and re-arms alarms.

The SKIP_TOMORROW override suppresses all alarms for an entire calendar day. However, students occasionally need to skip only a single alarm — for example, they have a second alarm at 8:30 AM for a lab that they plan to attend but want to sleep through the first alarm at 7:00 AM. Currently, no mechanism exists to skip an individual alarm within a day's alarm set.

Additionally, the Home screen does not display the full list of scheduled alarms. Users see only the next alarm time (in a summary card) and cannot browse upcoming alarms at a glance. This creates uncertainty about when the next alarm fires and which days have alarms set.

## Decision Drivers

- **Per-alarm granularity**: Users must be able to skip individual alarms within a day's set, not just entire days.
- **Consistency with existing override model**: The new skip mechanism should integrate seamlessly with ADR-0006's override system and ADR-0007's sync pipeline.
- **Schedule visibility**: Users should see all scheduled alarms (time, day) on the Home screen without navigating away.
- **Past alarm clarity**: Alarms that have already fired should be visually distinct from upcoming alarms.
- **Minimal surface area**: The change should extend the existing override model rather than duplicating alarm management logic.

## Considered Options

### Option A: New SKIP_ALARM override type with scheduled alarm list (Selected)

Add a `SKIP_ALARM` value to the `OverrideType` enum. Each skip creates a one-time override keyed by the alarm's `triggerAt` instant. The `OverrideResolver` filters matching alarms from the computed schedule during sync. A new `ScheduledAlarm` data class in `HomeViewModel` exposes individual alarms as a `StateFlow<List<ScheduledAlarm>>`. `HomeScreen` renders a `LazyColumn` of `AlarmRow` composables with a skip button for upcoming alarms and a "Past" label for passed alarms.

### Option B: Per-alarm skip via direct AlarmManager cancellation

When the user taps "Skip" on an individual alarm, call `AlarmManager.cancel()` directly on the alarm's `PendingIntent` without creating an override. The alarm is removed from the system immediately and is not restored on the next sync cycle unless the timetable changes.

### Option C: Extend SKIP_TOMORROW to support per-alarm selection

Keep the existing `SKIP_TOMORROW` type but add a per-alarm toggle within a day's alarm set. Toggling a single alarm would create a `SKIP_TOMORROW` override scoped to that specific alarm's index within the day. The "Skip Tomorrow" button would skip all alarms for the next day as before.

### Option D: Dedicated "skip this alarm" FAB on Home screen

Add a floating action button next to each alarm in a new scheduled alarm list section. Tapping it cancels the alarm via `AlarmManager` directly (similar to Option B) but with a confirmation dialog to prevent accidental skips.

## Decision

We will choose Option A.

A new `SKIP_ALARM` enum value will be added to `OverrideType` in `domain/model/Override.kt`. Each skip creates a `ManualOverride` of type `SKIP_ALARM` with the value set to the alarm's `triggerAt` ISO-8601 instant string.

The `OverrideResolver` (`domain/OverrideResolver.kt`) will be extended to handle `SKIP_ALARM` by filtering out any alarm whose `triggerAt` matches the override's stored instant from the computed schedule during sync. Unlike `SKIP_TOMORROW` (which clears all alarms for a day), `SKIP_ALARM` removes only the single matching alarm.

A new `cancelAlarm(requestCode: Int)` method will be added to the `AlarmRepository` interface (`domain/port/AlarmRepository.kt`) and implemented in `AlarmRepositoryImpl` (`data/repository/AlarmRepositoryImpl.kt`). This method delegates to `AlarmService` to cancel a single pending alarm by its `requestCode`, enabling the UI to cancel an already-armed `AlarmManager` alarm when the user skips it.

`HomeViewModel` (`ui/home/HomeViewModel.kt`) will introduce a `ScheduledAlarm` data class representing a single alarm with its display label (time, day), whether it is upcoming or past, and its `requestCode`. The ViewModel will expose a `scheduledAlarms: StateFlow<List<ScheduledAlarm>>` derived from `alarmRepository.schedules`, filtered to show upcoming alarms first followed by past alarms.

`HomeScreen` (`ui/home/HomeScreen.kt`) will render a `LazyColumn` below the existing "Skip Tomorrow" button showing all scheduled alarms. Each `AlarmRow` composable displays the alarm time, day name, and a skip button for upcoming alarms. Past alarms show a "Past" label instead of a skip button.

The sync pipeline (`SyncWorker`, step 5) will automatically apply `SKIP_ALARM` overrides during the `OverrideResolver.applyOverride` call, filtering matching alarms from the schedule before reconciliation. No changes to `SyncWorker` are needed — the existing override application loop handles the new type.

## Rationale

### Why a new override type instead of direct cancellation (reject Option B)

Direct `AlarmManager` cancellation without an override has two problems:

1. **Sync conflict**: The next sync cycle (12-hour periodic or user-triggered) recomputes the full schedule and re-arms all alarms, including the skipped one. The user would see the alarm reappear without understanding why.
2. **No persistent intent**: Without an override stored in DataStore, there is no record that the user intentionally skipped this alarm. If the user later wants to restore the alarm, they cannot — they would need to wait for the timetable to change or manually reapply.

The override model (ADR-0006) exists precisely to solve these problems: overrides are one-time, self-expiring, and respected by the sync pipeline. `SKIP_ALARM` extends this model to a per-alarm granularity.

### Why not extend SKIP_TOMORROW (reject Option C)

`SKIP_TOMORROW` is designed to suppress an entire day's alarms. Adding per-alarm scoping to it would complicate the override model:

- The `value` field would need to encode both "skip all" and "skip specific alarm index" semantics, making parsing ambiguous.
- The priority rules (ADR-0006: CUSTOM_TIME > SNOOZE > SKIP_TOMORROW) would become unclear if a partial skip coexists with a full-day skip.
- The expiration logic (midnight or sync, whichever comes first) works cleanly for "all alarms this day" but is awkward for "this one alarm" — if the user skips only the 8:30 AM alarm, the override conceptually expires when that alarm fires, not at midnight.

A distinct `SKIP_ALARM` type keeps the override model simple and each type's semantics unambiguous.

### Why show both upcoming and past alarms in the list (Option A detail)

Showing only upcoming alarms would hide the full scope of the alarm schedule. Users benefit from seeing:
- Which alarms have already fired today (confirming the schedule is working).
- Which alarms are coming up next.
- That alarms are set for future days.

The "Past" label on passed alarms provides clear visual distinction without requiring the user to mentally track time.

### Why `cancelAlarm(requestCode)` on AlarmRepository instead of AlarmService directly (Option A detail)

The `AlarmRepository` interface (`domain/port/AlarmRepository.kt`) is the domain-layer abstraction for alarm state. Adding `cancelAlarm` here keeps the cancellation logic within the override lifecycle: when a user skips an alarm, the override is created, the alarm is cancelled via `AlarmRepository`, and the sync pipeline will not re-arm it because the override filters it.

If `cancelAlarm` were called directly on `AlarmService`, it would bypass the domain layer and create a coupling between the UI and the Android platform abstraction. The repository layer is the correct boundary for state mutations.

### Why the sync pipeline needs no changes (Option A detail)

The existing override application loop in `SyncWorker` step 5 already handles arbitrary override types through the `OverrideResolver.applyOverride` switch:

```kotlin
val finalSchedules = OverrideResolver.resolve(activeOverrides, now, zone)?.let { resolved ->
    adjustedSchedules.map { schedule ->
        OverrideResolver.applyOverride(resolved, schedule, zone)
    }
} ?: adjustedSchedules
```

Adding a `SKIP_ALARM` branch to `applyOverride` is sufficient — the loop already iterates over all schedules and applies the resolved override. No changes to `SyncWorker`, `SyncUseCase`, or `WorkManager` configuration are needed.

## Consequences

### Positive

- **Per-alarm granularity**: Users can skip individual alarms without suppressing an entire day's schedule.
- **Consistent with existing model**: Uses the same override storage, sync integration, and expiration mechanics as SNOOZE/SKIP_TOMORROW/CUSTOM_TIME.
- **Schedule visibility**: The new alarm list gives users full awareness of upcoming alarms across days.
- **Past alarm clarity**: Passed alarms are visually distinct, reducing confusion about which alarms have already fired.
- **Self-cleaning**: Like other overrides, `SKIP_ALARM` expires at midnight or on the next sync cycle, preventing indefinite state accumulation.
- **No sync pipeline changes**: The existing override application loop in `SyncWorker` handles the new type transparently.

### Negative

- **Override proliferation**: Each skipped alarm creates a separate override entry in DataStore. If a user frequently skips individual alarms, the overrides list grows. This is mitigated by the existing expiration mechanism (midnight or next sync).
- **UI complexity**: The Home screen now has an additional `LazyColumn` section with `AlarmRow` composables. On narrow screens, this adds scrollable content below the quick actions.
- **New interface method**: `cancelAlarm(requestCode)` adds a platform-dependent operation to the domain interface. Tests must mock or fake this behavior.
- **OverrideResolver change**: Adding `SKIP_ALARM` to the `applyOverride` switch increases the surface area of the override application logic. The `resolve` function (which selects a single override) does not need to change because `SKIP_ALARM` is not a day-level override — it is applied per-alarm within `applyOverride`.

### Neutral / operational

- **New enum value**: `SKIP_ALARM` added to `OverrideType` in `domain/model/Override.kt`.
- **New data class**: `ScheduledAlarm` in `ui/home/HomeViewModel.kt` (or a dedicated file) for UI state.
- **New repository method**: `cancelAlarm(requestCode: Int)` on `AlarmRepository` interface and `AlarmRepositoryImpl`.
- **New UI composables**: `AlarmRow` and the `LazyColumn` section in `HomeScreen`.
- **OverrideResolver extension**: New `SKIP_ALARM` branch in `applyOverride` that filters the matching alarm by `triggerAt`.
- **No new dependencies**: Uses existing `WorkManager`, `DataStore`, `AlarmManager`, and Compose primitives.
- **ProGuard**: No reflection or keep rules needed.

## Alternatives Considered

### Option B: Per-alarm skip via direct AlarmManager cancellation

Direct cancellation skips the override system entirely. The alarm is removed from `AlarmManager` immediately but would be re-armed on the next sync cycle, creating a confusing user experience where skipped alarms reappear. Also provides no persistent record of the user's skip intent. Rejected for consistency with ADR-0006 and to avoid sync conflicts.

### Option C: Extend SKIP_TOMORROW to support per-alarm selection

Would require overloading the `SKIP_TOMORROW` type's semantics, complicating parsing, priority rules, and expiration logic. The existing `SKIP_TOMORROW` is a clean "all alarms this day" operation; adding partial-skip capability would make the override model harder to reason about. Rejected for simplicity — a distinct `SKIP_ALARM` type is cleaner.

### Option D: Dedicated "skip this alarm" FAB on Home screen

Similar to Option B (direct cancellation) but with a confirmation dialog. Still suffers from sync conflicts and lacks persistent skip intent. The FAB pattern is also inconsistent with the existing UI (quick actions are FABs, but individual alarm actions should be row-level buttons). Rejected for the same reasons as Option B, plus worse UX consistency.

## Follow-Ups

- Implementation of `SKIP_ALARM` enum value in `OverrideType`.
- Extension of `OverrideResolver.applyOverride` to handle `SKIP_ALARM` by filtering matching alarms.
- Addition of `cancelAlarm(requestCode)` to `AlarmRepository` and `AlarmRepositoryImpl`.
- New `ScheduledAlarm` data class and `scheduledAlarms` StateFlow in `HomeViewModel`.
- `AlarmRow` composable and `LazyColumn` section in `HomeScreen`.
- Unit tests for `OverrideResolver.applyOverride` with `SKIP_ALARM`.
- Unit tests for `HomeViewModel.scheduledAlarms` deriving correct upcoming/past classification.
- Consider whether `resolve()` should include `SKIP_ALARM` in its priority chain (currently it does not — `SKIP_ALARM` is applied per-alarm in `applyOverride`, not as a day-level override).

## References

- ADR-0003: alarm scheduling policy — defines `requestCode` derivation, clear-then-set reconciliation.
- ADR-0006: manual override model — defines `OverrideType`, `ManualOverride`, priority rules, and expiration.
- ADR-0007: background sync mechanism — defines `SyncWorker` pipeline and override application step.
- `app/src/main/java/com/cielshieru/wakey/domain/model/Override.kt` — current `OverrideType` enum (SNOOZE, SKIP_TOMORROW, CUSTOM_TIME).
- `app/src/main/java/com/cielshieru/wakey/domain/OverrideResolver.kt` — current override application logic.
- `app/src/main/java/com/cielshieru/wakey/domain/port/AlarmRepository.kt` — current interface (no `cancelAlarm` yet).
- `app/src/main/java/com/cielshieru/wakey/ui/home/HomeViewModel.kt` — current UI state (no alarm list yet).
- `app/src/main/java/com/cielshieru/wakey/ui/home/HomeScreen.kt` — current UI (no alarm list yet).
- `app/src/main/java/com/cielshieru/wakey/worker/SyncWorker.kt` — current sync pipeline, step 5 override application.
- `app/src/main/java/com/cielshieru/wakey/android/AlarmService.kt` — existing `cancelPending` (private) and `cancelAlarmsForDay` methods.

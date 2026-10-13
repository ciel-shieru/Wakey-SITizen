# ADR-0019: Debug mode target date awareness for DeviceCalendar discovery mode fetch window

## Status

Proposed

## Date

2026-10-13

## Context

ADR-0012 established the debug mode and target date override mechanism. It introduced `DebugConfigRepository` with `targetDateOverrideMillis: Long?`, and `SyncWorker.doWork()` reads this value to derive `targetDateLocal` at line 89-91, which is then passed to `AlarmComputer.computeSchedule()` and `BedtimeComputer.compute()`.

However, ADR-0012 explicitly constrained that the target date override does NOT affect timetable fetching:

> "Calendar events for future days may not yet exist in the device calendar (e.g., a professor has not yet added next week's lectures). Fetching with a target date override would produce incomplete or stale data, making the alarm simulation unreliable. By keeping calendar fetching on the real current date, the app always uses the most complete data available."

This constraint is codified in `SyncWorker.fetchTimetable()` at lines 301-306 (DeviceCalendar branch):

```kotlin
DiscoveryMode.DeviceCalendar -> {
    val today = now.atZone(zone).toLocalDate()  // always real current date
    val daysToFetch = 7
    (0 until daysToFetch).flatMap { offset ->
        calendarEventSource.getEventsForDate(today.plusDays(offset.toLong()), config.selectedCalendarIds)
    }
    ...
}
```

The `now` parameter comes from `clock.now()` and always reflects the real current date, regardless of debug mode state.

When debug mode is active with `targetDateMillis=1790812800000` (2026-10-01), `AlarmComputer` correctly uses the target date to compute alarms for 2026-10-01. But the DeviceCalendar fetch window still queries 2026-10-19 through 2026-10-25 (real current date + 7 days). The result is that `AlarmComputer` produces zero schedules because no calendar events exist on the target date — the events were fetched for a different date entirely.

This makes DeviceCalendar discovery mode unusable for debug mode testing. Users cannot simulate alarms for a specific date when using device calendar as the event source, which is the primary use case for debug mode with DeviceCalendar (testing alarm computation with known calendar events).

## Decision Drivers

- **Debug mode must work with DeviceCalendar**: When debug mode is enabled with a target date, the DeviceCalendar fetch window must anchor to that target date so that `AlarmComputer` receives events from the correct day.
- **Production behavior must remain unchanged**: The real current date fetch window must be preserved for production use (non-debug mode). Debug-mode-specific logic must be strictly gated.
- **Consistency with existing debug pipeline**: `AlarmComputer` already receives `targetDateLocal` from `SyncWorker.doWork()` at line 123. The fetch window should use the same value to avoid the date mismatch.
- **Minimal surface area**: The change should be localized to `SyncWorker.fetchTimetable()` and its signature — no new interfaces, stores, or pipeline stages.
- **RemoteServer mode is unaffected**: RemoteServer mode fetches all events from the JSON API (no date-range query), so only DeviceCalendar mode needs changes.

## Considered Options

### Option A: Pass `targetDateLocal` to `fetchTimetable()` and use it as the anchor date for DeviceCalendar (Selected)

Add a `targetDateOverride: LocalDate?` parameter to `SyncWorker.fetchTimetable()`. When non-null (debug mode active), use it as the anchor date for the DeviceCalendar fetch window instead of `now.atZone(zone).toLocalDate()`. When null, retain the existing behavior.

```kotlin
private suspend fun fetchTimetable(
    config: AlarmConfig,
    now: java.time.Instant,
    zone: java.time.ZoneId,
    targetDateOverride: java.time.LocalDate? = null  // new parameter
): List<TimetableEvent> =
    when (config.discoveryMode) {
        DiscoveryMode.DeviceCalendar -> {
            val anchorDate = targetDateOverride ?: now.atZone(zone).toLocalDate()
            val daysToFetch = 7
            (0 until daysToFetch).flatMap { offset ->
                calendarEventSource.getEventsForDate(anchorDate.plusDays(offset.toLong()), config.selectedCalendarIds)
            }
            ...
        }
        ...
    }
```

Call site in `doWork()` passes `targetDateLocal`:
```kotlin
val events = fetchTimetable(config, now, zone, targetDateLocal)
```

### Option B: Add a separate `debugFetchAnchorDate` parameter to `fetchTimetable()`

Introduce a distinct parameter name (`debugFetchAnchorDate`) that is only non-null when debug mode is active. This makes the debug-mode intent more explicit in the function signature but adds naming overhead and duplicates the concept already expressed by `targetDateLocal`.

### Option C: Pass the full `DebugConfig` to `fetchTimetable()`

Pass `DebugConfig` (or a subset containing `targetDateMillis`) to `fetchTimetable()` so it can independently derive the anchor date. This avoids threading `targetDateLocal` through the call but couples the fetch method to debug config semantics and requires another `Instant.ofEpochMilli()` conversion inside the method.

### Option D: Keep status quo — do not change DeviceCalendar fetch for debug mode

Leave the fetch window anchored to real current date. Users who want to test DeviceCalendar with debug mode must manually create calendar events on the real current date (or shift their device clock). This preserves ADR-0012's original constraint but renders debug mode ineffective for DeviceCalendar testing.

## Decision

We will choose Option A.

The `SyncWorker.fetchTimetable()` method will accept an additional `targetDateOverride: LocalDate?` parameter. When non-null (debug mode active with a `targetDateMillis` configured), the DeviceCalendar branch will use this value as the anchor date for the fetch window instead of deriving the anchor from `now.atZone(zone).toLocalDate()`.

The call site in `SyncWorker.doWork()` already has `targetDateLocal` computed at line 89-91, so it will be passed directly:

```kotlin
val events = fetchTimetable(config, now, zone, targetDateLocal)
```

The RemoteServer branch is unaffected — it does not use the `targetDateOverride` parameter and continues to fetch all events from the JSON API regardless of debug mode state.

When debug mode is off (`targetDateLocal` is `null`), the parameter defaults to `null`, and the existing behavior (`now.atZone(zone).toLocalDate()`) is retained.

## Rationale

### Why pass `targetDateLocal` directly (Option A) instead of a separate parameter or full debug config

`SyncWorker.doWork()` already computes `targetDateLocal` at line 89-91 and passes it to `AlarmComputer.computeSchedule()` at line 123. Passing the same value to `fetchTimetable()` avoids:
- Duplicating the `Instant.ofEpochMilli()...toLocalDate()` conversion (Option C).
- Introducing a semantically identical but differently named parameter (Option B).
- Adding a new parameter whose purpose is already clear from its name — it is the target date override that affects computation, and now also affects fetching.

### Why only affect DeviceCalendar mode

RemoteServer mode fetches all events from the JSON API in a single call (`timetableClient.fetch()`) with no date-range filtering. The entire timetable is returned regardless of the current date, so there is no date mismatch to resolve. Only DeviceCalendar mode uses a date-range query (`getEventsForDate()` for 7 consecutive days), which is where the bug manifests.

### Why retain ADR-0012's constraint for production but override it for debug mode

ADR-0012's rationale (calendar events for future days may not yet exist) applies to production use where a user wants alarms for the real upcoming week. In debug mode, the user explicitly specifies a target date and creates calendar events on that date specifically for testing. The "incomplete data" concern does not apply because:
1. The user controls both the target date and the calendar events.
2. Debug mode is opt-in and clearly labeled as a simulation tool.
3. The alternative (Option D) makes debug mode unusable for DeviceCalendar, which defeats its purpose.

The change is scoped to debug mode only — production behavior is unchanged because `targetDateLocal` is `null` when debug mode is off.

### Why the fetch window remains 7 days even in debug mode

The 7-day window is consistent with the existing alarm scheduling policy (ADR-0003) and provides a reasonable lookahead for alarm computation. Debug mode does not need a different window size — it only needs the window to be anchored to the correct date.

## Consequences

### Positive
- **Debug mode works with DeviceCalendar**: Users can now test alarm computation with simulated calendar events on specific dates, which is the primary use case for debug mode with DeviceCalendar.
- **Date consistency**: `AlarmComputer` and `fetchTimetable()` now operate on the same date in debug mode, eliminating the mismatch that caused zero schedules.
- **Minimal change surface**: Only `SyncWorker.fetchTimetable()` signature and one call site need modification. No new interfaces, stores, or pipeline stages.
- **Production behavior unchanged**: When debug mode is off, `targetDateLocal` is `null` and the existing real-date fetch window is retained.

### Negative
- **ADR-0012 constraint superseded for DeviceCalendar**: ADR-0012's rationale that "calendar events for future days may not yet exist" no longer applies in debug mode. Future readers must understand that this ADR explicitly narrows ADR-0012's constraint for the debug + DeviceCalendar combination.
- **Test impact**: Existing tests that verify DeviceCalendar fetch behavior with `FakeClock` may need updates if they exercise debug mode paths. Tests that rely on the real-date fetch window must ensure debug mode is off.

### Neutral or operational
- **Method signature change**: `fetchTimetable()` gains a `targetDateOverride: LocalDate? = null` parameter. The default value ensures backward compatibility for any existing callers (there are none outside `SyncWorker`).
- **No new DI bindings**: The parameter is supplied by `doWork()` which already has access to `targetDateLocal`.
- **No ProGuard changes**: No reflection or keep rules needed for this change.
- **No DataStore changes**: `DebugConfig` and `targetDateMillis` are already persisted per ADR-0012.

## Alternatives Considered

### Option A: Pass `targetDateLocal` to `fetchTimetable()` (Selected)

Minimal, clear, and consistent with the existing debug pipeline. The same `targetDateLocal` value flows through both `AlarmComputer` and `fetchTimetable()`.

### Option B: Separate `debugFetchAnchorDate` parameter

More explicit about debug intent but introduces a semantically identical parameter with a different name. Adds cognitive overhead without clear benefit. Rejected for simplicity — `targetDateOverride` already conveys the intent.

### Option C: Pass full `DebugConfig` to `fetchTimetable()`

Couples the fetch method to debug config semantics and requires an additional `Instant.ofEpochMilli()` conversion inside the method. The conversion is already done in `doWork()` at line 89-91, so duplicating it is wasteful. Rejected to avoid unnecessary coupling and conversion overhead.

### Option D: Keep status quo

Preserves ADR-0012's constraint but renders debug mode ineffective for DeviceCalendar. Users would need to manually create calendar events on the real current date or shift their device clock. Rejected because it fails the primary requirement of making debug mode usable with DeviceCalendar.

## Follow-Ups

- Add `targetDateOverride: LocalDate? = null` parameter to `SyncWorker.fetchTimetable()`.
- Update DeviceCalendar branch to use `anchorDate = targetDateOverride ?: now.atZone(zone).toLocalDate()`.
- Update call site in `doWork()` to pass `targetDateLocal`.
- Add unit tests for `SyncWorker` verifying DeviceCalendar fetch window anchors to target date in debug mode.
- Add unit tests verifying production behavior (real-date fetch) is unchanged when debug mode is off.
- Update ADR-0012's "Follow-Ups" section to note that the original constraint was narrowed by ADR-0019.

## References

- ADR-0002: ICS discovery and source selection — defines DeviceCalendar discovery mode and event fetching.
- ADR-0003: alarm scheduling policy — defines 7-day alarm lookahead window.
- ADR-0007: background sync mechanism and boot recovery — defines `SyncWorker` pipeline architecture.
- ADR-0012: debug mode and target date override — establishes `targetDateOverrideMillis`, constrains target date from affecting fetching (narrowed by this ADR).
- `app/src/main/java/com/cielshieru/wakey/worker/SyncWorker.kt` — `fetchTimetable()` at lines 280-322, `doWork()` at lines 81-268.
- `app/src/main/java/com/cielshieru/wakey/domain/port/CalendarEventSource.kt` — `getEventsForDate(date: LocalDate, calendarIds: List<String>)` interface.

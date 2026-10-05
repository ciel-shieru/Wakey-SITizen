# ADR-0006: manual override model

## Status

Proposed

## Date

2026-10-05

## Context

ADR-0003 established the alarm scheduling policy based on the first SIT-ICS-Go timetable event of the day. However, students occasionally have schedules outside their timetable (e.g., exam days, study leave, personal appointments) that require manual adjustment of alarms. The manual override feature allows users to temporarily modify or suppress upcoming alarms without disabling the automatic scheduling entirely.

## Decision Drivers

- **Temporary by design**: Overrides should be one-time and self-expiring. The computed schedule should be the default; overrides are exceptions for specific occasions.
- **Simple UI**: Quick actions on the Home screen, not buried in settings.
- **Sync-aware**: Overrides interact with the 12-hour background sync (ADR-0007). Expired overrides are cleared during the next sync.

## Decision

### Override types

Three override types are provided, all one-time and self-expiring:

#### 1. Snooze next alarm

Delays the next wake-up alarm by a user-selected increment. The available increments are:
- 15 minutes
- 30 minutes
- 60 minutes

The override stores the snooze duration and applies it to the next alarm set for the current day. The next sync cycle clears the override and recomputes the schedule from scratch.

#### 2. Skip tomorrow

Suppresses all alarms for the next calendar day. The override is active until:
- The next sync cycle runs (clearing the override), or
- The overridden day passes (midnight).

Whichever comes first, the override is cleared and the computed schedule takes over.

#### 3. Custom alarm time

The user sets a specific time for the next alarm. The repeat pattern (interval and count) is preserved from the computed schedule — only the base time is replaced. For example, if the computed first alarm is 7:00 AM with a 5-minute repeat interval and 6 repeats, and the user sets a custom time of 8:00 AM, the alarms fire at 8:00, 8:05, 8:10, 8:15, 8:20, 8:25.

The override is active until the next sync cycle or until the overridden day passes.

### Storage

Overrides are stored in `DataStore` (not Room) since they are simple key-value pairs:

```kotlin
data class ManualOverride(
    val type: OverrideType,        // SNOOZE, SKIP_TOMORROW, CUSTOM_TIME
    val value: String,              // Serialized override data (e.g., "PT30M" for snooze, "2026-10-06" for skip)
    val expiresAt: Instant,         // When the override expires
    val applied: Boolean = false    // Whether the override has been applied to alarms
)

enum class OverrideType {
    SNOOZE,
    SKIP_TOMORROW,
    CUSTOM_TIME
}
```

The `expiresAt` field is set at override creation time:
- Snooze: `now() + 24 hours` (or until next sync, whichever comes first).
- Skip tomorrow: `nextDayMidnight()`.
- Custom time: `nextDayMidnight()`.

### Sync interaction

During the sync flow (ADR-0007), `ReconcileAlarms` checks for active overrides:
1. Load overrides from DataStore.
2. Filter out expired overrides (`expiresAt < now()`).
3. Apply non-expired overrides to the computed alarm schedule.
4. Clear expired overrides from DataStore.

### UI

Quick-action buttons on the Home screen:
- **"Snooze"** — opens a dialog with 15/30/60 minute options.
- **"Skip tomorrow"** — confirmation dialog, then applies the skip override.
- **"Set custom time"** — opens a time picker, then applies the custom time override.

The Home screen also displays the current override status (e.g., "Alarms snoozed 30 min" or "Tomorrow's alarms skipped") so the user knows the automatic schedule is temporarily overridden.

## Rationale

### Why one-time overrides

The computed schedule is the default behavior. Overrides are for occasional deviations (exam days, personal appointments). Making overrides persistent would require the user to remember to disable them, leading to forgotten overrides that conflict with the regular schedule. One-time overrides that expire automatically are safer.

### Why DataStore instead of Room

Overrides are a handful of key-value pairs. Room is overkill for this use case. DataStore handles the coroutine-based async serialization cleanly and keeps the persistence layer simple.

### Why three override types

These three types cover the most common manual adjustment scenarios:
- **Snooze**: "I'll be fine, just give me a few more minutes."
- **Skip tomorrow**: "No classes tomorrow, I'm sleeping in."
- **Custom time**: "I have an exam at 7 AM instead of my usual 9 AM class."

Additional types (e.g., "disable for N days") are not included because they can be achieved by combining the existing types or by disabling background sync entirely.

## Consequences

### Positive

- **Self-cleaning**: Expired overrides are automatically cleared during sync, preventing stale overrides from affecting the schedule.
- **Simple UI**: Quick actions on the Home screen, not buried in settings.
- **Sync-aware**: Overrides are integrated into the sync flow, ensuring they don't conflict with the computed schedule.

### Negative

- **One-time only**: Users who need multi-day overrides must reapply them each day or disable background sync. This is an accepted tradeoff for simplicity.
- **Override conflicts**: If the user applies multiple overrides (e.g., snooze + custom time), the last one applied takes precedence. The UI prevents conflicting overrides from being active simultaneously.

### Neutral / operational

- **Override priority**: If multiple overrides are active, `CUSTOM_TIME` > `SNOOZE` > `SKIP_TOMORROW`. The UI prevents the user from creating conflicting overrides.
- **UI state**: The Home screen observes the override state via `StateFlow` and updates the display in real time.

## References

- ADR-0003: alarm scheduling policy
- ADR-0007: background sync mechanism
- [DataStore](https://developer.android.com/topic/arc/data-store) — Jetpack DataStore for data persistence

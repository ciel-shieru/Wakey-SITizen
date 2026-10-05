# ADR-0003: alarm scheduling policy

## Status

Proposed

## Date

2026-10-05

## Context

ADR-0001 established that wakey-sitizen manages Android alarms for students based on their SIT-ICS-Go timetable. The alarm scheduling policy determines how timetable events are transformed into concrete alarm times, how repeats are configured, and how the alarm state is reconciled on each sync cycle.

## Decision Drivers

- **Reliable alarm delivery**: `USE_EXACT_ALARM` + `setExactAndAllowWhileIdle()` guarantee precise delivery even in Doze mode.
- **Idempotency**: Each sync must produce a deterministic alarm set, with no stale alarms from previous syncs.
- **User configurability**: Students have different sleep habits and class schedules. Offsets, repeat intervals, and ringtone are user-configurable.
- **Simplicity**: The alarm policy must be deterministic and testable. No complex heuristics or machine learning.

## Decision

### First event of the day

For each calendar day in the future (starting from today):
1. Find the chronologically earliest event whose `DTSTART` falls within that calendar day (in system timezone).
2. If no events exist for that day, no alarms are set for that day.
3. Subsequent events on the same day do not affect the alarm schedule.

### Smart time offset

The user configures an offset in minutes for each event type:
- **Online offset** (default: 60 minutes): applied to events with `event_type == "online"`.
- **Campus offset** (default: 90 minutes): applied to events with `event_type == "campus"`.

The first alarm time is computed as:

```
firstAlarmTime = firstEventDTStart - offset
```

### Repeat pattern

The user configures:
- **Repeat interval** (`repeatInterval`): e.g., 5 minutes. Must be between 1 and 30 minutes.
- **Maximum repeats** (`maxRepeats`): e.g., 6. Must be between 1 and 20.

The alarm set fires at:

```
T, T + interval, T + 2*interval, ..., T + (maxRepeats - 1)*interval
```

Total alarms per day = `maxRepeats`.

### Alarm delivery

Each alarm is set via `AlarmManager.setExactAndAllowWhileIdle()`:
- `PendingIntent` targets `AlarmReceiver` (a `BroadcastReceiver`).
- Each alarm has a unique `requestCode` derived from `dayDate.hashCode() * 1000 + alarmIndex`.
- Same ringtone applied to all alarms in the set (user-selected via `RingtoneManager`, URI persisted in DataStore).

### Idempotent reconciliation

Before setting new alarms for a day:
1. Clear all pending alarms for that day by iterating `requestCode` values from `dayDate * 1000` to `dayDate * 1000 + maxRepeats`.
2. Call `AlarmManager.cancel()` on each `PendingIntent`.
3. Set the computed alarm set.

This clear-then-set approach is simpler than incremental update and guarantees no stale alarms accumulate across syncs.

### Alarm state persistence

The computed alarm schedule is persisted in DataStore so that:
- The UI can display upcoming alarms without waiting for a sync.
- `BootReceiver` (ADR-0007) can restore alarms after reboot.
- The alarm reconciliation knows which alarms were previously set.

## Rationale

### Why first event only

The requirement states "Only set for the first calendar event of the day." This simplifies the policy: the student needs one wake-up time, not one per class. Subsequent events are managed by the student's calendar app or by the repeat pattern (which gives multiple warning rings).

### Why clear-then-set instead of incremental update

Clear-then-set is deterministic and avoids stale alarm accumulation. Incremental update would require tracking which alarms were set, which ones need to be removed, and which ones need to be updated — more code, more edge cases, more bugs.

### Why `setExactAndAllowWhileIdle()` instead of `setExact()`

`setExactAndAllowWhileIdle()` ensures alarms fire even when the device is in Doze mode. A wake-up alarm that fails to fire because the device is sleeping defeats the purpose of the app.

### Why same ringtone for all alarms in the set

The requirement states "same ringtone patterns across the different alarms." Using a single ringtone simplifies the user experience — the student hears the same sound repeated, not a mix of different tones.

## Consequences

### Positive

- **Deterministic**: Given the same timetable and config, the alarm set is always identical.
- **No stale alarms**: Clear-then-set guarantees no accumulation.
- **Reliable delivery**: `setExactAndAllowWhileIdle()` works in Doze mode.
- **Testable**: Each step (first event selection, offset application, repeat generation) is independently testable.

### Negative

- **Single wake time**: Students with very early campus events and later online events get the same wake time. This is by design per the requirement.
- **Reboot recovery needed**: Android clears all `AlarmManager` alarms on reboot. `BootReceiver` + persisted config (ADR-0007) is required to restore them.
- **No per-event customization**: All alarms in the set share the same ringtone, interval, and repeat count.

### Neutral / operational

- **Alarm count cap**: `maxRepeats` is capped at 20 to prevent excessive alarm flooding. This means the maximum alarm span is `19 * repeatInterval` (e.g., 95 minutes at 5-minute intervals).
- **requestCode collision avoidance**: `requestCode` is derived from `dayDate * 1000 + index`. Since `maxRepeats <= 20`, the range for each day is at most 20 values, well within the 32-bit `requestCode` space.

## References

- [Android: Schedule alarms](https://developer.android.com/develop/background-work/services/alarms)
- [AlarmManager.setExactAndAllowWhileIdle()](https://developer.android.com/reference/android/app/AlarmManager#setExactAndAllowWhileIdle(int, long, android.app.PendingIntent))
- ADR-0001: wakey-sitizen as a GMS-free native Android companion app for SIT-ICS-Go

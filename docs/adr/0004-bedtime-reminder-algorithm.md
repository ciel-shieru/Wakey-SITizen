# ADR-0004: bedtime reminder algorithm

## Status

Proposed

## Date

2026-10-05

## Context

ADR-0003 established the alarm scheduling policy for wake-up alarms. Students also benefit from a bedtime reminder that helps them plan their sleep schedule. The bedtime reminder is derived from the same data source as wake-up alarms (the first scheduled event of the day) and user-configured sleep parameters.

## Decision Drivers

- **Derivation from wake-up alarms**: The bedtime reminder is computed from the first wake-up alarm time, keeping the algorithm deterministic and tied to the same data source.
- **User configurability**: Students have different sleep needs and schedules. Target sleep duration and bedtime reminder offset are user-configurable.
- **Edge case handling**: Early morning classes, no-events days, and past reminders must be handled gracefully.
- **Single alarm mechanism**: The bedtime reminder uses the same `AlarmManager` + `AlarmReceiver` infrastructure as wake-up alarms, avoiding a separate notification scheduling system.

## Decision

### Inputs (all user-configured)

- **targetSleepDuration**: The user's desired sleep duration, default 8 hours; accepted range is 1–24 hours.
- **bedtimeReminderMode**: Either Absolute or Relative. Default: Absolute.
- **bedtimeReminderAbsoluteTime**: In Absolute mode, the device-local time at which to notify; default 19:00 (7 PM).
- **bedtimeReminderOffset**: In Relative mode, how many minutes before the target bedtime to notify; default 30 minutes, range 5–1440 minutes.

### Derivation (computed during each sync)

1. Get the earliest first-alarm time that is still in the future (`firstAlarmTime`). If no upcoming alarm exists, skip bedtime computation.
2. Compute the actual `targetBedtime = firstAlarmTime - targetSleepDuration`. The reminder offset is never included in this calculation.
3. In Relative mode, compute `bedtimeReminderTime = targetBedtime - bedtimeReminderOffset`.
4. In Absolute mode, place `bedtimeReminderTime` at the configured local time on the target-bedtime date. If that clock time is at or after the target bedtime (for example, a 00:30 bedtime with a 19:00 notification), use the previous evening.
5. Display the actual target bedtime on the Home screen independently of notification delivery. Only schedule the notification if its trigger is still in the future.

### Delivery

The bedtime reminder is delivered as a notification:
- Set via `AlarmManager.setExactAndAllowWhileIdle()` targeting `AlarmReceiver` with a distinct action code (e.g., `ACTION_BEDTIME_REMINDER`) from wake-up alarms (`ACTION_WAKE_UP_ALARM`).
- `AlarmReceiver` posts a notification using `NotificationManager`.
- Requires `POST_NOTIFICATIONS` permission (ADR-0008).

### Edge cases

1. **No events**: If no timetable events exist for any future day, no alarms are set and no bedtime reminder is computed. The sync completes without error.

2. **Early morning events**: If the first event is early (e.g., 7:00 AM) and the target sleep duration is 8 hours, the target bedtime is 11:00 PM the previous evening. The bedtime reminder time would be, e.g., 10:30 PM. This is correct — the student should be reminded to sleep the evening before.

3. **Reminder trigger already passed**: Do not schedule a notification whose trigger time is in the past, as that could cause it to fire immediately. If the target bedtime itself is still in the future, keep displaying it on the Home screen; if the target bedtime has also passed, skip bedtime computation.

4. **Multiple days**: The bedtime reminder is computed for the next day's first alarm. If the user's schedule changes between syncs, the previous bedtime reminder is cleared and replaced with the new one during alarm reconciliation.

### Configuration defaults

| Setting | Default | Range |
|---------|---------|-------|
| Setting | Default | Range |
|---------|---------|-------|
| Target sleep | 8 hours | 1–24 hours |
| Reminder mode | Absolute | Absolute or Relative |
| Absolute notification time | 19:00 (7 PM local time) | Valid local time |
| Relative reminder offset | 30 minutes | 5–1440 minutes |

## Rationale

### Why derive from first wake-up alarm

The bedtime reminder is tied to when the student needs to wake up. If they need to wake at 8:00 AM and need 8 hours of sleep, they should be in bed by midnight, with a reminder at 11:30 PM. This keeps the algorithm simple and deterministic.

### Why use AlarmManager + AlarmReceiver instead of a separate system

Using the same infrastructure as wake-up alarms avoids:
- Introducing a second notification scheduling mechanism.
- Managing two separate sets of alarm state.
- Additional permissions or background work requirements.

### Why separate target bedtime from the reminder trigger

The target bedtime is always derived from the sleep-duration requirement. The reminder mode only determines notification timing. This keeps the Home screen's bedtime accurate even if the chosen notification time has already passed, while the sync worker avoids scheduling past triggers.

## Consequences

### Positive

- **Simple and deterministic**: Given the same first alarm time and config, the bedtime reminder is always identical.
- **No additional dependencies**: Uses existing `AlarmManager` + `AlarmReceiver` infrastructure.
- **Graceful degradation**: No events → no reminder, no error.

### Negative

- **Single reminder per day**: Only one bedtime reminder is set. Students who need multiple reminders (e.g., "wind down at 10 PM, bed at 11 PM") must configure a single offset that works for them.
- **Tied to alarm schedule**: If alarms are not set (no events), no bedtime reminder is set. The user cannot enable bedtime reminders without having a timetable.

### Neutral / operational

- **Notification channel**: The bedtime reminder uses a separate `NotificationChannel` (e.g., `"bedtime_reminders"`) from wake-up alarm notifications, allowing the user to silence one without affecting the other.
- **POST_NOTIFICATIONS**: The permission is only requested if the bedtime feature is enabled (i.e., both `targetSleepDuration` and `bedtimeReminderOffset` have valid values).

## References

- ADR-0003: alarm scheduling policy
- ADR-0008: permissions & privacy manifest
- [Android: Notifications](https://developer.android.com/develop/background-work/services/foregound-services#user-notifications)

# ADR-0005: external-event adjustment

## Status

Proposed

## Date

2026-10-05

## Context

ADR-0003 established that alarms are based on the first SIT-ICS-Go timetable event of the day. However, students may have external events (e.g., student briefings, extracurricular activities, club meetings) that occur before their scheduled classes and require them to wake up earlier. The external-event adjustment feature allows the app to detect such events and shift alarm times earlier accordingly.

## Decision Drivers

- **Availability in both discovery modes**: External-event adjustment should work regardless of whether the timetable source is base-URL or device-calendar. The feature depends only on `READ_CALENDAR` permission, not on the timetable source.
- **Privacy-first**: Only the minimum necessary data is read from the device calendar.
- **Start time over end time**: External events (e.g., briefings) require the student to be present at the start, not at the end. The alarm should account for when the event begins.
- **Opt-in per calendar**: The user selects which calendars to scan. Calendars not selected are not read.

## Decision

### Permission request

In base-URL mode, if the user enables external-event adjustment and `READ_CALENDAR` has not been granted, the app requests the permission at that time via `ActivityResultContracts.RequestPermission()`. If denied, the feature is disabled with a clear explanation in the UI.

In device-calendar mode, `READ_CALENDAR` is already granted (required for the mode), so the feature is immediately available.

### Calendar selection

The user selects which device calendars to scan for external events from a list displayed by the app. The list is populated by querying `CalendarContract.Calendars`:
- `ACCOUNT_NAME` and `ACCOUNT_TYPE` identify each calendar account.
- `DISPLAY_NAME` shows the calendar name.
- `CALENDAR_ACCESS_LEVEL` is checked to ensure the calendar is readable (`CalendarContract.Calendars.ACCESS_READ`).

At least one calendar must be selected for the feature to activate. The selection is persisted in DataStore.

### Event query

For each sync, the app queries events from the selected calendars:
- **Projection**: Only `title`, `dtstart`, and `dtend` are read. Never read: descriptions, attendees, locations, visibility, or transparency.
- **Time range**: `[today 00:00, tomorrow 00:00)` in the device's timezone.
- **Content URI**: `CalendarContract.Events.CONTENT_URI` with a selection filter on `CalendarContract.Events.CALENDAR_ID IN (selectedCalendarIds)`.
- **Timezone handling**: Events are returned in the device's timezone. The app converts them to `Asia/Singapore` for comparison with timetable events (which use `Asia/Singapore`).

### Adjustment logic

1. For each queried event, filter to events whose `dtstart` is **before** the computed first alarm time (from ADR-0003).
2. Among the filtered events, find the one with the **latest `dtstart`**. This is the event that most closely precedes the scheduled alarm.
3. If the gap between this event's `dtstart` and the first alarm time is within `bufferMinutes` (user-configurable, default 30 minutes), shift the first alarm earlier by the gap:

```
newAlarmTime = externalEventDStart
```

The repeat pattern and interval remain unchanged — only the base time shifts. If the gap exceeds `bufferMinutes`, no adjustment is made (the external event is too far from the scheduled alarm to be relevant).

### User configuration

| Setting | Default | Range | Description |
|---------|---------|-------|-------------|
| bufferMinutes | 30 | 5–120 | Maximum gap (in minutes) between an external event's start time and the first alarm time for the adjustment to apply. |

### Privacy guarantees

- Only `title`, `dtstart`, and `dtend` are read from calendar events.
- No descriptions, attendees, locations, or other fields are accessed.
- The query is scoped to only the user-selected calendars.
- Event data is not persisted — it is used only for the current sync cycle and discarded afterward.

## Rationale

### Why start time, not end time

External events such as student briefings, club meetings, and extracurricular activities require the student to be present at the **start** of the event. If a briefing starts at 7:30 AM and the first class is at 9:00 AM, the student needs to wake up before 7:30 AM — not before the briefing ends. Using `dtstart` correctly models this requirement.

### Why available in both discovery modes

The external-event feature depends on `READ_CALENDAR`, not on the timetable source. A user in base-URL mode who has granted `READ_CALENDAR` should be able to use external-event adjustment just as a user in device-calendar mode can. The timetable source and the calendar access are orthogonal concerns.

### Why bufferMinutes

Not every external event is relevant. A student might have a social gathering that ends at 8 AM, but their first class is at 9 AM — the gathering is not relevant to their wake-up schedule. The `bufferMinutes` threshold ensures only events that are close enough to the scheduled alarm time trigger an adjustment.

### Why only the latest dtstart

If there are multiple external events before the first alarm, the one with the latest start time is the most constraining — it's the event the student needs to be awake for that is closest to their scheduled alarm. Earlier events are less relevant because the student would already be awake for the later event.

## Consequences

### Positive

- **Works in both modes**: External-event adjustment is available regardless of timetable source.
- **Privacy-preserving**: Only the minimum necessary data is read. No descriptions, attendees, or locations are accessed.
- **User-controlled**: The user selects which calendars to scan and configures the buffer threshold.

### Negative

- **READ_CALENDAR in base-URL mode**: Users who prefer base-URL mode for its minimal privilege surface must grant `READ_CALENDAR` to use external-event adjustment. This is a tradeoff the user makes consciously.
- **Event data not persisted**: Since event data is ephemeral (used only per sync), a reboot without a sync may result in missed external events. The `BootReceiver` (ADR-0007) triggers an immediate sync to mitigate this.

### Neutral / operational

- **Timezone conversion**: Timetable events use `Asia/Singapore`. Device calendar events may be in any timezone. The app converts both to a common timezone for comparison.
- **Recurrence handling**: Recurring events are expanded by the `CalendarContract` query using the `dtstart` and `dtend` range. The app does not need to handle `RRULE` expansion itself.

## References

- ADR-0003: alarm scheduling policy
- ADR-0008: permissions & privacy manifest
- [Android: CalendarContract.Events](https://developer.android.com/reference/android/provider/CalendarContract.Events)
- [ActivityResultContracts.RequestPermission](https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.RequestPermission)

# ADR-0007: background sync mechanism and boot recovery

## Status

Proposed

## Date

2026-10-05

## Context

ADR-0003 through ADR-0006 established the alarm scheduling, bedtime reminder, external-event adjustment, and manual override policies. These policies must be executed regularly to keep alarms in sync with the user's timetable. The background sync mechanism handles this execution, while boot recovery ensures alarms survive device reboots.

## Decision Drivers

- **Regular updates**: The alarm schedule must be refreshed at least twice per day to account for timetable changes and to set alarms for the next day.
- **Battery efficiency**: Background work should respect system battery optimization. WorkManager's flex intervals allow the system to batch work with other periodic tasks.
- **Offline-first**: The app should work without network connectivity, falling back to last-known data.
- **Reboot survival**: Android clears all `AlarmManager` alarms on reboot. Alarms must be restored automatically.
- **Unprivileged**: No foreground service, no special background privileges. WorkManager is the platform-recommended approach for persistent background work.

## Decision

### Periodic sync (WorkManager)

A `PeriodicWorkRequest` is configured as follows:

| Parameter | Value |
|-----------|-------|
| `repeatInterval` | 12 hours |
| `flexInterval` | 30 minutes |
| `tag` | `"sync_work"` |
| `constraints` | No required network type (works offline) |

The work runs approximately every 12 hours (e.g., ~6 AM and ~6 PM), with a 30-minute flex window that allows the system to batch it with other periodic tasks.

### Sync flow (`SyncWorker.doWork()`)

The worker executes the full alarm-management pipeline in order:

1. **Fetch timetable**: Retrieve events from the configured source (base-URL JSON or device calendar). If fetch fails, use last-parsed events from local DataStore (stale-while-revalidate).
2. **Parse and filter**: Deserialize events (JSON) or query calendar (device-calendar mode). Filter out xsite/BrightSpace events (`Source` prefix `brightspace-`).
3. **Compute alarm schedule**: For each day, find the first event, apply smart offset, generate repeat pattern (ADR-0003).
4. **Adjust for external events**: If enabled, scan selected device calendars for pre-timetable events and shift alarms earlier (ADR-0005).
5. **Apply manual overrides**: Load and apply active overrides from DataStore (ADR-0006). Clear expired overrides.
6. **Reconcile alarms**: Clear old alarms for affected days, set new alarms via `AlarmService` (ADR-0003).
7. **Compute bedtime reminder**: Derive target bedtime from first alarm, compute reminder time, set alarm if not in the past (ADR-0004).

### Stale-while-revalidate

If the fetch step fails (network unavailable, server unreachable, calendar query error):
- Use last-parsed events from DataStore.
- Alarms from the last successful sync remain active.
- The sync is marked as successful (no retry needed) since the fallback data is valid.

This ensures the app always has a valid alarm schedule, even when the data source is temporarily unavailable.

### Cancellation

The user can disable background sync in settings. When disabled:
- `WorkManager.cancelAllWorkByTag("sync_work")` cancels the periodic work.
- Alarms are not cleared — they remain active until their scheduled time or until the user manually clears them.
- The next time sync is enabled, a full recomputation runs.

### Boot recovery (`BootReceiver`)

Android clears all `AlarmManager` alarms on reboot. To restore alarms:

1. `BootReceiver` listens for `android.intent.action.BOOT_COMPLETED`.
2. On receipt, it enqueues a one-time sync via `WorkManager.enqueueUniqueWork("boot_sync", ExistingWorkPolicy.REPLACE, ...)` with no constraints (runs immediately).
3. The one-time sync executes the full pipeline (same as the periodic sync), restoring alarms from persisted config.

**Manifest declaration:**

```xml
<receiver
    android:name=".receiver.BootReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

The receiver is `exported="false"` since `BOOT_COMPLETED` is a system-broadcast intent that does not require external components to send it. However, `RECEIVE_BOOT_COMPLETED` permission is still required (ADR-0008).

### AlarmReceiver (`onReceive`)

When any alarm fires (wake-up or bedtime), `AlarmReceiver.onReceive()` is invoked:
- For wake-up alarms: Post a notification (if not already visible) and optionally trigger haptic/vibration feedback via the ringtone.
- For bedtime reminders: Post a notification with the bedtime message.

The receiver is `exported="false"` since alarm `PendingIntent`s are sent by the system to this app's process.

## Rationale

### Why 12-hour interval

A 12-hour interval ensures the alarm schedule is refreshed at least twice per day, covering:
- Morning sync (~6 AM): Sets alarms for the rest of today and tomorrow.
- Evening sync (~6 PM): Updates alarms for the next day based on any timetable changes.

This cadence balances freshness with battery efficiency. A shorter interval (e.g., 6 hours) would provide more frequent updates but at the cost of additional battery drain. A longer interval (e.g., 24 hours) would risk stale alarms if the timetable changes between syncs.

### Why flexInterval of 30 minutes

The flex interval allows the system to batch this work with other periodic tasks, reducing battery impact. A 30-minute flex window is reasonable — it provides enough flexibility for the system to batch without significantly delaying the sync.

### Why no required network constraints

The app should work offline. If the network is available, the fetch is attempted. If not, the last-known data is used. Requiring network would prevent the app from functioning on devices without connectivity (e.g., airplane mode, no SIM).

### Why BootReceiver is necessary

Android clears all `AlarmManager` alarms on reboot. Without boot recovery, a student who reboots their phone (or whose phone reboots due to an update) would wake up with no alarms. The `RECEIVE_BOOT_COMPLETED` permission and `BootReceiver` are essential for this use case.

## Consequences

### Positive

- **Always up-to-date**: Alarms are refreshed at least twice per day.
- **Works offline**: Last-known data is used when the fetch fails.
- **Reboot-safe**: Alarms are restored automatically after reboot.
- **Battery-efficient**: Flex interval allows system-level batching.
- **Unprivileged**: No foreground service or special background privileges required.

### Negative

- **No real-time updates**: If the timetable changes between syncs, alarms are not updated until the next sync cycle. This is an accepted tradeoff — timetable changes are rare during a semester.
- **Boot receiver requires permission**: `RECEIVE_BOOT_COMPLETED` is a normal permission but must be declared and justified (ADR-0008).
- **WorkManager is not guaranteed**: WorkManager does not guarantee exact execution times. The system may delay work due to Doze mode, battery optimization, or device constraints. This is acceptable since the 12-hour interval provides a large margin.

### Neutral / operational

- **Unique work names**: `"sync_work"` for periodic work, `"boot_sync"` for boot recovery. `ExistingWorkPolicy.REPLACE` on boot sync ensures only one boot recovery runs at a time.
- **Worker logging**: Sync progress and errors are logged to `logcat` for debugging. No user-facing logs are stored.
- **Failure handling**: If the sync fails completely (e.g., all data sources unavailable), WorkManager retries with exponential backoff (default: up to 4 retries over ~2 hours).

## References

- [Android: Background work with WorkManager](https://developer.android.com/develop/background-work/services/workoverview)
- [PeriodicWorkRequest](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest)
- [Android: Schedule alarms](https://developer.android.com/develop/background-work/services/alarms)
- ADR-0003: alarm scheduling policy
- ADR-0004: bedtime reminder algorithm
- ADR-0005: external-event adjustment
- ADR-0006: manual override model
- ADR-0008: permissions & privacy manifest

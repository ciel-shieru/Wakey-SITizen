# ADR-0008: permissions & privacy manifest

## Status

Proposed

## Date

2026-10-05

## Context

ADR-0001 established that wakey-sitizen must be "as unprivileged as possible, with every permission documented and justified." This ADR provides the definitive permission list with per-permission justification, fulfilling the follow-up item from ADR-0001.

## Decision Drivers

- **Minimal privilege surface**: Only request permissions that are strictly required for the app's functionality.
- **Documentation**: Every permission must have a clear, user-understandable justification.
- **Conditional requests**: Permissions that are not always needed (e.g., `READ_CALENDAR`) must only be requested when the feature requiring them is enabled.
- **No GMS**: All permissions must be satisfiable without Google Play Services.

## Decision

### Requested permissions

| Permission | Type | API Level | When requested | Justification |
|------------|------|-----------|----------------|---------------|
| `USE_EXACT_ALARM` | Normal (auto-granted) | 33+ | Always | Required for precise wake-up alarm delivery via `AlarmManager.setExactAndAllowWhileIdle()`. Auto-granted on install for API 33+; no user prompt needed. |
| `POST_NOTIFICATIONS` | Runtime | 33+ | When bedtime feature is enabled | Required for bedtime reminder notification and alarm-fire notifications. Requested at first launch if `targetSleepDuration` and `bedtimeReminderOffset` have valid values. If denied, bedtime reminders are disabled but the app continues to function. |
| `RECEIVE_BOOT_COMPLETED` | Normal | All | Always (declared in manifest) | Restores alarm state after device reboot. Android clears all `AlarmManager` alarms on reboot; this receiver triggers a one-time WorkManager sync to re-establish alarms from persisted config. Without it, a student who reboots their phone would wake up with no alarms. |
| `INTERNET` | Normal | All | Always (declared in manifest) | Required to fetch `/wakey-sitizen/timetable.json` from the user-configured SIT-ICS-Go base URL. This is the **only** network code path in the app — no other egress exists. The destination is fully determined by the user's configuration. |
| `READ_CALENDAR` | Runtime | All | Only when device-calendar mode is selected OR external-event adjustment is enabled in base-URL mode | Required for reading device calendar events. Only requested when the user explicitly enables a feature that requires it. Never requested if base-URL mode is used without external-event adjustment. |

### Not requested (and justified as unnecessary)

| Permission | Reason |
|------------|--------|
| `WRITE_CALENDAR` | The app never writes to or modifies the device calendar. It only reads events for external-event adjustment. |
| `SCHEDULE_EXACT_ALARM` | Superseded by `USE_EXACT_ALARM` on API 33+. Since `minSdk` is 36, this permission is never needed. |
| `FOREGROUND_SERVICE` | No foreground service is used. Background sync runs via WorkManager, which does not require a foreground service. |
| `ACCESS_FINE_LOCATION` | The app has no location-related functionality. Timetable events do not require geolocation. |
| `ACCESS_COARSE_LOCATION` | Same as above. |
| `CAMERA` | No camera functionality. |
| `RECORD_AUDIO` / `RECORD_VIDEO` | No audio or video recording functionality. |
| `BLUETOOTH` | No Bluetooth functionality. |
| `BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN` | No Bluetooth functionality. |
| `NFC` | No NFC functionality. |
| `PHONE` / `READ_PHONE_STATE` | No phone functionality. |
| `SEND_SMS` | No SMS functionality. |
| `CALL_PHONE` | No phone call functionality. |
| `ACCESS_NETWORK_STATE` | Not required. `java.net.http.HttpClient` handles connectivity internally. The app does not need to check connectivity state — it attempts the fetch and falls back to last-known data on failure. |
| `ACCESS_WIFI_STATE` | Not required. Same rationale as `ACCESS_NETWORK_STATE`. |

### Permission request flow

```
First launch
    │
    ├── Bedtime feature enabled?
    │   ├── Yes → Request POST_NOTIFICATIONS
    │   └── No → Skip
    │
    ├── Discovery mode = Base-URL?
    │   ├── Yes → External events enabled?
    │   │           ├── Yes → Request READ_CALENDAR
    │   │           └── No  → Skip
    │   └── Discovery mode = Device-Calendar?
    │       └── Request READ_CALENDAR
    │
    └── USE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED, INTERNET → Declared in manifest (auto-granted or normal)
```

### Permission denial handling

- **POST_NOTIFICATIONS denied**: Bedtime reminders are disabled. The user can enable them later by granting the permission from settings. The app continues to function normally for alarm management.
- **READ_CALENDAR denied**: The device-calendar mode is unavailable. The user can switch to base-URL mode or enable external-event adjustment later by granting the permission. The app continues to function normally.

## Rationale

### Why only 5 permissions

The app's functionality is narrow and well-defined:
1. Set alarms → `USE_EXACT_ALARM`
2. Show notifications → `POST_NOTIFICATIONS`
3. Restore alarms on reboot → `RECEIVE_BOOT_COMPLETED`
4. Fetch timetable → `INTERNET`
5. Read device calendar (optional) → `READ_CALENDAR`

No other permissions are required.

### Why no `ACCESS_NETWORK_STATE`

While many apps check connectivity before making network requests, `java.net.http.HttpClient` handles connectivity internally. If the network is unavailable, the HTTP request fails and the app falls back to last-known data. Checking `ACCESS_NETWORK_STATE` would add a permission without adding functionality — the fallback behavior is the same whether we check connectivity or not.

### Why `RECEIVE_BOOT_COMPLETED` is normal (not runtime)

`RECEIVE_BOOT_COMPLETED` is a normal permission that is auto-granted on install. It does not require a runtime prompt. This is appropriate because:
- The user does not need to make a decision about this permission — it is a system-level requirement for reboot recovery.
- The permission does not expose any user data or device capabilities.

### Why `INTERNET` is normal (not runtime)

`INTERNET` is a normal permission that is auto-granted on install. This is appropriate because:
- The network destination is fully determined by the user's configuration (a self-hosted SIT-ICS-Go server).
- The permission does not expose any user data.
- The app has no other network code paths.

## Consequences

### Positive

- **Minimal surface**: Only 5 permissions, 2 of which are conditional (requested only when needed).
- **Documented**: Every permission has a clear justification that can be presented to the user in-app and in distribution documentation.
- **No GMS dependencies**: All permissions are satisfiable without Google Play Services.

### Negative

- **User education required**: Users may not understand why `INTERNET` or `RECEIVE_BOOT_COMPLETED` is needed for an alarm app. In-app explanations are necessary.
- **POST_NOTIFICATIONS is runtime**: Unlike `USE_EXACT_ALARM` (auto-granted), `POST_NOTIFICATIONS` requires a user prompt. If denied, bedtime reminders are disabled.

### Neutral / operational

- **Permission state in UI**: The app displays the current permission state for each feature (e.g., "Bedtime reminders: enabled/disabled/permission required") so the user understands what is and isn't working.
- **Settings entry point**: If a permission is denied, the app provides a settings entry point that opens the app's permission settings page (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`) so the user can grant it.

## References

- [Android: Request app permissions](https://developer.android.com/training/permissions/requesting)
- [USE_EXACT_ALARM vs SCHEDULE_EXACT_ALARM](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)
- ADR-0001: wakey-sitizen as a GMS-free native Android companion app for SIT-ICS-Go

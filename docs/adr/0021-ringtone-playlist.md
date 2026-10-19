# ADR-0021: Per-alarm ringtone playlist with picker UI

## Status

Proposed

## Date

2026-10-19

## Context

wakey-sitizen currently fires all repeat alarms within a day's alarm set using the same vibration pattern defined by the Android notification channel. Each day's alarms are computed by `AlarmComputer` as a sequence of `AlarmSchedule.Alarm` objects (indexed 0..maxRepeats-1), and `AlarmService.setAlarm()` arms each one via `AlarmManager` without any per-alarm audio customization.

Students who have multiple alarms per day (e.g., 6 repeats of a 5-minute interval) benefit from variety to avoid habituation — the same vibration pattern repeated 6 times becomes less effective at waking a deep sleeper. Additionally, users may wish to assign different ringtones to different alarms within a day (e.g., a gentle ring for the first alarm, a louder one for later repeats) or enable shuffle to add unpredictability.

Currently, no mechanism exists to associate audio assets with individual alarms. The notification channel's vibration pattern is static and shared across all alarms.

## Decision Drivers

- **Per-alarm audio variety**: Users should be able to assign multiple ringtones to a day's alarm set and have each alarm fire with a different ringtone.
- **Shuffle support**: Users should be able to enable randomized ringtone selection to add unpredictability (which improves wake-up effectiveness per sleep research).
- **Deterministic shuffle**: When shuffle is enabled, the same ringtone sequence must be produced for the same alarm index on every computation — so that re-syncing does not change which ringtone is assigned to which alarm.
- **Backward compatibility**: Existing users without any ringtone playlist configured must continue to function without changes. No migration is required.
- **Minimal audio surface**: Ringtones are selected from the device's existing ringtone library via `RingtoneManager` content URIs — no file storage, no custom audio asset bundling.
- **Alarm firing path integrity**: The ringtone URI must flow through the existing alarm firing path (`AlarmService` → `PendingIntent` → `AlarmReceiver` → `NotificationService`) without breaking the current vibration-only behavior.
- **UI discoverability**: The ringtone picker must be accessible from the Settings screen and easy to use (add/remove/reorder ringtones, toggle shuffle).

## Considered Options

### Option A: RingtonePlaylist domain model + per-alarm ringtone URI in AlarmSchedule.Alarm (Selected)

Create a new `RingtonePlaylist` domain model in `domain/model/` with properties `ringtoneUris: List<String>` and `shuffleEnabled: Boolean`. Add `ringtonePlaylist: RingtonePlaylist` to `AlarmConfig` (with a `DEFAULT` that has no ringtones). Extend `AlarmSchedule.Alarm` with an optional `ringtoneUri: String?` field. `AlarmComputer.ringtoneForRepeatIndex()` assigns URIs at schedule computation time. `AlarmService` passes the URI as an intent extra; `AlarmReceiver` forwards it to `NotificationService`; `NotificationService` plays it via `MediaPlayer` with `USAGE_ALARM` audio attributes when present, falling back to vibration-only when absent.

### Option B: Ringtone URI directly on AlarmConfig (single ringtone for all alarms)

Add a single `ringtoneUri: String?` to `AlarmConfig` that applies to all alarms in a day's set. This is simpler but does not support per-alarm variety or shuffle.

### Option C: RingtonePlaylist stored per-day in AlarmStore

Store the ringtone playlist in the alarm schedule persistence layer (alongside `AlarmSchedule`) rather than in `AlarmConfig`. This would allow different playlists per day but couples the playlist to the alarm schedule data model and complicates the sync pipeline.

### Option D: RingtonePlaylist with file-based audio assets

Bundle custom audio files in the app's `res/raw/` directory and reference them by resource ID. This gives full control over audio content but increases APK size, requires users to manage audio files, and does not leverage the device's existing ringtone library.

## Decision

We will choose **Option A**.

A new `RingtonePlaylist` data class will be created in `app/src/main/java/com/cielshieru/wakey/domain/model/RingtonePlaylist.kt` with the following contract:

```kotlin
@Serializable
data class RingtonePlaylist(
    val ringtoneUris: List<String> = emptyList(),
    val shuffleEnabled: Boolean = false
) {
    companion object {
        const val MAX_RINGTONES = 20
        val DEFAULT = RingtonePlaylist()
    }

    fun ringtoneForRepeatIndex(index: Int): String? {
        if (ringtoneUris.isEmpty()) return null
        return if (shuffleEnabled) {
            val seed = index.toLong()
            val shuffled = ringtoneUris.shuffled(seed)
            shuffled[index % ringtoneUris.size]
        } else {
            ringtoneUris[index % ringtoneUris.size]
        }
    }
}
```

The `AlarmConfig` data class will be extended with `val ringtonePlaylist: RingtonePlaylist = RingtonePlaylist.DEFAULT`. The `AlarmSchedule.Alarm` data class will be extended with `val ringtoneUri: String? = null`.

`AlarmComputer.computeSchedule()` will be updated to call `config.ringtonePlaylist.ringtoneForRepeatIndex(index)` when constructing each `AlarmSchedule.Alarm`, passing the resulting URI as the `ringtoneUri` parameter.

`AlarmService.setAlarm()` will accept an optional `ringtoneUri: String?` parameter and add it as an intent extra (`EXTRA_RINGTONE_URI`) when non-null. `AlarmReceiver.onReceive()` will read this extra from the intent and pass it to `NotificationService.showAlarmNotification()`. `NotificationService.showAlarmNotification()` will accept a `ringtoneUri: String?` parameter and, when non-null, play the ringtone via `MediaPlayer` with `AudioAttributes.USAGE_ALARM` before posting the notification.

A new navigation route `Route.RingtonePicker` will be added to `WakeyNavHost.kt`. A new `RingtonePickerScreen` composable will provide a UI for managing the playlist: browsing device ringtones via `RingtoneManager`, adding/removing ringtones, drag-and-drop reordering, and a shuffle toggle. A new `RingtonePickerViewModel` will manage the editing state. A new `RingtoneNameResolver` will resolve ringtone URIs to display names.

## Rationale

### Why RingtonePlaylist as a domain model (Option A detail)

A dedicated `RingtonePlaylist` type encapsulates the playlist semantics (shuffle, index-based selection, max size) in one place. This keeps the logic testable and isolated from both the configuration store and the UI. It also makes the contract explicit: a playlist is a list of URIs plus a shuffle flag, nothing more.

The `ringtoneForRepeatIndex(index)` method is pure (no I/O, no state mutation) and deterministic — it takes an index and returns a URI. This is critical because `AlarmComputer` is a pure computation function that receives a `Clock` and `ZoneProvider` but no mutable state. The method uses `index % size` for linear mode (cycling through the playlist) and `Random(index)`-seeded shuffle for randomized mode, ensuring the same index always produces the same ringtone regardless of when the schedule is computed.

### Why extend AlarmSchedule.Alarm with ringtoneUri (Option A detail)

The `AlarmSchedule.Alarm` data class already carries per-alarm metadata (`index`, `triggerAt`, `requestCode`). Adding `ringtoneUri` keeps the ringtone assignment tightly coupled to the alarm it belongs to. This is important because:

1. **Assignment happens at computation time**: `AlarmComputer` assigns the ringtone based on the alarm's index within the day's set. The ringtone is not a property of the alarm configuration alone — it is a property of the alarm *instance* (index 0 gets ringtone A, index 1 gets ringtone B, etc.).
2. **Persistence carries the assignment**: The alarm schedules are serialized to DataStore via `AlarmStore` (ADR-0007). Including `ringtoneUri` in `AlarmSchedule.Alarm` means the assigned ringtone survives across app restarts and sync cycles without requiring a separate persistence path.
3. **Backward compatibility**: The field has a default value of `null`, so existing serialized schedules that lack the field will deserialize correctly (the field will be `null`). `NotificationService` already handles `null` by falling back to vibration-only.

### Why pass ringtone URI as an intent extra (Option A detail)

The alarm firing path is: `AlarmManager` fires → `PendingIntent` delivers to `AlarmReceiver` → `AlarmReceiver` calls `NotificationService`. The `PendingIntent` already carries extras (`EXTRA_ALARM_INDEX`, `EXTRA_ALARM_DAY`). Adding `EXTRA_RINGTONE_URI` follows the same pattern and requires no changes to the `AlarmManager` or `PendingIntent` creation logic beyond adding one `putExtra` call.

This approach avoids:
- Creating a separate `MediaPlayer` instance in `AlarmService` (which would be killed before the alarm fires if the process is not in the foreground).
- Storing the ringtone URI in a global singleton that could be stale across alarm firings.
- Adding a new Android component (e.g., a foreground service) whose lifecycle is harder to manage.

### Why MediaPlayer with USAGE_ALARM (Option A detail)

`RingtoneManager.getActualDefaultRingtoneUri()` returns a `content://` URI that can be played by `MediaPlayer`. Using `MediaPlayer` (rather than `RingtoneManager.play()`) gives us control over the audio attributes:

- `AudioAttributes.USAGE_ALARM` ensures the system routes audio through the alarm channel, respects "Do Not Disturb" exceptions for alarms, and uses the device's alarm volume (not media volume).
- `AudioAttributes.CONTENT_TYPE_MUSIC` is appropriate because ringtones are music-like content.
- `MediaPlayer` supports looping (via `isLoopingStreaming` or repeated `start()` calls) which may be needed if a ringtone is shorter than the desired repeat duration.

The ringtone is played asynchronously (non-blocking) so that the notification is posted immediately. If playback fails (e.g., invalid URI, no audio focus), the notification is still posted — the ringtone is an enhancement, not a requirement.

### Why RingtoneManager content URIs (Option A detail)

Android's `RingtoneManager` provides access to the device's ringtone library through `content://` URIs. These URIs are:
- **Persistent**: They survive app restarts and can be stored in DataStore.
- **Shared**: Multiple apps can reference the same ringtone URI.
- **User-managed**: Users change ringtones via system settings, and the URIs update automatically.

This avoids bundling audio files (which increases APK size and requires user management of custom files) and avoids storing audio data in the app's private storage (which would be lost on uninstall and is unnecessary when the system already manages ringtones).

### Why shuffle uses deterministic Random(index) (Option A detail)

When shuffle is enabled, the ringtone at index `i` must be the same every time the schedule is computed. This is achieved by seeding `java.util.Random` with `index`:

```kotlin
val shuffled = ringtoneUris.shuffled(seed)
shuffled[index % ringtoneUris.size]
```

`List.shuffled(seed)` produces a deterministic permutation for a given seed. Since the seed is the index, the same index always maps to the same ringtone. This is different from a single shuffle applied to the entire playlist (which would produce a fixed permutation for all indices) — instead, each index independently selects a ringtone in a way that feels randomized but is reproducible.

### Why MAX_RINGTONES = 20

This matches `AlarmService.MAX_ALARM_REPEATS` (20 alarms per day). A playlist larger than the maximum number of alarms per day would never be fully consumed, so capping at 20 is a natural boundary. It also prevents excessive UI complexity in the picker (20 items is a reasonable scrollable list).

### Why AlarmStore needs no changes

`AlarmSchedule.Alarm` gains a new field with a default value (`ringtoneUri: String? = null`). Existing serialized schedules in DataStore do not contain this field. When `AlarmStore.schedulesFlow()` deserializes them via `JsonConfig.instance.decodeFromString<List<AlarmSchedule>>(it)`, the missing field is filled with its default value (`null`). No migration function is needed.

### Why AlarmConfigStore needs a new key

`AlarmConfig` gains a new field `ringtonePlaylist: RingtonePlaylist`. This field must be serialized to DataStore so it survives app restarts. Following the pattern of other `AlarmConfig` fields, it will be stored as a JSON string using `kotlinx.serialization`. The key will be `"ringtone_playlist"`. Deserialization uses `runCatching { ... }.getOrNull() ?: RingtonePlaylist.DEFAULT` for backward compatibility with existing users who have no playlist configured.

## Consequences

### Positive

- **Per-alarm audio variety**: Each alarm in a day's set can play a different ringtone, improving wake-up effectiveness through auditory variety.
- **Shuffle support**: Users can enable randomized selection for added unpredictability, which sleep research suggests improves arousal from deep sleep stages.
- **Deterministic assignment**: Re-syncing the schedule produces the same ringtone-to-alarm mapping, preventing confusion when alarms re-fire with different ringtones.
- **Backward compatible**: Existing users without a playlist configured see no change — alarms fire with vibration only, exactly as before.
- **No APK size increase**: Ringtones are referenced by system content URIs, not bundled as app assets.
- **Clear separation of concerns**: `RingtonePlaylist` encapsulates playlist logic; `AlarmComputer` assigns URIs; `NotificationService` plays them. Each component has a single responsibility.
- **UI discoverability**: The ringtone picker is accessible from Settings, making the feature easy to discover for users who want it.

### Negative

- **New domain model**: `RingtonePlaylist` adds a new data class that must be maintained alongside existing domain models.
- **AlarmSchedule.Alarm serialization change**: The `ringtoneUri` field is added to a `@Serializable` data class. While backward compatible, this changes the serialized format for all future alarm schedules.
- **MediaPlayer lifecycle**: `MediaPlayer` instances must be properly released to avoid resource leaks. `NotificationService` must track active players and release them when playback completes or is interrupted.
- **Audio focus conflicts**: If another app is playing audio (music, podcast) when an alarm fires, `MediaPlayer` with `USAGE_ALARM` should gain audio focus, but this behavior varies by device manufacturer and Android version.
- **UI complexity**: The ringtone picker introduces a new screen with drag-and-drop reordering, shuffle toggle, and ringtone browsing — adding to the Settings navigation depth.
- **New ViewModel**: `RingtonePickerViewModel` manages playlist editing state, including drag-and-drop order, shuffle state, and selected ringtones. This requires additional testing effort.
- **RingtoneManager dependency**: The picker and name resolver depend on `RingtoneManager`, which requires no special permissions but may behave differently across Android versions (though `minSdk 36` limits this concern).

### Neutral / operational

- **New file**: `app/src/main/java/com/cielshieru/wakey/domain/model/RingtonePlaylist.kt` — domain model with `ringtoneForRepeatIndex()`.
- **Modified file**: `app/src/main/java/com/cielshieru/wakey/domain/model/AlarmConfig.kt` — adds `ringtonePlaylist: RingtonePlaylist` property.
- **Modified file**: `app/src/main/java/com/cielshieru/wakey/domain/model/AlarmSchedule.kt` — adds `ringtoneUri: String?` to `Alarm` data class.
- **Modified file**: `app/src/main/java/com/cielshieru/wakey/domain/AlarmComputer.kt` — assigns ringtone URIs via `config.ringtonePlaylist.ringtoneForRepeatIndex(index)`.
- **Modified file**: `app/src/main/java/com/cielshieru/wakey/data/store/AlarmConfigStore.kt` — adds JSON-serialized `"ringtone_playlist"` key with serialize/deserialize logic.
- **Modified file**: `app/src/main/java/com/cielshieru/wakey/android/AlarmService.kt` — adds `EXTRA_RINGTONE_URI` constant and passes ringtone URI as intent extra in `setAlarm()`.
- **Modified file**: `app/src/main/java/com/cielshieru/wakey/android/AlarmReceiver.kt` — reads `EXTRA_RINGTONE_URI` from intent and passes it to `NotificationService`.
- **Modified file**: `app/src/main/java/com/cielshieru/wakey/android/NotificationService.kt` — accepts `ringtoneUri: String?` parameter, plays via `MediaPlayer` with `USAGE_ALARM` when non-null.
- **Modified file**: `app/src/main/java/com/cielshieru/wakey/ui/navigation/Route.kt` — adds `RingtonePicker` route.
- **Modified file**: `app/src/main/java/com/cielshieru/wakey/ui/navigation/WakeyNavHost.kt` — adds `composable(Route.RingtonePicker)` navigation.
- **New file**: `app/src/main/java/com/cielshieru/wakey/ui/ringtone/RingtonePickerScreen.kt` — Compose UI for ringtone picker.
- **New file**: `app/src/main/java/com/cielshieru/wakey/ui/ringtone/RingtonePickerViewModel.kt` — ViewModel for ringtone picker state management.
- **New file**: `app/src/main/java/com/cielshieru/wakey/ui/ringtone/RingtoneNameResolver.kt` — resolves ringtone URIs to display names via `RingtoneManager`.
- **Modified file**: `app/src/main/java/com/cielshieru/wakey/di/AppModule.kt` — adds `@Provides` for `RingtoneNameResolver`.
- **Modified file**: `app/src/main/java/com/cielshieru/wakey/ui/settings/SettingsScreen.kt` — adds link to ringtone picker.
- **New dependency**: `android.media.MediaPlayer` and `android.media.AudioAttributes` (platform APIs, no third-party dependency).
- **No ProGuard changes**: `MediaPlayer` and `RingtoneManager` are platform APIs; no keep rules needed.

## Alternatives Considered

### Option A: RingtonePlaylist domain model + per-alarm ringtone URI in AlarmSchedule.Alarm (Selected)

Full per-alarm variety with shuffle support, deterministic assignment, and clean separation of concerns. The trade-off is additional code across multiple files (domain model, store, service, receiver, notification, UI, ViewModel). This is the most capable option and aligns with the feature requirements.

### Option B: Single ringtone URI on AlarmConfig

Applies one ringtone to all alarms in a day's set. Simpler (one field on `AlarmConfig`, no `RingtonePlaylist` model, no shuffle logic) but does not support per-alarm variety. Rejected because the primary use case (variety across repeat alarms) cannot be satisfied.

### Option C: RingtonePlaylist stored per-day in AlarmStore

Stores the playlist alongside alarm schedules rather than in `AlarmConfig`. This would allow different playlists per day but couples the playlist to the alarm schedule data model. The sync pipeline would need to persist the playlist separately from the config. Rejected for unnecessary complexity — the playlist is a user preference (like offsets and repeat patterns), not schedule-specific data.

### Option D: File-based audio assets

Bundles custom audio files in `res/raw/`. Increases APK size, requires users to obtain and manage audio files, and does not leverage the device's existing ringtone library. Rejected because RingtoneManager content URIs are simpler, smaller, and more user-friendly.

## Follow-Ups

- Create `RingtonePlaylist.kt` domain model with `ringtoneForRepeatIndex()`, `MAX_RINGTONES`, and `DEFAULT`.
- Extend `AlarmConfig` with `ringtonePlaylist: RingtonePlaylist` field.
- Extend `AlarmSchedule.Alarm` with `ringtoneUri: String?` field.
- Update `AlarmComputer.computeSchedule()` to assign ringtone URIs.
- Update `AlarmConfigStore` with `"ringtone_playlist"` JSON key for serialization/deserialization.
- Update `AlarmService.setAlarm()` to pass ringtone URI as intent extra.
- Update `AlarmReceiver.onReceive()` to read and forward ringtone URI.
- Update `NotificationService.showAlarmNotification()` to play ringtone via `MediaPlayer` with `USAGE_ALARM`.
- Add `Route.RingtonePicker` to `Route.kt`.
- Add ringtone picker route to `WakeyNavHost.kt`.
- Create `RingtonePickerScreen.kt` with ringtone list, add/remove, drag-and-drop reordering, shuffle toggle.
- Create `RingtonePickerViewModel.kt` for playlist editing state.
- Create `RingtoneNameResolver.kt` for URI-to-name resolution.
- Add `RingtoneNameResolver` provider to `AppModule.kt`.
- Link ringtone picker from Settings screen.
- Unit tests for `RingtonePlaylist.ringtoneForRepeatIndex()` (linear and shuffle modes, empty list, index out of bounds).
- Unit tests for `AlarmComputer` ringtone assignment.
- Unit tests for `RingtonePickerViewModel` playlist editing operations.
- Unit tests for `AlarmConfigStore` ringtone serialization/deserialization.
- Integration test for end-to-end ringtone flow (picker → config → schedule → alarm fire → playback).

## References

- ADR-0003: Alarm scheduling policy — defines `AlarmSchedule.Alarm` with `index`, `triggerAt`, `requestCode`; `AlarmService.MAX_ALARMS_PER_DAY = 20`.
- ADR-0004: Bedtime reminder algorithm — defines how `NotificationService` posts notifications; baseline for audio playback addition.
- ADR-0006: Manual override model — defines how `AlarmConfig` fields are stored in DataStore via `AlarmConfigStore`.
- ADR-0007: Background sync and boot recovery — defines `AlarmStore` serialization of `AlarmSchedule` and the alarm firing pipeline.
- ADR-0008: Permissions and privacy manifest — defines the permission model; `RingtoneManager` requires no special permissions.
- `app/src/main/java/com/cielshieru/wakey/domain/model/AlarmConfig.kt` — current domain model (no ringtone playlist yet).
- `app/src/main/java/com/cielshieru/wakey/domain/model/AlarmSchedule.kt` — current alarm schedule model (no ringtone URI yet).
- `app/src/main/java/com/cielshieru/wakey/domain/AlarmComputer.kt` — current alarm computation (no ringtone assignment).
- `app/src/main/java/com/cielshieru/wakey/android/AlarmService.kt` — current alarm service with `setAlarm()` at lines 56-77.
- `app/src/main/java/com/cielshieru/wakey/android/AlarmReceiver.kt` — current receiver at lines 21-32.
- `app/src/main/java/com/cielshieru/wakey/android/NotificationService.kt` — current notification service with `showAlarmNotification()` at lines 68-88.
- `app/src/main/java/com/cielshieru/wakey/data/store/AlarmConfigStore.kt` — current config store serialization pattern.
- `app/src/main/java/com/cielshieru/wakey/ui/navigation/Route.kt` — current routes (Home, Settings).
- [Android: RingtoneManager](https://developer.android.com/reference/android/media/RingtoneManager) — API for accessing device ringtones.
- [Android: MediaPlayer](https://developer.android.com/reference/android/media/MediaPlayer) — API for audio playback.
- [Android: AudioAttributes](https://developer.android.com/reference/android/media/AudioAttributes) — `USAGE_ALARM` for alarm audio routing.

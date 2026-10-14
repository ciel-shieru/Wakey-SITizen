# ADR-0020: Device Calendar timetable persistence for "next event" display

## Status

Proposed

## Date

2026-10-14

## Context

ADR-0018 added a "next event" card to the Home screen that displays the user's upcoming timetable event (course code, title, time, location). The `HomeViewModel` computes this card by combining four flows (`schedules`, `overrides`, `config`, `debugConfig`) with `timetableRepository.eventsFlow` (a fifth flow) inside a single `combine` block. The `eventsFlow` emits `List<TimetableEvent>` derived from the timetable DataStore.

This works correctly for **Remote Server** discovery mode: `SyncWorker.fetchTimetable()` calls `timetableClient.fetch()`, receives a `WakeyResponse` containing all events, and calls `dataStore.saveTimetable(result.response)` which serializes the `WakeyResponse` to the `"last_timetable"` DataStore key. `TimetableRepositoryImpl.eventsFlow` reads this key, deserializes the JSON, and emits the events — so the Home screen's next event card displays correctly.

This does **not** work for **Device Calendar** discovery mode. In Device Calendar mode, `SyncWorker.fetchTimetable()` reads events from `CalendarEventSource.getEventsForDate()` for a 7-day window and converts them to `TimetableEvent` objects for alarm computation. However, these events are never persisted to the timetable DataStore — they are used only for computing alarm schedules (step 3 of the SyncWorker pipeline) and then discarded. The `"last_timetable"` DataStore key remains empty (or contains stale Remote Server data if the user ever switched modes). Consequently, `TimetableRepositoryImpl.eventsFlow` returns an empty list, and the Home screen's next event card is never shown in Device Calendar mode.

ADR-0002 established the two discovery modes as mutually exclusive: a user selects either Remote Server or Device Calendar, not both. ADR-0019 confirmed that Device Calendar fetching reads from `CalendarEventSource` for a date range and produces `TimetableEvent` objects with synthetic course codes (`cal-<hex hash>`). The gap is purely in persistence: Device Calendar events are ephemeral (used only for alarm computation), while Remote Server events are durable (persisted to DataStore).

## Decision Drivers

- **Feature parity**: The "next event" display (ADR-0018) must work in both discovery modes. Users who rely on Device Calendar should see the same information as Remote Server users.
- **Discovery-mode isolation**: Remote Server and Device Calendar events are fundamentally different data sources with different lifecycles. Remote Server events are versioned (`schema_version`, `generated_at`) and replaced atomically on each fetch. Device Calendar events are read from the device calendar in real-time and may change independently of the app. Mixing them in a single DataStore key would conflate these concerns.
- **Minimal coupling**: The persistence layer should not require changes to the `TimetableEvent` model or the `WakeyResponse` serialization format. Device Calendar events already exist as `TimetableEvent` objects; they should be persisted without transformation.
- **Backward compatibility**: Existing users who have only used Remote Server mode must not lose their timetable data. The `"last_timetable"` DataStore key and its `WakeyResponse` format must remain unchanged.
- **SyncWorker as the single writer**: Following ADR-0007's architecture, `SyncWorker` is the sole component that fetches and writes timetable data. Any persistence changes for Device Calendar events should be localized to `SyncWorker.fetchTimetable()`.
- **TimetableRepository reads from the appropriate source**: `TimetableRepositoryImpl` must read from the DataStore key corresponding to the active discovery mode. This requires it to be aware of the current mode, which is already available via `AlarmConfigStore.configFlow`.

## Considered Options

### Option A: Separate DataStore key for Device Calendar events + discovery-mode-aware TimetableRepositoryImpl (Selected)

Create a new `DeviceCalendarTimetableStore` object (parallel to `TimetableStore`) with its own DataStore key (`"last_device_calendar_events"`) that stores a serializable list of `TimetableEvent` objects. `SyncWorker.fetchTimetable()` in Device Calendar mode writes the fetched events to this new store and clears it when switching to Remote Server mode. `TimetableRepositoryImpl` reads from `AlarmConfigStore.configFlow` to determine the active discovery mode and selects the appropriate DataStore key: `"last_timetable"` for Remote Server, `"last_device_calendar_events"` for Device Calendar.

### Option B: Single unified DataStore key with a `source` field on TimetableEvent

Add a `source: String` field to `TimetableEvent` (e.g., `"remote_server"` or `"device_calendar"`) and store all events in the existing `"last_timetable"` key as a flat list. `TimetableRepositoryImpl` would not need to be discovery-mode-aware — it simply reads the single key. However, this requires changing the `TimetableEvent` data class, which is used throughout the codebase (alarm computation, debug logging, HomeViewModel, etc.), and breaks the separation of concerns between the two modes.

### Option C: Compute next event directly from CalendarEventSource in Device Calendar mode

Instead of persisting Device Calendar events, have `HomeViewModel` or a new use case read directly from `CalendarEventSource` when discovery mode is Device Calendar. This avoids new DataStore keys and makes `TimetableRepositoryImpl` unaware of discovery mode. However, it couples the UI layer to calendar permissions and introduces a second code path for the same logical operation (reading timetable events), increasing maintenance burden and test complexity.

### Option D: Persist Device Calendar events to the existing TimetableStore with a transformed WakeyResponse

In `SyncWorker.fetchTimetable()` Device Calendar branch, construct a synthetic `WakeyResponse` from the fetched events (with a fixed `schema_version`, current `generated_at`, and system timezone) and save it via `dataStore.saveTimetable()`. This avoids new stores but pollutes the `WakeyResponse` format with synthetic data that does not represent a real server response. Future maintainers reading the DataStore would see a `WakeyResponse` that could not have come from the server, making debugging confusing.

## Decision

We will choose Option A.

A new `DeviceCalendarTimetableStore` object will be created in the `data/store/` package with its own DataStore key (`"last_device_calendar_events"`). The store will serialize and deserialize a `List<TimetableEvent>` using `kotlinx.serialization`. This is a parallel to the existing `TimetableStore` which stores a single `WakeyResponse`.

`SyncWorker.fetchTimetable()` will be updated in two places:
1. **Device Calendar branch**: After fetching events from `CalendarEventSource`, the events will be saved to `DeviceCalendarTimetableStore` via a new `saveDeviceCalendarEvents()` extension function on `DataStore<Preferences>`.
2. **Remote Server branch**: After saving the fetched `WakeyResponse` via `saveTimetable()`, the Device Calendar events will be cleared via a new `clearDeviceCalendarEvents()` extension function to prevent stale Device Calendar data from appearing when the user switches to Remote Server mode.

`TimetableRepositoryImpl` will accept an additional constructor parameter: `Flow<DiscoveryMode>`. It will use this flow to select which DataStore key to read from:
- When `DiscoveryMode.RemoteServer`: read from `TimetableStore.timetableFlow()` (existing behavior).
- When `DiscoveryMode.DeviceCalendar`: read from `DeviceCalendarTimetableStore.deviceCalendarEventsFlow()`.

`AppModule.kt` will provide `Flow<DiscoveryMode>` by combining `AlarmConfigStore.configFlow` with a `.map { it.discoveryMode }` projection. The existing `provideTimetableRepository` provider will be updated to include this new parameter.

## Rationale

### Why separate DataStore keys (Option A detail)

Remote Server events and Device Calendar events have fundamentally different semantics:
- **Lifecycle**: Remote Server events are versioned and replaced atomically. Device Calendar events are read fresh from the device calendar on each sync and may change without any app action.
- **Serialization format**: Remote Server events are stored as `WakeyResponse` (a `@Serializable` data class with `schema_version`, `generated_at`, `timezone`, and `events`). Device Calendar events are a simple `List<TimetableEvent>` — there is no equivalent response wrapper because there is no server to provide metadata.
- **Staleness**: Remote Server events have a `generated_at` timestamp that indicates when the server last updated the timetable. Device Calendar events have no such metadata — they are always "current" as of the last sync.
- **Mutual exclusivity**: ADR-0002 established that the two modes are mutually exclusive. A single user never uses both modes simultaneously, so there is no need to merge or reconcile their data.

Separate keys preserve the existing `WakeyResponse` format for Remote Server users and provide a clean, purpose-built store for Device Calendar events. This follows the single-responsibility principle: each key stores data from exactly one source.

### Why TimetableRepositoryImpl becomes discovery-mode-aware

`TimetableRepository` is the abstraction that provides timetable events to the rest of the application. In the current architecture, it reads from a single DataStore key regardless of discovery mode. With two distinct event sources, the repository must route reads to the correct key based on the active mode.

This is not a violation of the port/implementation separation because:
1. The `TimetableRepository` interface (`domain/port/TimetableRepository.kt`) does not change — it still exposes `eventsFlow`, `getEventsForDate()`, `getEventsForRange()`, and `refreshTimetable()`. The discovery-mode routing is an internal implementation detail.
2. The discovery mode is already available in the DI graph via `AlarmConfigStore.configFlow`. Adding it as a constructor parameter is a minimal change that follows the existing pattern used by all other repository implementations (they all receive `DataStore<Preferences>` and read config from it).
3. The alternative — having the UI layer choose between two repository implementations based on discovery mode — would require conditional DI bindings or a factory pattern, which is more complex than a simple flow-based routing inside the existing implementation.

### Why DeviceCalendarTimetableStore stores `List<TimetableEvent>` directly

The existing `TimetableStore` stores `WakeyResponse` because that is the server's response format and includes metadata (`schema_version`, `generated_at`, `timezone`). Device Calendar events have no such metadata — they are read directly from the device calendar as `ExternalEvent` objects, converted to `TimetableEvent`, and there is no natural wrapper type. Storing a raw `List<TimetableEvent>` is the simplest representation.

The `TimetableEvent` data class is already `@Serializable`-compatible (it is a Kotlin data class with serializable fields: `String`, `LocalDateTime`, `EventKind`). The `DeviceCalendarTimetableStore` will use `JsonConfig.instance.encodeToString()` and `decodeFromString<List<TimetableEvent>>()` directly.

### Why clear Device Calendar events when switching to Remote Server mode

When a user switches from Device Calendar to Remote Server mode, stale Device Calendar events in the DataStore would cause `eventsFlow` to emit old data until the next Remote Server sync. Since the two modes are mutually exclusive (ADR-0002), clearing Device Calendar events on Remote Server fetch ensures that the correct source is always active. This is a one-way clear: Remote Server mode writes to its own key and does not need to clear Remote Server data when switching to Device Calendar (the next Device Calendar sync will overwrite it).

### Why not Option B (unified key with source field)

Adding a `source` field to `TimetableEvent` would require changes in many places:
- The `TimetableEvent` data class definition.
- `TimetableEvent.fromJson()` — the Remote Server path would need to set `source = "remote_server"`.
- The Device Calendar branch in `SyncWorker.fetchTimetable()` — would need to set `source = "device_calendar"`.
- All deserialization paths in `TimetableRepositoryImpl` — would need to handle the new field.
- Debug logging, alarm computation, and HomeViewModel — all reference `TimetableEvent` fields.

This is a high-churn change across the entire codebase for a problem that can be solved with a single new store object. The unified key also loses the clear separation between the two data sources, making debugging harder (a `TimetableEvent` in the DataStore no longer tells you which source it came from without the `source` field, and the `WakeyResponse` wrapper would need to carry source information too).

### Why not Option C (read directly from CalendarEventSource in UI)

Reading from `CalendarEventSource` in `HomeViewModel` would:
- Require `HomeViewModel` to depend on `CalendarEventSource`, which is currently only used by `SyncWorker` and `AdjustForExternalEvents`. This adds a new dependency that requires calendar permissions.
- Duplicate the event-fetching logic: `SyncWorker` fetches events for alarm computation, and `HomeViewModel` would fetch them again for display. Two separate calls to `CalendarEventSource.getEventsForDate()` are wasteful and could produce inconsistent results if the calendar changes between calls.
- Require conditional logic in the ViewModel: it would need to know the discovery mode and choose between `TimetableRepository.eventsFlow` (Remote Server) and `CalendarEventSource` (Device Calendar). This violates the principle that the repository abstraction should hide the data source.

### Why not Option D (synthetic WakeyResponse)

Constructing a fake `WakeyResponse` from Device Calendar events would:
- Store data in the existing `"last_timetable"` key, which means `TimetableRepositoryImpl` does not need changes. However, the stored `WakeyResponse` would have a synthetic `generated_at` (current time) and a hardcoded `schema_version` (e.g., `999`) that does not match any real server response. This is confusing for debugging — a developer inspecting the DataStore would see a `WakeyResponse` that could not have come from the server.
- Risk future conflicts: if the server's `schema_version` changes or new fields are added to `WakeyResponse`, the synthetic response might need to be updated to match, coupling Device Calendar persistence to server schema changes.
- Still requires `TimetableRepositoryImpl` to route reads based on discovery mode (because Remote Server mode overwrites the same key), so it does not eliminate the discovery-mode-awareness requirement.

## Consequences

### Positive

- **Next event display works in both modes**: Device Calendar users will see their upcoming timetable events on the Home screen, matching the Remote Server experience.
- **Clean separation of concerns**: Each discovery mode has its own DataStore key with a format appropriate to its data source. No synthetic wrappers or source tags needed.
- **No changes to TimetableEvent model**: The domain model remains untouched. `TimetableEvent` continues to represent a timetable event without awareness of its source.
- **No changes to TimetableRepository interface**: The public contract (`eventsFlow`, `getEventsForDate()`, etc.) remains unchanged. Discovery-mode routing is an internal implementation detail.
- **No changes to WakeyResponse or TimetableStore**: Existing Remote Server data and its serialization format are preserved exactly. Backward compatibility is maintained.
- **Single writer principle preserved**: `SyncWorker` remains the sole component that fetches and writes timetable data. No new fetch paths are introduced.
- **Stale data prevention**: Clearing Device Calendar events when switching to Remote Server mode prevents stale data from appearing after a mode switch.

### Negative

- **TimetableRepositoryImpl is discovery-mode-aware**: The implementation now depends on `Flow<DiscoveryMode>` from `AlarmConfigStore.configFlow`. This is a new constructor parameter that requires updating the Hilt binding in `AppModule.kt`.
- **New DataStore key**: The `"last_device_calendar_events"` key adds to the DataStore footprint. Each sync write stores a JSON-serialized `List<TimetableEvent>`, which is typically smaller than a full `WakeyResponse` (no `schema_version`, `generated_at`, or `timezone` fields).
- **Serialization change**: `DeviceCalendarTimetableStore` serializes `List<TimetableEvent>` directly, while `TimetableStore` serializes `WakeyResponse`. This means the two stores use different serialization paths, which is a minor but notable asymmetry.
- **Two stores to maintain**: `TimetableStore` and `DeviceCalendarTimetableStore` are parallel objects with similar structure. Any future changes to the serialization pattern (e.g., encryption, compression) must be applied to both.
- **Test impact**: Existing tests for `TimetableRepositoryImpl` that use `FakeTimetableRepository` (which provides a hardcoded `eventsFlow`) are unaffected. However, new tests will be needed to verify discovery-mode routing and the new store's serialization.

### Neutral or operational

- **New file**: `app/src/main/java/com/cielshieru/wakey/data/store/DeviceCalendarTimetableStore.kt` — parallel to `TimetableStore.kt`, with `saveDeviceCalendarEvents()`, `clearDeviceCalendarEvents()`, and `deviceCalendarEventsFlow()`.
- **Modified file**: `app/src/main/java/com/cielshieru/wakey/data/repository/TimetableRepositoryImpl.kt` — new constructor parameter `configFlow: Flow<AlarmConfig>`, internal routing logic in `eventsFlow`, `getEventsForDate()`, and `getEventsForRange()`.
- **Modified file**: `app/src/main/java/com/cielshieru/wakey/worker/SyncWorker.kt` — Device Calendar branch writes to `DeviceCalendarTimetableStore`; Remote Server branch clears Device Calendar events.
- **Modified file**: `app/src/main/java/com/cielshieru/wakey/di/AppModule.kt` — `provideTimetableRepository()` adds `Flow<DiscoveryMode>` parameter derived from `AlarmConfigStore.configFlow`.
- **No new DI bindings**: `Flow<DiscoveryMode>` is derived from the existing `datastore.data.configFlow().map { it.discoveryMode }`. No new `@Provides` function needed.
- **No ProGuard changes**: No reflection or keep rules needed for the new store or routing logic.
- **JsonConfig reuse**: `DeviceCalendarTimetableStore` uses the same `JsonConfig.instance` as `TimetableStore`, so no new serialization configuration is required.

## Alternatives Considered

### Option A: Separate DataStore key + discovery-mode-aware TimetableRepositoryImpl (Selected)

Clean separation of concerns, no model changes, no format pollution. The trade-off is that `TimetableRepositoryImpl` becomes aware of the discovery mode, which is a slight departure from the current "blind to source" design. This is acceptable because the awareness is internal to the implementation and does not affect the public interface.

### Option B: Unified DataStore key with `source` field on TimetableEvent

Simpler repository (no discovery-mode routing) but higher implementation cost (changes to `TimetableEvent` across the entire codebase). The unified key also loses the clear separation between data sources. Rejected for higher churn and reduced clarity.

### Option C: Read directly from CalendarEventSource in UI layer

Avoids new DataStore keys and keeps `TimetableRepositoryImpl` source-agnostic. However, it duplicates event-fetching logic, couples the UI to calendar permissions, and requires conditional logic in the ViewModel. Rejected for duplication and coupling concerns.

### Option D: Persist Device Calendar events as synthetic WakeyResponse

Minimal code changes (no new store, no repository changes) but pollutes the `WakeyResponse` format with synthetic data and still requires discovery-mode routing in the repository. Rejected for data integrity concerns and incomplete solution.

## Follow-Ups

- Create `DeviceCalendarTimetableStore.kt` with `saveDeviceCalendarEvents()`, `clearDeviceCalendarEvents()`, and `deviceCalendarEventsFlow()` extension functions on `DataStore<Preferences>`.
- Update `SyncWorker.fetchTimetable()` Device Calendar branch to call `saveDeviceCalendarEvents()` after fetching.
- Update `SyncWorker.fetchTimetable()` Remote Server branch to call `clearDeviceCalendarEvents()` after saving the `WakeyResponse`.
- Update `TimetableRepositoryImpl` constructor to accept `Flow<DiscoveryMode>` and route `eventsFlow`, `getEventsForDate()`, and `getEventsForRange()` based on the current mode.
- Update `AppModule.kt` `provideTimetableRepository()` to pass `datastore.data.configFlow().map { it.discoveryMode }` as the discovery mode flow.
- Add unit tests for `DeviceCalendarTimetableStore` serialization/deserialization.
- Add unit tests for `TimetableRepositoryImpl` discovery-mode routing (verify correct key is read for each mode).
- Add integration tests for `SyncWorker` verifying Device Calendar events are persisted and Remote Server events clear them.
- Consider whether `getEventsForDate()` and `getEventsForRange()` in `TimetableRepositoryImpl` should also be discovery-mode-aware (currently they read from the same key as `eventsFlow`, so the routing applies to all three methods).

## References

- ADR-0002: ICS discovery and source selection — establishes the two mutually exclusive discovery modes (Remote Server and Device Calendar).
- ADR-0007: Background sync mechanism and boot recovery — defines `SyncWorker` as the sole timetable fetcher and writer.
- ADR-0012: Debug mode and target date override — defines `DebugConfigRepository` and `targetDateOverrideMillis`.
- ADR-0015: Bedtime display on Home screen — same pattern of adding a card to Home screen via ViewModel state.
- ADR-0018: Next event display on Home screen — establishes the `nextEvent` card and its dependence on `TimetableRepository.eventsFlow`.
- ADR-0019: Debug mode target date for DeviceCalendar fetch — confirms Device Calendar events are fetched from `CalendarEventSource` and converted to `TimetableEvent`.
- `app/src/main/java/com/cielshieru/wakey/data/store/TimetableStore.kt` — existing store for Remote Server `WakeyResponse`.
- `app/src/main/java/com/cielshieru/wakey/data/repository/TimetableRepositoryImpl.kt` — current implementation that reads from a single DataStore key.
- `app/src/main/java/com/cielshieru/wakey/worker/SyncWorker.kt` — `fetchTimetable()` at lines 284-327, Remote Server branch at lines 291-305, Device Calendar branch at lines 306-326.
- `app/src/main/java/com/cielshieru/wakey/ui/home/HomeViewModel.kt` — `eventsFlow` consumption at line 135, next event computation at lines 94-121.
- `app/src/main/java/com/cielshieru/wakey/domain/model/AlarmConfig.kt` — `DiscoveryMode` sealed interface.
- `app/src/main/java/com/cielshieru/wakey/data/store/AlarmConfigStore.kt` — `configFlow` that provides `AlarmConfig` including `discoveryMode`.

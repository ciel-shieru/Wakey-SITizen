# ADR-0018: Next event display on Home screen

## Status

Proposed

## Date

2026-10-12

## Context

ADR-0003 established the alarm scheduling policy, ADR-0004 the bedtime reminder algorithm, and ADR-0015 added bedtime display to the Home screen. The Home screen currently shows the next alarm, bedtime (when enabled), override status, sync state, and scheduled alarms — but it does not show any information about the user's upcoming timetable events.

Students use wakey-sitizen to avoid missing classes. While the app computes alarms from the timetable, users have no way to glance at their next class from the Home screen. The timetable data is already fetched and stored via `TimetableRepository` (ADR-0002 defines the source), and `TimetableEvent` objects contain all the fields needed for display (`courseCode`, `title`, `dtstart`, `location`). The `ZoneProvider` interface (already provided as a singleton in `AppModule.kt:56`) gives access to the device timezone.

The requirement is to display the details of the first event of the day on the Home screen, below the existing "Next Alarm" card. The logic should show today's first event if it has not yet started (`dtstart > now`), or tomorrow's first event if today's has already started or ended (`dtstart <= now`). This mirrors the pattern established in ADR-0015 for bedtime display: a secondary information card computed in the ViewModel's combine block and rendered conditionally in the UI.

## Decision Drivers

- **Timetable visibility**: Students should see their next class at a glance on the Home screen, alongside their alarm information.
- **Conditional display logic**: Show today's first event if it hasn't started yet; otherwise show tomorrow's first event.
- **Consistency with existing time formatting**: Event times should use the same 12-hour format ("h:mm a") as the rest of the Home screen via `HomeUiState.TIME_FORMATTER`.
- **Debug mode awareness**: The same `now` variable used for alarm and bedtime calculations must also be used for event comparison, ensuring debug mode correctness.
- **Minimal coupling**: The Home screen should not directly depend on `TimetableRepository`. The ViewModel should own the event selection logic and expose it as UI state.
- **Reuse existing interfaces**: `TimetableRepository.getEventsForRange()` and `ZoneProvider.zone` are already available — no new interfaces needed.
- **Graceful degradation**: When no timetable events exist (no timetable configured, empty day), the card should not be rendered.

## Considered Options

### Option A: `nextEvent: NextEventUiState?` field in `HomeUiState` computed in ViewModel combine block (Selected)

Add a `NextEventUiState` data class to `HomeViewModel.kt` with fields `courseCode`, `title`, `dtstartFormatted`, and `location`. Add `nextEvent: NextEventUiState? = null` to `HomeUiState`. Inject `TimetableRepository` and `ZoneProvider` into `HomeViewModel`. Compute `nextEvent` inside the existing four-way `combine` block using the shared `now` variable, `timetableRepository.getEventsForRange(today, tomorrow)`, and the same `h:mm a` formatter. `HomeScreen` renders the event in a `Card` with `secondaryContainer` color below the "Next Alarm" card.

### Option B: Separate `nextEvent` StateFlow on `HomeViewModel`

Expose `nextEvent: StateFlow<NextEventUiState?>` as a separate flow derived from timetable data. `HomeScreen` collects this flow independently. This decouples event display from the main `uiState` but introduces a second coroutine flow collection that could drift out of sync.

### Option C: Show only the next alarm's associated course code

Instead of fetching timetable events separately, derive the course code from the first alarm's schedule day and show it alongside the next alarm text. This avoids new dependencies but loses title, time, and location information, and does not handle the "today's event already started" fallback case.

### Option D: Compute next event in `HomeScreen` directly

Pass raw timetable data to `HomeScreen` and compute the next event display in the composable. This avoids ViewModel changes but violates MVVM by putting business logic in the UI layer and requires passing additional parameters through the composable.

## Decision

We will choose Option A.

A `NextEventUiState` data class will be added to `HomeViewModel.kt` with fields `courseCode: String`, `title: String`, `dtstartFormatted: String`, and `location: String`. A `nextEvent: NextEventUiState? = null` field will be added to `HomeUiState`.

`TimetableRepository` and `ZoneProvider` will be injected into `HomeViewModel`'s constructor (both are already provided as singletons in `AppModule.kt`). The next event will be computed inside the existing four-way `combine` block using the shared `now` variable.

The computation logic:
1. Convert `now` (Instant) to local `LocalDateTime` using `ZoneProvider.zone`.
2. Fetch events for today and tomorrow via `timetableRepository.getEventsForRange(today, tomorrow)`.
3. Find today's first event (minimum `dtstart` among today's events).
4. Find tomorrow's first event (minimum `dtstart` among tomorrow's events).
5. If today's first event exists AND its `dtstart` is after `now` → show today's event.
6. Otherwise → show tomorrow's first event (may be null if no events tomorrow).
7. Format the selected event into `NextEventUiState` using `HomeUiState.TIME_FORMATTER` ("h:mm a").

`HomeScreen` will render the next event in a `Card` with `secondaryContainer` color, positioned below the existing "Next Alarm" card (after line 72, before the override card at line 74). The layout will use a `Column` with 16dp padding, spacedBy(4dp), containing a "Next Event" label in `titleMedium` with `onSecondaryContainer` color, the course code and title in `bodyLarge`, and the time and location in a `Row` with `bodyMedium`.

## Rationale

### Why compute in the existing combine block (Option A detail)

The `HomeViewModel` already combines four flows (`schedules`, `overrides`, `config`, `debugConfig`) in a single `init` block. Adding next event computation to this existing combine block:
- Ensures the event display is always derived from the same `now` value used for alarm and bedtime calculations, which is critical for debug mode correctness (ADR-0012).
- Avoids introducing a separate flow collection (Option B) that could drift out of sync with the main UI state.
- Keeps the event selection logic in the ViewModel rather than the UI layer (Option C), preserving MVVM separation.
- Exposes a structured UI state object (rather than raw timetable data) to the UI, keeping the composable focused on presentation.

### Why `NextEventUiState` data class instead of raw `TimetableEvent?` in UI state

The UI layer should not need to know about `TimetableEvent.dtstart: LocalDateTime` or format instants itself. By converting to display-ready strings (`dtstartFormatted`) in the ViewModel, the UI composable remains simple and formatting logic is centralized. This also makes it trivial to change the format later without touching the UI. The data class separates presentation concerns from domain model concerns.

### Why `TimetableRepository` and `ZoneProvider` injection (Option A detail)

Both interfaces are already provided as singletons in `AppModule.kt` (`ZoneProvider` at line 56, `TimetableRepository` at line 119). Adding them to `HomeViewModel`'s constructor is a minimal change that follows the existing DI pattern used by all other ViewModels in the app. No new DI bindings are needed.

### Why fetch today + tomorrow range (Option A detail)

The logic requires comparing today's first event against `now` to decide whether to show today or tomorrow. Fetching both days in a single `getEventsForRange(today, tomorrow)` call is more efficient than making two separate `getEventsForDate()` calls. It also ensures consistency — both days' data comes from the same source and is available simultaneously for the min-selection logic.

### Why Card with `secondaryContainer` color below "Next Alarm"

The override card (ADR-0013) uses `secondaryContainer` color to visually distinguish secondary information from primary content. The next event display is secondary to the alarm information but still important for students. Placing it below the "Next Alarm" card reflects the temporal relationship: alarms are actionable (they will ring), while the next event is informational (it tells you what's coming). Using `secondaryContainer` maintains visual hierarchy without competing with the primary alarm display.

### Why show tomorrow's event when today's has already started or ended

This mirrors the alarm scheduling philosophy: users care about what's coming up next, not what has already passed. If a student opens the app at 3 PM and their first class was at 8 AM, showing that past class would be unhelpful. Showing tomorrow's first event keeps the display forward-looking and actionable.

## Consequences

### Positive

- **Timetable visibility**: Students can see their next class at a glance on the Home screen without opening a separate timetable view.
- **Forward-looking display**: The "show tomorrow if today's passed" logic ensures the display is always relevant, not stale.
- **Consistent formatting**: Uses the same "h:mm a" formatter as the rest of the Home screen, reducing format inconsistencies.
- **Debug mode correctness**: Shared `now` variable ensures next event display matches alarm display in debug mode (ADR-0012).
- **Graceful degradation**: When no timetable events exist, no card is rendered — the UI degrades cleanly.
- **No new interfaces**: Reuses `TimetableRepository` and `ZoneProvider` which are already provided in DI.
- **Single combine block**: Computation is added to the existing four-way combine, not as a separate flow or method.

### Negative

- **ViewModel coupling to TimetableRepository**: `HomeViewModel` now depends on `TimetableRepository`, which was previously only used by `SyncWorker`. This is acceptable because `TimetableRepository` is a well-tested interface with a fake implementation for unit tests.
- **Additional suspend call in combine block**: `timetableRepository.getEventsForRange()` is a suspend function called inside the combine block. Since it uses the cached timetable data (already fetched by `SyncWorker` per ADR-0007), this should be fast, but it does add a coroutine dispatch point to the combine flow.
- **ViewModel constructor change**: Adding two new constructor parameters to `HomeViewModel` requires updating all test setups and any direct instantiation (though Hilt handles production injection).
- **UI state grows**: `HomeUiState` gains a nested data class field. For a ViewModel this small, the impact is negligible.
- **Card stacking**: The Home screen now has an additional `Card` below the "Next Alarm" card. On very narrow screens, this adds scrollable content.

### Neutral / operational

- **New data class**: `NextEventUiState(courseCode: String, title: String, dtstartFormatted: String, location: String)` added to `HomeViewModel.kt`.
- **New field**: `nextEvent: NextEventUiState? = null` added to `HomeUiState`.
- **Constructor change**: `TimetableRepository` and `ZoneProvider` added to `HomeViewModel` constructor.
- **Combine block change**: New logic after the existing four-way computations to fetch and select the next event.
- **UI rendering**: New conditional `Card` in `HomeScreen` below the "Next Alarm" card, rendered only when `nextEvent` is non-null.
- **No new DI bindings**: `TimetableRepository` and `ZoneProvider` are already provided as singletons in `AppModule.kt`.
- **No ProGuard changes**: No reflection or keep rules needed.
- **Testing**: New unit tests for `HomeViewModel` verifying next event derivation from `TimetableRepository` results. `FakeTimetableRepository` and `FakeZoneProvider` are already available in the test fakes directory.

## Alternatives Considered

### Option B: Separate `nextEvent` StateFlow on HomeViewModel

A separate `StateFlow<NextEventUiState?>` would decouple next event from the main UI state but introduces a second coroutine flow collection in `HomeViewModel.init`. This adds complexity without clear benefit for a single-value display. The separate flow could also drift out of sync with the main `uiState` if the combine block recomputes but the event flow does not. Rejected for simplicity — a single combined state is easier to reason about, following the same rationale as ADR-0015 for bedtime display.

### Option C: Show only the next alarm's associated course code

Deriving the course code from the first alarm's schedule day would be simpler (no new repository dependency) but loses critical information: event title, time, and location. It also does not handle the "today's event already started" fallback case — alarms may be set for a class that has already begun. Rejected because it provides insufficient information to be useful.

### Option D: Compute next event in HomeScreen directly

Putting `TimetableRepository.getEventsForRange()` in the composable violates MVVM by mixing data fetching and business logic with UI rendering. It would also require passing schedules, config, clock, and zone data through the composable parameters, increasing coupling between the ViewModel and UI. Rejected for architectural consistency.

## Follow-Ups

- Add `NextEventUiState` data class to `HomeViewModel.kt` with fields `courseCode`, `title`, `dtstartFormatted`, `location`.
- Add `nextEvent: NextEventUiState? = null` field to `HomeUiState`.
- Inject `TimetableRepository` and `ZoneProvider` into `HomeViewModel` constructor.
- Compute next event inside the existing four-way combine block in `HomeViewModel.init`, using the shared `now` variable.
- Fetch events for today and tomorrow via `timetableRepository.getEventsForRange(today, tomorrow)`.
- Select today's first event if `dtstart > now`, otherwise select tomorrow's first event.
- Format event time using `HomeUiState.TIME_FORMATTER` ("h:mm a") when non-null.
- Render new `Card` in `HomeScreen` below the "Next Alarm" card, conditionally shown when `nextEvent` is non-null.
- Use `secondaryContainer` color and `onSecondaryContainer` text color for the card.
- Unit tests for `HomeViewModel` verifying next event derivation from timetable events.
- Unit tests verifying `nextEvent` is `null` when no timetable events exist for both today and tomorrow.
- Consider whether the next event should also be included in the bedtime notification message (currently handled by `NotificationService` per ADR-0004).
- Consider whether the next event display should show the event's end time in addition to the start time.

## References

- ADR-0002: ICS discovery and source selection — defines timetable data source and `TimetableClient`.
- ADR-0003: alarm scheduling policy — defines alarm schedule computation and first-alarm-time derivation.
- ADR-0004: bedtime reminder algorithm — defines `BedtimeComputer.compute()` and bedtime notification behavior.
- ADR-0007: background sync mechanism and boot recovery — defines `SyncWorker` timetable fetching.
- ADR-0012: debug mode and target date override — defines shared `now` variable pattern used in `HomeViewModel`.
- ADR-0013: reset overrides from home — defines override card styling with `secondaryContainer` color.
- ADR-0015: bedtime display on Home screen — most similar ADR; same pattern of adding a card to Home screen via ViewModel state.
- `app/src/main/java/com/cielshieru/wakey/domain/port/TimetableRepository.kt` — `getEventsForRange(start, end): List<TimetableEvent>`.
- `app/src/main/java/com/cielshieru/wakey/domain/port/ZoneProvider.kt` — `val zone: ZoneId`.
- `app/src/main/java/com/cielshieru/wakey/domain/model/TimetableEvent.kt` — `courseCode`, `title`, `dtstart: LocalDateTime`, `location`.
- `app/src/main/java/com/cielshieru/wakey/di/AppModule.kt` — `ZoneProvider` (line 56) and `TimetableRepository` (line 119) providers.
- `app/src/main/java/com/cielshieru/wakey/ui/home/HomeViewModel.kt` — current `HomeUiState` and combine block.
- `app/src/main/java/com/cielshieru/wakey/ui/home/HomeScreen.kt` — current UI (next alarm card at line 52).
- `app/src/test/kotlin/com/cielshieru/wakey/fakes/FakeTimetableRepository.kt` — existing fake for unit tests.
- `app/src/test/kotlin/com/cielshieru/wakey/fakes/FakeZoneProvider.kt` — existing fake for unit tests.
- `app/src/test/kotlin/com/cielshieru/wakey/ui/home/HomeViewModelTest.kt` — existing ViewModel tests.

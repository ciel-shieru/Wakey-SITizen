# ADR-0015: Bedtime reminder display on Home screen

## Status

Proposed

## Date

2026-10-11

## Context

ADR-0004 established the bedtime reminder algorithm: given the first wake-up alarm time, user-configured sleep duration, and bedtime offset, compute a reminder time. The `BedtimeComputer.compute()` method already implements this logic and returns `null` when bedtime is disabled, no schedules exist, or the reminder has already passed.

The bedtime reminder can be delivered as a notification via `AlarmManager` + `AlarmReceiver` (ADR-0004), but users currently have no way to see their calculated bedtime on the Home screen. The Home screen displays "Next alarm" and override status, but the bedtime — which is a key part of the app's value proposition for students planning their sleep schedule — is invisible unless they navigate to Settings or receive the notification.

The requirement is to display the calculated bedtime reminder time above the "next alarm" label on the Home screen when the bedtime feature is enabled in Settings. The display should use the same 12-hour time format (e.g., "10:30 PM") as the rest of the UI, and should be hidden when bedtime is disabled or when `BedtimeComputer` returns null.

## Decision Drivers

- **Visibility**: Users should see their calculated bedtime at a glance on the Home screen, alongside their alarm information.
- **Consistency with existing time formatting**: The bedtime should use the same 12-hour format ("h:mm a") as the next alarm display and scheduled alarm list.
- **Conditional display**: Bedtime should only appear when enabled in Settings (`bedtimeEnabled = true`) and when a valid bedtime reminder exists (i.e., `BedtimeComputer.compute()` returns non-null).
- **Shared `now` variable**: Debug mode must be respected — the same `now` value used for alarm calculations must also be used for bedtime computation, ensuring consistent behavior in debug mode.
- **Minimal coupling**: The Home screen should not directly depend on `BedtimeComputer`. The ViewModel should own the bedtime display logic and expose it as UI state.
- **Preserve existing BedtimeComputer null logic**: When bedtime is disabled, no schedules exist, or the reminder has passed, `BedtimeComputer` already returns `null`. This null-safety should be preserved in the display logic.

## Considered Options

### Option A: `bedtimeText` field in `HomeUiState` computed in ViewModel combine block (Selected)

Add a `bedtimeText: String?` field to `HomeUiState`. Inject `BedtimeComputer` (as a singleton object, already a companion object) into `HomeViewModel`. Compute `bedtimeText` inside the existing four-way `combine` block using the shared `now` variable, `BedtimeComputer.compute()`, and the same `h:mm a` formatter. `HomeScreen` renders the bedtime in a `Card` with `Column` layout above the "next alarm" card, using `titleMedium` style and a muted color.

### Option B: Separate `bedtimeReminder` StateFlow on `HomeViewModel`

Expose `bedtimeReminder: StateFlow<BedtimeReminder?>` as a separate flow derived from the alarm schedules and config. `HomeScreen` collects this flow independently and renders the bedtime when non-null. This decouples bedtime from the main `uiState` but introduces a second coroutine flow collection.

### Option C: Compute bedtime in `HomeScreen` directly

Pass the raw data (schedules, config, clock) to `HomeScreen` and compute the bedtime display text directly in the composable. This avoids ViewModel changes but violates the MVVM pattern by putting business logic in the UI layer.

### Option D: Display full `BedtimeReminder` object in UI state

Add a `bedtimeReminder: BedtimeReminder?` field to `HomeUiState` and let `HomeScreen` format the time itself. This gives the UI maximum flexibility but couples the UI to the `BedtimeReminder` model and its `triggerAt` field structure.

## Decision

We will choose Option A.

A `bedtimeText: String?` field will be added to `HomeUiState`. The existing `BedtimeComputer` (a singleton `object`) will be called inside the existing four-way `combine` block in `HomeViewModel.init`, using the same `now` variable that is already computed for alarm calculations.

The bedtime text will be formatted using the existing `HomeUiState.TIME_FORMATTER` ("h:mm a") to ensure consistency with the next alarm display and scheduled alarm list. When `BedtimeComputer.compute()` returns `null` (bedtime disabled, no schedules, or reminder passed), `bedtimeText` will be `null` and the Home screen will not render the bedtime card.

`HomeScreen` will render the bedtime in a `Card` containing a `Column` with the label "Bedtime" and the formatted time, positioned above the existing "next alarm" card. The text will use `MaterialTheme.typography.titleMedium` style with a muted color (`MaterialTheme.colorScheme.onSurfaceVariant`).

## Rationale

### Why compute in the existing combine block (Option A detail)

The HomeViewModel already combines four flows (`schedules`, `overrides`, `config`, `debugConfig`) in a single `init` block. Adding bedtime computation to this existing combine block:
- Ensures the bedtime is always derived from the same `now` value used for alarm calculations, which is critical for debug mode correctness.
- Avoids introducing a separate flow collection (Option B) that could drift out of sync with the main UI state.
- Keeps the bedtime computation in the ViewModel rather than the UI layer (Option C), preserving MVVM separation.
- Exposes only a display-ready string (Option D) rather than a domain model, keeping the UI state focused on presentation concerns.

### Why `bedtimeText: String?` instead of `BedtimeReminder?` in UI state

The UI layer should not need to know about `BedtimeReminder.triggerAt` or format instants itself. By converting to a display string in the ViewModel, the UI composable remains simple and the formatting logic is centralized. This also makes it trivial to change the format later (e.g., adding seconds or timezone info) without touching the UI.

### Why Card with Column layout above "next alarm" (Option A detail)

The existing Home screen uses `Card` components for informational sections (next alarm, override status, stale data warning). The bedtime display should follow this pattern for visual consistency. Placing it above the "next alarm" card reflects the temporal relationship: bedtime comes before the first alarm. Using `titleMedium` with `onSurfaceVariant` color provides a muted, secondary-information appearance that does not compete with the primary "Next alarm" headline.

### Why preserve BedtimeComputer null logic

`BedtimeComputer.compute()` already handles all edge cases:
- `bedtimeEnabled = false` → returns `null`
- No schedules → returns `null`
- Reminder already passed → returns `null`

The display logic should not duplicate these checks. When `bedtimeText` is `null`, the Home screen simply does not render the bedtime card. This keeps the ViewModel and UI code simple and ensures the BedtimeComputer remains the single source of truth for bedtime validity.

## Consequences

### Positive

- **Bedtime visibility**: Students can see their calculated bedtime at a glance on the Home screen without navigating to Settings.
- **Consistent formatting**: Uses the same "h:mm a" formatter as the rest of the Home screen, reducing format inconsistencies.
- **Debug mode correctness**: Shared `now` variable ensures bedtime display matches alarm display in debug mode.
- **Graceful degradation**: When bedtime is disabled or invalid, no card is rendered — the UI degrades cleanly.
- **No new dependencies**: `BedtimeComputer` is already a singleton object in the codebase. No new DI bindings or dependencies are needed.
- **Minimal ViewModel change**: The bedtime computation is added to the existing combine block, not as a separate flow or method.

### Negative

- **ViewModel coupling to BedtimeComputer**: `HomeViewModel` now calls `BedtimeComputer.compute()` directly. This is acceptable because `BedtimeComputer` is a pure computation object with no side effects, and it is already tested in isolation (`BedtimeComputerTest`).
- **UI state grows**: `HomeUiState` gains an additional field. For a ViewModel this small, the impact is negligible, but it is worth noting for future state management decisions.
- **Card stacking**: The Home screen now has an additional `Card` above the "next alarm" card. On very narrow screens, this adds scrollable content above the quick actions.

### Neutral / operational

- **New field**: `bedtimeText: String?` added to `HomeUiState` with default value `null`.
- **BedtimeComputer call**: `BedtimeComputer.compute(schedules, config, now)` called inside the existing combine block in `HomeViewModel.init`.
- **UI rendering**: New conditional `Card` in `HomeScreen` above the "next alarm" card, rendered only when `bedtimeText` is non-null.
- **No new DI bindings**: `BedtimeComputer` is a singleton `object`, already available without Hilt injection.
- **No ProGuard changes**: No reflection or keep rules needed.
- **Testing**: Existing `BedtimeComputerTest` covers the computation logic. New unit tests for `HomeViewModel` should verify that `bedtimeText` is correctly derived from `BedtimeComputer.compute()` results and that it is `null` when bedtime is disabled.

## Alternatives Considered

### Option B: Separate `bedtimeReminder` StateFlow on HomeViewModel

A separate `StateFlow<BedtimeReminder?>` would decouple bedtime from the main UI state but introduces a second coroutine flow collection in `HomeViewModel.init`. This adds complexity without clear benefit for a single-value display. The separate flow could also drift out of sync with the main `uiState` if the combine block recomputes but the bedtime flow does not. Rejected for simplicity — a single combined state is easier to reason about.

### Option C: Compute bedtime in HomeScreen directly

Putting `BedtimeComputer.compute()` in the composable violates MVVM by mixing business logic with UI rendering. It would also require passing schedules, config, and clock data through the composable parameters, increasing coupling between the ViewModel and UI. Rejected for architectural consistency.

### Option D: Display full BedtimeReminder object in UI state

Exposing `BedtimeReminder?` in `HomeUiState` would give the UI maximum flexibility but couples the UI to the domain model's structure (`triggerAt: Instant`). It also duplicates formatting logic across the notification system (which uses `BedtimeReminder.message`) and the UI. Rejected for separation of concerns — the ViewModel should own display formatting.

## Follow-Ups

- Add `bedtimeText: String?` field to `HomeUiState` with default `null`.
- Call `BedtimeComputer.compute(schedules, config, now)` in the existing combine block in `HomeViewModel.init`.
- Format bedtime using `HomeUiState.TIME_FORMATTER` ("h:mm a") when non-null.
- Render new `Card` in `HomeScreen` above the "next alarm" card, conditionally shown when `bedtimeText` is non-null.
- Use `titleMedium` style and `onSurfaceVariant` color for bedtime text.
- Unit tests for `HomeViewModel` verifying `bedtimeText` derivation from `BedtimeComputer.compute()` results.
- Unit tests verifying `bedtimeText` is `null` when bedtime is disabled, no schedules exist, or reminder has passed.
- Consider whether the bedtime should also appear in the status bar notification when the bedtime reminder fires (currently handled by `NotificationService` per ADR-0004).

## References

- ADR-0004: bedtime reminder algorithm — defines `BedtimeComputer.compute()`, edge cases, and notification delivery.
- ADR-0003: alarm scheduling policy — defines alarm schedule computation and first-alarm-time derivation.
- ADR-0012: debug mode and target date override — defines shared `now` variable pattern used in `HomeViewModel`.
- `app/src/main/java/com/cielshieru/wakey/domain/BedtimeComputer.kt` — current `compute()` implementation.
- `app/src/main/java/com/cielshieru/wakey/domain/model/BedtimeReminder.kt` — current `BedtimeReminder` data class.
- `app/src/main/java/com/cielshieru/wakey/domain/model/AlarmConfig.kt` — `bedtimeEnabled`, `targetSleepDurationHours`, `bedtimeReminderOffsetMinutes`.
- `app/src/main/java/com/cielshieru/wakey/ui/home/HomeViewModel.kt` — current `HomeUiState` and combine block.
- `app/src/main/java/com/cielshieru/wakey/ui/home/HomeScreen.kt` — current UI (next alarm card at line 52).
- `app/src/test/kotlin/com/cielshieru/wakey/domain/BedtimeComputerTest.kt` — existing tests for `BedtimeComputer`.
- `app/src/test/kotlin/com/cielshieru/wakey/ui/home/HomeViewModelTest.kt` — existing ViewModel tests.

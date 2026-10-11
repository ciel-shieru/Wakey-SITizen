# ADR-0013: Reset Skip & Custom Time overrides from Home screen

## Status

Proposed

## Date

2026-10-11

## Context

ADR-0006 established three manual override types (SNOOZE, SKIP_TOMORROW, CUSTOM_TIME) that allow users to temporarily modify or suppress upcoming alarms. ADR-0007 defined the 12-hour background sync cycle that automatically clears expired overrides and recomputes alarms.

Users who have applied SKIP_TOMORROW or CUSTOM_TIME overrides may want to revert them and return to the automatic schedule without waiting for the next sync cycle. Currently, the only way to remove overrides is:
1. Wait for them to expire naturally (24 hours or midnight).
2. Wait for the next 12-hour sync cycle to clear expired overrides.
3. Apply a new override (which takes precedence per ADR-0006 priority rules).

There is no explicit "reset" or "cancel all overrides" action. This creates friction when a user skips tomorrow by mistake, changes their mind about a custom time, or wants to clean up stale state after a timetable change.

## Decision Drivers

- **Immediate user control**: Users should be able to clear overrides and return to the automatic schedule in one tap, without waiting for sync.
- **Sync integration**: Clearing overrides must trigger a fresh alarm recomputation so tomorrow's alarms are set correctly — the override removal alone does not cancel already-armed AlarmManager alarms.
- **Consistency with existing UI**: Override actions (Snooze, Skip Tomorrow, Set Time) are on the Home screen. Reset belongs alongside them.
- **Safety**: Reset must only affect overrides and alarm state. It must NOT modify the timetable, config, or debug settings.

## Considered Options

### Option A: New "Reset Overrides" OutlinedButton on HomeScreen (Selected)

Add a new `clearAllOverrides()` method on `AlarmRepository` that writes an empty list to the overrides DataStore. Add a `resetOverrides()` function on `HomeViewModel` that calls `clearAllOverrides()` then `syncOperations.syncImmediately()`. Render a new `OutlinedButton` below the existing "Skip Tomorrow" button with error color styling to signal a destructive action.

### Option B: Toggle behavior on "Skip Tomorrow" button

Change the "Skip Tomorrow" button to toggle — if SKIP_TOMORROW is already active, tap it to remove the skip instead of creating another. Requires tracking override state in the button's click handler and branching logic.

### Option C: Reset in Settings screen

Move the reset functionality to the Settings UI, alongside other override-related configuration. Keep the Home screen focused on alarm display and quick actions only.

### Option D: Swipe-to-reset gesture on override card

Add a swipe action on the override status card (the card showing "Tomorrow's alarms skipped" or "Custom alarm time at HH:mm") that reveals a "Reset" action. Requires custom swipe detection and UI state management.

## Decision

We will choose Option A.

A new `clearAllOverrides()` method will be added to the `AlarmRepository` interface (`domain/port/AlarmRepository.kt`). Its implementation in `AlarmRepositoryImpl` (`data/repository/AlarmRepositoryImpl.kt`) will write an empty list to the overrides DataStore via `OverrideStore.saveOverrides(emptyList())`.

`HomeViewModel` (`ui/home/HomeViewModel.kt`) will receive a new `resetOverrides()` function that:
1. Calls `alarmRepository.clearAllOverrides()` to remove all overrides from DataStore.
2. Calls `syncOperations.syncImmediately(SyncUseCase.UNIQUE_IMMEDIATE_SYNC)` to trigger a fresh sync cycle, which will recompute alarms without overrides and re-arm them via `AlarmService`.

A new `OutlinedButton` will be added to `HomeScreen` (`ui/home/HomeScreen.kt`) below the existing "Skip Tomorrow" button. The button will use `MaterialTheme.colorScheme.error` for its border and text colors to signal a destructive action, and will display a `Icons.Default.Cancel` icon with the label "Reset Overrides".

## Rationale

### Why a dedicated reset button (reject Option B)

Toggle behavior on "Skip Tomorrow" would conflate two distinct actions: creating a skip override and removing all overrides. The user might expect "Skip Tomorrow" to affect only the skip override, not CUSTOM_TIME or SNOOZE. A dedicated "Reset Overrides" button is unambiguous — it clears everything and returns to the automatic schedule.

### Why Home screen (reject Option C)

All override-related actions (Snooze 15m/30m/60m, Set Time, Skip Tomorrow) are on the Home screen. Moving reset to Settings would split override management across two screens and reduce discoverability. Users who apply overrides expect to manage them where they created them.

### Why OutlinedButton with error styling (Option A detail)

Reset is a destructive action — it removes user-applied overrides and cancels tomorrow's alarms. Error color styling (`MaterialTheme.colorScheme.error`) communicates this risk without requiring a confirmation dialog (which would add friction for a common, reversible action). The OutlinedButton style (rather than filled or tonal) visually de-emphasizes it relative to the primary actions, matching the hierarchy: Snooze/Set Time/Skip Tomorrow are FABs; Skip Tomorrow is an OutlinedButton; Reset is also an OutlinedButton but with error colors.

### Why trigger sync after clearing (Option A detail)

Clearing overrides from DataStore alone does not cancel alarms already set in `AlarmManager`. The fresh sync cycle performs the full pipeline (ADR-0007):
1. Clears expired overrides (no-op since they are already cleared).
2. Computes alarm schedules from timetable (ADR-0003).
3. Applies no overrides (since DataStore is empty).
4. Reconciles alarms — clear-then-set for each day, including cancelling days that had alarms before but are now free of overrides.

This ensures tomorrow's alarms reflect the automatic schedule without any override influence.

### Why use existing `SyncUseCase.UNIQUE_IMMEDIATE_SYNC` (Option A detail)

The `syncImmediately()` method already exists and is used by `SettingsViewModel` for manual sync triggers. Using `ExistingWorkPolicy.REPLACE` (via `enqueueUniqueWork`) ensures that if a sync is already in progress, it is cancelled and replaced — preventing stale override states from persisting across concurrent syncs.

## Consequences

### Positive

- **Immediate override removal**: Users can clear all overrides in one tap without waiting for sync or expiration.
- **Correct alarm state**: The triggered sync recomputes and re-arms alarms, ensuring no stale alarms survive.
- **Discoverable**: Placed directly below "Skip Tomorrow" on the Home screen, alongside other override actions.
- **Destructive action signaling**: Error color styling communicates that this is a non-trivial action.
- **Leverages existing pipeline**: No new sync mechanism or alarm cancellation code needed — uses `SyncWorker` and `AlarmService` as designed.

### Negative

- **Additional UI element**: The Home screen now has four rows of quick actions (three FAB rows + Skip Tomorrow + Reset), which may feel crowded on narrow screens.
- **Concurrent sync risk**: If the user triggers reset while a periodic sync is running, the `ExistingWorkPolicy.REPLACE` will cancel the periodic sync. This is acceptable (the reset sync is more urgent) but means the periodic sync timer resets.
- **No confirmation dialog**: The destructive action has no confirmation. This is intentional for UX but means a mistaken tap requires the user to re-apply overrides.

### Neutral / operational

- **New interface method**: `clearAllOverrides(): suspend fun` added to `AlarmRepository` interface.
- **DataStore write pattern**: Uses existing `OverrideStore.saveOverrides(emptyList())` — consistent with how overrides are stored elsewhere.
- **No new dependencies**: Uses existing `SyncUseCase`, `WorkManager`, and `AlarmRepository`.
- **ProGuard**: No reflection or keep rules needed.
- **Testing**: Unit tests for `HomeViewModel.resetOverrides()` should verify that `clearAllOverrides()` is called and `syncImmediately()` is triggered. Instrumented tests should verify the UI renders the button and the click handler executes correctly.

## Alternatives Considered

### Option B: Toggle behavior on "Skip Tomorrow"

Would require branching the click handler based on active override state. Ambiguous about whether it clears all overrides or only skip. Rejected for clarity — a dedicated button is more explicit about its scope.

### Option C: Reset in Settings screen

Settings already contains alarm configuration, discovery mode, and debug settings. Adding override reset there would split override management across Home (create) and Settings (destroy), reducing discoverability. Rejected for better UX cohesion.

### Option D: Swipe-to-reset on override card

Adds gesture-based interaction that may not be discoverable. Requires custom swipe detection logic and state management in the override card composable. Rejected for simplicity — a visible button is more accessible and consistent with the existing FAB/button pattern.

## Follow-Ups

- Implementation of `clearAllOverrides()` on `AlarmRepository` and `AlarmRepositoryImpl`.
- Addition of `resetOverrides()` function to `HomeViewModel` with Hilt-injected `SyncOperations`.
- New `OutlinedButton` in `HomeScreen` below "Skip Tomorrow" with error color styling.
- Unit tests for `HomeViewModel.resetOverrides()` verifying DataStore write and sync trigger.
- Consider whether SNOOZE overrides should also be cleared by reset (currently all override types are cleared).

## References

- ADR-0003: alarm scheduling policy — defines clear-then-set reconciliation in `AlarmService`.
- ADR-0006: manual override model — defines override types, priority, and expiration.
- ADR-0007: background sync mechanism — defines `SyncWorker` pipeline and 12-hour periodic sync.
- `app/src/main/java/com/cielshieru/wakey/domain/port/AlarmRepository.kt` — current interface (no `clearAllOverrides` yet).
- `app/src/main/java/com/cielshieru/wakey/data/repository/AlarmRepositoryImpl.kt` — current implementation (no `clearAllOverrides` yet).
- `app/src/main/java/com/cielshieru/wakey/ui/home/HomeViewModel.kt` — current override actions (`snooze`, `skipTomorrow`, `setCustomTime`).
- `app/src/main/java/com/cielshieru/wakey/ui/home/HomeScreen.kt` — current UI (Skip Tomorrow button at line 142).
- `app/src/main/java/com/cielshieru/wakey/usecase/SyncUseCase.kt` — existing `syncImmediately()` method.

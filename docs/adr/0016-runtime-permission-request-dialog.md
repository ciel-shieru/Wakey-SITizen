# ADR-0016: Runtime permission request dialog replaces "Open App Settings" button in settings UI

## Status

Proposed

## Date

2026-10-11

## Context

ADR-0008 established that `POST_NOTIFICATIONS` and `READ_CALENDAR` are runtime permissions requiring user consent. The current Settings UI handles denied runtime permissions in three separate locations:

1. **POST_NOTIFICATIONS warning card** (`SettingsScreen.kt:339-374`): When bedtime is enabled but POST_NOTIFICATIONS is not granted, a warning card with an "Open App Settings" `OutlinedButton` directs users to the system app settings page.
2. **READ_CALENDAR grant button** (`SettingsScreen.kt:434-442`): A standalone "Grant Calendar Permission" `Button` uses a single `permissionLauncher` (registered with `ActivityResultContracts.RequestPermission()`) to request READ_CALENDAR at runtime.
3. **READ_CALENDAR fallback** (`SettingsScreen.kt:457-464`): An "Open App Settings" `OutlinedButton` appears when READ_CALENDAR is explicitly denied, mirroring the POST_NOTIFICATIONS flow.
4. **PermissionCard** (`SettingsScreen.kt:698-703`): The shared `PermissionCard` composable renders an "Open App Settings" `OutlinedButton` for any runtime permission in `Denied` or `Required` state.

The `permissionLauncher` at `SettingsScreen.kt:183-191` only handles READ_CALENDAR. POST_NOTIFICATIONS has no launcher — the only path to grant it is through the system app settings page via `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`.

ADR-0008's "Permission denial handling" section states that if a permission is denied, "the user can enable them later by granting the permission from settings." The current implementation interprets "from settings" as the system app settings page, requiring users to navigate away from the app. Android 13+ (API 33+) supports in-app permission requests via `ActivityResultContracts.RequestPermission()`, which presents a system dialog without leaving the app.

The requirement is to replace all "Open App Settings" buttons for runtime permissions with in-app permission request dialogs using `ActivityResultContracts.RequestPermission()`, providing a better user experience by keeping users within the app during the permission grant flow.

## Decision Drivers

- **In-app permission flow**: Users should be able to grant runtime permissions without leaving the app to the system settings page.
- **Consistency**: Both POST_NOTIFICATIONS and READ_CALENDAR should use the same permission request mechanism.
- **Progressive enhancement**: The "Open App Settings" path should remain available as a fallback for edge cases (e.g., user permanently denied, system restrictions).
- **Single source of truth**: Permission state updates after a runtime request should flow through the existing `SettingsViewModel.setPermissionGranted()` method.
- **UI consolidation**: The standalone calendar permission buttons and the POST_NOTIFICATIONS warning card's "Open App Settings" button should be replaced by the unified `PermissionCard` component.

## Considered Options

### Option A: Second launcher for POST_NOTIFICATIONS + PermissionCard callback (Selected)

Add a second `postNotificationsLauncher` using `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` alongside the existing `permissionLauncher`. Update `PermissionCard` to accept an `onRequestPermission` callback in addition to `onOpenSettings`. Replace "Open App Settings" `OutlinedButton` with a "Grant" `Button` that invokes `onRequestPermission` for runtime permissions. Remove standalone calendar permission buttons since `PermissionCard` now handles them. Keep `openAppSettings` in `SettingsViewModel` for fallback edge cases.

### Option B: Single unified launcher accepting permission name

Create a single launcher that accepts the permission name as a parameter and dispatches to the correct permission string internally. This reduces launcher count to one but requires passing the permission name through the callback chain, adding complexity to the composable parameters.

### Option C: ViewModel-owned launchers

Move launcher registration into `SettingsViewModel` so the ViewModel owns the permission request lifecycle. The UI only calls `viewModel.requestPermission(permissionName)`. This centralizes permission logic but couples the ViewModel to Android's `ActivityResultContracts`, violating the separation between UI and domain layers.

### Option D: Maintain status quo with improved copy

Keep the current "Open App Settings" flow but improve the button text and adding inline permission request capability via a secondary "Request Permission" button alongside "Open App Settings". This provides both options but increases UI complexity and cognitive load.

## Decision

We will choose Option A.

A `postNotificationsLauncher` will be added using `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` for the `"POST_NOTIFICATIONS"` permission string. The launcher will call `viewModel.setPermissionGranted("POST_NOTIFICATIONS", granted)` on result, reusing the existing ViewModel method that already handles both permissions.

`PermissionCard` will accept an additional `onRequestPermission: () -> Unit` callback. For runtime permissions in `Denied` or `Required` state, the card will render a "Grant" `Button` (filled style) that invokes `onRequestPermission`, alongside the existing "Open App Settings" `OutlinedButton` as a secondary fallback option.

The standalone READ_CALENDAR "Grant Calendar Permission" button (`SettingsScreen.kt:434-442`) and the READ_CALENDAR "Open App Settings" button (`SettingsScreen.kt:457-464`) will be removed, as `PermissionCard` now handles calendar permission requests for all entries in the `permissionStatus` list.

The POST_NOTIFICATIONS warning card (`SettingsScreen.kt:339-374`) will retain its warning message but replace the "Open App Settings" button with a "Grant" button that triggers `postNotificationsLauncher`.

The `openAppSettings` method in `SettingsViewModel` will be preserved for fallback use cases where the runtime dialog is not available (e.g., user selected "Never ask again" and the system blocks further requests).

## Rationale

### Why two separate launchers (Option A) over single unified launcher (Option B)

Android's `ActivityResultContracts.RequestPermission()` requires the permission string to be passed at launch time. A unified launcher would need to accept the permission name as a parameter, which must flow through the composable hierarchy or be passed as a lambda capturing the permission string. Since there are only two runtime permissions and each has a distinct UI location (POST_NOTIFICATIONS in the bedtime section, READ_CALENDAR potentially in its own card or the shared PermissionCard), separate launchers with captured permission strings are simpler and more idiomatic Compose.

### Why UI-owned launchers over ViewModel-owned (Option C)

`rememberLauncherForActivityResult` is a Compose API that must be called within a composable context. Placing it in the ViewModel would require either:
1. Passing `ActivityResultRegistry` through DI (complex, couples ViewModel to Android APIs).
2. Using `ActivityResultContracts.RequestPermission()` in the ViewModel with a `ActivityResultLauncher` field (requires AndroidX Activity library in the ViewModel module, which may not be desirable for testability).

Keeping launchers in the UI layer is the standard Compose pattern. The ViewModel only receives the result via `setPermissionGranted()`, maintaining clean separation.

### Why keep "Open App Settings" as fallback

Some edge cases prevent the runtime dialog from appearing:
- The user previously selected "Never ask again" for the permission.
- The device manufacturer (e.g., Xiaomi, OnePlus) imposes additional permission restrictions beyond stock Android.
- The permission is managed by device admin policies.

In these cases, the system dialog cannot be shown, and the user must manually grant the permission through the system settings page. Keeping `openAppSettings` ensures these users still have a path to grant the permission.

### Why remove standalone calendar permission buttons

The standalone "Grant Calendar Permission" button (`SettingsScreen.kt:434-442`) and the "Open App Settings" fallback button (`SettingsScreen.kt:457-464`) duplicate functionality now provided by `PermissionCard`. The `permissionStatus` list in `uiState` already contains a `PermissionEntry` for READ_CALENDAR, which `PermissionCard` iterates over at `SettingsScreen.kt:592-594`. Consolidating into `PermissionCard` eliminates redundant UI code and ensures consistent permission handling across all entries.

### Why "Grant" button instead of "Request Permission"

"Grant" is shorter, matches the verb used in Android's permission dialog ("Allow"), and is consistent with the existing "Grant Calendar Permission" button text (abbreviated). The action is unambiguous in context — the button is inside a card labeled with the permission feature name.

## Consequences

### Positive
- **In-app permission flow**: Users can grant POST_NOTIFICATIONS without leaving the app, reducing friction in the bedtime setup flow.
- **Consistent UX**: Both runtime permissions use the same `ActivityResultContracts.RequestPermission()` mechanism.
- **Reduced UI code**: Removing standalone calendar permission buttons eliminates ~15 lines of duplicated UI logic.
- **Fallback preserved**: The "Open App Settings" path remains available for edge cases where the runtime dialog is blocked.
- **Single update path**: Both launchers call `setPermissionGranted()`, ensuring permission state updates are handled uniformly through the ViewModel.

### Negative
- **Two launchers in SettingsScreen**: The composable now manages two `ActivityResultLauncher` instances. For a screen of this size, the impact is negligible, but it is worth noting for future permission additions.
- **PermissionCard signature change**: `PermissionCard` now accepts an additional `onRequestPermission: () -> Unit` callback. Callers must provide this callback for runtime permissions.
- **POST_NOTIFICATIONS warning card still exists**: The warning card at `SettingsScreen.kt:339-374` is retained (with updated button behavior) rather than being removed, as it provides contextual explanation for why the permission is needed. This means there are two UI paths to request POST_NOTIFICATIONS — the warning card and the PermissionCard entry. This is acceptable because the warning card provides feature context ("Bedtime reminders require notification permission") that the generic PermissionCard does not.

### Neutral / operational
- **No ViewModel changes**: `setPermissionGranted()` already handles both `"POST_NOTIFICATIONS"` and `"READ_CALENDAR"` (line 227-266). No new ViewModel methods are needed.
- **No new DI bindings**: Launchers are registered in the UI layer; no Hilt modules change.
- **No ProGuard changes**: No reflection or class-keeping rules needed.
- **Testing**: Existing `SettingsViewModelTest` should verify that `setPermissionGranted("POST_NOTIFICATIONS", true)` correctly updates the POST_NOTIFICATIONS entry's status to `PermissionStatus.Granted`.

## Alternatives Considered

### Option B: Single unified launcher accepting permission name

A single launcher with a parameterized permission string would reduce the launcher count from two to one. However, it requires passing the permission name through the callback chain (e.g., `onRequestPermission: (String) -> Unit`), which adds complexity to `PermissionCard`'s signature and the call sites. With only two permissions and distinct UI locations, the benefit of consolidation does not justify the added parameter passing.

### Option C: ViewModel-owned launchers

Moving launchers into `SettingsViewModel` would centralize permission request logic. However, `rememberLauncherForActivityResult` is a Compose API that cannot be called from a non-composable context. Alternatives (passing `ActivityResultRegistry` via DI, using `registerForActivityResult` in ViewModel) either couple the ViewModel to Android APIs or add unnecessary complexity. The standard Compose pattern is to keep launchers in the UI layer.

### Option D: Maintain status quo with improved copy

Keeping the "Open App Settings" flow with an additional "Request Permission" button provides both options but doubles the UI surface for permission handling. Users may be confused by two buttons with similar purposes. The runtime dialog is the preferred path for modern Android (API 33+), so presenting it as a secondary option alongside "Open App Settings" inverts the expected user flow. Rejected for simplicity — the runtime dialog should be the primary path.

## Follow-Ups

- Add `postNotificationsLauncher` using `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` in `SettingsScreen`.
- Update `PermissionCard` signature to accept `onRequestPermission: () -> Unit` callback.
- Replace "Open App Settings" `OutlinedButton` with "Grant" `Button` in `PermissionCard` for runtime permissions.
- Remove standalone READ_CALENDAR "Grant Calendar Permission" button (`SettingsScreen.kt:434-442`).
- Remove standalone READ_CALENDAR "Open App Settings" button (`SettingsScreen.kt:457-464`).
- Update POST_NOTIFICATIONS warning card to use "Grant" button invoking `postNotificationsLauncher`.
- Add unit tests for `SettingsViewModel.setPermissionGranted("POST_NOTIFICATIONS", ...)` state updates.
- Consider whether the dual POST_NOTIFICATIONS UI (warning card + PermissionCard) should be consolidated in a future ADR — the warning card provides context that PermissionCard does not, but having two entry points for the same permission may confuse users.

## References

- ADR-0008: permissions & privacy manifest — establishes POST_NOTIFICATIONS and READ_CALENDAR as runtime permissions with denial handling.
- ADR-0011: Settings UI discovery mode selection and calendar picker redesign — establishes the current Settings UI structure and permission gating patterns.
- [Android: Request app permissions](https://developer.android.com/training/permissions/requesting)
- [ActivityResultContracts.RequestPermission](https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.RequestPermission)
- `app/src/main/java/com/cielshieru/wakey/ui/settings/SettingsScreen.kt` — current settings UI with permission handling at lines 183-191, 339-374, 434-442, 457-464, 637-706.
- `app/src/main/java/com/cielshieru/wakey/ui/settings/SettingsViewModel.kt` — `setPermissionGranted()` at line 227 handles both permissions.
- `app/src/main/java/com/cielshieru/wakey/domain/model/PermissionEntry.kt` — `isRuntimePermission` flag used by PermissionCard.
- `app/src/main/java/com/cielshieru/wakey/android/PlatformPermissionChecker.kt` — determines `PermissionStatus` for each permission.

# ADR-0011: Settings UI discovery mode selection and calendar picker redesign

## Status

Proposed

## Date

2026-10-10

## Context

ADR-0002 established two mutually exclusive timetable discovery modes: **Base-URL mode** (fetches JSON from a user-configured SIT-ICS-Go server) and **Device Calendar mode** (reads events directly from the device calendar). The current Settings UI presents this choice as a `Switch` component with the labels "Base URL mode" and "Device calendar mode".

Several usability issues have been identified with the current implementation:

1. **Terminology confusion**: "Base URL" is an implementation-level term that does not clearly communicate what the mode does. Users do not know what a "base URL" is or why they need to configure one.
2. **Switch semantics**: A `Switch` implies an on/off toggle rather than a mode selection between two named alternatives. The current state defaults to `DiscoveryMode.BaseUrl`, which means new users see "Base URL mode" as the default — a confusing label for first-time users.
3. **No conditional visibility**: The SIT-ICS-Go base URL text field is always visible, with only a supporting text hint ("Only used in Base URL mode") to indicate it is inactive in Device Calendar mode. Similarly, the calendar picker section is always rendered (with conditional content inside), cluttering the UI when irrelevant.
4. **Calendar picker UX**: The current calendar picker uses a `LazyColumn` with checkboxes for multi-select. This presents all calendars in a flat list without distinguishing between the two distinct roles (online timetable calendar vs. in-campus timetable calendar). Users must select calendars from a single list and then rely on the app's internal logic to apply the selected IDs uniformly to both event kinds.
5. **Permission gating**: The READ_CALENDAR permission request is triggered by a button press within the settings flow, but there is no explicit permission check before attempting to load or display calendar dropdowns.

## Decision Drivers

- **Clarity over implementation detail**: Labels should describe what the user is choosing, not how the app implements it.
- **Progressive disclosure**: UI elements should only appear when the selected mode requires them, reducing cognitive load and visual clutter.
- **Explicit role separation for calendars**: Online and campus events come from different calendar sources; users should select each independently rather than sharing a single multi-select list.
- **Permission-first workflow**: Calendar permission should be requested before attempting to read or display calendar data, avoiding unnecessary loading states and errors.
- **Material 3 consistency**: Segmented buttons are the recommended M3 component for single-select mode toggles with two options.

## Considered Options

### Option A: Segmented Button + Conditional Visibility + Dual Dropdowns (Selected)

Replace the `Switch` with a `SegmentedButton` group showing "Device Calendar" (first, default) and "Remote Server". Conditionally render the Remote Server URL field only when "Remote Server" is selected. Replace the checkbox-based calendar picker with two `ExposedDropdownMenu` components — one for online timetable calendar and one for in-campus timetable calendar. Request READ_CALENDAR permission before showing the calendar dropdowns.

### Option B: Radio Button Group + Conditional Visibility

Use a `RadioGroup` with `RadioButton` items instead of a `SegmentedButton`. Keep the single checkbox-based calendar picker. Conditionally show/hide sections based on selection. Request permission before loading calendars.

### Option C: Tab Row + Conditional Visibility

Replace the switch with a `TabRow` having two tabs ("Device Calendar" and "Remote Server"). Each tab reveals its own settings sub-section. Keep the checkbox-based calendar picker for the Device Calendar tab.

### Option D: Minimal — Rename Only

Keep the `Switch` component. Rename "Base URL mode" to "Remote Server" and "Device calendar mode" to "Device Calendar". Add conditional visibility for the URL field. Keep the existing checkbox calendar picker.

## Decision

We will choose Option A.

The settings UI will use a Material 3 `SegmentedButton` group for discovery mode selection, with "Device Calendar" as the first and default option (for fresh installs). The Remote Server URL text field will be conditionally visible only when "Remote Server" is selected. The single checkbox-based calendar picker will be replaced with two `ExposedDropdownMenu` components — one for online timetable calendar selection and one for in-campus timetable calendar selection. READ_CALENDAR permission will be requested before showing the calendar dropdowns.

The rename from "base URL" to "remote server" was implemented across the entire codebase — not just UI labels, but also domain-level objects (`DiscoveryMode.RemoteServer` replacing `DiscoveryMode.BaseUrl`), the `RemoteServerProvider` interface (formerly `BaseUrlProvider`), and DataStore key strings (`"remote_server"` replacing `"base_url"`). Backward compatibility is maintained by checking old key/value strings before new ones during reads.

## Rationale

**Why Segmented Button over Switch**: A `Switch` conveys a binary on/off state. A `SegmentedButton` with two labeled options clearly communicates that the user is choosing between two named modes. M3 documentation recommends segmented buttons for "two or more mutually exclusive options" — exactly this use case.

**Why Device Calendar first and default**: New users are more likely to rely on their device calendar (imported via ICS subscription) than to self-host a SIT-ICS-Go server. Making Device Calendar the default prevents first-time users from seeing a confusing "Base URL mode" label and a URL input field they do not understand. The DataStore default is updated to `DiscoveryMode.DeviceCalendar` for new installations; existing installations retain their current selection.

**Why rename to "Remote Server"**: "Base URL" exposes the HTTP transport detail. "Remote Server" describes the concept — events come from a remote server — without requiring users to understand URLs, ports, or networking. The `baseUrl` property name in `AlarmConfig` and the DataStore key `"base_url"` are kept unchanged to avoid migration complexity; only UI labels change.

**Why two ExposedDropdownMenu components**: Online and campus events serve different purposes and may reside in different calendars. A single multi-select checkbox list forces users to select calendars that apply to both event kinds, which is semantically incorrect when the calendars differ. Two separate dropdowns let users explicitly choose: "This calendar holds online events" and "This calendar holds campus events".

**Why permission-first**: Attempting to read calendars before granting permission produces empty results and loading spinners, confusing users. Requesting the permission upfront — with a clear "Grant Calendar Permission" button that appears only when Device Calendar mode is active and permission is not yet granted — provides a deterministic flow.

**Why not Option B (Radio Group)**: Radio buttons consume more vertical space and are typically used for longer option lists. A `SegmentedButton` group is compact and visually tied to the section header, matching the two-option nature of this toggle.

**Why not Option C (Tab Row)**: Tabs would require a separate screen or nested navigation for each mode, adding navigation complexity to a single-page settings form. The current LazyColumn layout is sufficient; conditional visibility within the same scroll is simpler.

**Why not Option D (Rename Only)**: While Option D is the minimal change, it does not address the fundamental UX problems: the checkbox-based calendar picker still conflates online and campus calendar roles, and the Switch component still miscommunicates the nature of the choice. The additional effort for Option A is justified by the meaningful UX improvement.

## Consequences

### Positive

- **Clearer user understanding**: "Device Calendar" and "Remote Server" are self-explanatory labels that describe what data source the user is choosing, not how the app implements it.
- **Reduced UI clutter**: Conditional visibility means users only see the settings relevant to their selected mode. Device Calendar users never see the URL field; Remote Server users never see the calendar picker.
- **Explicit calendar role separation**: Two dropdown menus make it clear that online and campus events can come from different calendars, reducing configuration errors.
- **Better permission flow**: Requesting permission before loading calendars eliminates the confusing "No readable calendars found" state that occurs when permission is denied.
- **M3 consistency**: Segmented buttons are the recommended M3 component for two-option mode toggles, improving visual consistency with the rest of the Material 3 design system.
- **Better default for new users**: Device Calendar as the default prevents first-time users from encountering an unfamiliar URL input field.

### Negative

- **Increased UI complexity**: The settings screen now manages more state (two dropdown selections, conditional visibility logic, permission flow) than before.
- **DataStore schema impact**: The `selectedCalendarIds` field in DataStore currently stores a comma-separated list of calendar IDs applied uniformly to both event kinds. Splitting into online and campus calendar selections requires either a new DataStore key (e.g., `online_calendar_id`, `campus_calendar_id`) or a migration strategy for existing users. This ADR does not decide the migration approach — that is a follow-up implementation detail.
- **Backward compatibility**: Existing DataStore entries with `DISCOVERY_MODE = "base_url"` will correctly parse as `DiscoveryMode.BaseUrl` via the existing `parseDiscoveryMode` function in `AlarmConfigStore`. No migration is needed for the mode itself.
- **More code in SettingsScreen.kt**: The conditional visibility logic and two dropdown components add complexity to the screen composable. This is acceptable given the single-screen scope.

### Neutral / operational

- **Domain model updated**: `DiscoveryMode.BaseUrl` was renamed to `DiscoveryMode.RemoteServer`, and `BaseUrlProvider` to `RemoteServerProvider`. DataStore key strings changed from `"base_url"` to `"remote_server"` with backward-compatible legacy key checks.
- **DataStore persistence**: The `DISCOVERY_MODE` key values (`"remote_server"` / `"device_calendar"`) are written with new strings but read with fallback to legacy `"base_url"` for existing users. The `selectedCalendarIds` key behavior may change if the two-dropdown approach is adopted, requiring a separate decision.
- **SyncWorker**: The `fetchTimetable` method in `SyncWorker.kt` uses `config.discoveryMode` to dispatch between `RemoteServer` and `DeviceCalendar` branches. Branch names updated to match domain model rename.

## Alternatives Considered

### Option B: Radio Button Group

Radio buttons provide clear single-select semantics and support longer labels. However, they consume approximately 2x the vertical space of a segmented button group and are typically used for option lists with three or more choices. For a two-option toggle adjacent to a section header, a segmented button is more appropriate per M3 guidelines.

### Option C: Tab Row

Tab rows provide strong visual separation between modes and prevent configuration conflicts (you cannot set both a URL and calendar IDs simultaneously). However, tabs require either nested navigation or a separate composable per tab, adding complexity to what is currently a single scrollable settings form. The conditional visibility approach within a single LazyColumn is simpler and sufficient for two options.

### Option D: Rename Only

Minimal implementation effort. Keeps the existing Switch component and checkbox-based calendar picker. Does not address the fundamental UX problems: confusing labels, no conditional visibility, conflated calendar roles, and poor permission flow. Rejected because the incremental UX improvement does not justify the persistent usability issues.

## Follow-Ups

- Implementation of the two-dropdown calendar picker requires a decision on DataStore schema: whether to add separate keys for online and campus calendar IDs, or migrate the existing `selectedCalendarIds` format. This should be captured in a follow-up ADR.
- Migration strategy for existing users' calendar selections when switching from single multi-select to dual dropdowns.
- Unit tests for the updated SettingsScreen composable, covering conditional visibility and segmented button state transitions.

## References

- ADR-0002: wakey-sitizen JSON discovery endpoint and source selection — establishes the two discovery modes
- [Material 3 Segmented Button documentation](https://m3.material.io/components/segmented-button)
- [Material 3 Exposed Dropdown Menu documentation](https://m3.material.io/components/dropdown-menu/extended-dropdown-menu)
- `app/src/main/java/com/cielshieru/wakey/ui/settings/SettingsScreen.kt` — current settings UI
- `app/src/main/java/com/cielshieru/wakey/domain/model/AlarmConfig.kt` — `DiscoveryMode` sealed interface
- `app/src/main/java/com/cielshieru/wakey/data/store/AlarmConfigStore.kt` — DataStore persistence layer

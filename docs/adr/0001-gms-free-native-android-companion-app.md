# ADR-0001: wakey-sitizen as a GMS-free native Android companion app for SIT-ICS-Go

## Status

Proposed

## Date

2026-10-05

## Context

Students at the Singapore Institute of Technology (SIT) receive their class timetables from the PeopleSoft student system. The sibling project **SIT-ICS-Go** (a Go service) authenticates against PeopleSoft, fetches the timetable, and publishes RFC 5545 iCalendar output over HTTP — including `/timetable-online.ics` (online events only) and `/timetable-campus.ics` (in-campus events only), each with events carrying `DTSTART;TZID=Asia/Singapore`, `SUMMARY`, and `LOCATION` (e.g. `W1-05-07` or `Online`). Students subscribe to these feeds in their calendar apps.

Despite having the timetable in their calendar, students must **manually manage their own wake-up alarms** every day based on the next day's schedule. This is tedious, error-prone (missed alarms, wrong offsets for online vs. in-campus classes), and a recurring daily burden.

**wakey-sitizen** is the companion Android application that relieves this burden: it consumes the SIT-ICS-Go ICS output for a student's PeopleSoft timetable and automatically manages a configurable set of Android alarms (wake-up alarms with per-event-type smart time offsets, and bedtime reminder notifications) based on the first scheduled event of each day.

This ADR decides the **architectural foundation** of the application: its platform, technology stack, integration boundary with SIT-ICS-Go, and privilege model. It does **not** decide the internal policies (offset computation, bedtime calculation, sync cadence, dependency allowlist, testing strategy), which are deferred to follow-up ADRs.

Key requirements shaping this decision:

- Security is the first priority; the app must be as unprivileged as possible, with every permission documented and justified.
- The app must **not** rely on Google Play Services (GMS) at all.
- The app must have **no network egress** other than to a configured SIT-ICS-Go server instance.
- It must manage alarms using the platform's exact-alarm capability (`USE_EXACT_ALARM`).
- Kotlin as the primary language; idiomatic and DRY; third-party libraries minimized.
- Targets Android 16 (API 36) and Android 17 (API 37); `minSdk 36`, `targetSdk 37`.
- Wake-up alarms are derived only from the **first calendar event of the day**, with a user-configurable smart time offset that differs between online and in-campus events, plus a configurable repeat pattern (e.g. repeat every 5 minutes, up to 6 times).
- Optional: read the device calendar to detect external events that occur before the student's timetable events and pull the alarm time earlier; users opt in per calendar.
- Optional: user manual override of upcoming alarms for off-schedule situations.
- **No** xsite/BrightSpace events are used.
- A bedtime notification reminder derived from a user-configured "target sleep duration" and "bedtime reminder offset" (e.g. 9:00 class, 1 h offset → first alarm 08:00; 8 h target sleep, 30 min offset → target bedtime 00:00, reminder 23:30).
- An unprivileged background sync every 12 hours that runs the entire calendar-check and alarm-management flow in the background.
- All dates and times follow the system timezone (default `Asia/Singapore`).
- Properly unit and E2E tested.

## Decision Drivers

- **Reliable, precise alarm delivery**: the core function (ringing at a precise time while the app is not running) requires the platform's exact-alarm APIs, which in turn require a native app with `USE_EXACT_ALARM`.
- **No GMS dependency**: the app must run on devices without Google Mobile Services (e.g. F-Droid builds, device variants without GMS), ruling out FCM/push-based designs and GMS-dependent libraries.
- **Minimal, auditable privilege surface**: every permission and background capability must be justifiable; the design must avoid permissions that are hard to justify (e.g. `WRITE_CALENDAR`, broad location, exact-alarm-free workarounds).
- **Network egress containment**: the only permitted network destination is the configured SIT-ICS-Go server; the architecture must make any other egress structurally impossible or easily auditable.
- **Clear separation from SIT-ICS-Go**: SIT-ICS-Go is the sole PeopleSoft integration point. wakey-sitizen must *consume* its published ICS output, not re-implement or import PeopleSoft/BrightSpace scraping.
- **Maintainability with a small dependency surface**: idiomatic Kotlin, DRY code, minimal third-party libraries, and a testable architecture (unit + E2E).
- **Modern baseline only**: `minSdk 36` removes the need for legacy API compatibility branches, simplifying code and reducing the surface for version-specific bugs.

## Considered Options

### Option A: Native Android app (Kotlin, Jetpack Compose, platform APIs)

A standalone Android application written in Kotlin, with Jetpack Compose (Material 3) for the UI, using only platform APIs and a minimal set of vetted Jetpack libraries: `AlarmManager` (with `USE_EXACT_ALARM`) for wake-up/bedtime alarms, `WorkManager` for the unprivileged 12-hour background sync, `CalendarContract` (read-only) for the device-calendar discovery and external-event modes, and an HTTP client for fetching the ICS feeds from the configured SIT-ICS-Go base URL.

### Option B: Web / progressive web app

A PWA consuming the same ICS feeds, with alarms implemented via Web Push / notifications and in-app reminders.

### Option C: Cross-platform framework (e.g. Flutter, React Native)

A cross-platform mobile app written in Dart/JavaScript bridging to the Android platform APIs for alarms, calendar, and background work.

### Option D: Server-driven alarms (extend SIT-ICS-Go)

Extend SIT-ICS-Go to push alarm instructions to student devices (e.g. via FCM), with a thin client that merely executes server-issued alarm commands.

### Option E: Delegate to the device calendar's native reminders

The app writes the timetable (and derived reminders) into the user's device calendar via `WRITE_CALENDAR` and relies on the device's native calendar app to fire reminders.

## Decision

We will choose **Option A: a native Android app** with the following foundation:

- **Platform**: Android, `minSdk 36` (Android 16), `targetSdk 37` (Android 17).
- **Language**: Kotlin 2.4.20 as the primary (and only) language.
- **UI**: Jetpack Compose 1.12.1 with the Material 3 (`androidx.compose.material3`) 1.4.0 component set.
- **Integration boundary**: the app consumes SIT-ICS-Go's **published JSON output** — either by fetching `/wakey-sitizen/timetable.json` from a user-configured SIT-ICS-Go **base URL** (the app appends the known JSON path), or by reading the corresponding calendars from the **device calendar** (read-only, `READ_CALENDAR`) after the user designates which calendars map to the online and in-campus timetables. The app does not import SIT-ICS-Go code, does not communicate with PeopleSoft, BrightSpace, or any xsite endpoint, and treats SIT-ICS-Go as an opaque JSON publisher. The app does **not** parse ICS files.
- **Alarm mechanism**: platform `AlarmManager` exact alarms under the `USE_EXACT_ALARM` normal permission (auto-granted on install for API 33+; no user revocation), used for both wake-up alarm sets and the bedtime reminder.
- **Background sync**: unprivileged `WorkManager` periodic work (12-hour interval) running the full flow — fetch/read calendar → compute next-day events → reconcile alarm and bedtime-reminder schedules — entirely in the background, with no foreground service and no special background privileges.
- **Privilege model**: GMS-free, no network egress beyond the configured SIT-ICS-Go base URL, and a permission set limited to what the features strictly require (`USE_EXACT_ALARM`, `POST_NOTIFICATIONS`, `READ_CALENDAR` — the latter only when the user selects device-calendar discovery — each to be documented and justified in a follow-up permissions ADR).

## Rationale

### Why Option A over Option B (web / PWA)

A web app cannot reliably deliver precisely-timed wake-up alarms: Web Push delivery depends on a push service (which would violate the no-GMS and no-extra-egress constraints if FCM is used, or a third-party push provider otherwise), is not guaranteed while the device is offline or the browser is closed, and cannot use `AlarmManager` at all. Browser notifications also cannot implement the required repeat patterns (e.g. ring every 5 minutes up to 6 times) with the same reliability as native exact alarms. The core function of the app — a dependable wake-up alarm — is a native-capability problem, which rules out a web foundation.

### Why Option A over Option C (cross-platform framework)

A cross-platform framework (Flutter, React Native, etc.) inserts a large third-party runtime (the Flutter engine, the JS bridge) into the app, directly conflicting with the "minimize third-party libraries" driver. It also mediates access to the platform's alarm, calendar, and background-work APIs through framework bridges, adding a layer that is harder to audit for the security-first requirement and weaker for the precise `AlarmManager`/`WorkManager` interactions this app depends on. Since the app targets Android only, a cross-platform abstraction buys nothing while adding dependency risk, binary size, and a maintenance surface.

### Why Option A over Option D (server-driven alarms)

Pushing alarm instructions from the server requires a push transport — FCM on GMS, which is explicitly excluded, or a non-GMS push channel, which would add a second network endpoint and violate the "no network egress except to the SIT-ICS-Go server" constraint in spirit (the server would need a device registry and per-device alarm state). It also inverts the appropriate boundary: per-device alarm configuration (offsets, repeat patterns, overrides) is device-local state, and the server has no business managing it. Keeping the device as the authority over its own alarms preserves the simple, auditable model: the server publishes an ICS snapshot; the device decides what alarms to set.

### Why Option A over Option E (device calendar reminders)

Delegating reminders to the native calendar app would require `WRITE_CALENDAR` — a permission with a materially larger privacy footprint than read-only calendar access, and difficult to justify for an alarm app (it would let the app mutate the user's calendar data). It also cedes control of the exact behaviors the requirements demand (per-event-type offset patterns, identical interval settings across a day's alarm set, uniform ringtone patterns, repeat-up-to-N semantics, external-event adjustment, manual override) to a third party (the user's calendar app) whose reminder implementation varies by vendor. Managing the alarms directly in-app is more code, but it is code we can unit and E2E test and keep DRY.

### Why the integration boundary is "consume ICS output, import nothing"

SIT-ICS-Go already solves the hard, fragile problem (PeopleSoft/ADFS authentication and timetable scraping) and publishes it as a stable, well-defined artifact: RFC 5545 ICS over HTTP with deterministic event `UID`s and `Asia/Singapore` timestamps. Consuming that artifact keeps wakey-sitizen small, secure, and independent: a change in PeopleSoft's UI affects SIT-ICS-Go, not this app; and this app's network surface is exactly one known destination. Importing SIT-ICS-Go (Go code) into a Kotlin app is impossible without a rewrite; re-implementing its scraping in Kotlin would duplicate its security-critical authentication logic (cookie/redirect filtering, session isolation) inside a mobile app — a far larger attack surface for zero benefit.

### Why `minSdk 36` is architecturally significant

With `minSdk 36`, the app never runs on API < 36, so it can rely unconditionally on `USE_EXACT_ALARM` (API 33+), `POST_NOTIFICATIONS` (API 33+), modern `WorkManager` constraints, and Android 16/17 behavior — no `Build.VERSION` compatibility branches, no legacy permission fallbacks (e.g. the `SCHEDULE_EXACT_ALARM` `maxSdkVersion` split). This directly serves the DRY and security drivers: fewer branches, fewer version-specific bugs, a smaller audit surface.

## Consequences

### Positive

- **Reliable core function**: exact alarms via `USE_EXACT_ALARM` (auto-granted, non-revocable) guarantee the wake-up alarms fire precisely, without any user-facing "Alarms & reminders" settings dance.
- **No GMS dependency**: the app installs and runs on any Android 16/17 device, including GMS-less variants; distribution is not coupled to Google Play.
- **Contained network surface**: structurally, the only network code path is the fetch of the JSON timetable from the configured base URL — trivially auditable and testable, satisfying the strict egress constraint.
- **Clean boundary with SIT-ICS-Go**: the JSON contract (schema version, event fields, `TZID=Asia/Singapore`, RFC 3339 timestamps) is the entire integration surface; no shared code, no shared credentials, no shared state.
- **Small permission surface**: `USE_EXACT_ALARM` (normal, auto-granted), `POST_NOTIFICATIONS` (runtime), and `READ_CALENDAR` (runtime, only when device-calendar mode is used) — each individually justifiable.
- **Simplicity from a modern floor**: no legacy-branch code; the codebase stays DRY and idiomatic Kotlin.
- **Testability**: a native, dependency-light codebase maps cleanly to the required unit + E2E testing strategy.

### Negative

- **Narrow device support**: the app only runs on Android 16+ devices (`minSdk 36`); students on older devices are excluded. This is an accepted tradeoff imposed by the requirements.
- **Platform-coupled maintenance**: Android background-execution policy changes (e.g. further restrictions on exact alarms, WorkManager scheduling, or calendar access in future Android releases) will require maintenance responses; a native app has no abstraction layer to absorb such changes.
- **Single-language, single-platform skill requirement**: maintenance requires Kotlin/Android competence.
- **No calendar write-back by design**: the app manages its own alarms and never touches the user's calendar events (read-only where it reads at all); users who expected the app to also write reminders into their calendar will find it does not.

### Neutral / operational

- **Version pinning**: per project policy, all toolchain and library versions (Kotlin 2.4.20, Compose 1.12.1, Material 3 1.4.0, WorkManager, AGP, etc.) are pinned in the build with digests/hashes.
- **Distribution**: sideload / F-Droid-style distribution is the natural channel; no Play Store policy constraints apply, and none of the Play-specific exact-alarm policy nuances apply since `USE_EXACT_ALARM` is used.
- **Server availability**: if the SIT-ICS-Go server is unreachable during a sync, the last known ICS snapshot is retained (stale-while-revalidate); alarm reconciliation degrades to the last good data.
- **Follow-up ADRs required**: this ADR deliberately defers a set of tightly-coupled sub-decisions that each warrant their own record.

## Alternatives Considered

### Option B: Web / progressive web app

**Summary**: A PWA consuming the ICS feeds, with alarms via Web Push and in-app scheduling.

**Benefits**: Cross-platform reach; no installation friction; smaller binary.

**Costs**: No access to `AlarmManager`; push-based delivery is unreliable for a wake-up alarm (offline device, closed browser, push service dependency); cannot satisfy the repeat-pattern and exactness requirements; a push server would add a second network destination, violating the egress constraint.

**Reason rejected**: The core function (precise, local, repeatable wake-up alarms) is only reliably achievable with native exact-alarm APIs.

### Option C: Cross-platform framework

**Summary**: Flutter (Dart) or React Native (JavaScript) app bridging to Android platform services.

**Benefits**: Single codebase for multiple platforms; large ecosystem.

**Costs**: Large third-party runtime dependency conflicting with the minimal-dependency driver; bridged access to `AlarmManager`/`WorkManager`/`CalendarContract` is less direct and harder to audit; no second platform is in scope, so the abstraction has no payoff.

**Reason rejected**: The app is Android-only, so a cross-platform runtime is pure cost — dependency risk, size, and an audit surface — with no benefit.

### Option D: Server-driven alarms via push

**Summary**: SIT-ICS-Go computes alarm schedules and pushes them to devices; a thin client executes them.

**Benefits**: Centralized logic; client stays trivial.

**Costs**: Requires a push transport (FCM → GMS, excluded; third-party push → second egress destination, excluded); the server must hold per-device alarm configuration and state, inverting the appropriate ownership boundary; a server outage directly breaks alarm management on the device (no local autonomy).

**Reason rejected**: Violates the no-GMS and egress constraints, and places device-local alarm policy under server control.

### Option E: Device calendar reminders via `WRITE_CALENDAR`

**Summary**: Write the timetable plus derived reminders into the user's calendar and let the native calendar app fire them.

**Benefits**: Less custom alarm code; reminders integrate with the user's existing calendar UI.

**Costs**: Requires `WRITE_CALENDAR` (a permission with a much larger privacy footprint than read-only, hard to justify); reminder semantics (intervals, repeat counts, ringtone patterns, per-event-type offsets) are controlled by the user's calendar app and vary by vendor; manual override and external-event adjustment become awkward; less testable.

**Reason rejected**: The permission footprint and the loss of control over exact reminder semantics outweigh the reduced code volume.

## Follow-Ups

This ADR intentionally leaves the following sub-decisions to separate, follow-up ADRs (each will reference ADR-0001):

1. **JSON discovery & source selection** — base-URL vs. device-calendar discovery modes, the `/wakey-sitizen/timetable.json` endpoint schema, source validation, and how the app distinguishes the online vs. in-campus feeds (and excludes xsite/BrightSpace events). See ADR-0002.
2. **Alarm scheduling policy** — the "first event of the day" selection rule, the smart time offset model (online vs. in-campus), repeat-pattern configuration, and alarm reconciliation/idempotency semantics.
3. **Bedtime reminder algorithm** — derivation of target bedtime from the first wake-up alarm, target sleep duration, and bedtime reminder offset, including edge cases (no events, early events).
4. **External-event adjustment** — the opt-in per-calendar device-calendar scan for pre-timetable external events and how they shift alarm times.
5. **Manual override model** — how the user overrides/suppresses upcoming alarms for off-schedule situations, and how overrides interact with the 12-hour sync.
6. **Background sync mechanism** — WorkManager configuration (12-hour periodic work, constraints, catch-up behavior, stale-while-revalidate on fetch failure).
7. **Permissions & privacy manifest** — the definitive permission list with per-permission justification, and the privacy/audit documentation required by the "as unprivileged as possible" constraint.
8. **Third-party dependency allowlist** — the vetted dependency policy (what may be added, under what criteria, with pinned versions and hashes).
9. **Testing strategy** — unit test architecture (clock/timezone injection, fake AlarmManager/WorkManager) and E2E test approach (instrumentation, emulator matrix for API 36/37).

Related ADRs:

- **SIT-ICS-Go ADR-0003** (ICS generation and caching strategy) — defines the event model (`Event` struct with `DTStart`, `DTEnd`, `Location`, `Source`, etc.) that the `/wakey-sitizen/timetable.json` endpoint serializes.
- **SIT-ICS-Go ADR-0014** (xsite ICS HTTP endpoints) — defines the public ICS endpoint surface from which the `/wakey-sitizen/` endpoint derives its filtering logic (excluding `brightspace-*` sources).
- **ADR-0002** (JSON discovery & source selection) — defines the `/wakey-sitizen/timetable.json` endpoint contract, JSON schema, and discovery modes.
- **ADR-0003** (alarm scheduling policy) — defines the "first event of the day" rule, smart offset, and repeat pattern.
- **ADR-0004** (bedtime reminder algorithm) — defines bedtime derivation from the first alarm.
- **ADR-0005** (external-event adjustment) — defines device-calendar scan for pre-timetable events.
- **ADR-0006** (manual override model) — defines snooze, skip, and custom-time overrides.
- **ADR-0007** (background sync & boot recovery) — defines WorkManager periodic work and `BootReceiver`.
- **ADR-0008** (permissions & privacy manifest) — defines the definitive permission list.
- **ADR-0009** (third-party dependency allowlist) — defines the vetted dependency policy.
- **ADR-0010** (testing strategy) — defines unit, instrumented, and E2E test architecture.

## Assumptions

- This repository is greenfield; the ADR directory (`docs/adr/`) and numbering conventions follow the sibling SIT-ICS-Go project.
- The requirement "Kotlin 2.4.20 idempotent" is interpreted as "idiomatic Kotlin at the pinned 2.4.20 release" (consistent with the separate "idiomatic and DRY" requirement).
- Version facts (Kotlin 2.4.20 released 2026-09-07; Compose 1.12.1 released 2026-09-09; Material 3 1.4.0 released 2026-09-23; Android 17 / API 37 released 2026-06-16; WorkManager 2.12.0 stable) were verified as of 2026-10-05.
- `USE_EXACT_ALARM` is sufficient for all of the app's exact-alarm needs (no `SCHEDULE_EXACT_ALARM` user-grant flow) because `minSdk 36` > 33, where `USE_EXACT_ALARM` is auto-granted to install.

## References

- `~/opencode/SIT-ICS-Go/docs/adr/0003-ics-generation-and-caching-strategy.md` — ICS output format (DTSTART/DTEND with `TZID=Asia/Singapore`, SUMMARY, LOCATION, deterministic UID)
- `~/opencode/SIT-ICS-Go/docs/adr/0014-xsite-ics-http-endpoints.md` — ICS HTTP endpoint contract
- `~/opencode/SIT-ICS-Go/internal/server/server.go` — endpoint registration (`/timetable-online.ics`, `/timetable-campus.ics`, xsite paths)
- [RFC 5545](https://tools.ietf.org/html/rfc5545) — iCalendar specification
- [Android: Schedule alarms](https://developer.android.com/develop/background-work/services/alarms) — `USE_EXACT_ALARM` vs `SCHEDULE_EXACT_ALARM`
- [Android: Schedule exact alarms are denied by default](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)
- [Android: Background work with WorkManager](https://developer.android.com/develop/background-work/services/workoverview)
- [Compose BOM / library versions](https://developer.android.com/develop/ui/compose/bom/bom-mapping)
- [Kotlin 2.4.20 release notes](https://kotlinlang.org/docs/whatsnew2420.html)
- [Android 17 release notes](https://developer.android.com/about/versions/17/release-notes)

# ADR-0024: Opt-in Pebble alarm/bedtime export through an authenticated ContentProvider

## Status

Proposed

## Date

2026-10-25

## Context

JankTime2 needs the next scheduled alarm and calculated bedtime from Wakey-SITizen. The values are private app data persisted in DataStore. Direct filesystem access from another app is blocked by Android's application sandbox. A generic HTTP server on localhost creates listener, authentication, lifecycle, and transport complexity for two local timestamp values.

## Options

1. Read the private DataStore directly from JankTime2. Android's per-application sandbox prohibits this.
2. Expose an unauthenticated ContentProvider. This would allow unrelated local applications to read personal schedule metadata and is rejected.
3. Run an HTTP server from Wakey-SITizen. This adds a listening surface, network/authentication configuration, and background lifecycle work for local inter-app access.
4. Push the data to a third-party service. This leaks personal schedule information to a new service and makes the data network-dependent.
5. An opt-in ContentProvider call interface with a high-entropy pairing token, a read-only schema, constant-time digest comparison, and no URI/data-table interface. Disabling export revokes the token.

## Decision

Choose option 5. Export is disabled by default. A user explicitly enables it from Settings, receives a cryptographically random 256-bit pairing token once, and supplies it to their JankTime2 companion. Only the SHA-256 digest is persisted in DataStore; plaintext token is not recoverable. Rotating the token invalidates the previous token. Disabling export both disables the provider and removes the digest.

The provider accepts only the get_snapshot call method and a token in the extras Bundle. It returns schema version 1 with ISO-8601 nextAlarmAt, bedtimeAt, calculatedAt, and updatedAt values. It does not return events, locations, timetable data, alarm overrides, or configuration. The result is computed from locally persisted alarm schedules and bedtime settings. bedtimeAt is returned only when a valid calculated target bedtime is before the next scheduled alarm.

A generic unavailable response is returned for unsupported methods, disabled export, missing/invalid credentials, and internal errors. Request values are bounded; calls are rate-limited per caller UID. No token or payload is written to logs.

## Consequences

- The paired client must be configured by the user and is granted access only while the export toggle is enabled.
- No network endpoint, socket, or third-party data recipient is added.
- Reinstalling the app or clearing app data invalidates local pairing.
- The implementation adds JVM tests for bedtime selection, disabled export, and missing schedules. Provider IPC/authentication tests should be added to instrumentation tests when the companion is integrated.

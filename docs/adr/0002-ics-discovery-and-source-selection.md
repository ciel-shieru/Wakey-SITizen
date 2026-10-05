# ADR-0002: wakey-sitizen JSON discovery endpoint and source selection

## Status

Proposed

## Date

2026-10-05

## Context

Students at the Singapore Institute of Technology (SIT) receive their class timetables from the PeopleSoft student system. The sibling project **SIT-ICS-Go** authenticates against PeopleSoft, fetches the timetable, and publishes data through HTTP endpoints. ADR-0001 established that wakey-sitizen consumes SIT-ICS-Go output to manage alarms, deferring the discovery mechanism to a follow-up ADR.

The integration contract between wakey-sitizen and SIT-ICS-Go must be precise, secure, and minimal. The app needs only ~6 fields per event: course code, title, start/end times, location, and event type (online vs. campus). The existing ICS format (RFC 5545) was designed for calendar-system interoperability, not for single-client API consumption. Parsing ICS introduces unnecessary complexity: line folding at 75 octets, escape sequences (`\\`, `\;`, `\,`, `\n`), CRLF vs. LF handling, and VTIMEZONE blocks — all for data that is already structured in memory on the server side.

## Decision Drivers

- **Security is the first priority**: JSON deserialization with a strict schema is trivially safe and auditable. ICS parsing is a larger attack surface for untrusted text input.
- **Minimal, auditable codebase**: A custom ICS parser would be ~200 lines of edge-case-heavy code. JSON deserialization with `kotlinx.serialization` is ~50 lines of data classes.
- **Predictable contract**: The server already has all events in memory (`ICSCache.events`). Rendering JSON is a small addition. The app gets exactly the fields it needs, typed and unambiguous.
- **Clear separation from SIT-ICS-Go**: wakey-sitizen consumes a well-defined JSON API, not a text format meant for human-readable calendar files.
- **No third-party ICS parsing library**: The format is simple enough that a custom parser would be short, but the edge cases (folding, escaping, timezone blocks) make it higher maintenance than the JSON alternative.

## Decision

wakey-sitizen discovers timetable events through two mutually exclusive modes:

### 1. Base-URL mode

The user provides a SIT-ICS-Go base URL (e.g., `http://192.168.1.100:42748`). The app fetches:

```
GET {baseUrl}/wakey-sitizen/timetable.json
```

The response is validated by checking `schema_version` and `timezone` fields, then deserialized into domain model objects using `kotlinx.serialization`. The `java.net.http.HttpClient` (built into Android API 36+) handles the HTTP request.

**JSON schema** (server-side Go types, serialized to JSON):

```go
type WakeyResponse struct {
    SchemaVersion int       `json:"schema_version"`
    GeneratedAt   time.Time `json:"generated_at"`
    Timezone      string    `json:"timezone"`
    Events        []WakeyEvent `json:"events"`
}

type WakeyEvent struct {
    CourseCode string `json:"course_code"`
    Title      string `json:"title"`
    Summary    string `json:"summary"`
    Dtstart    string `json:"dtstart"`
    Dtend      string `json:"dtend"`
    Location   string `json:"location"`
    EventType  string `json:"event_type"`
}
```

**Kotlin data classes** (client-side):

```kotlin
@Serializable
data class WakeyResponse(
    val schema_version: Int,
    val generated_at: String,
    val timezone: String,
    val events: List<WakeyEvent>
)

@Serializable
data class WakeyEvent(
    val course_code: String,
    val title: String,
    val summary: String,
    val dtstart: String,
    val dtend: String,
    val location: String,
    val event_type: String
)
```

**Schema rules:**
- `event_type` is `"online"` if `strings.EqualFold(Location, "Online")`, otherwise `"campus"`.
- Events with `Source` prefix `brightspace-` are excluded entirely.
- All timestamps are RFC 3339 with `+08:00` (Asia/Singapore fixed offset — SGT has no DST).
- `schema_version` starts at `1`. If the schema changes, bump this and the app validates it before deserializing.
- `generated_at` is the time the server last fetched/updated the timetable (for staleness detection).
- Empty result: `{"schema_version":1,"timezone":"Asia/Singapore","events":[]}` — returns 200 OK, not 204 No Content.

**SIT-ICS-Go implementation** (approx. 30 lines):
1. Add `GetEvents() []Event` method to `ICSCache` (returns a copy under read lock).
2. New handler in `handlers.go`: filters out `brightspace-*` sources, maps `Event` → `WakeyEvent` JSON struct.
3. Register route in `server.go`: `mux.HandleFunc("/wakey-sitizen/timetable.json", newWakeySitizenHandler(...))`.
4. Caching headers: same `Cache-Control` + `ETag` pattern as existing handlers.

### 2. Device-calendar mode

The user grants `READ_CALENDAR`, the app queries `CalendarContract.Calendars` to list user calendars, and the user designates one calendar for online events and one for campus events. The app identifies the correct events by matching the `LOCATION` field against the same predicates as SIT-ICS-Go:
- **Online**: `strings.EqualFold(Location, "Online")` (case-insensitive).
- **Campus**: non-online AND not sourced from BrightSpace (events with `Source` prefix `brightspace-`).

In device-calendar mode, the app reads events directly from `CalendarContract.Events` using the user-selected calendar IDs. Only `title`, `dtstart`, `dtend`, and `eventLocation` are read.

## Rationale

### Why JSON over ICS parsing

1. **Security**: JSON deserialization with a strict schema is trivially safe. ICS parsing introduces a larger attack surface (folding at 75 octets, escape sequences, CRLF handling) for no benefit.
2. **Simplicity**: The app needs only ~6 fields per event. A custom ICS parser would be ~200 lines of edge-case-heavy code. JSON deserialization with `kotlinx.serialization` is ~50 lines of data classes.
3. **Predictability**: The server already has all events in memory (`ICSCache.events`). Rendering JSON is a ~30-line addition to SIT-ICS-Go. The app gets exactly the fields it needs, typed and unambiguous.
4. **No third-party dependencies**: `kotlinx.serialization` is a Jet/Kotlin ecosystem library. No ICS parsing library needed.

### Why two discovery modes

- **Base-URL mode** is the primary mode for users who run their own SIT-ICS-Go server instance. It provides the cleanest integration with the least privilege surface (no calendar access needed).
- **Device-calendar mode** is for users who import their SIT-ICS-Go timetable into their device calendar via a standard ICS subscription and want wakey-sitizen to read from there.

## Consequences

### Positive

- **No ICS parser**: The app never parses ICS files. This eliminates ~200 lines of edge-case-heavy parsing code and the associated attack surface.
- **Predictable contract**: JSON deserialization with a strict schema is simpler to audit, test, and maintain than ICS parsing.
- **Smaller payload**: The JSON response contains only the fields the app needs, not redundant ICS metadata (VTIMEZONE blocks, X-SOURCE, X-OrgUnit*, VALARM, etc.).
- **Testable**: Unit-testing JSON deserialization is straightforward. No need to craft valid/invalid ICS fixtures with correct folding and escaping.

### Negative

- **Requires SIT-ICS-Go change**: A new `/wakey-sitizen/timetable.json` endpoint must be added to SIT-ICS-Go. This is a small addition (~30 lines) but couples the app to a server version that has the endpoint.
- **Version coupling**: The app depends on `schema_version >= 1`. If the schema changes, the app must validate and handle version mismatches.
- **No portability to non-SIT-ICS-Go ICS sources**: The app cannot consume arbitrary ICS feeds. This is acceptable since the app is specifically a companion to SIT-ICS-Go.

### Neutral / operational

- **Schema versioning**: `schema_version` starts at `1`. If fields are added, bump the version and validate on the client side. If fields are removed, keep them with a deprecated marker until the minimum supported version is updated.
- **SIT-ICS-Go endpoint visibility**: The `/wakey-sitizen/` path is a new sub-path under the existing server. It follows the same middleware (logging, caching, conditional requests) as existing endpoints.

## References

- ADR-0001: wakey-sitizen as a GMS-free native Android companion app for SIT-ICS-Go
- [RFC 5545](https://tools.ietf.org/html/rfc5545) — iCalendar specification (the format wakey-sitizen does NOT parse)
- [kotlinx.serialization](https://kotlinlang.org/docs/serialization.html) — Kotlin JSON serialization
- [java.net.http.HttpClient](https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpClient.html) — HTTP client available on Android API 36+

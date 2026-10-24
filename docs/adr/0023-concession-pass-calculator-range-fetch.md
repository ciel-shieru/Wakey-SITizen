# ADR-0023: Isolated concession-pass forecast range fetches

## Status

Proposed

## Date

2026-10-23

## Context

The concession-pass calculator estimates travel spending over an arbitrary 30-day window selected by the user. Normal timetable synchronization is designed around alarm scheduling and must retain its existing behavior: Remote Server sync continues to call the endpoint without a range (or with the existing debug target date), and Device Calendar sync continues to read the seven-day window needed by alarm computation.

Reusing only the normal sync cache is insufficient for a selected future date or a complete 30-day range. The calculator therefore needs its own read-only forecast fetch that does not overwrite or otherwise affect the alarm timetable cache.

## Decision

The calculator calls `TimetableRepository.fetchEventsForRange(start, endInclusive)` for the selected 30 inclusive calendar days.

- **Remote Server mode:** make one request to `/wakey-sitizen/timetable.json?date=YYYY-MM-DD&end_date=YYYY-MM-DD`. The start and end dates are interpreted in the server timezone and inclusive. The existing `date`-only four-day behavior remains supported for normal callers.
- **Device Calendar mode:** query the selected timetable calendars once for the inclusive range using a single `CalendarContract.Instances` range query. Reuse only calendar IDs already selected by the user; do not request broader calendar access.
- Filter and deduplicate events within the chosen range, count one campus visit per local calendar date, and calculate recommendations in the calculator domain logic.
- Do not persist the selected start date or write forecast response data into the normal timetable DataStore. The existing synchronization pipeline and alarm schedule are unchanged.

## Consequences

### Positive

- Forecast requests are straightforward and do not depend on the seven-day alarm sync window or eight sequential/four-day remote windows.
- The selected start date can be changed repeatedly without altering persistent settings or alarm scheduling inputs.
- Both discovery modes use their existing configured data source and least-privilege calendar selection.

### Negative

- A calculator refresh performs an additional request or range query separate from normal sync. Failures are shown as unavailable forecast data rather than treating an incomplete result as a valid zero-visit forecast.
- The remote client and SIT-ICS-Go endpoint must both support the optional inclusive `end_date` parameter. Existing calls without that parameter remain backward-compatible.

## References

- ADR-0002: ICS discovery endpoint and source selection.
- ADR-0007: Background sync mechanism and boot recovery.
- ADR-0020: Device Calendar timetable persistence for next-event display.
- `app/src/main/java/com/cielshieru/wakey/domain/port/TimetableRepository.kt`
- `app/src/main/java/com/cielshieru/wakey/data/repository/TimetableRepositoryImpl.kt`
- `app/src/main/java/com/cielshieru/wakey/data/network/TimetableClient.kt`
- SIT-ICS-Go ADR-0021: Wakey-SITizen JSON timetable endpoint.

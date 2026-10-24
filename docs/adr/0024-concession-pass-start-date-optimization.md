# ADR-0024: Concession pass start-date optimization

## Status

Proposed

## Date

2026-10-24

## Context

The monthly concession-pass calculator evaluates one inclusive 30-day period. Finding a good pass start date manually would require changing the selected date repeatedly and waiting for the same timetable data to load over and over.

The app supports two configured timetable discovery modes: a remote SIT-ICS-Go server and selected device calendars. Optimization must respect the current source configuration, avoid broadening calendar access, and avoid changing cached timetable events used for alarm scheduling.

## Decision

- Add a separate best-start-date screen launched from the calculator results.
- Read the next 60 inclusive calendar days of timetable events exactly once from the configured source. Remote Server mode uses one inclusive-range HTTP request; Device Calendar mode uses one selected-calendar range query.
- Evaluate every candidate start date from today through today + 29 days. Each candidate uses the existing calculator's inclusive 30-day campus-visit and integer-cent fare calculation. The 60-day source range covers the final candidate window through today + 58 days.
- For each pass type, rank the three candidate dates with the highest net savings: estimated fares without a pass minus the pass price. Negative values represent losses; equal results prefer the earlier start date.
- Persist the two average trip-cost input strings under dedicated DataStore keys. Do not write them into alarm configuration or trigger the settings auto-sync pipeline.
- Keep the optimization forecast read-only: do not modify the regular timetable cache, selected start date, or alarm schedule.

## Consequences

### Positive

- All 90 candidate/pass combinations are computed locally from one fetched event set.
- Candidate comparisons use exactly the same campus-day counting, integer-cent arithmetic, and pass prices as the existing calculator.
- The two input fields survive app restarts without creating an alarm settings change or sync run.
- Remote and device-calendar mode continue using their existing configured data sources.

### Negative

- The optimizer reads a 60-day forecast range and may take longer than the ordinary 30-day calculator request.
- A fetch failure prevents ranking, rather than being treated as an empty timetable and producing misleading savings.

## References

- ADR-0023: Isolated concession-pass forecast range fetches.
- `app/src/main/java/com/cielshieru/wakey/domain/ConcessionPassCalculator.kt`
- `app/src/main/java/com/cielshieru/wakey/data/store/ConcessionPassCalculatorStore.kt`
- `app/src/main/java/com/cielshieru/wakey/ui/concession/ConcessionPassBestStartDatesViewModel.kt`

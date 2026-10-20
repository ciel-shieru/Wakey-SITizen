# ADR-0022: Remote Server Status and Manual Refresh

## Status

Proposed

## Date

2026-10-21

## Context

When the app uses SIT-ICS-Go's Remote Server discovery mode, users need to see when the server last ran a fetch and inspect the current in-memory output integrity metadata. They also need an explicit way to request a fetch outside the cron schedule. These controls should not appear when the app has Device Calendar mode saved because the server endpoints may then be unreachable or irrelevant.

## Decision

The settings screen fetches `GET /status` from the saved SIT-ICS-Go base URL and renders the last fetch job time, all reported ETags, and SHA-256 hashes in a readable status card. A `Fetch now` action sends `POST /refresh` and then updates the status display.

The status controls are gated on the persisted discovery mode and use the persisted server URL, not the settings form's unsaved edits. The client accepts only HTTP or HTTPS URLs without embedded credentials, query parameters, or fragments, sets finite request timeouts, and disables redirect following so requests cannot be redirected to a different host. No new network host or endpoint is introduced.

## Consequences

The status card is hidden until Remote Server mode has been saved. Status retrieval failures are shown in the settings screen and do not affect timetable data or alarm scheduling. Manual refresh is an asynchronous server operation; the response acknowledges that the job was scheduled rather than waiting for the fetch to complete.

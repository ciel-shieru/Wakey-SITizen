# ADR-0010: testing strategy

## Status

Proposed

## Date

2026-10-05

## Context

ADR-0001 established that wakey-sitizen must be "properly unit and E2E tested." This ADR defines the testing architecture, test categories, test doubles, and execution strategy.

## Decision Drivers

- **Time-dependent code**: Alarm scheduling, bedtime calculation, and external-event adjustment are all time-dependent. Tests must be able to control the clock.
- **Platform API dependencies**: `AlarmManager`, `WorkManager`, and `CalendarContract` are platform APIs that cannot be instantiated directly in JVM tests. They require test doubles.
- **UI testing**: Jetpack Compose UIs must be tested for correctness, navigation, and state management.
- **E2E reliability**: Instrumented tests must work reliably on emulators (API 36 and 37) without requiring physical devices.

## Decision

### Test categories

#### Unit tests (JVM, `src/test/kotlin/`)

Unit tests run on the JVM without an Android device or emulator. They test pure business logic with injected dependencies.

| Test class | Tests |
|------------|-------|
| `WakeyResponseDeserializationTest` | Valid JSON deserialization, missing fields (defaults), wrong schema version (throws), empty events array, malformed timestamps. |
| `ComputeAlarmScheduleTest` | First-event selection (single event, multiple events, no events), offset application (online vs campus), repeat generation (various intervals and counts), boundary cases (offset > event time), multi-day schedule. |
| `ComputeBedtimeReminderTest` | Normal case, early events (target bedtime previous day), no-events case (returns null), past reminder (returns null), edge case (midnight wrap). |
| `AdjustForExternalEventsTest` | No external events (no adjustment), single external event within buffer (adjusts), external event outside buffer (no adjustment), multiple external events (latest start wins), exact buffer boundary. |
| `ReconcileAlarmsTest` | Idempotent set/clear (same config produces same result), override application (snooze, skip, custom time), override expiry (expired overrides are ignored), no-events clear (clears all alarms when no events). |
| `AlarmConfigValidationTest` | Valid offset ranges (1–720 min), invalid offsets (0, negative, >720), valid repeat intervals (1–30 min), invalid intervals (0, negative, >30), valid max repeats (1–20), invalid max repeats (0, negative, >20). |

#### Instrumented tests (Android, `src/androidTest/kotlin/`)

Instrumented tests run on an Android emulator or physical device. They test code that depends on the Android framework.

| Test class | Tests |
|------------|-------|
| `AlarmServiceE2ETest` | Set alarm → verify it fires (using `InstrumentationWatcher`), clear alarm → verify it does not fire, set multiple alarms → verify all fire, idempotent set/clear → no duplicate alarms. |
| `SyncWorkerE2ETest` | Trigger work → verify alarm reconciliation runs, work with no network → uses last-known data, work with invalid JSON → falls back to last-known data, work with valid JSON → sets correct alarms. |
| `UiTest` | Home screen displays next alarm time, settings navigation works, config changes are persisted, discovery mode switching works, override actions update the UI. |

### Test architecture

#### FakeClock

A `FakeClock` interface replaces `System.currentTimeMillis()` and `Instant.now()` in time-dependent code:

```kotlin
interface Clock {
    fun now(): Instant
    fun plus(duration: Duration): Instant
}

class FakeClock : Clock {
    private var _now: Instant = Instant.EPOCH
    
    fun set(time: Instant) { _now = time }
    override fun now(): Instant = _now
    override fun plus(duration: Duration): Instant = _now.plus(duration)
}
```

`FakeClock` is injected into `ComputeAlarmSchedule`, `ComputeBedtimeReminder`, `AdjustForExternalEvents`, and `ReconcileAlarms`.

#### FakeIcsJsonClient

A `FakeIcsJsonClient` returns test JSON responses:

```kotlin
interface IcsJsonClient {
    suspend fun fetch(baseUrl: String): WakeyResponse
}

class FakeIcsJsonClient : IcsJsonClient {
    private var _response: WakeyResponse? = null
    private var _error: Exception? = null
    
    fun set(response: WakeyResponse) { _response = response; _error = null }
    fun setError(error: Exception) { _error = error; _response = null }
    
    override suspend fun fetch(baseUrl: String): WakeyResponse {
        _error?.let { throw it }
        return _response ?: WakeyResponse(1, "", "Asia/Singapore", emptyList())
    }
}
```

#### FakeAlarmManager

A `FakeAlarmManager` wrapper verifies `setExactAndAllowWhileIdle()` calls without firing real alarms:

```kotlin
interface AlarmManagerWrapper {
    fun setExactAndAllowWhileIdle(type: Int, triggerAtMillis: Long, pendingIntent: PendingIntent)
    fun cancel(pendingIntent: PendingIntent)
    fun cancelAllForDay(dayDate: LocalDate)
}

class FakeAlarmManager : AlarmManagerWrapper {
    val calls = mutableListOf<AlarmCall>()
    val cancellations = mutableListOf<Cancellation>()
    
    data class AlarmCall(val type: Int, val triggerAtMillis: Long, val requestCode: Int)
    data class Cancellation(val requestCode: Int)
    
    override fun setExactAndAllowWhileIdle(type: Int, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        calls.add(AlarmCall(type, triggerAtMillis, pendingIntent.requestCode))
    }
    
    override fun cancel(pendingIntent: PendingIntent) {
        cancellations.add(Cancellation(pendingIntent.requestCode))
    }
    
    override fun cancelAllForDay(dayDate: LocalDate) {
        val baseCode = dayDate.hashCode() * 1000
        (baseCode until baseCode + 100).forEach { cancel(PendingIntent.getBroadcast(/* ... */, it, /* ... */, 0)) }
    }
}
```

#### Turbine for Flow testing

`app.cash.turbine:turbine` is used to test `StateFlow` emissions in ViewModels:

```kotlin
@Test
fun `HomeViewModel shows next alarm time`() = runTest {
    val viewModel = HomeViewModel(/* ... */)
    
    viewModel.uiState.test {
        val first = awaitItem()
        assertEquals("Next alarm: 08:00", first.nextAlarmText)
    }
}
```

### E2E emulator matrix

| Device | API Level | Notes |
|--------|-----------|-------|
| Pixel 7 | 36 (Android 16) | Baseline device. `setExactAndAllowWhileIdle()` works in emulator. |
| Pixel 8 | 37 (Android 17) | Target device. `setExactAndAllowWhileIdle()` works in emulator. |

No physical device testing is required for CI. Emulator-based `AlarmManager` tests are sufficient for verifying alarm firing behavior.

### Test execution

| Command | Scope | When |
|---------|-------|------|
| `./gradlew test` | Unit tests (JVM) | Every commit |
| `./gradlew connectedAndroidTest` | Instrumented tests (emulator) | Pre-merge, release candidates |
| `./gradlew lint` | Lint check | Every commit |
| `./gradlew build` | Full build (compile + test + lint) | Every commit |

### Coverage targets

| Category | Target | Notes |
|----------|--------|-------|
| Unit test coverage (domain layer) | >90% | Business logic is pure and testable. |
| Unit test coverage (data layer) | >80% | Repository wrappers have some platform dependency. |
| UI test coverage (critical paths) | 100% | Navigation, config persistence, alarm display. |
| E2E test coverage (sync flow) | 100% | Full pipeline from fetch to alarm reconciliation. |

## Rationale

### Why FakeClock instead of time-based system tests

Time-based system tests (waiting for real time to pass) are slow, flaky, and hard to parallelize. `FakeClock` allows tests to simulate any point in time instantly, making them fast, deterministic, and parallelizable.

### Why FakeAlarmManager instead of real AlarmManager in unit tests

`AlarmManager` is a system service that requires an Android context. It cannot be instantiated in JVM tests. `FakeAlarmManager` verifies the correct calls are made without actually scheduling alarms.

### Why Turbine for Flow testing

Turbine provides a simple, fluent API for testing `Flow` emissions with assertions. It handles coroutine dispatching automatically and provides clear error messages on assertion failures. Alternatives (e.g., manual `Channel` capture) are more verbose and error-prone.

### Why emulator E2E over physical device

Emulator-based tests are faster to set up, reproducible, and do not require physical device management. `setExactAndAllowWhileIdle()` works correctly in emulators (API 36+), so emulator tests are sufficient for verifying alarm behavior. Physical device testing can be added later if emulator-specific issues arise.

## Consequences

### Positive

- **Fast unit tests**: JVM unit tests with `FakeClock` and `FakeAlarmManager` run in seconds.
- **Deterministic**: No reliance on real time, network, or platform services in unit tests.
- **Comprehensive**: Tests cover business logic, data layer, UI, and E2E sync flow.
- **CI-friendly**: Tests run on emulators without physical device management.

### Negative

- **Test doubles add code**: `FakeClock`, `FakeAlarmManager`, and `FakeIcsJsonClient` add ~150 lines of test infrastructure. This is an accepted tradeoff for testability.
- **Emulator limitations**: Some `AlarmManager` behaviors may differ between emulator and physical device. If issues arise, physical device testing can be added.

### Neutral / operational

- **Test data**: Unit tests use hand-crafted test data (e.g., predefined `WakeyEvent` lists). No external test fixtures are needed.
- **Coroutine testing**: `kotlinx.coroutines.test`'s `runTest` with `StandardTestDispatcher` is used for all coroutine-based tests. No manual `Handler` or `Executor` manipulation is needed.

## References

- [Android: Test your app](https://developer.android.com/training/testing)
- [Compose Testing](https://developer.android.com/develop/ui/compose/testing)
- [Turbine](https://github.com/cashapp/turbine) — Flow testing for Kotlin
- [kotlinx.coroutines.test](https://kotlinlang.org/docs/testing-coroutines.html) — Coroutine testing utilities

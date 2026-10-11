package com.cielshieru.wakey.ui.home

import com.cielshieru.wakey.domain.model.AlarmSchedule
import com.cielshieru.wakey.domain.model.OverrideType
import com.cielshieru.wakey.domain.port.SyncStatus
import com.cielshieru.wakey.fakes.FakeAlarmRepository
import com.cielshieru.wakey.fakes.FakeClock
import com.cielshieru.wakey.fakes.FakeSyncStatus
import com.cielshieru.wakey.fakes.FakeSyncUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fakeClock = FakeClock()
    var fakeAlarmRepository: FakeAlarmRepository = FakeAlarmRepository()
        private set
    var fakeSyncUseCase: FakeSyncUseCase = FakeSyncUseCase()
        private set
    var fakeSyncStatus: FakeSyncStatus = FakeSyncStatus()
        private set

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeClock.set(Instant.EPOCH)
        fakeAlarmRepository = FakeAlarmRepository()
        fakeSyncUseCase = FakeSyncUseCase()
        fakeSyncStatus = FakeSyncStatus()

        viewModel = HomeViewModel(
            alarmRepository = fakeAlarmRepository,
            syncStatus = fakeSyncStatus,
            clock = fakeClock,
            syncOperations = fakeSyncUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial uiState has no alarms and no override text`() = runTest {
        val state = viewModel.uiState.value
        assertEquals("No alarms set", state.nextAlarmText)
        assertNull(state.overrideText)
        assertFalse(state.isSyncing)
        assertNull(state.staleDataWarning)
    }

    @Test
    fun `resetOverrides clears all overrides and triggers sync`() = runTest {
        val tomorrow = LocalDate.now(ZoneId.systemDefault()).plusDays(1).toString()
        fakeAlarmRepository.saveOverride(
            com.cielshieru.wakey.domain.model.ManualOverride(
                type = OverrideType.SKIP_TOMORROW,
                value = tomorrow,
                expiresAt = Instant.now().plusSeconds(86400)
            )
        )

        val overridesBefore = fakeAlarmRepository.overrides.first()
        assertTrue(overridesBefore.isNotEmpty())

        viewModel.resetOverrides()

        val overridesAfter = fakeAlarmRepository.overrides.first()
        assertTrue(overridesAfter.isEmpty())
        assertTrue(fakeAlarmRepository.clearedAllOverrides)
        assertTrue(fakeSyncUseCase.syncCalled)
    }

    @Test
    fun `resetOverrides with multiple override types clears all`() = runTest {
        fakeAlarmRepository.saveOverride(
            com.cielshieru.wakey.domain.model.ManualOverride(
                type = OverrideType.SNOOZE,
                value = "30",
                expiresAt = Instant.now().plusSeconds(86400)
            )
        )
        fakeAlarmRepository.saveOverride(
            com.cielshieru.wakey.domain.model.ManualOverride(
                type = OverrideType.CUSTOM_TIME,
                value = "8:30",
                expiresAt = Instant.now().plusSeconds(86400)
            )
        )

        val overridesBefore = fakeAlarmRepository.overrides.first()
        assertEquals(2, overridesBefore.size)

        viewModel.resetOverrides()

        val overridesAfter = fakeAlarmRepository.overrides.first()
        assertTrue(overridesAfter.isEmpty())
        assertTrue(fakeAlarmRepository.clearedAllOverrides)
    }

    @Test
    fun `resetOverrides with no existing overrides still triggers sync`() = runTest {
        val overridesBefore = fakeAlarmRepository.overrides.first()
        assertTrue(overridesBefore.isEmpty())

        viewModel.resetOverrides()

        val overridesAfter = fakeAlarmRepository.overrides.first()
        assertTrue(overridesAfter.isEmpty())
        assertTrue(fakeAlarmRepository.clearedAllOverrides)
        assertTrue(fakeSyncUseCase.syncCalled)
    }

    @Test
    fun `snooze sets override and updates uiState`() = runTest {
        viewModel.snooze(15)

        val overrides = fakeAlarmRepository.overrides.first()
        assertEquals(1, overrides.size)
        assertEquals(OverrideType.SNOOZE, overrides[0].type)
        assertEquals("15", overrides[0].value)

        val state = viewModel.uiState.value
        assertEquals("Alarms snoozed 15 min", state.overrideText)
    }

    @Test
    fun `skipTomorrow sets override and updates uiState`() = runTest {
        viewModel.skipTomorrow()

        val overrides = fakeAlarmRepository.overrides.first()
        assertEquals(1, overrides.size)
        assertEquals(OverrideType.SKIP_TOMORROW, overrides[0].type)

        val state = viewModel.uiState.value
        assertEquals("Tomorrow's alarms skipped", state.overrideText)
    }

    @Test
    fun `setCustomTime sets override and updates uiState`() = runTest {
        viewModel.setCustomTime(8, 30)

        val overrides = fakeAlarmRepository.overrides.first()
        assertEquals(1, overrides.size)
        assertEquals(OverrideType.CUSTOM_TIME, overrides[0].type)
        assertEquals("8:30", overrides[0].value)

        val state = viewModel.uiState.value
        assertEquals("Custom alarm time at 08:30", state.overrideText)
    }

    @Test
    fun `uiState displays next alarm when schedules are set`() = runTest {
        val zone = ZoneId.systemDefault()
        val futureTime = Instant.now().plusSeconds(3600)
        val alarmSchedule = AlarmSchedule(
            day = LocalDate.now(zone),
            alarms = listOf(
                AlarmSchedule.Alarm(
                    index = 0,
                    triggerAt = futureTime,
                    requestCode = AlarmSchedule.Alarm.computeRequestCode(LocalDate.now(zone), 0)
                )
            ),
            firstAlarmTime = futureTime
        )
        fakeAlarmRepository.saveSchedules(listOf(alarmSchedule))

        val state = viewModel.uiState.value
        assertFalse(state.nextAlarmText.contains("No alarms set"))
    }

    @Test
    fun `uiState override text is null when override expired`() = runTest {
        val pastTime = Instant.EPOCH
        fakeAlarmRepository.saveOverride(
            com.cielshieru.wakey.domain.model.ManualOverride(
                type = OverrideType.SKIP_TOMORROW,
                value = "2024-01-01",
                expiresAt = pastTime
            )
        )

        val state = viewModel.uiState.value
        assertNull(state.overrideText)
    }

    @Test
    fun `uiState shows stale data warning when last sync is old`() = runTest {
        fakeAlarmRepository.lastSyncTimeMillis = Instant.now().minusSeconds(3700000).toEpochMilli()

        val state = viewModel.uiState.value
        assertNotNull(state.staleDataWarning)
        assertTrue(state.staleDataWarning!!.contains("stale"))
    }

    @Test
    fun `uiState does not show stale warning when sync is recent`() = runTest {
        fakeAlarmRepository.lastSyncTimeMillis = Instant.now().toEpochMilli()

        val state = viewModel.uiState.value
        assertNull(state.staleDataWarning)
    }

    @Test
    fun `debug mode uses target date for next alarm calculation`() = runTest {
        val debugConfig = com.cielshieru.wakey.domain.model.DebugConfig(
            debugModeEnabled = true,
            targetDateMillis = Instant.now().plusSeconds(7200).toEpochMilli()
        )
        fakeAlarmRepository.saveDebugConfig(debugConfig)

        val zone = ZoneId.systemDefault()
        val futureTime = Instant.now().plusSeconds(7200)
        val alarmSchedule = AlarmSchedule(
            day = LocalDate.now(zone),
            alarms = listOf(
                AlarmSchedule.Alarm(
                    index = 0,
                    triggerAt = futureTime,
                    requestCode = AlarmSchedule.Alarm.computeRequestCode(LocalDate.now(zone), 0)
                )
            ),
            firstAlarmTime = futureTime
        )
        fakeAlarmRepository.saveSchedules(listOf(alarmSchedule))

        val state = viewModel.uiState.value
        assertFalse(state.nextAlarmText.contains("No alarms set"))
    }

    @Test
    fun `uiState scheduledAlarms is empty when no schedules`() = runTest {
        val state = viewModel.uiState.value
        assertTrue(state.scheduledAlarms.isEmpty())
    }

    @Test
    fun `uiState scheduledAlarms populated from schedules`() = runTest {
        val zone = ZoneId.systemDefault()
        val now = fakeClock.now()
        val futureTime = now.plusSeconds(3600)
        val pastTime = now.minusSeconds(3600)
        val alarmSchedule = AlarmSchedule(
            day = LocalDate.now(zone),
            alarms = listOf(
                AlarmSchedule.Alarm(
                    index = 0,
                    triggerAt = pastTime,
                    requestCode = AlarmSchedule.Alarm.computeRequestCode(LocalDate.now(zone), 0)
                ),
                AlarmSchedule.Alarm(
                    index = 1,
                    triggerAt = futureTime,
                    requestCode = AlarmSchedule.Alarm.computeRequestCode(LocalDate.now(zone), 1)
                )
            ),
            firstAlarmTime = pastTime
        )
        fakeAlarmRepository.saveSchedules(listOf(alarmSchedule))

        val state = viewModel.uiState.value
        assertEquals(2, state.scheduledAlarms.size)
        val pastAlarm = state.scheduledAlarms.find { it.formattedTime.contains("7:00 PM") || state.scheduledAlarms.indexOf(it) == 0 }
        val upcomingAlarms = state.scheduledAlarms.filter { it.isUpcoming }
        val pastAlarms = state.scheduledAlarms.filter { !it.isUpcoming }
        assertEquals(1, upcomingAlarms.size)
        assertEquals(1, pastAlarms.size)
    }

    @Test
    fun `skipAlarm creates override, cancels alarm, and triggers sync`() = runTest {
        val zone = ZoneId.systemDefault()
        val futureTime = Instant.now().plusSeconds(3600)
        val requestCode = AlarmSchedule.Alarm.computeRequestCode(LocalDate.now(zone), 0)
        val alarmSchedule = AlarmSchedule(
            day = LocalDate.now(zone),
            alarms = listOf(
                AlarmSchedule.Alarm(
                    index = 0,
                    triggerAt = futureTime,
                    requestCode = requestCode
                )
            ),
            firstAlarmTime = futureTime
        )
        fakeAlarmRepository.saveSchedules(listOf(alarmSchedule))

        val scheduledAlarm = viewModel.uiState.value.scheduledAlarms.first { it.isUpcoming }

        viewModel.skipAlarm(scheduledAlarm)

        val overrides = fakeAlarmRepository.overrides.first()
        assertEquals(1, overrides.size)
        assertEquals(com.cielshieru.wakey.domain.model.OverrideType.SKIP_ALARM, overrides[0].type)
        assertEquals(requestCode.toString(), overrides[0].value)
        assertTrue(fakeAlarmRepository.cancelledRequestCodes.contains(requestCode))
        assertTrue(fakeSyncUseCase.syncCalled)
    }

    @Test
    fun `uiState shows bedtimeText when enabled and schedules exist`() = runTest {
        val zone = ZoneId.systemDefault()
        val now = fakeClock.now()
        val firstAlarm = now.plusSeconds(12 * 3600)
        val alarmSchedule = AlarmSchedule(
            day = LocalDate.now(zone),
            alarms = listOf(
                AlarmSchedule.Alarm(
                    index = 0,
                    triggerAt = firstAlarm,
                    requestCode = AlarmSchedule.Alarm.computeRequestCode(LocalDate.now(zone), 0)
                )
            ),
            firstAlarmTime = firstAlarm
        )
        fakeAlarmRepository.saveSchedules(listOf(alarmSchedule))
        fakeAlarmRepository.saveConfig(
            com.cielshieru.wakey.domain.model.AlarmConfig(
                bedtimeEnabled = true,
                targetSleepDurationHours = 8,
                bedtimeReminderOffsetMinutes = 30
            )
        )

        val state = viewModel.uiState.value
        assertNotNull(state.bedtimeText)
        assertTrue(state.bedtimeText!!.startsWith("Bedtime:"))
    }

    @Test
    fun `uiState bedtimeText is null when bedtime disabled`() = runTest {
        val zone = ZoneId.systemDefault()
        val now = fakeClock.now()
        val firstAlarm = now.plusSeconds(8 * 3600 + 30 * 60)
        val alarmSchedule = AlarmSchedule(
            day = LocalDate.now(zone),
            alarms = listOf(
                AlarmSchedule.Alarm(
                    index = 0,
                    triggerAt = firstAlarm,
                    requestCode = AlarmSchedule.Alarm.computeRequestCode(LocalDate.now(zone), 0)
                )
            ),
            firstAlarmTime = firstAlarm
        )
        fakeAlarmRepository.saveSchedules(listOf(alarmSchedule))
        fakeAlarmRepository.saveConfig(
            com.cielshieru.wakey.domain.model.AlarmConfig(
                bedtimeEnabled = false,
                targetSleepDurationHours = 8,
                bedtimeReminderOffsetMinutes = 30
            )
        )

        val state = viewModel.uiState.value
        assertNull(state.bedtimeText)
    }

    @Test
    fun `uiState bedtimeText is null when no schedules`() = runTest {
        fakeAlarmRepository.saveConfig(
            com.cielshieru.wakey.domain.model.AlarmConfig(
                bedtimeEnabled = true,
                targetSleepDurationHours = 8,
                bedtimeReminderOffsetMinutes = 30
            )
        )

        val state = viewModel.uiState.value
        assertNull(state.bedtimeText)
    }

    @Test
    fun `uiState bedtimeText is null when reminder already passed`() = runTest {
        val zone = ZoneId.systemDefault()
        val now = fakeClock.now()
        val firstAlarm = now.plusSeconds(30 * 60)
        val alarmSchedule = AlarmSchedule(
            day = LocalDate.now(zone),
            alarms = listOf(
                AlarmSchedule.Alarm(
                    index = 0,
                    triggerAt = firstAlarm,
                    requestCode = AlarmSchedule.Alarm.computeRequestCode(LocalDate.now(zone), 0)
                )
            ),
            firstAlarmTime = firstAlarm
        )
        fakeAlarmRepository.saveSchedules(listOf(alarmSchedule))
        fakeAlarmRepository.saveConfig(
            com.cielshieru.wakey.domain.model.AlarmConfig(
                bedtimeEnabled = true,
                targetSleepDurationHours = 8,
                bedtimeReminderOffsetMinutes = 30
            )
        )

        val state = viewModel.uiState.value
        assertNull(state.bedtimeText)
    }
}

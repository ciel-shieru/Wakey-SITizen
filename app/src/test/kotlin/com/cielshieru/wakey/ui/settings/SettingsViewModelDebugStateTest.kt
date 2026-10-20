package com.cielshieru.wakey.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.lifecycle.viewModelScope
import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.RingtonePlaylist
import com.cielshieru.wakey.domain.model.PermissionEntry
import com.cielshieru.wakey.domain.model.PermissionStatus
import com.cielshieru.wakey.domain.port.AlarmRepository
import com.cielshieru.wakey.domain.port.PermissionChecker
import com.cielshieru.wakey.fakes.FakeAlarmRepository
import com.cielshieru.wakey.fakes.FakeSyncUseCase
import com.cielshieru.wakey.fakes.FakeClock
import com.cielshieru.wakey.fakes.FakeZoneProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelDebugStateTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var alarmRepository: FakeAlarmRepository
    private lateinit var permissionChecker: PermissionChecker
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var testFile: File
    private lateinit var syncUseCase: FakeSyncUseCase
    private lateinit var viewModel: SettingsViewModel
    private lateinit var clock: FakeClock
    private lateinit var zoneProvider: FakeZoneProvider

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        alarmRepository = FakeAlarmRepository()
        permissionChecker = object : PermissionChecker {
            override fun checkNotificationsPermission(context: android.content.Context): Boolean = true
            override fun checkCalendarPermission(context: android.content.Context): Boolean = true
            override suspend fun getAllPermissionEntries(context: android.content.Context): List<PermissionEntry> =
                emptyList()
        }

        testFile = File("/tmp/test_debug_datastore_${System.nanoTime()}.preferences_pb")
        testFile.delete()
        dataStore = PreferenceDataStoreFactory.create(produceFile = { testFile })

        syncUseCase = FakeSyncUseCase()
        clock = FakeClock().apply { set(Instant.parse("2026-10-20T05:46:40Z")) }
        zoneProvider = FakeZoneProvider().apply { zone = ZoneId.of("Asia/Singapore") }

        viewModel = SettingsViewModel(
            alarmRepository = alarmRepository,
            permissionChecker = permissionChecker,
            datastore = dataStore,
            syncUseCase = syncUseCase,
            clock = clock,
            zoneProvider = zoneProvider
        )
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
        testFile.delete()
    }

    @Test
    fun `server status eligibility follows saved discovery mode, not unsaved selection`() = runTest {
        assertEquals(com.cielshieru.wakey.domain.model.DiscoveryMode.DeviceCalendar, viewModel.savedServerSettings.value.discoveryMode)

        viewModel.selectDiscoveryMode(com.cielshieru.wakey.domain.model.DiscoveryMode.RemoteServer)
        viewModel.updateRemoteServerUrl("http://localhost:42748")
        assertEquals(com.cielshieru.wakey.domain.model.DiscoveryMode.DeviceCalendar, viewModel.savedServerSettings.value.discoveryMode)

        viewModel.saveConfig()
        assertEquals(com.cielshieru.wakey.domain.model.DiscoveryMode.RemoteServer, viewModel.savedServerSettings.value.discoveryMode)
        assertEquals("http://localhost:42748", viewModel.savedServerSettings.value.remoteServerUrl)
    }

    @Test
    fun `saving settings schedules periodic and immediate sync`() = runTest {
        viewModel.saveConfig()

        assertTrue(syncUseCase.cancelCalled)
        assertTrue(syncUseCase.scheduleCalled)
        assertTrue(syncUseCase.syncCalled)
    }

    @Test
    fun `test alarm schedules one second after press using normal alarm data`() = runTest {
        alarmRepository.saveConfig(
            AlarmConfig(
                ringtonePlaylist = RingtonePlaylist(listOf("content://alarm-tone"))
            )
        )
        viewModel.toggleDebugMode(true)

        viewModel.testAlarmNow()

        val (alarm, day) = alarmRepository.scheduledAlarms.single()
        assertEquals(Instant.parse("2026-10-20T05:46:41Z"), alarm.triggerAt)
        assertEquals(LocalDate.of(2026, 10, 20), day)
        assertEquals(com.cielshieru.wakey.android.AlarmService.DEBUG_TEST_ALARM_REQUEST_CODE, alarm.requestCode)
        assertEquals("content://alarm-tone", alarm.ringtoneUri)
    }

    @Test
    fun `test alarm does nothing when debug mode is disabled`() = runTest {
        viewModel.testAlarmNow()
        assertEquals(0, alarmRepository.scheduledAlarms.size)
    }

    @Test
    fun `initial debug state is disabled with null target date`() = runTest {
        val state = viewModel.uiState.value
        assertEquals(false, state.debugModeEnabled)
        assertNull(state.targetDateMillis)
    }

    @Test
    fun `toggleDebugMode enables mode`() = runTest {
        viewModel.toggleDebugMode(true)
        val state = viewModel.uiState.value
        assertEquals(true, state.debugModeEnabled)
        assertNull(state.targetDateMillis)
    }

    @Test
    fun `toggleDebugMode(false) clears target date`() = runTest {
        viewModel.setTargetDate(1700000000000L)
        var state = viewModel.uiState.value
        assertEquals(1700000000000L, state.targetDateMillis)

        viewModel.toggleDebugMode(false)
        state = viewModel.uiState.value
        assertEquals(false, state.debugModeEnabled)
        assertNull(state.targetDateMillis)
    }

    @Test
    fun `toggleDebugMode(true) then false clears both`() = runTest {
        viewModel.toggleDebugMode(true)
        var state = viewModel.uiState.value
        assertEquals(true, state.debugModeEnabled)

        viewModel.setTargetDate(1700000000000L)
        state = viewModel.uiState.value
        assertEquals(1700000000000L, state.targetDateMillis)

        viewModel.toggleDebugMode(false)
        state = viewModel.uiState.value
        assertEquals(false, state.debugModeEnabled)
        assertNull(state.targetDateMillis)
    }

    @Test
    fun `setTargetDate updates targetDateMillis`() = runTest {
        val testMillis = 1700000000000L
        viewModel.setTargetDate(testMillis)
        val state = viewModel.uiState.value
        assertEquals(testMillis, state.targetDateMillis)
    }

    @Test
    fun `setTargetDate can clear millis with null`() = runTest {
        viewModel.setTargetDate(1700000000000L)
        viewModel.setTargetDate(null)
        val state = viewModel.uiState.value
        assertNull(state.targetDateMillis)
    }

    @Test
    fun `saveDebugConfig calls repository with correct config`() = runTest {
        viewModel.toggleDebugMode(true)
        viewModel.setTargetDate(1700000000000L)
        viewModel.saveDebugConfig()

        val savedConfig = alarmRepository.savedDebugConfig
        assertEquals(true, savedConfig?.debugModeEnabled)
        assertEquals(1700000000000L, savedConfig?.targetDateMillis)
    }

    @Test
    fun `saveDebugConfig with disabled mode saves null target date`() = runTest {
        viewModel.toggleDebugMode(false)
        viewModel.saveDebugConfig()

        val savedConfig = alarmRepository.savedDebugConfig
        assertEquals(false, savedConfig?.debugModeEnabled)
        assertNull(savedConfig?.targetDateMillis)
    }

    @Test
    fun `debug config flows through alarmRepository`() = runTest {
        val initialDebug = alarmRepository.debugConfig.first()
        assertEquals(false, initialDebug.debugModeEnabled)
        assertNull(initialDebug.targetDateMillis)

        viewModel.toggleDebugMode(true)
        viewModel.setTargetDate(1700000000000L)
        viewModel.saveDebugConfig()

        val updatedDebug = alarmRepository.debugConfig.first()
        assertEquals(true, updatedDebug.debugModeEnabled)
        assertEquals(1700000000000L, updatedDebug.targetDateMillis)
    }

    @Test
    fun `discovery mode changes preserve selected calendars and calendar permission`() = runTest {
        viewModel.setPermissionGranted("READ_CALENDAR", true)
        viewModel.selectOnlineCalendar("online-calendar")
        viewModel.selectCampusCalendar("campus-calendar")
        viewModel.toggleExternalEventCalendar("external-calendar")

        viewModel.selectDiscoveryMode(com.cielshieru.wakey.domain.model.DiscoveryMode.RemoteServer)
        viewModel.selectDiscoveryMode(com.cielshieru.wakey.domain.model.DiscoveryMode.DeviceCalendar)
        viewModel.toggleExternalEvents(false)
        viewModel.toggleExternalEvents(true)

        val state = viewModel.uiState.value
        assertEquals("online-calendar", state.onlineCalendarPicker.selectedId)
        assertEquals("campus-calendar", state.campusCalendarPicker.selectedId)
        assertEquals(listOf("external-calendar"), state.externalEventCalendarPicker.selectedIds)
        assertEquals(true, state.onlineCalendarPicker.permissionGranted)
        assertEquals(true, state.campusCalendarPicker.permissionGranted)
        assertEquals(true, state.externalEventCalendarPicker.permissionGranted)
    }

}

package com.cielshieru.wakey.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.cielshieru.wakey.domain.model.PermissionEntry
import com.cielshieru.wakey.domain.model.PermissionStatus
import com.cielshieru.wakey.domain.port.AlarmRepository
import com.cielshieru.wakey.domain.port.PermissionChecker
import com.cielshieru.wakey.fakes.FakeAlarmRepository
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
    private lateinit var syncUseCase: FakeSyncUseCase
    private lateinit var viewModel: SettingsViewModel

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

        val testFile = File("/tmp/test_debug_datastore.preferences_pb")
        testFile.delete()
        dataStore = PreferenceDataStoreFactory.create(produceFile = { testFile })

        syncUseCase = FakeSyncUseCase()

        viewModel = SettingsViewModel(
            alarmRepository = alarmRepository,
            permissionChecker = permissionChecker,
            datastore = dataStore,
            syncUseCase = syncUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        File("/tmp/test_debug_datastore.preferences_pb").delete()
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

package com.cielshieru.wakey.ui.ringtone

import com.cielshieru.wakey.data.RingtoneNameResolver
import com.cielshieru.wakey.domain.model.RingtonePlaylist
import com.cielshieru.wakey.fakes.FakeAlarmRepository
import com.cielshieru.wakey.fakes.FakeSyncUseCase
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RingtonePickerViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeAlarmRepository: FakeAlarmRepository
    private lateinit var syncUseCase: FakeSyncUseCase
    private lateinit var viewModel: RingtonePickerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeAlarmRepository = FakeAlarmRepository()
        syncUseCase = FakeSyncUseCase()
        viewModel = RingtonePickerViewModel(
            alarmRepository = fakeAlarmRepository,
            ringtoneNameResolver = StubRingtoneNameResolver(),
            syncUseCase = syncUseCase
        )
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `initial uiState reflects empty playlist from repository`() = runTest {
        val state = viewModel.uiState.value
        assertTrue(state.ringtoneUris.isEmpty())
        assertFalse(state.shuffleEnabled)
    }

    @Test
    fun `initial uiState reflects non-empty playlist from repository`() = runTest {
        val uris = listOf("content://ringtone1", "content://ringtone2")
        fakeAlarmRepository.saveConfig(
            com.cielshieru.wakey.domain.model.AlarmConfig(
                ringtonePlaylist = RingtonePlaylist(uris, shuffleEnabled = true)
            )
        )

        val state = viewModel.uiState.value
        assertEquals(2, state.ringtoneUris.size)
        assertEquals("content://ringtone1", state.ringtoneUris[0])
        assertEquals("content://ringtone2", state.ringtoneUris[1])
        assertTrue(state.shuffleEnabled)
    }

    @Test
    fun `addRingtone adds URI to list`() = runTest {
        viewModel.addRingtone("content://new_ringtone")

        val state = viewModel.uiState.value
        assertEquals(1, state.ringtoneUris.size)
        assertEquals("content://new_ringtone", state.ringtoneUris[0])
    }

    @Test
    fun `addRingtone rejects when at max capacity`() = runTest {
        val uris = (1..20).map { "content://ringtone$it" }
        fakeAlarmRepository.saveConfig(
            com.cielshieru.wakey.domain.model.AlarmConfig(
                ringtonePlaylist = RingtonePlaylist(uris)
            )
        )

        viewModel.addRingtone("content://overflow")

        val state = viewModel.uiState.value
        assertEquals(20, state.ringtoneUris.size)
    }

    @Test
    fun `addRingtone rejects duplicate URI`() = runTest {
        viewModel.addRingtone("content://unique")
        viewModel.addRingtone("content://unique")

        val state = viewModel.uiState.value
        assertEquals(1, state.ringtoneUris.size)
    }

    @Test
    fun `removeRingtone removes URI from list`() = runTest {
        val uris = listOf("content://a", "content://b", "content://c")
        fakeAlarmRepository.saveConfig(
            com.cielshieru.wakey.domain.model.AlarmConfig(
                ringtonePlaylist = RingtonePlaylist(uris)
            )
        )

        viewModel.removeRingtone("content://b")

        val state = viewModel.uiState.value
        assertEquals(2, state.ringtoneUris.size)
        assertFalse(state.ringtoneUris.contains("content://b"))
        assertTrue(state.ringtoneUris.contains("content://a"))
        assertTrue(state.ringtoneUris.contains("content://c"))
    }

    @Test
    fun `reorderRingtones swaps items`() = runTest {
        val testRepo = FakeAlarmRepository()
        val testViewModel = RingtonePickerViewModel(
            alarmRepository = testRepo,
            ringtoneNameResolver = StubRingtoneNameResolver(),
            syncUseCase = FakeSyncUseCase()
        )

        val uris = listOf("content://a", "content://b", "content://c")
        testRepo.saveConfig(
            com.cielshieru.wakey.domain.model.AlarmConfig(
                ringtonePlaylist = RingtonePlaylist(uris)
            )
        )

        testViewModel.reorderRingtones(0, 2)

        val state = testViewModel.uiState.value
        assertEquals(3, state.ringtoneUris.size)
        // Moving index 0 to index 2: remove 'a' from position 0, add at position 2 => [b, c, a]
        assertEquals("content://b", state.ringtoneUris[0])
        assertEquals("content://c", state.ringtoneUris[1])
        assertEquals("content://a", state.ringtoneUris[2])
        testViewModel.viewModelScope.cancel()
    }

    @Test
    fun `reorderRingtones with invalid indices does nothing`() = runTest {
        val uris = listOf("content://a", "content://b")
        fakeAlarmRepository.saveConfig(
            com.cielshieru.wakey.domain.model.AlarmConfig(
                ringtonePlaylist = RingtonePlaylist(uris)
            )
        )

        viewModel.reorderRingtones(-1, 0)
        viewModel.reorderRingtones(0, 99)

        val state = viewModel.uiState.value
        assertEquals("content://a", state.ringtoneUris[0])
        assertEquals("content://b", state.ringtoneUris[1])
    }

    @Test
    fun `toggleShuffle updates shuffle state`() = runTest {
        viewModel.toggleShuffle(true)

        val state = viewModel.uiState.value
        assertTrue(state.shuffleEnabled)

        viewModel.toggleShuffle(false)

        val state2 = viewModel.uiState.value
        assertFalse(state2.shuffleEnabled)
    }

    @Test
    fun `save persists playlist to repository`() = runTest {
        viewModel.addRingtone("content://saved_ringtone")
        viewModel.toggleShuffle(true)
        viewModel.save()

        val savedConfig = fakeAlarmRepository.savedConfig
        assertNotNull(savedConfig)
        assertEquals(1, savedConfig!!.ringtonePlaylist.size)
        assertEquals("content://saved_ringtone", savedConfig.ringtonePlaylist.ringtoneUris[0])
        assertTrue(savedConfig.ringtonePlaylist.shuffleEnabled)
    }

    @Test
    fun `playlist changes auto-save and trigger a replacement sync after edits settle`() = runTest {
        viewModel.addRingtone("content://autosaved")
        advanceTimeBy(RingtonePickerViewModel.AUTO_SAVE_DEBOUNCE_MILLIS)
        runCurrent()

        val savedConfig = fakeAlarmRepository.savedConfig
        assertNotNull(savedConfig)
        assertEquals("content://autosaved", savedConfig!!.ringtonePlaylist.ringtoneUris.single())
        assertTrue(syncUseCase.syncCalled)
        assertFalse(syncUseCase.cancelCalled)
    }

    @Test
    fun `save emits Saved event`() = runTest {
        viewModel.save()

        val event = viewModel.event.first()
        assertNotNull(event)
        assertTrue(event is RingtonePickerEvent.Saved)
    }

    @Test
    fun `clearEvent clears the event`() = runTest {
        viewModel.save()
        viewModel.clearEvent()

        val event = viewModel.event.first()
        assertNull(event)
    }

    @Test
    fun `resolveRingtoneName delegates to resolver`() = runTest {
        val name = viewModel.resolveRingtoneName("content://some_ringtone")
        assertEquals("Ringtone A", name)
    }

    @Test
    fun `addRingtone respects MAX_RINGTONES limit`() = runTest {
        val uris = (1..19).map { "content://ringtone$it" }
        fakeAlarmRepository.saveConfig(
            com.cielshieru.wakey.domain.model.AlarmConfig(
                ringtonePlaylist = RingtonePlaylist(uris)
            )
        )

        repeat(5) {
            viewModel.addRingtone("content://extra$it")
        }

        val state = viewModel.uiState.value
        assertEquals(20, state.ringtoneUris.size)
    }
}

class StubRingtoneNameResolver : RingtoneNameResolver(null) {
    override fun resolveName(uriString: String): String {
        return "Ringtone A"
    }

    override fun resolveName(uri: android.net.Uri): String {
        return "Ringtone A"
    }
}

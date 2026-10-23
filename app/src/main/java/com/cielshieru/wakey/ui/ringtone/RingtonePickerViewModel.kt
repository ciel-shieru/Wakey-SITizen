package com.cielshieru.wakey.ui.ringtone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cielshieru.wakey.data.RingtoneNameResolver
import com.cielshieru.wakey.usecase.SyncOperations
import com.cielshieru.wakey.usecase.SyncUseCase
import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.RingtonePlaylist
import com.cielshieru.wakey.domain.port.AlarmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RingtonePickerUiState(
    val ringtoneUris: List<String> = emptyList(),
    val shuffleEnabled: Boolean = false,
    val isLoading: Boolean = false
)

sealed interface RingtonePickerEvent {
    data object Saved : RingtonePickerEvent
}

@HiltViewModel
class RingtonePickerViewModel @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val ringtoneNameResolver: RingtoneNameResolver,
    private val syncUseCase: SyncOperations
) : ViewModel() {

    companion object {
        const val AUTO_SAVE_DEBOUNCE_MILLIS = 400L
    }

    private var hasLocalChanges = false
    private var autoSaveJob: Job? = null
    private val playlistSaveMutex = Mutex()

    private val _uiState = MutableStateFlow(RingtonePickerUiState())
    val uiState: StateFlow<RingtonePickerUiState> = _uiState.asStateFlow()

    private val _event = MutableStateFlow<RingtonePickerEvent?>(null)
    val event: StateFlow<RingtonePickerEvent?> = _event.asStateFlow()

    init {
        viewModelScope.launch {
            alarmRepository.config.collect { config ->
                // Do not overwrite local edits when a just-saved configuration is emitted
                // back from DataStore while another edit is still pending.
                if (!hasLocalChanges) {
                    _uiState.update {
                        it.copy(
                            ringtoneUris = config.ringtonePlaylist.ringtoneUris,
                            shuffleEnabled = config.ringtonePlaylist.shuffleEnabled
                        )
                    }
                }
            }
        }
    }

    fun addRingtone(uri: String) {
        val state = _uiState.value
        if (state.ringtoneUris.size >= RingtonePlaylist.MAX_RINGTONES) return
        if (uri in state.ringtoneUris) return
        _uiState.update { it.copy(ringtoneUris = it.ringtoneUris + uri) }
        scheduleAutoSave()
    }

    fun removeRingtone(uri: String) {
        if (uri !in _uiState.value.ringtoneUris) return
        _uiState.update { state ->
            state.copy(ringtoneUris = state.ringtoneUris - uri)
        }
        scheduleAutoSave()
    }

    fun reorderRingtones(fromIndex: Int, toIndex: Int) {
        val current = _uiState.value.ringtoneUris
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return
        val list = current.toMutableList()
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _uiState.update { it.copy(ringtoneUris = list) }
        scheduleAutoSave()
    }

    fun toggleShuffle(enabled: Boolean) {
        if (_uiState.value.shuffleEnabled == enabled) return
        _uiState.update { it.copy(shuffleEnabled = enabled) }
        scheduleAutoSave()
    }

    private fun scheduleAutoSave() {
        hasLocalChanges = true
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(AUTO_SAVE_DEBOUNCE_MILLIS)
            persistPlaylist(_uiState.value, emitSaved = false)
        }
    }

    suspend fun flushPendingAutoSave() {
        val pending = autoSaveJob
        if (pending?.isActive == true) {
            pending.cancelAndJoin()
            persistPlaylist(_uiState.value, emitSaved = false)
        }
    }

    fun exit() {
        viewModelScope.launch {
            val pending = autoSaveJob
            if (pending?.isActive == true) {
                pending.cancelAndJoin()
                persistPlaylist(_uiState.value, emitSaved = true)
            } else {
                _event.value = RingtonePickerEvent.Saved
            }
        }
    }

    fun save() {
        viewModelScope.launch {
            autoSaveJob?.cancelAndJoin()
            persistPlaylist(_uiState.value, emitSaved = true)
        }
    }

    private suspend fun persistPlaylist(
        state: RingtonePickerUiState,
        emitSaved: Boolean
    ) = playlistSaveMutex.withLock {
        val playlist = RingtonePlaylist(
            ringtoneUris = state.ringtoneUris,
            shuffleEnabled = state.shuffleEnabled
        )
        val currentConfig = alarmRepository.config.first()
        alarmRepository.saveConfig(currentConfig.copy(ringtonePlaylist = playlist))
        // ExistingWorkPolicy.REPLACE in SyncUseCase cancels an older immediate sync.
        syncUseCase.syncImmediately(SyncUseCase.UNIQUE_IMMEDIATE_SYNC)
        if (emitSaved) _event.value = RingtonePickerEvent.Saved
    }

    fun clearEvent() {
        _event.value = null
    }

    fun resolveRingtoneName(uri: String): String {
        return ringtoneNameResolver.resolveName(uri)
    }
}

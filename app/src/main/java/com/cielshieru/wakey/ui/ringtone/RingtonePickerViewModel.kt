package com.cielshieru.wakey.ui.ringtone

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cielshieru.wakey.data.RingtoneNameResolver
import com.cielshieru.wakey.domain.model.AlarmConfig
import com.cielshieru.wakey.domain.model.RingtonePlaylist
import com.cielshieru.wakey.domain.port.AlarmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val ringtoneNameResolver: RingtoneNameResolver
) : ViewModel() {

    private val _uiState = MutableStateFlow(RingtonePickerUiState())
    val uiState: StateFlow<RingtonePickerUiState> = _uiState.asStateFlow()

    private val _event = MutableStateFlow<RingtonePickerEvent?>(null)
    val event: StateFlow<RingtonePickerEvent?> = _event.asStateFlow()

    init {
        viewModelScope.launch {
            alarmRepository.config.collect { config ->
                _uiState.update {
                    it.copy(
                        ringtoneUris = config.ringtonePlaylist.ringtoneUris,
                        shuffleEnabled = config.ringtonePlaylist.shuffleEnabled
                    )
                }
            }
        }
    }

    fun addRingtone(uri: String) {
        val current = _uiState.value.ringtoneUris
        if (current.size >= RingtonePlaylist.MAX_RINGTONES) return
        if (uri in current) return
        _uiState.update { it.copy(ringtoneUris = current + uri) }
    }

    fun removeRingtone(uri: String) {
        _uiState.update { state ->
            state.copy(ringtoneUris = state.ringtoneUris - uri)
        }
    }

    fun reorderRingtones(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val list = state.ringtoneUris.toMutableList()
            if (fromIndex in list.indices && toIndex in list.indices) {
                val item = list.removeAt(fromIndex)
                list.add(toIndex, item)
                state.copy(ringtoneUris = list)
            } else {
                state
            }
        }
    }

    fun toggleShuffle(enabled: Boolean) {
        _uiState.update { it.copy(shuffleEnabled = enabled) }
    }

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            val playlist = RingtonePlaylist(
                ringtoneUris = state.ringtoneUris,
                shuffleEnabled = state.shuffleEnabled
            )
            val currentConfig = alarmRepository.config.first()
            alarmRepository.saveConfig(currentConfig.copy(ringtonePlaylist = playlist))
            _event.value = RingtonePickerEvent.Saved
        }
    }

    fun clearEvent() {
        _event.value = null
    }

    fun resolveRingtoneName(uri: String): String {
        return ringtoneNameResolver.resolveName(uri)
    }
}

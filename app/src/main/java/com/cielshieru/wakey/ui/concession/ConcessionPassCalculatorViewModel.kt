package com.cielshieru.wakey.ui.concession

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cielshieru.wakey.domain.ConcessionPassCalculator
import com.cielshieru.wakey.domain.model.DiscoveryMode
import com.cielshieru.wakey.domain.port.AlarmRepository
import com.cielshieru.wakey.domain.port.Clock
import com.cielshieru.wakey.domain.port.TimetableRepository
import com.cielshieru.wakey.domain.port.ZoneProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConcessionPassCalculatorUiState(
    val today: LocalDate,
    val startDate: LocalDate,
    val toCampusCostInput: String = "",
    val fromCampusCostInput: String = "",
    val campusVisitDays: Int? = null,
    val isLoading: Boolean = true,
    val loadError: String? = null
) {
    val estimatedTotalCents: Long?
        get() {
            val visits = campusVisitDays ?: return null
            val toCampus = ConcessionPassCalculator.parseCostToCents(toCampusCostInput) ?: return null
            val fromCampus = ConcessionPassCalculator.parseCostToCents(fromCampusCostInput) ?: return null
            return ConcessionPassCalculator.totalCostCents(toCampus, fromCampus, visits)
        }
}

@HiltViewModel
class ConcessionPassCalculatorViewModel @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val timetableRepository: TimetableRepository,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider
) : ViewModel() {

    private val currentDate = LocalDate.ofInstant(clock.now(), zoneProvider.zone)
    private val selectedStartDate = MutableStateFlow(currentDate)
    private val retryCounter = MutableStateFlow(0)

    private val _uiState = MutableStateFlow(
        ConcessionPassCalculatorUiState(
            today = currentDate,
            startDate = currentDate
        )
    )
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(selectedStartDate, alarmRepository.config, retryCounter) { date, config, retry ->
                Triple(date, config, retry)
            }
                .distinctUntilChanged()
                .collect { (date, config, _) ->
                    _uiState.update {
                        it.copy(
                            startDate = date,
                            campusVisitDays = null,
                            isLoading = true,
                            loadError = null
                        )
                    }

                    val result = timetableRepository.fetchEventsForRange(
                        start = date,
                        endInclusive = date.plusDays(ConcessionPassCalculator.WINDOW_DAYS - 1L)
                    )

                    result.fold(
                        onSuccess = { events ->
                            _uiState.update {
                                it.copy(
                                    campusVisitDays = ConcessionPassCalculator.campusVisitCount(events, date),
                                    isLoading = false,
                                    loadError = null
                                )
                            }
                        },
                        onFailure = {
                            val message = when (config.discoveryMode) {
                                DiscoveryMode.RemoteServer ->
                                    "Unable to load timetable events for this 30-day range. Check the configured server connection and retry."
                                DiscoveryMode.DeviceCalendar ->
                                    "Unable to load timetable events for this 30-day range. Check the selected calendars and retry."
                            }
                            _uiState.update {
                                it.copy(
                                    campusVisitDays = null,
                                    isLoading = false,
                                    loadError = message
                                )
                            }
                        }
                    )
                }
        }
    }

    fun updateToCampusCost(value: String) {
        if (ConcessionPassCalculator.isCostInputAllowed(value)) {
            _uiState.update { it.copy(toCampusCostInput = value) }
        }
    }

    fun updateFromCampusCost(value: String) {
        if (ConcessionPassCalculator.isCostInputAllowed(value)) {
            _uiState.update { it.copy(fromCampusCostInput = value) }
        }
    }

    fun selectStartDate(date: LocalDate) {
        val today = LocalDate.ofInstant(clock.now(), zoneProvider.zone)
        if (!date.isBefore(today)) {
            selectedStartDate.value = date
        }
    }

    fun retry() {
        retryCounter.update { it + 1 }
    }
}

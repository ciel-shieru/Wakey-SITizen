package com.cielshieru.wakey.ui.concession

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cielshieru.wakey.data.store.concessionPassCostsFlow
import com.cielshieru.wakey.domain.ConcessionPassCalculator
import com.cielshieru.wakey.domain.MonthlyConcessionPass
import com.cielshieru.wakey.domain.ConcessionPassStartDateRecommendation
import com.cielshieru.wakey.domain.model.DiscoveryMode
import com.cielshieru.wakey.domain.port.AlarmRepository
import com.cielshieru.wakey.domain.port.Clock
import com.cielshieru.wakey.domain.port.TimetableRepository
import com.cielshieru.wakey.domain.port.ZoneProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConcessionPassBestStartDatesUiState(
    val today: LocalDate,
    val isLoading: Boolean = true,
    val recommendations: Map<MonthlyConcessionPass, List<ConcessionPassStartDateRecommendation>> = emptyMap(),
    val loadError: String? = null
)

@HiltViewModel
class ConcessionPassBestStartDatesViewModel @Inject constructor(
    private val datastore: DataStore<Preferences>,
    private val alarmRepository: AlarmRepository,
    private val timetableRepository: TimetableRepository,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider
) : ViewModel() {

    private val today = LocalDate.ofInstant(clock.now(), zoneProvider.zone)
    private val retryCounter = MutableStateFlow(0)

    private val _uiState = MutableStateFlow(ConcessionPassBestStartDatesUiState(today = today))
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            retryCounter.collect {
                _uiState.update {
                    it.copy(isLoading = true, recommendations = emptyMap(), loadError = null)
                }
                loadRecommendations()
            }
        }
    }

    private suspend fun loadRecommendations() {
        try {
            val costs = datastore.data.concessionPassCostsFlow().first()
            val toCampusCents = ConcessionPassCalculator.parseCostToCents(costs.toCampusCostInput)
            val fromCampusCents = ConcessionPassCalculator.parseCostToCents(costs.fromCampusCostInput)
            if (toCampusCents == null || fromCampusCents == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadError = "Enter valid average home-to-campus and campus-to-home trip costs in the calculator, then try again."
                    )
                }
                return
            }

            val config = alarmRepository.config.first()
            // One fetch supplies every candidate's inclusive 30-day calculation:
            // the final candidate starts 29 days from today and ends 58 days out.
            val result = timetableRepository.fetchEventsForRange(
                start = today,
                endInclusive = today.plusDays(FETCH_DAYS - 1L)
            )

            result.fold(
                onSuccess = { events ->
                    val recommendations = ConcessionPassCalculator.rankBestStartDates(
                        events = events,
                        toCampusCents = toCampusCents,
                        fromCampusCents = fromCampusCents,
                        firstCandidateDate = today,
                        candidateDays = SEARCH_DAYS,
                        topCount = TOP_RESULTS
                    )
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            recommendations = recommendations,
                            loadError = null
                        )
                    }
                },
                onFailure = {
                    val message = when (config.discoveryMode) {
                        DiscoveryMode.RemoteServer ->
                            "Unable to load the next 60 days of timetable events. Check the configured server connection and retry."
                        DiscoveryMode.DeviceCalendar ->
                            "Unable to load the next 60 days of timetable events. Check the selected calendars and retry."
                    }
                    _uiState.update {
                        it.copy(isLoading = false, recommendations = emptyMap(), loadError = message)
                    }
                }
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    recommendations = emptyMap(),
                    loadError = error.message ?: "Unable to calculate the best pass start dates."
                )
            }
        }
    }

    fun retry() {
        retryCounter.update { it + 1 }
    }

    private companion object {
        const val FETCH_DAYS = 60L
        const val SEARCH_DAYS = 30
        const val TOP_RESULTS = 3
    }
}

package com.cielshieru.wakey.ui.concession

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cielshieru.wakey.di.ConcessionPassDataStoreQualifier
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConcessionPassBestStartDatesUiState(
    val today: LocalDate,
    val startDate: LocalDate = today,
    val isLoading: Boolean = false,
    val recommendations: Map<MonthlyConcessionPass, List<ConcessionPassStartDateRecommendation>> = emptyMap(),
    val loadError: String? = null
)

@HiltViewModel
class ConcessionPassBestStartDatesViewModel @Inject constructor(
    @ConcessionPassDataStoreQualifier private val datastore: DataStore<Preferences>,
    private val alarmRepository: AlarmRepository,
    private val timetableRepository: TimetableRepository,
    private val clock: Clock,
    private val zoneProvider: ZoneProvider
) : ViewModel() {

    private fun currentDate(): LocalDate = LocalDate.ofInstant(clock.now(), zoneProvider.zone)

    private val initialDate = currentDate()
    private val _uiState = MutableStateFlow(
        ConcessionPassBestStartDatesUiState(today = initialDate, startDate = initialDate)
    )
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null

    /** Starts a fresh, screen-scoped calculation whenever the optimizer is entered. */
    fun start() {
        val date = currentDate()
        _uiState.value = ConcessionPassBestStartDatesUiState(today = date, startDate = date)
        loadRecommendations(date)
    }

    /** Clears transient optimizer state and cancels any in-flight request when leaving. */
    fun reset() {
        loadJob?.cancel()
        loadJob = null
        val date = currentDate()
        _uiState.value = ConcessionPassBestStartDatesUiState(
            today = date,
            startDate = date,
            isLoading = false
        )
    }

    fun selectStartDate(date: LocalDate) {
        val today = currentDate()
        if (date.isBefore(today) || date == _uiState.value.startDate) return
        _uiState.update {
            it.copy(
                startDate = date,
                isLoading = true,
                recommendations = emptyMap(),
                loadError = null
            )
        }
        loadRecommendations(date)
    }

    fun retry() {
        loadRecommendations(_uiState.value.startDate)
    }

    private fun loadRecommendations(startDate: LocalDate) {
        loadJob?.cancel()
        _uiState.update {
            it.copy(isLoading = true, recommendations = emptyMap(), loadError = null)
        }
        loadJob = viewModelScope.launch {
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
                    return@launch
                }

                val config = alarmRepository.config.first()
                // One fetch covers the 60-day range beginning on the selected optimizer date.
                val result = timetableRepository.fetchEventsForRange(
                    start = startDate,
                    endInclusive = startDate.plusDays(FETCH_DAYS - 1L)
                )
                currentCoroutineContext().ensureActive()

                result.fold(
                    onSuccess = { events ->
                        val recommendations = ConcessionPassCalculator.rankBestStartDates(
                            events = events,
                            toCampusCents = toCampusCents,
                            fromCampusCents = fromCampusCents,
                            firstCandidateDate = startDate,
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
                                "Unable to load the 60-day timetable range beginning ${startDate}. Check the configured server connection and retry."
                            DiscoveryMode.DeviceCalendar ->
                                "Unable to load the 60-day timetable range beginning ${startDate}. Check the selected calendars and retry."
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
    }

    private companion object {
        const val FETCH_DAYS = 60L
        const val SEARCH_DAYS = 30
        const val TOP_RESULTS = 3
    }
}

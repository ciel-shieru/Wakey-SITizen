package com.cielshieru.wakey.ui.concession

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cielshieru.wakey.domain.ConcessionPassStartDateRecommendation
import com.cielshieru.wakey.domain.MonthlyConcessionPass
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val bestDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
private val savingsGreen = Color(0xFF2E7D32)
private val savingsRed = Color(0xFFC62828)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcessionPassBestStartDatesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConcessionPassBestStartDatesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }

    val selectableDates = remember(uiState.today) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val selectedDate = Instant.ofEpochMilli(utcTimeMillis)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate()
                return !selectedDate.isBefore(uiState.today)
            }

            override fun isSelectableYear(year: Int): Boolean = year >= uiState.today.year
        }
    }

    DisposableEffect(viewModel) {
        viewModel.start()
        onDispose { viewModel.reset() }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Best Pass Start Dates") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Compare pass start dates from ${uiState.startDate.format(bestDateFormatter)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Choose the first day of the optimizer's 60-day timetable fetch. The app compares 30-day pass windows starting on that date or any of the following 29 days, then ranks the top three dates for each pass by estimated savings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Optimizer start date: ${uiState.startDate.format(bestDateFormatter)}")
                    }
                }
            }

            if (uiState.isLoading) {
                item {
                    Text(
                        text = "Loading timetable events and comparing start dates…",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            uiState.loadError?.let { message ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = viewModel::retry) {
                            Text("Retry")
                        }
                    }
                }
            }

            items(MonthlyConcessionPass.values().toList(), key = { it.name }) { pass ->
                val recommendations = uiState.recommendations[pass].orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${pass.label} (${formatBestDateSgd(pass.priceCents)})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (!uiState.isLoading && uiState.loadError == null) {
                        if (recommendations.isEmpty()) {
                            Text(
                                text = "No start dates could be calculated.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            recommendations.forEachIndexed { index, recommendation ->
                                BestStartDateRow(
                                    rank = index + 1,
                                    recommendation = recommendation
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(4.dp)) }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.startDate
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = selectableDates
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            viewModel.selectStartDate(selectedDate)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                showModeToggle = false,
                title = { Text("Select optimizer start date") },
                headline = { Text("Choose the first day of the 60-day fetch") }
            )
        }
    }
}

@Composable
private fun BestStartDateRow(
    rank: Int,
    recommendation: ConcessionPassStartDateRecommendation
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "$rank. ${recommendation.startDate.format(bestDateFormatter)}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Campus visit days: ${recommendation.campusVisitDays}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Estimated fares without pass: ${formatBestDateSgd(recommendation.estimatedTransportCostCents)}",
                style = MaterialTheme.typography.bodyMedium
            )
            val savings = recommendation.netSavingsCents
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        savings > 0L -> "Estimated savings: ${formatBestDateSgd(savings)}"
                        savings < 0L -> "Estimated loss: ${formatBestDateSgd(-savings)}"
                        else -> "Estimated savings: S$0.00 (break-even)"
                    },
                    color = when {
                        savings > 0L -> savingsGreen
                        savings < 0L -> savingsRed
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

private fun formatBestDateSgd(cents: Long): String =
    "S$" + (cents / 100L).toString() + "." + (cents % 100L).toString().padStart(2, '0')

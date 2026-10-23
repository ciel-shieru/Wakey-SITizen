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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cielshieru.wakey.domain.ConcessionPassCalculator
import com.cielshieru.wakey.domain.MonthlyConcessionPass
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
private val recommendationGreen = Color(0xFF2E7D32)
private val recommendationRed = Color(0xFFC62828)

private const val FOOTNOTE =
    """The bus-only and train-only calculations are assuming you only take 1 kind of public transport for the entire journey. If you have a pass for only 1 kind and you attempt to "chain" the same trip on another kind (e.g. with a train pass you tap into the train then continue on a bus), you will be charged the new trip fare when you tap into the other kind. Hence, it is recommended to attempt to condense your trip into 1 kind of public transport if possible, if the hybrid pass doesn't net savings. Substituting a one stop for 15 minutes of walking could potentially net savings."""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConcessionPassCalculatorScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConcessionPassCalculatorViewModel = hiltViewModel()
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
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Concession Pass Calculator") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Text(
                    text = FOOTNOTE,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                        text = "Calculate your monthly concession pass savings",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Choose a start date to forecast your scheduled campus visits over the next 30 days.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start date: ${uiState.startDate.format(dateFormatter)}")
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = uiState.toCampusCostInput,
                        onValueChange = viewModel::updateToCampusCost,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Average trip: home to campus") },
                        prefix = { Text("S$") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = { Text("Enter dollars and cents (up to 2 decimal places).") }
                    )
                    OutlinedTextField(
                        value = uiState.fromCampusCostInput,
                        onValueChange = viewModel::updateFromCampusCost,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Average trip: campus to home") },
                        prefix = { Text("S$") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = { Text("Enter dollars and cents (up to 2 decimal places).") }
                    )
                    Text(
                        text = "Your trip costs should include the costs of any regular stops you make on the way to or from campus, to accurately reflect your day-to-day costs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Estimated costs", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = when {
                            uiState.isLoading -> "Campus visits in selected 30 days: Calculating…"
                            uiState.campusVisitDays != null ->
                                "Campus visits in selected 30 days: ${uiState.campusVisitDays}"
                            else -> "Campus visits in selected 30 days: Unavailable"
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = uiState.estimatedTotalCents?.let { "Estimated transport total: ${formatSgd(it)}" }
                            ?: if (uiState.campusVisitDays == null) {
                                "Estimated transport total: Unavailable until timetable events load."
                            } else {
                                "Estimated transport total: Enter both trip costs."
                            },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    uiState.loadError?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = viewModel::retry) {
                            Text("Retry")
                        }
                    }
                }
            }

            items(MonthlyConcessionPass.values().toList(), key = { it.name }) { pass ->
                PassRecommendationRow(
                    pass = pass,
                    totalCostCents = uiState.estimatedTotalCents,
                    eventsLoaded = uiState.campusVisitDays != null
                )
            }
            item { Spacer(Modifier.height(4.dp)) }
        }
    }

    if (showDatePicker) {
        // Keep draft selection scoped to the dialog so cancelling cannot affect
        // the date preselected when the dialog is opened again.
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
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
                title = { Text("Select concession pass start date") },
                headline = { Text("Choose the first day of your 30-day range") }
            )
        }
    }
}

@Composable
private fun PassRecommendationRow(
    pass: MonthlyConcessionPass,
    totalCostCents: Long?,
    eventsLoaded: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${pass.label} (${formatSgd(pass.priceCents)})",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.width(8.dp))
        when {
            totalCostCents == null -> {
                Text(
                    text = if (eventsLoaded) "Enter costs" else "Unavailable",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ConcessionPassCalculator.shouldBuy(totalCostCents, pass) -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = "Savings", tint = recommendationGreen)
                    Spacer(Modifier.width(4.dp))
                    Text("Buy!", color = recommendationGreen, style = MaterialTheme.typography.labelLarge)
                }
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Close, contentDescription = "No savings", tint = recommendationRed)
                    Spacer(Modifier.width(4.dp))
                    Text("Don't buy!", color = recommendationRed, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private fun formatSgd(cents: Long): String =
    "S$" + (cents / 100L).toString() + "." + (cents % 100L).toString().padStart(2, '0')

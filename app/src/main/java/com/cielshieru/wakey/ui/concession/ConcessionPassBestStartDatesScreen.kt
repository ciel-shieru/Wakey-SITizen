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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.cielshieru.wakey.domain.ConcessionPassStartDateRecommendation
import com.cielshieru.wakey.domain.MonthlyConcessionPass
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
                        text = "Best start dates from ${uiState.today.format(bestDateFormatter)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "The app fetches the next 60 days of timetable events once, then compares each 30-day pass window starting today or on any of the following 29 days. The top three dates for each pass are ranked by estimated savings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

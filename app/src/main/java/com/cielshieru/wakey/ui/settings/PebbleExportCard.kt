package com.cielshieru.wakey.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.cielshieru.wakey.data.store.PebbleExportSettings
import com.cielshieru.wakey.data.store.PebbleExportStore
import com.cielshieru.wakey.di.dataStore
import kotlinx.coroutines.launch

@Composable
fun PebbleExportCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dataStore = remember(context) { context.dataStore }
    val settings by PebbleExportStore.settingsFlow(dataStore)
        .collectAsState(initial = PebbleExportSettings())
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var oneTimeToken by remember { mutableStateOf<String?>(null) }
    var operationInProgress by remember { mutableStateOf(false) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Pebble watchface export", style = MaterialTheme.typography.titleMedium)
            Text(
                "Share only the next alarm and calculated bedtime with a paired watchface. " +
                    "Export is off by default; timetable events and locations are never exported.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = settings.enabled,
                    enabled = !operationInProgress,
                    onCheckedChange = { enable ->
                        operationInProgress = true
                        scope.launch {
                            try {
                                if (enable) {
                                    // Show a fresh token once; store only its SHA-256 digest.
                                    oneTimeToken = PebbleExportStore.createOrRotateToken(dataStore)
                                } else {
                                    PebbleExportStore.disableAndRevoke(dataStore)
                                    oneTimeToken = null
                                }
                            } finally {
                                operationInProgress = false
                            }
                        }
                    },
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(if (settings.enabled) "Export enabled" else "Export disabled")
                    Text(
                        if (settings.enabled) "Only clients with the pairing token can read it."
                        else "Disabling export also revokes the current token.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (settings.enabled) {
                Text(
                    "Pairing token: " + if (settings.tokenConfigured) "configured" else "not configured",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "The token is not stored in plaintext. If you did not copy it when " +
                        "created, rotate it to issue a new token and invalidate the old one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    enabled = !operationInProgress,
                    onClick = {
                        operationInProgress = true
                        scope.launch {
                            try {
                                oneTimeToken = PebbleExportStore.createOrRotateToken(dataStore)
                            } finally {
                                operationInProgress = false
                            }
                        }
                    },
                ) {
                    Text("Rotate pairing token")
                }
            }

            oneTimeToken?.let { token ->
                Text("Copy this token into the JankTime2 companion now. It will not be shown again.")
                SelectionContainer {
                    Text(token, fontFamily = FontFamily.Monospace)
                }
                Button(
                    onClick = { clipboard.setText(AnnotatedString(token)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Copy pairing token")
                }
                TextButton(onClick = { oneTimeToken = null }) {
                    Text("Hide token")
                }
            }
        }
    }
}

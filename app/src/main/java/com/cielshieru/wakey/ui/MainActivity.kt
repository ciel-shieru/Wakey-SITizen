package com.cielshieru.wakey.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cielshieru.wakey.ui.navigation.WakeyNavHost
import com.cielshieru.wakey.ui.settings.SettingsViewModel
import com.cielshieru.wakey.ui.theme.WakeySitizenTheme
import dagger.hilt.android.AndroidEntryPoint
import com.cielshieru.wakey.usecase.SyncOperations
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var syncOperations: SyncOperations

    override fun onStart() {
        super.onStart()
        syncOperations.syncImmediately("sync_foreground")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val viewModel: SettingsViewModel = viewModel()

            LaunchedEffect(viewModel) {
                viewModel.syncEvent.collect { event ->
                    if (event is com.cielshieru.wakey.ui.settings.SyncEvent.SyncTriggered) {
                        val message = "Sync triggered: ${event.source} (${event.mode})"
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }

            WakeySitizenTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WakeyNavHost()
                }
            }
        }
    }
}

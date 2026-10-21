package com.cielshieru.wakey.ui.ringtone

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.media.RingtoneManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cielshieru.wakey.domain.model.RingtonePlaylist

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RingtonePickerScreen(
    onNavigateBack: () -> Unit,
    onShowSnackbar: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RingtonePickerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val event by viewModel.event.collectAsState()
    val listState = rememberLazyListState()

    val context = androidx.compose.ui.platform.LocalContext.current

    // ACTION_OPEN_DOCUMENT grants a persistable URI permission so user-picked
    // tracks remain playable after the app process is recreated or the device reboots.
    val contentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.addRingtone(it.toString())
        }
    }

    // Use Android's native alarm-ringtone picker for ringtones already installed
    // by the OS. Its returned URI goes into the same playlist as custom tracks.
    val systemAlarmRingtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data
                ?.getParcelableExtra<android.net.Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                ?.toString()
                ?.let(viewModel::addRingtone)
        }
    }

    LaunchedEffect(event) {
        event?.let { e ->
            when (e) {
                is RingtonePickerEvent.Saved -> {
                    onShowSnackbar("Ringtone playlist saved")
                    onNavigateBack()
                }
            }
            viewModel.clearEvent()
        }
    }

    var showShuffleDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Ringtone Playlist") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save) {
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = uiState.shuffleEnabled,
                    onCheckedChange = viewModel::toggleShuffle
                )
                Spacer(Modifier.width(8.dp))
                Text("Shuffle ringtones")
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { showShuffleDialog = true },
                    enabled = uiState.shuffleEnabled
                ) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Shuffle info",
                        tint = if (uiState.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (uiState.ringtoneUris.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No ringtones selected")
                }
            } else {
                RingtoneList(
                    ringtones = uiState.ringtoneUris,
                    viewModel = viewModel,
                    listState = listState,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { contentLauncher.launch(arrayOf("audio/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.ringtoneUris.size < RingtonePlaylist.MAX_RINGTONES
                ) {
                    Text("Add Custom Music Track")
                }

                OutlinedButton(
                    onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        }
                        systemAlarmRingtoneLauncher.launch(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.ringtoneUris.size < RingtonePlaylist.MAX_RINGTONES
                ) {
                    Text("Add Device Alarm Ringtone")
                }

                if (uiState.ringtoneUris.size >= RingtonePlaylist.MAX_RINGTONES) {
                    Text(
                        "Maximum ringtones reached (${RingtonePlaylist.MAX_RINGTONES})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Text(
                text = "${uiState.ringtoneUris.size} / ${RingtonePlaylist.MAX_RINGTONES} ringtones selected",
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (showShuffleDialog) {
        AlertDialog(
            onDismissRequest = { showShuffleDialog = false },
            title = { Text("How shuffle works") },
            text = {
                Text("When shuffle is enabled, each alarm in a day's set will play a different ringtone. The same alarm index always plays the same ringtone, so re-syncing does not change which ringtone is assigned to which alarm.")
            },
            confirmButton = {
                TextButton(onClick = { showShuffleDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun RingtoneList(
    ringtones: List<String>,
    viewModel: RingtonePickerViewModel,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOverIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(draggingIndex, dragOverIndex) {
        if (draggingIndex != null && dragOverIndex != null && draggingIndex != dragOverIndex) {
            viewModel.reorderRingtones(draggingIndex!!, dragOverIndex!!)
            draggingIndex = dragOverIndex
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(ringtones, key = { index, uri -> "$index-$uri" }) { index, uri ->
            val displayName = viewModel.resolveRingtoneName(uri)
            AnimatedContent(
                targetState = uri to index,
                label = "RingtoneItem"
            ) { (currentUri, currentIndex) ->
                RingtoneListItem(
                    index = currentIndex + 1,
                    displayName = displayName,
                    uri = currentUri,
                    isDragging = draggingIndex == currentIndex,
                    isDragOver = dragOverIndex == currentIndex,
                    onRemove = { viewModel.removeRingtone(currentUri) },
                    onDragStart = { draggingIndex = currentIndex },
                    onDragOver = { dragOverIndex = currentIndex }
                )
            }
        }
    }
}

@Composable
private fun RingtoneListItem(
    index: Int,
    displayName: String,
    uri: String,
    isDragging: Boolean,
    isDragOver: Boolean,
    onRemove: () -> Unit,
    onDragStart: () -> Unit,
    onDragOver: () -> Unit
) {
    var offsetXPx by remember { mutableIntStateOf(0) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isDragOver) {
                    Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                } else {
                    Modifier
                }
            )
            .offset { IntOffset(offsetXPx, 0) }
            .composed {
                Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDragEnd = {
                            offsetXPx = 0
                        },
                        onDragCancel = {
                            offsetXPx = 0
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        offsetXPx += dragAmount.x.toInt()
                    }
                }
            }
            .pointerInput(isDragOver) {
                if (isDragOver) {
                    detectTapGestures(onTap = { onDragOver() })
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DragIndicator,
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.width(12.dp))

            Text(
                text = "$index. $displayName",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )

            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove ringtone",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

package com.cielshieru.wakey.ui.ringtone

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    val previewPlayer = remember { mutableStateOf<MediaPlayer?>(null) }

    fun stopPreview() {
        val current = previewPlayer.value
        previewPlayer.value = null
        if (current != null) {
            runCatching { current.setOnPreparedListener(null) }
            runCatching { current.setOnCompletionListener(null) }
            runCatching { current.setOnErrorListener(null) }
            runCatching { current.stop() }
            runCatching { current.release() }
        }
    }

    DisposableEffect(context) {
        onDispose { stopPreview() }
    }

    val previewRingtone: (String) -> Unit = { ringtoneUri ->
        // Match the working alarm playback path: system-bundled ringtone URIs
        // are opened directly by MediaPlayer with USAGE_ALARM audio attributes.
        stopPreview()
        val player = MediaPlayer()
        previewPlayer.value = player

        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            player.isLooping = false
            player.setOnPreparedListener { prepared ->
                if (previewPlayer.value === prepared) {
                    prepared.start()
                } else {
                    runCatching { prepared.release() }
                }
            }
            player.setOnCompletionListener { completed ->
                if (previewPlayer.value === completed) {
                    previewPlayer.value = null
                    completed.release()
                }
            }
            player.setOnErrorListener { failed, _, _ ->
                if (previewPlayer.value === failed) {
                    previewPlayer.value = null
                    runCatching { failed.release() }
                    onShowSnackbar("Unable to preview ringtone")
                }
                true
            }
            player.setDataSource(context, Uri.parse(ringtoneUri))
            player.prepareAsync()
        } catch (_: Exception) {
            if (previewPlayer.value === player) previewPlayer.value = null
            runCatching { player.release() }
            onShowSnackbar("Unable to preview ringtone")
        }
    }

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
                    onPreviewRingtone = previewRingtone,
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
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Tap to preview ringtone. Drag up/down to reorder ringtones in playlist.",
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showShuffleDialog) {
        AlertDialog(
            onDismissRequest = { showShuffleDialog = false },
            title = { Text("How shuffle works") },
            text = {
                Text("Shuffle plays each ringtone once per complete playlist cycle before starting a new shuffled cycle. The order is deterministic for each alarm index, so re-syncing does not change the ringtone assigned to an alarm.")
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
    onPreviewRingtone: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOverIndex by remember { mutableStateOf<Int?>(null) }
    var draggedOffsetY by remember { mutableFloatStateOf(0f) }
    var dragPointerY by remember { mutableFloatStateOf(0f) }
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }
    var autoScrollDirection by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val edgeThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }

    fun stopAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = null
        autoScrollDirection = 0
    }

    fun updateDropTarget() {
        listState.layoutInfo.visibleItemsInfo
            .firstOrNull { item ->
                dragPointerY >= item.offset && dragPointerY < item.offset + item.size
            }
            ?.let { dragOverIndex = it.index }
    }

    fun updateAutoScroll(lastDeltaY: Float) {
        val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
        val direction = when {
            dragPointerY < edgeThresholdPx && lastDeltaY < 0f -> -1
            dragPointerY > viewportHeight - edgeThresholdPx && lastDeltaY > 0f -> 1
            else -> 0
        }
        if (direction == 0) {
            stopAutoScroll()
            return
        }
        if (autoScrollDirection == direction && autoScrollJob?.isActive == true) return

        stopAutoScroll()
        autoScrollDirection = direction
        autoScrollJob = scope.launch {
            while (isActive && draggingIndex != null && autoScrollDirection == direction) {
                listState.scrollBy(20f * direction)
                updateDropTarget()
                delay(16L)
            }
        }
    }

    fun resetDrag() {
        stopAutoScroll()
        draggingIndex = null
        dragOverIndex = null
        draggedOffsetY = 0f
    }

    fun finishDrag() {
        val fromIndex = draggingIndex
        val toIndex = dragOverIndex
        stopAutoScroll()
        if (fromIndex != null && toIndex != null && fromIndex != toIndex) {
            viewModel.reorderRingtones(fromIndex, toIndex)
        }
        resetDrag()
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(ringtones, key = { _, uri -> uri }) { index, uri ->
            val displayName = viewModel.resolveRingtoneName(uri)
            RingtoneListItem(
                index = index + 1,
                displayName = displayName,
                isDragging = draggingIndex == index,
                isDragOver = dragOverIndex == index,
                draggedOffsetY = draggedOffsetY,
                onPreview = { onPreviewRingtone(uri) },
                onRemove = { viewModel.removeRingtone(uri) },
                onDragStart = { startOffset ->
                    draggingIndex = index
                    dragOverIndex = index
                    draggedOffsetY = 0f
                    val itemOffset = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == index }
                        ?.offset ?: 0
                    dragPointerY = itemOffset + startOffset.y
                },
                onDrag = { deltaY ->
                    draggedOffsetY += deltaY
                    dragPointerY += deltaY
                    updateDropTarget()
                    updateAutoScroll(deltaY)
                },
                onDragEnd = ::finishDrag,
                onDragCancel = ::resetDrag
            )
        }
    }
}

@Composable
private fun RingtoneListItem(
    index: Int,
    displayName: String,
    isDragging: Boolean,
    isDragOver: Boolean,
    draggedOffsetY: Float,
    onPreview: () -> Unit,
    onRemove: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .offset {
                IntOffset(
                    x = 0,
                    y = if (isDragging) draggedOffsetY.roundToInt() else 0
                )
            }
            .zIndex(if (isDragging) 1f else 0f)
            .pointerInput(index, displayName) {
                detectDragGestures(
                    onDragStart = onDragStart,
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.y)
                }
            }
            .clickable(onClick = onPreview),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDragging -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                isDragOver -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
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
                contentDescription = "Drag up or down to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.width(12.dp))

            Text(
                text = "$index. $displayName",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
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


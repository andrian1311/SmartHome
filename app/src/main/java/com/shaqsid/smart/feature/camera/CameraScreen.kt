package com.shaqsid.smart.feature.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.shaqsid.smart.domain.model.PtzDirection
import com.shaqsid.smart.feature.devicedetail.DeviceDetailViewModel
import com.thingclips.smart.camera.middleware.widget.AbsVideoViewCallback
import com.thingclips.smart.camera.middleware.widget.ThingCameraView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    viewModel: DeviceDetailViewModel,
    onNavigateBack: () -> Unit
) {
    val device by viewModel.device.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    var cameraState by remember { mutableStateOf(CameraState.Idle) }
    var muted by remember { mutableStateOf(true) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    val controller = remember(viewModel.deviceId) {
        CameraController(viewModel.deviceId) { cameraState = it }
    }
    // Keep a reference to the SDK video view so we can forward Compose lifecycle to it.
    var cameraView by remember { mutableStateOf<ThingCameraView?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    // Drive the camera P2P lifecycle from the composable's lifecycle.
    DisposableEffect(lifecycleOwner, controller) {
        controller.create()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    cameraView?.onResume()
                    cameraView?.createdView()?.let { controller.attachView(it) }
                    controller.start()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    cameraView?.onPause()
                    controller.stop()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.stop()
            controller.destroy()
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Device") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.rename(newName); showRenameDialog = false },
                    enabled = newName.isNotBlank()
                ) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Remove Device") },
            text = { Text("Remove this camera from your home? This unpairs the device.") },
            confirmButton = {
                Button(
                    onClick = { showDeleteDialog = false; viewModel.remove(onRemoved = onNavigateBack) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(device?.name ?: "Camera") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { newName = device?.name ?: ""; showRenameDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        ThingCameraView(ctx).apply {
                            setCameraViewCallback(object : AbsVideoViewCallback() {
                                override fun onCreated(view: Any) {
                                    controller.attachView(view)
                                }
                            })
                            createVideoView(viewModel.deviceId)
                            cameraView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                when (cameraState) {
                    CameraState.Connecting, CameraState.Idle ->
                        CircularProgressIndicator(color = Color.White)
                    CameraState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Couldn't connect to the camera.", color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { controller.start() }) { Text("Retry") }
                    }
                    CameraState.Playing -> {}
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusText = when (cameraState) {
                    CameraState.Playing -> "Live"
                    CameraState.Connecting -> "Connecting…"
                    CameraState.Error -> "Disconnected"
                    CameraState.Idle -> "—"
                }
                Text("Status: $statusText", modifier = Modifier.weight(1f))
                FilledTonalButton(
                    onClick = { muted = controller.toggleMute() },
                    enabled = cameraState == CameraState.Playing
                ) {
                    Text(if (muted) "Unmute" else "Mute")
                }
            }

            // Pan/tilt controls — only for cameras whose schema exposes ptz_control.
            if (device?.ptz != null) {
                Spacer(Modifier.height(24.dp))
                PtzControls(
                    enabled = device?.isOnline == true,
                    onMove = { viewModel.ptzMove(it) },
                    onStop = { viewModel.ptzStop() }
                )
            }

            if (device?.isOnline == false) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Camera is offline.",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/** A directional D-pad that pans/tilts the camera while a button is held. */
@Composable
private fun PtzControls(
    enabled: Boolean,
    onMove: (PtzDirection) -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Move camera", style = MaterialTheme.typography.titleSmall)
        PtzButton(Icons.Filled.KeyboardArrowUp, "Up", PtzDirection.UP, enabled, onMove, onStop)
        Row(horizontalArrangement = Arrangement.spacedBy(56.dp)) {
            PtzButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Left", PtzDirection.LEFT, enabled, onMove, onStop)
            PtzButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Right", PtzDirection.RIGHT, enabled, onMove, onStop)
        }
        PtzButton(Icons.Filled.KeyboardArrowDown, "Down", PtzDirection.DOWN, enabled, onMove, onStop)
    }
}

/**
 * A single press-and-hold direction button: sends the move command on press and the stop
 * command on release. Uses a raw pointer gesture (not onClick) so movement tracks the hold.
 */
@Composable
private fun PtzButton(
    icon: ImageVector,
    label: String,
    direction: PtzDirection,
    enabled: Boolean,
    onMove: (PtzDirection) -> Unit,
    onStop: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(
                if (enabled) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .pointerInput(enabled, direction) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    onMove(direction)
                    tryAwaitRelease()
                    onStop()
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

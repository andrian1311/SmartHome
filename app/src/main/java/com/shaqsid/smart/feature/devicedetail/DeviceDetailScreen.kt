package com.shaqsid.smart.feature.devicedetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shaqsid.smart.domain.model.DeviceControl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    viewModel: DeviceDetailViewModel,
    onNavigateBack: () -> Unit
) {
    val device by viewModel.device.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    val schedules by viewModel.schedules.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(viewModel.deviceId) {
        viewModel.loadSchedules()
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
                    onClick = {
                        viewModel.rename(newName)
                        showRenameDialog = false
                    },
                    enabled = newName.isNotBlank()
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Remove Device") },
            text = { Text("Remove this device from your home? This unpairs the device.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.remove(onRemoved = onNavigateBack)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(device?.name ?: "Device") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        newName = device?.name ?: ""
                        showRenameDialog = true
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Rename")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        }
    ) { padding ->
        val current = device
        if (current == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Device not available")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = current.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (current.isOnline) "Online" else "Offline",
                color = if (current.isOnline) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            if (!current.isOnline) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Controls are disabled while the device is offline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(20.dp))

            if (current.controls.isEmpty()) {
                Text(
                    text = "No controllable functions reported for this device.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                current.controls.forEach { control ->
                    if (control is DeviceControl.Switch) {
                        SwitchControlCard(
                            control = control,
                            enabled = current.isOnline && control.editable,
                            onToggle = { on -> viewModel.toggleSwitch(control, on) },
                            onSetCountdown = { seconds ->
                                control.countdownDpId?.let { viewModel.setCountdown(it, seconds) }
                            },
                            onRename = { name -> viewModel.renameControl(control.dpId, name) }
                        )
                    } else {
                        ControlCard(
                            control = control,
                            enabled = current.isOnline && control.editable,
                            onValueChange = { value -> viewModel.setControl(control.dpId, value) }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            ScheduleSection(
                schedules = schedules,
                switches = current.controls.filterIsInstance<DeviceControl.Switch>(),
                onAdd = { time, loops, dpId, turnOn -> viewModel.addSchedule(time, loops, dpId, turnOn) },
                onUpdate = { id, time, loops, dpId, turnOn -> viewModel.updateSchedule(id, time, loops, dpId, turnOn) },
                onToggle = { id, enabled -> viewModel.setScheduleEnabled(id, enabled) },
                onDelete = { id -> viewModel.deleteSchedule(id) }
            )

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Device ID: ${current.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ControlCard(
    control: DeviceControl,
    enabled: Boolean,
    onValueChange: (Any) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (control) {
                is DeviceControl.Switch -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(control.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = control.on,
                        onCheckedChange = { onValueChange(it) },
                        enabled = enabled
                    )
                }

                is DeviceControl.Numeric -> NumericControl(control, enabled, onValueChange)

                is DeviceControl.Enumeration -> EnumerationControl(control, enabled, onValueChange)

                is DeviceControl.Text -> {
                    Text(control.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = control.current.ifBlank { "—" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NumericControl(
    control: DeviceControl.Numeric,
    enabled: Boolean,
    onValueChange: (Any) -> Unit
) {
    // Local slider position for smooth dragging; published only when the drag finishes.
    var sliderValue by remember(control.current) { mutableFloatStateOf(control.current.toFloat()) }
    val divisor = Math.pow(10.0, control.scale.toDouble())
    val display = sliderValue / divisor
    val steps = if (control.step > 0) ((control.max - control.min) / control.step - 1).coerceAtLeast(0) else 0

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(control.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text(
            text = formatNumber(display) + control.unit,
            style = MaterialTheme.typography.bodyMedium
        )
    }
    Slider(
        value = sliderValue.coerceIn(control.min.toFloat(), control.max.toFloat()),
        onValueChange = { sliderValue = it },
        onValueChangeFinished = { onValueChange(sliderValue.toInt()) },
        valueRange = control.min.toFloat()..control.max.toFloat(),
        steps = steps,
        enabled = enabled
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnumerationControl(
    control: DeviceControl.Enumeration,
    enabled: Boolean,
    onValueChange: (Any) -> Unit
) {
    Text(control.name, style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        control.options.forEach { option ->
            FilterChip(
                selected = control.current == option,
                onClick = { onValueChange(option) },
                label = { Text(option) },
                enabled = enabled
            )
        }
    }
}

private fun formatNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

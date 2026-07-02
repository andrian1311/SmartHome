package com.shaqsid.smart.feature.devicedetail

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shaqsid.smart.domain.model.DeviceControl
import com.shaqsid.smart.domain.model.DeviceSchedule

private val DAY_LABELS = listOf("S", "M", "T", "W", "T", "F", "S")
private val DAY_NAMES = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

/**
 * Lists and manages a device's on/off schedules. [switches] are the device's switch controls a
 * schedule can target.
 */
@Composable
fun ScheduleSection(
    schedules: List<DeviceSchedule>,
    switches: List<DeviceControl.Switch>,
    onAdd: (time: String, loops: String, dpId: String, turnOn: Boolean) -> Unit,
    onUpdate: (id: String, time: String, loops: String, dpId: String, turnOn: Boolean) -> Unit,
    onToggle: (id: String, enabled: Boolean) -> Unit,
    onDelete: (id: String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    // The schedule currently being edited, or null when not editing.
    var editing by remember { mutableStateOf<DeviceSchedule?>(null) }

    if (showAddDialog && switches.isNotEmpty()) {
        ScheduleDialog(
            switches = switches,
            existing = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { time, loops, dpId, turnOn ->
                onAdd(time, loops, dpId, turnOn)
                showAddDialog = false
            }
        )
    }

    editing?.let { schedule ->
        ScheduleDialog(
            switches = switches,
            existing = schedule,
            onDismiss = { editing = null },
            onConfirm = { time, loops, dpId, turnOn ->
                onUpdate(schedule.id, time, loops, dpId, turnOn)
                editing = null
            }
        )
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Schedules", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        TextButton(
            onClick = { showAddDialog = true },
            enabled = switches.isNotEmpty()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Add")
        }
    }

    if (switches.isEmpty()) {
        Text(
            "Scheduling needs a switch on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else if (schedules.isEmpty()) {
        Text(
            "No schedules yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        schedules.forEach { schedule ->
            ScheduleRow(
                schedule = schedule,
                switchName = switches.firstOrNull { it.dpId == schedule.dpId }?.name ?: "Switch ${schedule.dpId}",
                onEdit = { editing = schedule },
                onToggle = { onToggle(schedule.id, it) },
                onDelete = { onDelete(schedule.id) }
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleRow(
    schedule: DeviceSchedule,
    switchName: String,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(schedule.time, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "$switchName → ${if (schedule.turnOn) "On" else "Off"} · ${loopsSummary(schedule.loops)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = schedule.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete schedule", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ScheduleDialog(
    switches: List<DeviceControl.Switch>,
    existing: DeviceSchedule?,
    onDismiss: () -> Unit,
    onConfirm: (time: String, loops: String, dpId: String, turnOn: Boolean) -> Unit
) {
    // Prefill from the schedule being edited, or sensible defaults when adding.
    val initialHour = existing?.time?.substringBefore(":")?.toIntOrNull() ?: 18
    val initialMinute = existing?.time?.substringAfter(":")?.toIntOrNull() ?: 0
    val timeState = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
    val days = remember {
        mutableStateListOf(*(0 until 7).map { existing?.loops?.getOrNull(it) == '1' }.toTypedArray())
    }
    var selectedSwitch by remember {
        mutableStateOf(switches.firstOrNull { it.dpId == existing?.dpId } ?: switches.first())
    }
    var turnOn by remember { mutableStateOf(existing?.turnOn ?: true) }
    var switchMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Schedule" else "Edit Schedule") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TimePicker(state = timeState)
                Spacer(Modifier.height(12.dp))

                Text("Repeat", style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.Start))
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DAY_LABELS.forEachIndexed { i, label ->
                        FilterChip(
                            selected = days[i],
                            onClick = { days[i] = !days[i] },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                // Target switch
                ExposedDropdownMenuBox(
                    expanded = switchMenuOpen,
                    onExpandedChange = { switchMenuOpen = it }
                ) {
                    OutlinedTextField(
                        value = selectedSwitch.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Switch") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(switchMenuOpen) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = switchMenuOpen, onDismissRequest = { switchMenuOpen = false }) {
                        switches.forEach { sw ->
                            DropdownMenuItem(
                                text = { Text(sw.name) },
                                onClick = { selectedSwitch = sw; switchMenuOpen = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Action", modifier = Modifier.weight(1f))
                    Text(if (turnOn) "Turn On" else "Turn Off")
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = turnOn, onCheckedChange = { turnOn = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val time = "%02d:%02d".format(timeState.hour, timeState.minute)
                val loops = days.joinToString("") { if (it) "1" else "0" }
                onConfirm(time, loops, selectedSwitch.dpId, turnOn)
            }) { Text(if (existing == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun loopsSummary(loops: String): String = when {
    loops == DeviceSchedule.LOOPS_ONCE || !loops.contains("1") -> "Once"
    loops == DeviceSchedule.LOOPS_DAILY -> "Every day"
    else -> loops.mapIndexedNotNull { i, c -> if (c == '1') DAY_NAMES.getOrNull(i) else null }.joinToString(", ")
}

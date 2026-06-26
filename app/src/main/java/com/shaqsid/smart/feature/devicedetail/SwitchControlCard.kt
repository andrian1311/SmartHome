package com.shaqsid.smart.feature.devicedetail

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shaqsid.smart.domain.model.DeviceControl
import kotlinx.coroutines.delay

/**
 * A switch row with an optional per-switch countdown timer. When a countdown is running it shows a
 * live "Off in HH:mm:ss" beside the name; the Timer button opens a picker to set/edit/cancel it.
 * Toggling the switch while a countdown is active cancels the countdown (handled by the caller).
 */
@Composable
fun SwitchControlCard(
    control: DeviceControl.Switch,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onSetCountdown: (Int) -> Unit
) {
    var showTimerDialog by remember { mutableStateOf(false) }

    if (showTimerDialog) {
        SetTimerDialog(
            initialSeconds = control.countdownSeconds,
            onDismiss = { showTimerDialog = false },
            onConfirm = {
                onSetCountdown(it)
                showTimerDialog = false
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(control.name, style = MaterialTheme.typography.titleMedium)
                if (control.countdownSeconds > 0) {
                    CountdownText(seconds = control.countdownSeconds, switchOn = control.on)
                }
            }
            if (control.countdownDpId != null) {
                TextButton(onClick = { showTimerDialog = true }, enabled = enabled) {
                    Text("Timer")
                }
            }
            Switch(
                checked = control.on,
                onCheckedChange = { onToggle(it) },
                enabled = enabled
            )
        }
    }
}

/**
 * Shows a locally-ticking countdown that re-syncs whenever the device reports a new value.
 * The countdown flips the switch, so the label reflects the target state: "Off in …" when the
 * switch is currently on, "On in …" when it's off.
 */
@Composable
private fun CountdownText(seconds: Int, switchOn: Boolean) {
    var remaining by remember(seconds) { mutableIntStateOf(seconds) }
    LaunchedEffect(seconds) {
        remaining = seconds
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
    }
    val prefix = if (switchOn) "Off in" else "On in"
    Text(
        text = "$prefix ${formatCountdown(remaining)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetTimerDialog(
    initialSeconds: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialSeconds / 3600,
        initialMinute = (initialSeconds % 3600) / 60,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Timer") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Turn off after (hours : minutes)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                TimePicker(state = state)
                Text(
                    "Set 00:00 to turn the timer off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(state.hour * 3600 + state.minute * 60) }) {
                Text(if (initialSeconds > 0) "Update" else "Set")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** HH:mm:ss when >= 1h, mm:ss when >= 1m, otherwise "<n> seconds". */
private fun formatCountdown(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return when {
        h > 0 -> "%02d:%02d:%02d".format(h, m, sec)
        m > 0 -> "%02d:%02d".format(m, sec)
        else -> "$sec seconds"
    }
}

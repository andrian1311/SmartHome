package com.shaqsid.smart.feature.adddevice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.shaqsid.smart.domain.model.PairingMode
import com.shaqsid.smart.util.WifiUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(
    viewModel: AddDeviceViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // EZ/AP pairing pairs onto the phone's current Wi-Fi. Reading that SSID needs
    // location permission on Android 8.1+, so request it and pre-fill the field.
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.prefillSsid(WifiUtils.getCurrentSsid(context))
        }
    }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.prefillSsid(WifiUtils.getCurrentSsid(context))
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add New Device") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text("Pairing mode", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PairingMode.entries.forEach { mode ->
                    FilterChip(
                        selected = uiState.mode == mode,
                        onClick = { viewModel.selectMode(mode) },
                        label = { Text(mode.label()) },
                        enabled = !uiState.isLoading,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = uiState.mode.instructions(),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(24.dp))

            when (uiState.mode) {
                PairingMode.QR -> {
                    OutlinedTextField(
                        value = uiState.qrContent,
                        onValueChange = { viewModel.updateQrContent(it) },
                        label = { Text("Scanned QR content / device UUID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !uiState.isLoading
                    )
                }
                else -> {
                    OutlinedTextField(
                        value = uiState.ssid,
                        onValueChange = { viewModel.updateSsid(it) },
                        label = { Text("Wi-Fi SSID (2.4 GHz)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !uiState.isLoading
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = { viewModel.updatePassword(it) },
                        label = { Text("Wi-Fi Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !uiState.isLoading
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { viewModel.addDevice(onSuccess = onNavigateBack) },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.canSubmit && !uiState.isLoading
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Start Pairing")
                }
            }
            if (uiState.isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Pairing in progress… keep the app open. This can take up to 100 seconds.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = uiState.error!!, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun PairingMode.label(): String = when (this) {
    PairingMode.EZ -> "EZ"
    PairingMode.AP -> "AP"
    PairingMode.QR -> "QR"
}

private fun PairingMode.instructions(): String = when (this) {
    PairingMode.EZ ->
        "EZ (SmartConfig): make sure the device indicator is blinking rapidly, then connect " +
            "your phone to a 2.4 GHz Wi-Fi and start pairing."
    PairingMode.AP ->
        "AP (Hotspot): set the device to AP mode (indicator blinking slowly) and connect your " +
            "phone to the device's \"SmartLife-xxxx\" hotspot, then enter your home Wi-Fi and start pairing."
    PairingMode.QR ->
        "QR: scan the QR code printed on the device (or shown in its app) and paste its content " +
            "below, then start pairing."
}

package com.geodnet.ntrip.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.geodnet.ntrip.ntrip.NtripConfig
import com.geodnet.ntrip.ntrip.NtripStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NtripScreen(viewModel: NtripViewModel) {
    val config by viewModel.config.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    var host by remember(config.host) { mutableStateOf(config.host) }
    var port by remember(config.port) { mutableStateOf(config.port.toString()) }
    var mountpoint by remember(config.mountpoint) { mutableStateOf(config.mountpoint) }
    var username by remember(config.username) { mutableStateOf(config.username) }
    var password by remember(config.password) { mutableStateOf(config.password) }
    var latitude by remember(config.latitude) { mutableStateOf(config.latitude.toString()) }
    var longitude by remember(config.longitude) { mutableStateOf(config.longitude.toString()) }

    val isConnected = connectionState.status == NtripStatus.CONNECTED ||
        connectionState.status == NtripStatus.CONNECTING

    fun currentConfig(): NtripConfig = config.copy(
        host = host,
        port = port.toIntOrNull() ?: config.port,
        mountpoint = mountpoint,
        username = username,
        password = password,
        latitude = latitude.toDoubleOrNull() ?: config.latitude,
        longitude = longitude.toDoubleOrNull() ?: config.longitude,
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ntrip Client") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Host") },
                enabled = !isConnected,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it },
                label = { Text("Port") },
                enabled = !isConnected,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = mountpoint,
                onValueChange = { mountpoint = it },
                label = { Text("Mountpoint") },
                enabled = !isConnected,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                enabled = !isConnected,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                enabled = !isConnected,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = latitude,
                onValueChange = { latitude = it },
                label = { Text("Latitude") },
                enabled = !isConnected,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = longitude,
                onValueChange = { longitude = it },
                label = { Text("Longitude") },
                enabled = !isConnected,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    if (isConnected) {
                        viewModel.disconnect()
                    } else {
                        val updated = currentConfig()
                        viewModel.updateConfig(updated)
                        viewModel.connect()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isConnected) "Disconnect" else "Connect")
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Status: ${connectionState.status}", style = MaterialTheme.typography.titleMedium)
                    Text("Bytes received: ${connectionState.bytesReceived}")
                    connectionState.errorMessage?.let { Text("Error: $it") }
                }
            }
        }
    }
}

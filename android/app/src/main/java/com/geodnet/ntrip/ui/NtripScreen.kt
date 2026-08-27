package com.geodnet.ntrip.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.geodnet.ntrip.ble.BleDeviceInfo
import com.geodnet.ntrip.ble.BleStatus
import com.geodnet.ntrip.data.NearbyStation
import com.geodnet.ntrip.net.TcpServerState
import com.geodnet.ntrip.ntrip.NtripConfig
import com.geodnet.ntrip.ntrip.NtripProfile
import com.geodnet.ntrip.ntrip.NtripSourcetable
import com.geodnet.ntrip.ntrip.NtripStreamRecord
import com.geodnet.ntrip.ntrip.NtripState
import com.geodnet.ntrip.ntrip.NtripStatus
import com.geodnet.ntrip.rtcm.RtcmMessage
import com.geodnet.ntrip.rtcm.RtcmMessageDescriptions
import com.geodnet.ntrip.ui.theme.SurveyColors
import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt().coerceIn(1, 4)
    val pre = "KMGT"[exp - 1]
    return String.format(Locale.US, "%.1f %cB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NtripScreen(viewModel: NtripViewModel) {
    val config by viewModel.config.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val selectedProfileId by viewModel.selectedProfileId.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val rtcmStats by viewModel.rtcmStats.collectAsState()
    val rtcmLog by viewModel.rtcmLog.collectAsState()
    val bleDevices by viewModel.bleDevices.collectAsState()
    val bleIsScanning by viewModel.bleIsScanning.collectAsState()
    val bleState by viewModel.bleConnectionState.collectAsState()
    val lastBleName by viewModel.lastBleName.collectAsState()
    val lastBleAddress by viewModel.lastBleAddress.collectAsState()
    val bestFix by viewModel.bestFix.collectAsState()
    val nearbyStations by viewModel.nearbyStations.collectAsState()
    val isCoverageLoading by viewModel.isCoverageLoading.collectAsState()
    val mockLocationState by viewModel.mockLocationState.collectAsState()
    val nmeaServerState by viewModel.nmeaServerState.collectAsState()
    val rtcmServerState by viewModel.rtcmServerState.collectAsState()
    val epochStats by viewModel.epochStats.collectAsState()
    val filterEphemerisForBle by viewModel.filterEphemerisForBle.collectAsState()
    val rawLoggerState by viewModel.rawLoggerState.collectAsState()
    val gnssRawLoggerState by viewModel.gnssRawLoggerState.collectAsState()
    val soundAlertsEnabled by viewModel.soundAlertsEnabled.collectAsState()
    val sourcetable by viewModel.sourcetable.collectAsState()
    val isSourcetableLoading by viewModel.isSourcetableLoading.collectAsState()
    val sourcetableError by viewModel.sourcetableError.collectAsState()
    val baseStation by viewModel.baseStation.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showSourcetableDialog by remember { mutableStateOf(false) }

    var profileName by remember(selectedProfileId) {
        mutableStateOf(profiles.find { it.id == selectedProfileId }?.name ?: "")
    }

    var host by remember(config.host) { mutableStateOf(config.host) }
    var port by remember(config.port) { mutableStateOf(config.port.toString()) }
    var mountpoint by remember(config.mountpoint) { mutableStateOf(config.mountpoint) }
    var username by remember(config.username) { mutableStateOf(config.username) }
    var password by remember(config.password) { mutableStateOf(config.password) }
    var latitude by remember(config.latitude) { mutableStateOf(config.latitude.toString()) }
    var longitude by remember(config.longitude) { mutableStateOf(config.longitude.toString()) }

    LaunchedEffect(config) {
        host = config.host
        port = config.port.toString()
        mountpoint = config.mountpoint
        username = config.username
        password = config.password
        latitude = config.latitude.toString()
        longitude = config.longitude.toString()
    }

    val isConnected = connectionState.status == NtripStatus.CONNECTED ||
        connectionState.status == NtripStatus.CONNECTING

    fun currentConfig(): NtripConfig = config.copy(
        host = host.trim(),
        port = port.toIntOrNull() ?: config.port,
        mountpoint = mountpoint.trim(),
        username = username.trim(),
        password = password,
        latitude = latitude.toDoubleOrNull() ?: config.latitude,
        longitude = longitude.toDoubleOrNull() ?: config.longitude,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("GEODNET NTRIP", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. Hero Connection Status Card
            HeroConnectionCard(
                config = config,
                connectionState = connectionState,
                isConnected = isConnected,
                bestFix = bestFix,
                onOpenSettings = { showSettingsDialog = true },
                onConnect = {
                    if (config.username.isBlank() || config.password.isBlank()) {
                        showSettingsDialog = true
                    } else {
                        viewModel.connect()
                    }
                },
                onDisconnect = { viewModel.disconnect() },
            )

            // 2. BLE RTK Receiver Card with Survey Telemetry
            BleReceiverCard(
                bleState = bleState,
                bleDevices = bleDevices,
                bleIsScanning = bleIsScanning,
                lastBleName = lastBleName,
                lastBleAddress = lastBleAddress,
                filterEphemeris = filterEphemerisForBle,
                epochStats = epochStats,
                onStartScan = { viewModel.startBleScan() },
                onStopScan = { viewModel.stopBleScan() },
                onConnectDevice = { viewModel.connectBleDevice(it) },
                onDisconnectDevice = { viewModel.disconnectBleDevice() },
                onFilterEphemerisChange = { viewModel.setFilterEphemerisForBle(it) }
            )

            // 3. GEODNET Network Base Station Discovery Card
            GeodnetCoverageCard(
                nearbyStations = nearbyStations,
                isLoading = isCoverageLoading,
                baseStation = baseStation,
                epochStats = epochStats,
                diffStationId = bestFix?.diffStationId,
                onRefresh = { viewModel.refreshCoverageStations() }
            )

            // 4. RTCM Inspector & Epoch Latency Card
            RtcmInspectorCard(
                rtcmStats = rtcmStats,
                epochStats = epochStats
            )

            // 5. Location Outputs & Local GIS Servers Card
            LocationAndServerCard(
                bestFix = bestFix,
                mockLocationState = mockLocationState,
                nmeaServerState = nmeaServerState,
                rtcmServerState = rtcmServerState,
                soundAlertsEnabled = soundAlertsEnabled,
                onToggleMockLocation = { viewModel.setMockLocationEnabled(it) },
                onToggleNmeaServer = { viewModel.setNmeaServerEnabled(it) },
                onToggleRtcmServer = { viewModel.setRtcmServerEnabled(it) },
                onToggleSoundAlerts = { viewModel.setSoundAlertsEnabled(it) },
            )

            // 6. Dual Data Logger Card
            DataLoggerCard(
                rawLoggerState = rawLoggerState,
                gnssRawLoggerState = gnssRawLoggerState,
                onToggleRawLogger = { viewModel.setRawLoggingEnabled(it) },
                onToggleGnssRawLogger = { viewModel.setGnssRawLoggingEnabled(it) },
            )

            // 6. Live RTCM Decode Terminal Log
            RtcmLiveLogCard(
                messages = rtcmLog,
                onClearLog = { viewModel.clearTrajectory() /* placeholder or clear */ }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showSettingsDialog) {
        NtripSettingsDialog(
            profiles = profiles,
            selectedProfileId = selectedProfileId,
            profileName = profileName,
            onProfileNameChange = { profileName = it },
            host = host,
            onHostChange = { host = it },
            port = port,
            onPortChange = { port = it },
            mountpoint = mountpoint,
            onMountpointChange = { mountpoint = it },
            username = username,
            onUsernameChange = { username = it },
            password = password,
            onPasswordChange = { password = it },
            latitude = latitude,
            onLatitudeChange = { latitude = it },
            longitude = longitude,
            onLongitudeChange = { longitude = it },
            onUseCurrentLocation = {
                bestFix?.let { fix ->
                    latitude = String.format(Locale.US, "%.7f", fix.latitude)
                    longitude = String.format(Locale.US, "%.7f", fix.longitude)
                }
            },
            onPullSourcetable = {
                viewModel.fetchSourcetable(
                    host = host.trim(),
                    port = port.toIntOrNull() ?: 2101,
                    user = username.trim(),
                    pass = password
                )
                showSourcetableDialog = true
            },
            onLoadProfile = {
                viewModel.loadProfile(it)
                host = it.config.host
                port = it.config.port.toString()
                mountpoint = it.config.mountpoint
                username = it.config.username
                password = it.config.password
                latitude = it.config.latitude.toString()
                longitude = it.config.longitude.toString()
                profileName = it.name
            },
            onDeleteProfile = { viewModel.deleteProfile(it) },
            onSaveAsNew = { viewModel.saveAsNewProfile(profileName, currentConfig()) },
            onUpdateSelected = { viewModel.updateSelectedProfile(profileName, currentConfig()) },
            onConnect = {
                viewModel.updateConfig(currentConfig())
                viewModel.connect()
                showSettingsDialog = false
            },
            onDismiss = { showSettingsDialog = false },
        )
    }

    if (showSourcetableDialog) {
        SourcetableDialog(
            sourcetable = sourcetable,
            isLoading = isSourcetableLoading,
            errorMessage = sourcetableError,
            currentMountpoint = mountpoint,
            onRefresh = {
                viewModel.fetchSourcetable(
                    host = host.trim(),
                    port = port.toIntOrNull() ?: 2101,
                    user = username.trim(),
                    pass = password
                )
            },
            onSelectStream = { stream ->
                mountpoint = stream.mountpoint
                if (stream.latitude != null && stream.longitude != null) {
                    latitude = String.format(Locale.US, "%.7f", stream.latitude)
                    longitude = String.format(Locale.US, "%.7f", stream.longitude)
                }
                showSourcetableDialog = false
            },
            onDismiss = { showSourcetableDialog = false }
        )
    }
}

// ---------------------------------------------------------------------------
// UI Cards & Sub-components
// ---------------------------------------------------------------------------

@Composable
private fun HeroConnectionCard(
    config: NtripConfig,
    connectionState: NtripState,
    isConnected: Boolean,
    bestFix: com.geodnet.ntrip.location.PositionFix? = null,
    onOpenSettings: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val statusColor = when (connectionState.status) {
        NtripStatus.CONNECTED -> SurveyColors.Connected
        NtripStatus.CONNECTING -> SurveyColors.Connecting
        NtripStatus.ERROR -> SurveyColors.Error
        NtripStatus.DISCONNECTED -> SurveyColors.Disconnected
    }

    val datum = remember(config.mountpoint, config.host, bestFix?.latitude, bestFix?.longitude) {
        com.geodnet.ntrip.data.GeodnetDatumResolver.resolve(
            mountpoint = config.mountpoint,
            host = config.host,
            lat = bestFix?.latitude,
            lon = bestFix?.longitude
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                            .then(
                                if (connectionState.status == NtripStatus.CONNECTING ||
                                    connectionState.status == NtripStatus.CONNECTED
                                ) Modifier.alpha(pulseAlpha) else Modifier
                            )
                    )
                    Text(
                        text = connectionState.status.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }

                if (connectionState.status == NtripStatus.CONNECTED) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SurveyColors.Connected.copy(alpha = 0.12f),
                    ) {
                        Text(
                            text = formatBytes(connectionState.bytesReceived),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = SurveyColors.Connected
                        )
                    }
                }
            }

            // Stream endpoint box with Coordinate System
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CASTER MOUNTPOINT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = datum.name,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Text(
                        text = "${config.host}:${config.port}/${config.mountpoint.ifBlank { "—" }}",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Datum: ${datum.name} • Epoch: ${datum.epoch} (${datum.region})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            connectionState.errorMessage?.let { error ->
                Text(
                    text = "⚠️ $error",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }

            // Quick Actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isConnected) {
                    Button(
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Disconnect", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Button(
                        onClick = onConnect,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Connect", fontWeight = FontWeight.SemiBold)
                    }
                }
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Settings & Profiles", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BleReceiverCard(
    bleState: com.geodnet.ntrip.ble.BleConnectionState,
    bleDevices: List<BleDeviceInfo>,
    bleIsScanning: Boolean,
    lastBleName: String?,
    lastBleAddress: String?,
    filterEphemeris: Boolean,
    epochStats: com.geodnet.ntrip.rtcm.EpochLatencyStats,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnectDevice: (String) -> Unit,
    onDisconnectDevice: () -> Unit,
    onFilterEphemerisChange: (Boolean) -> Unit
) {
    val bleConnected = bleState.status == BleStatus.CONNECTED

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "BLE RTK Receiver",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                StatusChip(
                    text = if (bleConnected) "CONNECTED" else if (bleIsScanning) "SCANNING" else "DISCONNECTED",
                    color = if (bleConnected) SurveyColors.Connected else if (bleIsScanning) SurveyColors.Connecting else SurveyColors.Disconnected
                )
            }

            if (bleConnected) {
                Text(
                    text = "${bleState.deviceName ?: "Unknown"} (${bleState.deviceAddress})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )

                // Throughput counters & MTU size & Message counts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "↓ RX: ${formatBytes(bleState.bytesFromReceiver.toLong())} (${bleState.messagesReceived} msgs)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Text(
                            "MTU: ${bleState.mtu} (${(bleState.mtu - 3).coerceAtLeast(20)}B)",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 10.sp
                        )
                    }
                    Text(
                        "↑ TX: ${formatBytes(bleState.bytesToReceiver.toLong())} (${bleState.messagesSent} pkts)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // NMEA Sentences & RTCM Messages Decoded Breakdown
                if (bleState.nmeaCounts.isNotEmpty() || bleState.rtcmCounts.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        bleState.nmeaCounts.toSortedMap().forEach { (type, count) ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = type,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = "×$count",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }

                        bleState.rtcmCounts.toSortedMap().forEach { (type, count) ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = SurveyColors.Connected.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SurveyColors.Connected.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = type,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = SurveyColors.Connected,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = "×$count",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // GNSS Fix telemetry banner
                bleState.latestFix?.let { fix ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FixQualityBadge(quality = fix.fixQuality)
                        Text(
                            text = "Sats: ${fix.numSatellites}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Differential Base ID, Correction Age (GGA field 13), and Latency (Rover time tag - Base time tag)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val baseStationId = if (fix.diffStationId != 0) fix.diffStationId else epochStats.baseStationId
                        TelemetryTile(
                            title = "BASE ID",
                            value = baseStationId?.let { "#$it" } ?: "—",
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryTile(
                            title = "AGE (GGA)",
                            value = if (fix.diffAgeSec > 0.0) "%.1fs".format(fix.diffAgeSec) else "—",
                            modifier = Modifier.weight(1f)
                        )
                        val latencySec = com.geodnet.ntrip.rtcm.TimeTagMath.calculateLatencySec(
                            roverGgaUtcTime = fix.utcTime,
                            baseTimeTagUtcSec = epochStats.lastBaseTimeTagUtcSec
                        ) ?: (epochStats.lastMessageAgeMs.takeIf { it > 0 }?.let { it / 1000.0 })
                        TelemetryTile(
                            title = "LATENCY",
                            value = latencySec?.let { "%.1fs".format(it) } ?: "—",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Telemetry Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryTile(
                            title = "ALTITUDE",
                            value = "%.2fm".format(fix.altitudeM),
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryTile(
                            title = "ELLIPSOID",
                            value = "%.2fm".format(fix.altitudeM + fix.geoidSeparationM),
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryTile(
                            title = "HDOP",
                            value = "%.2f".format(fix.hdop),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    bleState.latestGsa?.let { gsa ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TelemetryTile(
                                title = "PDOP",
                                value = "%.2f".format(gsa.pdop),
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryTile(
                                title = "VDOP",
                                value = "%.2f".format(gsa.vdop),
                                modifier = Modifier.weight(1f)
                            )
                            TelemetryTile(
                                title = "MODE",
                                value = "${gsa.fixType}D",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    bleState.latestGst?.let { gst ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Text(
                                    "σ_Lat: ±%.3fm".format(gst.latStdDevM),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    "σ_Lon: ±%.3fm".format(gst.lonStdDevM),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    "σ_Alt: ±%.3fm".format(gst.altStdDevM),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                ToggleRow(
                    label = "Filter ephemeris before BLE forward",
                    sublabel = "Drops 1019..1046 to save BLE bandwidth",
                    checked = filterEphemeris,
                    onCheckedChange = onFilterEphemerisChange,
                )

                OutlinedButton(
                    onClick = onDisconnectDevice,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Disconnect Receiver")
                }
            } else {
                bleState.errorMessage?.let {
                    Text("Error: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                if (!lastBleAddress.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConnectDevice(lastBleAddress) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "LAST CONNECTED RECEIVER",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = lastBleName?.ifBlank { null } ?: "GNSS Receiver",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = lastBleAddress,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { onConnectDevice(lastBleAddress) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Connect", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Button(
                    onClick = { if (bleIsScanning) onStopScan() else onStartScan() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (bleIsScanning) "Stop Scanning" else "Scan for BLE Receivers")
                }

                if (bleDevices.isNotEmpty()) {
                    Text(
                        "Discovered Receivers (${bleDevices.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                        items(bleDevices) { device ->
                            BleDeviceCard(device = device, onConnect = { onConnectDevice(device.address) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BleDeviceCard(device: BleDeviceInfo, onConnect: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(onClick = onConnect)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name ?: "Unknown GNSS Receiver",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${device.address}  •  ${device.rssi} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onConnect) {
                Text("Connect")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RtcmInspectorCard(
    rtcmStats: com.geodnet.ntrip.rtcm.RtcmStats,
    epochStats: com.geodnet.ntrip.rtcm.EpochLatencyStats
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("RTCM3 Inspector", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val crcRate = if (rtcmStats.msgsDecoded > 0L) {
                    "%.1f%% CRC Fail".format((rtcmStats.msgsCrcFail.toFloat() / (rtcmStats.msgsDecoded + rtcmStats.msgsCrcFail)) * 100f)
                } else "0% CRC Fail"
                StatusChip(
                    text = if (rtcmStats.msgsCrcFail == 0L) "CRC OK" else crcRate,
                    color = if (rtcmStats.msgsCrcFail == 0L) SurveyColors.Connected else SurveyColors.Error
                )
            }

            // Summary metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TelemetryTile(
                    title = "BYTES",
                    value = formatBytes(rtcmStats.bytesReceived),
                    modifier = Modifier.weight(1f)
                )
                TelemetryTile(
                    title = "FRAMES",
                    value = "${rtcmStats.msgsDecoded}",
                    modifier = Modifier.weight(1f)
                )
                TelemetryTile(
                    title = "EPOCHS",
                    value = "${epochStats.epochsCompleted}",
                    modifier = Modifier.weight(1f)
                )
            }

            // Epoch latency block
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("EPOCH LATENCY & TIME TAG", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        epochStats.baseStationId?.let {
                            Text("Base #$it", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Base Time Tag: ${epochStats.lastBaseTimeTagUtcSec?.let { "%.2fs UTC".format(it) } ?: "—"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Span (Δt): ${epochStats.lastEpochSpanMs?.let { "%.2fms".format(it) } ?: "—"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "First Latency: ${epochStats.firstMessageLatencyMs?.let { "${it}ms" } ?: "—"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Arrival Age: ${epochStats.lastMessageAgeMs}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Categorized RTCM Message Badges
            if (rtcmStats.msgCounts.isNotEmpty()) {
                Text("Decoded Message Streams:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rtcmStats.msgCounts.toSortedMap(compareBy { it.substringBefore(".").toIntOrNull() ?: 0 })
                        .forEach { (typeKey, count) ->
                            val color = getRtcmConstellationColor(typeKey)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = color.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = typeKey,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = color
                                    )
                                    Text(
                                        text = "×$count",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = color
                                    )
                                }
                            }
                        }
                }
            }
        }
    }
}

private fun getRtcmConstellationColor(typeKey: String): Color {
    val type = typeKey.substringBefore(".").toIntOrNull() ?: 0
    return when {
        type in 1001..1004 || type in 1071..1077 || type == 1019 -> SurveyColors.Gps
        type in 1009..1012 || type in 1081..1087 || type == 1020 || type == 1230 -> SurveyColors.Glonass
        type in 1091..1097 || type in 1045..1046 -> SurveyColors.Galileo
        type in 1121..1127 || type == 1042 -> SurveyColors.Beidou
        type in 1111..1117 || type == 1044 -> SurveyColors.Qzss
        type in 1005..1006 || type == 1033 -> SurveyColors.Station
        type in 1057..1068 || type in 1240..1270 -> SurveyColors.Ssr
        else -> SurveyColors.Single
    }
}

@Composable
private fun LocationAndServerCard(
    bestFix: com.geodnet.ntrip.location.PositionFix?,
    mockLocationState: com.geodnet.ntrip.location.MockLocationState,
    nmeaServerState: TcpServerState,
    rtcmServerState: TcpServerState,
    soundAlertsEnabled: Boolean,
    onToggleMockLocation: (Boolean) -> Unit,
    onToggleNmeaServer: (Boolean) -> Unit,
    onToggleRtcmServer: (Boolean) -> Unit,
    onToggleSoundAlerts: (Boolean) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("GIS Outputs & Sound Alerts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            bestFix?.let { fix ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("ACTIVE BEST FIX SOURCE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            StatusChip(
                                text = fix.source.name,
                                color = if (fix.source == com.geodnet.ntrip.location.FixSource.BLE) SurveyColors.Connected else SurveyColors.Dgps
                            )
                        }
                        Text(
                            "%.7f, %.7f  (alt: %.2fm)".format(fix.latitude, fix.longitude, fix.altitudeM),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            ToggleRow(
                label = "RTK Fix Audio Beep Alerts",
                sublabel = if (soundAlertsEnabled) "Audio beeps for first fix, refix, lost fix, entering/exiting RTK" else "Muted",
                checked = soundAlertsEnabled,
                onCheckedChange = onToggleSoundAlerts,
            )

            HorizontalDivider()

            ToggleRow(
                label = "Android Mock Location Provider",
                sublabel = if (mockLocationState.enabled) "Injecting fixes (${mockLocationState.updateCount} updates)" else "Off",
                checked = mockLocationState.enabled,
                onCheckedChange = onToggleMockLocation,
            )
            mockLocationState.errorMessage?.let {
                Text("⚠️ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            HorizontalDivider()

            ToggleRow(
                label = "NMEA TCP Server (SW Maps / QField)",
                sublabel = "127.0.0.1:${nmeaServerState.port} • ${if (nmeaServerState.listening) "${nmeaServerState.clientCount} clients (${formatBytes(nmeaServerState.bytesSent)})" else "Stopped"}",
                checked = nmeaServerState.listening,
                onCheckedChange = onToggleNmeaServer,
            )

            ToggleRow(
                label = "RTCM TCP Server",
                sublabel = "127.0.0.1:${rtcmServerState.port} • ${if (rtcmServerState.listening) "${rtcmServerState.clientCount} clients (${formatBytes(rtcmServerState.bytesSent)})" else "Stopped"}",
                checked = rtcmServerState.listening,
                onCheckedChange = onToggleRtcmServer,
            )
        }
    }
}

@Composable
private fun DataLoggerCard(
    rawLoggerState: com.geodnet.ntrip.logging.RawLoggerState,
    gnssRawLoggerState: com.geodnet.ntrip.logging.GnssRawLoggerState,
    onToggleRawLogger: (Boolean) -> Unit,
    onToggleGnssRawLogger: (Boolean) -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Data Logging (PPK / RINEX)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            ToggleRow(
                label = "Raw Binary Stream Logger (Base & Rover)",
                sublabel = if (rawLoggerState.active) "Base: ${formatBytes(rawLoggerState.baseBytesWritten)} • Rove: ${formatBytes(rawLoggerState.roveBytesWritten)}" else "Off",
                checked = rawLoggerState.active,
                onCheckedChange = onToggleRawLogger,
            )

            ToggleRow(
                label = "Android GNSS Raw & IMU Logger",
                sublabel = if (gnssRawLoggerState.active) "${gnssRawLoggerState.measurementEventCount} meas, ${gnssRawLoggerState.navMessageCount} nav msgs" else "Off",
                checked = gnssRawLoggerState.active,
                onCheckedChange = onToggleGnssRawLogger,
            )
            gnssRawLoggerState.errorMessage?.let {
                Text("⚠️ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RtcmLiveLogCard(
    messages: List<RtcmMessage>,
    onClearLog: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf("ALL") }
    var isPaused by remember { mutableStateOf(false) }
    var frozenMessages by remember { mutableStateOf<List<RtcmMessage>>(emptyList()) }
    val listState = rememberLazyListState()

    val sourceMessages = if (isPaused) frozenMessages else messages
    val filteredMessages = remember(sourceMessages, selectedFilter) {
        when (selectedFilter) {
            "MSM" -> sourceMessages.filter { it.msgKey.startsWith("107") || it.msgKey.startsWith("108") || it.msgKey.startsWith("109") || it.msgKey.startsWith("111") || it.msgKey.startsWith("112") }
            "EPH" -> sourceMessages.filter { it.msgKey in listOf("1019", "1020", "1042", "1044", "1045", "1046") }
            "STA" -> sourceMessages.filter { it.msgKey in listOf("1005", "1006", "1033") }
            "ERR" -> sourceMessages.filter { !it.crcOk }
            else -> sourceMessages
        }
    }

    LaunchedEffect(filteredMessages.size, isPaused) {
        if (!isPaused && filteredMessages.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Live Decode Log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (isPaused) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Text(
                                "PAUSED",
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            if (!isPaused) {
                                frozenMessages = messages
                                isPaused = true
                            } else {
                                isPaused = false
                            }
                        }
                    ) {
                        Text(
                            if (isPaused) "▶ Resume" else "⏸ Pause",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isPaused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Filter Chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("ALL", "MSM", "EPH", "STA", "ERR").forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter, fontSize = 11.sp) }
                    )
                }
            }

            // Terminal container
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                if (filteredMessages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No messages matching filter", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    ) {
                        items(filteredMessages) { msg ->
                            RtcmLogRow(msg)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RtcmLogRow(message: RtcmMessage) {
    val color = if (!message.crcOk) SurveyColors.Error else getRtcmConstellationColor(message.msgKey)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message.msgKey.padEnd(5),
            color = color,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
        Text(
            text = "${message.lengthBytes}B".padStart(4),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.5.sp
        )
        Text(
            text = message.summary,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.5.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TelemetryTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun FixQualityBadge(quality: Int) {
    val (label, color) = when (quality) {
        4 -> "RTK FIXED [4]" to SurveyColors.RtkFixed
        5 -> "RTK FLOAT [5]" to SurveyColors.RtkFloat
        2 -> "DGPS [2]" to SurveyColors.Dgps
        1 -> "SINGLE [1]" to SurveyColors.Single
        else -> "NO FIX [$quality]" to SurveyColors.NoFix
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    sublabel: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            sublabel?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ---------------------------------------------------------------------------
// Settings & Profile Dialog
// ---------------------------------------------------------------------------

@Composable
private fun NtripSettingsDialog(
    profiles: List<NtripProfile>,
    selectedProfileId: String?,
    profileName: String,
    onProfileNameChange: (String) -> Unit,
    host: String,
    onHostChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    mountpoint: String,
    onMountpointChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    latitude: String,
    onLatitudeChange: (String) -> Unit,
    longitude: String,
    onLongitudeChange: (String) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onPullSourcetable: () -> Unit,
    onLoadProfile: (NtripProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onSaveAsNew: () -> Unit,
    onUpdateSelected: () -> Unit,
    onConnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showPassword by remember { mutableStateOf(false) }
    var validationAttempted by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(8.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("NTRIP Settings & Profiles", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onDismiss) { Text("Close") }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Account Guidance & Application Banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (validationAttempted && (username.isBlank() || password.isBlank()))
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                        else
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (validationAttempted && (username.isBlank() || password.isBlank()))
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("🔑", fontSize = 16.sp)
                                Text(
                                    if (validationAttempted && (username.isBlank() || password.isBlank()))
                                        "NTRIP Credentials Required"
                                    else
                                        "NTRIP Account & Credentials",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (validationAttempted && (username.isBlank() || password.isBlank()))
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                if (validationAttempted && (username.isBlank() || password.isBlank()))
                                    "A valid username and password are required to connect to the caster. Please enter your credentials below, or apply for an account:"
                                else
                                    "An active NTRIP account is required to stream RTCM corrections. Enter your username & password below, or apply for a free GEODNET RTK account:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://geodnet.com/free"))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("🌐 Apply for Free Account (geodnet.com/free)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    Text("SAVED PROFILES", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (profiles.isEmpty()) {
                        Text("No saved profiles yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        profiles.forEach { profile ->
                            ProfileItem(
                                profile = profile,
                                selected = profile.id == selectedProfileId,
                                onLoad = { onLoadProfile(profile) },
                                onDelete = { onDeleteProfile(profile.id) },
                            )
                        }
                    }

                    val trimmedInputName = profileName.trim()
                    val existingDuplicate = remember(trimmedInputName, profiles, selectedProfileId) {
                        if (trimmedInputName.isNotEmpty()) {
                            profiles.find { it.name.trim().equals(trimmedInputName, ignoreCase = true) && it.id != selectedProfileId }
                        } else null
                    }

                    OutlinedTextField(
                        value = profileName,
                        onValueChange = onProfileNameChange,
                        label = { Text("Profile Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        supportingText = if (existingDuplicate != null) {
                            { Text("⚠️ Profile name already exists. Saving will update/overwrite it.", color = MaterialTheme.colorScheme.error) }
                        } else null
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onSaveAsNew,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Save As New")
                        }
                        OutlinedButton(
                            onClick = onUpdateSelected,
                            enabled = selectedProfileId != null,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Update Selected")
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text("CASTER PARAMETERS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                    OutlinedTextField(
                        value = host,
                        onValueChange = onHostChange,
                        label = { Text("Caster Host / IP") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = port,
                            onValueChange = onPortChange,
                            label = { Text("Port") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = mountpoint,
                            onValueChange = onMountpointChange,
                            label = { Text("Mountpoint") },
                            singleLine = true,
                            modifier = Modifier.weight(2f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    OutlinedButton(
                        onClick = onPullSourcetable,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("📋 Pull NTRIP Sourcetable", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            validationAttempted = false
                            onUsernameChange(it)
                        },
                        label = { Text("Username *") },
                        isError = validationAttempted && username.isBlank(),
                        supportingText = if (validationAttempted && username.isBlank()) {
                            { Text("⚠️ Username is required", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            validationAttempted = false
                            onPasswordChange(it)
                        },
                        label = { Text("Password *") },
                        isError = validationAttempted && password.isBlank(),
                        supportingText = if (validationAttempted && password.isBlank()) {
                            { Text("⚠️ Password is required", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        singleLine = true,
                        trailingIcon = {
                            TextButton(onClick = { showPassword = !showPassword }) {
                                Text(if (showPassword) "Hide" else "Show", fontSize = 12.sp)
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("REFERENCE POSITION (GGA)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        TextButton(onClick = onUseCurrentLocation) {
                            Text("Use Current GPS", fontSize = 12.sp)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = latitude,
                            onValueChange = onLatitudeChange,
                            label = { Text("Latitude") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        OutlinedTextField(
                            value = longitude,
                            onValueChange = onLongitudeChange,
                            label = { Text("Longitude") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Button(
                    onClick = {
                        if (username.isBlank() || password.isBlank()) {
                            validationAttempted = true
                        } else {
                            onConnect()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save & Connect", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ProfileItem(
    profile: NtripProfile,
    selected: Boolean,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLoad)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    profile.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${profile.config.host}:${profile.config.port}/${profile.config.mountpoint}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
            TextButton(onClick = onDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
/**
 * Modern GEODNET Base Station Network Discovery & Nearest Station Assistant Card.
 */
@Composable
private fun GeodnetCoverageCard(
    nearbyStations: List<NearbyStation>,
    isLoading: Boolean,
    baseStation: com.geodnet.ntrip.rtcm.BaseStationFix?,
    epochStats: com.geodnet.ntrip.rtcm.EpochLatencyStats,
    diffStationId: Int?,
    onRefresh: () -> Unit,
) {
    fun isStationMatched(st: NearbyStation): Boolean {
        // 1. Strict Physical Coordinate Match from RTCM 1005/1006 (within 500 meters)
        if (baseStation != null && (baseStation.latDeg != 0.0 || baseStation.lonDeg != 0.0)) {
            val distKm = com.geodnet.ntrip.data.GeodnetCoverageRepository.haversineDistanceKm(
                st.lat, st.lng, baseStation.latDeg, baseStation.lonDeg
            )
            return distKm < 0.5
        }

        // 2. Exact Numeric Station ID Match (only when coordinates are not yet available)
        val validIds = listOfNotNull(
            baseStation?.staId?.takeIf { it > 0 },
            epochStats.baseStationId?.takeIf { it > 0 },
            diffStationId?.takeIf { it > 0 }
        )

        if (validIds.isEmpty()) return false

        val stNumericId = st.shortName.toIntOrNull()
            ?: st.name.filter { it.isDigit() }.toIntOrNull()

        if (stNumericId != null && stNumericId > 0) {
            return validIds.any { it == stNumericId }
        }

        return false
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SurveyColors.Gps.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🌐", fontSize = 14.sp)
                    }
                    Column {
                        Text(
                            "GEODNET Station Discovery",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (nearbyStations.isNotEmpty()) "${nearbyStations.size} base stations within 100 km" else "Global 19k+ GNSS Network",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onRefresh,
                    enabled = !isLoading,
                    modifier = Modifier.size(32.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("🔄", fontSize = 14.sp)
                    }
                }
            }

            val hasActiveWithin40km = nearbyStations.any {
                it.distanceKm <= 40.0 && it.status.equals("ACTIVE", ignoreCase = true)
            }

            if (!hasActiveWithin40km) {
                val context = LocalContext.current
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("📡", fontSize = 18.sp)
                            Text(
                                "No Active Base Station Within 40 km",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            "For reliable centimeter-level RTK fix accuracy, an active base station within 40 km is recommended. Host your own GEODNET RTK base station to provide local coverage and earn rewards.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://store.geodnet.com/"))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🛒 Purchase / Host Base Station (store.geodnet.com)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (nearbyStations.isNotEmpty()) {
                val activeStation = nearbyStations.find { isStationMatched(it) }
                val targetStation = activeStation ?: nearbyStations.first()
                val isMatched = isStationMatched(targetStation)
                val (qualityLabel, qualityColor) = when {
                    targetStation.distanceKm <= 25.0 -> "OPTIMAL RTK (<25km)" to SurveyColors.RtkFixed
                    targetStation.distanceKm <= 50.0 -> "EXTENDED RTK (25-50km)" to SurveyColors.RtkFloat
                    else -> "DGPS BASELINE (>50km)" to SurveyColors.Dgps
                }

                // Active / Nearest Base Station Hero Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isMatched) SurveyColors.Connected.copy(alpha = 0.12f) else qualityColor.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(
                        if (isMatched) 2.dp else 1.5.dp,
                        if (isMatched) SurveyColors.Connected else qualityColor.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        if (isMatched) "Connected Base: #${targetStation.shortName}" else "Nearest Base: #${targetStation.shortName}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text(
                                    "Baseline: %.2f km • Azimuth: %.1f° %s".format(
                                        targetStation.distanceKm,
                                        targetStation.azimuthDeg,
                                        targetStation.cardinalDirection
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Connected RTCM or Network Status + Quality Badge
                            if (isMatched) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = SurveyColors.Connected.copy(alpha = 0.2f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SurveyColors.Connected)
                                ) {
                                    Text(
                                        text = "CONNECTED BASE ✓",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = SurveyColors.Connected,
                                        fontSize = 11.sp
                                    )
                                }
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (targetStation.status.equals("ACTIVE", ignoreCase = true)) SurveyColors.RtkFixed.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (targetStation.status.equals("ACTIVE", ignoreCase = true)) SurveyColors.RtkFixed else MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Text(
                                            text = targetStation.status,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (targetStation.status.equals("ACTIVE", ignoreCase = true)) SurveyColors.RtkFixed else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = qualityColor.copy(alpha = 0.2f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, qualityColor)
                                    ) {
                                        Text(
                                            text = qualityLabel,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = qualityColor,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Coords: %.5f, %.5f".format(targetStation.lat, targetStation.lng),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // Other close active stations list (up to 30 within 100 km)
                val otherStations = nearbyStations.filter { it != targetStation }
                if (otherStations.isNotEmpty()) {
                    var expandedList by remember { mutableStateOf(false) }
                    val displayList = if (expandedList) otherStations else otherStations.take(4)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "OTHER NEARBY BASE STATIONS (${otherStations.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.5.sp
                        )

                        displayList.forEach { st ->
                            val isStMatched = isStationMatched(st)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isStMatched) SurveyColors.Connected.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = if (isStMatched) androidx.compose.foundation.BorderStroke(1.5.dp, SurveyColors.Connected) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "Base #${st.shortName}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            "%.2f km • %.1f° %s • %.4f, %.4f".format(
                                                st.distanceKm,
                                                st.azimuthDeg,
                                                st.cardinalDirection,
                                                st.lat,
                                                st.lng
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isStMatched) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = SurveyColors.Connected.copy(alpha = 0.15f),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, SurveyColors.Connected)
                                            ) {
                                                Text(
                                                    "CONNECTED BASE ✓",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SurveyColors.Connected
                                                )
                                            }
                                        } else {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (st.status.equals("ACTIVE", ignoreCase = true)) SurveyColors.RtkFixed.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                                border = androidx.compose.foundation.BorderStroke(1.dp, if (st.status.equals("ACTIVE", ignoreCase = true)) SurveyColors.RtkFixed else MaterialTheme.colorScheme.outlineVariant)
                                            ) {
                                                Text(
                                                    text = st.status,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (st.status.equals("ACTIVE", ignoreCase = true)) SurveyColors.RtkFixed else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (otherStations.size > 4) {
                            TextButton(
                                onClick = { expandedList = !expandedList },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text(
                                    if (expandedList) "Show Less" else "Show All (${otherStations.size}) Stations",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Acquiring Position Fix...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Connect a BLE RTK receiver or configure manual coordinates to discover closest GEODNET base stations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modern popup modal dialog allowing users to browse, search, filter, and select
 * mountpoints from the caster's live NTRIP Sourcetable.
 */
@Composable
private fun SourcetableDialog(
    sourcetable: NtripSourcetable?,
    isLoading: Boolean,
    errorMessage: String?,
    currentMountpoint: String,
    onRefresh: () -> Unit,
    onSelectStream: (NtripStreamRecord) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "NTRIP Sourcetable",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (sourcetable != null) "${sourcetable.streams.size} streams available" else "Querying caster streams...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onRefresh,
                        enabled = !isLoading,
                        modifier = Modifier.size(36.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("🔄", fontSize = 16.sp)
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Text("✕", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search mountpoint, format, nav system...") },
                    leadingIcon = { Text("🔍", fontSize = 14.sp) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Text("✕", fontSize = 12.sp)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Quick Filters
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val filters = listOf("All", "RTCM 3", "MSM", "AUTO", "NMEA Req")
                    filters.forEach { filter ->
                        val isSelected = selectedFilter == filter
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.clickable { selectedFilter = filter }
                        ) {
                            Text(
                                text = filter,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Error Banner
                if (errorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Error: $errorMessage",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onRefresh) {
                                Text("Retry", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Stream List
                val streams = sourcetable?.streams ?: emptyList()
                val filteredStreams = remember(streams, searchQuery, selectedFilter) {
                    streams.filter { stream ->
                        val matchesSearch = searchQuery.isBlank() ||
                            stream.mountpoint.contains(searchQuery, ignoreCase = true) ||
                            stream.identifier.contains(searchQuery, ignoreCase = true) ||
                            stream.format.contains(searchQuery, ignoreCase = true) ||
                            stream.navSystem.contains(searchQuery, ignoreCase = true) ||
                            stream.formatDetails.contains(searchQuery, ignoreCase = true)

                        val matchesFilter = when (selectedFilter) {
                            "RTCM 3" -> stream.format.contains("RTCM 3", ignoreCase = true)
                            "MSM" -> stream.formatDetails.contains("107", ignoreCase = true) ||
                                stream.formatDetails.contains("108", ignoreCase = true) ||
                                stream.formatDetails.contains("109", ignoreCase = true) ||
                                stream.formatDetails.contains("112", ignoreCase = true)
                            "AUTO" -> stream.mountpoint.startsWith("AUTO", ignoreCase = true)
                            "NMEA Req" -> stream.nmea
                            else -> true
                        }

                        matchesSearch && matchesFilter
                    }
                }

                if (isLoading && streams.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator()
                            Text("Fetching sourcetable from caster...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else if (filteredStreams.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            if (streams.isEmpty()) "No streams found. Check caster host & port." else "No streams matching filter.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredStreams) { stream ->
                            val isSelected = currentMountpoint.equals(stream.mountpoint, ignoreCase = true)
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            stream.mountpoint,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        if (stream.format.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                            ) {
                                                Text(
                                                    text = stream.format,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }

                                    if (stream.identifier.isNotBlank() && !stream.identifier.equals(stream.mountpoint, ignoreCase = true)) {
                                        Text(
                                            stream.identifier,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Nav System & NMEA info
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (stream.navSystem.isNotBlank()) {
                                            Text(
                                                stream.navSystem,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 10.sp
                                            )
                                        }
                                        if (stream.nmea) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = SurveyColors.Dgps.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    "NMEA REQUIRED",
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SurveyColors.Dgps,
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }
                                    }

                                    if (stream.latitude != null && stream.longitude != null) {
                                        Text(
                                            "Coords: %.4f, %.4f".format(stream.latitude, stream.longitude),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Action Button
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        if (isSelected) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = SurveyColors.Connected.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    "Selected Mountpoint ✓",
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SurveyColors.Connected
                                                )
                                            }
                                        } else {
                                            Button(
                                                onClick = { onSelectStream(stream) },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Text("Select Mountpoint", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}



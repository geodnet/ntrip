package com.geodnet.ntrip.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.geodnet.ntrip.ble.NmeaSentence
import com.geodnet.ntrip.data.GeodnetCoverageRepository
import com.geodnet.ntrip.data.NearbyStation
import com.geodnet.ntrip.location.PositionFix
import com.geodnet.ntrip.location.StaticSegment
import com.geodnet.ntrip.ntrip.NtripConfig
import com.geodnet.ntrip.rtcm.BaseStationFix
import com.geodnet.ntrip.rtcm.EpochLatencyStats
import com.geodnet.ntrip.ui.theme.SurveyColors
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject

// Custom Vector Icons for Map Screen Floating Controls
val CrosshairIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Crosshair",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2f)
            lineTo(12f, 5f)
            moveTo(12f, 19f)
            lineTo(12f, 22f)
            moveTo(2f, 12f)
            lineTo(5f, 12f)
            moveTo(19f, 12f)
            lineTo(22f, 12f)
            moveTo(12f, 7f)
            curveTo(9.24f, 7f, 7f, 9.24f, 7f, 12f)
            curveTo(7f, 14.76f, 9.24f, 17f, 12f, 17f)
            curveTo(14.76f, 17f, 17f, 14.76f, 17f, 12f)
            curveTo(17f, 9.24f, 14.76f, 7f, 12f, 7f)
            close()
        }
    }.build()
}

val ExtentsIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Extents",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 5f)
            lineTo(7f, 5f)
            lineTo(7f, 3f)
            lineTo(3f, 3f)
            curveTo(1.9f, 3f, 1f, 3.9f, 1f, 5f)
            lineTo(1f, 9f)
            lineTo(3f, 9f)
            lineTo(3f, 5f)
            close()
            moveTo(19f, 3f)
            lineTo(15f, 3f)
            lineTo(15f, 5f)
            lineTo(19f, 5f)
            lineTo(19f, 9f)
            lineTo(21f, 9f)
            lineTo(21f, 5f)
            curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
            close()
            moveTo(3f, 15f)
            lineTo(1f, 15f)
            lineTo(1f, 19f)
            curveTo(1f, 20.1f, 1.9f, 21f, 3f, 21f)
            lineTo(7f, 21f)
            lineTo(7f, 19f)
            lineTo(3f, 19f)
            lineTo(3f, 15f)
            close()
            moveTo(19f, 19f)
            lineTo(15f, 19f)
            lineTo(15f, 21f)
            lineTo(19f, 21f)
            curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
            lineTo(21f, 15f)
            lineTo(19f, 15f)
            lineTo(19f, 19f)
            close()
        }
    }.build()
}

val BaseTowerIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BaseTower",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2f)
            lineTo(2f, 22f)
            lineTo(22f, 22f)
            close()
            moveTo(12f, 8f)
            lineTo(18f, 20f)
            lineTo(6f, 20f)
            close()
        }
    }.build()
}

/**
 * Offline Leaflet map view with a floating Glassmorphic Survey HUD and interactive Camera FABs.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MapScreen(viewModel: NtripViewModel) {
    val bestFix by viewModel.bestFix.collectAsState()
    val baseStation by viewModel.baseStation.collectAsState()
    val trajectory by viewModel.trajectory.collectAsState()
    val staticSegments by viewModel.staticSegments.collectAsState()
    val showBaseStation by viewModel.showBaseStation.collectAsState()
    val nearbyStations by viewModel.nearbyStations.collectAsState()
    val showNearbyStations by viewModel.showNearbyStations.collectAsState()
    val config by viewModel.config.collectAsState()
    val epochStats by viewModel.epochStats.collectAsState()
    val bleState by viewModel.bleConnectionState.collectAsState()

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageReady by remember { mutableStateOf(false) }
    var sentTrajectoryCount by remember { mutableStateOf(0) }
    var showNearbyDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Fullscreen Leaflet WebView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            sentTrajectoryCount = 0
                            pageReady = true
                        }
                    }
                    loadUrl("file:///android_asset/map/map.html")
                    webViewRef = this
                }
            },
        )

        // 2. Top Floating Glassmorphic Survey HUD
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MapSurveyHud(
                fix = bestFix,
                gst = bleState.latestGst,
                baseStation = baseStation,
                config = config,
                epochStats = epochStats
            )
        }

        // 3. Right Floating Map Action Controls (Center Rover, Fit Extents, Base Station Toggle, Nearby NET Popup)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Re-center Rover
            FloatingActionButton(
                onClick = { webViewRef?.evaluateJavascript("centerRover()", null) },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                elevation = FloatingActionButtonDefaults.elevation(4.dp),
                modifier = Modifier.size(46.dp)
            ) {
                Icon(
                    imageVector = CrosshairIcon,
                    contentDescription = "Center on Rover",
                    modifier = Modifier.size(22.dp)
                )
            }

            // Fit Both Extents
            FloatingActionButton(
                onClick = { webViewRef?.evaluateJavascript("fitExtents()", null) },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                elevation = FloatingActionButtonDefaults.elevation(4.dp),
                modifier = Modifier.size(46.dp)
            ) {
                Icon(
                    imageVector = ExtentsIcon,
                    contentDescription = "Fit Extents",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Toggle Connected Base Station ARP & Vector
            FloatingActionButton(
                onClick = { viewModel.setShowBaseStation(!showBaseStation) },
                shape = CircleShape,
                containerColor = if (showBaseStation) SurveyColors.Connected else MaterialTheme.colorScheme.surface,
                contentColor = if (showBaseStation) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                elevation = FloatingActionButtonDefaults.elevation(4.dp),
                modifier = Modifier.size(46.dp)
            ) {
                Icon(
                    imageVector = BaseTowerIcon,
                    contentDescription = "Toggle Base Station",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Open Nearby GEODNET Base Stations Popup Window
            FloatingActionButton(
                onClick = { showNearbyDialog = true },
                shape = CircleShape,
                containerColor = if (nearbyStations.isNotEmpty()) SurveyColors.RtkFixed else MaterialTheme.colorScheme.surface,
                contentColor = if (nearbyStations.isNotEmpty()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                elevation = FloatingActionButtonDefaults.elevation(4.dp),
                modifier = Modifier.size(46.dp)
            ) {
                Text(
                    text = "NET",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }

        // 4. Bottom Track Stats & Clear HUD
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            tonalElevation = 4.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Track: ${trajectory.size} pts • Net: ${nearbyStations.size} bases",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable { showNearbyDialog = true }
                )
                if (trajectory.isNotEmpty()) {
                    Text(
                        "Clear",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clickable { viewModel.clearTrajectory() }
                    )
                }
            }
        }
    }

    if (showNearbyDialog) {
        NearbyStationsDialog(
            nearbyStations = nearbyStations,
            currentMountpoint = config.mountpoint,
            onDismiss = { showNearbyDialog = false },
            onSelectMountpoint = { selectedMountpoint, selectedLat, selectedLon ->
                viewModel.applyMountpoint(selectedMountpoint, selectedLat, selectedLon)
                showNearbyDialog = false
            },
            onCenterOnStation = { lat, lng ->
                webViewRef?.evaluateJavascript("window.centerRover = null; map.setView([$lat, $lng], 16)", null)
                showNearbyDialog = false
            }
        )
    }

    LaunchedEffect(pageReady, showBaseStation) {
        if (pageReady) webViewRef?.evaluateJavascript("setBaseVisible($showBaseStation)", null)
    }

    LaunchedEffect(pageReady, showNearbyStations) {
        if (pageReady) webViewRef?.evaluateJavascript("setNearbyStationsVisible($showNearbyStations)", null)
    }

    LaunchedEffect(pageReady, nearbyStations) {
        if (pageReady) {
            webViewRef?.evaluateJavascript("setNearbyStations('${nearbyStationsJson(nearbyStations)}')", null)
        }
    }

    LaunchedEffect(pageReady, bestFix) {
        val fix = bestFix
        if (pageReady && fix != null) {
            webViewRef?.evaluateJavascript(
                "setRover(${fix.latitude}, ${fix.longitude}, ${fix.altitudeM}, ${fix.fixQuality})",
                null,
            )
        }
    }

    LaunchedEffect(pageReady, baseStation) {
        if (!pageReady) return@LaunchedEffect
        val base = baseStation
        if (base != null) {
            webViewRef?.evaluateJavascript(
                "setBase(${base.latDeg}, ${base.lonDeg}, ${base.altM}, ${base.staId}, ${base.baselineKm})",
                null,
            )
        } else {
            webViewRef?.evaluateJavascript("clearBase()", null)
        }
    }

    LaunchedEffect(pageReady, trajectory) {
        if (!pageReady) return@LaunchedEffect
        if (trajectory.size < sentTrajectoryCount) sentTrajectoryCount = 0
        if (sentTrajectoryCount == 0) {
            webViewRef?.evaluateJavascript("setTrajectory('${trajectoryJson(trajectory)}')", null)
        } else if (trajectory.size > sentTrajectoryCount) {
            val newPoints = trajectory.subList(sentTrajectoryCount, trajectory.size)
            webViewRef?.evaluateJavascript("appendTrajectoryPoints('${trajectoryJson(newPoints)}')", null)
        }
        sentTrajectoryCount = trajectory.size
    }

    LaunchedEffect(pageReady, staticSegments) {
        if (pageReady) {
            webViewRef?.evaluateJavascript("setStaticSegments('${segmentsJson(staticSegments)}')", null)
        }
    }
}

/**
 * Top floating survey HUD pill bar: Fix Quality, Satellites, Accuracy, Baseline Length, Data Latency, and Base ID.
 */
@Composable
private fun MapSurveyHud(
    fix: PositionFix?,
    gst: NmeaSentence.Gst?,
    baseStation: BaseStationFix?,
    config: NtripConfig,
    epochStats: EpochLatencyStats
) {
    val satellites = fix?.numSatellites
    val accuracyM = gst?.let { sqrt(it.latStdDevM * it.latStdDevM + it.lonStdDevM * it.lonStdDevM) }
    val baseId = when {
        fix != null && fix.diffStationId != 0 -> fix.diffStationId
        baseStation != null -> baseStation.staId
        else -> null
    }

    // Base station data latency (rover time tag - base station last obs time tag)
    val latencySec: Double? = fix?.diffAgeSec
        ?: (epochStats.lastMessageAgeMs.takeIf { it > 0 }?.let { it / 1000.0 })

    // Baseline length in km between rover and connected/configured base
    val baselineKm: Double? = baseStation?.baselineKm
        ?: if (fix != null && (config.latitude != 0.0 || config.longitude != 0.0)) {
            GeodnetCoverageRepository.haversineDistanceKm(fix.latitude, fix.longitude, config.latitude, config.longitude)
        } else null

    val fixQuality = fix?.fixQuality ?: 0
    val (qualityLabel, qualityColor) = when (fixQuality) {
        4 -> "RTK FIX" to SurveyColors.RtkFixed
        5 -> "RTK FLOAT" to SurveyColors.RtkFloat
        2 -> "DGPS" to SurveyColors.Dgps
        1 -> "SINGLE" to SurveyColors.Single
        else -> "NO FIX" to SurveyColors.NoFix
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Fix Quality Badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = qualityColor.copy(alpha = 0.16f),
                border = androidx.compose.foundation.BorderStroke(1.dp, qualityColor)
            ) {
                Text(
                    text = qualityLabel,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = qualityColor
                )
            }

            // Baseline Length [km]
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("BASELINE", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = baselineKm?.let { "%.2f km".format(it) } ?: "—",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Data Latency (Rover time tag - Base last obs time tag)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LATENCY", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = latencySec?.let { "%.1fs".format(it) } ?: "—",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Sats
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SATS", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = "${satellites ?: "—"}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }

            // Accuracy
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("RMS ACC", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = accuracyM?.let { "±%.2fm".format(it) } ?: "—",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Base ID
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("BASE", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = baseId?.let { "#$it" } ?: (if (config.mountpoint.isNotBlank() && !config.mountpoint.equals("AUTO", ignoreCase = true)) "#${config.mountpoint.takeLast(5)}" else "—"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun trajectoryJson(points: List<PositionFix>): String {
    val arr = JSONArray()
    for (p in points) arr.put(JSONArray(listOf(p.latitude, p.longitude, p.fixQuality)))
    return arr.toString()
}

private fun segmentsJson(segments: List<StaticSegment>): String {
    val arr = JSONArray()
    for (s in segments) {
        arr.put(JSONArray(listOf(s.meanLatDeg, s.meanLonDeg, s.epochCount, s.stdDevM)))
    }
    return arr.toString()
}

private fun nearbyStationsJson(stations: List<NearbyStation>): String {
    val arr = JSONArray()
    for (st in stations) {
        val obj = JSONObject().apply {
            put("name", st.name)
            put("shortName", st.shortName)
            put("lat", st.lat)
            put("lng", st.lng)
            put("distanceKm", st.distanceKm)
            put("azimuthDeg", st.azimuthDeg)
            put("cardinalDirection", st.cardinalDirection)
            put("isOptimalRtk", st.isOptimalRtk)
        }
        arr.put(obj)
    }
    return arr.toString()
}

/**
 * Modern popup window dialog displaying discovered GEODNET base stations,
 * 5-digit station ID, baseline length in km, azimuth bearing, and coordinates.
 */
@Composable
private fun NearbyStationsDialog(
    nearbyStations: List<NearbyStation>,
    currentMountpoint: String,
    onDismiss: () -> Unit,
    onSelectMountpoint: (String, Double, Double) -> Unit,
    onCenterOnStation: (Double, Double) -> Unit
) {
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
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.78f)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Nearby GEODNET Stations",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (nearbyStations.isNotEmpty()) "${nearbyStations.size} stations within 100 km" else "No stations in range",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Text("✕", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                if (nearbyStations.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No base stations found within 100 km.\nAcquiring a position fix or setting manual coordinates will refresh discovery.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(nearbyStations) { st ->
                            val isSelected = currentMountpoint.equals(st.name, ignoreCase = true) ||
                                currentMountpoint.equals(st.shortName, ignoreCase = true)
                            val (qualityLabel, qualityColor) = when {
                                st.distanceKm <= 25.0 -> "OPTIMAL RTK" to SurveyColors.RtkFixed
                                st.distanceKm <= 50.0 -> "EXTENDED RTK" to SurveyColors.RtkFloat
                                else -> "DGPS BASE" to SurveyColors.Dgps
                            }

                            Card(
                                shape = RoundedCornerShape(14.dp),
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
                                            "Base #${st.shortName}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        // RTK Quality Badge
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = qualityColor.copy(alpha = 0.15f),
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

                                    // Baseline, Azimuth, and Coordinates
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("BASELINE", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                "%.2f km".format(st.distanceKm),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Column {
                                            Text("AZIMUTH", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                "%.1f° %s".format(st.azimuthDeg, st.cardinalDirection),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Column {
                                            Text("COORDINATES", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                "%.4f, %.4f".format(st.lat, st.lng),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Action Buttons Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = { onCenterOnStation(st.lat, st.lng) },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text("View on Map", style = MaterialTheme.typography.labelSmall)
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        if (isSelected) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = SurveyColors.Connected.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    "Active Base ✓",
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = SurveyColors.Connected
                                                )
                                            }
                                        } else {
                                            Button(
                                                onClick = { onSelectMountpoint(st.name, st.lat, st.lng) },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Text("Switch Base", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Dialog Footer
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


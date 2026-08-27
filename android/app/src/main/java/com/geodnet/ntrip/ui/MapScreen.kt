package com.geodnet.ntrip.ui

import android.annotation.SuppressLint
import java.util.Locale
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilterChip
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
import com.geodnet.ntrip.data.GeodnetDatumInfo
import com.geodnet.ntrip.data.GeodnetDatumResolver
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

val PinDropIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "PinDrop",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(18f, 8f)
            curveTo(18f, 4.69f, 15.31f, 2f, 12f, 2f)
            curveTo(8.69f, 2f, 6f, 4.69f, 6f, 8f)
            curveTo(6f, 12.5f, 12f, 19f, 12f, 19f)
            curveTo(12f, 19f, 18f, 12.5f, 18f, 8f)
            close()
            moveTo(12f, 10f)
            curveTo(10.9f, 10f, 10f, 9.1f, 10f, 8f)
            curveTo(10f, 6.9f, 10.9f, 6f, 12f, 6f)
            curveTo(13.1f, 6f, 14f, 6.9f, 14f, 8f)
            curveTo(14f, 9.1f, 13.1f, 10f, 12f, 10f)
            close()
            moveTo(5f, 20f)
            verticalLineTo(22f)
            horizontalLineTo(19f)
            verticalLineTo(20f)
            horizontalLineTo(5f)
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

    val datumInfo = remember(config.mountpoint, config.host, bestFix?.latitude, bestFix?.longitude) {
        GeodnetDatumResolver.resolve(
            mountpoint = config.mountpoint,
            host = config.host,
            lat = bestFix?.latitude,
            lon = bestFix?.longitude
        )
    }

    val mapRover = remember(bestFix, datumInfo.name) {
        bestFix?.let { fix ->
            if (fix.latitude != 0.0 || fix.longitude != 0.0) {
                val pt = com.geodnet.ntrip.data.CoordinateTransform.transformForMapDisplay(
                    fix.latitude, fix.longitude, fix.altitudeM, datumInfo.name, fix.fixQuality
                )
                fix.copy(latitude = pt.latDeg, longitude = pt.lonDeg, altitudeM = pt.heightM)
            } else fix
        }
    }

    val mapBase = remember(baseStation, datumInfo.name) {
        baseStation?.let { base ->
            if (base.latDeg != 0.0 || base.lonDeg != 0.0) {
                val pt = com.geodnet.ntrip.data.CoordinateTransform.transformForMapDisplay(
                    base.latDeg, base.lonDeg, base.altM, datumInfo.name
                )
                base.copy(latDeg = pt.latDeg, lonDeg = pt.lonDeg, altM = pt.heightM)
            } else base
        }
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var pageReady by remember { mutableStateOf(false) }
    var sentTrajectoryCount by remember { mutableStateOf(0) }
    var showNearbyDialog by remember { mutableStateOf(false) }
    var showStaticDialog by remember { mutableStateOf(false) }

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
                epochStats = epochStats,
                datumInfo = datumInfo
            )
        }

        // 3. Right Floating Map Action Controls (Center Rover, Fit Extents, Base Station Toggle, Static Segments, Nearby NET Popup)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Re-center Rover (Phone or BLE RTK Receiver)
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

            // Toggle / View Detected Static Segments
            FloatingActionButton(
                onClick = { showStaticDialog = true },
                shape = CircleShape,
                containerColor = if (staticSegments.isNotEmpty()) Color(0xFF7C3AED) else MaterialTheme.colorScheme.surface,
                contentColor = if (staticSegments.isNotEmpty()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                elevation = FloatingActionButtonDefaults.elevation(4.dp),
                modifier = Modifier.size(46.dp)
            ) {
                if (staticSegments.isNotEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${staticSegments.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "SEG",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp
                        )
                    }
                } else {
                    Icon(
                        imageVector = PinDropIcon,
                        contentDescription = "Static Segments",
                        modifier = Modifier.size(20.dp)
                    )
                }
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
        val isDatumConverted = !datumInfo.name.startsWith("WGS84") &&
                !datumInfo.name.startsWith("ITRF2020") &&
                !datumInfo.name.startsWith("AUTO (Pending") &&
                !datumInfo.name.startsWith("Broadcast")

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 14.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Track: ${trajectory.size} pts • Net: ${nearbyStations.size} bases",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.clickable {
                            if (staticSegments.isNotEmpty()) showStaticDialog = true else showNearbyDialog = true
                        }
                    )
                    if (trajectory.isNotEmpty()) {
                        Text(
                            "Clear",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .clickable { viewModel.clearTrajectory() }
                        )
                    }
                }

                if (isDatumConverted) {
                    Text(
                        text = "🌐 ${datumInfo.name} → WGS84",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }

    if (showNearbyDialog) {
        NearbyStationsDialog(
            nearbyStations = nearbyStations,
            baseStation = baseStation,
            epochStats = epochStats,
            diffStationId = bestFix?.diffStationId,
            onDismiss = { showNearbyDialog = false },
            onCenterOnStation = { lat, lng ->
                webViewRef?.evaluateJavascript("window.centerRover = null; map.setView([$lat, $lng], 16)", null)
                showNearbyDialog = false
            }
        )
    }

    if (showStaticDialog) {
        StaticSegmentsDialog(
            staticSegments = staticSegments,
            onDismiss = { showStaticDialog = false },
            onZoomToSegment = { lat, lng ->
                val pt = com.geodnet.ntrip.data.CoordinateTransform.transformForMapDisplay(lat, lng, 0.0, datumInfo.name)
                webViewRef?.evaluateJavascript("window.zoomToStaticSegment(${pt.latDeg}, ${pt.lonDeg})", null)
                showStaticDialog = false
            },
            onZoomToAll = {
                webViewRef?.evaluateJavascript("window.zoomToAllStaticSegments('${segmentsJson(staticSegments, datumInfo.name)}')", null)
                showStaticDialog = false
            }
        )
    }

    // Auto-zoom to current location (phone or RTK receiver) on tab switch / page load
    LaunchedEffect(pageReady) {
        if (pageReady) {
            val fix = mapRover
            if (fix != null && (fix.latitude != 0.0 || fix.longitude != 0.0)) {
                webViewRef?.evaluateJavascript(
                    "setRover(${fix.latitude}, ${fix.longitude}, ${fix.altitudeM}, ${fix.fixQuality})",
                    null
                )
                webViewRef?.evaluateJavascript(
                    "map.setView([${fix.latitude}, ${fix.longitude}], 18, { animate: true })",
                    null
                )
            }
        }
    }

    LaunchedEffect(pageReady, showBaseStation) {
        if (pageReady) webViewRef?.evaluateJavascript("setBaseVisible($showBaseStation)", null)
    }

    LaunchedEffect(pageReady, showNearbyStations) {
        if (pageReady) webViewRef?.evaluateJavascript("setNearbyStationsVisible($showNearbyStations)", null)
    }

    val currentStationId = mapRover?.diffStationId ?: baseStation?.staId ?: epochStats.baseStationId
    LaunchedEffect(pageReady, nearbyStations, mapBase, currentStationId) {
        if (pageReady) {
            webViewRef?.evaluateJavascript(
                "setNearbyStations('${nearbyStationsJson(nearbyStations, mapBase, null, currentStationId)}')",
                null
            )
        }
    }

    LaunchedEffect(pageReady, mapRover) {
        val fix = mapRover
        if (pageReady && fix != null) {
            webViewRef?.evaluateJavascript(
                "setRover(${fix.latitude}, ${fix.longitude}, ${fix.altitudeM}, ${fix.fixQuality})",
                null,
            )
        }
    }

    LaunchedEffect(pageReady, mapBase) {
        if (!pageReady) return@LaunchedEffect
        val base = mapBase
        if (base != null) {
            webViewRef?.evaluateJavascript(
                "setBase(${base.latDeg}, ${base.lonDeg}, ${base.altM}, ${base.staId}, ${base.baselineKm})",
                null,
            )
        } else {
            webViewRef?.evaluateJavascript("clearBase()", null)
        }
    }

    LaunchedEffect(pageReady, trajectory, datumInfo.name) {
        if (!pageReady) return@LaunchedEffect
        if (trajectory.size < sentTrajectoryCount) sentTrajectoryCount = 0
        if (sentTrajectoryCount == 0) {
            webViewRef?.evaluateJavascript("setTrajectory('${trajectoryJson(trajectory, datumInfo.name)}')", null)
        } else if (trajectory.size > sentTrajectoryCount) {
            val newPoints = trajectory.subList(sentTrajectoryCount, trajectory.size)
            webViewRef?.evaluateJavascript("appendTrajectoryPoints('${trajectoryJson(newPoints, datumInfo.name)}')", null)
        }
        sentTrajectoryCount = trajectory.size
    }

    LaunchedEffect(pageReady, staticSegments, datumInfo.name) {
        if (pageReady) {
            webViewRef?.evaluateJavascript("setStaticSegments('${segmentsJson(staticSegments, datumInfo.name)}')", null)
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
    epochStats: EpochLatencyStats,
    datumInfo: GeodnetDatumInfo? = null
) {
    val satellites = fix?.numSatellites
    val accuracyM = gst?.let { sqrt(it.latStdDevM * it.latStdDevM + it.lonStdDevM * it.lonStdDevM) }
    val baseId = when {
        fix != null && fix.diffStationId != 0 -> fix.diffStationId
        baseStation != null -> baseStation.staId
        epochStats.baseStationId != null -> epochStats.baseStationId
        else -> null
    }

    // AGE of differential corrections (from NMEA GGA field 13)
    val ageSec: Double? = fix?.diffAgeSec?.takeIf { it > 0.0 }

    // LATENCY: Rover NMEA GGA time tag - Base station RTCM observation time tag
    val latencySec: Double? = com.geodnet.ntrip.rtcm.TimeTagMath.calculateLatencySec(
        roverGgaUtcTime = fix?.utcTime,
        baseTimeTagUtcSec = epochStats.lastBaseTimeTagUtcSec
    ) ?: (epochStats.lastMessageAgeMs.takeIf { it > 0 }?.let { it / 1000.0 })

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
                .padding(horizontal = 10.dp, vertical = 8.dp)
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
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = qualityColor,
                    fontSize = 11.sp
                )
            }

            // Base ID (from NMEA GGA field 14 / RTCM)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("BASE ID", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = baseId?.let { "#$it" } ?: "—",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
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

            // AGE (NMEA GGA field 13)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("AGE", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = ageSec?.let { "%.1fs".format(it) } ?: "—",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // LATENCY (Rover time tag - Base observation time tag)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("LATENCY", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = latencySec?.let { "%.1fs".format(it) } ?: "—",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if ((latencySec ?: 0.0) < 3.0) SurveyColors.Connected else SurveyColors.RtkFloat
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
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun trajectoryJson(points: List<PositionFix>, datumName: String = ""): String {
    val arr = JSONArray()
    for (p in points) {
        val pt = if (datumName.isNotBlank() && p.fixQuality in listOf(2, 4, 5)) {
            com.geodnet.ntrip.data.CoordinateTransform.transformForMapDisplay(p.latitude, p.longitude, p.altitudeM, datumName, p.fixQuality)
        } else null
        val lat = pt?.latDeg ?: p.latitude
        val lon = pt?.lonDeg ?: p.longitude
        arr.put(JSONArray(listOf(lat, lon, p.fixQuality)))
    }
    return arr.toString()
}

private fun segmentsJson(segments: List<StaticSegment>, datumName: String = ""): String {
    val arr = JSONArray()
    for (s in segments) {
        val pt = if (datumName.isNotBlank()) {
            com.geodnet.ntrip.data.CoordinateTransform.transformForMapDisplay(s.meanLatDeg, s.meanLonDeg, s.meanAltM, datumName)
        } else null
        val lat = pt?.latDeg ?: s.meanLatDeg
        val lon = pt?.lonDeg ?: s.meanLonDeg
        val alt = pt?.heightM ?: s.meanAltM
        arr.put(
            JSONArray(
                listOf(
                    lat,
                    lon,
                    s.epochCount,
                    s.stdDev2dM,
                    alt,
                    s.durationSec,
                    s.stdDevNorthM,
                    s.stdDevEastM,
                    s.stdDevUpM,
                    s.stdDev3dM
                )
            )
        )
    }
    return arr.toString()
}

private fun nearbyStationsJson(
    stations: List<NearbyStation>,
    baseStation: com.geodnet.ntrip.rtcm.BaseStationFix? = null,
    epochStats: com.geodnet.ntrip.rtcm.EpochLatencyStats? = null,
    diffStationId: Int? = null
): String {
    val arr = JSONArray()
    for (st in stations) {
        val isConnected = if (baseStation != null && (baseStation.latDeg != 0.0 || baseStation.lonDeg != 0.0)) {
            val distKm = com.geodnet.ntrip.data.GeodnetCoverageRepository.haversineDistanceKm(
                st.lat, st.lng, baseStation.latDeg, baseStation.lonDeg
            )
            distKm < 0.5
        } else {
            val validIds = listOfNotNull(
                baseStation?.staId?.takeIf { it > 0 },
                epochStats?.baseStationId?.takeIf { it > 0 },
                diffStationId?.takeIf { it > 0 }
            )
            val stNumericId = st.shortName.toIntOrNull() ?: st.name.filter { it.isDigit() }.toIntOrNull()
            stNumericId != null && stNumericId > 0 && validIds.contains(stNumericId)
        }

        val obj = JSONObject().apply {
            put("name", st.name)
            put("shortName", st.shortName)
            put("lat", st.lat)
            put("lng", st.lng)
            put("distanceKm", st.distanceKm)
            put("azimuthDeg", st.azimuthDeg)
            put("cardinalDirection", st.cardinalDirection)
            put("isOptimalRtk", st.isOptimalRtk)
            put("status", st.status)
            put("isConnected", isConnected)
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
    baseStation: com.geodnet.ntrip.rtcm.BaseStationFix?,
    epochStats: com.geodnet.ntrip.rtcm.EpochLatencyStats?,
    diffStationId: Int?,
    onDismiss: () -> Unit,
    onCenterOnStation: (Double, Double) -> Unit
) {
    fun isStationMatched(st: NearbyStation): Boolean {
        if (baseStation != null && (baseStation.latDeg != 0.0 || baseStation.lonDeg != 0.0)) {
            val distKm = com.geodnet.ntrip.data.GeodnetCoverageRepository.haversineDistanceKm(
                st.lat, st.lng, baseStation.latDeg, baseStation.lonDeg
            )
            if (distKm < 0.5) return true
        }

        val validIds = listOfNotNull(
            baseStation?.staId?.takeIf { it > 0 },
            epochStats?.baseStationId?.takeIf { it > 0 },
            diffStationId?.takeIf { it > 0 }
        )

        if (validIds.isNotEmpty()) {
            val stNumericId = st.shortName.toIntOrNull() ?: st.name.filter { it.isDigit() }.toIntOrNull()
            if (stNumericId != null && stNumericId > 0 && validIds.contains(stNumericId)) {
                return true
            }
        }

        return false
    }

    val hasActiveStation = nearbyStations.any { isStationMatched(it) }
    var filterActiveOnly by remember(hasActiveStation) { mutableStateOf(false) }
    val sortedStations = remember(nearbyStations, baseStation, epochStats, diffStationId) {
        nearbyStations.sortedWith(
            compareByDescending<NearbyStation> { isStationMatched(it) }
                .thenBy { it.distanceKm }
        )
    }
    val displayStations = if (filterActiveOnly && hasActiveStation) sortedStations.filter { isStationMatched(it) } else sortedStations

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "GEODNET Base Stations",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (filterActiveOnly && hasActiveStation) "Showing connected base station"
                            else "${nearbyStations.size} active stations within 100 km (ready for RTK)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }

                if (hasActiveStation) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = filterActiveOnly,
                            onClick = { filterActiveOnly = true },
                            label = { Text("Connected Base Only", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                        FilterChip(
                            selected = !filterActiveOnly,
                            onClick = { filterActiveOnly = false },
                            label = { Text("All Base Stations (${nearbyStations.size})", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                if (displayStations.isEmpty()) {
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
                        items(displayStations) { st ->
                            val isMatched = isStationMatched(st)
                            val (qualityLabel, qualityColor) = when {
                                st.distanceKm <= 25.0 -> "OPTIMAL RTK" to SurveyColors.RtkFixed
                                st.distanceKm <= 50.0 -> "EXTENDED RTK" to SurveyColors.RtkFloat
                                else -> "DGPS BASE" to SurveyColors.Dgps
                            }

                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isMatched) SurveyColors.Connected.copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ),
                                border = if (isMatched) androidx.compose.foundation.BorderStroke(1.5.dp, SurveyColors.Connected)
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
                                        Column {
                                            Text(
                                                "Base #${st.shortName}",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isMatched) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = SurveyColors.Connected.copy(alpha = 0.2f),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, SurveyColors.Connected)
                                                ) {
                                                    Text(
                                                        text = "CONNECTED BASE ✓",
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = SurveyColors.Connected,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            } else {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (st.status.equals("ACTIVE", ignoreCase = true)) SurveyColors.RtkFixed.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (st.status.equals("ACTIVE", ignoreCase = true)) SurveyColors.RtkFixed else MaterialTheme.colorScheme.outlineVariant)
                                                ) {
                                                    Text(
                                                        text = st.status,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (st.status.equals("ACTIVE", ignoreCase = true)) SurveyColors.RtkFixed else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }

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

                                    // Action Button Row
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
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog displaying detected static survey occupation points, durations, epoch counts, and millimeter precision.
 */
@Composable
private fun StaticSegmentsDialog(
    staticSegments: List<StaticSegment>,
    onDismiss: () -> Unit,
    onZoomToSegment: (Double, Double) -> Unit,
    onZoomToAll: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Static Survey Segments",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7C3AED)
                        )
                        Text(
                            if (staticSegments.isNotEmpty()) "${staticSegments.size} static occupation points detected" else "Stationary GNSS auto-detection",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }

                if (staticSegments.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = onZoomToAll,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Fit All Segments on Map", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (staticSegments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Text("📍", fontSize = 32.sp)
                            Text(
                                "No Static Segments Detected Yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "When the receiver holds still for 5+ seconds within a tight cluster (<5cm), the app automatically computes the centroid position and records a high-precision static occupation point.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(staticSegments) { index, seg ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF7C3AED).copy(alpha = 0.08f)
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF7C3AED).copy(alpha = 0.4f)),
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
                                            "Occupation #${index + 1}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF7C3AED)
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF7C3AED).copy(alpha = 0.2f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF7C3AED))
                                        ) {
                                            Text(
                                                text = "σ2D: ±%.4fm".format(Locale.US, seg.stdDev2dM),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF7C3AED),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("DURATION", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                "%.1fs (%d eps)".format(Locale.US, seg.durationSec, seg.epochCount),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Column {
                                            Text("HEIGHT (UP)", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                "%.4f m".format(Locale.US, seg.meanAltM),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Column {
                                            Text("COORDINATES (WGS84)", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                "%.9f,\n%.9f".format(Locale.US, seg.meanLatDeg, seg.meanLonDeg),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    // NEU Precision Details
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "σN: ±%.4fm".format(Locale.US, seg.stdDevNorthM),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 10.sp
                                            )
                                            Text(
                                                "σE: ±%.4fm".format(Locale.US, seg.stdDevEastM),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 10.sp
                                            )
                                            Text(
                                                "σU: ±%.4fm".format(Locale.US, seg.stdDevUpM),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = { onZoomToSegment(seg.meanLatDeg, seg.meanLonDeg) },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(30.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                                        ) {
                                            Text("Zoom on Map", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}



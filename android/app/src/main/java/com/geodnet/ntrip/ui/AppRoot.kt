package com.geodnet.ntrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geodnet.ntrip.ntrip.NtripStatus
import com.geodnet.ntrip.ui.theme.SurveyColors

private enum class AppTab(val label: String) { CONNECTION("Client"), MAP("Live Map") }

// Clean custom vector icons for NavigationBar
val AntennaIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Antenna",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 4f,
            pathFillType = PathFillType.NonZero
        ) {
            // Mast and center tower
            moveTo(11f, 11f)
            lineTo(11f, 21f)
            lineTo(13f, 21f)
            lineTo(13f, 11f)
            close()
            // Top emitter
            moveTo(12f, 8f)
            curveTo(13.1f, 8f, 14f, 7.1f, 14f, 6f)
            curveTo(14f, 4.9f, 13.1f, 4f, 12f, 4f)
            curveTo(10.9f, 4f, 10f, 4.9f, 10f, 6f)
            curveTo(10f, 7.1f, 10.9f, 8f, 12f, 8f)
            close()
            // Outer waves
            moveTo(5.64f, 3.64f)
            lineTo(4.22f, 2.22f)
            curveTo(1.62f, 4.82f, 0f, 8.22f, 0f, 12f)
            curveTo(0f, 15.78f, 1.62f, 19.18f, 4.22f, 21.78f)
            lineTo(5.64f, 20.36f)
            curveTo(3.39f, 18.11f, 2f, 15.22f, 2f, 12f)
            curveTo(2f, 8.78f, 3.39f, 5.89f, 5.64f, 3.64f)
            close()
            moveTo(19.78f, 2.22f)
            lineTo(18.36f, 3.64f)
            curveTo(20.61f, 5.89f, 22f, 8.78f, 22f, 12f)
            curveTo(22f, 15.22f, 20.61f, 18.11f, 18.36f, 20.36f)
            lineTo(19.78f, 21.78f)
            curveTo(22.38f, 19.18f, 24f, 15.78f, 24f, 12f)
            curveTo(24f, 8.22f, 22.38f, 4.82f, 19.78f, 2.22f)
            close()
            // Inner waves
            moveTo(7.76f, 7.76f)
            lineTo(6.34f, 6.34f)
            curveTo(4.91f, 7.78f, 4f, 9.79f, 4f, 12f)
            curveTo(4f, 14.21f, 4.91f, 16.22f, 6.34f, 17.66f)
            lineTo(7.76f, 16.24f)
            curveTo(6.68f, 15.16f, 6f, 13.66f, 6f, 12f)
            curveTo(6f, 10.34f, 6.68f, 8.84f, 7.76f, 7.76f)
            close()
            moveTo(17.66f, 6.34f)
            lineTo(16.24f, 7.76f)
            curveTo(17.32f, 8.84f, 18f, 10.34f, 18f, 12f)
            curveTo(18f, 13.66f, 17.32f, 15.16f, 16.24f, 16.24f)
            lineTo(17.66f, 17.66f)
            curveTo(19.09f, 16.22f, 20f, 14.21f, 20f, 12f)
            curveTo(20f, 9.79f, 19.09f, 7.78f, 17.66f, 6.34f)
            close()
        }
    }.build()
}

val MapLocationIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "MapLocation",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            fillAlpha = 1f,
            stroke = null,
            strokeAlpha = 1f,
            strokeLineWidth = 0f,
            strokeLineCap = StrokeCap.Butt,
            strokeLineJoin = StrokeJoin.Miter,
            strokeLineMiter = 4f,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(12f, 2f)
            curveTo(8.13f, 2f, 5f, 5.13f, 5f, 9f)
            curveTo(5f, 14.25f, 12f, 22f, 12f, 22f)
            curveTo(12f, 22f, 19f, 14.25f, 19f, 9f)
            curveTo(19f, 5.13f, 15.87f, 2f, 12f, 2f)
            close()
            moveTo(12f, 11.5f)
            curveTo(10.62f, 11.5f, 9.5f, 10.38f, 9.5f, 9f)
            curveTo(9.5f, 7.62f, 10.62f, 6.5f, 12f, 6.5f)
            curveTo(13.38f, 6.5f, 14.5f, 7.62f, 14.5f, 9f)
            curveTo(14.5f, 10.38f, 13.38f, 11.5f, 12f, 11.5f)
            close()
        }
    }.build()
}

@Composable
fun AppRoot(viewModel: NtripViewModel) {
    var selectedTab by remember { mutableStateOf(AppTab.CONNECTION) }
    val connectionState by viewModel.connectionState.collectAsState()
    val bestFix by viewModel.bestFix.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            when (tab) {
                                AppTab.CONNECTION -> {
                                    BadgedBox(
                                        badge = {
                                            val statusColor = when (connectionState.status) {
                                                NtripStatus.CONNECTED -> SurveyColors.Connected
                                                NtripStatus.CONNECTING -> SurveyColors.Connecting
                                                NtripStatus.ERROR -> SurveyColors.Error
                                                NtripStatus.DISCONNECTED -> Color.Transparent
                                            }
                                            if (connectionState.status != NtripStatus.DISCONNECTED) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(statusColor)
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = AntennaIcon,
                                            contentDescription = tab.label,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                AppTab.MAP -> {
                                    BadgedBox(
                                        badge = {
                                            bestFix?.let { fix ->
                                                val badgeText = when (fix.fixQuality) {
                                                    4 -> "FIX"
                                                    5 -> "FLT"
                                                    1, 2 -> "DGPS"
                                                    else -> "${fix.numSatellites}"
                                                }
                                                val badgeColor = when (fix.fixQuality) {
                                                    4 -> SurveyColors.RtkFixed
                                                    5 -> SurveyColors.RtkFloat
                                                    1, 2 -> SurveyColors.Dgps
                                                    else -> SurveyColors.NoFix
                                                }
                                                Badge(
                                                    containerColor = badgeColor,
                                                    contentColor = Color.White
                                                ) {
                                                    Text(badgeText, fontSize = 9.sp)
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = MapLocationIcon,
                                            contentDescription = tab.label,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        },
                        label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                AppTab.CONNECTION -> NtripScreen(viewModel)
                AppTab.MAP -> MapScreen(viewModel)
            }
        }
    }
}

package com.geodnet.ntrip.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GeodnetStation(
    val name: String,
    val lat: Double,
    val lng: Double
)

data class NearbyStation(
    val name: String,
    val lat: Double,
    val lng: Double,
    val distanceKm: Double,
    val azimuthDeg: Double,
    val cardinalDirection: String,
    val isOptimalRtk: Boolean
) {
    val shortName: String
        get() = name.takeLast(5)
}

class GeodnetCoverageRepository(private val context: Context) {

    private var cachedStations: List<GeodnetStation> = emptyList()
    private val cacheFile = File(context.cacheDir, "geodnet_stations.json")

    suspend fun loadStations(forceRefresh: Boolean = false): List<GeodnetStation> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedStations.isNotEmpty()) {
            return@withContext cachedStations
        }

        // Try disk cache first if not forcing refresh
        if (!forceRefresh && cacheFile.exists() && cacheFile.length() > 0) {
            try {
                val jsonStr = cacheFile.readText(Charsets.UTF_8)
                val parsed = parseStationsJson(jsonStr)
                if (parsed.isNotEmpty()) {
                    cachedStations = parsed
                    return@withContext parsed
                }
            } catch (_: Exception) {
                // Disk cache corrupted or invalid, proceed to fetch
            }
        }

        // Fetch from GEODNET API
        try {
            val url = URL("https://rtk.geodnet.com/api/v2/coverage_stations")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 12000
                setRequestProperty("User-Agent", "GeodnetNtripAndroid/0.1.0")
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val parsed = parseStationsJson(body)
                if (parsed.isNotEmpty()) {
                    cachedStations = parsed
                    try {
                        cacheFile.writeText(body, Charsets.UTF_8)
                    } catch (_: Exception) {}
                    return@withContext parsed
                }
            }
        } catch (_: Exception) {
            // Network failure: fallback to disk cache if available
            if (cacheFile.exists() && cacheFile.length() > 0) {
                try {
                    val parsed = parseStationsJson(cacheFile.readText(Charsets.UTF_8))
                    if (parsed.isNotEmpty()) {
                        cachedStations = parsed
                        return@withContext parsed
                    }
                } catch (_: Exception) {}
            }
        }

        return@withContext cachedStations
    }

    fun findNearbyStations(
        userLat: Double,
        userLon: Double,
        maxRadiusKm: Double = 100.0,
        limit: Int = 20
    ): List<NearbyStation> {
        if (cachedStations.isEmpty()) return emptyList()

        return cachedStations
            .map { station ->
                val dist = haversineDistanceKm(userLat, userLon, station.lat, station.lng)
                val azimuth = calculateAzimuthDeg(userLat, userLon, station.lat, station.lng)
                val cardinal = azimuthToCardinal(azimuth)
                NearbyStation(
                    name = station.name,
                    lat = station.lat,
                    lng = station.lng,
                    distanceKm = dist,
                    azimuthDeg = azimuth,
                    cardinalDirection = cardinal,
                    isOptimalRtk = dist <= 25.0
                )
            }
            .filter { it.distanceKm <= maxRadiusKm }
            .sortedBy { it.distanceKm }
            .take(limit)
    }

    companion object {
        private val stationPattern = Regex(
            """\{[^{}]*?"name"\s*:\s*"([^"]+)"[^{}]*?"lat"\s*:\s*([-\d.eE]+)[^{}]*?"lng"\s*:\s*([-\d.eE]+)[^{}]*?\}"""
        )

        fun parseStationsJson(jsonStr: String): List<GeodnetStation> {
            if (jsonStr.isBlank()) return emptyList()
            val list = ArrayList<GeodnetStation>()
            try {
                for (match in stationPattern.findAll(jsonStr)) {
                    val name = match.groupValues[1]
                    val lat = match.groupValues[2].toDoubleOrNull() ?: continue
                    val lng = match.groupValues[3].toDoubleOrNull() ?: continue
                    list.add(GeodnetStation(name = name, lat = lat, lng = lng))
                }
            } catch (_: Exception) {
                return emptyList()
            }
            return list
        }

        fun haversineDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371.0 // Earth radius in km
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return r * c
        }

        fun calculateAzimuthDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val phi1 = Math.toRadians(lat1)
            val phi2 = Math.toRadians(lat2)
            val deltaLambda = Math.toRadians(lon2 - lon1)
            val y = sin(deltaLambda) * cos(phi2)
            val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
            val bearingRad = atan2(y, x)
            return (Math.toDegrees(bearingRad) + 360.0) % 360.0
        }

        fun azimuthToCardinal(azimuthDeg: Double): String {
            val directions = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
            val index = (((azimuthDeg % 360.0) + 11.25) / 22.5).toInt() % 16
            return directions[index]
        }
    }
}

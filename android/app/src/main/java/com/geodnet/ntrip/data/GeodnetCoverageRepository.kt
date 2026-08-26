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
                setRequestProperty("Accept-Encoding", "gzip")
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val rawStream = conn.inputStream
                val stream = if ("gzip".equals(conn.contentEncoding, ignoreCase = true)) {
                    java.util.zip.GZIPInputStream(rawStream)
                } else {
                    rawStream
                }
                val body = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
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

        // Fast bounding box pre-filter in degrees (~111km per degree)
        val latDelta = (maxRadiusKm / 110.0) + 0.05
        val cosLat = kotlin.math.cos(Math.toRadians(userLat)).let { if (kotlin.math.abs(it) < 0.01) 0.01 else kotlin.math.abs(it) }
        val lonDelta = (maxRadiusKm / (110.0 * cosLat)) + 0.05

        val minLat = userLat - latDelta
        val maxLat = userLat + latDelta
        val minLon = userLon - lonDelta
        val maxLon = userLon + lonDelta

        val candidates = ArrayList<NearbyStation>(32)
        for (i in 0 until cachedStations.size) {
            val station = cachedStations[i]
            if (station.lat in minLat..maxLat && station.lng in minLon..maxLon) {
                val dist = haversineDistanceKm(userLat, userLon, station.lat, station.lng)
                if (dist <= maxRadiusKm) {
                    val azimuth = calculateAzimuthDeg(userLat, userLon, station.lat, station.lng)
                    val cardinal = azimuthToCardinal(azimuth)
                    candidates.add(
                        NearbyStation(
                            name = station.name,
                            lat = station.lat,
                            lng = station.lng,
                            distanceKm = dist,
                            azimuthDeg = azimuth,
                            cardinalDirection = cardinal,
                            isOptimalRtk = dist <= 25.0
                        )
                    )
                }
            }
        }

        candidates.sortBy { it.distanceKm }
        return if (candidates.size > limit) candidates.subList(0, limit) else candidates
    }

    companion object {
        fun parseStationsJson(jsonStr: String): List<GeodnetStation> {
            if (jsonStr.isBlank()) return emptyList()
            val list = ArrayList<GeodnetStation>(2048)
            val len = jsonStr.length
            var pos = 0

            try {
                while (pos < len) {
                    val nameIdx = jsonStr.indexOf("\"name\"", pos)
                    if (nameIdx == -1) break

                    val colonAfterName = jsonStr.indexOf(':', nameIdx + 6)
                    if (colonAfterName == -1) break
                    val openQuote = jsonStr.indexOf('"', colonAfterName + 1)
                    if (openQuote == -1) break
                    val closeQuote = jsonStr.indexOf('"', openQuote + 1)
                    if (closeQuote == -1) break
                    val name = jsonStr.substring(openQuote + 1, closeQuote)

                    // Find "lat" within next 300 chars
                    val latIdx = jsonStr.indexOf("\"lat\"", closeQuote)
                    if (latIdx == -1 || latIdx - closeQuote > 300) {
                        pos = closeQuote + 1
                        continue
                    }
                    val latColon = jsonStr.indexOf(':', latIdx + 5)
                    if (latColon == -1) break
                    var latEnd = latColon + 1
                    while (latEnd < len && (jsonStr[latEnd] == ' ' || jsonStr[latEnd] == '\t')) latEnd++
                    val latStart = latEnd
                    while (latEnd < len && (jsonStr[latEnd].isDigit() || jsonStr[latEnd] == '-' || jsonStr[latEnd] == '.' || jsonStr[latEnd] == 'e' || jsonStr[latEnd] == 'E' || jsonStr[latEnd] == '+')) {
                        latEnd++
                    }
                    val lat = jsonStr.substring(latStart, latEnd).toDoubleOrNull()

                    // Find "lng" within next 300 chars
                    val lngIdx = jsonStr.indexOf("\"lng\"", latEnd)
                    if (lngIdx == -1 || lngIdx - latEnd > 300) {
                        pos = latEnd
                        continue
                    }
                    val lngColon = jsonStr.indexOf(':', lngIdx + 5)
                    if (lngColon == -1) break
                    var lngEnd = lngColon + 1
                    while (lngEnd < len && (jsonStr[lngEnd] == ' ' || jsonStr[lngEnd] == '\t')) lngEnd++
                    val lngStart = lngEnd
                    while (lngEnd < len && (jsonStr[lngEnd].isDigit() || jsonStr[lngEnd] == '-' || jsonStr[lngEnd] == '.' || jsonStr[lngEnd] == 'e' || jsonStr[lngEnd] == 'E' || jsonStr[lngEnd] == '+')) {
                        lngEnd++
                    }
                    val lng = jsonStr.substring(lngStart, lngEnd).toDoubleOrNull()

                    if (lat != null && lng != null) {
                        list.add(GeodnetStation(name = name, lat = lat, lng = lng))
                        pos = lngEnd
                    } else {
                        pos = closeQuote + 1
                    }
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

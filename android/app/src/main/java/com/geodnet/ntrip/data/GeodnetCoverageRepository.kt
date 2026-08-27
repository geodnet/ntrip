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
    val lng: Double,
    val status: String = "ACTIVE",
    val rtcmStationId: Int? = null
)

data class NearbyStation(
    val name: String,
    val lat: Double,
    val lng: Double,
    val distanceKm: Double,
    val azimuthDeg: Double,
    val cardinalDirection: String,
    val isOptimalRtk: Boolean,
    val status: String = "ACTIVE",
    val rtcmStationId: Int? = null
) {
    val shortName: String
        get() = name.takeLast(5)

    val effectiveStationId: Int?
        get() = rtcmStationId
            ?: shortName.toIntOrNull()
            ?: name.filter { it.isDigit() }.toIntOrNull()
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
        limit: Int = 30,
        activeOnly: Boolean = true
    ): List<NearbyStation> {
        if (cachedStations.isEmpty()) return emptyList()

        // 1. First try requested radius (e.g. 100 km)
        val initial = searchRadius(userLat, userLon, maxRadiusKm, limit, activeOnly)
        if (initial.isNotEmpty()) return initial

        // 2. If no stations within 100 km, expand radius to 400 km so users see the nearest base stations
        return searchRadius(userLat, userLon, 400.0, limit, activeOnly)
    }

    private fun searchRadius(
        userLat: Double,
        userLon: Double,
        radiusKm: Double,
        limit: Int,
        activeOnly: Boolean
    ): List<NearbyStation> {
        val latDelta = (radiusKm / 110.0) + 0.05
        val cosLat = kotlin.math.cos(Math.toRadians(userLat)).let { if (kotlin.math.abs(it) < 0.01) 0.01 else kotlin.math.abs(it) }
        val lonDelta = (radiusKm / (110.0 * cosLat)) + 0.05

        val minLat = userLat - latDelta
        val maxLat = userLat + latDelta
        val minLon = userLon - lonDelta
        val maxLon = userLon + lonDelta

        val candidates = ArrayList<NearbyStation>(32)
        for (i in 0 until cachedStations.size) {
            val station = cachedStations[i]
            // Exclude explicitly offline stations when activeOnly is true
            if (activeOnly && station.status.equals("OFFLINE", ignoreCase = true)) {
                continue
            }
            if (station.lat in minLat..maxLat && station.lng in minLon..maxLon) {
                val dist = haversineDistanceKm(userLat, userLon, station.lat, station.lng)
                if (dist <= radiusKm) {
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
                            isOptimalRtk = dist <= 25.0,
                            status = station.status,
                            rtcmStationId = station.rtcmStationId
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
            val list = ArrayList<GeodnetStation>(20000)
            val len = jsonStr.length
            var i = 0

            try {
                while (i < len) {
                    val nameIdx = jsonStr.indexOf("\"name\"", i)
                    if (nameIdx == -1) break

                    // Find the '{' before "name"
                    var objStart = nameIdx
                    while (objStart >= i && jsonStr[objStart] != '{') objStart--
                    if (objStart < i) {
                        i = nameIdx + 6
                        continue
                    }

                    // Find matching '}' for this object
                    var depth = 0
                    var objEnd = objStart
                    var inString = false
                    while (objEnd < len) {
                        val c = jsonStr[objEnd]
                        if (c == '"' && (objEnd == 0 || jsonStr[objEnd - 1] != '\\')) {
                            inString = !inString
                        } else if (!inString) {
                            if (c == '{') depth++
                            else if (c == '}') {
                                depth--
                                if (depth == 0) break
                            }
                        }
                        objEnd++
                    }
                    if (objEnd >= len) break

                    val objStr = jsonStr.substring(objStart, objEnd + 1)
                    parseSingleStation(objStr)?.let { list.add(it) }

                    i = objEnd + 1
                }
            } catch (_: Exception) {
                return emptyList()
            }
            return list
        }

        private fun parseSingleStation(objStr: String): GeodnetStation? {
            val len = objStr.length
            var name: String? = null
            var status = "ACTIVE"
            var rtcmStationId: Int? = null
            var lat: Double? = null
            var lng: Double? = null

            var i = 0
            while (i < len) {
                if (objStr[i] == '"') {
                    i++
                    val kStart = i
                    while (i < len && objStr[i] != '"') i++
                    val key = objStr.substring(kStart, i)
                    if (i < len) i++ // skip quote

                    while (i < len && (objStr[i] == ' ' || objStr[i] == ':' || objStr[i] == '\t')) i++

                    when (key) {
                        "name" -> {
                            if (i < len && objStr[i] == '"') {
                                i++
                                val vStart = i
                                while (i < len && objStr[i] != '"') i++
                                name = objStr.substring(vStart, i)
                                if (i < len) i++
                            }
                        }
                        "status" -> {
                            if (i < len && objStr[i] == '"') {
                                i++
                                val vStart = i
                                while (i < len && objStr[i] != '"') i++
                                status = objStr.substring(vStart, i)
                                if (i < len) i++
                            }
                        }
                        "stationId", "station_id", "rtcmStationId", "rtcm_station_id", "staId", "sta_id", "rtcmId", "rtcm_id", "id" -> {
                            if (i < len && objStr[i] == '"') {
                                i++
                                val vStart = i
                                while (i < len && objStr[i] != '"') i++
                                rtcmStationId = objStr.substring(vStart, i).toIntOrNull()
                                if (i < len) i++
                            } else {
                                val vStart = i
                                while (i < len && (objStr[i].isDigit() || objStr[i] == '-')) i++
                                rtcmStationId = objStr.substring(vStart, i).toIntOrNull()
                            }
                        }
                        "lat", "latitude" -> {
                            if (i < len && objStr[i] == '"') {
                                i++
                                val vStart = i
                                while (i < len && objStr[i] != '"') i++
                                lat = objStr.substring(vStart, i).toDoubleOrNull()
                                if (i < len) i++
                            } else {
                                val vStart = i
                                while (i < len && (objStr[i].isDigit() || objStr[i] == '-' || objStr[i] == '.' || objStr[i] == 'e' || objStr[i] == 'E' || objStr[i] == '+')) i++
                                lat = objStr.substring(vStart, i).toDoubleOrNull()
                            }
                        }
                        "lng", "lon", "longitude" -> {
                            if (i < len && objStr[i] == '"') {
                                i++
                                val vStart = i
                                while (i < len && objStr[i] != '"') i++
                                lng = objStr.substring(vStart, i).toDoubleOrNull()
                                if (i < len) i++
                            } else {
                                val vStart = i
                                while (i < len && (objStr[i].isDigit() || objStr[i] == '-' || objStr[i] == '.' || objStr[i] == 'e' || objStr[i] == 'E' || objStr[i] == '+')) i++
                                lng = objStr.substring(vStart, i).toDoubleOrNull()
                            }
                        }
                    }
                } else {
                    i++
                }
            }

            if (!name.isNullOrBlank() && lat != null && lng != null) {
                if (rtcmStationId == null) {
                    rtcmStationId = name.takeLast(5).toIntOrNull() ?: name.filter { it.isDigit() }.toIntOrNull()
                }
                return GeodnetStation(
                    name = name,
                    lat = lat,
                    lng = lng,
                    status = status,
                    rtcmStationId = rtcmStationId
                )
            }
            return null
        }

        /**
         * Matches a candidate base station against the active connected base station using
         * BOTH the RTCM station ID and physical ARP coordinates.
         *
         * @param stationLat Latitude of candidate station from Coverage API
         * @param stationLon Longitude of candidate station from Coverage API
         * @param stationRtcmId RTCM Station ID from Coverage API or extracted from station name
         * @param stationName Full station name
         * @param baseLat Active connected base station latitude from RTCM 1005/1006 (or null)
         * @param baseLon Active connected base station longitude from RTCM 1005/1006 (or null)
         * @param activeStaIds List of valid active RTCM Station IDs (from RTCM 1005/1006, MSM header, or GGA NMEA)
         */
        fun isStationMatch(
            stationLat: Double,
            stationLon: Double,
            stationRtcmId: Int?,
            stationName: String,
            baseLat: Double?,
            baseLon: Double?,
            activeStaIds: List<Int>
        ): Boolean {
            val hasBaseCoords = baseLat != null && baseLon != null && (baseLat != 0.0 || baseLon != 0.0)
            val coordDistKm = if (hasBaseCoords) {
                haversineDistanceKm(stationLat, stationLon, baseLat!!, baseLon!!)
            } else null

            val effectiveStId = stationRtcmId
                ?: stationName.takeLast(5).toIntOrNull()
                ?: stationName.filter { it.isDigit() }.toIntOrNull()

            val hasMatchingId = if (effectiveStId != null && effectiveStId > 0 && activeStaIds.isNotEmpty()) {
                activeStaIds.contains(effectiveStId)
            } else false

            // Scenario 1: Both Physical Coordinates AND RTCM Station ID are available
            if (coordDistKm != null && activeStaIds.isNotEmpty()) {
                // If coordinate distance is < 1.0 km AND station ID matches -> 100% Definitive match
                if (coordDistKm < 1.0 && hasMatchingId) return true
                // If coordinates are exact (< 300m) and ID is matching or not explicitly conflicting
                if (coordDistKm < 0.3) return true
                // If station ID matches and distance is very close (< 2.0 km)
                if (hasMatchingId && coordDistKm < 2.0) return true
                return false
            }

            // Scenario 2: Only Physical Coordinates are available
            if (coordDistKm != null) {
                return coordDistKm < 0.5
            }

            // Scenario 3: Only RTCM Station ID is available (e.g. before RTCM 1005 coordinate arrives)
            if (activeStaIds.isNotEmpty()) {
                return hasMatchingId
            }

            return false
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

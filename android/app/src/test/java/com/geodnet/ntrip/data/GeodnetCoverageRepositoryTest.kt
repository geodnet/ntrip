package com.geodnet.ntrip.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeodnetCoverageRepositoryTest {

    @Test
    fun parseStationsJson_validJson_returnsParsedStations() {
        val sampleJson = """
        {
            "code": 0,
            "msg": "success",
            "data": [
                { "name": "****69D0C", "status": "ACTIVE", "lat": 37.399256, "lng": -121.976698 },
                { "name": "****70316", "status": "ONLINE", "lat": 29.947643, "lng": -95.179958 },
                { "name": "****0EC16", "status": "OFFLINE", "lat": 42.344, "lng": -83.386 }
            ]
        }
        """.trimIndent()

        val stations = GeodnetCoverageRepository.parseStationsJson(sampleJson)
        assertEquals(3, stations.size)
        assertEquals("****69D0C", stations[0].name)
        assertEquals("ACTIVE", stations[0].status)
        assertEquals(37.399256, stations[0].lat, 1e-6)
        assertEquals(-121.976698, stations[0].lng, 1e-6)
        assertEquals("ONLINE", stations[1].status)
        assertEquals("OFFLINE", stations[2].status)
    }

    @Test
    fun parseStationsJson_emptyOrInvalid_returnsEmptyList() {
        assertEquals(0, GeodnetCoverageRepository.parseStationsJson("").size)
        assertEquals(0, GeodnetCoverageRepository.parseStationsJson("{ invalid json }").size)
        assertEquals(0, GeodnetCoverageRepository.parseStationsJson("""{ "code": 1 }""").size)
    }

    @Test
    fun haversineDistanceKm_knownPoints_returnsAccurateDistance() {
        val dist = GeodnetCoverageRepository.haversineDistanceKm(37.3541, -121.9552, 37.3382, -121.8863)
        assertTrue(dist > 6.0 && dist < 7.0)

        val zeroDist = GeodnetCoverageRepository.haversineDistanceKm(37.3541, -121.9552, 37.3541, -121.9552)
        assertEquals(0.0, zeroDist, 1e-3)
    }

    @Test
    fun calculateAzimuthDeg_cardinalBearings_returnsAccurateBearing() {
        // Due North from (0,0) to (1,0) should be 0 deg
        val north = GeodnetCoverageRepository.calculateAzimuthDeg(0.0, 0.0, 1.0, 0.0)
        assertEquals(0.0, north, 1e-2)
        assertEquals("N", GeodnetCoverageRepository.azimuthToCardinal(north))

        // Due East from (0,0) to (0,1) should be 90 deg
        val east = GeodnetCoverageRepository.calculateAzimuthDeg(0.0, 0.0, 0.0, 1.0)
        assertEquals(90.0, east, 1e-2)
        assertEquals("E", GeodnetCoverageRepository.azimuthToCardinal(east))

        // Due South from (1,0) to (0,0) should be 180 deg
        val south = GeodnetCoverageRepository.calculateAzimuthDeg(1.0, 0.0, 0.0, 0.0)
        assertEquals(180.0, south, 1e-2)
        assertEquals("S", GeodnetCoverageRepository.azimuthToCardinal(south))

        // Due West from (0,1) to (0,0) should be 270 deg
        val west = GeodnetCoverageRepository.calculateAzimuthDeg(0.0, 1.0, 0.0, 0.0)
        assertEquals(270.0, west, 1e-2)
        assertEquals("W", GeodnetCoverageRepository.azimuthToCardinal(west))
    }

    @Test
    fun nearbyStation_shortName_takesLast5Digits() {
        val st = NearbyStation(
            name = "****69D0C",
            lat = 37.399,
            lng = -121.976,
            distanceKm = 4.21,
            azimuthDeg = 124.5,
            cardinalDirection = "SE",
            isOptimalRtk = true
        )
        assertEquals("69D0C", st.shortName)
    }

    @Test
    fun parseStationsJson_withRtcmStationId_parsesCorrectly() {
        val sampleJson = """
        {
            "code": 0,
            "data": [
                { "name": "GEOD_12345", "status": "ACTIVE", "rtcm_station_id": 12345, "lat": 37.399, "lng": -121.976 },
                { "name": "GEOD_99999", "status": "ACTIVE", "station_id": 99999, "lat": 37.400, "lng": -121.980 }
            ]
        }
        """.trimIndent()

        val stations = GeodnetCoverageRepository.parseStationsJson(sampleJson)
        assertEquals(2, stations.size)
        assertEquals(12345, stations[0].rtcmStationId)
        assertEquals(99999, stations[1].rtcmStationId)
    }

    @Test
    fun isStationMatch_dualCondition_matchesAccurately() {
        // 1. Both RTCM Station ID and Coordinates match (< 1km and same ID) -> TRUE
        val matchBoth = GeodnetCoverageRepository.isStationMatch(
            stationLat = 37.399256,
            stationLon = -121.976698,
            stationRtcmId = 1234,
            stationName = "GEOD_1234",
            baseLat = 37.399300,
            baseLon = -121.976700,
            activeStaIds = listOf(1234)
        )
        assertTrue(matchBoth)

        // 2. Same ID but coordinates 50 km away -> FALSE (prevents false matches across states)
        val mismatchCoords = GeodnetCoverageRepository.isStationMatch(
            stationLat = 37.399256,
            stationLon = -121.976698,
            stationRtcmId = 1234,
            stationName = "GEOD_1234",
            baseLat = 38.000000,
            baseLon = -121.000000,
            activeStaIds = listOf(1234)
        )
        org.junit.Assert.assertFalse(mismatchCoords)

        // 3. Exact coordinates match (< 300m) even if activeStaIds is empty -> TRUE
        val matchCoordsOnly = GeodnetCoverageRepository.isStationMatch(
            stationLat = 37.399256,
            stationLon = -121.976698,
            stationRtcmId = 1234,
            stationName = "GEOD_1234",
            baseLat = 37.399260,
            baseLon = -121.976700,
            activeStaIds = emptyList()
        )
        assertTrue(matchCoordsOnly)

        // 4. Station ID match when coordinates not yet available (0.0, 0.0) -> TRUE
        val matchIdOnly = GeodnetCoverageRepository.isStationMatch(
            stationLat = 37.399256,
            stationLon = -121.976698,
            stationRtcmId = 1234,
            stationName = "GEOD_1234",
            baseLat = 0.0,
            baseLon = 0.0,
            activeStaIds = listOf(1234)
        )
        assertTrue(matchIdOnly)
    }
}

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
}

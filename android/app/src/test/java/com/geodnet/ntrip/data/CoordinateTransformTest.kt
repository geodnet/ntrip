package com.geodnet.ntrip.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class CoordinateTransformTest {

    @Test
    fun testPosToEcefAndBack() {
        val lat = 37.774929
        val lon = -122.419416
        val h = 30.5

        val ecef = CoordinateTransform.posToEcef(lat, lon, h)
        val pos = CoordinateTransform.ecefToPos(ecef[0], ecef[1], ecef[2])

        assertEquals(lat, pos.latDeg, 1e-8)
        assertEquals(lon, pos.lonDeg, 1e-8)
        assertEquals(h, pos.heightM, 1e-4)
    }

    @Test
    fun testNad83ToItrf2020Transformation() {
        // Point in USA (San Francisco, CA)
        val latNad83 = 37.774929
        val lonNad83 = -122.419416
        val hNad83 = 30.0

        val itrf2020Pos = CoordinateTransform.convertNad2011ToItrf2020(latNad83, lonNad83, hNad83)

        // The shift between NAD83(2011)(2010.0) and ITRF2020(2020.0) in CONUS is ~1.0 to 1.5 meters horizontally
        val dLatM = (itrf2020Pos.latDeg - latNad83) * 111320.0
        val dLonM = (itrf2020Pos.lonDeg - lonNad83) * 111320.0 * cos(Math.toRadians(latNad83))
        val horizontalShiftM = kotlin.math.sqrt(dLatM * dLatM + dLonM * dLonM)

        assertTrue("Horizontal shift should be ~1.2m-1.5m, got $horizontalShiftM m", horizontalShiftM in 0.8..2.0)
    }

    @Test
    fun testEtrf2000ToItrf2020Transformation() {
        // Point in Europe (Paris, France: 48.8566 N, 2.3522 E)
        val latEtrf = 48.8566
        val lonEtrf = 2.3522
        val hEtrf = 60.0

        val itrfPos = CoordinateTransform.convertEtrf2000ToItrf2020(latEtrf, lonEtrf, hEtrf)

        // ETRF2000(2010.0) vs ITRF2020(2020.0) shift in Western Europe is ~0.4 to 0.9 meters
        val dLatM = (itrfPos.latDeg - latEtrf) * 111320.0
        val dLonM = (itrfPos.lonDeg - lonEtrf) * 111320.0 * cos(Math.toRadians(latEtrf))
        val horizontalShiftM = kotlin.math.sqrt(dLatM * dLatM + dLonM * dLonM)

        assertTrue("EU horizontal shift should be ~0.4m-1.0m, got $horizontalShiftM m", horizontalShiftM in 0.3..1.2)
    }

    @Test
    fun testGda2020AndGda94ToItrf2020Transformation() {
        // Point in Australia (Sydney, NSW: -33.8688 S, 151.2093 E)
        val latGda94 = -33.8688
        val lonGda94 = 151.2093
        val hGda94 = 25.0

        val itrfFromGda94 = CoordinateTransform.convertGda94ToItrf2020(latGda94, lonGda94, hGda94)

        // GDA94(1994.0) vs ITRF2020(2020.0) shift in Australia is ~1.5 to 2.0 meters due to 7cm/yr plate motion
        val dLatM = (itrfFromGda94.latDeg - latGda94) * 111320.0
        val dLonM = (itrfFromGda94.lonDeg - lonGda94) * 111320.0 * cos(Math.toRadians(latGda94))
        val horizontalShiftM = kotlin.math.sqrt(dLatM * dLatM + dLonM * dLonM)

        assertTrue("AUS GDA94 shift should be ~1.5m-2.0m, got $horizontalShiftM m", horizontalShiftM in 1.2..2.2)
    }

    @Test
    fun testNzgd2000ToItrf2020Transformation() {
        // Point in New Zealand (Wellington: -41.2865 S, 174.7762 E)
        val latNzgd = -41.2865
        val lonNzgd = 174.7762
        val hNzgd = 30.0

        val itrfPos = CoordinateTransform.convertNzgd2000ToItrf2020(latNzgd, lonNzgd, hNzgd)

        // NZGD2000(2000.0) vs ITRF2020(2020.0) shift in NZ
        val dLatM = (itrfPos.latDeg - latNzgd) * 111320.0
        val dLonM = (itrfPos.lonDeg - lonNzgd) * 111320.0 * cos(Math.toRadians(latNzgd))
        val horizontalShiftM = kotlin.math.sqrt(dLatM * dLatM + dLonM * dLonM)

        assertTrue("NZ shift should be > 0.01m, got $horizontalShiftM m", horizontalShiftM > 0.01)
    }

    @Test
    fun testSirgas2000ToItrf2020Transformation() {
        // Point in South America (São Paulo, Brazil: -23.5505 S, -46.6333 W)
        val latSirgas = -23.5505
        val lonSirgas = -46.6333
        val hSirgas = 760.0

        val itrfPos = CoordinateTransform.convertSirgas2000ToItrf2020(latSirgas, lonSirgas, hSirgas)

        val dLatM = (itrfPos.latDeg - latSirgas) * 111320.0
        val dLonM = (itrfPos.lonDeg - lonSirgas) * 111320.0 * cos(Math.toRadians(latSirgas))
        val horizontalShiftM = kotlin.math.sqrt(dLatM * dLatM + dLonM * dLonM)

        assertTrue("SA SIRGAS shift should be > 0.01m, got $horizontalShiftM m", horizontalShiftM > 0.01)
    }

    @Test
    fun testTransformForMapDisplay() {
        val lat = 34.052235
        val lon = -118.243683
        val h = 50.0

        // In NAD83(2011), should transform to WGS84/ITRF2020
        val transformedNad = CoordinateTransform.transformForMapDisplay(lat, lon, h, "NAD83(2011)")
        assertTrue(transformedNad.latDeg != lat)
        assertTrue(transformedNad.lonDeg != lon)

        // In EU ETRS89, should transform to WGS84/ITRF2020
        val transformedEtrf = CoordinateTransform.transformForMapDisplay(48.85, 2.35, 50.0, "ETRS89(ETRF2000)")
        assertTrue(transformedEtrf.latDeg != 48.85)

        // In AUS GDA94, should transform to WGS84/ITRF2020
        val transformedGda = CoordinateTransform.transformForMapDisplay(-33.86, 151.20, 50.0, "GDA94")
        assertTrue(transformedGda.latDeg != -33.86)

        // In NZ NZGD2000, should transform to WGS84/ITRF2020
        val transformedNz = CoordinateTransform.transformForMapDisplay(-41.28, 174.77, 50.0, "NZGD2000")
        assertTrue(transformedNz.latDeg != -41.28)

        // In SA SIRGAS2000, should transform to WGS84/ITRF2020
        val transformedSirgas = CoordinateTransform.transformForMapDisplay(-23.55, -46.63, 50.0, "SIRGAS2000 = ITRF2000")
        assertTrue(transformedSirgas.latDeg != -23.55)

        // In WGS84, should remain identical
        val unchanged = CoordinateTransform.transformForMapDisplay(lat, lon, h, "WGS84(G2139)")
        assertEquals(lat, unchanged.latDeg, 1e-9)
        assertEquals(lon, unchanged.lonDeg, 1e-9)
    }
}

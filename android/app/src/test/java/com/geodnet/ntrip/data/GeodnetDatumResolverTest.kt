package com.geodnet.ntrip.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeodnetDatumResolverTest {

    @Test
    fun resolvesExplicitMountpoints() {
        assertEquals("ITRF2020", GeodnetDatumResolver.resolve("AUTO_ITRF2020").name)
        assertEquals("ITRF2014", GeodnetDatumResolver.resolve("AUTO_ITRF2014").name)
        assertEquals("WGS84(G2139)", GeodnetDatumResolver.resolve("AUTO_WGS84").name)
        assertEquals("NATRF2020", GeodnetDatumResolver.resolve("NATRF2020").name)
        assertEquals("SIRGAS2000", GeodnetDatumResolver.resolve("SIRGAS2000(2026.5)").name)
        assertEquals("Broadcast Ephemeris", GeodnetDatumResolver.resolve("BRDC").name)
    }

    @Test
    fun resolvesAutoMountpointByGeographicLocation() {
        // San Jose, California USA
        val usa = GeodnetDatumResolver.resolve("AUTO", "rtk.geodnet.com", 37.3382, -121.8863)
        assertEquals("NAD83(2011)", usa.name)
        assertEquals("2010.0", usa.epoch)

        // Honolulu, Hawaii USA
        val hawaii = GeodnetDatumResolver.resolve("AUTO", "rtk.geodnet.com", 21.3069, -157.8583)
        assertEquals("NAD83(PA11)", hawaii.name)

        // Toronto, Canada
        val canada = GeodnetDatumResolver.resolve("AUTO", "rtk.geodnet.com", 43.6532, -79.3832)
        assertEquals("NAD83(CSRS)v7", canada.name)

        // Berlin, Germany (Europe)
        val europe = GeodnetDatumResolver.resolve("AUTO", "rtk.geodnet.com", 52.5200, 13.4050)
        assertEquals("ETRS89(ETRF2000)", europe.name)

        // Sydney, Australia
        val aus = GeodnetDatumResolver.resolve("AUTO", "rtk.geodnet.com", -33.8688, 151.2093)
        assertEquals("GDA2020", aus.name)

        // Tokyo, Japan
        val jpn = GeodnetDatumResolver.resolve("AUTO", "rtk.geodnet.com", 35.6762, 139.6503)
        assertTrue(jpn.name.startsWith("JGD2011"))

        // Seoul, South Korea
        val kor = GeodnetDatumResolver.resolve("AUTO", "rtk.geodnet.com", 37.5665, 126.9780)
        assertTrue(kor.name.startsWith("KGD2002"))

        // Taipei, Taiwan
        val twn = GeodnetDatumResolver.resolve("AUTO", "rtk.geodnet.com", 25.0330, 121.5654)
        assertEquals("ITRF2020", twn.name)

        // São Paulo, Brazil (South America)
        val sam = GeodnetDatumResolver.resolve("AUTO", "rtk.geodnet.com", -23.5505, -46.6333)
        assertTrue(sam.name.startsWith("SIRGAS2000"))
    }
}

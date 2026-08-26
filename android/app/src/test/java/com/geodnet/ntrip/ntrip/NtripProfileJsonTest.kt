package com.geodnet.ntrip.ntrip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NtripProfileJsonTest {

    private val sampleConfig = NtripConfig(
        host = "rtk.geodnet.com",
        port = 2101,
        mountpoint = "AUTO",
        username = "user1",
        password = "secret",
        latitude = 37.5,
        longitude = -122.25,
        altitude = 12.5,
        numSatellites = 18,
        hdop = 0.7,
        ggaIntervalMs = 3000L,
    )

    @Test
    fun `round-trips a list of profiles through serialize and parse`() {
        val profiles = listOf(
            NtripProfile(id = "a", name = "Home", config = sampleConfig),
            NtripProfile(id = "b", name = "Site B", config = sampleConfig.copy(host = "other.host", port = 2102)),
        )

        val json = NtripProfileJson.serialize(profiles)
        val parsed = NtripProfileJson.parse(json)

        assertEquals(profiles, parsed)
    }

    @Test
    fun `parse of null or empty string returns an empty list`() {
        assertEquals(emptyList<NtripProfile>(), NtripProfileJson.parse(null))
        assertEquals(emptyList<NtripProfile>(), NtripProfileJson.parse(""))
    }

    @Test
    fun `parse of malformed json returns an empty list rather than throwing`() {
        assertEquals(emptyList<NtripProfile>(), NtripProfileJson.parse("not json"))
        assertEquals(emptyList<NtripProfile>(), NtripProfileJson.parse("[{\"id\":\"a\"}]")) // missing "name"
    }

    @Test
    fun `serialize of an empty list produces a valid empty json array`() {
        val json = NtripProfileJson.serialize(emptyList())
        assertTrue(NtripProfileJson.parse(json).isEmpty())
        assertEquals("[]", json)
    }

    @Test
    fun `missing optional fields fall back to NtripConfig defaults`() {
        val defaults = NtripConfig()
        val json = "[{\"id\":\"a\",\"name\":\"Minimal\"}]"

        val parsed = NtripProfileJson.parse(json)

        assertEquals(1, parsed.size)
        assertEquals(defaults, parsed[0].config)
    }
}

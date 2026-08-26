package com.geodnet.ntrip.logging

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class LogPathsTest {

    @Test
    fun `gpsNow is 18 seconds ahead of UTC`() {
        val utcNow = Instant.now()
        val gps = LogPaths.gpsNow()
        val deltaSeconds = gps.epochSecond - utcNow.epochSecond
        assertEquals(18L, deltaSeconds)
    }

    @Test
    fun `dateFolder and timestampPrefix format a known instant correctly`() {
        // 2024-01-02T03:04:05Z, already expressed as a GPS-time instant (no offset applied here).
        val instant = Instant.parse("2024-01-02T03:04:05Z")
        assertEquals("2024-01-02", LogPaths.dateFolder(instant))
        assertEquals("2024-01-02-03-04-05", LogPaths.timestampPrefix(instant))
    }

    @Test
    fun `sanitizeForFilename strips unsafe characters`() {
        assertEquals("AUTO", LogPaths.sanitizeForFilename("AUTO"))
        assertEquals("AA_BB_CC_DD_EE_FF", LogPaths.sanitizeForFilename("AA:BB:CC:DD:EE:FF"))
        assertEquals("a_b_c", LogPaths.sanitizeForFilename("a/b\\c"))
    }

    @Test
    fun `sanitizeForFilename falls back to unknown only for an empty string`() {
        assertEquals("unknown", LogPaths.sanitizeForFilename(""))
        // Unsafe characters are replaced, not stripped -- "///" becomes underscores, not empty.
        assertEquals("___", LogPaths.sanitizeForFilename("///"))
    }
}

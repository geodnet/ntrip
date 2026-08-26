package com.geodnet.ntrip.ntrip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NtripSourcetableTest {

    @Test
    fun parse_validSourcetable_returnsParsedStreams() {
        val sample = """
            SOURCETABLE 200 OK
            Server: NTRIP Caster 2.0
            Content-Type: text/plain
            Connection: close

            NET;GEODNET;GEODNET Global Network;N;N;https://geodnet.com;info@geodnet.com;misc
            STR;AUTO;AUTO;RTCM 3.3;1005(5),1077(1),1087(1),1097(1),1127(1);2;GPS+GLO+GAL+BDS;GEODNET;USA;37.40;-121.98;1;2;sgl_caster;none;B;N;9600;misc
            STR;AUTO_ITRF2020;AUTO ITRF2020;RTCM 3.3;1005(5),1077(1),1087(1),1097(1),1127(1);2;GPS+GLO+GAL+BDS;GEODNET;USA;37.40;-121.98;1;2;sgl_caster;none;B;N;9600;misc
            STR;69D0C;San Jose Station;RTCM 3.2;1005(5),1074(1),1084(1);2;GPS+GLO;GEODNET;USA;37.399256;-121.976698;0;1;rover_box;none;B;N;9600;misc
            CAS;rtk.geodnet.com;2101;GEODNET Caster;GEODNET;0;USA;37.40;-121.98;0;0
            ENDSOURCETABLE
        """.trimIndent()

        val table = NtripSourcetable.parse(sample)
        assertEquals(3, table.streams.size)
        assertEquals(1, table.networks.size)
        assertEquals(1, table.casters.size)

        val auto = table.streams[0]
        assertEquals("AUTO", auto.mountpoint)
        assertEquals("RTCM 3.3", auto.format)
        assertEquals("GPS+GLO+GAL+BDS", auto.navSystem)
        assertEquals(37.40, auto.latitude ?: 0.0, 1e-4)
        assertEquals(-121.98, auto.longitude ?: 0.0, 1e-4)
        assertTrue(auto.nmea)

        val st69 = table.streams[2]
        assertEquals("69D0C", st69.mountpoint)
        assertEquals("San Jose Station", st69.identifier)
        assertEquals("RTCM 3.2", st69.format)
        assertFalse(st69.nmea)
    }

    @Test
    fun parse_emptyOrInvalid_returnsEmptySourcetable() {
        val empty = NtripSourcetable.parse("")
        assertEquals(0, empty.streams.size)

        val invalid = NtripSourcetable.parse("SOME RANDOM STRING")
        assertEquals(0, invalid.streams.size)
    }
}

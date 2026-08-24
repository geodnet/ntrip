package com.geodnet.ntrip.rtcm

/**
 * MSM (Multiple Signal Message) signal mask bit -> RTCM/RINEX signal code, indexed by signal ID
 * 1-32 (list index = ID-1); "" means undefined/reserved. Ported from node/ntrip_client.js's
 * MSM_SIG_* tables, which were cross-checked against RTKLIB's rtcm3.c (including the updated
 * BeiDou/NavIC signal IDs -- see node/CLAUDE.md for why those specific values matter).
 */
object MsmSignalTables {
    val gps = listOf("", "1C", "1P", "1W", "", "", "", "2C", "2P", "2W", "", "", "", "", "2S", "2L", "2X", "", "", "", "", "5I", "5Q", "5X", "", "", "", "", "", "1S", "1L", "1X")
    val glonass = listOf("", "1C", "1P", "", "", "", "", "2C", "2P", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "")
    val galileo = listOf("", "1C", "1A", "1B", "1X", "1Z", "", "6C", "6A", "6B", "6X", "6Z", "", "7I", "7Q", "7X", "", "8I", "8Q", "8X", "", "5I", "5Q", "5X", "", "", "", "", "", "", "", "")
    val qzss = listOf("", "1C", "", "", "", "", "", "", "6S", "6L", "6X", "", "", "", "2S", "2L", "2X", "", "", "", "", "5I", "5Q", "5X", "", "", "", "", "", "1S", "1L", "1X")
    val sbas = listOf("", "1C", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "5I", "5Q", "5X", "", "", "", "", "", "", "", "")
    val beidou = listOf("", "2I", "2Q", "2X", "", "", "", "6I", "6Q", "6X", "", "", "", "7I", "7Q", "7X", "", "", "", "", "", "5D", "5P", "5X", "7D", "", "", "", "", "1D", "1P", "1X")
    val navic = listOf("", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "5A", "", "", "", "", "", "", "", "", "", "")
}

data class MsmSystem(val name: String, val sigTable: List<String>?)

private val msmSystemByBase = mapOf(
    107 to MsmSystem("GPS", MsmSignalTables.gps),
    108 to MsmSystem("GLONASS", MsmSignalTables.glonass),
    109 to MsmSystem("Galileo", MsmSignalTables.galileo),
    110 to MsmSystem("SBAS", MsmSignalTables.sbas),
    111 to MsmSystem("QZSS", MsmSignalTables.qzss),
    112 to MsmSystem("BeiDou", MsmSignalTables.beidou),
    113 to MsmSystem("NavIC/IRNSS", MsmSignalTables.navic),
)

/** MSM types are <base>1..7 (e.g. 1071-1077 = GPS MSM1-7); base = type / 10. */
fun getMsmSystem(msgType: Int): MsmSystem? {
    val base = msgType / 10
    val ordinal = msgType % 10
    if (ordinal !in 1..7) return null
    return msmSystemByBase[base]
}

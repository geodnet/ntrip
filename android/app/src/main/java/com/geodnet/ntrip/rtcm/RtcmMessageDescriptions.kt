package com.geodnet.ntrip.rtcm

/**
 * Human-readable descriptions for RTCM3 message types, ported from
 * node/ntrip_client.js's RTCM_MSG_DESCRIPTIONS / SSR / IGS SSR tables. Keep in sync with that
 * file if the numbering ever needs correcting -- see node/CLAUDE.md and node/doc/igs_ssr_v1.pdf
 * for where the IGS SSR sub-type numbers came from.
 */
object RtcmMessageDescriptions {

    private val baseDescriptions: MutableMap<Int, String> = mutableMapOf(
        1001 to "GPS L1 RTK observables",
        1002 to "GPS L1 RTK observables (extended)",
        1003 to "GPS L1/L2 RTK observables",
        1004 to "GPS L1/L2 RTK observables (extended)",
        1005 to "Stationary RTK reference station ARP",
        1006 to "Stationary RTK reference station ARP with antenna height",
        1007 to "Antenna descriptor",
        1008 to "Antenna descriptor and serial number",
        1009 to "GLONASS L1 RTK observables",
        1010 to "GLONASS L1 RTK observables (extended)",
        1011 to "GLONASS L1/L2 RTK observables",
        1012 to "GLONASS L1/L2 RTK observables (extended)",
        1013 to "System parameters",
        1019 to "GPS ephemeris",
        1020 to "GLONASS ephemeris",
        1029 to "Unicode text string",
        1033 to "Receiver and antenna descriptors",
        1041 to "NavIC/IRNSS ephemeris",
        1042 to "BeiDou ephemeris",
        1044 to "QZSS ephemeris",
        1045 to "Galileo F/NAV ephemeris",
        1046 to "Galileo I/NAV ephemeris",
        1230 to "GLONASS code-phase biases",
    )

    // RTCM SSR message blocks: each GNSS gets 6 consecutive types
    // (orbit, clock, code bias, combined orbit+clock, URA, high-rate clock)
    private val ssrKindByOffset = listOf(
        "Orbit Correction",
        "Clock Correction",
        "Code Bias",
        "Combined Orbit and Clock Correction",
        "URA",
        "High Rate Clock Correction",
    )
    private val ssrSystemBlocks = listOf(
        1057 to "GPS",
        1063 to "GLONASS",
        1240 to "Galileo",
        1246 to "QZSS",
        1252 to "SBAS",
        1258 to "BeiDou",
    )

    // MSM message families: type = <base>1..7, e.g. 1071-1077 = GPS MSM1-7
    private val msmSystemByBase = mapOf(
        107 to "GPS",
        108 to "GLONASS",
        109 to "Galileo",
        110 to "SBAS",
        111 to "QZSS",
        112 to "BeiDou",
        113 to "NavIC/IRNSS",
    )

    // IGS SSR sub-type message numbers (IDF002) carried inside RTCM message 4076, per the IGS
    // State Space Representation (SSR) format v1.00, Table 5. Each GNSS gets a block of 7
    // consecutive numbers: Orbit, Clock, Combined Orbit+Clock, High Rate Clock, Code Bias,
    // Phase Bias, URA.
    private val igsSsrKindByOffset = listOf(
        "Orbit Correction",
        "Clock Correction",
        "Combined Orbit and Clock Correction",
        "High Rate Clock Correction",
        "Code Bias",
        "Phase Bias",
        "URA",
    )
    private val igsSsrSystemBlocks = listOf(
        21 to "GPS",
        41 to "GLONASS",
        61 to "Galileo",
        81 to "QZSS",
        101 to "BeiDou",
        121 to "SBAS",
    )
    private val igsSsrSubtypes: MutableMap<Int, String> = mutableMapOf(
        201 to "Ionosphere VTEC Spherical Harmonics",
    )

    init {
        for ((base, system) in ssrSystemBlocks) {
            ssrKindByOffset.forEachIndexed { offset, kind ->
                baseDescriptions[base + offset] = "$system SSR $kind"
            }
        }
        listOf("GPS", "GLONASS", "Galileo", "QZSS", "SBAS", "BeiDou").forEachIndexed { offset, system ->
            baseDescriptions[1265 + offset] = "$system SSR Phase Bias"
        }
        for ((base, system) in igsSsrSystemBlocks) {
            igsSsrKindByOffset.forEachIndexed { offset, kind ->
                igsSsrSubtypes[base + offset] = "$system SSR $kind"
            }
        }
    }

    /** msgKey is either "<type>" or "<type>.<subtype>" (see RtcmFrameParser). */
    fun describe(msgKey: String): String {
        val parts = msgKey.split(".")
        val msgType = parts[0].toIntOrNull() ?: return "Unknown"
        val subType = parts.getOrNull(1)?.toIntOrNull()

        if (msgType == 4076 && subType != null) {
            return "IGS SSR ${igsSsrSubtypes[subType] ?: "(unknown subtype $subType)"}"
        }
        baseDescriptions[msgType]?.let { return it }
        val msmBase = msgType / 10
        val msmOrdinal = msgType % 10
        msmSystemByBase[msmBase]?.let { system ->
            if (msmOrdinal in 1..7) return "$system MSM$msmOrdinal"
        }
        if (msgType in 4001..4095) return "Proprietary message"
        return "Unknown"
    }
}

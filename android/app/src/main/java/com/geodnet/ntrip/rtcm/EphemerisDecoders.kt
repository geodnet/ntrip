package com.geodnet.ntrip.rtcm

/** Decoded ephemeris fields: system, PRN, satellite health, and TOE as a UTC epoch ms. */
data class EphemerisInfo(val sys: String, val prn: Int, val svh: Int, val toeMs: Long)

/**
 * Ephemeris message decoders (1019/1020/1042/1044/1045/1046), ported from
 * node/ntrip_client.js's decodeEph1019/decodeEph1044/decodeEphGalileo/decodeEph1042/decodeEph1020.
 * Bit layouts were cross-checked against RTKLIB's rtcm3.c; if you need to touch these, re-check
 * against that reference rather than the RTCM spec text alone.
 */
object EphemerisDecoders {

    fun decode1019(payload: ByteArray, nowMs: Long): EphemerisInfo {
        val br = RtcmBitReader(payload)
        br.skip(12) // message number
        var prn = br.readUnsigned(6).toInt()
        val week10 = br.readUnsigned(10).toInt()
        br.skip(4 + 2 + 14 + 8) // sva, code, idot, iode
        br.skip(16) // toc
        br.skip(8 + 16 + 22) // f2, f1, f0
        br.skip(10) // iodc
        br.skip(16 + 16 + 32 + 16 + 32 + 16 + 32) // crs, deln, M0, cuc, e, cus, sqrtA
        val toes = br.readUnsigned(16) * 16.0
        br.skip(16 + 32 + 16 + 32 + 16 + 32 + 24) // cic, OMG0, cis, i0, crc, omg, OMGd
        br.skip(8) // tgd
        val svh = br.readUnsigned(6).toInt()

        var sys = "GPS"
        if (prn >= 40) {
            sys = "SBAS"
            prn += 80
        }
        val week = EphemerisTime.resolveWeek(week10, 10, EphemerisTime.GPS_EPOCH_MS, nowMs)
        return EphemerisInfo(sys, prn, svh, EphemerisTime.toeFromGpsWeek(week, toes))
    }

    fun decode1044(payload: ByteArray, nowMs: Long): EphemerisInfo {
        val br = RtcmBitReader(payload)
        br.skip(12)
        val prn = br.readUnsigned(4).toInt() + 192
        br.skip(16) // toc
        br.skip(8 + 16 + 22) // f2, f1, f0
        br.skip(8) // iode
        br.skip(16 + 16 + 32 + 16 + 32 + 16 + 32) // crs, deln, M0, cuc, e, cus, sqrtA
        val toes = br.readUnsigned(16) * 16.0
        br.skip(16 + 32 + 16 + 32 + 16 + 32 + 24) // cic, OMG0, cis, i0, crc, omg, OMGd
        br.skip(14 + 2) // idot, code
        val week10 = br.readUnsigned(10).toInt()
        br.skip(4) // sva
        val svh = br.readUnsigned(6).toInt()

        val week = EphemerisTime.resolveWeek(week10, 10, EphemerisTime.GPS_EPOCH_MS, nowMs)
        return EphemerisInfo("QZSS", prn, svh, EphemerisTime.toeFromGpsWeek(week, toes))
    }

    fun decodeGalileo(payload: ByteArray, isInav: Boolean): EphemerisInfo {
        val br = RtcmBitReader(payload)
        br.skip(12)
        val prn = br.readUnsigned(6).toInt()
        val gstWeek = br.readUnsigned(12).toInt()
        br.skip(10) // iode
        br.skip(8) // sva
        br.skip(14) // idot
        br.skip(14) // toc
        br.skip(6 + 21 + 31) // f2, f1, f0
        br.skip(16 + 16 + 32 + 16 + 32 + 16 + 32) // crs, deln, M0, cuc, e, cus, sqrtA
        val toes = br.readUnsigned(14) * 60.0
        br.skip(16 + 32 + 16 + 32 + 16 + 32 + 24) // cic, OMG0, cis, i0, crc, omg, OMGd
        val svh: Int
        if (isInav) {
            br.skip(10 + 10) // tgd e5a/e1, tgd e5b/e1
            val e5bHs = br.readUnsigned(2).toInt()
            val e5bDvs = br.readUnsigned(1).toInt()
            val e1Hs = br.readUnsigned(2).toInt()
            val e1Dvs = br.readUnsigned(1).toInt()
            svh = (e5bHs shl 7) or (e5bDvs shl 6) or (e1Hs shl 1) or e1Dvs
        } else {
            br.skip(10) // tgd e5a/e1
            val e5aHs = br.readUnsigned(2).toInt()
            val e5aDvs = br.readUnsigned(1).toInt()
            svh = (e5aHs shl 4) or (e5aDvs shl 3)
        }

        val gpsEquivWeek = (gstWeek + 1024).toLong() // Galileo week 0 = GPS week 1024
        return EphemerisInfo("Galileo", prn, svh, EphemerisTime.toeFromGpsWeek(gpsEquivWeek, toes))
    }

    fun decode1042(payload: ByteArray): EphemerisInfo {
        val br = RtcmBitReader(payload)
        br.skip(12)
        val prn = br.readUnsigned(6).toInt()
        val week = br.readUnsigned(13)
        br.skip(4) // sva
        br.skip(14) // idot
        br.skip(5) // iode (AODE)
        br.skip(17) // toc
        br.skip(11 + 22 + 24) // f2, f1, f0
        br.skip(5) // iodc (AODC)
        br.skip(18 + 16 + 32 + 18 + 32 + 18 + 32) // crs, deln, M0, cuc, e, cus, sqrtA
        val toes = br.readUnsigned(17) * 8.0
        br.skip(18 + 32 + 18 + 32 + 18 + 32 + 24) // cic, OMG0, cis, i0, crc, omg, OMGd
        br.skip(10 + 10) // tgd0, tgd1
        val svh = br.readUnsigned(1).toInt()

        return EphemerisInfo("BeiDou", prn, svh, EphemerisTime.toeFromBdtWeek(week, toes))
    }

    fun decode1020(payload: ByteArray, nowMs: Long): EphemerisInfo {
        val br = RtcmBitReader(payload)
        br.skip(12)
        val prn = br.readUnsigned(6).toInt()
        br.skip(5 + 2 + 2) // frq, almanac health avail, P1
        br.skip(5 + 6 + 1) // tk: hours, minutes, 30s flag
        val svh = br.readUnsigned(1).toInt() // bn: satellite health (1 = unhealthy)
        br.skip(1) // reserved
        val tb = br.readUnsigned(7).toInt()

        return EphemerisInfo("GLONASS", prn, svh, EphemerisTime.toeFromGlonassTb(tb, nowMs))
    }
}

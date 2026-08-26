package com.geodnet.ntrip.data

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-precision geodetic datum and coordinate transformation engine ported from c:\dev\coordinate\coord.c / coord.h.
 *
 * Implements 14-parameter time-dependent Helmert coordinate transformations between regional datums
 * (USA: NAD83(2011), NAD83(PA11), NAD83(MA11); Europe: ETRS89/ETRF2000; Australia: GDA2020/GDA94;
 * New Zealand: NZGD2000; South America: SIRGAS2000; South Africa: ITRF1991; Asia: ITRF2014/ITRF2008/CGCS2000/TUREF)
 * and global WGS84 / ITRF2020 for map visualization.
 */
object CoordinateTransform {

    const val PI: Double = 3.1415926535897932
    const val D2R: Double = PI / 180.0
    const val R2D: Double = 180.0 / PI

    const val RE_WGS84: Double = 6378137.0
    const val FE_WGS84: Double = 1.0 / 298.257223563

    const val RE_GRS80: Double = 6378137.0
    const val FE_GRS80: Double = 1.0 / 298.257222101

    const val MAS2R: Double = 0.001 / 3600.0 * D2R

    data class Point3D(val latDeg: Double, val lonDeg: Double, val heightM: Double)

    /**
     * Converts Geodetic Coordinates (lat, lon, height) to Earth-Centered Earth-Fixed (ECEF) XYZ in meters.
     * Ported from pos2ecef_ in coord.c.
     */
    fun posToEcef(
        latDeg: Double,
        lonDeg: Double,
        heightM: Double,
        a: Double = RE_GRS80,
        f: Double = FE_GRS80
    ): DoubleArray {
        val latRad = latDeg * D2R
        val lonRad = lonDeg * D2R
        val sinp = sin(latRad)
        val cosp = cos(latRad)
        val sinl = sin(lonRad)
        val cosl = cos(lonRad)
        val e2 = f * (2.0 - f)
        val v = a / sqrt(1.0 - e2 * sinp * sinp)

        val x = (v + heightM) * cosp * cosl
        val y = (v + heightM) * cosp * sinl
        val z = (v * (1.0 - e2) + heightM) * sinp
        return doubleArrayOf(x, y, z)
    }

    /**
     * Converts Earth-Centered Earth-Fixed (ECEF) XYZ in meters to Geodetic Coordinates (latDeg, lonDeg, heightM).
     * Ported from ecef2pos_ in coord.c.
     */
    fun ecefToPos(
        x: Double,
        y: Double,
        z: Double,
        a: Double = RE_GRS80,
        f: Double = FE_GRS80
    ): Point3D {
        val r = doubleArrayOf(x, y, z)
        val e2 = f * (2.0 - f)
        val r2 = r[0] * r[0] + r[1] * r[1]
        var currentZ = r[2]
        var zk = 0.0
        var v = a

        while (abs(currentZ - zk) >= 1e-4) {
            zk = currentZ
            val sinp = currentZ / sqrt(r2 + currentZ * currentZ)
            v = a / sqrt(1.0 - e2 * sinp * sinp)
            currentZ = r[2] + v * e2 * sinp
        }

        val latRad = if (r2 > 1e-12) atan(currentZ / sqrt(r2)) else if (r[2] > 0.0) PI / 2.0 else -PI / 2.0
        val lonRad = if (r2 > 1e-12) atan2(r[1], r[0]) else 0.0
        val heightM = sqrt(r2 + currentZ * currentZ) - v

        return Point3D(latDeg = latRad * R2D, lonDeg = lonRad * R2D, heightM = heightM)
    }

    /**
     * 7-parameter Helmert coordinate transformation at epoch.
     * Ported from coordinate_transformation_to in coord.c.
     */
    fun coordinateTransformationTo(
        xyzSrc: DoubleArray,
        vxyzSrc: DoubleArray,
        t: DoubleArray,
        r: DoubleArray,
        d: Double,
        vt: DoubleArray,
        vr: DoubleArray,
        vd: Double
    ): Pair<DoubleArray, DoubleArray> {
        val xyzTo = DoubleArray(3)
        val vxyzTo = DoubleArray(3)

        xyzTo[0] = xyzSrc[0] + t[0] + d * xyzSrc[0] - r[2] * xyzSrc[1] + r[1] * xyzSrc[2]
        xyzTo[1] = xyzSrc[1] + t[1] + d * xyzSrc[1] + r[2] * xyzSrc[0] - r[0] * xyzSrc[2]
        xyzTo[2] = xyzSrc[2] + t[2] + d * xyzSrc[2] - r[1] * xyzSrc[0] + r[0] * xyzSrc[1]

        vxyzTo[0] = vxyzSrc[0] + vt[0] + vd * xyzSrc[0] - vr[2] * xyzSrc[1] + vr[1] * xyzSrc[2]
        vxyzTo[1] = vxyzSrc[1] + vt[1] + vd * xyzSrc[1] + vr[2] * xyzSrc[0] - vr[0] * xyzSrc[2]
        vxyzTo[2] = vxyzSrc[2] + vt[2] + vd * xyzSrc[2] - vr[1] * xyzSrc[0] + vr[0] * xyzSrc[1]

        return Pair(xyzTo, vxyzTo)
    }

    private data class TransformationParams(
        val t: DoubleArray,
        val r: DoubleArray,
        val d: Double,
        val vt: DoubleArray,
        val vr: DoubleArray,
        val vd: Double
    )

    /**
     * Helper to perform Helmert transformation with inverted parameters and velocity propagation.
     */
    private fun transformHelmert(
        latDeg: Double,
        lonDeg: Double,
        heightM: Double,
        srcEpoch: Double,
        targetEpoch: Double,
        getParams: (Double) -> TransformationParams
    ): Point3D {
        val xyzSrc = posToEcef(latDeg, lonDeg, heightM, a = RE_GRS80, f = FE_GRS80)
        val vxyzSrc = DoubleArray(3)

        val params = getParams(srcEpoch)

        val invT = doubleArrayOf(-params.t[0], -params.t[1], -params.t[2])
        val invR = doubleArrayOf(-params.r[0], -params.r[1], -params.r[2])
        val invD = -params.d
        val invVt = doubleArrayOf(-params.vt[0], -params.vt[1], -params.vt[2])
        val invVr = doubleArrayOf(-params.vr[0], -params.vr[1], -params.vr[2])
        val invVd = -params.vd

        val (xyzTo, vxyzTo) = coordinateTransformationTo(
            xyzSrc = xyzSrc,
            vxyzSrc = vxyzSrc,
            t = invT,
            r = invR,
            d = invD,
            vt = invVt,
            vr = invVr,
            vd = invVd
        )

        val dt = targetEpoch - srcEpoch
        xyzTo[0] += vxyzTo[0] * dt
        xyzTo[1] += vxyzTo[1] * dt
        xyzTo[2] += vxyzTo[2] * dt

        return ecefToPos(xyzTo[0], xyzTo[1], xyzTo[2], a = RE_GRS80, f = FE_GRS80)
    }

    // --- 1. NORTH AMERICA (USA, Hawaii, Guam, Canada) ---

    private fun getParameterItrf2020ToNad2011(epoch: Double): TransformationParams {
        val pT = doubleArrayOf(1003.90 / 1000.0, -1909.61 / 1000.0, -541.17 / 1000.0)
        val vT = doubleArrayOf(0.79 / 1000.0, -0.70 / 1000.0, -1.24 / 1000.0)
        val pD = -0.05109 * 1.0e-9
        val vD = -0.07201 * 1.0e-9
        val pR = doubleArrayOf(26.78138 * MAS2R, -0.42027 * MAS2R, 10.93206 * MAS2R)
        val vR = doubleArrayOf(0.06667 * MAS2R, -0.75744 * MAS2R, -0.05133 * MAS2R)
        val dt = epoch - 2010.0

        val t = doubleArrayOf(pT[0] + vT[0] * dt, pT[1] + vT[1] * dt, pT[2] + vT[2] * dt)
        val r = doubleArrayOf(-(pR[0] + vR[0] * dt), -(pR[1] + vR[1] * dt), -(pR[2] + vR[2] * dt))
        val d = pD + vD * dt
        return TransformationParams(t, r, d, doubleArrayOf(vT[0], vT[1], vT[2]), doubleArrayOf(-vR[0], -vR[1], -vR[2]), vD)
    }

    private fun getParameterItrf2020ToNadPa11(epoch: Double): TransformationParams {
        val pT = doubleArrayOf(909.50 / 1000.0, -2013.30 / 1000.0, -585.90 / 1000.0)
        val vT = doubleArrayOf(0.10 / 1000.0, 0.00 / 1000.0, -1.70 / 1000.0)
        val pD = 1.70 * 1.0e-9
        val vD = 0.11 * 1.0e-9
        val pR = doubleArrayOf(22.749 * MAS2R, 26.560 * MAS2R, -25.706 * MAS2R)
        val vR = doubleArrayOf(-0.384 * MAS2R, 1.007 * MAS2R, -2.186 * MAS2R)
        val dt = epoch - 2010.0

        val t = doubleArrayOf(pT[0] + vT[0] * dt, pT[1] + vT[1] * dt, pT[2] + vT[2] * dt)
        val r = doubleArrayOf(-(pR[0] + vR[0] * dt), -(pR[1] + vR[1] * dt), -(pR[2] + vR[2] * dt))
        val d = pD + vD * dt
        return TransformationParams(t, r, d, doubleArrayOf(vT[0], vT[1], vT[2]), doubleArrayOf(-vR[0], -vR[1], -vR[2]), vD)
    }

    private fun getParameterItrf2020ToNadMa11(epoch: Double): TransformationParams {
        val pT = doubleArrayOf(909.50 / 1000.0, -2013.30 / 1000.0, -585.90 / 1000.0)
        val vT = doubleArrayOf(0.10 / 1000.0, 0.00 / 1000.0, -1.70 / 1000.0)
        val pD = 1.70 * 1.0e-9
        val vD = 0.11 * 1.0e-9
        val pR = doubleArrayOf(28.711 * MAS2R, 11.785 * MAS2R, 4.417 * MAS2R)
        val vR = doubleArrayOf(-0.020 * MAS2R, 0.105 * MAS2R, -0.347 * MAS2R)
        val dt = epoch - 2010.0

        val t = doubleArrayOf(pT[0] + vT[0] * dt, pT[1] + vT[1] * dt, pT[2] + vT[2] * dt)
        val r = doubleArrayOf(-(pR[0] + vR[0] * dt), -(pR[1] + vR[1] * dt), -(pR[2] + vR[2] * dt))
        val d = pD + vD * dt
        return TransformationParams(t, r, d, doubleArrayOf(vT[0], vT[1], vT[2]), doubleArrayOf(-vR[0], -vR[1], -vR[2]), vD)
    }

    fun convertNad2011ToItrf2020(latDeg: Double, lonDeg: Double, heightM: Double, epoch: Double = 2010.0): Point3D =
        transformHelmert(latDeg, lonDeg, heightM, epoch, 2020.0, ::getParameterItrf2020ToNad2011)

    fun convertNadPa11ToItrf2020(latDeg: Double, lonDeg: Double, heightM: Double, epoch: Double = 2010.0): Point3D =
        transformHelmert(latDeg, lonDeg, heightM, epoch, 2020.0, ::getParameterItrf2020ToNadPa11)

    fun convertNadMa11ToItrf2020(latDeg: Double, lonDeg: Double, heightM: Double, epoch: Double = 2010.0): Point3D =
        transformHelmert(latDeg, lonDeg, heightM, epoch, 2020.0, ::getParameterItrf2020ToNadMa11)

    // --- 2. EUROPE (ETRS89 / ETRF2000) ---

    /**
     * Parameters from ITRF2020 to ETRF2000 from EUREF / ITRS.
     * Ported from get_parameter_itrf2020_to_etrf2000 in coord.c.
     */
    private fun getParameterItrf2020ToEtrf2000(epoch: Double): TransformationParams {
        val pT = doubleArrayOf(53.8 / 1000.0, 51.8 / 1000.0, -82.2 / 1000.0)
        val vT = doubleArrayOf(0.1 / 1000.0, 0.0 / 1000.0, -1.7 / 1000.0)
        val pD = 2.25 * 1.0e-9
        val vD = 0.11 * 1.0e-9
        val pR = doubleArrayOf(2.106 * MAS2R, 12.740 * MAS2R, -20.592 * MAS2R)
        val vR = doubleArrayOf(0.081 * MAS2R, 0.490 * MAS2R, -0.792 * MAS2R)

        val dt = epoch - 2015.0

        val t = doubleArrayOf(pT[0] + vT[0] * dt, pT[1] + vT[1] * dt, pT[2] + vT[2] * dt)
        val r = doubleArrayOf(pR[0] + vR[0] * dt, pR[1] + vR[1] * dt, pR[2] + vR[2] * dt)
        val d = pD + vD * dt

        val vt = doubleArrayOf(vT[0], vT[1], vT[2])
        val vr = doubleArrayOf(vR[0], vR[1], vR[2])
        val vd = vD

        return TransformationParams(t, r, d, vt, vr, vd)
    }

    fun convertEtrf2000ToItrf2020(latDeg: Double, lonDeg: Double, heightM: Double, epoch: Double = 2010.0): Point3D =
        transformHelmert(latDeg, lonDeg, heightM, epoch, 2020.0, ::getParameterItrf2020ToEtrf2000)

    // --- 3. AUSTRALIA (GDA2020 & GDA94) ---

    /**
     * Parameters from ITRF2020 to GDA2020 / GDA94 (ICSM Technical Manual).
     * Plate motion of Australian plate relative to ITRF2020:
     * Rx = -1.503 mas/yr, Ry = -1.183 mas/yr, Rz = 1.211 mas/yr.
     */
    private fun getParameterItrf2020ToGda2020(epoch: Double): TransformationParams {
        val dt = epoch - 2020.0
        val vR = doubleArrayOf(-1.503 * MAS2R, -1.183 * MAS2R, 1.211 * MAS2R)
        val r = doubleArrayOf(vR[0] * dt, vR[1] * dt, vR[2] * dt)
        return TransformationParams(DoubleArray(3), r, 0.0, DoubleArray(3), vR, 0.0)
    }

    private fun getParameterItrf2020ToGda94(epoch: Double): TransformationParams {
        // Transformation from GDA94 (1994.0) to GDA2020 / ITRF
        val pT = doubleArrayOf(0.06155, -0.01087, -0.04019)
        val pD = -0.009994 * 1.0e-6
        val pR = doubleArrayOf(-39.4924 * MAS2R, -32.7221 * MAS2R, -32.8974 * MAS2R)
        val vR = doubleArrayOf(-1.503 * MAS2R, -1.183 * MAS2R, 1.211 * MAS2R)
        val dt = epoch - 2020.0

        val r = doubleArrayOf(pR[0] + vR[0] * dt, pR[1] + vR[1] * dt, pR[2] + vR[2] * dt)
        return TransformationParams(pT, r, pD, DoubleArray(3), vR, 0.0)
    }

    fun convertGda2020ToItrf2020(latDeg: Double, lonDeg: Double, heightM: Double, epoch: Double = 2020.0): Point3D =
        transformHelmert(latDeg, lonDeg, heightM, epoch, 2020.0, ::getParameterItrf2020ToGda2020)

    fun convertGda94ToItrf2020(latDeg: Double, lonDeg: Double, heightM: Double, epoch: Double = 1994.0): Point3D =
        transformHelmert(latDeg, lonDeg, heightM, epoch, 2020.0, ::getParameterItrf2020ToGda94)

    // --- 4. NEW ZEALAND (NZGD2000) ---

    /**
     * NZGD2000 is defined at epoch 2000.0 on ITRF96.
     * Ported from get_parameter_itrf2020_to_itrf1996 in coord.c.
     */
    private fun getParameterItrf2020ToNzgd2000(epoch: Double): TransformationParams {
        val pT = doubleArrayOf(6.5 / 1000.0, -3.9 / 1000.0, -77.9 / 1000.0)
        val vT = doubleArrayOf(0.1 / 1000.0, -0.6 / 1000.0, -3.1 / 1000.0)
        val pD = 3.98 * 1.0e-9
        val vD = 0.12 * 1.0e-9
        val pR = doubleArrayOf(0.0, 0.0, 0.36 * MAS2R)
        val vR = doubleArrayOf(0.0, 0.0, 0.02 * MAS2R)

        val dt = epoch - 2015.0

        val t = doubleArrayOf(pT[0] + vT[0] * dt, pT[1] + vT[1] * dt, pT[2] + vT[2] * dt)
        val r = doubleArrayOf(pR[0] + vR[0] * dt, pR[1] + vR[1] * dt, pR[2] + vR[2] * dt)
        val d = pD + vD * dt

        return TransformationParams(t, r, d, doubleArrayOf(vT[0], vT[1], vT[2]), doubleArrayOf(vR[0], vR[1], vR[2]), vD)
    }

    fun convertNzgd2000ToItrf2020(latDeg: Double, lonDeg: Double, heightM: Double, epoch: Double = 2000.0): Point3D =
        transformHelmert(latDeg, lonDeg, heightM, epoch, 2020.0, ::getParameterItrf2020ToNzgd2000)

    // --- 5. SOUTH AMERICA (SIRGAS2000 / ITRF2000) & SOUTH AFRICA (ITRF1991) ---

    /**
     * Parameters from ITRF2020 to ITRF2000 / SIRGAS2000.
     * Ported from get_parameter_itrf2020_to_itrf2000 in coord.c.
     */
    private fun getParameterItrf2020ToItrf2000(epoch: Double): TransformationParams {
        val pT = doubleArrayOf(-0.2 / 1000.0, 0.8 / 1000.0, -34.2 / 1000.0)
        val vT = doubleArrayOf(0.1 / 1000.0, 0.0 / 1000.0, -1.7 / 1000.0)
        val pD = 2.25 * 1.0e-9
        val vD = 0.11 * 1.0e-9
        val dt = epoch - 2015.0

        val t = doubleArrayOf(pT[0] + vT[0] * dt, pT[1] + vT[1] * dt, pT[2] + vT[2] * dt)
        val d = pD + vD * dt

        return TransformationParams(t, DoubleArray(3), d, doubleArrayOf(vT[0], vT[1], vT[2]), DoubleArray(3), vD)
    }

    fun convertSirgas2000ToItrf2020(latDeg: Double, lonDeg: Double, heightM: Double, epoch: Double = 2000.4): Point3D =
        transformHelmert(latDeg, lonDeg, heightM, epoch, 2020.0, ::getParameterItrf2020ToItrf2000)

    /**
     * Parameters from ITRF2020 to ITRF1991 (South Africa).
     * Ported from get_parameter_itrf2020_to_itrf1991 in coord.c.
     */
    private fun getParameterItrf2020ToItrf1991(epoch: Double): TransformationParams {
        val pT = doubleArrayOf(26.5 / 1000.0, 12.1 / 1000.0, -91.9 / 1000.0)
        val vT = doubleArrayOf(0.1 / 1000.0, -0.6 / 1000.0, -3.1 / 1000.0)
        val pD = 4.67 * 1.0e-9
        val vD = 0.12 * 1.0e-9
        val pR = doubleArrayOf(0.0, 0.0, 0.36 * MAS2R)
        val vR = doubleArrayOf(0.0, 0.0, 0.02 * MAS2R)
        val dt = epoch - 2015.0

        val t = doubleArrayOf(pT[0] + vT[0] * dt, pT[1] + vT[1] * dt, pT[2] + vT[2] * dt)
        val r = doubleArrayOf(pR[0] + vR[0] * dt, pR[1] + vR[1] * dt, pR[2] + vR[2] * dt)
        val d = pD + vD * dt

        return TransformationParams(t, r, d, doubleArrayOf(vT[0], vT[1], vT[2]), doubleArrayOf(vR[0], vR[1], vR[2]), vD)
    }

    fun convertItrf1991ToItrf2020(latDeg: Double, lonDeg: Double, heightM: Double, epoch: Double = 1994.0): Point3D =
        transformHelmert(latDeg, lonDeg, heightM, epoch, 2020.0, ::getParameterItrf2020ToItrf1991)

    // --- 6. ASIA / GLOBAL ITRF REALIZATIONS (ITRF2014, ITRF2008, ITRF1997/CGCS2000, ITRF1996/TUREF) ---

    private fun getParameterItrf2020ToItrf2014(epoch: Double): TransformationParams {
        val pT = doubleArrayOf(-1.4 / 1000.0, -0.9 / 1000.0, 1.4 / 1000.0)
        val vT = doubleArrayOf(0.0 / 1000.0, -0.1 / 1000.0, 0.2 / 1000.0)
        val pD = -0.42 * 1.0e-9
        val dt = epoch - 2015.0

        val t = doubleArrayOf(pT[0] + vT[0] * dt, pT[1] + vT[1] * dt, pT[2] + vT[2] * dt)
        return TransformationParams(t, DoubleArray(3), pD, doubleArrayOf(vT[0], vT[1], vT[2]), DoubleArray(3), 0.0)
    }

    fun convertItrf2014ToItrf2020(latDeg: Double, lonDeg: Double, heightM: Double, epoch: Double = 2010.0): Point3D =
        transformHelmert(latDeg, lonDeg, heightM, epoch, 2020.0, ::getParameterItrf2020ToItrf2014)

    private fun getParameterItrf2020ToItrf2008(epoch: Double): TransformationParams {
        val pT = doubleArrayOf(0.2 / 1000.0, 1.0 / 1000.0, 3.3 / 1000.0)
        val vT = doubleArrayOf(0.0 / 1000.0, -0.1 / 1000.0, 0.1 / 1000.0)
        val pD = -0.29 * 1.0e-9
        val dt = epoch - 2015.0

        val t = doubleArrayOf(pT[0] + vT[0] * dt, pT[1] + vT[1] * dt, pT[2] + vT[2] * dt)
        return TransformationParams(t, DoubleArray(3), pD, doubleArrayOf(vT[0], vT[1], vT[2]), DoubleArray(3), 0.0)
    }

    fun convertItrf2008ToItrf2020(latDeg: Double, lonDeg: Double, heightM: Double, epoch: Double = 2011.0): Point3D =
        transformHelmert(latDeg, lonDeg, heightM, epoch, 2020.0, ::getParameterItrf2020ToItrf2008)

    private fun getParameterItrf2020ToItrf1997(epoch: Double): TransformationParams {
        val pT = doubleArrayOf(6.5 / 1000.0, -3.9 / 1000.0, -77.9 / 1000.0)
        val vT = doubleArrayOf(0.1 / 1000.0, -0.6 / 1000.0, -3.1 / 1000.0)
        val pD = 3.98 * 1.0e-9
        val vD = 0.12 * 1.0e-9
        val pR = doubleArrayOf(0.0, 0.0, 0.36 * MAS2R)
        val vR = doubleArrayOf(0.0, 0.0, 0.02 * MAS2R)
        val dt = epoch - 2015.0

        val t = doubleArrayOf(pT[0] + vT[0] * dt, pT[1] + vT[1] * dt, pT[2] + vT[2] * dt)
        val r = doubleArrayOf(pR[0] + vR[0] * dt, pR[1] + vR[1] * dt, pR[2] + vR[2] * dt)
        val d = pD + vD * dt

        return TransformationParams(t, r, d, doubleArrayOf(vT[0], vT[1], vT[2]), doubleArrayOf(vR[0], vR[1], vR[2]), vD)
    }

    fun convertItrf1997ToItrf2020(latDeg: Double, lonDeg: Double, heightM: Double, epoch: Double = 2000.0): Point3D =
        transformHelmert(latDeg, lonDeg, heightM, epoch, 2020.0, ::getParameterItrf2020ToItrf1997)

    /**
     * Transforms coordinates for Leaflet map display based on the active datum / mountpoint.
     * Converts regional tectonic-plate coordinates (EU: ETRS89/ETRF2000, AUS: GDA2020, NZ: NZGD2000,
     * SA: SIRGAS2000 / South Africa, USA: NAD83(2011)/PA11/MA11/CSRS, Asia: ITRF2014/ITRF2008/CGCS2000)
     * back to global WGS84 / ITRF2020 so rover positions and static surveys align accurately on the map.
     */
    fun transformForMapDisplay(
        latDeg: Double,
        lonDeg: Double,
        heightM: Double,
        datumName: String
    ): Point3D {
        if (latDeg == 0.0 && lonDeg == 0.0) return Point3D(latDeg, lonDeg, heightM)

        val nameUpper = datumName.uppercase()

        return when {
            // USA & Canada
            nameUpper.startsWith("NAD83(2011)") || nameUpper.startsWith("NAD83(CSRS)") -> convertNad2011ToItrf2020(latDeg, lonDeg, heightM)
            nameUpper.startsWith("NAD83(PA11)") -> convertNadPa11ToItrf2020(latDeg, lonDeg, heightM)
            nameUpper.startsWith("NAD83(MA11)") -> convertNadMa11ToItrf2020(latDeg, lonDeg, heightM)

            // Europe (ETRS89 / ETRF2000)
            nameUpper.contains("ETRS89") || nameUpper.contains("ETRF") -> convertEtrf2000ToItrf2020(latDeg, lonDeg, heightM)

            // Australia (GDA2020 / GDA94)
            nameUpper.contains("GDA2020") -> convertGda2020ToItrf2020(latDeg, lonDeg, heightM)
            nameUpper.contains("GDA94") -> convertGda94ToItrf2020(latDeg, lonDeg, heightM)

            // New Zealand (NZGD2000)
            nameUpper.contains("NZGD") -> convertNzgd2000ToItrf2020(latDeg, lonDeg, heightM)

            // South America (SIRGAS2000 / ITRF2000)
            nameUpper.contains("SIRGAS") || nameUpper.contains("ITRF2000") || nameUpper.contains("KGD2002") || nameUpper.contains("MTRF2000") -> {
                convertSirgas2000ToItrf2020(latDeg, lonDeg, heightM)
            }

            // South Africa (ITRF1991 / ZAF)
            nameUpper.contains("ITRF1991") || nameUpper.contains("ZAF") -> convertItrf1991ToItrf2020(latDeg, lonDeg, heightM)

            // Asia / Global (ITRF2014, ITRF2008, CGCS2000, TUREF)
            nameUpper.contains("ITRF2014") || nameUpper.contains("PGD2020") -> convertItrf2014ToItrf2020(latDeg, lonDeg, heightM)
            nameUpper.contains("ITRF2008") || nameUpper.contains("JGD2011") || nameUpper.contains("IGRS2013") || nameUpper.contains("NGD2012") || nameUpper.contains("GGD") -> {
                convertItrf2008ToItrf2020(latDeg, lonDeg, heightM)
            }
            nameUpper.contains("CGCS2000") || nameUpper.contains("ITRF97") || nameUpper.contains("ITRF1997") -> {
                convertItrf1997ToItrf2020(latDeg, lonDeg, heightM)
            }
            nameUpper.contains("TUREF") || nameUpper.contains("ITRF96") || nameUpper.contains("ITRF1996") -> {
                convertNzgd2000ToItrf2020(latDeg, lonDeg, heightM)
            }

            // Global default / already WGS84 or ITRF2020
            else -> Point3D(latDeg, lonDeg, heightM)
        }
    }
}

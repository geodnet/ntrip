package com.geodnet.ntrip.data

data class GeodnetDatumInfo(
    val name: String,
    val epoch: String,
    val region: String,
    val regionCode: String,
)

/**
 * Regional Geodetic Coordinate System (RGCS) resolver based on GEODNET RTK specification
 * (geodnet.github.io/rtk).
 *
 * When the "AUTO" mountpoint is selected, the GEODNET RTK caster automatically maps base
 * station coordinates to local regional mapping datums (e.g. NAD83 in North America, ETRS89
 * in Europe) based on the rover's NMEA GGA location.
 */
object GeodnetDatumResolver {

    val DATUM_LIST = listOf(
        GeodnetDatumInfo("NAD83(2011)", "2010.0", "USA and North America", "namerica"),
        GeodnetDatumInfo("NAD83(PA11)", "2010.0", "USA Hawaii", "namerica"),
        GeodnetDatumInfo("NAD83(MA11)", "2010.0", "Guam (GUM)", "asiapac"),
        GeodnetDatumInfo("NAD83(CSRS)v7", "2010.0", "Canada (CAN)", "namerica"),
        GeodnetDatumInfo("ETRS89(ETRF2000)", "2010.0", "Europe (EUR)", "europe"),
        GeodnetDatumInfo("GDA2020", "2020.0", "Australia (AUS)", "asiapac"),
        GeodnetDatumInfo("NZGD2000", "2000.0", "New Zealand (NZL)", "asiapac"),
        GeodnetDatumInfo("TUREF(2005.0) = ITRF96", "2005.0", "Turkey (TUR)", "europe"),
        GeodnetDatumInfo("ITRF2008", "2005.0", "India (IND)", "asiapac"),
        GeodnetDatumInfo("ITRF2008", "2011.811", "Egypt (EGY)", "others"),
        GeodnetDatumInfo("NGD2012 = ITRF2008", "2012.0", "Nigeria (NGA)", "others"),
        GeodnetDatumInfo("PGD2020 = ITRF2014", "2020.044", "Philippines (PHL)", "asiapac"),
        GeodnetDatumInfo("ITRF2014", "2010.0", "Mexico (MEX)", "namerica"),
        GeodnetDatumInfo("ITRF2014", "Current", "Kenya (KEN)", "others"),
        GeodnetDatumInfo("CGCS2000 = ITRF97", "2000.0", "China (CHN)", "asiapac"),
        GeodnetDatumInfo("JGD2011 = ITRF2008", "2011.3945", "Japan (JPN)", "asiapac"),
        GeodnetDatumInfo("IGRS2013 = ITRF2008", "2012.0", "Indonesia (IDN)", "asiapac"),
        GeodnetDatumInfo("ITRF1991", "1994.0", "South Africa (ZAF)", "others"),
        GeodnetDatumInfo("WGS84(G730)", "1994.0", "Sri Lanka (LKA)", "asiapac"),
        GeodnetDatumInfo("ITRF2020", "2025.0", "Taiwan (TWN)", "asiapac"),
        GeodnetDatumInfo("ITRF2014", "2010.0", "Thailand (THA)", "asiapac"),
        GeodnetDatumInfo("KGD2002 = ITRF2000", "2002.0", "South Korea (KOR)", "asiapac"),
        GeodnetDatumInfo("MGRF2020 = ITRF2020", "2020.0", "Malaysia (MYS)", "asiapac"),
        GeodnetDatumInfo("MTRF2000 = ITRF2000", "2004.0", "United Arab Emirates (ARE)", "others"),
        GeodnetDatumInfo("SIRGAS2000 = ITRF2000", "2000.4", "South America (SIRGAS)", "others"),
        GeodnetDatumInfo("GGD = ITRF2008", "2011.353", "Georgia (GEO)", "others"),
        GeodnetDatumInfo("WGS84(G2139)", "Dynamic", "Global Default", "others"),
    )

    /**
     * Resolves the coordinate system based on mountpoint, host, and rover coordinates.
     */
    fun resolve(
        mountpoint: String,
        host: String = "",
        lat: Double? = null,
        lon: Double? = null
    ): GeodnetDatumInfo {
        val mpUpper = mountpoint.trim().uppercase()
        val isGeodnet = host.contains("geodnet", ignoreCase = true) || host.isBlank()

        // Explicit non-AUTO mountpoints
        when {
            mpUpper == "AUTO_ITRF2020" -> return GeodnetDatumInfo("ITRF2020", "Current", "Global Scientific", "global")
            mpUpper == "AUTO_ITRF2014" -> return GeodnetDatumInfo("ITRF2014", "Current", "Global Standard", "global")
            mpUpper == "AUTO_WGS84" -> return GeodnetDatumInfo("WGS84(G2139)", "Dynamic (20xx.5)", "Global Orbit-Aligned", "global")
            mpUpper == "NATRF2020" -> return GeodnetDatumInfo("NATRF2020", "2020.0", "North America (Test Frame)", "namerica")
            mpUpper.startsWith("SIRGAS2000") -> return GeodnetDatumInfo("SIRGAS2000", "2026.5", "South America (Test Frame)", "others")
            mpUpper == "BRDC" -> return GeodnetDatumInfo("Broadcast Ephemeris", "Current", "Satellite Orbit Frame", "global")
            mpUpper != "AUTO" && !isGeodnet -> return GeodnetDatumInfo(mountpoint.ifBlank { "Standard RTCM" }, "N/A", "Caster Default", "custom")
        }

        // AUTO mountpoint with GEODNET RTK -> resolve from rover coordinates
        if (lat == null || lon == null || (lat == 0.0 && lon == 0.0)) {
            return GeodnetDatumInfo("AUTO (Pending Fix)", "Auto", "Auto Regional Detection", "namerica")
        }

        // Hawaii (USA)
        if (lat in 18.0..29.0 && lon in -179.0..-154.0) {
            return GeodnetDatumInfo("NAD83(PA11)", "2010.0", "USA Hawaii", "namerica")
        }
        // Guam
        if (lat in 13.0..14.0 && lon in 144.0..145.5) {
            return GeodnetDatumInfo("NAD83(MA11)", "2010.0", "Guam (GUM)", "asiapac")
        }
        // Canada
        if ((lat in 41.6..85.0 && lon in -95.0..-52.0) || (lat in 49.0..85.0 && lon in -141.0..-52.0)) {
            return GeodnetDatumInfo("NAD83(CSRS)v7", "2010.0", "Canada (CAN)", "namerica")
        }
        // Mexico
        if (lat in 14.0..33.0 && lon in -118.0..-86.0) {
            return GeodnetDatumInfo("ITRF2014", "2010.0", "Mexico (MEX)", "namerica")
        }
        // USA (Conterminous & Alaska)
        if ((lat in 24.0..49.0 && lon in -125.0..-66.0) || (lat in 51.0..72.0 && lon in -170.0..-129.0)) {
            return GeodnetDatumInfo("NAD83(2011)", "2010.0", "USA and North America", "namerica")
        }
        // Turkey
        if (lat in 35.5..42.5 && lon in 25.5..45.0) {
            return GeodnetDatumInfo("TUREF(2005.0) = ITRF96", "2005.0", "Turkey (TUR)", "europe")
        }
        // Europe (ETRS89)
        if (lat in 34.0..72.0 && lon in -25.0..40.0) {
            return GeodnetDatumInfo("ETRS89(ETRF2000)", "2010.0", "Europe (EUR)", "europe")
        }
        // Australia
        if (lat in -45.0..-10.0 && lon in 112.0..155.0) {
            return GeodnetDatumInfo("GDA2020", "2020.0", "Australia (AUS)", "asiapac")
        }
        // New Zealand
        if (lat in -48.0..-34.0 && lon in 165.0..179.5) {
            return GeodnetDatumInfo("NZGD2000", "2000.0", "New Zealand (NZL)", "asiapac")
        }
        // South Korea
        if (lat in 33.0..39.0 && lon in 124.0..131.0) {
            return GeodnetDatumInfo("KGD2002 = ITRF2000", "2002.0", "South Korea (KOR)", "asiapac")
        }
        // Taiwan
        if (lat in 21.5..26.5 && lon in 119.5..122.5) {
            return GeodnetDatumInfo("ITRF2020", "2025.0", "Taiwan (TWN)", "asiapac")
        }
        // Japan
        if (lat in 24.0..46.0 && lon in 128.0..154.0) {
            return GeodnetDatumInfo("JGD2011 = ITRF2008", "2011.3945", "Japan (JPN)", "asiapac")
        }
        // China
        if (lat in 18.0..54.0 && lon in 73.0..135.0) {
            return GeodnetDatumInfo("CGCS2000 = ITRF97", "2000.0", "China (CHN)", "asiapac")
        }
        // Philippines
        if (lat in 4.5..21.5 && lon in 116.5..127.0) {
            return GeodnetDatumInfo("PGD2020 = ITRF2014", "2020.044", "Philippines (PHL)", "asiapac")
        }
        // Thailand
        if (lat in 5.5..21.0 && lon in 97.0..106.0) {
            return GeodnetDatumInfo("ITRF2014", "2010.0", "Thailand (THA)", "asiapac")
        }
        // Malaysia
        if ((lat in 1.0..7.5 && lon in 99.5..105.0) || (lat in 0.8..7.5 && lon in 109.0..119.5)) {
            return GeodnetDatumInfo("MGRF2020 = ITRF2020", "2020.0", "Malaysia (MYS)", "asiapac")
        }
        // Indonesia
        if (lat in -11.5..6.0 && lon in 95.0..141.0) {
            return GeodnetDatumInfo("IGRS2013 = ITRF2008", "2012.0", "Indonesia (IDN)", "asiapac")
        }
        // India
        if (lat in 6.5..37.5 && lon in 68.0..97.5) {
            return GeodnetDatumInfo("ITRF2008", "2005.0", "India (IND)", "asiapac")
        }
        // Sri Lanka
        if (lat in 5.8..9.9 && lon in 79.5..82.0) {
            return GeodnetDatumInfo("WGS84(G730)", "1994.0", "Sri Lanka (LKA)", "asiapac")
        }
        // UAE
        if (lat in 22.5..26.5 && lon in 51.5..56.5) {
            return GeodnetDatumInfo("MTRF2000 = ITRF2000", "2004.0", "United Arab Emirates (ARE)", "others")
        }
        // Georgia
        if (lat in 41.0..43.6 && lon in 39.9..46.8) {
            return GeodnetDatumInfo("GGD = ITRF2008", "2011.353", "Georgia (GEO)", "others")
        }
        // Egypt
        if (lat in 21.5..32.0 && lon in 24.5..37.0) {
            return GeodnetDatumInfo("ITRF2008", "2011.811", "Egypt (EGY)", "others")
        }
        // Nigeria
        if (lat in 4.0..14.0 && lon in 2.5..15.0) {
            return GeodnetDatumInfo("NGD2012 = ITRF2008", "2012.0", "Nigeria (NGA)", "others")
        }
        // Kenya
        if (lat in -5.0..5.5 && lon in 33.8..42.0) {
            return GeodnetDatumInfo("ITRF2014", "Current", "Kenya (KEN)", "others")
        }
        // South Africa
        if (lat in -35.0..-22.0 && lon in 16.0..33.0) {
            return GeodnetDatumInfo("ITRF1991", "1994.0", "South Africa (ZAF)", "others")
        }
        // South America (SIRGAS)
        if (lat in -56.0..13.0 && lon in -82.0..-34.0) {
            return GeodnetDatumInfo("SIRGAS2000 = ITRF2000", "2000.4", "South America (SIRGAS)", "others")
        }

        // Global default fallback
        return GeodnetDatumInfo("WGS84(G2139)", "Dynamic", "Global Default", "others")
    }
}

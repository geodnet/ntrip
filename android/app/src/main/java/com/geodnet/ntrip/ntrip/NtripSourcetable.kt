package com.geodnet.ntrip.ntrip

/**
 * Represents a single NTRIP stream (STR) record from a caster's sourcetable.
 *
 * Format per NTRIP 1.0 / 2.0 standard:
 * STR;mountpoint;identifier;format;formatDetails;carrier;navSystem;network;country;latitude;longitude;nmea;solution;generator;compression;authentication;fee;bitrate;misc
 */
data class NtripStreamRecord(
    val mountpoint: String,
    val identifier: String = "",
    val format: String = "",
    val formatDetails: String = "",
    val carrier: Int = 0,
    val navSystem: String = "",
    val network: String = "",
    val country: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val nmea: Boolean = false,
    val solution: Int = 0,
    val generator: String = "",
    val compression: String = "",
    val authentication: String = "",
    val fee: String = "",
    val bitrate: Int = 0,
    val misc: String = ""
)

data class NtripSourcetable(
    val streams: List<NtripStreamRecord> = emptyList(),
    val casters: List<String> = emptyList(),
    val networks: List<String> = emptyList()
) {
    companion object {
        fun parse(body: String): NtripSourcetable {
            val lines = body.replace("\r\n", "\n").split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.equals("ENDSOURCETABLE", ignoreCase = true) }

            val streams = mutableListOf<NtripStreamRecord>()
            val casters = mutableListOf<String>()
            val networks = mutableListOf<String>()

            for (line in lines) {
                val fields = line.split(";")
                if (fields.isEmpty()) continue
                when (fields[0].uppercase()) {
                    "STR" -> {
                        if (fields.size > 1 && fields[1].isNotBlank()) {
                            val lat = fields.getOrNull(9)?.toDoubleOrNull()
                            val lon = fields.getOrNull(10)?.toDoubleOrNull()
                            val nmeaFlag = fields.getOrNull(11)?.let {
                                it == "1" || it.equals("Y", ignoreCase = true) || it.equals("YES", ignoreCase = true)
                            } ?: false

                            streams.add(
                                NtripStreamRecord(
                                    mountpoint = fields.getOrElse(1) { "" },
                                    identifier = fields.getOrElse(2) { "" },
                                    format = fields.getOrElse(3) { "" },
                                    formatDetails = fields.getOrElse(4) { "" },
                                    carrier = fields.getOrNull(5)?.toIntOrNull() ?: 0,
                                    navSystem = fields.getOrElse(6) { "" },
                                    network = fields.getOrElse(7) { "" },
                                    country = fields.getOrElse(8) { "" },
                                    latitude = lat,
                                    longitude = lon,
                                    nmea = nmeaFlag,
                                    solution = fields.getOrNull(12)?.toIntOrNull() ?: 0,
                                    generator = fields.getOrElse(13) { "" },
                                    compression = fields.getOrElse(14) { "" },
                                    authentication = fields.getOrElse(15) { "" },
                                    fee = fields.getOrElse(16) { "" },
                                    bitrate = fields.getOrNull(17)?.toIntOrNull() ?: 0,
                                    misc = fields.getOrElse(18) { "" }
                                )
                            )
                        }
                    }
                    "CAS" -> casters.add(line)
                    "NET" -> networks.add(line)
                }
            }

            return NtripSourcetable(
                streams = streams,
                casters = casters,
                networks = networks
            )
        }
    }
}

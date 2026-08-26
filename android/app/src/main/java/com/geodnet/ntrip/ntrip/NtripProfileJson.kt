package com.geodnet.ntrip.ntrip

/**
 * Serializes/parses [NtripProfile] lists as JSON text, split out of
 * `data/NtripProfileRepository.kt` (Context-dependent, hard to unit-test directly) so this part
 * -- the part actually worth verifying -- can be tested without Robolectric/an emulator.
 *
 * Deliberately **not** `org.json`: Android's real `org.json` implementation only exists on-device
 * (or under Robolectric) -- the plain-JVM unit-test classpath uses a stub jar where every method
 * throws "not mocked", which would make this class untestable the way the rest of this codebase's
 * pure logic is tested. This is a small hand-written parser/writer instead, with no Android
 * dependency at all. It only needs to round-trip what *this app itself* writes -- not arbitrary
 * external JSON -- so it deliberately only supports the shape this schema actually uses: a flat
 * JSON array of flat objects with string/number values, no nesting.
 */
object NtripProfileJson {

    fun serialize(profiles: List<NtripProfile>): String = buildString {
        append('[')
        profiles.forEachIndexed { index, p ->
            if (index > 0) append(',')
            append('{')
            appendStringField("id", p.id); append(',')
            appendStringField("name", p.name); append(',')
            appendStringField("host", p.config.host); append(',')
            appendNumberField("port", p.config.port); append(',')
            appendStringField("mountpoint", p.config.mountpoint); append(',')
            appendStringField("username", p.config.username); append(',')
            appendStringField("password", p.config.password); append(',')
            appendNumberField("latitude", p.config.latitude); append(',')
            appendNumberField("longitude", p.config.longitude); append(',')
            appendNumberField("altitude", p.config.altitude); append(',')
            appendNumberField("numSatellites", p.config.numSatellites); append(',')
            appendNumberField("hdop", p.config.hdop); append(',')
            appendNumberField("ggaIntervalMs", p.config.ggaIntervalMs)
            append('}')
        }
        append(']')
    }

    fun parse(json: String?): List<NtripProfile> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            JsonParser(json).parseProfileArray()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun StringBuilder.appendStringField(key: String, value: String) {
        append('"').append(key).append("\":")
        appendJsonString(value)
    }

    private fun StringBuilder.appendNumberField(key: String, value: Number) {
        append('"').append(key).append("\":").append(value)
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        for (c in value) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c.code < 0x20 -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }

    /** Minimal recursive-descent parser for exactly the shape [serialize] produces: an array of
     * flat objects, string/number values only. */
    private class JsonParser(private val s: String) {
        private var i = 0

        fun parseProfileArray(): List<NtripProfile> {
            expect('[')
            val result = mutableListOf<NtripProfile>()
            if (peek() == ']') {
                i++
                return result
            }
            while (true) {
                result += parseProfileObject()
                when (peek()) {
                    ',' -> i++
                    ']' -> {
                        i++
                        return result
                    }
                    else -> parseError("expected ',' or ']'")
                }
            }
        }

        private fun parseProfileObject(): NtripProfile {
            val defaults = NtripConfig()
            val fields = mutableMapOf<String, Any>()
            expect('{')
            if (peek() != '}') {
                while (true) {
                    val key = parseJsonString()
                    expect(':')
                    fields[key] = parseValue()
                    when (peek()) {
                        ',' -> i++
                        '}' -> break
                        else -> parseError("expected ',' or '}'")
                    }
                }
            }
            expect('}')

            fun str(key: String, default: String) = fields[key] as? String ?: default
            fun num(key: String) = fields[key] as? Double

            return NtripProfile(
                id = fields["id"] as? String ?: parseError("missing \"id\""),
                name = fields["name"] as? String ?: parseError("missing \"name\""),
                config = NtripConfig(
                    host = str("host", defaults.host),
                    port = num("port")?.toInt() ?: defaults.port,
                    mountpoint = str("mountpoint", defaults.mountpoint),
                    username = str("username", defaults.username),
                    password = str("password", defaults.password),
                    latitude = num("latitude") ?: defaults.latitude,
                    longitude = num("longitude") ?: defaults.longitude,
                    altitude = num("altitude") ?: defaults.altitude,
                    numSatellites = num("numSatellites")?.toInt() ?: defaults.numSatellites,
                    hdop = num("hdop") ?: defaults.hdop,
                    ggaIntervalMs = num("ggaIntervalMs")?.toLong() ?: defaults.ggaIntervalMs,
                ),
            )
        }

        private fun parseValue(): Any = if (peek() == '"') parseJsonString() else parseNumber()

        private fun parseJsonString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                when (val c = s[i++]) {
                    '"' -> return sb.toString()
                    '\\' -> when (val esc = s[i++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            sb.append(s.substring(i, i + 4).toInt(16).toChar())
                            i += 4
                        }
                        else -> parseError("bad escape '\\$esc'")
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun parseNumber(): Double {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            if (i == start) parseError("expected a number")
            return s.substring(start, i).toDouble()
        }

        private fun peek(): Char {
            skipWs()
            return s[i]
        }

        private fun expect(c: Char) {
            skipWs()
            if (s[i] != c) parseError("expected '$c', found '${s[i]}'")
            i++
        }

        private fun skipWs() {
            while (i < s.length && s[i].isWhitespace()) i++
        }

        private fun parseError(message: String): Nothing = error("$message at index $i")
    }
}

package com.geodnet.ntrip.rtcm

data class EpochLatencyStats(
    /** Time from the connection attempt starting to the first observation message received. */
    val firstMessageLatencyMs: Long? = null,
    /** Time since the most recent observation message, as of the last time this snapshot was
     * taken -- not continuously live-ticking (see [EpochLatencyEngine]'s class doc). */
    val lastMessageAgeMs: Long = 0,
    /** Δt_epoch = t_last - t_first for the most recently completed epoch batch, in fractional
     * milliseconds (nanosecond-resolution timers underneath, matching readme.md's
     * "sub-millisecond" claim literally). Null until at least one epoch has closed. */
    val lastEpochSpanMs: Double? = null,
    /** Observation messages seen so far in the epoch batch still in progress. */
    val epochMessageCount: Int = 0,
    val epochsCompleted: Long = 0,
    /** Base station observation epoch time tag (UTC seconds of day: 0..86399.999), decoded from RTCM MSM. */
    val lastBaseTimeTagUtcSec: Double? = null,
    /** Base station ID decoded from RTCM observation frames. */
    val baseStationId: Int? = null,
)

/**
 * Tracks arrival-time latency/epoch-span metrics for observation-carrying RTCM messages (MSM /
 * legacy 100x), per readme.md's "Sub-Millisecond Epoch Span & Latency Engine".
 */
class EpochLatencyEngine(private val epochGapNanos: Long = DEFAULT_EPOCH_GAP_NANOS) {

    private var connectionStartNanos: Long? = null
    private var firstMessageLatencyMs: Long? = null
    private var lastMessageAtNanos: Long? = null
    private var lastEpochSpanMs: Double? = null
    private var epochsCount: Long = 0
    private var lastBaseTimeTagUtcSec: Double? = null
    private var currentEpochTimeTagUtcSec: Double? = null
    private var baseStationId: Int? = null

    private var epochStartNanos: Long = 0
    private var epochLastNanos: Long = 0
    private var epochMessageCount: Int = 0
    private val seenConstellationFamilies = mutableSetOf<Int>()

    /** Call once when the connection attempt begins (before the first message can possibly
     * arrive) -- [firstMessageLatencyMs] is measured from here. */
    fun onConnectionStart(nowNanos: Long = System.nanoTime()) {
        connectionStartNanos = nowNanos
    }

    /** Call for every observation-carrying message (MSM / legacy 100x) as it arrives. */
    fun onObservationMessage(
        nowNanos: Long = System.nanoTime(),
        msgType: Int = 0,
        isMoreMessagesInEpoch: Boolean? = null,
        baseTimeTagUtcSec: Double? = null,
        staId: Int? = null,
    ) {
        if (firstMessageLatencyMs == null) {
            val start = connectionStartNanos
            firstMessageLatencyMs = if (start != null) (nowNanos - start) / NANOS_PER_MS else 0
        }
        lastMessageAtNanos = nowNanos
        if (baseTimeTagUtcSec != null) {
            lastBaseTimeTagUtcSec = baseTimeTagUtcSec
        }
        if (staId != null && staId != 0) {
            baseStationId = staId
        }

        val family = getConstellationFamily(msgType)

        // Check if this message starts a new epoch based on time tag or family repetition
        val startsNewEpoch: Boolean = when {
            epochMessageCount == 0 -> true
            baseTimeTagUtcSec != null && currentEpochTimeTagUtcSec != null &&
                kotlin.math.abs(baseTimeTagUtcSec - currentEpochTimeTagUtcSec!!).let { kotlin.math.min(it, 86400.0 - it) >= 0.05 } -> {
                // Time tag changed to a new observation second
                true
            }
            family != 0 && seenConstellationFamilies.contains(family) -> {
                // Constellation repeated (e.g. Next GPS MSM4 / 1074 frame)
                true
            }
            nowNanos - epochStartNanos > epochGapNanos -> {
                // Timeout fallback gap (>600ms)
                true
            }
            else -> false
        }

        if (startsNewEpoch) {
            if (epochMessageCount > 0) {
                lastEpochSpanMs = (epochLastNanos - epochStartNanos) / NANOS_PER_MS.toDouble()
            }
            epochStartNanos = nowNanos
            epochMessageCount = 0
            seenConstellationFamilies.clear()
            currentEpochTimeTagUtcSec = baseTimeTagUtcSec
        }

        epochLastNanos = nowNanos
        epochMessageCount++
        if (family != 0) {
            seenConstellationFamilies.add(family)
        }

        // Epoch completion detection:
        // 1. MSM Sync flag == false (Multiple Message Bit is 0): unambiguously signals last message of epoch
        // 2. Or if sync flag is null, each epoch start already counts the epoch
        if (isMoreMessagesInEpoch == false) {
            lastEpochSpanMs = (epochLastNanos - epochStartNanos) / NANOS_PER_MS.toDouble()
            epochsCount++
            epochMessageCount = 0
            seenConstellationFamilies.clear()
            currentEpochTimeTagUtcSec = null
        } else if (isMoreMessagesInEpoch == null && startsNewEpoch) {
            epochsCount++
        }
    }

    fun snapshot(nowNanos: Long = System.nanoTime()): EpochLatencyStats = EpochLatencyStats(
        firstMessageLatencyMs = firstMessageLatencyMs,
        lastMessageAgeMs = lastMessageAtNanos?.let { (nowNanos - it) / NANOS_PER_MS } ?: 0,
        lastEpochSpanMs = lastEpochSpanMs,
        epochMessageCount = epochMessageCount,
        epochsCompleted = epochsCount,
        lastBaseTimeTagUtcSec = lastBaseTimeTagUtcSec,
        baseStationId = baseStationId,
    )

    fun reset() {
        connectionStartNanos = null
        firstMessageLatencyMs = null
        lastMessageAtNanos = null
        lastEpochSpanMs = null
        epochsCount = 0
        lastBaseTimeTagUtcSec = null
        currentEpochTimeTagUtcSec = null
        baseStationId = null
        epochStartNanos = 0
        epochLastNanos = 0
        epochMessageCount = 0
        seenConstellationFamilies.clear()
    }

    companion object {
        private const val DEFAULT_EPOCH_GAP_NANOS = 600_000_000L // 600ms fallback gap
        private const val NANOS_PER_MS = 1_000_000L

        fun getConstellationFamily(msgType: Int): Int {
            return when (msgType) {
                in 1071..1077, in 1001..1004 -> 1 // GPS
                in 1081..1087, in 1009..1012 -> 2 // GLONASS
                in 1091..1097 -> 3 // Galileo
                in 1101..1107 -> 4 // SBAS
                in 1111..1117 -> 5 // QZSS
                in 1121..1127 -> 6 // BeiDou
                in 1131..1137 -> 7 // NavIC
                else -> 0
            }
        }
    }
}

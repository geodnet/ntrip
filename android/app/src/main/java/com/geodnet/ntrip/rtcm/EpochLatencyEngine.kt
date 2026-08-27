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
    private var epochsCompleted: Long = 0
    private var lastBaseTimeTagUtcSec: Double? = null
    private var currentEpochTimeTagUtcSec: Double? = null
    private var baseStationId: Int? = null

    private var epochStartNanos: Long = 0
    private var epochLastNanos: Long = 0
    private var epochMessageCount: Int = 0
    private val seenMessageTypes = mutableSetOf<Int>()

    /** Call once when the connection attempt begins (before the first message can possibly
     * arrive) -- [firstMessageLatencyMs] is measured from here. */
    fun onConnectionStart(nowNanos: Long = System.nanoTime()) {
        connectionStartNanos = nowNanos
    }

    /** Call for every observation-carrying message (MSM / legacy 100x) as it arrives. */
    fun onObservationMessage(
        nowNanos: Long = System.nanoTime(),
        msgType: Int = 0,
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

        // Determine whether this observation message begins a NEW GNSS epoch:
        val isNewEpoch: Boolean = when {
            epochMessageCount == 0 -> true
            baseTimeTagUtcSec != null && currentEpochTimeTagUtcSec != null -> {
                // Time tags available for both: check if time tag changed (handling 24h midnight rollover)
                val diffSec = kotlin.math.abs(baseTimeTagUtcSec - currentEpochTimeTagUtcSec!!)
                val normalizedDiff = kotlin.math.min(diffSec, 86400.0 - diffSec)
                normalizedDiff >= 0.05
            }
            msgType > 0 && seenMessageTypes.contains(msgType) -> {
                // Repeating constellation message type within stream (e.g. subsequent 1074 GPS frame)
                true
            }
            else -> {
                // Timing gap fallback for non-time-tagged messages without repeating types
                nowNanos - epochStartNanos > epochGapNanos
            }
        }

        if (isNewEpoch) {
            if (epochMessageCount > 0) {
                lastEpochSpanMs = (epochLastNanos - epochStartNanos) / NANOS_PER_MS.toDouble()
            }
            epochsCompleted++
            epochStartNanos = nowNanos
            epochMessageCount = 0
            seenMessageTypes.clear()
            currentEpochTimeTagUtcSec = baseTimeTagUtcSec
        }

        epochLastNanos = nowNanos
        epochMessageCount++
        if (msgType > 0) {
            seenMessageTypes.add(msgType)
        }
    }

    fun snapshot(nowNanos: Long = System.nanoTime()): EpochLatencyStats = EpochLatencyStats(
        firstMessageLatencyMs = firstMessageLatencyMs,
        lastMessageAgeMs = lastMessageAtNanos?.let { (nowNanos - it) / NANOS_PER_MS } ?: 0,
        lastEpochSpanMs = lastEpochSpanMs,
        epochMessageCount = epochMessageCount,
        epochsCompleted = epochsCompleted,
        lastBaseTimeTagUtcSec = lastBaseTimeTagUtcSec,
        baseStationId = baseStationId,
    )

    fun reset() {
        connectionStartNanos = null
        firstMessageLatencyMs = null
        lastMessageAtNanos = null
        lastEpochSpanMs = null
        epochsCompleted = 0
        lastBaseTimeTagUtcSec = null
        currentEpochTimeTagUtcSec = null
        baseStationId = null
        epochStartNanos = 0
        epochLastNanos = 0
        epochMessageCount = 0
        seenMessageTypes.clear()
    }

    companion object {
        private const val DEFAULT_EPOCH_GAP_NANOS = 600_000_000L // 600ms fallback gap
        private const val NANOS_PER_MS = 1_000_000L
    }
}

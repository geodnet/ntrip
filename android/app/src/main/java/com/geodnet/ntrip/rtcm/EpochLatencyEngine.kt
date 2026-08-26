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
    private var baseStationId: Int? = null

    private var epochStartNanos: Long = 0
    private var epochLastNanos: Long = 0
    private var epochMessageCount: Int = 0

    /** Call once when the connection attempt begins (before the first message can possibly
     * arrive) -- [firstMessageLatencyMs] is measured from here. */
    fun onConnectionStart(nowNanos: Long = System.nanoTime()) {
        connectionStartNanos = nowNanos
    }

    /** Call for every observation-carrying message (MSM / legacy 100x) as it arrives. */
    fun onObservationMessage(
        nowNanos: Long = System.nanoTime(),
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

        if (epochMessageCount == 0 || nowNanos - epochLastNanos > epochGapNanos) {
            if (epochMessageCount > 0) closeEpoch()
            epochStartNanos = nowNanos
        }
        epochLastNanos = nowNanos
        epochMessageCount++
    }

    private fun closeEpoch() {
        lastEpochSpanMs = (epochLastNanos - epochStartNanos) / NANOS_PER_MS.toDouble()
        epochsCompleted++
        epochMessageCount = 0
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
        baseStationId = null
        epochStartNanos = 0
        epochLastNanos = 0
        epochMessageCount = 0
    }

    companion object {
        private const val DEFAULT_EPOCH_GAP_NANOS = 200_000_000L // 200ms
        private const val NANOS_PER_MS = 1_000_000L
    }
}

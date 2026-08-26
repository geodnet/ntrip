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
)

/**
 * Tracks arrival-time latency/epoch-span metrics for observation-carrying RTCM messages (MSM /
 * legacy 100x), per readme.md's "Sub-Millisecond Epoch Span & Latency Engine".
 *
 * **This does not implement the readme's literal "20-bit MSM TOW modulo matching"**: correlating
 * each message's own decoded epoch-time field across constellations that use different GNSS time
 * scales (GPS/GLONASS/Galileo/BeiDou each have their own epoch/leap-second conventions) is a
 * substantially harder problem than what's implemented here, and RTCM MSM TOW fields are actually
 * ~30 bits (enough to cover a full GPS week in milliseconds) in every MSM variant this app
 * decodes -- not 20 -- so there's no real 20-bit field to match on. Reimplementing that literally
 * would mean guessing at a scheme rather than porting a known-correct one, which conflicts with
 * this codebase's usual bar of verifying RTCM bit-level decode logic (see rtcm/CLAUDE.md-style
 * notes elsewhere in this file's siblings). Instead, "epoch" here means a *burst of observation
 * messages that arrive close together in real arrival time* -- a new epoch starts whenever the gap
 * since the last observation message exceeds [epochGapNanos] (default 200ms, comfortably longer
 * than the sub-second burst of MSM frames for one real epoch, comfortably shorter than the gap
 * between one epoch's burst and the next at any normal GGA/reporting interval). This still
 * produces the exact Δt_epoch = t_last - t_first the readme asks for, just correlated by arrival
 * time rather than by decoding and cross-referencing each system's own epoch field.
 */
class EpochLatencyEngine(private val epochGapNanos: Long = DEFAULT_EPOCH_GAP_NANOS) {

    private var connectionStartNanos: Long? = null
    private var firstMessageLatencyMs: Long? = null
    private var lastMessageAtNanos: Long? = null
    private var lastEpochSpanMs: Double? = null
    private var epochsCompleted: Long = 0

    private var epochStartNanos: Long = 0
    private var epochLastNanos: Long = 0
    private var epochMessageCount: Int = 0

    /** Call once when the connection attempt begins (before the first message can possibly
     * arrive) -- [firstMessageLatencyMs] is measured from here. */
    fun onConnectionStart(nowNanos: Long = System.nanoTime()) {
        connectionStartNanos = nowNanos
    }

    /** Call for every observation-carrying message (MSM / legacy 100x) as it arrives. */
    fun onObservationMessage(nowNanos: Long = System.nanoTime()) {
        if (firstMessageLatencyMs == null) {
            val start = connectionStartNanos
            firstMessageLatencyMs = if (start != null) (nowNanos - start) / NANOS_PER_MS else 0
        }
        lastMessageAtNanos = nowNanos

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
    )

    fun reset() {
        connectionStartNanos = null
        firstMessageLatencyMs = null
        lastMessageAtNanos = null
        lastEpochSpanMs = null
        epochsCompleted = 0
        epochStartNanos = 0
        epochLastNanos = 0
        epochMessageCount = 0
    }

    companion object {
        private const val DEFAULT_EPOCH_GAP_NANOS = 200_000_000L // 200ms
        private const val NANOS_PER_MS = 1_000_000L
    }
}

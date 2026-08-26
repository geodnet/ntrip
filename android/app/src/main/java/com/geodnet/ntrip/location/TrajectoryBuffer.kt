package com.geodnet.ntrip.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory buffer of every [PositionFix] seen this session, capped at [MAX_POINTS] (oldest
 * dropped) so a long field session doesn't grow unbounded. Owned by NtripForegroundService so it
 * survives the Map screen's WebView being destroyed/recreated -- by rotation, tab navigation, or
 * the page itself reloading -- which is what readme.md's "Persistent Trajectory Storage" actually
 * asks for: the *data* survives, not necessarily the WebView instance. MapScreen replays the
 * current contents into a freshly (re)created WebView on `onPageFinished`.
 */
class TrajectoryBuffer {

    private val _points = MutableStateFlow<List<PositionFix>>(emptyList())
    val points: StateFlow<List<PositionFix>> = _points.asStateFlow()

    fun add(fix: PositionFix) {
        _points.update { current ->
            val next = current + fix
            if (next.size > MAX_POINTS) next.takeLast(MAX_POINTS) else next
        }
    }

    fun clear() {
        _points.value = emptyList()
    }

    companion object {
        // 20,000 (the original cap) is only ~30-65min at a 5-10Hz BLE receiver GGA rate --
        // comfortably hit during one drive, at which point the oldest part of the trip visibly
        // disappears from the map as it's dropped (reported as "trajectory lost"). 200,000 covers
        // ~5.5h at 10Hz or ~55h at 1Hz, while staying memory-bounded (a PositionFix is small,
        // roughly 100 bytes with object overhead, so this caps well under 25MB).
        private const val MAX_POINTS = 200_000
    }
}

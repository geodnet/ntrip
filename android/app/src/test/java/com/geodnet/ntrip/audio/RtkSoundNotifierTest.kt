package com.geodnet.ntrip.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class RtkSoundNotifierTest {

    private class TestableSoundNotifier : RtkSoundNotifier() {
        val playedEvents = mutableListOf<SoundType>()

        init {
            // Override play logic for unit testing without audio hardware
        }
    }

    @Test
    fun testRtkSoundStateTransitions() {
        val played = mutableListOf<RtkSoundNotifier.SoundType>()
        val notifier = object : RtkSoundNotifier() {
            override fun play(type: SoundType) {
                played.add(type)
            }
        }

        // 1. Initial fix is Single (1) -> no sound on initial startup
        notifier.onFixQualityChanged(1)
        assertEquals(0, played.size)

        // 2. Entering RTK Float (5) -> ENTERING_RTK
        notifier.onFixQualityChanged(5)
        assertEquals(listOf(RtkSoundNotifier.SoundType.ENTERING_RTK), played)

        // 3. Achieving First RTK Fixed (4) -> FIRST_FIX
        notifier.onFixQualityChanged(4)
        assertEquals(
            listOf(
                RtkSoundNotifier.SoundType.ENTERING_RTK,
                RtkSoundNotifier.SoundType.FIRST_FIX
            ),
            played
        )

        // 4. Dropping from Fixed (4) to Float (5) -> LOST_FIX
        notifier.onFixQualityChanged(5)
        assertEquals(
            listOf(
                RtkSoundNotifier.SoundType.ENTERING_RTK,
                RtkSoundNotifier.SoundType.FIRST_FIX,
                RtkSoundNotifier.SoundType.LOST_FIX
            ),
            played
        )

        // 5. Regaining RTK Fixed (4) -> REFIX
        notifier.onFixQualityChanged(4)
        assertEquals(
            listOf(
                RtkSoundNotifier.SoundType.ENTERING_RTK,
                RtkSoundNotifier.SoundType.FIRST_FIX,
                RtkSoundNotifier.SoundType.LOST_FIX,
                RtkSoundNotifier.SoundType.REFIX
            ),
            played
        )

        // 6. Dropping completely out of RTK to Single (1) -> EXITING_RTK
        notifier.onFixQualityChanged(1)
        assertEquals(
            listOf(
                RtkSoundNotifier.SoundType.ENTERING_RTK,
                RtkSoundNotifier.SoundType.FIRST_FIX,
                RtkSoundNotifier.SoundType.LOST_FIX,
                RtkSoundNotifier.SoundType.REFIX,
                RtkSoundNotifier.SoundType.EXITING_RTK
            ),
            played
        )
    }
}

package com.geodnet.ntrip.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Generates distinct audio beep/tone notifications for RTK fix state transitions:
 * - FIRST_FIX: Rising 3-tone chime (1200Hz -> 1600Hz -> 2200Hz) when RTK Fixed is acquired for the first time.
 * - REFIX: Crisp 2-tone chime (1700Hz -> 2300Hz) when RTK Fixed is regained after float/loss.
 * - LOST_FIX: Descending 2-tone warning (950Hz -> 550Hz) when dropping from Fixed to Float.
 * - ENTERING_RTK: Gentle ascending blip (1100Hz -> 1450Hz) when entering Float from Single/DGPS.
 * - EXITING_RTK: Low 2-buzz alarm (420Hz -> 350Hz) when dropping out of RTK completely back to Single/None.
 */
open class RtkSoundNotifier {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    enum class SoundType {
        FIRST_FIX,      // RTK First Fix
        REFIX,          // RTK Refix
        LOST_FIX,       // RTK Lost Fix (Fixed -> Float)
        ENTERING_RTK,   // Entering RTK (Single/None -> Float)
        EXITING_RTK     // Exiting RTK (Float/Fixed -> Single/None)
    }

    var isEnabled: Boolean = true

    private var previousFixQuality: Int = 0
    private var hasHadFirstFix: Boolean = false
    private var wasInRtk: Boolean = false

    fun reset() {
        previousFixQuality = 0
        hasHadFirstFix = false
        wasInRtk = false
    }

    fun onFixQualityChanged(newQuality: Int) {
        val prev = previousFixQuality
        if (newQuality == prev) return

        if (isEnabled && prev != 0) {
            val wasRtk = prev == 4 || prev == 5
            val isRtk = newQuality == 4 || newQuality == 5

            when {
                // Transition to RTK Fixed (quality 4)
                newQuality == 4 -> {
                    if (!hasHadFirstFix) {
                        hasHadFirstFix = true
                        play(SoundType.FIRST_FIX)
                    } else {
                        play(SoundType.REFIX)
                    }
                }
                // Dropped from RTK Fixed (4) to Float (5)
                prev == 4 && newQuality == 5 -> {
                    play(SoundType.LOST_FIX)
                }
                // Dropped out of RTK completely (from 4 or 5 down to 1, 2, or 0)
                wasRtk && !isRtk -> {
                    play(SoundType.EXITING_RTK)
                }
                // Entered RTK Float from non-RTK (Single/DGPS/None -> 5)
                !wasRtk && newQuality == 5 -> {
                    play(SoundType.ENTERING_RTK)
                }
            }
        } else if (newQuality == 4) {
            hasHadFirstFix = true
        }

        previousFixQuality = newQuality
        wasInRtk = (newQuality == 4 || newQuality == 5)
    }

    open fun play(type: SoundType) {
        scope.launch {
            val tones = when (type) {
                SoundType.FIRST_FIX -> listOf(
                    ToneStep(1200.0, 70),
                    ToneStep(1600.0, 70),
                    ToneStep(2200.0, 140)
                )
                SoundType.REFIX -> listOf(
                    ToneStep(1700.0, 60),
                    ToneStep(2300.0, 100)
                )
                SoundType.LOST_FIX -> listOf(
                    ToneStep(950.0, 80),
                    ToneStep(550.0, 120)
                )
                SoundType.ENTERING_RTK -> listOf(
                    ToneStep(1100.0, 60),
                    ToneStep(1450.0, 80)
                )
                SoundType.EXITING_RTK -> listOf(
                    ToneStep(420.0, 90),
                    ToneStep(0.0, 40),
                    ToneStep(350.0, 140)
                )
            }
            playToneSequence(tones)
        }
    }

    private data class ToneStep(val freqHz: Double, val durationMs: Int)

    private fun playToneSequence(steps: List<ToneStep>) {
        val sampleRate = 24000
        val totalSamples = steps.sumOf { (sampleRate * it.durationMs) / 1000 }
        if (totalSamples <= 0) return

        val pcm = ShortArray(totalSamples)
        var writeIdx = 0

        for (step in steps) {
            val stepSamples = (sampleRate * step.durationMs) / 1000
            val fadeSamples = (sampleRate * 0.005).toInt().coerceAtMost(stepSamples / 2) // 5ms fade envelope

            for (i in 0 until stepSamples) {
                val sample: Short = if (step.freqHz <= 0.0) {
                    0
                } else {
                    val angle = 2.0 * Math.PI * i * step.freqHz / sampleRate
                    val raw = sin(angle)
                    // Apply linear ramp fade-in/out to avoid speaker pops
                    val envelope = when {
                        i < fadeSamples -> i.toDouble() / fadeSamples
                        i > stepSamples - fadeSamples -> (stepSamples - i).toDouble() / fadeSamples
                        else -> 1.0
                    }
                    (raw * envelope * 24000.0).toInt().toShort()
                }
                if (writeIdx < pcm.size) {
                    pcm[writeIdx++] = sample
                }
            }
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            Thread.sleep((totalSamples * 1000L / sampleRate) + 40L)
        } catch (_: Exception) {
        } finally {
            try {
                track.stop()
                track.release()
            } catch (_: Exception) {}
        }
    }
}

package com.lowas.metronome

import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

val WAVE_NAMES = listOf("Sine", "Warm", "Organ")

fun midiOf(note: Int, octave: Int): Int = 12 * (octave + 1) + note

fun freqOf(midi: Int, a4: Int): Double = a4 * 2.0.pow((midi - 69) / 12.0)

/** Continuous drone tone. Settings live in Metronome.settings so they persist. */
object Drone {
    val playing = MutableStateFlow(false)

    @Volatile private var running = false
    @Volatile private var gen = 0

    fun start() {
        synchronized(this) {
            if (running) return
            running = true
            gen++
            val g = gen
            playing.value = true
            Thread({ loop(g) }, "drone").apply {
                priority = Thread.MAX_PRIORITY
                isDaemon = true
                start()
            }
        }
    }

    fun stop() {
        synchronized(this) {
            running = false
            gen++
            playing.value = false
        }
    }

    private var phase = 0.0

    private fun fill(chunk: ShortArray, freq: Double, g0: Float, g1: Float, wave: Int) {
        val n = chunk.size
        for (i in 0 until n) {
            val g = g0 + (g1 - g0) * i / n
            phase += 2 * PI * freq / SR
            if (phase > 2 * PI) phase -= 2 * PI
            val x = when (wave) {
                0 -> sin(phase)
                1 -> (sin(phase) + 0.45 * sin(2 * phase) + 0.2 * sin(3 * phase)) / 1.65
                else -> (sin(phase) + 0.6 * sin(2 * phase) + 0.45 * sin(3 * phase) +
                    0.3 * sin(4 * phase) + 0.15 * sin(6 * phase)) / 2.5
            }
            chunk[i] = (x * g * 0.8 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private fun loop(myGen: Int) {
        var track: AudioTrack? = null
        try {
            val t = buildTrack()
            track = t
            t.play()
            phase = 0.0
            val chunk = ShortArray(1024)
            var gain = 0f
            while (running && gen == myGen) {
                val s = Metronome.settings.value
                val f = freqOf(midiOf(s.droneNote, s.droneOctave), s.a4)
                val target = s.droneVolume
                // Ramp towards the target volume; this also gives a soft fade-in.
                val next = gain + (target - gain) * 0.25f
                fill(chunk, f, gain, next, s.droneWave)
                gain = next
                if (t.write(chunk, 0, chunk.size) < 0) break
            }
            // Fade out so stopping does not click.
            val s = Metronome.settings.value
            fill(chunk, freqOf(midiOf(s.droneNote, s.droneOctave), s.a4), gain, 0f, s.droneWave)
            t.write(chunk, 0, chunk.size)
        } catch (e: Exception) {
        } finally {
            try { track?.stop() } catch (e: Exception) { }
            try { track?.release() } catch (e: Exception) { }
            synchronized(this) {
                if (gen == myGen) {
                    running = false
                    playing.value = false
                }
            }
        }
    }
}

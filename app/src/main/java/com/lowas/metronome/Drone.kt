package com.lowas.metronome

import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

val WAVE_NAMES = listOf("Sine", "Warm", "Organ")
val CHORD_NAMES = listOf("Root", "+ Fifth", "+ Octave", "Power")

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

    private val phases = DoubleArray(3)

    private fun ratiosFor(chord: Int): DoubleArray = when (chord) {
        1 -> doubleArrayOf(1.0, 1.5)
        2 -> doubleArrayOf(1.0, 2.0)
        3 -> doubleArrayOf(1.0, 1.5, 2.0)
        else -> doubleArrayOf(1.0)
    }

    private fun voice(phase: Double, wave: Int): Double = when (wave) {
        0 -> sin(phase)
        1 -> (sin(phase) + 0.45 * sin(2 * phase) + 0.2 * sin(3 * phase)) / 1.65
        else -> (sin(phase) + 0.6 * sin(2 * phase) + 0.45 * sin(3 * phase) +
            0.3 * sin(4 * phase) + 0.15 * sin(6 * phase)) / 2.5
    }

    private var vibPhase = 0.0

    private fun fill(
        chunk: ShortArray, freq: Double, g0: Float, g1: Float, wave: Int, chord: Int,
        vib: Float, vibRate: Float
    ) {
        val ratios = ratiosFor(chord)
        val norm = 1.0 / sqrt(ratios.size.toDouble())
        val n = chunk.size
        for (i in 0 until n) {
            val g = g0 + (g1 - g0) * i / n
            // Vibrato: up to a quarter-tone of wobble at the chosen rate.
            var f = freq
            if (vib > 0f) {
                vibPhase += 2 * PI * vibRate / SR
                if (vibPhase > 2 * PI) vibPhase -= 2 * PI
                f = freq * 2.0.pow(sin(vibPhase) * vib * 25.0 / 1200.0)
            }
            var x = 0.0
            for (v in ratios.indices) {
                phases[v] += 2 * PI * f * ratios[v] / SR
                if (phases[v] > 2 * PI) phases[v] -= 2 * PI
                x += voice(phases[v], wave)
            }
            chunk[i] = (x * norm * g * 0.8 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    /** The drone's pitch, including the detune setting in cents. */
    fun droneFreq(s: MSettings): Double =
        freqOf(midiOf(s.droneNote, s.droneOctave), s.a4) * 2.0.pow(s.droneDetune / 1200.0)

    private fun loop(myGen: Int) {
        var track: AudioTrack? = null
        try {
            val t = buildTrack()
            track = t
            t.play()
            phases.fill(0.0)
            val chunk = ShortArray(1024)
            var gain = 0f
            while (running && gen == myGen) {
                val s = Metronome.settings.value
                val f = droneFreq(s)
                val target = s.droneVolume
                // Ramp towards the target volume; this also gives a soft fade-in.
                val next = gain + (target - gain) * 0.25f
                fill(chunk, f, gain, next, s.droneWave, s.droneChord, s.droneVib, s.droneVibRate)
                gain = next
                if (t.write(chunk, 0, chunk.size) < 0) break
            }
            // Fade out so stopping does not click.
            val s = Metronome.settings.value
            fill(chunk, droneFreq(s), gain, 0f, s.droneWave, s.droneChord, s.droneVib, s.droneVibRate)
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

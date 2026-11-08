package com.lowas.metronome

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class Reading(val freq: Double, val midi: Int, val cents: Double)

/** Microphone pitch detector using the YIN algorithm. Only runs while the Tuner screen is visible. */
object Tuner {
    val reading = MutableStateFlow<Reading?>(null)

    @Volatile private var running = false
    @Volatile private var gen = 0

    private const val IN_RATE = 44100
    private const val HOP = 2048
    private const val WIN = 4096

    fun start() {
        synchronized(this) {
            if (running) return
            running = true
            gen++
            val g = gen
            Thread({ loop(g) }, "tuner").apply {
                isDaemon = true
                start()
            }
        }
    }

    fun stop() {
        synchronized(this) {
            running = false
            gen++
        }
        reading.value = null
    }

    @SuppressLint("MissingPermission")
    private fun loop(myGen: Int) {
        var rec: AudioRecord? = null
        try {
            val minBuf = AudioRecord.getMinBufferSize(
                IN_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val r = AudioRecord(
                MediaRecorder.AudioSource.MIC, IN_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, 8192) * 2
            )
            rec = r
            if (r.state != AudioRecord.STATE_INITIALIZED) return
            r.startRecording()

            val win = FloatArray(WIN)
            val tmp = ShortArray(HOP)
            var miss = 0
            var last: Reading? = null

            while (running && gen == myGen) {
                var got = 0
                while (got < HOP && running && gen == myGen) {
                    val n = r.read(tmp, got, HOP - got)
                    if (n <= 0) break
                    got += n
                }
                if (got < HOP) break

                System.arraycopy(win, HOP, win, 0, WIN - HOP)
                var sum = 0.0
                for (i in 0 until HOP) {
                    val v = tmp[i] / 32768f
                    win[WIN - HOP + i] = v
                    sum += v * v
                }
                val rms = sqrt(sum / HOP)

                val f = if (rms < 0.008) -1.0 else yin(win)
                if (f > 0) {
                    miss = 0
                    val a4 = Metronome.settings.value.a4
                    val midiF = 69 + 12 * log2(f / a4)
                    val nearest = midiF.roundToInt()
                    var cents = (midiF - nearest) * 100
                    val prev = last
                    if (prev != null && prev.midi == nearest) cents = 0.5 * prev.cents + 0.5 * cents
                    val rd = Reading(f, nearest, cents)
                    last = rd
                    reading.value = rd
                } else {
                    miss++
                    if (miss >= 4) {
                        last = null
                        reading.value = null
                    }
                }
            }
        } catch (e: Exception) {
        } finally {
            try { rec?.stop() } catch (e: Exception) { }
            try { rec?.release() } catch (e: Exception) { }
        }
    }

    /** YIN pitch estimate in Hz, or -1 when no clear pitch is found. buf must be WIN long. */
    private fun yin(buf: FloatArray): Double {
        val w = WIN / 2
        val tauMax = min(w, IN_RATE / 40) // lowest detectable pitch is about 40 Hz
        val minTau = IN_RATE / 1500       // highest detectable pitch is about 1500 Hz
        val d = FloatArray(tauMax)
        for (tau in 1 until tauMax) {
            var sum = 0f
            for (i in 0 until w) {
                val diff = buf[i] - buf[i + tau]
                sum += diff * diff
            }
            d[tau] = sum
        }
        val cm = FloatArray(tauMax)
        cm[0] = 1f
        var run = 0f
        for (tau in 1 until tauMax) {
            run += d[tau]
            cm[tau] = if (run > 0f) d[tau] * tau / run else 1f
        }
        var tau = minTau
        var found = -1
        while (tau < tauMax - 1) {
            if (cm[tau] < 0.12f) {
                while (tau + 1 < tauMax - 1 && cm[tau + 1] < cm[tau]) tau++
                found = tau
                break
            }
            tau++
        }
        if (found < 1) return -1.0
        val s0 = cm[found - 1]
        val s1 = cm[found]
        val s2 = cm[found + 1]
        val denom = 2 * (2 * s1 - s2 - s0)
        val shift = if (denom != 0f) (s2 - s0) / denom else 0f
        return IN_RATE / (found + shift.toDouble())
    }
}

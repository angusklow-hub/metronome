package com.lowas.metronome

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class Fxk { TICK, TAP, ON, OFF, SELECT, HEAVY }

/**
 * Interface feedback: a short click and a haptic for taps, toggles and dial detents.
 * The sound is synthesised per interface style: Material is round and bubbly, One UI is soft,
 * Glass is bright and glassy.
 */
object Fx {
    private var vib: Vibrator? = null
    private val tracks = HashMap<Int, AudioTrack>()
    private var lastTick = 0L

    fun init(c: Context) {
        if (vib != null) return
        vib = try {
            if (Build.VERSION.SDK_INT >= 31) {
                (c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        } catch (e: Exception) {
            null
        }
    }

    fun play(k: Fxk, haptic: Boolean = true) {
        val s = Metronome.settings.value
        if (k == Fxk.TICK) {
            val now = System.nanoTime() / 1_000_000L
            if (now - lastTick < 22L) return
            lastTick = now
        }
        if (haptic && s.uiHaptics) buzz(k, s.skin)
        if (s.uiSounds) blip(k, s.skin)
    }

    private fun buzz(k: Fxk, skin: Int) {
        val v = vib ?: return
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val id = when (k) {
                    Fxk.TICK, Fxk.OFF, Fxk.SELECT -> VibrationEffect.EFFECT_TICK
                    Fxk.TAP, Fxk.ON -> VibrationEffect.EFFECT_CLICK
                    Fxk.HEAVY -> VibrationEffect.EFFECT_HEAVY_CLICK
                }
                // Material feels firmer; Glass stays light.
                val eff = if (skin == 0 && k == Fxk.SELECT) VibrationEffect.EFFECT_CLICK else id
                v.vibrate(VibrationEffect.createPredefined(eff))
            } else {
                val ms = when (k) { Fxk.TICK -> 8L; Fxk.HEAVY -> 30L; else -> 14L }
                v.vibrate(VibrationEffect.createOneShot(ms, if (k == Fxk.HEAVY) 255 else 110))
            }
        } catch (e: Exception) {
        }
    }

    private fun blip(k: Fxk, skin: Int) {
        try {
            val key = k.ordinal * 3 + skin
            val t = tracks.getOrPut(key) { build(k, skin) }
            t.stop()
            t.reloadStaticData()
            t.play()
        } catch (e: Exception) {
        }
    }

    private fun build(k: Fxk, skin: Int): AudioTrack {
        val (f0, f1) = when (k) {
            Fxk.TICK -> 1500f to 1500f
            Fxk.TAP -> 900f to 700f
            Fxk.ON -> 650f to 1000f
            Fxk.OFF -> 1000f to 620f
            Fxk.SELECT -> 1150f to 1150f
            Fxk.HEAVY -> 320f to 220f
        }
        val ms = when (k) { Fxk.TICK -> 14; Fxk.HEAVY -> 70; Fxk.ON, Fxk.OFF -> 55; else -> 32 }
        val decay = when (skin) { 0 -> 55f; 1 -> 70f; else -> 95f }
        val n = SR * ms / 1000
        val pcm = ShortArray(n)
        var phase = 0.0
        var phase2 = 0.0
        for (i in 0 until n) {
            val p = i / n.toFloat()
            val f = f0 + (f1 - f0) * p
            // Material bends upwards for a bubbly pop; Glass adds bright inharmonic partials.
            val bend = if (skin == 0) 1f + 0.35f * (1f - p) else 1f
            phase += 2 * PI * f * bend / SR
            phase2 += 2 * PI * f * 2.76 / SR
            val env = exp(-decay * i / SR.toFloat()) * (if (i < 40) i / 40f else 1f)
            val tone = when (skin) {
                0 -> sin(phase)
                1 -> sin(phase) * 0.85 + sin(phase * 2) * 0.1
                else -> sin(phase) * 0.6 + sin(phase2) * 0.35
            }
            pcm[i] = (tone * env * 0.4 * Short.MAX_VALUE).toInt().toShort()
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
                    .setSampleRate(SR)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(n * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(pcm, 0, n)
        return track
    }
}

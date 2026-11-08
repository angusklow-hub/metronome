package com.lowas.metronome

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

enum class Sound(val label: String) {
    CLICK("Click"), WOOD("Wood block"), BEEP("Beep"),
    HIHAT("Hi-hat"), KICK("Kick"), COWBELL("Cowbell")
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark"), AMOLED("AMOLED")
}

/** Every user setting for the whole app (metronome, drone, tuner, theme). */
data class MSettings(
    val bpm: Int = 100,
    val beats: Int = 4,
    val subdiv: Int = 1,
    val sound: Sound = Sound.CLICK,
    val volume: Float = 0.8f,
    val vibrate: Boolean = true,
    val background: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accent: Int = 0,
    val droneNote: Int = 9,
    val droneOctave: Int = 3,
    val droneWave: Int = 1,
    val droneVolume: Float = 0.6f,
    val a4: Int = 440,
)

/** Settings store plus the metronome engine. */
object Metronome {
    val settings = MutableStateFlow(MSettings())
    val playing = MutableStateFlow(false)
    val beat = MutableStateFlow(-1)

    private var inited = false
    private lateinit var prefs: SharedPreferences
    private var vibrator: Vibrator? = null

    @Volatile private var running = false
    @Volatile private var gen = 0
    private val cache = ConcurrentHashMap<Int, ShortArray>()
    private val taps = ArrayList<Long>()

    fun init(ctx: Context) {
        if (inited) return
        inited = true
        val app = ctx.applicationContext
        prefs = app.getSharedPreferences("metronome", Context.MODE_PRIVATE)
        vibrator = findVibrator(app)
        settings.value = load()
    }

    @Suppress("DEPRECATION")
    private fun findVibrator(app: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun load(): MSettings {
        val d = MSettings()
        return MSettings(
            bpm = prefs.getInt("bpm", d.bpm).coerceIn(30, 300),
            beats = prefs.getInt("beats", d.beats).coerceIn(1, 12),
            subdiv = prefs.getInt("subdiv", d.subdiv).coerceIn(1, 4),
            sound = Sound.values().getOrElse(prefs.getInt("sound", 0)) { Sound.CLICK },
            volume = prefs.getFloat("volume", d.volume),
            vibrate = prefs.getBoolean("vibrate", d.vibrate),
            background = prefs.getBoolean("background", d.background),
            theme = ThemeMode.values().getOrElse(prefs.getInt("theme", 0)) { ThemeMode.SYSTEM },
            accent = prefs.getInt("accent", d.accent),
            droneNote = prefs.getInt("droneNote", d.droneNote).coerceIn(0, 11),
            droneOctave = prefs.getInt("droneOctave", d.droneOctave).coerceIn(1, 6),
            droneWave = prefs.getInt("droneWave", d.droneWave).coerceIn(0, 2),
            droneVolume = prefs.getFloat("droneVolume", d.droneVolume),
            a4 = prefs.getInt("a4", d.a4).coerceIn(415, 466),
        )
    }

    private fun save(s: MSettings) {
        prefs.edit()
            .putInt("bpm", s.bpm).putInt("beats", s.beats).putInt("subdiv", s.subdiv)
            .putInt("sound", s.sound.ordinal).putFloat("volume", s.volume)
            .putBoolean("vibrate", s.vibrate).putBoolean("background", s.background)
            .putInt("theme", s.theme.ordinal).putInt("accent", s.accent)
            .putInt("droneNote", s.droneNote).putInt("droneOctave", s.droneOctave)
            .putInt("droneWave", s.droneWave).putFloat("droneVolume", s.droneVolume)
            .putInt("a4", s.a4)
            .apply()
    }

    fun update(fn: (MSettings) -> MSettings) {
        val n = fn(settings.value)
        settings.value = n
        if (inited) save(n)
    }

    fun setBpm(v: Int) = update { it.copy(bpm = v.coerceIn(30, 300)) }

    fun tap() {
        val now = SystemClock.elapsedRealtime()
        if (taps.isNotEmpty() && now - taps.last() > 2000) taps.clear()
        taps.add(now)
        if (taps.size > 8) taps.removeAt(0)
        if (taps.size >= 2) {
            val avg = (taps.last() - taps.first()).toDouble() / (taps.size - 1)
            setBpm((60000.0 / avg).roundToInt())
        }
    }

    // ---- engine ----

    fun start() {
        synchronized(this) {
            if (running) return
            running = true
            gen++
            val g = gen
            playing.value = true
            Thread({ loop(g) }, "metronome").apply {
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
            beat.value = -1
        }
    }

    private fun loop(myGen: Int) {
        var track: android.media.AudioTrack? = null
        try {
            val t = buildTrack()
            track = t
            t.play()
            var b = 0
            var tick = 0
            var carry = 0.0
            while (running && gen == myGen) {
                val s = settings.value
                val exact = SR * 60.0 / s.bpm / s.subdiv + carry
                val frames = exact.toInt().coerceAtLeast(64)
                carry = exact - frames
                val bar = b % s.beats
                val kind = if (tick == 0 && bar == 0) 0 else if (tick == 0) 1 else 2

                val clk = click(s.sound, kind)
                val buf = ShortArray(frames)
                val n = min(frames, clk.size)
                for (i in 0 until n) {
                    var v = clk[i] * s.volume
                    if (n < clk.size && i > n - 80) v *= (n - i) / 80f
                    buf[i] = v.toInt().toShort()
                }

                if (tick == 0) {
                    beat.value = bar
                    if (s.vibrate) buzz(bar == 0)
                }
                val w = t.write(buf, 0, frames)
                if (w < 0) break

                tick++
                if (tick >= s.subdiv) {
                    tick = 0
                    b = (b + 1) % s.beats
                }
            }
        } catch (e: Exception) {
            // Audio device unavailable; fall through to cleanup.
        } finally {
            try { track?.stop() } catch (e: Exception) { }
            try { track?.release() } catch (e: Exception) { }
            synchronized(this) {
                if (gen == myGen) {
                    running = false
                    playing.value = false
                    beat.value = -1
                }
            }
        }
    }

    /** Plays one click so a sound can be auditioned while stopped. */
    fun preview(sound: Sound) {
        if (running) return
        val clk = click(sound, 1)
        val vol = settings.value.volume
        Thread {
            var t: android.media.AudioTrack? = null
            try {
                val tr = buildTrack(clk.size * 2)
                t = tr
                tr.play()
                val buf = ShortArray(clk.size + 2000)
                for (i in clk.indices) buf[i] = (clk[i] * vol).toInt().toShort()
                tr.write(buf, 0, buf.size)
                Thread.sleep(buf.size * 1000L / SR + 80)
            } catch (e: Exception) {
            } finally {
                try { t?.stop() } catch (e: Exception) { }
                try { t?.release() } catch (e: Exception) { }
            }
        }.start()
    }

    fun buzz(strong: Boolean) {
        try {
            val v = vibrator ?: return
            if (!v.hasVibrator()) return
            v.vibrate(VibrationEffect.createOneShot(if (strong) 45L else 25L, if (strong) 255 else 140))
        } catch (e: Exception) {
        }
    }

    // ---- synthesised sounds ----

    private fun click(sound: Sound, kind: Int): ShortArray =
        cache.getOrPut(sound.ordinal * 3 + kind) { synth(sound, kind) }

    private fun synth(sound: Sound, kind: Int): ShortArray {
        val gain = when (kind) { 0 -> 1.0; 1 -> 0.8; else -> 0.45 }
        val p = when (kind) { 0 -> 1.25; 1 -> 1.0; else -> 0.85 }
        val ms = when (sound) {
            Sound.CLICK -> 40
            Sound.WOOD -> 60
            Sound.BEEP -> 70
            Sound.HIHAT -> 70
            Sound.KICK -> 140
            Sound.COWBELL -> 150
        }
        val n = SR * ms / 1000
        val out = ShortArray(n)
        val rnd = Random(7)
        var phase = 0.0
        var prev = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / SR
            val x: Double = when (sound) {
                Sound.CLICK -> sin(2 * PI * 1500 * p * t) * exp(-t * 110)
                Sound.WOOD -> (sin(2 * PI * 800 * p * t) * 0.7 + sin(2 * PI * 1650 * p * t) * 0.3) * exp(-t * 90)
                Sound.BEEP -> {
                    val env = min(1.0, min(t * 400, (ms / 1000.0 - t) * 400))
                    sin(2 * PI * 880 * p * t) * env
                }
                Sound.HIHAT -> {
                    val w = rnd.nextDouble() * 2 - 1
                    val hp = w - prev
                    prev = w
                    hp * 0.6 * exp(-t * 70)
                }
                Sound.KICK -> {
                    val f = 50 * p + 110 * exp(-t * 35)
                    phase += 2 * PI * f / SR
                    sin(phase) * exp(-t * 22)
                }
                Sound.COWBELL -> (sin(2 * PI * 540 * p * t) + sin(2 * PI * 800 * p * t)) * 0.5 * exp(-t * 28)
            }
            out[i] = (x * gain * 0.9 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }
}

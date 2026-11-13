package com.lowas.metronome

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

enum class Sound(val label: String) {
    CLICK("Click"), WOOD("Wood block"), BEEP("Beep"),
    HIHAT("Hi-hat"), KICK("Kick"), COWBELL("Cowbell")
}

enum class Skin(val label: String) {
    M3("Material 3"), ONEUI("One UI"), IOS("iOS 27")
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark"), AMOLED("AMOLED")
}

const val DEFAULT_PATTERN = "211111111111"

/** Every user setting for the whole app (metronome, drone, tuner, theme). */
data class MSettings(
    val bpm: Int = 100,
    val beats: Int = 4,
    val subdiv: Int = 1,
    val sound: Sound = Sound.CLICK,
    val volume: Float = 0.8f,
    val vibrate: Boolean = true,
    val background: Boolean = true,
    val theme: ThemeMode = ThemeMode.DARK,
    val accent: Int = 0,
    val droneNote: Int = 9,
    val droneOctave: Int = 3,
    val droneWave: Int = 1,
    val droneVolume: Float = 0.6f,
    val a4: Int = 440,
    /** One char per beat: '2' accent, '1' normal, '0' muted. */
    val pattern: String = DEFAULT_PATTERN,
    val keepAwake: Boolean = false,
    /** Material You: use wallpaper colours on Android 12 and later. */
    val dynamic: Boolean = Build.VERSION.SDK_INT >= 31,
    /** 0 root, 1 root + fifth, 2 root + octave, 3 root + fifth + octave. */
    val droneChord: Int = 0,
    val mixAccent: Float = 1f,
    val mixBeat: Float = 1f,
    val mixSub: Float = 1f,
    /** 0 soft, 1 normal, 2 strong. */
    val vibStrength: Int = 1,
    /** Fine-tune in milliseconds: positive makes the buzz fire earlier. */
    val vibOffset: Int = 0,
    val trainerOn: Boolean = false,
    val trainerStep: Int = 5,
    val trainerBars: Int = 4,
    val trainerTarget: Int = 140,
    /** Auto-stop after this many minutes; 0 means never. */
    val timerMin: Int = 0,
    val flash: Boolean = false,
    /** 0 dial, 1 pendulum, 2 blob. */
    val style: Int = 0,
    val showPattern: Boolean = true,
    val showName: Boolean = true,
    val showTimer: Boolean = true,
    val showTap: Boolean = true,
    val showQuick: Boolean = false,
    val dialHaptic: Boolean = true,
    /** Turn the audible click off to practise with vibration and visuals only. */
    val soundOn: Boolean = true,
    /** Click pitch in semitones, -12 to +12. */
    val pitch: Int = 0,
    /** Swing amount for eighths, percent 0 to 25. */
    val swing: Int = 0,
    /** Gap click: play this many bars, then silence this many. */
    val gapOn: Boolean = false,
    val gapPlay: Int = 2,
    val gapMute: Int = 1,
    /** Count-in bars before the trainer, gap and timer begin. */
    val countIn: Int = 0,
    /** Frosted-glass look: translucent panels over a soft colour backdrop. */
    val glass: Boolean = true,
    val glassAmt: Float = 0.6f,
    /** Interface style: Material 3, One UI or iOS 27. */
    val skin: Int = 0,
    /** Second pulse across the same bar, for polyrhythms such as 3 over 4. */
    val polyOn: Boolean = false,
    val polyBeats: Int = 3,
    val polySound: Int = 3,
    val polyVol: Float = 0.7f,
    /** Percentage of beats silenced at random, to test your inner pulse. */
    val randMute: Int = 0,
    /** Sound used for accent beats; -1 means the same as the main sound. */
    val accentSound: Int = -1,
    /** Tap also starts the metronome if it is stopped. */
    val tapStarts: Boolean = false,
    /** Play through the saved song sections instead of one fixed tempo. */
    val songOn: Boolean = false,
    val droneDetune: Int = 0,
    val droneVib: Float = 0f,
    val droneVibRate: Float = 5f,
    /** Tuner display transposition in semitones, for Bb, Eb and F instruments. */
    val transpose: Int = 0,
    /** 0 chromatic, otherwise an instrument whose open strings are shown as targets. */
    val instrument: Int = 0,
)

data class Preset(
    val name: String,
    val bpm: Int,
    val beats: Int,
    val subdiv: Int,
    val sound: Int,
    val pattern: String,
)

/** One part of a song: hold these settings for this many bars, then move to the next. */
data class Section(
    val name: String,
    val bpm: Int,
    val beats: Int,
    val subdiv: Int,
    val bars: Int,
    val pattern: String,
)

/** A beat that will be heard once the audio device reaches [frame]. */
private class Evt(val frame: Long, val bar: Int, val kind: Int, val barNo: Int)

/** Settings store plus the metronome engine. */
object Metronome {
    val settings = MutableStateFlow(MSettings())
    val playing = MutableStateFlow(false)
    val beat = MutableStateFlow(-1)
    /** Increments on every heard beat, so visuals still react when the beat number repeats. */
    val tick = MutableStateFlow(0)
    /** Total seconds spent playing, kept between launches. */
    val practiceSec = MutableStateFlow(0)
    val elapsed = MutableStateFlow(0)
    /** Bar number since playing started, counting from 1. */
    val barNo = MutableStateFlow(0)
    val presets = MutableStateFlow<List<Preset>>(emptyList())
    val sections = MutableStateFlow<List<Section>>(emptyList())
    /** Index of the song section playing now, or -1 when song mode is off. */
    val sectionIdx = MutableStateFlow(-1)

    private var inited = false
    private lateinit var prefs: SharedPreferences
    private var vibrator: Vibrator? = null

    @Volatile private var running = false
    @Volatile private var gen = 0
    private val cache = ConcurrentHashMap<Int, ShortArray>()
    private val taps = ArrayList<Long>()
    private val events = ConcurrentLinkedQueue<Evt>()

    /** Typical delay between asking a phone motor to buzz and feeling it, in milliseconds. */
    private const val VIB_LATENCY_MS = 25L

    fun init(ctx: Context) {
        if (inited) return
        inited = true
        val app = ctx.applicationContext
        prefs = app.getSharedPreferences("metronome", Context.MODE_PRIVATE)
        vibrator = findVibrator(app)
        settings.value = load()
        presets.value = loadPresets()
        practiceSec.value = prefs.getInt("practiceSec", 0)
        sections.value = loadSections()
    }

    @Suppress("DEPRECATION")
    private fun findVibrator(app: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private fun validPattern(p: String?): String =
        if (p != null && p.length >= 12 && p.all { it == '0' || it == '1' || it == '2' }) p else DEFAULT_PATTERN

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
            theme = ThemeMode.values().getOrElse(prefs.getInt("theme", d.theme.ordinal)) { ThemeMode.DARK },
            accent = prefs.getInt("accent", d.accent),
            droneNote = prefs.getInt("droneNote", d.droneNote).coerceIn(0, 11),
            droneOctave = prefs.getInt("droneOctave", d.droneOctave).coerceIn(1, 6),
            droneWave = prefs.getInt("droneWave", d.droneWave).coerceIn(0, 2),
            droneVolume = prefs.getFloat("droneVolume", d.droneVolume),
            a4 = prefs.getInt("a4", d.a4).coerceIn(415, 466),
            pattern = validPattern(prefs.getString("pattern", d.pattern)),
            keepAwake = prefs.getBoolean("keepAwake", d.keepAwake),
            dynamic = prefs.getBoolean("dynamic", d.dynamic),
            droneChord = prefs.getInt("droneChord", d.droneChord).coerceIn(0, 3),
            mixAccent = prefs.getFloat("mixAccent", d.mixAccent),
            mixBeat = prefs.getFloat("mixBeat", d.mixBeat),
            mixSub = prefs.getFloat("mixSub", d.mixSub),
            vibStrength = prefs.getInt("vibStrength", d.vibStrength).coerceIn(0, 2),
            vibOffset = prefs.getInt("vibOffset", d.vibOffset).coerceIn(-60, 60),
            trainerOn = prefs.getBoolean("trainerOn", d.trainerOn),
            trainerStep = prefs.getInt("trainerStep", d.trainerStep).coerceIn(1, 20),
            trainerBars = prefs.getInt("trainerBars", d.trainerBars).coerceIn(1, 16),
            trainerTarget = prefs.getInt("trainerTarget", d.trainerTarget).coerceIn(30, 300),
            timerMin = prefs.getInt("timerMin", d.timerMin).coerceIn(0, 120),
            flash = prefs.getBoolean("flash", d.flash),
            style = prefs.getInt("style", d.style).coerceIn(0, 4),
            showPattern = prefs.getBoolean("showPattern", d.showPattern),
            showName = prefs.getBoolean("showName", d.showName),
            showTimer = prefs.getBoolean("showTimer", d.showTimer),
            showTap = prefs.getBoolean("showTap", d.showTap),
            showQuick = prefs.getBoolean("showQuick", d.showQuick),
            dialHaptic = prefs.getBoolean("dialHaptic", d.dialHaptic),
            soundOn = prefs.getBoolean("soundOn", d.soundOn),
            pitch = prefs.getInt("pitch", d.pitch).coerceIn(-12, 12),
            swing = prefs.getInt("swing", d.swing).coerceIn(0, 25),
            gapOn = prefs.getBoolean("gapOn", d.gapOn),
            gapPlay = prefs.getInt("gapPlay", d.gapPlay).coerceIn(1, 8),
            gapMute = prefs.getInt("gapMute", d.gapMute).coerceIn(1, 8),
            countIn = prefs.getInt("countIn", d.countIn).coerceIn(0, 2),
            glass = prefs.getBoolean("glass", d.glass),
            glassAmt = prefs.getFloat("glassAmt", d.glassAmt).coerceIn(0f, 1f),
            skin = prefs.getInt("skin", d.skin).coerceIn(0, 2),
            polyOn = prefs.getBoolean("polyOn", d.polyOn),
            polyBeats = prefs.getInt("polyBeats", d.polyBeats).coerceIn(2, 12),
            polySound = prefs.getInt("polySound", d.polySound).coerceIn(0, Sound.values().lastIndex),
            polyVol = prefs.getFloat("polyVol", d.polyVol).coerceIn(0f, 1.5f),
            randMute = prefs.getInt("randMute", d.randMute).coerceIn(0, 90),
            accentSound = prefs.getInt("accentSound", d.accentSound).coerceIn(-1, Sound.values().lastIndex),
            tapStarts = prefs.getBoolean("tapStarts", d.tapStarts),
            songOn = prefs.getBoolean("songOn", d.songOn),
            droneDetune = prefs.getInt("droneDetune", d.droneDetune).coerceIn(-50, 50),
            droneVib = prefs.getFloat("droneVib", d.droneVib).coerceIn(0f, 1f),
            droneVibRate = prefs.getFloat("droneVibRate", d.droneVibRate).coerceIn(1f, 9f),
            transpose = prefs.getInt("transpose", d.transpose).coerceIn(-12, 12),
            instrument = prefs.getInt("instrument", d.instrument).coerceIn(0, 5),
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
            .putString("pattern", s.pattern).putBoolean("keepAwake", s.keepAwake)
            .putBoolean("dynamic", s.dynamic).putInt("droneChord", s.droneChord)
            .putFloat("mixAccent", s.mixAccent).putFloat("mixBeat", s.mixBeat).putFloat("mixSub", s.mixSub)
            .putInt("vibStrength", s.vibStrength).putInt("vibOffset", s.vibOffset)
            .putBoolean("trainerOn", s.trainerOn).putInt("trainerStep", s.trainerStep)
            .putInt("trainerBars", s.trainerBars).putInt("trainerTarget", s.trainerTarget)
            .putInt("timerMin", s.timerMin).putBoolean("flash", s.flash).putInt("style", s.style)
            .putBoolean("showPattern", s.showPattern).putBoolean("showName", s.showName)
            .putBoolean("showTimer", s.showTimer).putBoolean("showTap", s.showTap)
            .putBoolean("showQuick", s.showQuick).putBoolean("dialHaptic", s.dialHaptic)
            .putBoolean("soundOn", s.soundOn).putInt("pitch", s.pitch).putInt("swing", s.swing)
            .putBoolean("gapOn", s.gapOn).putInt("gapPlay", s.gapPlay).putInt("gapMute", s.gapMute)
            .putInt("countIn", s.countIn)
            .putBoolean("glass", s.glass).putFloat("glassAmt", s.glassAmt)
            .putInt("skin", s.skin)
            .putBoolean("polyOn", s.polyOn).putInt("polyBeats", s.polyBeats)
            .putInt("polySound", s.polySound).putFloat("polyVol", s.polyVol)
            .putInt("randMute", s.randMute).putInt("accentSound", s.accentSound)
            .putBoolean("tapStarts", s.tapStarts).putBoolean("songOn", s.songOn)
            .putInt("droneDetune", s.droneDetune).putFloat("droneVib", s.droneVib)
            .putFloat("droneVibRate", s.droneVibRate).putInt("transpose", s.transpose)
            .putInt("instrument", s.instrument)
            .apply()
    }

    fun update(fn: (MSettings) -> MSettings) {
        val n = fn(settings.value)
        settings.value = n
        if (inited) save(n)
    }

    fun setBpm(v: Int) = update { it.copy(bpm = v.coerceIn(30, 300)) }

    /** Cycles one beat through accent, normal and muted. */
    fun cyclePattern(i: Int) = update { s ->
        val chars = s.pattern.toCharArray()
        if (i in chars.indices) {
            chars[i] = when (chars[i]) { '2' -> '1'; '1' -> '0'; else -> '2' }
        }
        s.copy(pattern = String(chars))
    }

    fun tap() {
        val now = SystemClock.elapsedRealtime()
        if (taps.isNotEmpty() && now - taps.last() > 2000) taps.clear()
        taps.add(now)
        if (taps.size > 8) taps.removeAt(0)
        if (taps.size >= 2) {
            val avg = (taps.last() - taps.first()).toDouble() / (taps.size - 1)
            setBpm((60000.0 / avg).roundToInt())
            if (settings.value.tapStarts && !running) start()
        }
    }

    private fun addPractice(sec: Int) {
        if (sec <= 0 || !inited) return
        practiceSec.value = practiceSec.value + sec
        prefs.edit().putInt("practiceSec", practiceSec.value).apply()
    }

    fun resetPractice() {
        practiceSec.value = 0
        if (inited) prefs.edit().putInt("practiceSec", 0).apply()
    }

    // ---- song sections ----

    private fun persistSections() {
        if (!inited) return
        val text = sections.value.joinToString("\n") {
            "${it.name}|${it.bpm}|${it.beats}|${it.subdiv}|${it.bars}|${it.pattern}"
        }
        prefs.edit().putString("sections", text).apply()
    }

    private fun loadSections(): List<Section> {
        val raw = prefs.getString("sections", "") ?: ""
        return raw.lines().mapNotNull { line ->
            val f = line.split("|")
            if (f.size < 6) {
                null
            } else {
                try {
                    Section(
                        f[0], f[1].toInt().coerceIn(30, 300), f[2].toInt().coerceIn(1, 12),
                        f[3].toInt().coerceIn(1, 4), f[4].toInt().coerceIn(1, 64), validPattern(f[5])
                    )
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    fun addSection(name: String, bars: Int) {
        val s = settings.value
        val clean = name.trim().replace('|', '/').replace('\n', ' ').ifEmpty { "Part ${sections.value.size + 1}" }
        sections.value = sections.value + Section(clean, s.bpm, s.beats, s.subdiv, bars.coerceIn(1, 64), s.pattern)
        persistSections()
    }

    fun deleteSection(index: Int) {
        sections.value = sections.value.filterIndexed { i, _ -> i != index }
        persistSections()
    }

    fun moveSection(index: Int, delta: Int) {
        val list = sections.value.toMutableList()
        val to = index + delta
        if (index !in list.indices || to !in list.indices) return
        val item = list.removeAt(index)
        list.add(to, item)
        sections.value = list
        persistSections()
    }

    private fun applySection(sec: Section) = update {
        it.copy(bpm = sec.bpm, beats = sec.beats, subdiv = sec.subdiv, pattern = sec.pattern)
    }

    // ---- presets ----

    private fun persistPresets() {
        if (!inited) return
        val text = presets.value.joinToString("\n") {
            "${it.name}|${it.bpm}|${it.beats}|${it.subdiv}|${it.sound}|${it.pattern}"
        }
        prefs.edit().putString("presets", text).apply()
    }

    private fun loadPresets(): List<Preset> {
        val raw = prefs.getString("presets", "") ?: ""
        return raw.lines().mapNotNull { line ->
            val f = line.split("|")
            if (f.size < 6) {
                null
            } else {
                try {
                    Preset(
                        f[0], f[1].toInt().coerceIn(30, 300), f[2].toInt().coerceIn(1, 12),
                        f[3].toInt().coerceIn(1, 4), f[4].toInt(), validPattern(f[5])
                    )
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    fun savePreset(name: String) {
        val s = settings.value
        val clean = name.trim().replace('|', '/').replace('\n', ' ').ifEmpty { "${s.bpm} BPM" }
        presets.value = presets.value + Preset(clean, s.bpm, s.beats, s.subdiv, s.sound.ordinal, s.pattern)
        persistPresets()
    }

    fun loadPreset(p: Preset) = update {
        it.copy(
            bpm = p.bpm, beats = p.beats, subdiv = p.subdiv,
            sound = Sound.values().getOrElse(p.sound) { Sound.CLICK }, pattern = p.pattern
        )
    }

    fun deletePreset(index: Int) {
        presets.value = presets.value.filterIndexed { i, _ -> i != index }
        persistPresets()
    }

    // ---- engine ----

    fun start() {
        synchronized(this) {
            if (running) return
            running = true
            gen++
            val g = gen
            playing.value = true
            elapsed.value = 0
            barNo.value = 0
            val st = settings.value
            if (st.songOn && sections.value.isNotEmpty()) {
                sectionIdx.value = 0
                applySection(sections.value[0])
            } else {
                sectionIdx.value = -1
            }
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
            sectionIdx.value = -1
        }
    }

    private fun loop(myGen: Int) {
        var track: AudioTrack? = null
        var secsPlayed = 0
        try {
            val t = buildTrack()
            track = t
            t.play()
            events.clear()
            Thread({ syncLoop(t, myGen) }, "metronome-sync").apply {
                priority = Thread.MAX_PRIORITY
                isDaemon = true
                start()
            }

            var b = 0
            var tick = 0
            var carry = 0.0
            var queued = 0L
            var bars = 0
            var barStart = 0L
            var barFrames = 0L
            var polyIdx = 0
            var secIdx = 0
            var secBars = 0
            var startNs = System.nanoTime()
            var lastSec = -1
            val rnd = Random(System.nanoTime())

            while (running && gen == myGen) {
                val s = settings.value
                val perBeat = SR * 60.0 / s.bpm
                if (tick == 0 && b == 0) {
                    barStart = queued
                    barFrames = (perBeat * s.beats).toLong()
                    polyIdx = 0
                }
                val exact = (if (s.subdiv == 2 && s.swing > 0) {
                    perBeat * (if (tick == 0) 0.5 + s.swing / 100.0 else 0.5 - s.swing / 100.0)
                } else {
                    perBeat / s.subdiv
                }) + carry
                val frames = exact.toInt().coerceAtLeast(64)
                carry = exact - frames
                val bar = b % s.beats
                val st = s.pattern.getOrElse(bar) { '1' }
                // kind: 0 accent, 1 normal, 2 subdivision, -1 muted beat (silent)
                val eff = bars - s.countIn
                val gapMuted = s.gapOn && eff >= 0 && (eff % (s.gapPlay + s.gapMute)) >= s.gapPlay
                val kind = if (gapMuted) -1
                else if (tick == 0) (when (st) { '2' -> 0; '0' -> -1; else -> 1 }) else 2

                // Random mute silences the click but leaves the beat indicator running.
                val dropped = s.randMute > 0 && kind >= 0 && rnd.nextInt(100) < s.randMute
                val buf = ShortArray(frames)
                if (kind >= 0 && s.soundOn && !dropped) {
                    val voice = if (kind == 0 && s.accentSound >= 0) {
                        Sound.values().getOrElse(s.accentSound) { s.sound }
                    } else {
                        s.sound
                    }
                    val mix = when (kind) { 0 -> s.mixAccent; 1 -> s.mixBeat; else -> s.mixSub }
                    mixInto(buf, 0, click(voice, kind, s.pitch), s.volume * mix)
                }
                // Second pulse spread evenly across the same bar, for polyrhythms.
                if (s.polyOn && s.soundOn && barFrames > 0) {
                    val pn = s.polyBeats.coerceIn(2, 12)
                    val pv = Sound.values().getOrElse(s.polySound) { Sound.HIHAT }
                    while (polyIdx < pn) {
                        val hit = barStart + barFrames * polyIdx / pn
                        if (hit < queued) {
                            polyIdx++
                            continue
                        }
                        if (hit >= queued + frames) break
                        mixInto(
                            buf, (hit - queued).toInt(),
                            click(pv, if (polyIdx == 0) 0 else 1, s.pitch), s.volume * s.polyVol
                        )
                        polyIdx++
                    }
                }

                // Visuals and vibration are triggered when this beat is actually heard.
                if (tick == 0) events.add(Evt(queued, bar, kind, bars + 1))

                val w = t.write(buf, 0, frames)
                if (w < 0) break
                queued += frames

                val secs = if (bars < s.countIn) 0 else ((System.nanoTime() - startNs) / 1_000_000_000L).toInt()
                secsPlayed = secs
                if (secs != lastSec) {
                    lastSec = secs
                    elapsed.value = secs
                }
                if (s.timerMin > 0 && secs >= s.timerMin * 60) break

                tick++
                if (tick >= s.subdiv) {
                    tick = 0
                    b = (b + 1) % s.beats
                    if (b == 0) {
                        bars++
                        val cur = settings.value
                        if (bars == cur.countIn) startNs = System.nanoTime()
                        val eff2 = bars - cur.countIn
                        if (cur.songOn && sections.value.isNotEmpty()) {
                            secBars++
                            val list = sections.value
                            val cs = list.getOrNull(secIdx)
                            if (cs == null || secBars >= cs.bars) {
                                secIdx = (secIdx + 1) % list.size
                                secBars = 0
                                applySection(list[secIdx])
                            }
                            sectionIdx.value = secIdx
                        }
                        if (cur.trainerOn && eff2 > 0 && eff2 % max(1, cur.trainerBars) == 0) {
                            val next = if (cur.trainerTarget >= cur.bpm) {
                                min(cur.bpm + cur.trainerStep, cur.trainerTarget)
                            } else {
                                max(cur.bpm - cur.trainerStep, cur.trainerTarget)
                            }
                            if (next != cur.bpm) setBpm(next)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Audio device unavailable; fall through to cleanup.
        } finally {
            addPractice(secsPlayed)
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

    /** Current playback position of the audio device, in frames, or -1 if it is not known yet. */
    private fun headFrames(track: AudioTrack, ts: AudioTimestamp): Long {
        return try {
            if (track.getTimestamp(ts)) {
                ts.framePosition + (System.nanoTime() - ts.nanoTime) * SR / 1_000_000_000L
            } else {
                val p = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                if (p == 0L) -1L else p
            }
        } catch (e: Exception) {
            -1L
        }
    }

    /**
     * Fires the beat indicator and vibration at the moment the click is heard, by watching the
     * audio device's real playback position rather than when the click was queued.
     */
    private fun syncLoop(track: AudioTrack, myGen: Int) {
        val ts = AudioTimestamp()
        val t0 = System.nanoTime()
        try {
            while (running && gen == myGen) {
                val ev = events.peek()
                if (ev == null) {
                    Thread.sleep(2)
                    continue
                }
                val head = headFrames(track, ts)
                val s = settings.value
                val lead = (VIB_LATENCY_MS + s.vibOffset) * SR / 1000L
                // Give the audio device a moment to start before trusting an unknown position.
                val warming = head < 0L && System.nanoTime() - t0 < 250_000_000L
                if (!warming && (head < 0L || head >= ev.frame - lead)) {
                    events.poll()
                    beat.value = ev.bar
                    barNo.value = ev.barNo
                    tick.value = tick.value + 1
                    if (s.vibrate && ev.kind >= 0) buzz(ev.kind == 0)
                } else {
                    Thread.sleep(1)
                }
            }
        } catch (e: InterruptedException) {
        } finally {
            events.clear()
        }
    }

    /** Adds a click into [buf] at [off], fading it out if the chunk ends first. */
    private fun mixInto(buf: ShortArray, off: Int, clk: ShortArray, gain: Float) {
        if (off < 0 || off >= buf.size) return
        val n = min(clk.size, buf.size - off)
        for (i in 0 until n) {
            var v = clk[i] * gain
            if (n < clk.size && i > n - 80) v *= (n - i) / 80f
            buf[off + i] = (buf[off + i] + v).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    /** Plays one click so a sound can be auditioned while stopped. */
    fun preview(sound: Sound) {
        if (running) return
        val clk = click(sound, 1, settings.value.pitch)
        val vol = settings.value.volume
        Thread {
            var t: AudioTrack? = null
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

    /** One crisp buzz. Accents are one step stronger than the chosen strength. */
    fun buzz(accent: Boolean) {
        try {
            val v = vibrator ?: return
            if (!v.hasVibrator()) return
            val level = if (accent) min(2, settings.value.vibStrength + 1) else settings.value.vibStrength
            val effect = if (Build.VERSION.SDK_INT >= 29) {
                VibrationEffect.createPredefined(
                    when (level) {
                        0 -> VibrationEffect.EFFECT_TICK
                        1 -> VibrationEffect.EFFECT_CLICK
                        else -> VibrationEffect.EFFECT_HEAVY_CLICK
                    }
                )
            } else {
                VibrationEffect.createOneShot(
                    when (level) { 0 -> 15L; 1 -> 25L; else -> 40L },
                    when (level) { 0 -> 90; 1 -> 160; else -> 255 }
                )
            }
            v.vibrate(effect)
        } catch (e: Exception) {
        }
    }

    // ---- synthesised sounds ----

    private fun click(sound: Sound, kind: Int, semi: Int): ShortArray =
        cache.getOrPut((sound.ordinal * 3 + kind) * 32 + semi + 12) { synth(sound, kind, semi) }

    private fun synth(sound: Sound, kind: Int, semi: Int): ShortArray {
        val gain = when (kind) { 0 -> 1.0; 1 -> 0.8; else -> 0.45 }
        val p = when (kind) { 0 -> 1.25; 1 -> 1.0; else -> 0.85 } * 2.0.pow(semi / 12.0)
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

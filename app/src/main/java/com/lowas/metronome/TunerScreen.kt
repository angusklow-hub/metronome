package com.lowas.metronome

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun TunerScreen(pal: Pal, s: MSettings) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val owner = LocalLifecycleOwner.current

    // Listen only while the tuner is on screen and the app is in the foreground.
    DisposableEffect(granted, owner) {
        if (!granted) {
            onDispose { }
        } else {
            val obs = LifecycleEventObserver { _, e ->
                when (e) {
                    Lifecycle.Event.ON_RESUME -> Tuner.start()
                    Lifecycle.Event.ON_PAUSE -> Tuner.stop()
                    else -> {}
                }
            }
            owner.lifecycle.addObserver(obs)
            onDispose {
                owner.lifecycle.removeObserver(obs)
                Tuner.stop()
            }
        }
    }

    val r by Tuner.reading.collectAsState()

    if (!granted) {
        GlassCard(pal) {
            Text("Microphone needed", color = pal.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "The tuner listens through the microphone to find the pitch you play or sing. Audio is analysed on the phone and never stored or sent anywhere.",
                color = pal.sub,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = pal.accent,
                    contentColor = pal.onAccent
                )
            ) {
                Text("Allow microphone", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    } else {
        val reading = r
        val cents = reading?.cents ?: 0.0
        val shown = reading?.let { it.midi + s.transpose }
        val tuned = reading != null && abs(cents) < 5
        val tone = if (reading == null) pal.sub else if (tuned) InTune else pal.accent
        val needle by animateFloatAsState(cents.toFloat(), tween(140), label = "needle")

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(8.dp))
            Gauge(pal, if (reading == null) null else needle, tone)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                val name = shown?.let { NOTE_NAMES[((it % 12) + 12) % 12] } ?: "—"
                val oct = shown?.let { (it / 12 - 1).toString() } ?: ""
                Text(
                    name,
                    color = if (reading == null) pal.sub else pal.text,
                    style = TextStyle(fontSize = 88.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp)
                )
                Text(
                    oct,
                    color = pal.sub,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.padding(bottom = 18.dp, start = 2.dp)
                )
            }
            Text(
                if (reading == null) "Play a note" else String.format("%.1f Hz", reading.freq),
                color = pal.sub,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    reading == null -> " "
                    tuned -> "In tune"
                    cents > 0 -> "${cents.roundToInt()} cents sharp"
                    else -> "${(-cents).roundToInt()} cents flat"
                },
                color = tone,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            if (s.transpose != 0 && reading != null) {
                Text(
                    "Written pitch · sounds ${NOTE_NAMES[((reading.midi % 12) + 12) % 12]}",
                    color = pal.sub,
                    fontSize = 13.sp
                )
            }
            if (s.instrument > 0) {
                Spacer(Modifier.height(16.dp))
                StringRow(pal, s.instrument, reading?.midi)
            }
        }
    }
    Spacer(Modifier.height(24.dp))

    GlassCard(pal) {
        SectionLabel(pal, "Instrument")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            INSTRUMENTS.forEachIndexed { i, inst ->
                Chip(pal, inst.first, s.instrument == i) { Metronome.update { it.copy(instrument = i) } }
            }
        }
        Spacer(Modifier.height(18.dp))
        SectionLabel(pal, "Transposition")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TRANSPOSITIONS.forEach { (label, semis) ->
                Chip(pal, label, s.transpose == semis) { Metronome.update { it.copy(transpose = semis) } }
            }
        }
        Text(
            "Shows the note as your instrument reads it, so a Bb trumpet sees C for a concert Bb.",
            color = pal.sub,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
    Spacer(Modifier.height(14.dp))

    GlassCard(pal) {
        SectionLabel(pal, "Reference pitch (A4)")
        Stepper(
            pal, "${s.a4} Hz",
            { Metronome.update { it.copy(a4 = (it.a4 - 1).coerceAtLeast(415)) } },
            { Metronome.update { it.copy(a4 = (it.a4 + 1).coerceAtMost(466)) } }
        )
        Text(
            "Also used by the drone. Standard is 440; many baroque ensembles use 415.",
            color = pal.sub,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}

/** Semicircle gauge: the needle sweeps 60 degrees either side for 50 cents flat or sharp. */
@Composable
private fun Gauge(pal: Pal, cents: Float?, tone: androidx.compose.ui.graphics.Color) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
    ) {
        val cx = size.width / 2f
        val cy = size.height - 10.dp.toPx()
        val rad = minOf(size.width / 2f - 12.dp.toPx(), size.height - 24.dp.toPx())

        fun pt(angleDeg: Float, radius: Float): Offset {
            val a = Math.toRadians(angleDeg.toDouble())
            return Offset(cx + radius * cos(a).toFloat(), cy + radius * sin(a).toFloat())
        }

        val arcTopLeft = Offset(cx - rad, cy - rad)
        val arcSize = Size(rad * 2f, rad * 2f)
        // Track, with the in-tune zone (within 5 cents) lit up in the middle.
        drawArc(
            pal.chip, 210f, 120f, false,
            topLeft = arcTopLeft, size = arcSize,
            style = Stroke(10.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            InTune.copy(alpha = 0.55f), 264f, 12f, false,
            topLeft = arcTopLeft, size = arcSize,
            style = Stroke(10.dp.toPx(), cap = StrokeCap.Butt)
        )
        for (c in -50..50 step 10) {
            val angle = 270f + c * 1.2f
            val center = c == 0
            drawLine(
                if (center) pal.text.copy(alpha = 0.8f) else pal.sub.copy(alpha = 0.55f),
                pt(angle, rad - 24.dp.toPx()),
                pt(angle, rad - (if (center) 44.dp.toPx() else 36.dp.toPx())),
                strokeWidth = if (center) 2.5.dp.toPx() else 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        val angle = 270f + (cents ?: 0f).coerceIn(-50f, 50f) * 1.2f
        val needleColor = if (cents == null) pal.sub.copy(alpha = 0.5f) else tone
        drawLine(
            needleColor,
            Offset(cx, cy),
            pt(angle, rad - 30.dp.toPx()),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(needleColor, radius = 8.dp.toPx(), center = Offset(cx, cy))
    }
}

/** Open strings per instrument, as MIDI note numbers. The first entry turns this off. */
val INSTRUMENTS: List<Pair<String, List<Int>>> = listOf(
    "Chromatic" to emptyList(),
    "Guitar" to listOf(40, 45, 50, 55, 59, 64),
    "Bass" to listOf(28, 33, 38, 43),
    "Ukulele" to listOf(67, 60, 64, 69),
    "Violin" to listOf(55, 62, 69, 76),
    "Cello" to listOf(36, 43, 50, 57),
)

/** Written pitch offsets in semitones for transposing instruments. */
val TRANSPOSITIONS = listOf(
    "Concert C" to 0,
    "Bb" to 2,
    "A" to 3,
    "Eb" to 9,
    "F" to 7,
)

/** The open strings of the chosen instrument, with the nearest one lit up. */
@Composable
private fun StringRow(pal: Pal, instrument: Int, midi: Int?) {
    val strings = INSTRUMENTS.getOrNull(instrument)?.second ?: return
    val nearest = midi?.let { m -> strings.minByOrNull { abs(it - m) } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        strings.forEach { n ->
            val on = nearest == n && midi != null && abs(n - midi) <= 3
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (on) pal.accent else pal.chip),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    NOTE_NAMES[((n % 12) + 12) % 12],
                    color = if (on) pal.onAccent else pal.chipText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

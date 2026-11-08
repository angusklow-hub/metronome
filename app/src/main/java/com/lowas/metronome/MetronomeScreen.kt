package com.lowas.metronome

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

fun tempoName(bpm: Int): String = when {
    bpm < 40 -> "Grave"
    bpm < 60 -> "Largo"
    bpm < 66 -> "Larghetto"
    bpm < 76 -> "Adagio"
    bpm < 108 -> "Andante"
    bpm < 120 -> "Moderato"
    bpm < 156 -> "Allegro"
    bpm < 176 -> "Vivace"
    bpm < 200 -> "Presto"
    else -> "Prestissimo"
}

@Composable
fun MetronomeScreen(pal: Pal, s: MSettings) {
    val playing by Metronome.playing.collectAsState()
    val beat by Metronome.beat.collectAsState()

    GlassCard(pal) {
        Text(
            tempoName(s.bpm).uppercase(),
            color = pal.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RoundBtn(pal, "−") { Metronome.setBpm(s.bpm - 1) }
            // Drag up or down on the number to change tempo quickly.
            Column(
                Modifier
                    .weight(1f)
                    .pointerInput(Unit) {
                        var acc = 0f
                        detectVerticalDragGestures(onDragStart = { acc = 0f }) { _, d ->
                            acc -= d
                            val step = (acc / 12f).toInt()
                            if (step != 0) {
                                Metronome.setBpm(Metronome.settings.value.bpm + step)
                                acc -= step * 12f
                            }
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("${s.bpm}", color = pal.text, fontSize = 76.sp, fontWeight = FontWeight.SemiBold)
                Text("BPM", color = pal.sub, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            RoundBtn(pal, "+") { Metronome.setBpm(s.bpm + 1) }
        }
        Slider(
            value = s.bpm.toFloat(),
            onValueChange = { Metronome.setBpm(it.roundToInt()) },
            valueRange = 30f..300f
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
            for (i in 0 until s.beats) {
                val on = playing && beat == i
                val sc by animateFloatAsState(if (on) 1.35f else 1f, tween(90), label = "dot")
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (i == 0) 20.dp else 16.dp)
                        .scale(sc)
                        .clip(CircleShape)
                        .background(if (on) pal.accent else pal.chip)
                )
            }
        }
    }
    Spacer(Modifier.height(14.dp))

    GlassCard(pal) {
        SectionLabel(pal, "Beats per bar")
        Stepper(
            pal, "${s.beats} / 4",
            { Metronome.update { it.copy(beats = (it.beats - 1).coerceAtLeast(1)) } },
            { Metronome.update { it.copy(beats = (it.beats + 1).coerceAtMost(12)) } }
        )
        Spacer(Modifier.height(18.dp))
        SectionLabel(pal, "Subdivision")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "Quarter", 2 to "Eighths", 3 to "Triplets", 4 to "Sixteenths").forEach { (v, label) ->
                Chip(pal, label, s.subdiv == v) { Metronome.update { it.copy(subdiv = v) } }
            }
        }
    }
    Spacer(Modifier.height(14.dp))

    GlassCard(pal) {
        SectionLabel(pal, "Sound")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sound.values().forEach { snd ->
                Chip(pal, snd.label, s.sound == snd) {
                    Metronome.update { it.copy(sound = snd) }
                    Metronome.preview(snd)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        SectionLabel(pal, "Volume")
        Slider(value = s.volume, onValueChange = { v -> Metronome.update { it.copy(volume = v) } })
        Spacer(Modifier.height(6.dp))
        SwitchRow(pal, "Vibration", "Pulse the phone on every beat; stronger on beat 1", s.vibrate) { v ->
            Metronome.update { it.copy(vibrate = v) }
            if (v) Metronome.buzz(true)
        }
    }
    Spacer(Modifier.height(14.dp))
    BackgroundCard(pal, s)
}

@Composable
fun Dock(pal: Pal, modifier: Modifier, onToggle: () -> Unit) {
    val playing by Metronome.playing.collectAsState()
    Row(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .glass(pal, RoundedCornerShape(44.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tap registers on touch-down for accurate timing.
        Box(
            Modifier
                .weight(1f)
                .height(56.dp)
                .clip(RoundedCornerShape(50))
                .background(pal.chip)
                .pointerInput(Unit) { detectTapGestures(onPress = { Metronome.tap() }) },
            contentAlignment = Alignment.Center
        ) {
            Text("Tap tempo", color = pal.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.size(10.dp))
        PlayButton(pal, playing, 76, onToggle)
    }
}

@Composable
fun PlayButton(pal: Pal, playing: Boolean, sizeDp: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(lerp(pal.accent, Color.White, 0.25f), pal.accent)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size((sizeDp * 0.36f).dp)) {
            if (playing) {
                drawRoundRect(
                    Color.White,
                    topLeft = Offset(size.width * 0.1f, size.height * 0.1f),
                    size = Size(size.width * 0.8f, size.height * 0.8f),
                    cornerRadius = CornerRadius(6.dp.toPx())
                )
            } else {
                val p = Path().apply {
                    moveTo(size.width * 0.2f, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(size.width * 0.2f, size.height)
                    close()
                }
                drawPath(p, Color.White)
            }
        }
    }
}

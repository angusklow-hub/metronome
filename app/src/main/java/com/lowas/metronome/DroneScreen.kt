package com.lowas.metronome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun DroneScreen(pal: Pal, s: MSettings, onToggle: () -> Unit) {
    val playing by Drone.playing.collectAsState()
    val hz = Drone.droneFreq(s)

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                NOTE_NAMES[s.droneNote],
                color = pal.text,
                style = TextStyle(fontSize = 96.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp)
            )
            Text(
                "${s.droneOctave}",
                color = pal.sub,
                fontSize = 30.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(bottom = 20.dp, start = 2.dp)
            )
        }
        Text(String.format("%.2f Hz", hz), color = pal.sub, fontSize = 15.sp)
        Spacer(Modifier.height(20.dp))
        PlayButton(pal, playing, 92, onToggle)
        Spacer(Modifier.height(8.dp))
        Text(
            if (playing) "Playing" else "Tap to play",
            color = if (playing) pal.accent else pal.sub,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
    Spacer(Modifier.height(24.dp))

    GlassCard(pal) {
        SectionLabel(pal, "Note")
        // Four notes per row so all twelve are visible at once; sharps are outlined.
        NOTE_NAMES.withIndex().chunked(4).forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { (i, name) ->
                    val sel = s.droneNote == i
                    val sharp = name.contains('#')
                    val shape = RoundedCornerShape(16.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(if (sel) pal.accent else if (sharp) pal.card else pal.chip)
                            .border(1.dp, if (sel) pal.accent else pal.chip, shape)
                            .clickable { Metronome.update { it.copy(droneNote = i) } }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            name,
                            color = if (sel) pal.onAccent else pal.text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionLabel(pal, "Octave")
        Stepper(
            pal, "${s.droneOctave}",
            { Metronome.update { it.copy(droneOctave = (it.droneOctave - 1).coerceAtLeast(1)) } },
            { Metronome.update { it.copy(droneOctave = (it.droneOctave + 1).coerceAtMost(6)) } }
        )
        Text(
            "Octave 1 is very low and may be hard to hear on a phone speaker; headphones help.",
            color = pal.sub,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
    Spacer(Modifier.height(14.dp))

    GlassCard(pal) {
        SectionLabel(pal, "Tone")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WAVE_NAMES.forEachIndexed { i, name ->
                Chip(pal, name, s.droneWave == i) { Metronome.update { it.copy(droneWave = i) } }
            }
        }
        Spacer(Modifier.height(20.dp))
        SectionLabel(pal, "Layers")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CHORD_NAMES.forEachIndexed { i, name ->
                Chip(pal, name, s.droneChord == i) { Metronome.update { it.copy(droneChord = i) } }
            }
        }
        Spacer(Modifier.height(20.dp))
        SectionLabel(pal, "Volume")
        AppSlider(pal, s.droneVolume, { v -> Metronome.update { it.copy(droneVolume = v) } })
        Spacer(Modifier.height(16.dp))
        SectionLabel(
            pal,
            "Fine tune: " + if (s.droneDetune == 0) "centred" else "${if (s.droneDetune > 0) "+" else ""}${s.droneDetune} cents"
        )
        AppSlider(
            pal, s.droneDetune.toFloat(),
            { v -> Metronome.update { it.copy(droneDetune = v.roundToInt()) } }, -50f..50f
        )
        Spacer(Modifier.height(16.dp))
        SectionLabel(pal, if (s.droneVib <= 0.01f) "Vibrato: off" else "Vibrato depth")
        AppSlider(pal, s.droneVib, { v -> Metronome.update { it.copy(droneVib = v) } })
        if (s.droneVib > 0.01f) {
            Spacer(Modifier.height(10.dp))
            SectionLabel(pal, String.format("Vibrato speed: %.1f Hz", s.droneVibRate))
            AppSlider(pal, s.droneVibRate, { v -> Metronome.update { it.copy(droneVibRate = v) } }, 1f..9f)
        }
    }
    Spacer(Modifier.height(14.dp))
    BackgroundCard(pal, s)
}

package com.lowas.metronome

import androidx.compose.foundation.background
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DroneScreen(pal: Pal, s: MSettings, onToggle: () -> Unit) {
    val playing by Drone.playing.collectAsState()
    val midi = midiOf(s.droneNote, s.droneOctave)
    val hz = freqOf(midi, s.a4)

    GlassCard(pal) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                NOTE_NAMES[s.droneNote] + s.droneOctave,
                color = pal.text,
                fontSize = 88.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(String.format("%.2f Hz", hz), color = pal.sub, fontSize = 15.sp)
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(pal.accent)
                    .clickable(onClick = onToggle)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (playing) "Stop drone" else "Play drone",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
    Spacer(Modifier.height(14.dp))

    GlassCard(pal) {
        SectionLabel(pal, "Note")
        // Four notes per row so all twelve are visible at once.
        NOTE_NAMES.withIndex().chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (i, name) ->
                    val sel = s.droneNote == i
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (sel) pal.accent else pal.chip)
                            .clickable { Metronome.update { it.copy(droneNote = i) } }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            name,
                            color = if (sel) Color.White else pal.text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
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
            modifier = Modifier.padding(top = 10.dp)
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
        Spacer(Modifier.height(18.dp))
        SectionLabel(pal, "Volume")
        Slider(value = s.droneVolume, onValueChange = { v -> Metronome.update { it.copy(droneVolume = v) } })
    }
    Spacer(Modifier.height(14.dp))
    BackgroundCard(pal, s)
}

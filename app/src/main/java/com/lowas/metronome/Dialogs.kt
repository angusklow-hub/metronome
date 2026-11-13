package com.lowas.metronome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Brief full-screen wash of colour on each beat; off unless the Flash option is on. */
@Composable
fun FlashOverlay(pal: Pal, enabled: Boolean) {
    if (!enabled) return
    val beat by Metronome.beat.collectAsState()
    val playing by Metronome.playing.collectAsState()
    val tick by Metronome.tick.collectAsState()
    val a = remember { Animatable(0f) }
    LaunchedEffect(tick, playing) {
        if (playing && beat >= 0) {
            a.snapTo(0.3f)
            a.animateTo(0f, tween(180))
        } else {
            a.snapTo(0f)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { if (a.value > 0.01f) drawRect(pal.accent.copy(alpha = a.value)) }
    )
}

@Composable
fun PresetsSheet(pal: Pal, onSave: () -> Unit, onClose: () -> Unit) {
    val list by Metronome.presets.collectAsState()
    Column(
        Modifier
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Text("Presets", color = pal.text, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(14.dp))
        if (list.isEmpty()) {
            Text("No presets yet. Set up a tempo, beats and sound, then save it here.", color = pal.sub, fontSize = 14.sp)
            Spacer(Modifier.height(14.dp))
        }
        list.forEachIndexed { i, p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(pal.chip)
                    .clickable { Metronome.loadPreset(p); onClose() }
                    .padding(start = 18.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, color = pal.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "${p.bpm} BPM · ${p.beats} beats · " +
                            (SUBDIVS.firstOrNull { it.first == p.subdiv }?.second ?: "Quarter"),
                        color = pal.sub, fontSize = 13.sp
                    )
                }
                TextButton(onClick = { Metronome.deletePreset(i) }) {
                    Text("Delete", color = pal.sub)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = pal.accent, contentColor = pal.onAccent)
        ) {
            Text("Save current settings", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** Type a tempo directly. */
@Composable
fun BpmDialog(pal: Pal, current: Int, onDone: () -> Unit) {
    var text by remember { mutableStateOf(current.toString()) }
    val n = text.toIntOrNull()
    val ok = n != null && n in 30..300
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = pal.card,
        title = { Text("Set tempo", color = pal.text) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { v -> text = v.filter { it.isDigit() }.take(3) },
                singleLine = true,
                isError = !ok,
                supportingText = { Text("30 to 300 BPM") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        },
        confirmButton = {
            TextButton(
                enabled = ok,
                onClick = { Metronome.setBpm(n ?: current); onDone() }
            ) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } }
    )
}

@Composable
fun SaveDialog(pal: Pal, bpm: Int, onDone: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = pal.card,
        title = { Text("Save preset", color = pal.text) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                singleLine = true,
                label = { Text("Name (optional)") },
                placeholder = { Text("$bpm BPM") }
            )
        },
        confirmButton = { TextButton(onClick = { Metronome.savePreset(name); onDone() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } }
    )
}

package com.lowas.metronome

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, l ->
            SegmentedButton(
                selected = selected == i,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(i, labels.size)
            ) { Text(l, maxLines = 1) }
        }
    }
}

@Composable
private fun SubHeading(pal: Pal, text: String) {
    Spacer(Modifier.height(26.dp))
    Text(text, color = pal.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun LabeledStepper(
    pal: Pal, label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = pal.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        RoundBtn(pal, "−", onMinus, 40.dp)
        Text(
            value, color = pal.text, fontSize = 17.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp)
        )
        RoundBtn(pal, "+", onPlus, 40.dp)
    }
}

@Composable
private fun MixRow(pal: Pal, label: String, value: Float, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = pal.text, fontSize = 15.sp, modifier = Modifier.padding(end = 8.dp).weight(0.9f))
        Text("${(value * 100).roundToInt()}%", color = pal.sub, fontSize = 13.sp)
    }
    AppSlider(pal, value, onChange, 0f..1.5f)
}

private val TIME_SIGS = listOf(
    Triple("2/4", 2, "211111111111"),
    Triple("3/4", 3, "211111111111"),
    Triple("4/4", 4, "211111111111"),
    Triple("5/4", 5, "211211111111"),
    Triple("6/8", 6, "211211111111"),
    Triple("7/8", 7, "212121111111"),
)

@Composable
fun OptionsSheet(pal: Pal, s: MSettings) {
    Column(
        Modifier
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Text("Options", color = pal.text, style = MaterialTheme.typography.headlineSmall)
        OptDisplay(pal, s)
        OptRhythm(pal, s)
        OptPolyrhythm(pal, s)
        OptSound(pal, s)
        OptMixer(pal, s)
        OptPractice(pal, s)
        OptSongMode(pal, s)
        OptFeel(pal, s)
    }
}


@Composable
private fun OptDisplay(pal: Pal, s: MSettings) {
    SubHeading(pal, "DISPLAY")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Dial", "Pendulum", "Blob", "Ring", "Lights").forEachIndexed { i, label ->
            Chip(pal, label, s.style == i) { Metronome.update { it.copy(style = i) } }
        }
    }
    Spacer(Modifier.height(10.dp))
    SwitchRow(pal, "Beat bars", "Show the tappable accent pattern under the display", s.showPattern) { v ->
        Metronome.update { it.copy(showPattern = v) }
    }
    SwitchRow(pal, "Tempo marking", "Show the Italian name such as Andante", s.showName) { v ->
        Metronome.update { it.copy(showName = v) }
    }
    SwitchRow(pal, "Practice timer", "Show elapsed time in the display while playing", s.showTimer) { v ->
        Metronome.update { it.copy(showTimer = v) }
    }
    SwitchRow(pal, "Tap button", "Show the Tap tempo button", s.showTap) { v ->
        Metronome.update { it.copy(showTap = v) }
    }
    SwitchRow(pal, "Quick tempos", "A row of common tempos to jump to", s.showQuick) { v ->
        Metronome.update { it.copy(showQuick = v) }
    }
    SwitchRow(pal, "Dial haptics", "A small tick for every BPM while you spin the dial", s.dialHaptic) { v ->
        Metronome.update { it.copy(dialHaptic = v) }
    }
    SwitchRow(pal, "Interface sounds", "Soft clicks on buttons, toggles and the dial", s.uiSounds) { v ->
        Metronome.update { it.copy(uiSounds = v) }
    }
    SwitchRow(pal, "Interface haptics", "A small buzz on buttons and toggles", s.uiHaptics) { v ->
        Metronome.update { it.copy(uiHaptics = v) }
    }
}

@Composable
private fun OptRhythm(pal: Pal, s: MSettings) {
    SubHeading(pal, "RHYTHM")
    SectionLabel(pal, "Time signature")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TIME_SIGS.forEach { (label, beats, pat) ->
            Chip(pal, label, s.beats == beats && s.pattern == pat) {
                Metronome.update { it.copy(beats = beats, pattern = pat) }
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    SectionLabel(pal, "Beats per bar")
    Stepper(
        pal, "${s.beats}",
        { Metronome.update { it.copy(beats = (it.beats - 1).coerceAtLeast(1)) } },
        { Metronome.update { it.copy(beats = (it.beats + 1).coerceAtMost(12)) } }
    )
    Spacer(Modifier.height(18.dp))
    SectionLabel(pal, "Subdivision")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SUBDIVS.forEach { (v, label) ->
            Chip(pal, label, s.subdiv == v) { Metronome.update { it.copy(subdiv = v) } }
        }
    }
    if (s.subdiv == 2) {
        Spacer(Modifier.height(10.dp))
        SectionLabel(pal, if (s.swing == 0) "Swing: straight" else "Swing: ${s.swing}%")
        AppSlider(pal, s.swing.toFloat(), { v -> Metronome.update { it.copy(swing = v.roundToInt()) } }, 0f..25f)
    }
}

@Composable
private fun OptPolyrhythm(pal: Pal, s: MSettings) {
    SubHeading(pal, "POLYRHYTHM")
    SwitchRow(
        pal, "Second pulse",
        "Spread another pulse evenly across the same bar, for ${s.polyBeats} over ${s.beats}",
        s.polyOn
    ) { v -> Metronome.update { it.copy(polyOn = v) } }
    if (s.polyOn) {
        LabeledStepper(
            pal, "Pulses per bar", "${s.polyBeats}",
            { Metronome.update { it.copy(polyBeats = (it.polyBeats - 1).coerceAtLeast(2)) } },
            { Metronome.update { it.copy(polyBeats = (it.polyBeats + 1).coerceAtMost(12)) } }
        )
        Spacer(Modifier.height(8.dp))
        SectionLabel(pal, "Second pulse sound")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sound.values().forEachIndexed { i, snd ->
                Chip(pal, snd.label, s.polySound == i) {
                    Metronome.update { it.copy(polySound = i) }
                    Metronome.preview(snd)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        MixRow(pal, "Second pulse level", s.polyVol) { v -> Metronome.update { it.copy(polyVol = v) } }
    }
}

@Composable
private fun OptSound(pal: Pal, s: MSettings) {
    SubHeading(pal, "SOUND")
    SwitchRow(pal, "Audible click", "Turn off to practise with vibration and visuals only", s.soundOn) { v ->
        Metronome.update { it.copy(soundOn = v) }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Sound.values().forEach { snd ->
            Chip(pal, snd.label, s.sound == snd) {
                Metronome.update { it.copy(sound = snd) }
                Metronome.preview(snd)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    SectionLabel(pal, "Accent sound")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(pal, "Same", s.accentSound < 0) { Metronome.update { it.copy(accentSound = -1) } }
        Sound.values().forEachIndexed { i, snd ->
            Chip(pal, snd.label, s.accentSound == i) {
                Metronome.update { it.copy(accentSound = i) }
                Metronome.preview(snd)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    SectionLabel(
        pal,
        "Click pitch: " + when {
            s.pitch == 0 -> "default"
            s.pitch > 0 -> "+${s.pitch} semitones"
            else -> "${s.pitch} semitones"
        }
    )
    AppSlider(pal, s.pitch.toFloat(), { v -> Metronome.update { it.copy(pitch = v.roundToInt()) } }, -12f..12f)
}

@Composable
private fun OptMixer(pal: Pal, s: MSettings) {
    SubHeading(pal, "MIXER")
    MixRow(pal, "Master volume", s.volume.coerceAtMost(1f)) { v ->
        Metronome.update { it.copy(volume = v.coerceAtMost(1f)) }
    }
    MixRow(pal, "Accent beat", s.mixAccent) { v -> Metronome.update { it.copy(mixAccent = v) } }
    MixRow(pal, "Normal beat", s.mixBeat) { v -> Metronome.update { it.copy(mixBeat = v) } }
    MixRow(pal, "Subdivisions", s.mixSub) { v -> Metronome.update { it.copy(mixSub = v) } }
}

@Composable
private fun OptPractice(pal: Pal, s: MSettings) {
    val practice by Metronome.practiceSec.collectAsState()
    SubHeading(pal, "PRACTICE")
    SectionLabel(pal, "Count-in")
    Segmented(listOf("Off", "1 bar", "2 bars"), s.countIn) { i -> Metronome.update { it.copy(countIn = i) } }
    Spacer(Modifier.height(8.dp))
    SwitchRow(
        pal, "Tempo trainer",
        "Speed up (or slow down) automatically towards a target tempo", s.trainerOn
    ) { v -> Metronome.update { it.copy(trainerOn = v) } }
    if (s.trainerOn) {
        LabeledStepper(
            pal, "Change by", "${s.trainerStep} BPM",
            { Metronome.update { it.copy(trainerStep = (it.trainerStep - 1).coerceAtLeast(1)) } },
            { Metronome.update { it.copy(trainerStep = (it.trainerStep + 1).coerceAtMost(20)) } }
        )
        LabeledStepper(
            pal, "Every", "${s.trainerBars} bars",
            { Metronome.update { it.copy(trainerBars = (it.trainerBars - 1).coerceAtLeast(1)) } },
            { Metronome.update { it.copy(trainerBars = (it.trainerBars + 1).coerceAtMost(16)) } }
        )
        LabeledStepper(
            pal, "Target", "${s.trainerTarget} BPM",
            { Metronome.update { it.copy(trainerTarget = (it.trainerTarget - 5).coerceAtLeast(30)) } },
            { Metronome.update { it.copy(trainerTarget = (it.trainerTarget + 5).coerceAtMost(300)) } }
        )
    }
    SectionLabel(
        pal,
        if (s.randMute == 0) "Random mute: off" else "Random mute: ${s.randMute}% of beats"
    )
    AppSlider(
        pal, s.randMute.toFloat(),
        { v -> Metronome.update { it.copy(randMute = v.roundToInt()) } }, 0f..90f
    )
    Text(
        "Drops beats at random while the indicator keeps going, so you carry the pulse.",
        color = pal.sub, fontSize = 12.sp
    )
    Spacer(Modifier.height(14.dp))
    SwitchRow(
        pal, "Gap click",
        "Play some bars, then go silent for a few so you keep time yourself", s.gapOn
    ) { v -> Metronome.update { it.copy(gapOn = v) } }
    if (s.gapOn) {
        LabeledStepper(
            pal, "Play", "${s.gapPlay} bars",
            { Metronome.update { it.copy(gapPlay = (it.gapPlay - 1).coerceAtLeast(1)) } },
            { Metronome.update { it.copy(gapPlay = (it.gapPlay + 1).coerceAtMost(8)) } }
        )
        LabeledStepper(
            pal, "Silent", "${s.gapMute} bars",
            { Metronome.update { it.copy(gapMute = (it.gapMute - 1).coerceAtLeast(1)) } },
            { Metronome.update { it.copy(gapMute = (it.gapMute + 1).coerceAtMost(8)) } }
        )
    }
    Spacer(Modifier.height(14.dp))
    SectionLabel(pal, "Auto-stop timer")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0, 5, 10, 15, 30, 60).forEach { m ->
            Chip(pal, if (m == 0) "Off" else "$m min", s.timerMin == m) {
                Metronome.update { it.copy(timerMin = m) }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Total practice time", color = pal.text, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (practice >= 3600) "${practice / 3600} h ${practice % 3600 / 60} min" else "${practice / 60} min",
                color = pal.sub, style = MaterialTheme.typography.bodySmall
            )
        }
        TextButton(onClick = { Metronome.resetPractice() }) { Text("Reset") }
    }
}

@Composable
private fun OptSongMode(pal: Pal, s: MSettings) {
    SubHeading(pal, "SONG MODE")
    SwitchRow(
        pal, "Play through sections",
        "Step through the parts below, each for its own number of bars", s.songOn
    ) { v -> Metronome.update { it.copy(songOn = v) } }
    SectionList(pal)
}

@Composable
private fun OptFeel(pal: Pal, s: MSettings) {
    SubHeading(pal, "FEEL")
    SwitchRow(pal, "Tap starts playing", "Tapping the tempo also starts the metronome", s.tapStarts) { v ->
        Metronome.update { it.copy(tapStarts = v) }
    }
    SwitchRow(pal, "Vibration", "Buzz in time with every click; stronger on accents", s.vibrate) { v ->
        Metronome.update { it.copy(vibrate = v) }
        if (v) Metronome.buzz(true)
    }
    if (s.vibrate) {
        SectionLabel(pal, "Vibration strength")
        Segmented(listOf("Soft", "Normal", "Strong"), s.vibStrength) { i ->
            Metronome.update { it.copy(vibStrength = i) }
            Metronome.buzz(false)
        }
        Spacer(Modifier.height(10.dp))
        val off = s.vibOffset
        SectionLabel(
            pal,
            "Vibration timing: " + when {
                off == 0 -> "default"
                off > 0 -> "$off ms earlier"
                else -> "${-off} ms later"
            }
        )
        AppSlider(pal, off.toFloat(), { v -> Metronome.update { it.copy(vibOffset = v.roundToInt()) } }, -60f..60f)
        Text(
            "If the buzz feels ahead of or behind the sound on your phone, nudge this.",
            color = pal.sub, fontSize = 12.sp
        )
    }
    SwitchRow(pal, "Flash on beat", "Briefly tint the screen with your accent colour", s.flash) { v ->
        Metronome.update { it.copy(flash = v) }
    }
    SwitchRow(pal, "Keep screen on", "Stops the screen sleeping while something is playing", s.keepAwake) { v ->
        Metronome.update { it.copy(keepAwake = v) }
    }
    SwitchRow(
        pal, "Keep playing in other apps",
        "Background service with a Stop button in the notification shade", s.background
    ) { v -> Metronome.update { it.copy(background = v) } }
}

/** The song sections, in order, with controls to reorder, remove and add. */
@Composable
private fun SectionList(pal: Pal) {
    val list by Metronome.sections.collectAsState()
    val playingIdx by Metronome.sectionIdx.collectAsState()
    var name by remember { mutableStateOf("") }
    var bars by remember { mutableStateOf(8) }

    if (list.isEmpty()) {
        Text(
            "No sections yet. Set a tempo and time signature, then add it as a part below.",
            color = pal.sub, fontSize = 13.sp
        )
    }
    list.forEachIndexed { i, sec ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clip(RoundedCornerShape(LocalLook.current.chip))
                .background(if (i == playingIdx) pal.accent.copy(alpha = 0.25f) else pal.chip.copy(alpha = 0.5f))
                .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(sec.name, color = pal.text, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${sec.bpm} BPM · ${sec.beats}/4 · ${sec.bars} bars",
                    color = pal.sub, style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = { Metronome.moveSection(i, -1) }) { Text("↑") }
            TextButton(onClick = { Metronome.moveSection(i, 1) }) { Text("↓") }
            TextButton(onClick = { Metronome.deleteSection(i) }) { Text("✕") }
        }
    }
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(20) },
        label = { Text("Part name") },
        placeholder = { Text("Chorus") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    LabeledStepper(
        pal, "Length", "$bars bars",
        { bars = (bars - 1).coerceAtLeast(1) },
        { bars = (bars + 1).coerceAtMost(64) }
    )
    Button(
        onClick = {
            Metronome.addSection(name, bars)
            name = ""
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Add current settings as a part")
    }
}

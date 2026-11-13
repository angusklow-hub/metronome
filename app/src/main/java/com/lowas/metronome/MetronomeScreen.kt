package com.lowas.metronome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

val SUBDIVS = listOf(1 to "Quarter", 2 to "Eighths", 3 to "Triplets", 4 to "Sixteenths")

/** Round −/+ button that repeats while held. */
@Composable
private fun HoldBtn(pal: Pal, label: String, delta: Int) {
    Surface(
        shape = if (LocalLook.current.m3) RoundedCornerShape(18.dp) else CircleShape,
        color = if (LocalLook.current.ios) Color.White.copy(alpha = 0.16f) else pal.chip.copy(alpha = if (pal.glass) 0.55f else 1f),
        contentColor = pal.chipText,
        border = if (pal.glass) BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)) else null,
        modifier = Modifier
            .size(52.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        Metronome.setBpm(Metronome.settings.value.bpm + delta)
                        Fx.play(Fxk.TICK)
                        coroutineScope {
                            val job = launch {
                                delay(400)
                                while (true) {
                                    Metronome.setBpm(Metronome.settings.value.bpm + delta)
                                    Fx.play(Fxk.TICK)
                                    delay(70)
                                }
                            }
                            tryAwaitRelease()
                            job.cancel()
                        }
                    }
                )
            }
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 24.sp, fontWeight = FontWeight.Light)
        }
    }
}

/** One bar per beat. Tap a bar to cycle accent, normal, muted. */
@Composable
fun BeatPattern(pal: Pal, s: MSettings, playing: Boolean, beat: Int) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(76.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            for (i in 0 until s.beats) {
                val st = s.pattern.getOrElse(i) { '1' }
                val target: Dp = when (st) {
                    '2' -> 76.dp
                    '1' -> 48.dp
                    else -> 14.dp
                }
                val h by animateDpAsState(target, LocalLook.current.beat(), label = "bar")
                val base = when (st) {
                    '2' -> pal.sub
                    '1' -> if (LocalLook.current.ios) Color.White.copy(alpha = 0.2f) else pal.chip
                    else -> pal.chip.copy(alpha = 0.6f)
                }
                val on = playing && beat == i && st != '0'
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { Fx.play(Fxk.SELECT); Metronome.cyclePattern(i) },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(h)
                            .clip(RoundedCornerShape(LocalLook.current.bar))
                            .background(if (on) pal.accent else base)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Tap a beat to cycle: accent, normal, mute",
            color = pal.sub,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun MetronomeScreen(
    pal: Pal, s: MSettings, onToggle: () -> Unit, onEditBpm: () -> Unit, onOptions: () -> Unit
) {
    val playing by Metronome.playing.collectAsState()
    val beat by Metronome.beat.collectAsState()
    val elapsed by Metronome.elapsed.collectAsState()
    val barNo by Metronome.barNo.collectAsState()
    val sections by Metronome.sections.collectAsState()
    val secIdx by Metronome.sectionIdx.collectAsState()
    val subLabel = SUBDIVS.firstOrNull { it.first == s.subdiv }?.second ?: "Quarter"

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(4.dp))
        Stage(pal) {
            val m = Modifier
                .fillMaxWidth()
                .widthIn(max = 300.dp)
                .aspectRatio(1f)
            when (s.style) {
                1 -> PendulumView(pal, s.bpm, playing, elapsed, onEditBpm, onToggle, m)
                2 -> BlobView(pal, s.bpm, playing, beat, elapsed, onEditBpm, m)
                3 -> RingView(pal, s.bpm, playing, beat, elapsed, onEditBpm, m)
                4 -> LightsView(pal, s.bpm, playing, beat, elapsed, onEditBpm, m)
                else -> TempoDial(pal, s.bpm, playing, beat, elapsed, onEditBpm, m)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HoldBtn(pal, "−", -1)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (s.showName) {
                    Text(
                        tempoName(s.bpm),
                        color = pal.accent,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(2.dp))
                }
                val sectionName = sections.getOrNull(secIdx)?.name
                Text(
                    if (playing && sectionName != null) {
                        "$sectionName · bar $barNo · ${s.bpm} BPM"
                    } else if (playing) {
                        "Bar $barNo · ${s.beats} beats · $subLabel" +
                            (if (s.trainerOn) " · trainer" else "") +
                            (if (s.gapOn) " · gap" else "")
                    } else {
                        "${s.beats} beats · $subLabel · ${s.sound.label}" +
                            (if (s.polyOn) " · ${s.polyBeats}:${s.beats}" else "") +
                            (if (s.trainerOn) " · trainer" else "") +
                            (if (s.gapOn) " · gap" else "")
                    },
                    color = pal.sub,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            HoldBtn(pal, "+", 1)
        }
        if (s.showQuick) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(60, 72, 80, 90, 100, 110, 120, 132, 144, 160, 180).forEach { v ->
                    Chip(pal, "$v", s.bpm == v) { Metronome.setBpm(v) }
                }
            }
        }
        if (s.showPattern) {
            Spacer(Modifier.height(22.dp))
            BeatPattern(pal, s, playing, beat)
        }
        Spacer(Modifier.height(26.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (s.showTap) {
                // Tap registers on touch-down for accurate timing.
                Surface(
                    shape = RoundedCornerShape(LocalLook.current.button),
                    color = pal.chip.copy(alpha = if (pal.glass) 0.55f else 1f),
                    contentColor = pal.chipText,
                    border = if (pal.glass) BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)) else null,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .pointerInput(Unit) { detectTapGestures(onPress = { Fx.play(Fxk.TAP); Metronome.tap() }) }
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Tap", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.size(14.dp))
            }
            PlayButton(pal, playing, 84, onToggle)
            Spacer(Modifier.size(14.dp))
            val lk = LocalLook.current
            FilledTonalButton(
                onClick = { Fx.play(Fxk.SELECT); onOptions() },
                modifier = (if (s.showTap) Modifier.weight(1f) else Modifier.width(140.dp)).height(56.dp),
                shape = RoundedCornerShape(lk.button),
                colors = if (lk.ios) ButtonDefaults.filledTonalButtonColors(
                    containerColor = Color.White.copy(alpha = 0.16f), contentColor = pal.text
                ) else ButtonDefaults.filledTonalButtonColors(),
                border = if (lk.ios) BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)) else null,
            ) {
                Text("Options")
            }
        }
    }
}

@Composable
private fun PlayGlyph(color: Color, dp: Dp, playing: Boolean) {
    Canvas(Modifier.size(dp)) {
        if (playing) {
            drawRoundRect(
                color,
                topLeft = Offset(size.width * 0.1f, size.height * 0.1f),
                size = Size(size.width * 0.8f, size.height * 0.8f),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
        } else {
            val p = Path().apply {
                moveTo(size.width * 0.22f, 0f)
                lineTo(size.width, size.height / 2f)
                lineTo(size.width * 0.22f, size.height)
                close()
            }
            drawPath(p, color)
        }
    }
}

/**
 * The play/stop control, built differently per style: an extended pill with a label on Material 3,
 * a big round button on One UI, and a glowing glass capsule on Glass.
 */
@Composable
fun PlayButton(pal: Pal, playing: Boolean, sizeDp: Int, onClick: () -> Unit) {
    val look = LocalLook.current
    val src = remember { MutableInteractionSource() }
    val click = { Fx.play(if (playing) Fxk.OFF else Fxk.ON); onClick() }
    when (look.skin) {
        Skin.M3 -> ExtendedFloatingActionButton(
            text = { Text(if (playing) "Stop" else "Start", style = MaterialTheme.typography.titleMedium) },
            icon = { PlayGlyph(pal.onAccent, 20.dp, playing) },
            onClick = click,
            modifier = Modifier.height(72.dp).pressSpring(src),
            shape = RoundedCornerShape(24.dp),
            containerColor = pal.accent,
            contentColor = pal.onAccent,
            interactionSource = src
        )

        Skin.ONEUI -> FloatingActionButton(
            onClick = click,
            interactionSource = src,
            modifier = Modifier.size(sizeDp.dp).pressSpring(src),
            shape = CircleShape,
            containerColor = pal.accent,
            contentColor = pal.onAccent
        ) { PlayGlyph(pal.onAccent, (sizeDp * 0.34f).dp, playing) }

        Skin.IOS -> {
            val shape = RoundedCornerShape(36.dp)
            Box(
                Modifier
                    .width((sizeDp * 1.5f).dp)
                    .height(72.dp)
                    .pressSpring(src)
                    .drawBehind {
                        drawRoundRect(
                            pal.accent.copy(alpha = if (playing) 0.45f else 0.25f),
                            topLeft = Offset(-6.dp.toPx(), 4.dp.toPx()),
                            size = Size(size.width + 12.dp.toPx(), size.height + 8.dp.toPx()),
                            cornerRadius = CornerRadius(40.dp.toPx())
                        )
                    }
                    .clip(shape)
                    .background(Brush.verticalGradient(listOf(pal.accent.copy(alpha = 0.95f), pal.accent.copy(alpha = 0.72f))))
                    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.1f))), shape)
                    .clickable(interactionSource = src, indication = null) { click() },
                contentAlignment = Alignment.Center
            ) { PlayGlyph(pal.onAccent, 26.dp, playing) }
        }
    }
}

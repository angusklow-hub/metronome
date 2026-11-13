package com.lowas.metronome

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private fun fmtTime(sec: Int) = String.format("%d:%02d", sec / 60, sec % 60)

private fun angleOf(o: Offset, cx: Float, cy: Float): Float =
    Math.toDegrees(atan2((o.y - cy).toDouble(), (o.x - cx).toDouble())).toFloat()

/** The surface all three tempo displays sit on, so they share one look. */
@Composable
fun Stage(pal: Pal, content: @Composable () -> Unit) {
    val look = LocalLook.current
    val shape = RoundedCornerShape(look.stage)
    val skinMod = when (look.skin) {
        // Material: a solid tonal block, no glass.
        Skin.M3 -> Modifier.background(androidx.compose.ui.graphics.lerp(pal.card, pal.accent, 0.16f), shape)
        // One UI: a calm card with only a touch of glass.
        Skin.ONEUI -> Modifier.glassSurface(pal, shape)
        // Glass: frosted panel with a coloured glow shining through it.
        Skin.IOS -> Modifier.glassSurface(pal, shape).drawBehind {
            val r = size.minDimension * 0.7f
            drawCircle(
                Brush.radialGradient(listOf(pal.accent.copy(alpha = 0.3f), Color.Transparent), center = center, radius = r),
                radius = r
            )
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .then(skinMod)
            .padding(look.cardPad),
        contentAlignment = Alignment.Center
    ) { content() }
}

/** The tempo number shared by every style. Tap it to type a tempo. */
@Composable
private fun Readout(
    bpm: Int, playing: Boolean, elapsed: Int, onEdit: () -> Unit,
    valueColor: Color, labelColor: Color, small: Boolean = false
) {
    val ty = MaterialTheme.typography
    val showTime = Metronome.settings.collectAsState().value.showTimer
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable { onEdit() }
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Text("$bpm", color = valueColor, style = if (small) ty.displayMedium else ty.displayLarge)
        Text(if (playing && showTime) fmtTime(elapsed) else "BPM", color = labelColor, style = ty.labelLarge)
    }
}

/** Rotary dial: drag around the ring; the ticks turn with your finger. */
@Composable
fun TempoDial(
    pal: Pal, bpm: Int, playing: Boolean, beat: Int, elapsed: Int,
    onEdit: () -> Unit, modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val look = LocalLook.current
    val pulse = remember { Animatable(0f) }
    val rot by animateFloatAsState(
        bpm * 3f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessHigh), label = "dialRot"
    )
    val tick by Metronome.tick.collectAsState()
    LaunchedEffect(tick, playing) {
        if (playing && tick > 0) {
            pulse.snapTo(if (beat == 0) 1f else 0.55f)
            pulse.animateTo(0f, look.motion())
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // A real knob: the ticks turn exactly with your finger (3 degrees per BPM,
                    // clockwise is faster) and a flick keeps it spinning.
                    val tracker = VelocityTracker()
                    var fling: Job? = null
                    var acc = 0f
                    var lastAng = 0f
                    var unwrapped = 0f
                    fun angleOf(p: Offset): Float =
                        Math.toDegrees(
                            atan2((p.y - size.height / 2f).toDouble(), (p.x - size.width / 2f).toDouble())
                        ).toFloat()
                    fun bump(deg: Float) {
                        acc += deg / 3f
                        val step = acc.toInt()
                        if (step != 0) {
                            acc -= step
                            val before = Metronome.settings.value.bpm
                            Metronome.setBpm(before + step)
                            if (Metronome.settings.value.bpm != before) {
                                Fx.play(Fxk.TICK, Metronome.settings.value.dialHaptic)
                            }
                        }
                    }
                    detectDragGestures(
                        onDragStart = { o ->
                            fling?.cancel()
                            tracker.resetTracking()
                            acc = 0f
                            unwrapped = 0f
                            lastAng = angleOf(o)
                        },
                        onDragEnd = {
                            val v = tracker.calculateVelocity().x
                            if (abs(v) > 150f) {
                                fling = scope.launch {
                                    var last = 0f
                                    Animatable(0f).animateDecay(v, exponentialDecay<Float>(frictionMultiplier = 1.3f)) {
                                        bump(value - last)
                                        last = value
                                    }
                                }
                            }
                        }
                    ) { change, _ ->
                        change.consume()
                        val a = angleOf(change.position)
                        val dist = (change.position - Offset(size.width / 2f, size.height / 2f)).getDistance()
                        if (dist < 36.dp.toPx()) {
                            // Too close to the middle the angle jumps about; ignore it.
                            lastAng = a
                            return@detectDragGestures
                        }
                        var d = a - lastAng
                        if (d > 180f) d -= 360f
                        if (d < -180f) d += 360f
                        lastAng = a
                        unwrapped += d
                        tracker.addPosition(change.uptimeMillis, Offset(unwrapped, 0f))
                        bump(d)
                    }
                }
        ) {
            drawDial(pal, look.skin, bpm, rot, pulse.value)
        }
        Readout(bpm, playing, elapsed, onEdit, pal.text, if (playing) pal.accent else pal.sub)
    }
}

/**
 * Traditional pendulum metronome. It clicks at each end of the swing. Tap it to stop or start,
 * drag up or down to change tempo, tap the number to type one.
 */
@Composable
fun PendulumView(
    pal: Pal, bpm: Int, playing: Boolean, elapsed: Int,
    onEdit: () -> Unit, onToggle: () -> Unit, modifier: Modifier = Modifier
) {
    val tick by Metronome.tick.collectAsState()
    val look = LocalLook.current
    val angle = remember { Animatable(0f) }
    var side by remember { mutableStateOf(1) }
    val maxAngle = 24f
    LaunchedEffect(tick, playing) {
        if (playing && tick > 0) {
            side = -side
            if (abs(angle.value) < 1f) angle.snapTo(-side * maxAngle)
            val period = (60000f / bpm).toInt().coerceAtLeast(120)
            angle.animateTo(side * maxAngle, tween(period, easing = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)))
        } else if (!playing) {
            angle.animateTo(0f, tween(350))
        }
    }

    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(onToggle) { detectTapGestures(onTap = { onToggle() }) }
                .pointerInput(Unit) {
                    fun set(y: Float) {
                        val pivotY = size.height * 0.7f
                        val len = size.height * 0.64f
                        val t = ((pivotY - y) / len).coerceIn(0.2f, 0.9f)
                        val nb = (30f + (0.9f - t) / 0.7f * 270f).toInt().coerceIn(30, 300)
                        if (nb != Metronome.settings.value.bpm) Fx.play(Fxk.TICK, Metronome.settings.value.dialHaptic)
                        Metronome.setBpm(nb)
                    }
                    detectDragGestures(onDragStart = { set(it.y) }) { change, _ ->
                        change.consume()
                        set(change.position.y)
                    }
                }
        ) {
            drawPendulum(pal, look.skin, bpm, angle.value)
        }
        Box(Modifier.padding(bottom = 2.dp)) {
            Readout(bpm, playing, elapsed, onEdit, pal.text, if (playing) pal.accent else pal.sub, small = true)
        }
    }
}

/** Organic blob that wobbles gently and swells on each beat. Drag up or down to change tempo. */
@Composable
fun BlobView(
    pal: Pal, bpm: Int, playing: Boolean, beat: Int, elapsed: Int,
    onEdit: () -> Unit, modifier: Modifier = Modifier
) {
    val tick by Metronome.tick.collectAsState()
    val look = LocalLook.current
    val scale = remember { Animatable(1f) }
    val inf = rememberInfiniteTransition(label = "blob")
    val phase by inf.animateFloat(
        0f, (2 * PI).toFloat(),
        infiniteRepeatable(tween(7000, easing = LinearEasing)),
        label = "phase"
    )
    LaunchedEffect(tick, playing) {
        if (playing && tick > 0) {
            scale.snapTo(1f + look.swell * (if (beat == 0) 1f else 0.6f))
            scale.animateTo(1f, look.beat())
        } else if (!playing) {
            scale.animateTo(1f, tween(250))
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var acc = 0f
                    detectDragGestures { change, drag ->
                        change.consume()
                        acc -= drag.y / 6.dp.toPx()
                        val step = acc.toInt()
                        if (step != 0) {
                            acc -= step
                            Metronome.setBpm(Metronome.settings.value.bpm + step)
                            Fx.play(Fxk.TICK, Metronome.settings.value.dialHaptic)
                        }
                    }
                }
        ) {
            drawBlob(pal, look.skin, scale.value, phase, playing)
        }
        if (look.ios) {
            Readout(bpm, playing, elapsed, onEdit, pal.text, pal.text.copy(alpha = 0.75f))
        } else {
            Readout(bpm, playing, elapsed, onEdit, pal.onAccent, pal.onAccent.copy(alpha = 0.8f))
        }
    }
}

/** Segmented ring: one arc per beat that fills as the beat plays. Accent beats are thicker. */
@Composable
fun RingView(
    pal: Pal, bpm: Int, playing: Boolean, beat: Int, elapsed: Int,
    onEdit: () -> Unit, modifier: Modifier = Modifier
) {
    val s by Metronome.settings.collectAsState()
    val look = LocalLook.current
    val tick by Metronome.tick.collectAsState()
    val prog = remember { Animatable(0f) }
    LaunchedEffect(tick, playing) {
        if (playing && tick > 0) {
            prog.snapTo(0f)
            prog.animateTo(1f, tween((60000f / bpm).toInt().coerceAtLeast(100), easing = LinearEasing))
        } else if (!playing) {
            prog.snapTo(0f)
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawRing(pal, look.skin, s, playing, beat, prog.value)
        }
        Readout(bpm, playing, elapsed, onEdit, pal.text, if (playing) pal.accent else pal.sub)
    }
}

/** A light per beat, numbered. The current beat glows; accents wear a ring. */
@Composable
fun LightsView(
    pal: Pal, bpm: Int, playing: Boolean, beat: Int, elapsed: Int,
    onEdit: () -> Unit, modifier: Modifier = Modifier
) {
    val s by Metronome.settings.collectAsState()
    val look = LocalLook.current
    val n = s.beats
    val cols = when { n <= 4 -> n; n <= 8 -> 4; else -> 6 }
    val rows = (n + cols - 1) / cols
    val sz = if (cols <= 4) 64.dp else 46.dp
    Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        for (r in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (c in 0 until cols) {
                    val i = r * cols + c
                    if (i >= n) {
                        Spacer(Modifier.size(sz))
                    } else {
                        val st = s.pattern.getOrElse(i) { '1' }
                        val on = playing && beat == i && st != '0'
                        val fill by animateColorAsState(
                            when {
                                on -> pal.accent
                                st == '0' -> pal.face.copy(alpha = 0.5f)
                                else -> pal.chip
                            },
                            tween(90), label = "light"
                        )
                        val sc by animateFloatAsState(
                            if (on) 1f + look.swell * 0.6f else 1f, look.beat(), label = "lightScale"
                        )
                        val shape = when (look.skin) {
                            Skin.M3 -> CircleShape
                            Skin.ONEUI -> RoundedCornerShape(16.dp)
                            Skin.IOS -> RoundedCornerShape(50)
                        }
                        val w = if (look.skin == Skin.IOS) sz * 0.78f else sz
                        val glassFill = if (look.ios) {
                            if (on) pal.accent.copy(alpha = 0.9f) else Color.White.copy(alpha = if (st == '0') 0.06f else 0.16f)
                        } else fill
                        Box(
                            Modifier
                                .size(w, if (look.skin == Skin.IOS) sz * 0.5f else sz)
                                .scale(sc)
                                .clip(shape)
                                .background(glassFill)
                                .then(
                                    when {
                                        look.ios -> Modifier.border(1.dp, Color.White.copy(alpha = if (on) 0.7f else 0.3f), shape)
                                        st == '2' -> Modifier.border(2.dp, pal.accent, shape)
                                        else -> Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${i + 1}",
                                color = if (on) pal.onAccent else pal.chipText,
                                style = if (look.skin == Skin.ONEUI) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(6.dp))
        Readout(bpm, playing, elapsed, onEdit, pal.text, if (playing) pal.accent else pal.sub, small = true)
    }
}

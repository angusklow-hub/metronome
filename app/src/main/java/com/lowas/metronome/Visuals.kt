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
    Box(
        Modifier
            .fillMaxWidth()
            .glassSurface(pal, RoundedCornerShape(LocalLook.current.stage))
            .padding(LocalLook.current.cardPad),
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
                    // Drag right to speed up, left to slow down; flick to let it keep spinning.
                    val tracker = VelocityTracker()
                    var fling: Job? = null
                    var acc = 0f
                    val perBpm = 6.dp.toPx()
                    fun bump(px: Float) {
                        acc += px / perBpm
                        val step = acc.toInt()
                        if (step != 0) {
                            acc -= step
                            Metronome.setBpm(Metronome.settings.value.bpm + step)
                        }
                    }
                    detectDragGestures(
                        onDragStart = {
                            fling?.cancel()
                            tracker.resetTracking()
                            acc = 0f
                        },
                        onDragEnd = {
                            val v = tracker.calculateVelocity().x
                            if (abs(v) > 300f) {
                                fling = scope.launch {
                                    var last = 0f
                                    Animatable(0f).animateDecay(v, exponentialDecay<Float>()) {
                                        bump(value - last)
                                        last = value
                                    }
                                }
                            }
                        }
                    ) { change, drag ->
                        change.consume()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        bump(drag.x)
                        if (Metronome.settings.value.dialHaptic) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
        ) {
            val ring = size.minDimension / 2f - 4.dp.toPx()
            drawCircle(pal.face, radius = ring)
            if (pal.glass) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(center.x - ring * 0.35f, center.y - ring * 0.5f), radius = ring * 1.1f
                    ),
                    radius = ring
                )
            }
            drawCircle(pal.outline, radius = ring, style = Stroke(2.dp.toPx()))
            if (pulse.value > 0.01f) {
                drawCircle(pal.accent.copy(alpha = pulse.value * 0.6f), radius = ring, style = Stroke(8.dp.toPx()))
            }
            rotate(degrees = (bpm * 3f) % 360f, pivot = center) {
                for (i in 0 until 60) {
                    val major = i % 5 == 0
                    val a = Math.toRadians(i * 6.0)
                    val r1 = ring - 20.dp.toPx()
                    val r2 = r1 - (if (major) 16.dp.toPx() else 9.dp.toPx())
                    drawLine(
                        if (major) pal.text.copy(alpha = 0.75f) else pal.sub.copy(alpha = 0.45f),
                        Offset(center.x + (r1 * cos(a)).toFloat(), center.y + (r1 * sin(a)).toFloat()),
                        Offset(center.x + (r2 * cos(a)).toFloat(), center.y + (r2 * sin(a)).toFloat()),
                        strokeWidth = if (major) 2.5.dp.toPx() else 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            drawCircle(pal.accent, radius = 4.dp.toPx(), center = Offset(center.x, center.y - ring + 9.dp.toPx()))
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
                        Metronome.setBpm((30f + (0.9f - t) / 0.7f * 270f).toInt())
                    }
                    detectDragGestures(onDragStart = { set(it.y) }) { change, _ ->
                        change.consume()
                        set(change.position.y)
                    }
                }
        ) {
            val bw = min(size.width, size.height)
            val h = size.height
            val cx = size.width / 2f
            val pivot = Offset(cx, h * 0.7f)
            val len = h * 0.64f

            val body = Path().apply {
                moveTo(cx - bw * 0.09f, h * 0.12f)
                lineTo(cx + bw * 0.09f, h * 0.12f)
                lineTo(cx + bw * 0.42f, h * 0.99f)
                lineTo(cx - bw * 0.42f, h * 0.99f)
                close()
            }
            drawPath(body, pal.face)
            drawPath(body, pal.outline, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
            for (i in 0..8) {
                val y = pivot.y - (0.2f + 0.7f * i / 8f) * len
                if (y > h * 0.15f) {
                    drawLine(
                        pal.sub.copy(alpha = 0.5f),
                        Offset(cx - 22.dp.toPx(), y), Offset(cx - 14.dp.toPx(), y),
                        strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round
                    )
                }
            }
            rotate(degrees = angle.value, pivot = pivot) {
                drawLine(pal.text.copy(alpha = 0.85f), pivot, Offset(pivot.x, pivot.y - len), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                val t = 0.9f - (bpm - 30f) / 270f * 0.7f
                val ww = bw * 0.22f
                val wh = bw * 0.14f
                drawRoundRect(
                    pal.accent,
                    topLeft = Offset(pivot.x - ww / 2f, pivot.y - t * len - wh / 2f),
                    size = Size(ww, wh),
                    cornerRadius = CornerRadius(wh * 0.4f)
                )
            }
            drawCircle(pal.accent, radius = 8.dp.toPx(), center = pivot)
            drawCircle(pal.face, radius = 3.dp.toPx(), center = pivot)
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
                        }
                    }
                }
        ) {
            val base = size.minDimension / 2f * 0.64f
            val s = scale.value
            drawCircle(pal.face, radius = size.minDimension / 2f - 4.dp.toPx())
            drawCircle(pal.accent.copy(alpha = 0.14f), radius = base * (1.2f + (s - 1f) * 0.8f))

            val n = 10
            val wob = if (playing) 0.075f else 0.045f
            val pts = List(n) { k ->
                val a = 2 * PI * k / n
                val r = base * s * (1f + wob * sin(phase * 2f + k * 1.7f) + wob * 0.7f * sin(phase * 3f - k * 2.3f))
                Offset(center.x + (r * cos(a)).toFloat(), center.y + (r * sin(a)).toFloat())
            }
            fun mid(a: Offset, b: Offset) = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
            val path = Path()
            val start = mid(pts[n - 1], pts[0])
            path.moveTo(start.x, start.y)
            for (i in 0 until n) {
                val m = mid(pts[i], pts[(i + 1) % n])
                path.quadraticBezierTo(pts[i].x, pts[i].y, m.x, m.y)
            }
            path.close()
            drawPath(path, pal.accent)
            drawPath(
                path,
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = if (pal.glass) 0.38f else 0.15f), Color.Transparent),
                    center = Offset(center.x - base * 0.35f, center.y - base * 0.45f), radius = base * 1.1f
                )
            )
        }
        Readout(bpm, playing, elapsed, onEdit, pal.onAccent, pal.onAccent.copy(alpha = 0.8f))
    }
}

/** Segmented ring: one arc per beat that fills as the beat plays. Accent beats are thicker. */
@Composable
fun RingView(
    pal: Pal, bpm: Int, playing: Boolean, beat: Int, elapsed: Int,
    onEdit: () -> Unit, modifier: Modifier = Modifier
) {
    val s by Metronome.settings.collectAsState()
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
            val n = s.beats
            val gap = if (n > 1) 7f else 0f
            val sw = 360f / n
            val r = size.minDimension / 2f - 24.dp.toPx()
            val tl = Offset(center.x - r, center.y - r)
            val box = Size(r * 2f, r * 2f)
            for (i in 0 until n) {
                val st = s.pattern.getOrElse(i) { '1' }
                val width = (if (st == '2') 22.dp else 13.dp).toPx()
                val start = -90f + i * sw + gap / 2f
                val sweep = sw - gap
                drawArc(
                    if (st == '0') pal.outline.copy(alpha = 0.4f) else pal.face,
                    start, sweep, false, tl, box, style = Stroke(width)
                )
                val frac = when {
                    !playing -> 0f
                    i < beat -> 1f
                    i == beat -> prog.value
                    else -> 0f
                }
                if (frac > 0f && st != '0') {
                    drawArc(
                        if (i < beat) pal.accent.copy(alpha = 0.45f) else pal.accent,
                        start, sweep * frac, false, tl, box, style = Stroke(width)
                    )
                }
            }
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
    val sz = if (cols <= 4) 56.dp else 42.dp
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
                        Box(
                            Modifier
                                .size(sz)
                                .scale(sc)
                                .clip(CircleShape)
                                .background(fill)
                                .then(if (st == '2') Modifier.border(2.dp, pal.accent, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${i + 1}",
                                color = if (on) pal.onAccent else pal.chipText,
                                style = MaterialTheme.typography.titleMedium
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

package com.lowas.metronome

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * One drawing routine per display, each with three distinct looks:
 *   Material 3  chunky tonal shapes that bounce
 *   One UI      thin, clean, flat lines with generous space
 *   iOS 27      translucent glass with specular highlights and hairline rims
 */

private fun polar(c: Offset, r: Float, deg: Float): Offset {
    val a = Math.toRadians(deg.toDouble())
    return Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
}

private fun white(a: Float) = Color.White.copy(alpha = a)

private fun DrawScope.dialTicks(
    ring: Float, rot: Float, inset: Float,
    major: Color, minor: Color,
    wMajor: Float, wMinor: Float, lenMajor: Float, lenMinor: Float
) {
    rotate(degrees = rot % 360f, pivot = center) {
        for (i in 0 until 60) {
            val isMajor = i % 5 == 0
            val r1 = ring - inset
            val r2 = r1 - (if (isMajor) lenMajor else lenMinor)
            drawLine(
                if (isMajor) major else minor,
                polar(center, r1, i * 6f), polar(center, r2, i * 6f),
                strokeWidth = if (isMajor) wMajor else wMinor,
                cap = StrokeCap.Round
            )
        }
    }
}

// ---------------------------------------------------------------- dial

fun DrawScope.drawDial(pal: Pal, skin: Skin, bpm: Int, rot: Float, rawPulse: Float) {
    val pulse = rawPulse.coerceIn(0f, 1f)
    val ring = size.minDimension / 2f - 4.dp.toPx()
    when (skin) {
        Skin.M3 -> {
            drawCircle(pal.face, radius = ring)
            drawCircle(pal.outline, radius = ring, style = Stroke(2.dp.toPx()))
            if (pulse > 0.01f) {
                drawCircle(pal.accent.copy(alpha = pulse * 0.6f), radius = ring, style = Stroke(8.dp.toPx()))
            }
            dialTicks(
                ring, rot, 20.dp.toPx(), pal.text.copy(alpha = 0.75f), pal.sub.copy(alpha = 0.45f),
                2.5.dp.toPx(), 1.5.dp.toPx(), 16.dp.toPx(), 9.dp.toPx()
            )
            drawCircle(pal.accent, radius = 4.dp.toPx(), center = Offset(center.x, center.y - ring + 9.dp.toPx()))
        }

        Skin.ONEUI -> {
            // A thin arc gauge: the tempo is where the handle sits between 30 and 300.
            val r = ring - 10.dp.toPx()
            val tl = Offset(center.x - r, center.y - r)
            val box = Size(r * 2f, r * 2f)
            val frac = ((bpm - 30f) / 270f).coerceIn(0f, 1f)
            drawArc(
                pal.outline.copy(alpha = 0.8f), 135f, 270f, false, tl, box,
                style = Stroke(7.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                pal.accent, 135f, 270f * frac, false, tl, box,
                style = Stroke(7.dp.toPx() + 5.dp.toPx() * pulse, cap = StrokeCap.Round)
            )
            val thumb = polar(center, r, 135f + 270f * frac)
            drawCircle(Color.White, radius = 11.dp.toPx(), center = thumb)
            drawCircle(pal.accent, radius = 7.dp.toPx(), center = thumb)
        }

        Skin.IOS -> {
            // A glass lens with hairline ticks and a bright rim.
            drawCircle(pal.face, radius = ring)
            drawCircle(
                Brush.radialGradient(
                    listOf(white(0.22f), Color.Transparent),
                    center = Offset(center.x - ring * 0.35f, center.y - ring * 0.5f), radius = ring * 1.1f
                ),
                radius = ring
            )
            if (pulse > 0.01f) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(pal.accent.copy(alpha = pulse * 0.5f), Color.Transparent),
                        center = center, radius = ring * 1.14f
                    ),
                    radius = ring * 1.14f
                )
            }
            drawCircle(
                Brush.verticalGradient(listOf(white(0.55f), white(0.06f))),
                radius = ring, style = Stroke(1.5.dp.toPx())
            )
            dialTicks(
                ring, rot, 16.dp.toPx(), pal.text.copy(alpha = 0.85f), pal.sub.copy(alpha = 0.5f),
                1.6.dp.toPx(), 1.dp.toPx(), 14.dp.toPx(), 7.dp.toPx()
            )
            drawLine(
                pal.accent,
                Offset(center.x, center.y - ring + 4.dp.toPx()), Offset(center.x, center.y - ring + 20.dp.toPx()),
                strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round
            )
        }
    }
}

// ---------------------------------------------------------------- pendulum

fun DrawScope.drawPendulum(pal: Pal, skin: Skin, bpm: Int, angle: Float) {
    val bw = min(size.width, size.height)
    val h = size.height
    val cx = size.width / 2f
    val pivot = Offset(cx, h * 0.7f)
    val len = h * 0.64f
    val t = 0.9f - (bpm - 30f) / 270f * 0.7f
    val weightCenter = Offset(pivot.x, pivot.y - t * len)

    fun body() = Path().apply {
        moveTo(cx - bw * 0.09f, h * 0.12f)
        lineTo(cx + bw * 0.09f, h * 0.12f)
        lineTo(cx + bw * 0.42f, h * 0.99f)
        lineTo(cx - bw * 0.42f, h * 0.99f)
        close()
    }

    fun scaleMarks(color: Color) {
        for (i in 0..8) {
            val y = pivot.y - (0.2f + 0.7f * i / 8f) * len
            if (y > h * 0.15f) {
                drawLine(
                    color, Offset(cx - 22.dp.toPx(), y), Offset(cx - 14.dp.toPx(), y),
                    strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round
                )
            }
        }
    }

    when (skin) {
        Skin.M3 -> {
            drawPath(body(), pal.face)
            drawPath(body(), pal.outline, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
            scaleMarks(pal.sub.copy(alpha = 0.5f))
            rotate(degrees = angle, pivot = pivot) {
                drawLine(
                    pal.text.copy(alpha = 0.85f), pivot, Offset(pivot.x, pivot.y - len),
                    strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round
                )
                val ww = bw * 0.22f
                val wh = bw * 0.14f
                drawRoundRect(
                    pal.accent,
                    topLeft = Offset(weightCenter.x - ww / 2f, weightCenter.y - wh / 2f),
                    size = Size(ww, wh),
                    cornerRadius = CornerRadius(wh * 0.4f)
                )
            }
            drawCircle(pal.accent, radius = 8.dp.toPx(), center = pivot)
            drawCircle(pal.face, radius = 3.dp.toPx(), center = pivot)
        }

        Skin.ONEUI -> {
            // A slim pill track and a plain round weight: nothing but what you need.
            val trackW = 22.dp.toPx()
            drawRoundRect(
                pal.face,
                topLeft = Offset(cx - trackW / 2f, h * 0.08f),
                size = Size(trackW, h * 0.9f),
                cornerRadius = CornerRadius(trackW / 2f)
            )
            for (i in 0..8) {
                val y = pivot.y - (0.2f + 0.7f * i / 8f) * len
                drawCircle(pal.sub.copy(alpha = 0.45f), radius = 1.8.dp.toPx(), center = Offset(cx + 22.dp.toPx(), y))
            }
            rotate(degrees = angle, pivot = pivot) {
                drawLine(
                    pal.text.copy(alpha = 0.8f), pivot, Offset(pivot.x, pivot.y - len),
                    strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round
                )
                drawCircle(pal.accent, radius = bw * 0.075f, center = weightCenter)
            }
            drawCircle(pal.accent, radius = 7.dp.toPx(), center = pivot)
            drawCircle(Color.White, radius = 2.5.dp.toPx(), center = pivot)
        }

        Skin.IOS -> {
            // A glass case, a hairline rod and a glossy bead.
            drawPath(body(), Brush.verticalGradient(listOf(white(0.2f), white(0.05f))))
            drawPath(
                body(), Brush.verticalGradient(listOf(white(0.55f), white(0.06f))),
                style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round)
            )
            scaleMarks(white(0.4f))
            rotate(degrees = angle, pivot = pivot) {
                drawLine(
                    white(0.85f), pivot, Offset(pivot.x, pivot.y - len),
                    strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round
                )
                val r = bw * 0.085f
                drawCircle(
                    Brush.radialGradient(
                        listOf(white(0.7f), pal.accent, pal.accent.copy(alpha = 0.75f)),
                        center = Offset(weightCenter.x - r * 0.3f, weightCenter.y - r * 0.35f), radius = r * 1.4f
                    ),
                    radius = r, center = weightCenter
                )
                drawCircle(
                    Brush.verticalGradient(listOf(white(0.6f), white(0.1f))),
                    radius = r, center = weightCenter, style = Stroke(1.dp.toPx())
                )
            }
            drawCircle(white(0.9f), radius = 6.dp.toPx(), center = pivot)
            drawCircle(pal.accent, radius = 3.dp.toPx(), center = pivot)
        }
    }
}

// ---------------------------------------------------------------- blob

fun DrawScope.drawBlob(pal: Pal, skin: Skin, scale: Float, phase: Float, playing: Boolean) {
    val base = size.minDimension / 2f * 0.64f
    val s = scale
    val well = size.minDimension / 2f - 4.dp.toPx()

    when (skin) {
        Skin.M3 -> {
            drawCircle(pal.face, radius = well)
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
                    listOf(white(0.15f), Color.Transparent),
                    center = Offset(center.x - base * 0.35f, center.y - base * 0.45f), radius = base * 1.1f
                )
            )
        }

        Skin.ONEUI -> {
            // A clean disc with calm ripples that spread on each beat.
            drawCircle(pal.accent.copy(alpha = 0.09f), radius = base * (1.42f + (s - 1f) * 2.2f))
            drawCircle(pal.accent.copy(alpha = 0.16f), radius = base * (1.2f + (s - 1f) * 1.6f))
            drawCircle(pal.accent, radius = base * s)
        }

        Skin.IOS -> {
            // A glass orb: a soft glow, a translucent body, a bright rim and a specular highlight.
            val r = base * s * (1f + 0.02f * sin(phase * 2f))
            drawCircle(
                Brush.radialGradient(
                    listOf(pal.accent.copy(alpha = 0.34f), Color.Transparent),
                    center = center, radius = r * 1.55f
                ),
                radius = r * 1.55f
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(pal.accent.copy(alpha = 0.85f), pal.accent.copy(alpha = 0.4f)),
                    center = Offset(center.x - r * 0.25f, center.y - r * 0.3f), radius = r * 1.3f
                ),
                radius = r
            )
            drawCircle(
                Brush.verticalGradient(listOf(white(0.7f), white(0.08f))),
                radius = r, style = Stroke(1.5.dp.toPx())
            )
            drawOval(
                Brush.verticalGradient(listOf(white(0.6f), Color.Transparent)),
                topLeft = Offset(center.x - r * 0.6f, center.y - r * 0.88f),
                size = Size(r * 1.2f, r * 0.6f)
            )
        }
    }
}

// ---------------------------------------------------------------- ring

fun DrawScope.drawRing(pal: Pal, skin: Skin, s: MSettings, playing: Boolean, beat: Int, prog: Float) {
    val n = s.beats
    val sw = 360f / n
    val r = size.minDimension / 2f - 24.dp.toPx()
    val tl = Offset(center.x - r, center.y - r)
    val box = Size(r * 2f, r * 2f)

    for (i in 0 until n) {
        val st = s.pattern.getOrElse(i) { '1' }
        val accent = st == '2'
        val muted = st == '0'
        val frac = when {
            !playing -> 0f
            i < beat -> 1f
            i == beat -> prog
            else -> 0f
        }
        when (skin) {
            Skin.M3 -> {
                // Chunky flat segments with square ends.
                val gap = if (n > 1) 7f else 0f
                val width = (if (accent) 22.dp else 13.dp).toPx()
                val start = -90f + i * sw + gap / 2f
                val sweep = sw - gap
                drawArc(
                    if (muted) pal.outline.copy(alpha = 0.4f) else pal.face,
                    start, sweep, false, tl, box, style = Stroke(width)
                )
                if (frac > 0f && !muted) {
                    drawArc(
                        if (i < beat) pal.accent.copy(alpha = 0.45f) else pal.accent,
                        start, sweep * frac, false, tl, box, style = Stroke(width)
                    )
                }
            }

            Skin.ONEUI -> {
                // Thin rounded lines with open air between them.
                val gap = if (n > 1) 16f else 0f
                val width = (if (accent) 11.dp else 7.dp).toPx()
                val start = -90f + i * sw + gap / 2f
                val sweep = (sw - gap).coerceAtLeast(2f)
                drawArc(
                    pal.outline.copy(alpha = if (muted) 0.35f else 0.8f),
                    start, sweep, false, tl, box, style = Stroke(width, cap = StrokeCap.Round)
                )
                if (frac > 0f && !muted) {
                    drawArc(
                        if (i < beat) pal.accent.copy(alpha = 0.5f) else pal.accent,
                        start, (sweep * frac).coerceAtLeast(0.5f), false, tl, box,
                        style = Stroke(width, cap = StrokeCap.Round)
                    )
                }
            }

            Skin.IOS -> {
                // Glass tracks with a glowing, rounded progress.
                val gap = if (n > 1) 16f else 0f
                val width = (if (accent) 17.dp else 12.dp).toPx()
                val start = -90f + i * sw + gap / 2f
                val sweep = (sw - gap).coerceAtLeast(2f)
                drawArc(
                    white(if (muted) 0.06f else 0.14f),
                    start, sweep, false, tl, box, style = Stroke(width, cap = StrokeCap.Round)
                )
                if (frac > 0f && !muted) {
                    val head = i == beat
                    if (head) {
                        drawArc(
                            pal.accent.copy(alpha = 0.28f),
                            start, (sweep * frac).coerceAtLeast(0.5f), false, tl, box,
                            style = Stroke(width + 9.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    drawArc(
                        if (i < beat) pal.accent.copy(alpha = 0.5f) else pal.accent,
                        start, (sweep * frac).coerceAtLeast(0.5f), false, tl, box,
                        style = Stroke(width, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

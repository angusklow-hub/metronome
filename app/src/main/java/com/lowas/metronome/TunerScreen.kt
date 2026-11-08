package com.lowas.metronome

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.abs
import kotlin.math.roundToInt

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
            Text("Microphone needed", color = pal.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "The tuner listens through the microphone to find the pitch you play or sing. Audio is analysed on the phone and never stored or sent anywhere.",
                color = pal.sub,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(pal.accent)
                    .clickable { launcher.launch(Manifest.permission.RECORD_AUDIO) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Allow microphone", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    } else {
        val cents = r?.cents ?: 0.0
        val tuned = r != null && abs(cents) < 5
        val tone = if (r == null) pal.sub else if (tuned) InTune else pal.accent

        GlassCard(pal) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val name = r?.let { NOTE_NAMES[((it.midi % 12) + 12) % 12] } ?: "—"
                val oct = r?.let { (it.midi / 12 - 1).toString() } ?: ""
                Text(
                    name + oct,
                    color = if (r == null) pal.sub else pal.text,
                    fontSize = 88.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (r == null) "Play a note" else String.format("%.1f Hz", r!!.freq),
                    color = pal.sub,
                    fontSize = 15.sp
                )
                Spacer(Modifier.height(18.dp))
                Gauge(pal, if (r == null) null else cents, tone)
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        r == null -> " "
                        tuned -> "In tune"
                        cents > 0 -> "${cents.roundToInt()} cents sharp"
                        else -> "${(-cents).roundToInt()} cents flat"
                    },
                    color = tone,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
    Spacer(Modifier.height(14.dp))

    GlassCard(pal) {
        SectionLabel(pal, "Reference pitch (A4)")
        Stepper(
            pal, "${s.a4} Hz",
            { Metronome.update { it.copy(a4 = (it.a4 - 1).coerceAtLeast(415)) } },
            { Metronome.update { it.copy(a4 = (it.a4 + 1).coerceAtMost(466)) } }
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Also used by the drone. Standard is 440; many baroque ensembles use 415.",
            color = pal.sub,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun Gauge(pal: Pal, cents: Double?, tone: Color) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(70.dp)
    ) {
        val w = size.width
        val mid = w / 2f
        val half = w / 2f - 12.dp.toPx()
        for (c in -50..50 step 10) {
            val x = mid + c / 50f * half
            val tall = c == 0
            drawLine(
                if (tall) tone else pal.chip,
                Offset(x, if (tall) 4.dp.toPx() else 18.dp.toPx()),
                Offset(x, size.height - if (tall) 4.dp.toPx() else 18.dp.toPx()),
                strokeWidth = if (tall) 3.dp.toPx() else 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        if (cents != null) {
            val x = mid + (cents.coerceIn(-50.0, 50.0) / 50.0).toFloat() * half
            drawLine(tone, Offset(x, 6.dp.toPx()), Offset(x, size.height - 6.dp.toPx()), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

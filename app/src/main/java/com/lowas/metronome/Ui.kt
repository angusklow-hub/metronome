package com.lowas.metronome

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** Colours for the current theme. */
class Pal(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val text: Color,
    val sub: Color,
    val accent: Color,
    val chip: Color,
)

val Accents = listOf(
    "Ocean" to Color(0xFF3E91FF),
    "Violet" to Color(0xFF8E6CFF),
    "Mint" to Color(0xFF22C99A),
    "Sunset" to Color(0xFFFF8A4C),
    "Rose" to Color(0xFFFF5C93),
    "Graphite" to Color(0xFF8A8F98),
)

val InTune = Color(0xFF34C759)

fun palette(mode: ThemeMode, systemDark: Boolean, accentIdx: Int): Pal {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AMOLED -> true
    }
    val accent = Accents[accentIdx.coerceIn(0, Accents.lastIndex)].second
    return if (!dark) {
        Pal(false, Color(0xFFF2F3F7), Color(0xFFFFFFFF), Color(0xFF111114), Color(0xFF7B7F89), accent, Color(0xFFE9EAF0))
    } else if (mode == ThemeMode.AMOLED) {
        Pal(true, Color.Black, Color(0xFF111113), Color.White, Color(0xFF9A9EA8), accent, Color(0xFF1C1C20))
    } else {
        Pal(true, Color(0xFF16171B), Color(0xFF232429), Color.White, Color(0xFF9A9EA8), accent, Color(0xFF2E3037))
    }
}

/** Frosted-glass look: translucent surface with a bright top edge, in the spirit of iOS 26/27. */
fun Modifier.glass(pal: Pal, shape: Shape): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(pal.card.copy(alpha = 0.96f), pal.card.copy(alpha = 0.84f))))
    .border(
        1.dp,
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = if (pal.dark) 0.20f else 0.95f),
                if (pal.dark) Color.White.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.05f),
            )
        ),
        shape
    )

@Composable
fun GlassCard(pal: Pal, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .glass(pal, RoundedCornerShape(28.dp))
            .padding(20.dp),
        content = content
    )
}

@Composable
fun SectionLabel(pal: Pal, text: String) {
    Text(
        text.uppercase(),
        color = pal.sub,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
fun Chip(pal: Pal, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) pal.accent else pal.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            color = if (selected) Color.White else pal.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun RoundBtn(pal: Pal, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(pal.chip)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = pal.text, fontSize = 26.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun Stepper(pal: Pal, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        RoundBtn(pal, "−", onMinus)
        Text(value, color = pal.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        RoundBtn(pal, "+", onPlus)
    }
}

@Composable
fun SwitchRow(pal: Pal, title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = pal.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = pal.sub, fontSize = 13.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = pal.accent,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = pal.chip,
                uncheckedThumbColor = pal.sub,
                uncheckedBorderColor = Color.Transparent,
            )
        )
    }
}

@Composable
fun BackgroundCard(pal: Pal, s: MSettings) {
    GlassCard(pal) {
        SwitchRow(
            pal,
            "Keep playing in other apps",
            "Runs as a background service with a Stop button in the notification shade",
            s.background
        ) { v -> Metronome.update { it.copy(background = v) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetronomeApp(onToggleMetronome: () -> Unit, onToggleDrone: () -> Unit) {
    val s by Metronome.settings.collectAsState()
    val pal = palette(s.theme, isSystemInDarkTheme(), s.accent)

    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = Color.Transparent.toArgb()
        val c = WindowCompat.getInsetsController(window, view)
        c.isAppearanceLightStatusBars = !pal.dark
        c.isAppearanceLightNavigationBars = !pal.dark
    }

    var tab by rememberSaveable { mutableStateOf(0) }
    var showTheme by remember { mutableStateOf(false) }

    val scheme = if (pal.dark) {
        darkColorScheme(primary = pal.accent, background = pal.bg, surface = pal.card, onSurface = pal.text)
    } else {
        lightColorScheme(primary = pal.accent, background = pal.bg, surface = pal.card, onSurface = pal.text)
    }

    MaterialTheme(colorScheme = scheme) {
        Box(
            Modifier
                .fillMaxSize()
                .background(pal.bg)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
            ) {
                // One UI style: large title with generous space above it.
                Box(Modifier.fillMaxWidth().height(150.dp)) {
                    Column(Modifier.align(Alignment.BottomStart)) {
                        Text("Metronome", color = pal.text, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                        Text("Tempo, tuner and drone", color = pal.sub, fontSize = 15.sp)
                    }
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 12.dp)
                            .glass(pal, RoundedCornerShape(50))
                            .clickable { showTheme = true }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text("Theme", color = pal.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // iOS style segmented control.
                val tabs = listOf("Metronome", "Tuner", "Drone")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                        .background(pal.chip)
                        .padding(4.dp)
                ) {
                    tabs.forEachIndexed { i, t ->
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(if (tab == i) pal.card else Color.Transparent)
                                .clickable { tab = i }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                t,
                                color = if (tab == i) pal.accent else pal.sub,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                when (tab) {
                    0 -> MetronomeScreen(pal, s)
                    1 -> TunerScreen(pal, s)
                    else -> DroneScreen(pal, s, onToggleDrone)
                }
                Spacer(Modifier.height(if (tab == 0) 140.dp else 40.dp))
            }

            if (tab == 0) {
                Dock(
                    pal,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 14.dp),
                    onToggleMetronome
                )
            }

            if (showTheme) {
                ModalBottomSheet(
                    onDismissRequest = { showTheme = false },
                    containerColor = pal.card,
                    contentColor = pal.text
                ) {
                    ThemeSheet(pal, s)
                }
            }
        }
    }
}

@Composable
private fun ThemeSheet(pal: Pal, s: MSettings) {
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding()
    ) {
        Text("Theme", color = pal.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        SectionLabel(pal, "Appearance")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.values().forEach { m ->
                Chip(pal, m.label, s.theme == m) { Metronome.update { it.copy(theme = m) } }
            }
        }
        Spacer(Modifier.height(22.dp))
        SectionLabel(pal, "Accent colour")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Accents.forEachIndexed { i, (_, color) ->
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(3.dp, if (s.accent == i) pal.text else Color.Transparent, CircleShape)
                        .clickable { Metronome.update { it.copy(accent = i) } }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(Accents[s.accent.coerceIn(0, Accents.lastIndex)].first, color = pal.sub, fontSize = 13.sp)
    }
}

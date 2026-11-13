package com.lowas.metronome

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** Colours used by the screens, read from the active Material colour scheme. */
class Pal(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val text: Color,
    val sub: Color,
    val accent: Color,
    val chip: Color,
    val onAccent: Color,
    val high: Color,
    val outline: Color,
    val chipText: Color,
    val glass: Boolean,
    val glassAmt: Float,
) {
    /** Fill for faces and wells: see-through when the glass look is on. */
    val face: Color get() = if (glass) high.copy(alpha = 0.5f) else high
}

val Accents = listOf(
    "Ocean" to Color(0xFF3E91FF),
    "Violet" to Color(0xFF8E6CFF),
    "Mint" to Color(0xFF22C99A),
    "Sunset" to Color(0xFFFF8A4C),
    "Rose" to Color(0xFFFF5C93),
    "Graphite" to Color(0xFF8A8F98),
    "Amber" to Color(0xFFFFB020),
)

val InTune = Color(0xFF34C759)

private fun customScheme(dark: Boolean, accent: Color): ColorScheme {
    val onAcc = if (accent.luminance() > 0.5f) Color(0xFF111114) else Color.White
    return if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAcc,
            primaryContainer = lerp(accent, Color(0xFF0B0C0F), 0.7f),
            onPrimaryContainer = lerp(accent, Color.White, 0.6f),
            secondaryContainer = lerp(accent, Color(0xFF15171B), 0.8f),
            onSecondaryContainer = lerp(accent, Color.White, 0.6f),
            background = Color(0xFF0B0C0F),
            onBackground = Color(0xFFF2F3F5),
            surface = Color(0xFF0B0C0F),
            onSurface = Color(0xFFF2F3F5),
            surfaceVariant = Color(0xFF22252B),
            onSurfaceVariant = Color(0xFF8A8F99),
            outline = Color(0xFF5A5F69),
            outlineVariant = Color(0xFF2A2D34),
            surfaceContainerLow = Color(0xFF0F1114),
            surfaceContainer = Color(0xFF15171B),
            surfaceContainerHigh = Color(0xFF1B1D22),
            surfaceContainerHighest = Color(0xFF22252B),
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAcc,
            primaryContainer = lerp(accent, Color.White, 0.75f),
            onPrimaryContainer = lerp(accent, Color.Black, 0.6f),
            secondaryContainer = lerp(accent, Color.White, 0.8f),
            onSecondaryContainer = lerp(accent, Color.Black, 0.6f),
            background = Color(0xFFF4F5F8),
            onBackground = Color(0xFF111114),
            surface = Color(0xFFF4F5F8),
            onSurface = Color(0xFF111114),
            surfaceVariant = Color(0xFFECEDF2),
            onSurfaceVariant = Color(0xFF6B6F79),
            outline = Color(0xFF9A9EA8),
            outlineVariant = Color(0xFFDADCE3),
            surfaceContainerLow = Color(0xFFF4F5F8),
            surfaceContainer = Color(0xFFFFFFFF),
            surfaceContainerHigh = Color(0xFFF7F8FA),
            surfaceContainerHighest = Color(0xFFECEDF2),
        )
    }
}

/** Picks the Material colour scheme: wallpaper colours (Material You) or the chosen accent. */
private fun resolveScheme(ctx: Context, s: MSettings, systemDark: Boolean): Pair<ColorScheme, Boolean> {
    val dark = when (s.theme) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AMOLED -> true
    }
    val accent = Accents[s.accent.coerceIn(0, Accents.lastIndex)].second
    val base: ColorScheme =
        if (s.dynamic && Build.VERSION.SDK_INT >= 31) {
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        } else {
            customScheme(dark, accent)
        }
    val scheme = if (dark && s.theme == ThemeMode.AMOLED) {
        base.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainer = Color(0xFF0E0F11),
            surfaceContainerHigh = Color(0xFF15161A),
            surfaceContainerHighest = Color(0xFF1B1D21),
        )
    } else {
        base
    }
    return scheme to dark
}

private fun palFrom(cs: ColorScheme, dark: Boolean, s: MSettings) = Pal(
    dark = dark,
    bg = cs.surface,
    card = cs.surfaceContainer,
    text = cs.onSurface,
    sub = cs.onSurfaceVariant,
    accent = cs.primary,
    chip = cs.secondaryContainer,
    onAccent = cs.onPrimary,
    high = cs.surfaceContainerHighest,
    outline = cs.outlineVariant,
    chipText = cs.onSecondaryContainer,
    glass = s.glass,
    glassAmt = s.glassAmt,
)

/** Frosted-glass panel: a translucent gradient fill with a thin light edge. Solid when glass is off. */
fun Modifier.glassSurface(pal: Pal, shape: Shape): Modifier {
    if (!pal.glass) return this.background(pal.card, shape)
    val a = pal.glassAmt
    val top = if (pal.dark) Color.White.copy(alpha = 0.06f + 0.14f * a) else Color.White.copy(alpha = 0.45f + 0.35f * a)
    val bot = if (pal.dark) Color.White.copy(alpha = 0.02f + 0.05f * a) else Color.White.copy(alpha = 0.2f + 0.2f * a)
    val e1 = Color.White.copy(alpha = if (pal.dark) 0.30f else 0.9f)
    val e2 = Color.White.copy(alpha = if (pal.dark) 0.06f else 0.35f)
    return this
        .background(Brush.verticalGradient(listOf(top, bot)), shape)
        .border(1.dp, Brush.verticalGradient(listOf(e1, e2)), shape)
}

/** Soft colour glows behind everything so the glass panels have something to frost. */
@Composable
fun GlassBackdrop(pal: Pal) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(pal.bg)
        if (pal.glass) {
            val a = 0.18f + 0.3f * pal.glassAmt
            val warm = lerp(pal.accent, Color(0xFFFF5C93), 0.6f)
            drawCircle(
                Brush.radialGradient(
                    listOf(pal.accent.copy(alpha = a), Color.Transparent),
                    center = Offset(size.width * 0.9f, size.height * 0.12f), radius = size.width * 0.95f
                ),
                radius = size.width * 0.95f, center = Offset(size.width * 0.9f, size.height * 0.12f)
            )
            drawCircle(
                Brush.radialGradient(
                    listOf(warm.copy(alpha = a * 0.8f), Color.Transparent),
                    center = Offset(size.width * 0.05f, size.height * 0.62f), radius = size.width * 0.9f
                ),
                radius = size.width * 0.9f, center = Offset(size.width * 0.05f, size.height * 0.62f)
            )
        }
    }
}

@Composable
fun GlassCard(pal: Pal, content: @Composable ColumnScope.() -> Unit) {
    val look = LocalLook.current
    Column(
        Modifier
            .fillMaxWidth()
            .glassSurface(pal, RoundedCornerShape(look.card))
            .padding(look.cardPad),
        content = content
    )
}

@Composable
fun SectionLabel(pal: Pal, text: String) {
    Text(
        text,
        color = pal.sub,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Chip(pal: Pal, label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(LocalLook.current.chip)
    )
}

@Composable
fun RoundBtn(pal: Pal, label: String, onClick: () -> Unit, size: Dp = 52.dp) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(size)) {
        Text(label, fontSize = 24.sp, fontWeight = FontWeight.Light)
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
        Text(value, color = pal.text, style = MaterialTheme.typography.headlineMedium)
        RoundBtn(pal, "+", onPlus)
    }
}

@Composable
fun AppSlider(pal: Pal, value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float> = 0f..1f) {
    Slider(value = value, onValueChange = onChange, valueRange = range)
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
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = pal.text, style = MaterialTheme.typography.bodyLarge)
            Text(sub, color = pal.sub, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
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

// ---- icons, drawn so no icon library is needed ----

val MetronomeIcon: DrawScope.(Color) -> Unit = { c ->
    val w = size.width
    val h = size.height
    val p = Path().apply {
        moveTo(w * 0.34f, h * 0.12f)
        lineTo(w * 0.66f, h * 0.12f)
        lineTo(w * 0.88f, h * 0.9f)
        lineTo(w * 0.12f, h * 0.9f)
        close()
    }
    drawPath(p, c, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
    drawLine(c, Offset(w * 0.5f, h * 0.74f), Offset(w * 0.64f, h * 0.3f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
}

val TunerIcon: DrawScope.(Color) -> Unit = { c ->
    val w = size.width
    val h = size.height
    drawArc(
        c, 200f, 140f, false,
        topLeft = Offset(w * 0.08f, h * 0.2f),
        size = Size(w * 0.84f, w * 0.84f),
        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
    )
    drawLine(c, Offset(w * 0.5f, h * 0.62f), Offset(w * 0.66f, h * 0.3f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
}

val DroneIcon: DrawScope.(Color) -> Unit = { c ->
    val w = size.width
    val h = size.height
    val xs = listOf(0.16f, 0.33f, 0.5f, 0.67f, 0.84f)
    val hs = listOf(0.22f, 0.5f, 0.78f, 0.4f, 0.16f)
    for (i in xs.indices) {
        drawLine(
            c,
            Offset(w * xs[i], h * (0.5f - hs[i] / 2f)),
            Offset(w * xs[i], h * (0.5f + hs[i] / 2f)),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun NavBar(pal: Pal, tab: Int, onTab: (Int) -> Unit) {
    val items = listOf("Metronome" to MetronomeIcon, "Tuner" to TunerIcon, "Drone" to DroneIcon)
    val look = LocalLook.current
    NavigationBar(
        containerColor = if (pal.glass) pal.card.copy(alpha = 0.5f) else NavigationBarDefaults.containerColor
    ) {
        items.forEachIndexed { i, (label, icon) ->
            NavigationBarItem(
                selected = tab == i,
                onClick = { onTab(i) },
                alwaysShowLabel = !look.ios,
                icon = {
                    val c = LocalContentColor.current
                    Canvas(Modifier.size(if (look.ios) 26.dp else 24.dp)) { icon(this, c) }
                },
                label = if (look.navLabels) ({ Text(label) }) else null,
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = if (look.ios) Color.Transparent else pal.accent.copy(alpha = 0.22f)
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(pal: Pal, title: String, onPresets: (() -> Unit)?, onTheme: () -> Unit) {
    val look = LocalLook.current
    val actions: @Composable RowScope.() -> Unit = {
            if (onPresets != null) {
                IconButton(onClick = onPresets) {
                    val c = LocalContentColor.current
                    Canvas(Modifier.size(22.dp)) {
                        for (k in 0..2) {
                            val y = size.height * (0.22f + 0.28f * k)
                            drawLine(
                                c, Offset(size.width * 0.1f, y), Offset(size.width * 0.9f, y),
                                strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onTheme) {
                val c = LocalContentColor.current
                Canvas(Modifier.size(22.dp)) {
                    drawCircle(c, radius = size.minDimension / 2f - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                    drawArc(
                        c, 90f, 180f, true,
                        topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                        size = Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx())
                    )
                }
            }
    }
    val clear = Color.Transparent
    if (look.bigTitle) {
        // One UI: a large title down the left, with the actions above it.
        LargeTopAppBar(
            title = { Text(title) },
            actions = actions,
            colors = TopAppBarDefaults.largeTopAppBarColors(
                containerColor = clear,
                scrolledContainerColor = clear
            )
        )
    } else {
        Column {
            CenterAlignedTopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                actions = actions,
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = clear)
            )
            if (look.hairline) {
                // iOS: a hairline under the bar instead of a tinted surface.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(pal.outline.copy(alpha = 0.6f))
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetronomeApp(onToggleMetronome: () -> Unit, onToggleDrone: () -> Unit) {
    val s by Metronome.settings.collectAsState()
    val playing by Metronome.playing.collectAsState()
    val dronePlaying by Drone.playing.collectAsState()
    val ctx = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val (scheme, dark) = resolveScheme(ctx, s, systemDark)
    val look = lookOf(s.skin)
    val pal = palFrom(scheme, dark, s)
    val sheetColor = if (pal.glass) pal.card.copy(alpha = 0.55f + 0.3f * (1f - pal.glassAmt)) else pal.card

    val view = LocalView.current
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = Color.Transparent.toArgb()
        val c = WindowCompat.getInsetsController(window, view)
        c.isAppearanceLightStatusBars = !pal.dark
        c.isAppearanceLightNavigationBars = !pal.dark
        view.keepScreenOn = s.keepAwake && (playing || dronePlaying)
    }

    var tab by rememberSaveable { mutableStateOf(0) }
    var showTheme by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var showPresets by remember { mutableStateOf(false) }
    var showBpm by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalLook provides look) {
    MaterialTheme(
        colorScheme = scheme,
        typography = typographyFor(look.skin),
        shapes = look.shapes
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(pal.bg)
        ) {
            GlassBackdrop(pal)
            Column(Modifier.fillMaxSize()) {
                TopBar(
                    pal, listOf("Metronome", "Tuner", "Drone")[tab],
                    if (tab == 0) ({ showPresets = true }) else null
                ) { showTheme = true }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                ) {
                    Crossfade(
                        targetState = tab,
                        animationSpec = tween(if (look.ios) 180 else 260),
                        label = "tab"
                    ) { which ->
                        Column(Modifier.fillMaxWidth()) {
                            when (which) {
                                0 -> MetronomeScreen(pal, s, onToggleMetronome, { showBpm = true }) { showOptions = true }
                                1 -> TunerScreen(pal, s)
                                else -> DroneScreen(pal, s, onToggleDrone)
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                NavBar(pal, tab) { tab = it }
            }

            FlashOverlay(pal, s.flash)

            if (showPresets) {
                ModalBottomSheet(
                    shape = RoundedCornerShape(topStart = look.stage, topEnd = look.stage),
                    onDismissRequest = { showPresets = false },
                    containerColor = sheetColor,
                    contentColor = pal.text
                ) {
                    PresetsSheet(pal, onSave = { showPresets = false; showSave = true }, onClose = { showPresets = false })
                }
            }
            if (showBpm) BpmDialog(pal, s.bpm) { showBpm = false }
            if (showSave) SaveDialog(pal, s.bpm) { showSave = false }
            if (showTheme) {
                ModalBottomSheet(
                    shape = RoundedCornerShape(topStart = look.stage, topEnd = look.stage),
                    onDismissRequest = { showTheme = false },
                    containerColor = sheetColor,
                    contentColor = pal.text
                ) {
                    ThemeSheet(pal, s)
                }
            }
            if (showOptions) {
                ModalBottomSheet(
                    shape = RoundedCornerShape(topStart = look.stage, topEnd = look.stage),
                    onDismissRequest = { showOptions = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    containerColor = sheetColor,
                    contentColor = pal.text
                ) {
                    OptionsSheet(pal, s)
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSheet(pal: Pal, s: MSettings) {
    val canDynamic = Build.VERSION.SDK_INT >= 31
    Column(
        Modifier
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Text("Appearance", color = pal.text, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(18.dp))
        SectionLabel(pal, "Interface style")
        val skins = Skin.values()
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            skins.forEachIndexed { i, sk ->
                SegmentedButton(
                    selected = s.skin == i,
                    onClick = {
                        // iOS is built around translucency, so switching to it turns glass on.
                        Metronome.update { it.copy(skin = i, glass = if (i == 2) true else it.glass) }
                    },
                    shape = SegmentedButtonDefaults.itemShape(i, skins.size)
                ) { Text(sk.label, maxLines = 1) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when (Skin.values().getOrElse(s.skin) { Skin.M3 }) {
                Skin.M3 -> "Material 3: springy motion, wallpaper colours, pill-shaped controls."
                Skin.ONEUI -> "One UI: large titles, roomy rounded cards, calm easing."
                Skin.IOS -> "iOS 27: translucent glass panels, tight type, quick settling motion."
            },
            color = pal.sub,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(20.dp))
        SectionLabel(pal, "Theme")
        val modes = ThemeMode.values()
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            modes.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = s.theme == m,
                    onClick = { Metronome.update { it.copy(theme = m) } },
                    shape = SegmentedButtonDefaults.itemShape(i, modes.size)
                ) { Text(m.label, maxLines = 1) }
            }
        }
        Spacer(Modifier.height(20.dp))
        SwitchRow(pal, "Glass effect", "Frosted translucent panels over a soft colour glow", s.glass) { v ->
            Metronome.update { it.copy(glass = v) }
        }
        if (s.glass) {
            SectionLabel(pal, "Glass strength")
            AppSlider(pal, s.glassAmt, { v -> Metronome.update { it.copy(glassAmt = v) } })
        }
        if (canDynamic) {
            Spacer(Modifier.height(20.dp))
            SwitchRow(
                pal,
                "Wallpaper colours",
                "Material You: take the colours from your wallpaper",
                s.dynamic
            ) { v -> Metronome.update { it.copy(dynamic = v) } }
        }
        if (!(canDynamic && s.dynamic)) {
            Spacer(Modifier.height(20.dp))
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
}

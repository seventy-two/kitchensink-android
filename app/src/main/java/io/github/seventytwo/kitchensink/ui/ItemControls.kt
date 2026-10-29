// A checklist item's slider and colour swatch (the lights' brightness and
// colour). In a switches block (the lights) each item is a slim tile: its
// name, its brightness as a thin line, a swatch opening a dialog of
// presets and a colour wheel, and a switch turning it on and off.
package io.github.seventytwo.kitchensink.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.seventytwo.kitchensink.model.Colour
import io.github.seventytwo.kitchensink.model.Item
import io.github.seventytwo.kitchensink.model.Tone
import io.github.seventytwo.kitchensink.model.Slider as ItemSlider
import io.github.seventytwo.kitchensink.model.hexColour
import io.github.seventytwo.kitchensink.model.parseHexColour
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** 0xrrggbb as an opaque colour. */
fun rgbColour(rgb: Int) = Color(0xff000000.toInt() or rgb)

/**
 * An item of a switches block (a light) as a slim row, flat on the panel: its title, a thin level line if it has a slider (tap or drag
 * along it, or left and right on a remote, sent when let go), its colour swatch and a switch. Tapped elsewhere,
 * it toggles.
 */
@Composable
fun SwitchTile(monitor: String, it: Item, done: Boolean) {
    val p = LocalPalette.current
    val mt = LocalMetrics.current
    val actions = LocalActions.current
    val s = it.slider
    var dragging by remember(monitor, it.id) { mutableStateOf<Float?>(null) }
    var sent by remember(monitor, it.id, s?.value) { mutableStateOf<Int?>(null) }
    val level = dragging ?: sent?.toFloat() ?: s?.value?.toFloat() ?: 0f
    val on = !done || dragging != null || (sent ?: 0) > 0
    val fill = it.colour?.value?.let(::parseHexColour)?.let(::rgbColour) ?: p.accent
    fun send(v: Int) {
        if (s == null || v == s.value) return
        sent = v
        actions.send(monitor, s.action, it.id, v.toString(), done = v == 0)
    }
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier.fillMaxWidth().clip(shape)
            .onPreviewKeyEvent { e ->
                val step = when (e.key) { Key.DirectionLeft -> -10; Key.DirectionRight -> 10; else -> 0 }
                if (s == null || step == 0) return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyUp) send(((if (on) level.roundToInt() else 0) + step).coerceIn(0, 100))
                true
            }
            .tappable(shape = shape) { actions.toggle(monitor, it.id, !done) }
            .semantics { contentDescription = it.title },
    ) {
        Row(Modifier.fillMaxWidth().height(44.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                it.title, style = mt.body, color = if (on) p.text else p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = if (s != null) Modifier.width(112.dp) else Modifier.weight(1f),
            )
            if (s != null) {
                // The row's whole height, and a little past each end, takes touches.
                val slack = with(LocalDensity.current) { 8.dp.toPx() }
                fun at(x: Float, w: Int) = ((x - slack) / (w - 2 * slack) * 100f).coerceIn(0f, 100f)
                Canvas(
                    Modifier.weight(1f).fillMaxHeight()
                        .pointerInput(monitor, it.id) { detectTapGestures { o -> send(at(o.x, size.width).roundToInt()) } }
                        .pointerInput(monitor, it.id) {
                            detectHorizontalDragGestures(
                                onDragStart = { o -> dragging = at(o.x, size.width) },
                                onDragEnd = { dragging?.let { v -> send(v.roundToInt()) }; dragging = null },
                                onDragCancel = { dragging = null },
                            ) { change, _ ->
                                change.consume()
                                dragging = at(change.position.x, size.width)
                            }
                        }
                        .semantics { contentDescription = s.hint.ifEmpty { "Level" } },
                ) {
                    val y = size.height / 2
                    val t = 3.dp.toPx()
                    val x0 = slack
                    val x1 = size.width - slack
                    val x = x0 + (x1 - x0) * (if (on) level else 0f) / 100f
                    drawLine(p.frame, Offset(x0, y), Offset(x1, y), t, StrokeCap.Round)
                    if (x > x0) drawLine(fill, Offset(x0, y), Offset(x, y), t, StrokeCap.Round)
                }
            }
            if (it.tone == Tone.Warn) Text(it.subtitle, style = mt.caption, color = p.warn, maxLines = 1)
            it.colour?.let { c -> ItemColourView(monitor, it.id, it.title, c, done, size = 18.dp) }
            Switch(
                !done, { _ -> actions.toggle(monitor, it.id, !done) }, Modifier.scale(0.7f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White, checkedTrackColor = p.accent, checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = p.muted, uncheckedTrackColor = p.frame, uncheckedBorderColor = Color.Transparent,
                ),
            )
        }
    }
}

/**
 * The item's slider, sent when let go. What was sent shows until the server's value changes, as lights report
 * a moment late. [fill] is the light's colour, if it has one.
 */
@Composable
fun ItemSliderView(monitor: String, id: String, s: ItemSlider, done: Boolean, fill: Color?, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    var dragging by remember(monitor, id) { mutableStateOf<Float?>(null) }
    var sent by remember(monitor, id, s.value) { mutableStateOf<Int?>(null) }
    val shown = dragging ?: sent?.toFloat() ?: s.value.toFloat()
    val colour = if (done || shown < 0.5f) p.muted else fill ?: p.accent
    Slider(
        value = shown,
        onValueChange = { dragging = it },
        onValueChangeFinished = {
            val v = (dragging ?: shown).roundToInt()
            dragging = null
            if (v != s.value) {
                sent = v
                actions.send(monitor, s.action, id, v.toString(), done = v == 0)
            }
        },
        valueRange = 0f..100f,
        colors = SliderDefaults.colors(
            thumbColor = colour, activeTrackColor = colour, inactiveTrackColor = p.frame,
            activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
        ),
        modifier = modifier.height(32.dp).semantics { contentDescription = s.hint.ifEmpty { "Level" } },
    )
}

/**
 * The item's colour swatch: tapped, a dialog of presets and (if [Colour.wheel]) a colour wheel. A colour picked
 * shows until the server's changes.
 */
@Composable
fun ItemColourView(monitor: String, id: String, title: String, c: Colour, done: Boolean, size: Dp = 26.dp) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    var open by remember { mutableStateOf(false) }
    var picked by remember(monitor, id, c.value) { mutableStateOf<String?>(null) }
    val rgb = parseHexColour(picked ?: c.value)
    val off = done && picked == null
    Box(
        Modifier.padding(horizontal = 6.dp).size(size + 8.dp).tappable(shape = CircleShape) { open = true }
            .semantics { contentDescription = "Colour" },
        contentAlignment = Alignment.Center,
    ) {
        // Filled when the light is on, a ring when it's off.
        val lit = rgb != null && !off
        Box(
            Modifier.size(size).background(if (lit) rgbColour(rgb!!) else Color.Transparent, CircleShape)
                .border(1.5.dp, if (lit) Color.White.copy(alpha = 0.25f) else p.muted, CircleShape),
        )
    }
    if (open) {
        ColourDialog(title, c, rgb ?: 0xffffff, onDismiss = { open = false }) { value, show ->
            open = false
            picked = show
            // Picking a colour turns a light on.
            actions.send(monitor, c.action, id, value, done = false)
        }
    }
}

/** Presets in rows of six, whites (sent as kelvin) on rows of their own, then the wheel. */
@Composable
private fun ColourDialog(title: String, c: Colour, start: Int, onDismiss: () -> Unit, pick: (value: String, show: String) -> Unit) {
    val p = LocalPalette.current
    val mt = LocalMetrics.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.background(p.card, RoundedCornerShape(12.dp)).border(1.dp, p.border, RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Text(title, style = mt.body, color = p.text)
            Spacer(Modifier.height(12.dp))
            val (whites, hues) = c.presets.filter { parseHexColour(it.show) != null }.partition { !it.value.startsWith("#") }
            for (group in listOf(whites, hues)) {
                for (row in group.chunked(6)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        for (s in row) {
                            Box(
                                Modifier.size(36.dp).background(rgbColour(parseHexColour(s.show)!!), CircleShape)
                                    .border(1.dp, p.border, CircleShape)
                                    .tappable(shape = CircleShape) { pick(s.value, s.show) }
                                    .semantics { contentDescription = s.label },
                            )
                        }
                    }
                }
            }
            if (c.wheel) {
                Spacer(Modifier.height(8.dp))
                ColourWheel(start, Modifier.size(260.dp).align(Alignment.CenterHorizontally)) { hexColour(it).let { hex -> pick(hex, hex) } }
            }
        }
    }
}

/**
 * A hue and saturation wheel (brightness is the slider's): hue around it, white at the middle. Tap, or drag and
 * let go, to pick.
 */
@Composable
private fun ColourWheel(start: Int, modifier: Modifier, pick: (Int) -> Unit) {
    val p = LocalPalette.current
    var hs by remember {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(start, hsv)
        mutableStateOf(hsv[0] to hsv[1])
    }
    fun rgb(h: Float, s: Float) = android.graphics.Color.HSVToColor(floatArrayOf(h, s, 1f)) and 0xffffff
    fun at(o: Offset, w: Float, h: Float): Pair<Float, Float> {
        val dx = o.x - w / 2
        val dy = o.y - h / 2
        val hue = ((Math.toDegrees(atan2(dy, dx).toDouble()) + 360) % 360).toFloat()
        return hue to (hypot(dx, dy) / (min(w, h) / 2)).coerceIn(0f, 1f)
    }
    val hues = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    hs = at(o, size.width.toFloat(), size.height.toFloat())
                    pick(rgb(hs.first, hs.second))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { pick(rgb(hs.first, hs.second)) },
                ) { change, _ -> hs = at(change.position, size.width.toFloat(), size.height.toFloat()) }
            }
            .semantics { contentDescription = "Colour wheel" },
    ) {
        val r = min(size.width, size.height) / 2
        // Clockwise from the right, as screen y runs down: the same way at() reads it.
        drawCircle(Brush.sweepGradient(hues, center), r)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center, r), r)
        val a = Math.toRadians(hs.first.toDouble())
        val m = Offset(center.x + (cos(a) * hs.second * r).toFloat(), center.y + (sin(a) * hs.second * r).toFloat())
        drawCircle(rgbColour(rgb(hs.first, hs.second)), 12.dp.toPx(), m)
        drawCircle(p.card, 12.dp.toPx(), m, style = Stroke(3.dp.toPx()))
    }
}

package io.github.seventytwo.kitchensink.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.seventytwo.kitchensink.KitchensinkApp
import io.github.seventytwo.kitchensink.MainActivity
import io.github.seventytwo.kitchensink.data.ThemeChoice
import io.github.seventytwo.kitchensink.model.Tone
import io.github.seventytwo.kitchensink.model.ago
import io.github.seventytwo.kitchensink.ui.Palette
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A home screen widget of each monitor's summary, like the app's compact
 * rows. The app saves what it shows (see [WidgetStore]) whenever the
 * dashboard changes, so it's as fresh as the app's connection; tapping it
 * opens the app.
 */
class DashboardWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val (data, saved) = withContext(Dispatchers.IO) { WidgetStore.load(context) to WidgetStore.saved(context) }
        // Nothing saved yet: have the app fetch the dashboard, which saves
        // it and redraws this.
        if (!saved) withContext(Dispatchers.Main) { (context.applicationContext as KitchensinkApp).hub.fillWidgets() }
        provideContent { WidgetContent(data, System.currentTimeMillis() / 1000) }
    }
}

class DashboardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DashboardWidget()
}

/** What the widget shows, kept as widget.json in the app's private storage. */
object WidgetStore {
    private fun file(context: Context) = File(context.filesDir, "widget.json")

    fun saved(context: Context) = file(context).exists()

    fun load(context: Context): WidgetData = try {
        WidgetData.decode(file(context).readText())
    } catch (_: Exception) {
        WidgetData()
    }

    /** Saves [data] and redraws the widgets, unless it's what they already show. */
    suspend fun show(context: Context, data: WidgetData) {
        val text = data.encode()
        val changed = withContext(Dispatchers.IO) {
            val f = file(context)
            if (f.exists() && f.readText() == text) return@withContext false
            val tmp = File(f.path + ".tmp")
            tmp.writeText(text)
            tmp.renameTo(f)
        }
        if (changed) DashboardWidget().updateAll(context)
    }
}

/** The app's palettes for the widget, by day and by night, following its theme. */
internal fun widgetPalettes(theme: ThemeChoice, black: Boolean): Pair<Palette, Palette> = when {
    theme == ThemeChoice.Retro -> Palette.Retro to Palette.Retro
    black -> Palette.Oled to Palette.Oled
    theme == ThemeChoice.Light -> Palette.Light to Palette.Light
    theme == ThemeChoice.Dark -> Palette.Dark to Palette.Dark
    else -> Palette.Light to Palette.Dark
}

private fun coloured(t: Tone) = t == Tone.Good || t == Tone.Bad || t == Tone.Warn || t == Tone.Accent

/** The palette's colours as Glance wants them, switching with the launcher's day and night. */
internal class Colours(private val day: Palette, private val night: Palette) {
    fun of(f: (Palette) -> Color): ColorProvider = ColorProvider(day = f(day), night = f(night))
    val bg = of { it.bg }
    val card = of { it.card }
    val frame = of { it.frame }
    val border = of { it.border }
    val text = of { it.text }
    val muted = of { it.muted }
    val heading = of { it.heading }
    val warn = of { it.warn }
    fun tone(t: Tone) = of { it.tone(t) }

    /** A chip's underline: its tone's colour, or the border's for a plain one. */
    fun underline(t: Tone) = of { if (coloured(t)) it.tone(t) else it.border }

    /** A price line's colour: muted for no tone. */
    fun line(t: Tone, alpha: Float = 1f) = of { (if (t == Tone.None) it.muted else it.tone(t)).copy(alpha = alpha) }

    /** How full a bar is: in the accent normally, the tone's colour as it fills up. */
    fun barFill(t: Tone) = of { io.github.seventytwo.kitchensink.ui.barFill(it, t) }
}

private val Caption: TextUnit = 11.sp
private val Value: TextUnit = 14.sp
private val Gap: Dp = 4.dp

/** The most children a widget's row or column may have. */
private const val MaxChildren = 10

/** The widget. [scroll] is off only in tests, as Robolectric can't fill a RemoteViews list. */
@Composable
internal fun WidgetContent(data: WidgetData, now: Long, scroll: Boolean = true) {
    val (day, night) = widgetPalettes(data.theme, data.black)
    val c = Colours(day, night)
    val open = actionStartActivity<MainActivity>()
    val pad = 8.dp
    Column(GlanceModifier.fillMaxSize().appWidgetBackground().background(c.bg).cornerRadius(16.dp).padding(horizontal = pad, vertical = 6.dp)) {
        Row(GlanceModifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 4.dp).clickable(open), verticalAlignment = Alignment.CenterVertically) {
            Text("kitchensink", style = TextStyle(color = c.heading, fontSize = Value, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.defaultWeight())
            if (data.updated > 0) Text(ago(data.updated, now), style = TextStyle(color = c.muted, fontSize = Caption), maxLines = 1)
        }
        when {
            !data.configured -> Message("Set up kitchensink in the app first.", c, open)
            data.rows.isEmpty() -> Message("Waiting for the dashboard…", c, open)
            else -> {
                val inner = LocalSize.current.width - pad * 2 - 16.dp
                if (scroll) {
                    LazyColumn(GlanceModifier.fillMaxSize()) {
                        items(data.rows, itemId = { it.monitor.hashCode().toLong() }) { row -> MonitorRow(row, inner, c, open) }
                    }
                } else {
                    Column(GlanceModifier.fillMaxSize()) { for (row in data.rows) MonitorRow(row, inner, c, open) }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String, c: Colours, open: Action) {
    Box(GlanceModifier.fillMaxSize().clickable(open), contentAlignment = Alignment.Center) {
        Text(text, style = TextStyle(color = c.muted, fontSize = Value))
    }
}

/** One monitor: its title, then its summary in lines of equal-width stats; or, slim, one line without its title. [inner] is the width inside the card. */
@Composable
private fun MonitorRow(row: WidgetRow, inner: Dp, c: Colours, open: Action) {
    Column(GlanceModifier.fillMaxWidth().padding(bottom = if (row.slim) 3.dp else Gap)) {
        val padV = if (row.slim) 4.dp else 6.dp
        Column(GlanceModifier.fillMaxWidth().background(c.card).cornerRadius(8.dp).padding(horizontal = 8.dp, vertical = padV).clickable(open)) {
            if (!row.slim) {
                Text(row.title, style = TextStyle(color = c.muted, fontSize = Caption, fontWeight = FontWeight.Medium), maxLines = 1)
                Spacer(GlanceModifier.height(2.dp))
            }
            when {
                row.error.isNotEmpty() -> Text(
                    (if (row.slim) row.title + ": " else "") + row.error, style = TextStyle(color = c.warn, fontSize = Caption), maxLines = if (row.slim) 1 else 2,
                )
                row.stats.isEmpty() -> Text(if (row.slim) row.title else "Open for details", style = TextStyle(color = c.muted, fontSize = Caption), maxLines = 1)
                row.slim -> SlimLine(row, inner, c)
                row.stats.all { it.bar } -> {
                    // A summary of only bars (Claude's limits): a line each.
                    for ((i, s) in row.stats.withIndex()) {
                        if (i > 0) Spacer(GlanceModifier.height(3.dp))
                        BarLine(s, c)
                    }
                }
                else -> {
                    val sparks = row.chips && row.stats.any { !it.lead && it.spark != null }
                    // A widget's row holds ten things at most.
                    val n = statsPerLine(row.stats.size, inner.value, if (sparks) 96f else 72f, Gap.value).coerceAtMost(MaxChildren)
                    for ((i, line) in row.stats.chunked(n).withIndex()) {
                        // Stats on a line have the same parts, so they're one height.
                        val labels = line.any { it.label.isNotEmpty() }
                        val details = line.any { it.detail.isNotEmpty() && !it.bar }
                        // Spaced with padding, not spacers, which would count towards the ten.
                        Row(GlanceModifier.fillMaxWidth().padding(top = if (i > 0) Gap else 0.dp)) {
                            for (k in 0 until n) {
                                val s = line.getOrNull(k)
                                val m = GlanceModifier.defaultWeight().padding(start = if (k > 0) Gap else 0.dp)
                                when {
                                    s == null -> Spacer(m)
                                    s.bar -> Box(m.padding(vertical = 3.dp)) { BarLine(s, c) }
                                    row.chips && s.lead -> LeadCell(s, c, m)
                                    sparks -> SparkCell(s, c, m)
                                    else -> StatCell(s, row.chips, labels, details, c, m)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A stat as the app draws it: a chip, its label above its value and
 * underlined in its tone's colour, or a plain tile. The underline is a thin
 * coloured box, as a widget can't draw a line.
 */
@Composable
private fun StatCell(s: WidgetStat, chip: Boolean, labels: Boolean, details: Boolean, c: Colours, modifier: GlanceModifier) {
    val labelColour = if (chip && s.tone != Tone.Muted) c.text else c.muted
    val valueColour = if (chip && !coloured(s.tone) && s.tone != Tone.Muted) c.text else c.tone(s.tone)
    Column(modifier) {
        Column(GlanceModifier.fillMaxWidth().padding(horizontal = if (chip) 4.dp else 2.dp, vertical = 2.dp)) {
            if (labels) Text(s.label.replace('\n', ' ').ifEmpty { " " }, style = TextStyle(color = labelColour, fontSize = Caption), maxLines = 1)
            Text(s.value, style = TextStyle(color = valueColour, fontSize = Value, fontWeight = FontWeight.Bold), maxLines = 1)
            if (details) Text(s.detail.replace('\n', ' ').ifEmpty { " " }, style = TextStyle(color = c.muted, fontSize = Caption), maxLines = 1)
        }
        if (chip) Box(GlanceModifier.fillMaxWidth().height(2.dp).background(c.underline(s.tone))) {}
    }
}

/** A lead chip (the portfolio's day): its label and value as plain text on one line. */
@Composable
private fun LeadCell(s: WidgetStat, c: Colours, modifier: GlanceModifier) {
    Row(modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(s.label, style = TextStyle(color = if (s.tone == Tone.Muted) c.muted else c.text, fontSize = Caption), maxLines = 1)
        Spacer(GlanceModifier.width(5.dp))
        Text(s.value, style = TextStyle(color = if (coloured(s.tone)) c.tone(s.tone) else c.text, fontSize = Value, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

/** A watchlist chip: a plain block, its label and value (and any tag) on one line, its price line under them. */
@Composable
private fun SparkCell(s: WidgetStat, c: Colours, modifier: GlanceModifier) {
    val valueColour = if (coloured(s.tone)) c.tone(s.tone) else if (s.tone == Tone.Muted) c.muted else c.text
    Column(modifier.background(c.frame).padding(horizontal = 5.dp, vertical = 3.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(s.label, style = TextStyle(color = if (s.tone == Tone.Muted) c.muted else c.text, fontSize = Caption), maxLines = 1)
            Spacer(GlanceModifier.defaultWeight())
            Text(s.value, style = TextStyle(color = valueColour, fontSize = Value, fontWeight = FontWeight.Bold), maxLines = 1)
            if (s.tag.isNotEmpty()) {
                Spacer(GlanceModifier.width(2.dp))
                Text(s.tag.uppercase(), style = TextStyle(color = c.muted, fontSize = 8.sp), maxLines = 1)
            }
        }
        Spacer(GlanceModifier.height(2.dp))
        s.spark?.let { Spark(it, c, GlanceModifier.fillMaxWidth().height(18.dp)) } ?: Spacer(GlanceModifier.height(18.dp))
    }
}

/** A price line: its layers stacked, each tinted with its colour. */
@Composable
private fun Spark(sp: WidgetSpark, c: Colours, modifier: GlanceModifier) {
    val layers = sparkLayers(sp) ?: return Spacer(modifier)
    Box(modifier) {
        @Composable
        fun layer(b: android.graphics.Bitmap, colour: ColorProvider) =
            Image(ImageProvider(b), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds, colorFilter = ColorFilter.tint(colour))
        layers.base?.let { layer(it, c.line(Tone.None, 0.5f)) }
        layer(layers.points, c.line(sp.tone, if (sp.faded) 0.55f else 1f))
        layers.ext?.let { layer(it, c.line(sp.extTone)) }
    }
}

/** A bar stat on one line: its label, a bar filled to its level, then its value. */
@Composable
private fun BarLine(s: WidgetStat, c: Colours) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(s.label, style = TextStyle(color = c.muted, fontSize = Caption), maxLines = 1)
        Spacer(GlanceModifier.width(6.dp))
        Box(GlanceModifier.defaultWeight().height(5.dp).background(c.frame)) {
            Image(ImageProvider(barFill(s.level)), null, GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds, colorFilter = ColorFilter.tint(c.barFill(s.tone)))
        }
        Spacer(GlanceModifier.width(6.dp))
        Text(s.value, style = TextStyle(color = c.tone(s.tone), fontSize = Caption), maxLines = 1)
    }
}

/**
 * A row on one slim line, as the app's ultra compact rows: each stat's
 * label then its value (or brief), chips underlined, a small price line
 * after a watchlist chip's value; what doesn't fit is counted as "+n".
 */
@Composable
private fun SlimLine(row: WidgetRow, inner: Dp, c: Colours) {
    val gap = if (row.chips) 6f else 14f
    val sparkW = 34f
    // A widget's row holds ten things at most: nine stats and the count.
    val fit = slimFit(row.stats, inner.value, gap) { if (row.chips && !it.lead && it.spark != null) sparkW + 4f else 0f }
        .coerceIn(1, if (row.stats.size > MaxChildren) MaxChildren - 1 else MaxChildren)
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        for ((i, s) in row.stats.take(fit).withIndex()) {
            val value = s.brief ?: s.value
            val chip = row.chips && !s.lead
            val labelColour = if (row.chips && s.tone != Tone.Muted) c.text else c.muted
            val valueColour = if (coloured(s.tone) || !row.chips) c.tone(s.tone) else if (s.tone == Tone.Muted) c.muted else c.text
            // Each as wide as its text is guessed to be: a widget can't size an
            // underline to its text otherwise.
            val w = slimWidth(s) + if (chip && s.spark != null) sparkW + 4f else 0f
            val lead = if (i > 0) gap else 0f
            Column(GlanceModifier.width((w + lead).dp).padding(start = lead.dp)) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    if (s.label.isNotEmpty()) Text(s.label, style = TextStyle(color = labelColour, fontSize = Caption), maxLines = 1)
                    if (s.label.isNotEmpty() && value.isNotEmpty()) Spacer(GlanceModifier.width(4.dp))
                    if (value.isNotEmpty()) Text(value, style = TextStyle(color = valueColour, fontSize = Value, fontWeight = FontWeight.Bold), maxLines = 1)
                    if (s.tag.isNotEmpty()) Text(" " + s.tag.uppercase(), style = TextStyle(color = c.muted, fontSize = 8.sp), maxLines = 1)
                    if (chip && s.spark != null) {
                        Spacer(GlanceModifier.width(4.dp))
                        Spark(s.spark, c, GlanceModifier.width(sparkW.dp).height(12.dp))
                    }
                }
                if (chip) Box(GlanceModifier.fillMaxWidth().height(2.dp).background(c.underline(s.tone))) {}
            }
        }
        if (fit < row.stats.size) {
            Text("+${row.stats.size - fit}", style = TextStyle(color = c.muted, fontSize = Caption), maxLines = 1, modifier = GlanceModifier.padding(start = 6.dp))
        }
    }
}

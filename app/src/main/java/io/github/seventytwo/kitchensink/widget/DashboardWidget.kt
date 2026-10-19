package io.github.seventytwo.kitchensink.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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
        val data = withContext(Dispatchers.IO) { WidgetStore.load(context) }
        provideContent { WidgetContent(data, System.currentTimeMillis() / 1000) }
    }
}

class DashboardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DashboardWidget()
}

/** What the widget shows, kept as widget.json in the app's private storage. */
object WidgetStore {
    private fun file(context: Context) = File(context.filesDir, "widget.json")

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
private class Colours(private val day: Palette, private val night: Palette) {
    private fun of(f: (Palette) -> Color): ColorProvider = ColorProvider(day = f(day), night = f(night))
    val bg = of { it.bg }
    val card = of { it.card }
    val text = of { it.text }
    val muted = of { it.muted }
    val heading = of { it.heading }
    val warn = of { it.warn }
    fun tone(t: Tone) = of { it.tone(t) }
    fun chip(t: Tone) = of { if (coloured(t)) it.tone(t).copy(alpha = 0.2f) else it.frame }
}

private val Caption: TextUnit = 11.sp
private val Value: TextUnit = 14.sp
private val Gap: Dp = 4.dp

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

/** One monitor: its title, then its summary in lines of equal-width stats. [inner] is the width inside the card. */
@Composable
private fun MonitorRow(row: WidgetRow, inner: Dp, c: Colours, open: Action) {
    Column(GlanceModifier.fillMaxWidth().padding(bottom = Gap)) {
        Column(GlanceModifier.fillMaxWidth().background(c.card).cornerRadius(8.dp).padding(horizontal = 8.dp, vertical = 6.dp).clickable(open)) {
            Text(row.title, style = TextStyle(color = c.muted, fontSize = Caption, fontWeight = FontWeight.Medium), maxLines = 1)
            Spacer(GlanceModifier.height(2.dp))
            when {
                row.error.isNotEmpty() -> Text(row.error, style = TextStyle(color = c.warn, fontSize = Caption), maxLines = 2)
                row.stats.isEmpty() -> Text("Open for details", style = TextStyle(color = c.muted, fontSize = Caption), maxLines = 1)
                else -> {
                    val n = statsPerLine(row.stats.size, inner.value, 72f, Gap.value)
                    for ((i, line) in row.stats.chunked(n).withIndex()) {
                        if (i > 0) Spacer(GlanceModifier.height(Gap))
                        // Stats on a line have the same parts, so they're one height.
                        val labels = line.any { it.label.isNotEmpty() }
                        val details = line.any { it.detail.isNotEmpty() }
                        Row(GlanceModifier.fillMaxWidth()) {
                            for (k in 0 until n) {
                                if (k > 0) Spacer(GlanceModifier.width(Gap))
                                val s = line.getOrNull(k)
                                if (s == null) Spacer(GlanceModifier.defaultWeight())
                                else StatCell(s, row.chips, labels, details, c, GlanceModifier.defaultWeight())
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A stat as the app draws it: a chip filled with its tone's colour, or a plain tile. */
@Composable
private fun StatCell(s: WidgetStat, chip: Boolean, labels: Boolean, details: Boolean, c: Colours, modifier: GlanceModifier) {
    val back = if (chip) GlanceModifier.background(c.chip(s.tone)).cornerRadius(4.dp) else GlanceModifier
    val labelColour = if (chip && s.tone != Tone.Muted) c.text else c.muted
    val valueColour = if (chip && !coloured(s.tone) && s.tone != Tone.Muted) c.text else c.tone(s.tone)
    Column(modifier.then(back).padding(horizontal = if (chip) 6.dp else 2.dp, vertical = 3.dp)) {
        if (labels) Text(s.label.replace('\n', ' ').ifEmpty { " " }, style = TextStyle(color = labelColour, fontSize = Caption), maxLines = 1)
        Text(s.value, style = TextStyle(color = valueColour, fontSize = Value, fontWeight = FontWeight.Bold), maxLines = 1)
        if (details) Text(s.detail.replace('\n', ' ').ifEmpty { " " }, style = TextStyle(color = c.muted, fontSize = Caption), maxLines = 1)
    }
}

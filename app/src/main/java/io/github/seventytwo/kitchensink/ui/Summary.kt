package io.github.seventytwo.kitchensink.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.github.seventytwo.kitchensink.draw.DrawScopeShapes
import io.github.seventytwo.kitchensink.draw.Vec2
import io.github.seventytwo.kitchensink.draw.drawWeatherIcon
import io.github.seventytwo.kitchensink.draw.isWeatherIcon
import io.github.seventytwo.kitchensink.model.ChipMeasure
import io.github.seventytwo.kitchensink.model.ChipSpacing
import io.github.seventytwo.kitchensink.model.Spark
import io.github.seventytwo.kitchensink.model.Stat
import io.github.seventytwo.kitchensink.model.Tone
import io.github.seventytwo.kitchensink.model.isBar
import io.github.seventytwo.kitchensink.model.layoutChips
import kotlin.math.PI
import kotlin.math.cos

/** A monitor's summary: chips if it asks for them, else tiles. */
@Composable
fun Summary(stats: List<Stat>, style: String, modifier: Modifier = Modifier) {
    if (stats.isEmpty()) return
    if (style == "chips") Chips(stats, modifier) else Tiles(stats, modifier)
}

/** Each line of [text] measured on one line, shortened with an ellipsis to fit [width] when given. */
private fun measureLines(m: TextMeasurer, text: String, style: TextStyle, width: Int? = null): List<TextLayoutResult> =
    text.split('\n').map {
        m.measure(
            it, style, overflow = TextOverflow.Ellipsis, softWrap = false, maxLines = 1,
            constraints = if (width != null) Constraints(maxWidth = maxOf(width, 0)) else Constraints(),
        )
    }

/** A stat's tag as drawn: small capitals. */
fun tagText(tag: String) = tag.uppercase()

/** A tag's size against the caption's. */
private const val TagScale = 0.72f

/**
 * Draws a chip's price line in the box at [at], [size] across: the
 * session's points (dimmed once it's over), then any extended trading
 * dotted after a tick, over a faint dotted line at the base (the previous
 * close). It scales to fit them all.
 */
fun DrawScope.drawSpark(s: Spark, at: Offset, size: Size, p: Palette) {
    val n = s.points.size + s.ext.size
    if (s.points.isEmpty() || n < 2) return
    val all = s.points + s.ext + listOfNotNull(s.base)
    val lo = all.min()
    val range = maxOf(all.max() - lo, 1e-6f)
    val thick = 1.5.dp.toPx()
    val unit = 1.dp.toPx()
    fun x(i: Int) = at.x + size.width * i / (n - 1)
    fun y(v: Float) = at.y + thick / 2 + (size.height - thick) * (1 - (v - lo) / range)
    fun colour(t: Tone) = if (t == Tone.None) p.muted else p.tone(t)
    s.base?.let {
        drawLine(
            p.muted.copy(alpha = 0.5f), Offset(at.x, y(it)), Offset(at.x + size.width, y(it)), unit,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(unit * 1.5f, unit * 1.5f)),
        )
    }
    val line = Path().apply { s.points.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
    if (s.points.size >= 2) drawPath(line, colour(s.tone).copy(alpha = if (s.faded) 0.55f else 1f), style = Stroke(thick, join = StrokeJoin.Round))
    if (s.ext.isNotEmpty()) {
        val tx = x(s.points.size - 1)
        drawLine(p.muted.copy(alpha = 0.6f), Offset(tx, at.y), Offset(tx, at.y + size.height), unit)
        val ext = Path().apply {
            moveTo(tx, y(s.points.last()))
            s.ext.forEachIndexed { i, v -> lineTo(x(s.points.size + i), y(v)) }
        }
        drawPath(ext, colour(s.extTone), style = Stroke(thick, pathEffect = PathEffect.dashPathEffect(floatArrayOf(unit * 2, unit * 2))))
    }
}

/** Whether a tone is drawn in its colour on a chip, rather than as plain text. */
fun coloured(t: Tone) = t == Tone.Good || t == Tone.Bad || t == Tone.Warn || t == Tone.Accent

/**
 * Stats as small blocks, all one size so they line up; see [layoutChips].
 * Each is underlined in its tone's colour; ones with price lines (the
 * watchlist) are plain blocks with the label and value on one line, any
 * tag small after the value, and the line under them. A lead chip is plain
 * text on one line. A stat's level is drawn faintly behind, joined to its
 * neighbours' as one chart, e.g. the chance of rain. Chips with a link
 * open it when tapped. As a [grid] (a panel's charts) they fill the line
 * in columns, with taller price lines.
 */
@Composable
fun Chips(stats: List<Stat>, modifier: Modifier = Modifier, grid: Boolean = false) {
    val p = LocalPalette.current
    val mt = LocalMetrics.current
    val actions = LocalActions.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = constraints.maxWidth.toFloat()
        val sp = with(density) {
            ChipSpacing(
                padX = 6.dp.toPx(), padY = 4.dp.toPx(), gap = 4.dp.toPx(), minShare = 44.dp.toPx(),
                leadGap = 10.dp.toPx(), sparkH = (if (grid) 40 else 18).dp.roundToPx().toFloat(), sparkGap = 3.dp.roundToPx().toFloat(),
            )
        }
        val caption = mt.caption
        val value = mt.heading
        val tagStyle = caption.copy(fontSize = caption.fontSize * TagScale, lineHeight = caption.lineHeight * TagScale)
        fun tagW(s: Stat) = if (s.tag.isEmpty()) 0f else measurer.measure(tagText(s.tag), tagStyle, softWrap = false, maxLines = 1).size.width + sp.sparkGap
        val layout = remember(stats, width, caption, value, grid) {
            val ms = stats.map { s ->
                val label = measureLines(measurer, s.label, caption)
                val v = measurer.measure(s.value, value, softWrap = false, maxLines = 1)
                val d = if (s.detail.isEmpty()) emptyList() else measureLines(measurer, s.detail, caption)
                ChipMeasure(
                    labelW = label.maxOf { it.size.width }.toFloat(),
                    labelH = if (s.label.isEmpty()) 0f else label.sumOf { it.size.height }.toFloat(),
                    valueW = v.size.width.toFloat(),
                    valueH = if (s.value.isEmpty()) 0f else v.size.height.toFloat(),
                    detailW = d.maxOfOrNull { it.size.width }?.toFloat() ?: 0f,
                    detailH = d.sumOf { it.size.height }.toFloat(),
                    icon = isWeatherIcon(s.icon),
                    lead = s.lead,
                    spark = s.hasSpark,
                    tagW = tagW(s),
                )
            }
            layoutChips(ms, width, sp, grid)
        }
        val px = sp.padX
        val py = sp.padY
        val levels = stats.any { it.level != null && !it.lead }
        val icons = p.iconColours()
        val heightDp = with(density) { layout.total.toDp() }
        Canvas(Modifier.fillMaxWidth().height(heightDp)) {
            val shapes = DrawScopeShapes(this)
            for ((i, s) in stats.withIndex()) {
                val ax = layout.x[i]
                val ay = layout.y[i]
                val w = layout.widths[i]
                val h = layout.height
                val inner = (w - px * 2).toInt()
                val tone = p.tone(s.tone)
                val col = coloured(s.tone)
                val valueColour = if (col) tone else if (s.tone == Tone.Muted) p.muted else p.text
                val labelColour = if (s.tone == Tone.Muted) p.muted else p.text
                if (s.lead || layout.sparks) {
                    // The label and value on one line, resting on the same
                    // bottom: a lead centred in the row's height, a price
                    // line's chip at its top with the line beneath.
                    val vy = if (s.lead) ay + (h - layout.valueH) / 2 else ay + py
                    val v = measurer.measure(s.value, value.copy(color = valueColour), softWrap = false, maxLines = 1)
                    val l = measurer.measure(s.label, caption.copy(color = labelColour), softWrap = false, maxLines = 1)
                    val ly = vy + v.firstBaseline - l.firstBaseline
                    if (s.lead) {
                        drawText(l, topLeft = Offset(ax, ly))
                        drawText(v, topLeft = Offset(ax + l.size.width + sp.gap * 1.5f, vy))
                        continue
                    }
                    drawRect(p.frame, Offset(ax, ay), Size(w, h))
                    drawText(l, topLeft = Offset(ax + px, ly))
                    val tw = tagW(s)
                    drawText(v, topLeft = Offset(ax + w - px - tw - v.size.width, vy))
                    if (tw > 0) {
                        val t = measurer.measure(tagText(s.tag), tagStyle.copy(color = p.muted), softWrap = false, maxLines = 1)
                        drawText(t, topLeft = Offset(ax + w - px - tw + sp.sparkGap, vy + v.firstBaseline - t.firstBaseline))
                    }
                    val sparkTop = ay + py + layout.valueH + sp.sparkGap
                    if (s.hasSpark) drawSpark(s.spark!!, Offset(ax + px, sparkTop), Size(inner.toFloat(), sp.sparkH), p)
                    if (s.detail.isNotEmpty()) {
                        var dy = sparkTop + sp.sparkH
                        for (d in measureLines(measurer, s.detail, caption.copy(color = p.muted), inner)) {
                            drawText(d, topLeft = Offset(ax + px, dy))
                            dy += d.size.height
                        }
                    }
                    continue
                }
                if (levels) {
                    // The levels as one faint chart behind the row: through the
                    // middle of each block at its own level, and at each edge
                    // halfway to its neighbour's.
                    fun lv(j: Int) = stats[j].level ?: 0f
                    val mid = lv(i)
                    val left = if (layout.rowStart[i]) mid else (lv(i - 1) + mid) / 2
                    val right = if (i + 1 == stats.size || layout.rowStart[i + 1]) mid else (lv(i + 1) + mid) / 2
                    val steps = 10
                    val pts = (0..steps * 2).map { k ->
                        val u = k.toFloat() / (steps * 2)
                        val from = if (k <= steps) left else mid
                        val to = if (k <= steps) mid else right
                        val e = (1 - cos((if (k <= steps) k else k - steps).toFloat() / steps * PI.toFloat())) / 2
                        Offset(ax + u * w, ay + h - (from + (to - from) * e) * h)
                    }
                    val fill = Path().apply {
                        moveTo(ax, ay + h)
                        pts.forEach { lineTo(it.x, it.y) }
                        lineTo(ax + w, ay + h)
                        close()
                    }
                    drawPath(fill, p.accent.copy(alpha = 0.13f))
                    // At 0% the line still shows, along the bottom.
                    val thick = 1.25f * density.density
                    val floor = ay + h - thick / 2
                    val line = Path().apply {
                        pts.forEachIndexed { k, o -> if (k == 0) moveTo(o.x, minOf(o.y, floor)) else lineTo(o.x, minOf(o.y, floor)) }
                    }
                    drawPath(line, p.accent.copy(alpha = 0.5f), style = Stroke(thick))
                } else {
                    // No block: a line along the bottom in the tone's colour.
                    val t = 2.dp.roundToPx().toFloat()
                    drawRect(if (col) tone else p.border, Offset(ax, ay + h - t), Size(w, t))
                }
                var ty = ay + py
                for (l in measureLines(measurer, s.label, caption.copy(color = labelColour), inner)) {
                    drawText(l, topLeft = Offset(ax + px, ty))
                    ty += l.size.height
                }
                val icon = isWeatherIcon(s.icon)
                val vw = inner - if (icon) (layout.iconSize + sp.gap).toInt() else 0
                val v = measurer.measure(
                    s.value, value.copy(color = valueColour), overflow = TextOverflow.Ellipsis, softWrap = false, maxLines = 1,
                    constraints = Constraints(maxWidth = maxOf(vw, 0)),
                )
                drawText(v, topLeft = Offset(ax + px, ay + py + layout.labelH))
                if (icon) {
                    val size = layout.iconSize
                    drawWeatherIcon(shapes, s.icon, Vec2(ax + w - px - size, ay + py + layout.labelH + layout.valueH - size), size, icons)
                }
                if (s.detail.isNotEmpty()) {
                    var dy = ay + py + layout.labelH + layout.valueH
                    for (l in measureLines(measurer, s.detail, caption.copy(color = p.muted), inner)) {
                        drawText(l, topLeft = Offset(ax + px, dy))
                        dy += l.size.height
                    }
                }
            }
        }
        // Chips with a link open it when tapped, rather than the row.
        for ((i, s) in stats.withIndex()) {
            if (s.url.isEmpty()) continue
            with(density) {
                Box(
                    Modifier.offset(layout.x[i].toDp(), layout.y[i].toDp()).size(layout.widths[i].toDp(), layout.height.toDp())
                        .tappable { actions.open(s.url) },
                )
            }
        }
    }
}

/**
 * Stats as tiles of label, value and detail, flowing onto more lines when
 * they don't fit. A bar tile is one line: its label, the bar, then its
 * value, without its detail (Windows keeps that for a tooltip); a summary
 * of nothing but bars (Claude's limits) shares the line between them, so
 * they run across rather than sit stubby.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Tiles(stats: List<Stat>, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val mt = LocalMetrics.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val details = stats.any { !it.isBar && it.detail.isNotEmpty() }
    val spacing = 24.dp
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val max = maxWidth
        val barShare = if (stats.all { it.isBar }) {
            val n = stats.size
            minOf((max - spacing * (n - 1)) / n, 280.dp)
        } else 0.dp
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (s in stats) {
                if (s.isBar) {
                    val natural = with(density) {
                        (measurer.measure(s.label, mt.caption, maxLines = 1).size.width + measurer.measure(s.value, mt.caption, maxLines = 1).size.width).toDp()
                    } + BarGap * 2 + BarMin
                    BarTile(s, Modifier.width(minOf(maxOf(natural, barShare), max)))
                    continue
                }
                Column(Modifier.widthIn(max = max)) {
                    Text(s.label, style = mt.caption, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.value, style = mt.heading, color = p.tone(s.tone), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (details) Text(s.detail.ifEmpty { " " }, style = mt.caption, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private val BarGap = 6.dp
private val BarMin = 48.dp

/** A summary's bar tile on one line: its label, a bar filled to its level, then its value. */
@Composable
private fun BarTile(s: Stat, modifier: Modifier) {
    val p = LocalPalette.current
    val mt = LocalMetrics.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(s.label, style = mt.caption, color = p.muted, maxLines = 1)
        Canvas(Modifier.padding(horizontal = BarGap).weight(1f).height(5.dp)) {
            drawRect(p.frame)
            drawRect(barFill(p, s.tone), size = Size(size.width * (s.level ?: 0f), size.height))
        }
        Text(s.value, style = mt.caption, color = p.tone(s.tone), maxLines = 1)
    }
}

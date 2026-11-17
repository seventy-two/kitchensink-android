package io.github.seventytwo.kitchensink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.seventytwo.kitchensink.model.Fit
import io.github.seventytwo.kitchensink.model.Panel
import io.github.seventytwo.kitchensink.model.Pin
import io.github.seventytwo.kitchensink.model.Show
import io.github.seventytwo.kitchensink.model.Snapshot
import io.github.seventytwo.kitchensink.model.ago
import io.github.seventytwo.kitchensink.model.fitPanels
import io.github.seventytwo.kitchensink.model.isBar
import io.github.seventytwo.kitchensink.model.repin
import io.github.seventytwo.kitchensink.model.summaryOnly

private val Slot = 36.dp

@Composable
private fun CardBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Box(modifier.fillMaxWidth().background(p.card).border(1.dp, p.border).bevel()) { content() }
}

/** The panel's subtitle, error and blocks. */
@Composable
private fun PanelBody(s: Snapshot, wrapSubtitle: Boolean = true) {
    val p = LocalPalette.current
    val mt = LocalMetrics.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (wrapSubtitle && s.subtitle.isNotEmpty()) Text(s.subtitle, style = mt.caption, color = p.muted)
        if (s.error.isNotEmpty()) ErrorLine(s.error)
        if (s.summaryOnly()) Summary(s.summary, s.summaryStyle)
        else if (s.blocks.isEmpty() && s.error.isEmpty()) Text("Waiting for the first check…", style = mt.body, color = p.muted)
        for (b in s.blocks) BlockView(s.monitor, b)
    }
}

@Composable
private fun ErrorLine(error: String) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.Warning, null, Modifier.padding(top = 2.dp).width(14.dp), tint = p.warn)
        Spacer(Modifier.width(6.dp))
        Text(error, style = LocalMetrics.current.caption, color = p.warn)
    }
}

@Composable
private fun RefreshButton(s: Snapshot) {
    val actions = LocalActions.current
    val pending = s.monitor in LocalPanelState.current.refreshing
    IconButtonSmall(Icons.Filled.Refresh, if (pending) "Checking…" else "Check now", size = Slot, enabled = !pending) {
        actions.refresh(s.monitor)
    }
}

/**
 * One monitor in the compact layout: collapsed, a row of its summary;
 * open, its full panel at its full height, with a strip down the right
 * holding the close chevron and refresh. A [slim] row is one slim line
 * (ultra compact): its summary as label and value pairs, a short table's
 * first row only, no subtitle; open, it's the full panel all the same.
 * [onToggle] opens or closes it; a long press on the row or the chevron
 * offers compact or ultra compact for this panel, given to [onStyle].
 */
@Composable
fun CompactCard(s: Snapshot, show: Show, slim: Boolean = false, onStyle: ((Boolean) -> Unit)? = null, onToggle: (() -> Unit)?) {
    val p = LocalPalette.current
    val mt = LocalMetrics.current
    val state = LocalPanelState.current
    val toggle = onToggle ?: {}
    var menu by remember { mutableStateOf(false) }
    val longPress: (() -> Unit)? = onStyle?.let { { menu = true } }
    CardBox {
        if (onStyle != null) RowStyleMenu(menu, slim, { menu = false }, onStyle)
        if (show == Show.Row && slim) {
            SlimRow(s, toggle, longPress)
        } else if (show == Show.Row) {
            val failed = s.error.isNotEmpty()
            val pending = s.monitor in state.refreshing
            val whenText = when {
                failed -> "Failed"
                pending -> "refreshing…"
                s.updated == 0L -> "waiting…"
                else -> ""
            }
            val only = s.summaryOnly()
            Column(Modifier.fillMaxWidth().tappable(enabled = !only, onLongClick = longPress, onClick = toggle).padding(start = mt.cardPadH, end = 4.dp, top = mt.cardPadV, bottom = mt.cardPadV)) {
                @Composable
                fun right() {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (whenText.isNotEmpty()) {
                            if (failed) Icon(Icons.Filled.Warning, null, Modifier.width(13.dp), tint = p.warn)
                            Text(whenText, style = mt.caption, color = if (failed) p.warn else p.muted, modifier = Modifier.padding(start = 4.dp, end = 4.dp))
                        }
                        // A monitor with only a summary never opens: no chevron, but its room kept.
                        if (only) Spacer(Modifier.width(20.dp))
                        else Icon(Icons.Filled.KeyboardArrowDown, "Open", Modifier.width(20.dp), tint = p.muted)
                    }
                }
                @Composable
                fun summary(modifier: Modifier) {
                    Box(modifier) {
                        when {
                            // A short table with no rows (Big box with nothing worth a
                            // look today) shows nothing, not even its headings.
                            s.summaryBlock?.type == "table" && s.summaryBlock.rows.isEmpty() -> {}
                            s.summaryBlock != null -> BlockView(s.monitor, s.summaryBlock)
                            s.summary.isNotEmpty() -> Summary(s.summary, s.summaryStyle)
                            s.blocks.isEmpty() && !failed -> Text("Waiting for the first check…", style = mt.body, color = p.muted)
                            else -> Text("Tap for details", style = mt.body, color = p.muted)
                        }
                    }
                }
                // A hover-only subtitle is for a closer look: touch has no hover, so a row leaves it out.
                if (s.subtitle.isNotEmpty() && !s.subtitleHover) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.subtitle, style = mt.caption, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        right()
                    }
                    summary(Modifier.fillMaxWidth().padding(end = 10.dp, top = 2.dp))
                } else {
                    // A line of bars (Claude's limits) sits level with the text beside it.
                    val bars = s.summaryBlock == null && s.summary.isNotEmpty() && s.summary.all { it.isBar }
                    Row(verticalAlignment = if (bars) Alignment.CenterVertically else Alignment.Top) {
                        summary(Modifier.weight(1f).padding(end = 8.dp))
                        right()
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(start = mt.cardPadH, top = mt.cardPadV, bottom = mt.cardPadV)) {
                Box(Modifier.weight(1f)) { PanelBody(s) }
                // The strip closes the panel when tapped.
                Column(Modifier.width(Slot + 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(Slot).tappable(onLongClick = longPress, onClick = toggle).semantics { contentDescription = "Close" }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.KeyboardArrowUp, null, Modifier.size(22.dp), tint = p.muted)
                    }
                    RefreshButton(s)
                    if (s.timely && s.updated > 0) {
                        Text(ago(s.updated, state.now).removeSuffix(" ago"), style = mt.caption, color = p.muted, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** A long press's menu: this panel's closed row as compact or ultra compact. */
@Composable
private fun RowStyleMenu(open: Boolean, slim: Boolean, onDismiss: () -> Unit, onStyle: (Boolean) -> Unit) {
    val p = LocalPalette.current
    val mt = LocalMetrics.current
    DropdownMenu(open, onDismiss, containerColor = p.card) {
        for ((label, v) in listOf("Compact" to false, "Ultra compact" to true)) {
            DropdownMenuItem(
                text = { Text(label, style = mt.body, color = p.text) },
                leadingIcon = { if (v == slim) Icon(Icons.Filled.Check, null, tint = p.accent) else Spacer(Modifier.size(24.dp)) },
                onClick = {
                    onDismiss()
                    onStyle(v)
                },
            )
        }
    }
}

/**
 * A closed panel on one slim line, for ultra compact: the summary as label
 * and value pairs (or a short block's first row, without headings), then
 * whatever it has to say about its check, and the chevron.
 */
@Composable
private fun SlimRow(s: Snapshot, toggle: () -> Unit, longPress: (() -> Unit)?) {
    val p = LocalPalette.current
    val mt = LocalMetrics.current
    val state = LocalPanelState.current
    val failed = s.error.isNotEmpty()
    val pending = s.monitor in state.refreshing
    val whenText = when {
        failed -> "Failed"
        pending -> "refreshing…"
        s.updated == 0L -> "waiting…"
        else -> ""
    }
    val only = s.summaryOnly()
    Row(
        Modifier.fillMaxWidth().tappable(enabled = !only, onLongClick = longPress, onClick = toggle)
            .padding(start = mt.cardPadH, end = 4.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).padding(end = 8.dp)) {
            val b = s.summaryBlock
            when {
                b?.type == "table" && b.rows.isEmpty() -> {}
                b != null -> CompositionLocalProvider(LocalSlim provides true) { BlockView(s.monitor, b.copy(title = "")) }
                s.summary.isNotEmpty() -> SlimStats(s.summary, s.summaryStyle == "chips")
                s.blocks.isEmpty() && !failed -> Text("Waiting for the first check…", style = mt.caption, color = p.muted)
                else -> Text("Tap for details", style = mt.caption, color = p.muted)
            }
        }
        if (whenText.isNotEmpty()) {
            if (failed) Icon(Icons.Filled.Warning, null, Modifier.width(13.dp), tint = p.warn)
            Text(whenText, style = mt.caption, color = if (failed) p.warn else p.muted, maxLines = 1, modifier = Modifier.padding(horizontal = 4.dp))
        }
        if (only) Spacer(Modifier.width(20.dp))
        else Icon(Icons.Filled.KeyboardArrowDown, "Open", Modifier.width(20.dp).height(18.dp), tint = p.muted)
    }
}

/** Keeps what the last layout measured, for working out a tap's new pin. */
private class Measured {
    var panels: List<Panel> = emptyList()
    var fit = Fit(emptyList())
}

/**
 * Fills the space with one row per monitor, full width so the summaries
 * line up, opening as many full panels as fit [viewport] whole (see
 * fitPanels). Stale panels, with nothing new for a long while, fade and
 * don't open by themselves. Tapping a row opens or closes it, and [onPin] keeps that
 * choice while it differs from what would happen by itself; the column
 * scrolls if open panels run past the screen.
 */
@Composable
fun CompactPanels(
    panels: List<Snapshot>,
    pins: Map<String, Pin>,
    stale: Set<String>,
    viewport: Dp,
    onPin: (String, Pin) -> Unit,
    modifier: Modifier = Modifier,
    slim: (String) -> Boolean = { false },
    onStyle: ((String, Boolean) -> Unit)? = null,
    gap: Dp = LocalMetrics.current.gap,
) {
    val density = LocalDensity.current
    val measured = remember { Measured() }
    val gapPx = with(density) { gap.toPx() }
    val heightPx = with(density) { viewport.toPx() }

    fun tapped(i: Int) {
        val ps = measured.panels
        if (i >= ps.size || i >= panels.size || panels[i].summaryOnly()) return
        val open = measured.fit.show[i] == Show.Row
        onPin(panels[i].monitor, repin(ps, i, open, heightPx, gapPx))
    }

    SubcomposeLayout(modifier) { constraints ->
        val w = constraints.maxWidth
        val cw = Constraints(minWidth = w, maxWidth = w)
        // Each monitor measured each way at this width.
        val rows = panels.mapIndexed { i, s ->
            subcompose(s.monitor + "#row") {
                Box(if (s.monitor in stale) Modifier.alpha(StaleAlpha) else Modifier) {
                    CompactCard(s, Show.Row, slim(s.monitor), onStyle?.let { f -> { v -> f(s.monitor, v) } }) { tapped(i) }
                }
            }.first().measure(cw)
        }
        val fulls = panels.mapIndexed { i, s -> subcompose(s.monitor + "#full") {
                CompactCard(s, Show.Full, slim(s.monitor), onStyle?.let { f -> { v -> f(s.monitor, v) } }) { tapped(i) }
            }.first().measure(cw) }
        val ps = panels.mapIndexed { i, s ->
            Panel(rows[i].height.toFloat(), fulls[i].height.toFloat(), if (s.summaryOnly()) Pin.Closed else pins[s.monitor] ?: Pin.Auto, s.monitor in stale)
        }
        val fit = fitPanels(ps, heightPx, gapPx)
        measured.panels = ps
        measured.fit = fit
        // The row and full copies are the real thing.
        val placed = panels.indices.map { i -> if (fit.show[i] == Show.Full) fulls[i] else rows[i] }
        val g = gapPx.toInt()
        val total = placed.sumOf { it.height } + g * maxOf(0, placed.size - 1)
        layout(w, total) {
            var y = 0
            for (pl in placed) {
                pl.place(0, y)
                y += pl.height + g
            }
        }
    }
}

/** One monitor's full panel, for screens with room for them all; [onCollapse] folds it to its summary row. */
@Composable
fun FullCard(s: Snapshot, onCollapse: (() -> Unit)? = null) {
    val p = LocalPalette.current
    val mt = LocalMetrics.current
    val state = LocalPanelState.current
    CardBox {
        Column(Modifier.padding(start = mt.cardPadH, end = 6.dp, top = 6.dp, bottom = mt.cardPadV)) {
            // What's being watched on the left; when it was updated (if that
            // matters now) and refresh on the right.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.subtitle, style = mt.caption, color = p.muted, modifier = Modifier.weight(1f))
                val pending = s.monitor in state.refreshing
                val whenText = if (pending) "refreshing…" else if (s.timely && s.updated > 0) "updated " + ago(s.updated, state.now) else ""
                if (whenText.isNotEmpty()) Text(whenText, style = mt.caption, color = p.muted)
                RefreshButton(s)
                if (onCollapse != null) IconButtonSmall(Icons.Filled.KeyboardArrowUp, "Collapse", size = Slot, iconSize = 22.dp, tint = p.muted, onClick = onCollapse)
            }
            Box(Modifier.padding(end = mt.cardPadH - 6.dp)) { PanelBody(s, wrapSubtitle = false) }
        }
    }
}

/** How much a stale panel fades. */
private const val StaleAlpha = 0.5f

/**
 * Full panels in columns about 440dp wide, each going in the shortest
 * column so far; stale ones fade. A panel can be folded to its summary row
 * even when there's room for it (its chevron), kept in [collapsed] as the
 * compact layout's are; [onCollapse] folds or unfolds one.
 */
@Composable
fun MasonryPanels(
    panels: List<Snapshot>,
    stale: Set<String>,
    modifier: Modifier = Modifier,
    collapsed: Set<String> = emptySet(),
    onCollapse: ((String, Boolean) -> Unit)? = null,
    slim: (String) -> Boolean = { false },
    onStyle: ((String, Boolean) -> Unit)? = null,
) {
    val gap = LocalMetrics.current.gap
    Layout(content = {
        for (s in panels) Box(if (s.monitor in stale) Modifier.alpha(StaleAlpha) else Modifier) {
            if (s.monitor in collapsed || s.summaryOnly()) {
                CompactCard(s, Show.Row, slim(s.monitor), onStyle?.let { f -> { v -> f(s.monitor, v) } }) { onCollapse?.invoke(s.monitor, false) }
            } else {
                FullCard(s, onCollapse?.let { f -> { f(s.monitor, true) } })
            }
        }
    }, modifier = modifier) { measurables, constraints ->
        val g = gap.roundToPx()
        val width = constraints.maxWidth
        val cols = maxOf(1, (width + g) / (440.dp.roundToPx() + g)).coerceAtMost(maxOf(1, measurables.size))
        val colW = (width - g * (cols - 1)) / cols
        val heights = IntArray(cols)
        val places = measurables.map { m ->
            val pl = m.measure(Constraints.fixedWidth(colW))
            val c = heights.indices.minBy { heights[it] }
            val at = Triple(pl, c * (colW + g), heights[c])
            heights[c] += pl.height + g
            at
        }
        layout(width, maxOf(0, (heights.maxOrNull() ?: 0) - g)) {
            for ((pl, x, y) in places) pl.place(x, y)
        }
    }
}


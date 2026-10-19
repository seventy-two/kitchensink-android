package io.github.seventytwo.kitchensink.widget

import io.github.seventytwo.kitchensink.data.Config
import io.github.seventytwo.kitchensink.data.ThemeChoice
import io.github.seventytwo.kitchensink.model.Snapshot
import io.github.seventytwo.kitchensink.model.Stat
import io.github.seventytwo.kitchensink.model.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WidgetDataTest {
    private val configured = Config(url = "https://example.com/kitchensink", token = "x")
    private val snapshots = listOf(
        Snapshot(monitor = "weather", title = "Weather", updated = 100, summaryStyle = "chips", summary = listOf(Stat("Now", "12°", tone = Tone.Good))),
        Snapshot(monitor = "stocks", title = "Stocks", updated = 300, summary = listOf(Stat("Portfolio", "£1", isPrivate = true), Stat("NVDA", "+2%"))),
        Snapshot(monitor = "mail", title = "Mail", background = true, summary = listOf(Stat("Inbox", "3"))),
        Snapshot(monitor = "todo", updated = 200, error = "timed out"),
    )

    @Test
    fun rowsFollowThePanels() {
        val d = widgetData(snapshots, configured.copy(order = listOf("todo", "stocks"), theme = ThemeChoice.Retro))
        // Background monitors aren't panels; ones the saved order doesn't
        // mention keep their place.
        assertEquals(listOf("weather", "todo", "stocks"), d.rows.map { it.monitor })
        assertEquals("todo", d.rows[1].title)
        assertEquals("timed out", d.rows[1].error)
        assertEquals(WidgetRow("weather", "Weather", listOf(WidgetStat("Now", "12°", tone = Tone.Good)), chips = true), d.rows[0])
        assertEquals(300, d.updated)
        assertEquals(ThemeChoice.Retro, d.theme)
    }

    @Test
    fun privateFiguresStayHidden() {
        val d = widgetData(snapshots, configured.copy(hidePrivate = true))
        assertEquals(listOf("NVDA"), d.rows.first { it.monitor == "stocks" }.stats.map { it.label })
        assertEquals(2, widgetData(snapshots, configured).rows.first { it.monitor == "stocks" }.stats.size)
    }

    @Test
    fun notSetUp() {
        assertFalse(widgetData(emptyList(), Config()).configured)
    }

    @Test
    fun roundTrips() {
        val d = widgetData(snapshots, configured)
        assertEquals(d, WidgetData.decode(d.encode()))
        assertEquals(WidgetData(), WidgetData.decode("not json"))
    }

    @Test
    fun statsShareLinesEvenly() {
        // 4 fit in 316dp at 72dp with 4dp gaps.
        assertEquals(4, statsPerLine(4, 316f, 72f, 4f))
        assertEquals(3, statsPerLine(5, 316f, 72f, 4f))
        assertEquals(4, statsPerLine(7, 316f, 72f, 4f))
        assertEquals(2, statsPerLine(2, 316f, 72f, 4f))
        assertEquals(1, statsPerLine(3, 50f, 72f, 4f))
        assertEquals(1, statsPerLine(0, 316f, 72f, 4f))
    }
}

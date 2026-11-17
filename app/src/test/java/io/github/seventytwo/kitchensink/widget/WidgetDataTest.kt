package io.github.seventytwo.kitchensink.widget

import io.github.seventytwo.kitchensink.data.Config
import io.github.seventytwo.kitchensink.data.ThemeChoice
import io.github.seventytwo.kitchensink.model.Snapshot
import io.github.seventytwo.kitchensink.model.Spark
import io.github.seventytwo.kitchensink.model.Stat
import io.github.seventytwo.kitchensink.model.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun carriesChipsExtrasAndSlimRows() {
        val s = Snapshot(
            monitor = "stocks", summaryStyle = "chips",
            summary = listOf(
                Stat("Today", "£1", lead = true),
                Stat("ACME", "-1%", tone = Tone.Bad, tag = "pre", spark = Spark(listOf(1f, 2f), base = 1f)),
                Stat("Week", "31%", bar = true, level = 0.31f),
                Stat("Halloumi", "Monday", brief = ""),
                Stat("Odd", "1", spark = Spark(listOf(1f))),
            ),
        )
        val c = configured.copy(layout = io.github.seventytwo.kitchensink.data.LayoutChoice.Ultra)
        val row = widgetData(listOf(s), c).rows[0]
        assertTrue(row.slim)
        assertTrue(row.stats[0].lead)
        assertEquals(WidgetSpark(listOf(1f, 2f), base = 1f), row.stats[1].spark)
        assertEquals("pre", row.stats[1].tag)
        assertTrue(row.stats[2].bar)
        assertEquals(0.31f, row.stats[2].level)
        assertEquals("", row.stats[3].brief)
        assertNull(row.stats[4].spark) // one point isn't a line
        assertFalse(widgetData(listOf(s), c.withRowStyle("stocks", false)).rows[0].slim)
    }

    @Test
    fun fitsASlimLine() {
        val stats = List(5) { WidgetStat("AB", "12") } // 2*6.5 + 4 + 2*9.5 + 4 = 40 each
        assertEquals(5, slimFit(stats, 500f, 6f))
        // Two (40 + 6 + 40) fit in 110dp with room for a "+3" (24); in 100 only one.
        assertEquals(2, slimFit(stats, 110f, 6f))
        assertEquals(1, slimFit(stats, 100f, 6f))
    }
}

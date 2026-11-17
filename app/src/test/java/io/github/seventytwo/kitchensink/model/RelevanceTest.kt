package io.github.seventytwo.kitchensink.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RelevanceTest {
    @Test
    fun fingerprintsSummaries() {
        val s = Snapshot(monitor = "m")
        assertEquals(0L, summaryFingerprint(s))
        val a = s.copy(summary = listOf(Stat("NVDA", "-3.17%")))
        assertNotEquals(0L, summaryFingerprint(a))
        assertNotEquals(summaryFingerprint(a), summaryFingerprint(a.copy(summary = listOf(Stat("NVDA", "-3.20%")))))
        // A short table's rows trading places isn't a change; a new row is.
        val t = Snapshot(summaryBlock = Block(type = "table", rows = listOf(listOf(Cell("one")), listOf(Cell("two")))))
        val f = summaryFingerprint(t)
        assertEquals(f, summaryFingerprint(t.copy(summaryBlock = t.summaryBlock!!.copy(rows = t.summaryBlock.rows.reversed()))))
        assertNotEquals(f, summaryFingerprint(t.copy(summaryBlock = t.summaryBlock.copy(rows = listOf(listOf(Cell("three")), listOf(Cell("two")))))))
    }

    @Test
    fun matchesTheWindowsFingerprint() {
        // FNV-1a over "a", a separator, "b", a separator, "", a separator: the
        // same sum the Windows client keeps, worked through by hand.
        var h = -3750763034362895579L
        for (c in listOf("a", "b", "")) {
            for (b in c.toByteArray()) h = (h xor b.toLong()) * 1099511628211L
            h = (h xor 0x1f) * 1099511628211L
        }
        assertEquals(h, summaryFingerprint(Snapshot(summary = listOf(Stat("a", "b")))))
    }

    @Test
    fun ranksPanels() {
        val now = 100_000L
        val s = Snapshot(monitor = "m", summary = listOf(Stat("a", "b")))
        assertEquals(Tier.Middle, panelTier(s, News(), now)) // nothing known yet
        assertEquals(Tier.Middle, panelTier(s, News(changed = now - 3600), now))
        assertEquals(Tier.Stale, panelTier(s, News(changed = now - StaleAfter), now))
        assertEquals(Tier.Top, panelTier(s, News(changed = now - StaleAfter, event = now - 600), now)) // an event: top for a while
        assertEquals(Tier.Middle, panelTier(s, News(event = now - TopFor), now))
        // The server saying it matters now beats being stale.
        assertEquals(Tier.Top, panelTier(s.copy(relevantUntil = now + 60), News(changed = now - StaleAfter), now))
        assertEquals(Tier.Stale, panelTier(s.copy(relevantFrom = now + 10, relevantUntil = now + 60), News(changed = now - StaleAfter), now))

        assertEquals(listOf(2, 4, 1, 3, 0), byTier(listOf(Tier.Stale, Tier.Middle, Tier.Top, Tier.Middle, Tier.Top).withIndex().toList()) { it.value }.map { it.index })
    }

    @Test
    fun keepsCalmPanelsLow() {
        // Reddit: its summary changing isn't news, so it's low until the
        // server says it's relevant (a J-Mod's post) or it has an event.
        val now = 100_000L
        val c = Snapshot(calm = true)
        assertEquals(Tier.Stale, panelTier(c, News(changed = now - 60), now))
        assertEquals(Tier.Top, panelTier(c.copy(relevantUntil = now + 600), News(changed = now - 60), now))
        assertEquals(Tier.Middle, panelTier(c, News(event = now - TopFor - 1), now))
        assertEquals(Tier.Stale, panelTier(c, News(event = now - StaleAfter), now))
        val parsed = parseSnapshot("""{"monitor": "reddit", "calm": true, "relevant": {"until": "2026-10-12T15:00:00Z"}}""")
        assertTrue(parsed.calm)
        assertEquals(parseTime("2026-10-12T15:00:00Z"), parsed.relevantUntil)
    }

    @Test
    fun notesNews() {
        val s = Snapshot(monitor = "m", updated = 5, summary = listOf(Stat("a", "1")))
        val once = noteSummary(emptyMap(), s, 100)
        assertEquals(100L, once["m"]!!.changed)
        // The same summary again isn't news.
        assertSame(once, noteSummary(once, s, 200))
        assertEquals(300L, noteSummary(once, s.copy(summary = listOf(Stat("a", "2"))), 300)["m"]!!.changed)
        // Not yet polled: nothing to go on.
        assertTrue(noteSummary(emptyMap(), s.copy(updated = 0), 100).isEmpty())

        val e = Event(monitor = "m", time = 400)
        assertEquals(400L, noteEvent(once, e)["m"]!!.event)
        assertSame(once, noteEvent(once, e.copy(quiet = true)))
        assertEquals(400L, noteEvent(noteEvent(once, e), e.copy(time = 350))["m"]!!.event) // an older one doesn't move it back
    }
}

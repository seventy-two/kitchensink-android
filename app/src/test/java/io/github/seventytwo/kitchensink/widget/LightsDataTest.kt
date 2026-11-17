package io.github.seventytwo.kitchensink.widget

import io.github.seventytwo.kitchensink.data.Config
import io.github.seventytwo.kitchensink.data.ThemeChoice
import io.github.seventytwo.kitchensink.model.Block
import io.github.seventytwo.kitchensink.model.Colour
import io.github.seventytwo.kitchensink.model.Item
import io.github.seventytwo.kitchensink.model.Snapshot
import io.github.seventytwo.kitchensink.model.Stat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LightsDataTest {
    private val configured = Config(url = "https://example.com/kitchensink", token = "x", theme = ThemeChoice.Retro)
    private val lights = Snapshot(
        monitor = "lights",
        summary = listOf(Stat("Lights", "3 of 4 on", lead = true)),
        summaryBlock = Block(
            type = "checklist", switches = true, buttons = true,
            items = listOf(
                Item(title = "Hallway", id = "room:Hallway"),
                Item(title = "Hallway 1", id = "h1", colour = Colour("#ff0000")),
                Item(title = "Hallway 2", id = "h2", done = true, colour = Colour("#00ff00")),
                Item(title = "Study", id = "study"),
            ),
        ),
    )

    @Test
    fun groupsAsTheAppDoes() {
        val d = lightsData(listOf(Snapshot(monitor = "todo"), lights), configured)
        assertEquals("lights", d.monitor)
        assertEquals("3 of 4 on", d.summary)
        assertEquals(ThemeChoice.Retro, d.theme)
        // The room's own row heads its numbered lights, which go by their numbers.
        assertEquals(listOf(listOf("Hallway", "1", "2"), listOf("Study")), d.groups.map { g -> g.map { it.label } })
        assertEquals("Hallway 1", d.groups[0][1].title)
        assertEquals(0xff0000, d.groups[0][1].rgb)
        // Off: no colour, and done means off.
        assertFalse(d.groups[0][2].on)
        assertNull(d.groups[0][2].rgb)
        assertTrue(d.groups[1][0].on)
    }

    @Test
    fun togglesAtOnce() {
        val d = lightsData(listOf(lights), configured)
        val t = d.toggled("h2")
        assertTrue(t.groups[0][2].on)
        assertEquals(d, t.toggled("h2"))
    }

    @Test
    fun withoutLights() {
        val d = lightsData(listOf(Snapshot(monitor = "todo")), configured)
        assertEquals("", d.monitor)
        assertTrue(d.groups.isEmpty())
        assertFalse(lightsData(emptyList(), Config()).configured)
        assertEquals(d, LightsData.decode(d.encode()))
        assertEquals(LightsData(), LightsData.decode("not json"))
    }
}

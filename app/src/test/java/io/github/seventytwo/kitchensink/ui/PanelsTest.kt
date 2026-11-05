package io.github.seventytwo.kitchensink.ui

import io.github.seventytwo.kitchensink.data.Config
import io.github.seventytwo.kitchensink.model.Block
import io.github.seventytwo.kitchensink.model.Item
import io.github.seventytwo.kitchensink.model.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class PanelsTest {
    @Test
    fun backgroundMonitorsAreLeftOff() {
        val snaps = listOf(Snapshot(monitor = "calendar"), Snapshot(monitor = "mail", background = true), Snapshot(monitor = "todo"))
        assertEquals(listOf("calendar", "todo"), orderedPanels(snaps, Config()).map { it.monitor })
        assertEquals(listOf("todo", "calendar"), orderedPanels(snaps, Config(order = listOf("todo", "mail", "calendar"))).map { it.monitor })
    }

    @Test
    fun joinsNumberedSwitchesIntoGroups() {
        val titles = listOf("Living room 1", "Living room 2", "Study", "Hallway 1", "Bedroom 1", "Hallway 2")
        val b = Block(type = "checklist", switches = true, items = titles.map { Item(id = it, title = it) })
        assertEquals(
            listOf(listOf("Living room 1", "Living room 2"), listOf("Study"), listOf("Hallway 1"), listOf("Bedroom 1"), listOf("Hallway 2")),
            buttonGroups(b).map { g -> g.map { it.title } },
        )
        // Only switches are joined.
        assertEquals(6, buttonGroups(b.copy(switches = false)).size)
    }
}


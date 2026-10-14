package io.github.seventytwo.kitchensink.ui

import io.github.seventytwo.kitchensink.data.Config
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
}

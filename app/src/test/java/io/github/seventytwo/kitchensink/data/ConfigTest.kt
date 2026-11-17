package io.github.seventytwo.kitchensink.data

import io.github.seventytwo.kitchensink.model.parseSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigTest {
    @Test
    fun choosesEachPanelsRow() {
        val c = Config()
        assertFalse(c.slimRow("stocks"))
        // A panel's own choice wins; one that matches the layout's is forgotten.
        val slim = c.withRowStyle("stocks", true)
        assertTrue(slim.slimRow("stocks"))
        assertEquals(c, slim.withRowStyle("stocks", false))
        val ultra = Config(layout = LayoutChoice.Ultra)
        assertTrue(ultra.slimRow("weather"))
        val normal = ultra.withRowStyle("stocks", false)
        assertFalse(normal.slimRow("stocks"))
        assertEquals("normal", normal.rowStyle["stocks"])
        assertTrue(normal.slimRow("weather"))
    }

    @Test
    fun readsBriefs() {
        val s = parseSnapshot("""{"monitor":"food","summary":[{"label":"Avocado","value":"Saturday","brief":""},{"label":"Basil","value":"Tomorrow"}]}""")
        assertEquals("", s.summary[0].brief)
        assertNull(s.summary[1].brief)
    }
}

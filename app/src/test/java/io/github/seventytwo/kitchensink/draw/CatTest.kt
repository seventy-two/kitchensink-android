package io.github.seventytwo.kitchensink.draw

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Records how many shapes are drawn and the box they fill. */
private class Recorder : Shapes {
    var shapes = 0
    var minX = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    private fun box(x0: Float, y0: Float, x1: Float, y1: Float) {
        shapes++
        minX = min(minX, x0); maxX = max(maxX, x1); minY = min(minY, y0); maxY = max(maxY, y1)
    }
    override fun circle(c: Vec2, r: Float, col: Color) = box(c.x - r, c.y - r, c.x + r, c.y + r)
    override fun ellipse(c: Vec2, rx: Float, ry: Float, rot: Float, col: Color) {
        // The box round an ellipse turned by rot.
        val w = kotlin.math.hypot(rx * kotlin.math.cos(rot), ry * kotlin.math.sin(rot))
        val h = kotlin.math.hypot(rx * kotlin.math.sin(rot), ry * kotlin.math.cos(rot))
        box(c.x - w, c.y - h, c.x + w, c.y + h)
    }
    override fun line(a: Vec2, b: Vec2, thickness: Float, col: Color) =
        box(min(a.x, b.x) - thickness / 2, min(a.y, b.y) - thickness / 2, max(a.x, b.x) + thickness / 2, max(a.y, b.y) + thickness / 2)
    override fun polygon(points: List<Vec2>, col: Color) =
        box(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })
}

class CatTest {
    @Test
    fun staysInHerSpaceAndGetsUpToAllSorts() {
        // Over many episodes, at a narrow and a wide column, she stays on the
        // floor and inside her space: 68 units tall (4 below the floor) and
        // the column's width. A narrow one has no room for a pounce or a toy.
        for (half in listOf(60f, 150f)) {
            val did = mutableSetOf<CatAction>()
            var asleep = 0
            var frames = 0
            var licked = false
            var looked = false
            var leapt = false
            var drank = false
            var batted = false
            var t = 0.0
            while (t < 30 * CatEpisode) {
                val c = catAt(t, half)
                did += c.action
                frames++
                if (c.asleep >= 1) asleep++
                licked = licked || c.pose.tongue > 0.9f
                looked = looked || c.look > 0.99f
                leapt = leapt || c.lift > 9
                drank = drank || (c.action == CatAction.Drink && c.bowl.alpha >= 1 && c.bowl.fill < 0.5f)
                batted = batted || (c.action == CatAction.Hunt && c.toy.lift > 2)
                if (c.action != CatAction.Drink) assertEquals(0f, c.bowl.alpha)
                if (c.action != CatAction.Hunt) assertEquals(0f, c.toy.alpha)
                for (paw in c.pose.paw) assertTrue(paw.y <= 0.01f)
                val r = Recorder()
                drawCat(r, c, t, Vec2(0f, 0f), 1f)
                assertTrue(r.shapes > 30)
                assertTrue("below the floor at $t", r.maxY <= 4)
                assertTrue("above her space at $t", r.minY >= -64)
                assertTrue("outside the column at $t", r.minX >= -half && r.maxX <= half)
                t += 0.1
            }
            for (a in listOf(CatAction.Stir, CatAction.Groom, CatAction.Scratch, CatAction.Blink, CatAction.Knead, CatAction.Walk)) {
                assertTrue("$a at $half", a in did)
            }
            assertTrue(licked && looked && drank && CatAction.Drink in did)
            assertEquals(half > 100, leapt)
            assertEquals(half > 100, CatAction.Pounce in did)
            assertEquals(half > 100, CatAction.Hunt in did)
            assertEquals(half > 100, batted)
            assertTrue("asleep most of the time", asleep > frames / 2)
        }
    }

    @Test
    fun neverJumps() {
        // From one frame to the next she moves a little, and only turns round
        // by turning, at the end of which she faces the other way.
        for (half in listOf(60f, 150f)) {
            var t = 0.0
            while (t < 40 * CatEpisode) {
                val a = catAt(t, half)
                val b = catAt(t + 0.05, half)
                assertTrue(abs(a.x - b.x) < 3)
                assertTrue(abs(a.pose.head.y - b.pose.head.y) < 3)
                assertTrue(abs(a.lift - b.lift) < 3)
                assertTrue(abs(a.look - b.look) < 0.2f)
                if (a.toy.alpha > 0 && b.toy.alpha > 0) assertTrue(abs(a.toy.x - b.toy.x) < 12)
                if (a.bowl.alpha > 0 && b.bowl.alpha > 0) assertTrue(abs(a.bowl.x - b.bowl.x) < 12)
                if (a.facing != b.facing) assertTrue(a.turn > 0.9f)
                t += 0.05
            }
        }
        // Asked out of order, e.g. after a jump in time, she's the same.
        val later = catAt(37 * CatEpisode + 3, 150f)
        catAt(2 * CatEpisode, 150f)
        val again = catAt(37 * CatEpisode + 3, 150f)
        assertTrue(later.x == again.x && later.facing == again.facing)
    }
}

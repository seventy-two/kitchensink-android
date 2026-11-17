// Pictures for the widgets. Glance can't draw lines, so price lines and bars
// are small bitmaps drawn in white, one per colour, which the widget tints
// with the theme's colours: that way they still follow the launcher's day
// and night.
package io.github.seventytwo.kitchensink.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path

/** A price line's layers, each white on clear, to be tinted: the base (the previous close), the session, and any extended trading with its tick. */
class SparkLayers(val base: Bitmap?, val points: Bitmap, val ext: Bitmap?)

/** How big the price line's bitmaps are, in pixels; the widget stretches them to fit. */
private const val SparkW = 240
private const val SparkH = 54

private fun white(width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.WHITE
    style = Paint.Style.STROKE
    strokeWidth = width
    strokeJoin = Paint.Join.ROUND
    strokeCap = Paint.Cap.ROUND
}

/** Draws a price line as the app's chips do, scaled to fit its points, extended points and base. */
fun sparkLayers(s: WidgetSpark, w: Int = SparkW, h: Int = SparkH): SparkLayers? {
    val n = s.points.size + s.ext.size
    if (s.points.isEmpty() || n < 2) return null
    val all = s.points + s.ext + listOfNotNull(s.base)
    val lo = all.min()
    val range = maxOf(all.max() - lo, 1e-6f)
    val thick = h / 12f
    fun x(i: Int) = w * i / (n - 1f)
    fun y(v: Float) = thick / 2 + (h - thick) * (1 - (v - lo) / range)
    fun layer(draw: Canvas.() -> Unit) = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { Canvas(it).draw() }
    val base = s.base?.let { b ->
        layer {
            drawLine(0f, y(b), w.toFloat(), y(b), white(thick / 2).apply { pathEffect = DashPathEffect(floatArrayOf(thick, thick), 0f) })
        }
    }
    val points = layer {
        val p = Path()
        s.points.forEachIndexed { i, v -> if (i == 0) p.moveTo(x(i), y(v)) else p.lineTo(x(i), y(v)) }
        drawPath(p, white(thick))
    }
    val ext = if (s.ext.isEmpty()) null else layer {
        val tx = x(s.points.size - 1)
        drawLine(tx, 0f, tx, h.toFloat(), white(thick / 2))
        val p = Path().apply { moveTo(tx, y(s.points.last())) }
        s.ext.forEachIndexed { i, v -> p.lineTo(x(s.points.size + i), y(v)) }
        drawPath(p, white(thick).apply { pathEffect = DashPathEffect(floatArrayOf(thick * 1.5f, thick * 1.5f), 0f) })
    }
    return SparkLayers(base, points, ext)
}

/** A bar's fill, white for [level] (0 to 1) of its width and clear after, to be stretched over its track and tinted. */
fun barFill(level: Float): Bitmap {
    val w = 100
    val bm = Bitmap.createBitmap(w, 1, Bitmap.Config.ARGB_8888)
    val filled = Math.round(level.coerceIn(0f, 1f) * w)
    for (x in 0 until filled) bm.setPixel(x, 0, android.graphics.Color.WHITE)
    return bm
}

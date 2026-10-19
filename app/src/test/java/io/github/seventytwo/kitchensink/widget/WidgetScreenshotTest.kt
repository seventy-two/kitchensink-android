package io.github.seventytwo.kitchensink.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import io.github.seventytwo.kitchensink.data.Config
import io.github.seventytwo.kitchensink.data.ThemeChoice
import io.github.seventytwo.kitchensink.model.json
import io.github.seventytwo.kitchensink.model.parseSnapshot
import io.github.seventytwo.kitchensink.model.parseTime
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as Robo
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the widget from the sample at a few home screen sizes, through
 * the RemoteViews a launcher would get. The pictures land in
 * app/build/screenshots/ for a look; nothing compares them.
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Robo(sdk = [36], qualifiers = "w411dp-h891dp-port-xxhdpi")
class WidgetScreenshotTest {
    private val sample = json.parseToJsonElement(javaClass.getResource("/sample.json")!!.readText()).jsonObject
    private val snapshots = (sample["monitors"] as JsonArray).map(::parseSnapshot)
    private val now = parseTime("2026-10-12T15:00:00Z")
    private val configured = Config(url = "https://example.com/kitchensink", token = "x")

    private fun shot(name: String, config: Config = configured, w: Int = 380, h: Int = 420) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val data = widgetData(snapshots, config)
        val size = DpSize(w.dp, h.dp)
        val views = runBlocking { GlanceRemoteViews().compose(context, size) { WidgetContent(data, now, scroll = false) } }.remoteViews
        val parent = FrameLayout(context)
        val view = views.apply(context, parent)
        val px = context.resources.displayMetrics.density
        parent.addView(view, FrameLayout.LayoutParams((w * px).toInt(), (h * px).toInt()))
        parent.measure(View.MeasureSpec.makeMeasureSpec((w * px).toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec((h * px).toInt(), View.MeasureSpec.EXACTLY))
        parent.layout(0, 0, (w * px).toInt(), (h * px).toInt())
        val bitmap = Bitmap.createBitmap(parent.width, parent.height, Bitmap.Config.ARGB_8888)
        // A wallpaper-ish grey behind, so the corners show.
        Canvas(bitmap).apply { drawColor(Color.rgb(90, 96, 110)); parent.draw(this) }
        File("build/screenshots").mkdirs()
        File("build/screenshots/widget-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test @Robo(qualifiers = "+night")
    fun dark() = shot("dark")

    @Test @Robo(qualifiers = "+notnight")
    fun light() = shot("light")

    @Test
    fun retroNarrow() = shot("retro-narrow", configured.copy(theme = ThemeChoice.Retro), w = 200, h = 300)

    @Test
    fun notSetUp() = shot("not-set-up", Config(), h = 160)
}

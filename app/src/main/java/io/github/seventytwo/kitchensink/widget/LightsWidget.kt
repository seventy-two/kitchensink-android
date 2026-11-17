package io.github.seventytwo.kitchensink.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.seventytwo.kitchensink.KitchensinkApp
import io.github.seventytwo.kitchensink.MainActivity
import io.github.seventytwo.kitchensink.R
import io.github.seventytwo.kitchensink.data.ConfigStore
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * A home screen widget of just the lights, as the app's lights card: one
 * group a line, each room's own light at the head of its numbered ones,
 * with bulbs lit in the light's colour. Tapping a light switches it, as
 * tapping it in the app does. The app saves what it shows (see
 * [LightsStore]) whenever the dashboard changes.
 */
class LightsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { LightsStore.load(context) }
        provideContent { LightsContent(data) }
    }
}

class LightsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LightsWidget()
}

/** What the lights widget shows, kept as lights-widget.json in the app's private storage. */
object LightsStore {
    private fun file(context: Context) = File(context.filesDir, "lights-widget.json")

    fun load(context: Context): LightsData = try {
        LightsData.decode(file(context).readText())
    } catch (_: Exception) {
        LightsData()
    }

    /** Saves [data] and redraws the widgets, unless it's what they already show. */
    suspend fun show(context: Context, data: LightsData) {
        val text = data.encode()
        val changed = withContext(Dispatchers.IO) {
            val f = file(context)
            if (f.exists() && f.readText() == text) return@withContext false
            val tmp = File(f.path + ".tmp")
            tmp.writeText(text)
            tmp.renameTo(f)
        }
        if (changed) LightsWidget().updateAll(context)
    }
}

private val LightId = ActionParameters.Key<String>("light")

/**
 * A tap on a light: it's shown switched at once and the monitor sent the
 * same toggle the app sends. The server's next snapshot, when the app is
 * connected, puts the widget right if it didn't take.
 */
class ToggleLight : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[LightId] ?: return
        val data = LightsStore.load(context)
        if (data.monitor.isEmpty()) return
        LightsStore.show(context, data.toggled(id))
        withContext(Dispatchers.IO) {
            val c = ConfigStore(context).load()
            if (!c.configured) return@withContext
            val body = buildJsonObject { put("type", "toggle"); put("id", id) }.toString()
            val req = Request.Builder().url(c.url.trim().trimEnd('/') + "/api/monitors/${data.monitor}/actions")
                .header("Authorization", "Bearer ${c.token}")
                .post(body.toRequestBody("application/json".toMediaType())).build()
            try {
                (context.applicationContext as KitchensinkApp).http.newCall(req).execute().close()
            } catch (_: IOException) {
            } catch (_: IllegalArgumentException) {
            }
        }
    }
}

/** A light's bulb while it's on and has no colour of its own: a warm white, as in the app. */
private val WarmBulb = Color(0xFFFFD68C)

/** The lights widget. [scroll] is off only in tests, as Robolectric can't fill a RemoteViews list. */
@Composable
internal fun LightsContent(data: LightsData, scroll: Boolean = true) {
    val (day, night) = widgetPalettes(data.theme, data.black)
    val c = Colours(day, night)
    val open = actionStartActivity<MainActivity>()
    val pad = 8.dp
    val headerH = 26.dp
    Column(GlanceModifier.fillMaxSize().appWidgetBackground().background(c.bg).cornerRadius(16.dp).padding(pad)) {
        Row(GlanceModifier.fillMaxWidth().height(headerH).padding(horizontal = 4.dp).clickable(open), verticalAlignment = Alignment.CenterVertically) {
            Text("Lights", style = TextStyle(color = c.heading, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            Spacer(GlanceModifier.defaultWeight())
            if (data.summary.isNotEmpty()) Text(data.summary, style = TextStyle(color = c.muted, fontSize = 11.sp), maxLines = 1)
        }
        val message = when {
            !data.configured -> "Set up kitchensink in the app first."
            data.monitor.isEmpty() -> "No lights on the dashboard yet."
            else -> ""
        }
        if (message.isNotEmpty()) {
            Box(GlanceModifier.fillMaxSize().clickable(open), contentAlignment = Alignment.Center) {
                Text(message, style = TextStyle(color = c.muted, fontSize = 13.sp))
            }
            return@Column
        }
        // Lines share the height, between a comfortable tap and a big button
        // on a tablet; past that the list scrolls.
        val error = data.error.isNotEmpty()
        val room = LocalSize.current.height - pad * 2 - headerH - (if (error) 18.dp else 0.dp)
        val gap = 4.dp
        val n = maxOf(data.groups.size, 1)
        val lineH = ((room - gap * n) / n).coerceIn(34.dp, 64.dp)
        val big = lineH >= 52.dp
        if (scroll) {
            LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(data.groups, itemId = { it.first().id.hashCode().toLong() }) { g -> LightGroup(g, lineH, big, gap, c) }
            }
        } else {
            Column(GlanceModifier.fillMaxWidth().defaultWeight()) { for (g in data.groups.take(9)) LightGroup(g, lineH, big, gap, c) }
        }
        if (error) Text(data.error, style = TextStyle(color = c.warn, fontSize = 11.sp), maxLines = 1, modifier = GlanceModifier.padding(horizontal = 4.dp))
    }
}

/**
 * One group on a line: its buttons joined, the card showing through a
 * hairline between them. The head (a room, or a light on its own) takes
 * the spare width, and its numbered lights are narrow.
 */
@Composable
private fun LightGroup(g: List<LightButton>, lineH: Dp, big: Boolean, gap: Dp, c: Colours) {
    Row(GlanceModifier.fillMaxWidth().padding(bottom = gap)) {
        Row(GlanceModifier.fillMaxWidth().height(lineH).cornerRadius(6.dp).background(c.card)) {
            // A widget's row holds ten things at most.
            for ((i, b) in g.take(10).withIndex()) {
                val w = if (big) 64.dp else 48.dp
                LightButtonView(b, i > 0, big, c, if (i == 0) GlanceModifier.defaultWeight() else GlanceModifier.width(w))
            }
        }
    }
}

@Composable
private fun LightButtonView(b: LightButton, joined: Boolean, big: Boolean, c: Colours, modifier: GlanceModifier) {
    val bulb = when {
        !b.on -> c.of { it.muted.copy(alpha = 0.6f) }
        b.rgb != null -> ColorProvider(Color(0xff000000.toInt() or b.rgb))
        else -> ColorProvider(WarmBulb)
    }
    Box(modifier.padding(start = if (joined) 1.dp else 0.dp)) {
        Row(
            GlanceModifier.fillMaxSize().background(c.frame).padding(horizontal = if (joined) 8.dp else 10.dp)
                .clickable(actionRunCallback<ToggleLight>(actionParametersOf(LightId to b.id)))
                .semantics { contentDescription = b.title + if (b.on) ", on" else ", off" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(ImageProvider(R.drawable.ic_bulb), null, GlanceModifier.size(if (big) 20.dp else 16.dp), colorFilter = ColorFilter.tint(bulb))
            Spacer(GlanceModifier.width(if (joined) 3.dp else 6.dp))
            Text(b.label, style = TextStyle(color = if (b.on) c.text else c.muted, fontSize = if (big) 16.sp else 13.sp), maxLines = 1)
        }
    }
}

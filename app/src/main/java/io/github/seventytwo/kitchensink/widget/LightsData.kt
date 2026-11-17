// What the lights widget shows, worked out from the dashboard and kept in a
// file, so the widget can draw it while the app isn't running. Nothing here
// depends on Android.
package io.github.seventytwo.kitchensink.widget

import io.github.seventytwo.kitchensink.data.Config
import io.github.seventytwo.kitchensink.data.ThemeChoice
import io.github.seventytwo.kitchensink.model.Snapshot
import io.github.seventytwo.kitchensink.model.parseHexColour
import io.github.seventytwo.kitchensink.model.switchGroup
import io.github.seventytwo.kitchensink.model.switchesPanel
import io.github.seventytwo.kitchensink.ui.buttonGroups
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One light's button. */
@Serializable
data class LightButton(
    /** The item's ID, which a toggle is sent for. */
    val id: String = "",
    /** What the button says: a numbered light in a group just its number. */
    val label: String = "",
    /** Its full name, for accessibility. */
    val title: String = "",
    val on: Boolean = false,
    /** Its colour (0xrrggbb) while it's on and has one; a warm white otherwise. */
    val rgb: Int? = null,
)

@Serializable
data class LightsData(
    val configured: Boolean = false,
    /** Nothing saved yet: the app's been asked for the lights. */
    val loading: Boolean = false,
    /** The lights monitor, which toggles are sent to; empty if there isn't one. */
    val monitor: String = "",
    /** The lights in the app's groups: a room's own row heads its numbered lights, joined as one button. */
    val groups: List<List<LightButton>> = emptyList(),
    /** How many are on, as the monitor sums it up (e.g. "4 of 6 on"). */
    val summary: String = "",
    val error: String = "",
    val theme: ThemeChoice = ThemeChoice.System,
    val black: Boolean = false,
) {
    fun encode(): String = format.encodeToString(serializer(), this)

    /** This with light [id] switched over, shown at once ahead of the server's update. */
    fun toggled(id: String): LightsData = copy(groups = groups.map { g -> g.map { if (it.id == id) it.copy(on = !it.on) else it } })

    companion object {
        private val format = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** The data read back, or an empty widget if it can't be. */
        fun decode(text: String): LightsData = try {
            format.decodeFromString(serializer(), text)
        } catch (_: Exception) {
            LightsData()
        }
    }
}

/**
 * The lights as the app's lights card has them, from the monitor whose short view is a row of switch buttons.
 * [ticked] holds lights just switched ("monitor/id" to off), shown so until the server says otherwise, as in the app.
 */
fun lightsData(snapshots: List<Snapshot>, config: Config, ticked: Map<String, Boolean> = emptyMap()): LightsData {
    val base = LightsData(configured = config.configured, theme = config.theme, black = config.oled.black)
    val s = switchesPanel(snapshots) ?: return base
    val b = s.summaryBlock ?: return base
    return base.copy(
        monitor = s.monitor,
        groups = buttonGroups(b).map { g ->
            g.mapIndexed { i, it ->
                val on = !(ticked["${s.monitor}/${it.id}"] ?: it.done)
                LightButton(
                    id = it.id,
                    label = if (i > 0) it.title.substring(switchGroup(it.title).length + 1) else it.title,
                    title = it.title,
                    on = on,
                    rgb = if (on) it.colour?.value?.let(::parseHexColour) else null,
                )
            }
        },
        summary = s.summary.firstOrNull()?.value ?: "",
        error = s.error,
    )
}

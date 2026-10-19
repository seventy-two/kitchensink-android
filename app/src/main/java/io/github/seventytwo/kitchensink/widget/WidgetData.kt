// What the home screen widget shows, worked out from the dashboard and kept
// in a file, so the widget can draw it while the app isn't running.
// Nothing here depends on Android.
package io.github.seventytwo.kitchensink.widget

import io.github.seventytwo.kitchensink.data.Config
import io.github.seventytwo.kitchensink.data.ThemeChoice
import io.github.seventytwo.kitchensink.model.Snapshot
import io.github.seventytwo.kitchensink.model.Tone
import io.github.seventytwo.kitchensink.ui.orderedPanels
import kotlin.math.ceil
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WidgetStat(val label: String = "", val value: String = "", val detail: String = "", val tone: Tone = Tone.None)

/** One monitor's summary row. */
@Serializable
data class WidgetRow(
    val monitor: String = "",
    val title: String = "",
    val stats: List<WidgetStat> = emptyList(),
    /** Coloured chips, as the monitor asks; otherwise plain tiles. */
    val chips: Boolean = false,
    val error: String = "",
)

@Serializable
data class WidgetData(
    val configured: Boolean = false,
    val rows: List<WidgetRow> = emptyList(),
    /** When a monitor last checked (Unix seconds), so the widget can say how fresh it is. */
    val updated: Long = 0,
    val theme: ThemeChoice = ThemeChoice.System,
    /** OLED care's true black. */
    val black: Boolean = false,
) {
    fun encode(): String = format.encodeToString(serializer(), this)

    companion object {
        private val format = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** The data read back, or an empty widget if it can't be. */
        fun decode(text: String): WidgetData = try {
            format.decodeFromString(serializer(), text)
        } catch (_: Exception) {
            WidgetData()
        }
    }
}

/**
 * Each monitor's summary in the user's panel order, private figures left
 * out if they're hidden, as in the app's compact rows. Monitors that send
 * a summary block send a summary too, for screens like this one.
 */
fun widgetData(snapshots: List<Snapshot>, config: Config): WidgetData = WidgetData(
    configured = config.configured,
    rows = orderedPanels(snapshots, config).map { s ->
        WidgetRow(
            monitor = s.monitor,
            title = s.title.ifEmpty { s.monitor },
            stats = s.summary.map { WidgetStat(it.label, it.value, it.detail, it.tone) },
            chips = s.summaryStyle == "chips",
            error = s.error,
        )
    },
    updated = if (config.configured) snapshots.maxOfOrNull { it.updated } ?: 0 else 0,
    theme = config.theme,
    black = config.oled.black,
)

/**
 * How many stats go on each line of a row [width] wide (dp), each at least
 * [min] wide with [gap] between: as many as fit, then evened out so the
 * last line isn't left with one or two (five with room for four go three
 * and two).
 */
fun statsPerLine(count: Int, width: Float, min: Float, gap: Float): Int {
    if (count <= 0) return 1
    val fit = ((width + gap) / (min + gap)).toInt().coerceIn(1, count)
    val lines = ceil(count / fit.toFloat()).toInt()
    return ceil(count / lines.toFloat()).toInt()
}

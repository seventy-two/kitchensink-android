// What the home screen widget shows, worked out from the dashboard and kept
// in a file, so the widget can draw it while the app isn't running.
// Nothing here depends on Android.
package io.github.seventytwo.kitchensink.widget

import io.github.seventytwo.kitchensink.data.Config
import io.github.seventytwo.kitchensink.data.ThemeChoice
import io.github.seventytwo.kitchensink.model.Snapshot
import io.github.seventytwo.kitchensink.model.Tone
import io.github.seventytwo.kitchensink.model.isBar
import io.github.seventytwo.kitchensink.ui.orderedPanels
import kotlin.math.ceil
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A chip's price line, as [io.github.seventytwo.kitchensink.model.Spark]. */
@Serializable
data class WidgetSpark(
    val points: List<Float> = emptyList(),
    val ext: List<Float> = emptyList(),
    val base: Float? = null,
    val tone: Tone = Tone.None,
    val extTone: Tone = Tone.None,
    val faded: Boolean = false,
)

@Serializable
data class WidgetStat(
    val label: String = "",
    val value: String = "",
    val detail: String = "",
    val tone: Tone = Tone.None,
    /** Plain text on one line, set apart from the chips after it. */
    val lead: Boolean = false,
    /** A bar filled to [level]. */
    val bar: Boolean = false,
    val level: Float = 0f,
    /** A word drawn small after the value, e.g. "pre". */
    val tag: String = "",
    /** Replaces the value on a slim line. */
    val brief: String? = null,
    val spark: WidgetSpark? = null,
)

/** One monitor's summary row. */
@Serializable
data class WidgetRow(
    val monitor: String = "",
    val title: String = "",
    val stats: List<WidgetStat> = emptyList(),
    /** Coloured chips, as the monitor asks; otherwise plain tiles. */
    val chips: Boolean = false,
    val error: String = "",
    /** One slim line, as the app's ultra compact rows: no title, label and value pairs. */
    val slim: Boolean = false,
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
            stats = s.summary.map { st ->
                WidgetStat(
                    st.label, st.value, st.detail, st.tone,
                    lead = st.lead, bar = st.isBar, level = st.level ?: 0f, tag = st.tag, brief = st.brief,
                    spark = st.spark?.takeIf { st.hasSpark }?.let { WidgetSpark(it.points, it.ext, it.base, it.tone, it.extTone, it.faded) },
                )
            },
            chips = s.summaryStyle == "chips",
            error = s.error,
            slim = config.slimRow(s.monitor),
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

/**
 * How many stats fit on a slim line [width] dp wide, guessing their width
 * from their text (a widget can't measure): each label at about 6.5dp a
 * letter and value at about 9.5dp, at least 18dp, [gap] apart, with room kept for a "+n"
 * when some are left out.
 */
fun slimFit(stats: List<WidgetStat>, width: Float, gap: Float, extra: (WidgetStat) -> Float = { 0f }): Int {
    var x = 0f
    for ((i, s) in stats.withIndex()) {
        val w = slimWidth(s) + extra(s)
        val more = if (i + 1 < stats.size) 24f else 0f
        if (x + w + more > width) return i
        x += w + gap
    }
    return stats.size
}

/** A stat's guessed width on a slim line, in dp. */
fun slimWidth(s: WidgetStat): Float {
    val value = s.brief ?: s.value
    val between = if (s.label.isNotEmpty() && value.isNotEmpty()) 4f else 0f
    val w = s.label.length * 6.5f + between + value.length * 9.5f + 4f + if (s.tag.isNotEmpty()) s.tag.length * 5.5f + 4f else 0f
    return maxOf(w, 18f)
}

// The dashboard's data: snapshots and events as the server sends them.
// Nothing here depends on Android, so it's unit tested on the JVM. It
// follows the Windows client's model.cpp.
package io.github.seventytwo.kitchensink.model

import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.longOrNull

enum class Tone { None, Good, Bad, Warn, Muted, Accent, Rain }
enum class Align { Left, Right, Center }
enum class Priority { Low, Normal, High }

/**
 * A chip's small price line: [points] evenly spaced, then [ext] carrying on
 * dotted after a tick (pre-market or after hours). [base], when set, is a
 * level drawn faintly (the previous close); [faded] dims the points, whose
 * session is over.
 */
data class Spark(
    val points: List<Float> = emptyList(),
    val ext: List<Float> = emptyList(),
    val base: Float? = null,
    val tone: Tone = Tone.None,
    val extTone: Tone = Tone.None,
    val faded: Boolean = false,
)

data class Stat(
    val label: String = "",
    val value: String = "",
    val detail: String = "",
    val tone: Tone = Tone.None,
    val series: List<Float> = emptyList(),
    /** Fixes the sparkline's scale, e.g. 100 for a percentage; 0 fits it to the series. */
    val top: Float = 0f,
    /** A personal figure, e.g. money, hidden on request. */
    val isPrivate: Boolean = false,
    /** A picture by the value, e.g. "rain". */
    val icon: String = "",
    /** 0 to 1, a faint chart behind a chip, e.g. the chance of rain; null draws nothing. */
    val level: Float? = null,
    /** In chips: sets the chip apart from the ones after it, as plain text on one line (the portfolio's day). */
    val lead: Boolean = false,
    /** Opened when the stat is tapped. */
    val url: String = "",
    /** A word drawn small after the value, e.g. "pre". */
    val tag: String = "",
    /** A price line drawn in a chip, when it has two points or more. */
    val spark: Spark? = null,
    /** Drawn as a bar filled to [level], e.g. how much of a limit is used. */
    val bar: Boolean = false,
    /** Replaces the value on a slim line (the ultra compact layout) when set; empty leaves only the label. */
    val brief: String? = null,
) {
    /** Whether [spark] has enough to draw a line. */
    val hasSpark: Boolean get() = spark != null && spark.points.isNotEmpty() && spark.points.size + spark.ext.size >= 2
}

data class Column(val title: String = "", val align: Align = Align.Left, val fill: Boolean = false)

data class Cell(
    val text: String = "",
    val url: String = "",
    val tone: Tone = Tone.None,
    val detail: String = "",
    /** A button drawn in place of the text (a used-up or ignore cross). */
    val action: CellAction? = null,
)

/** A table cell's button: tapped, it sends the monitor an action of [type] for [id]; [label] says what it does. */
data class CellAction(val type: String = "", val id: String = "", val label: String = "")

/** A checklist item's 0-100 control (a light's brightness): let go at a new value, it sends [action] with the value. */
data class Slider(val value: Int = 0, val action: String = "", val hint: String = "")

/** A preset colour: [value] is sent when it's picked (e.g. "2700K"), [show] ("#rrggbb") is what it looks like. */
data class Swatch(val label: String = "", val value: String = "", val show: String = "")

/** A checklist item's colour swatch (a light's colour), opening a picker that sends [action] with "#rrggbb" or a preset's value. */
data class Colour(
    /** "#rrggbb" now, or empty (e.g. off). */
    val value: String = "",
    val action: String = "",
    /** Any colour, not only [presets]. */
    val wheel: Boolean = false,
    val presets: List<Swatch> = emptyList(),
)

data class Item(
    val title: String = "",
    val subtitle: String = "",
    val detail: String = "",
    val url: String = "",
    val image: String = "",
    val tone: Tone = Tone.None,
    // For checklists.
    val id: String = "",
    val done: Boolean = false,
    val slider: Slider? = null,
    val colour: Colour? = null,
)

/** One of a stats block's sets of stats, e.g. the watchlist's week. */
data class StatsView(val label: String = "", val stats: List<Stat> = emptyList())

/** One part of a monitor's panel. Only the fields for its type are set. */
data class Block(
    val type: String = "",
    val title: String = "",
    val text: String = "",
    /** A checklist takes new items when set. */
    val addHint: String = "",
    /** What a checklist's delete does, when it isn't deleting. */
    val deleteHint: String = "",
    /** A checklist without delete buttons. */
    val noDelete: Boolean = false,
    /** A checklist whose items have up and down buttons, and what they do when it isn't moving them in the list. */
    val movable: Boolean = false,
    val upHint: String = "",
    val downHint: String = "",
    /** A checklist drawn as on/off switches: on while an item isn't done. */
    val switches: Boolean = false,
    /** A checklist drawn as small buttons in a row, each opening its controls (a summary block). */
    val buttons: Boolean = false,
    val isPrivate: Boolean = false,
    val stats: List<Stat> = emptyList(),
    /** A stats block drawn as a grid of chips (the watchlist's charts). */
    val chips: Boolean = false,
    /** Other sets of a stats block's stats to switch between (Today, 1W, 1M); [stats] is the first's. */
    val views: List<StatsView> = emptyList(),
    val columns: List<Column> = emptyList(),
    val rows: List<List<Cell>> = emptyList(),
    val items: List<Item> = emptyList(),
)

data class Snapshot(
    val monitor: String = "",
    val kind: String = "",
    val title: String = "",
    val error: String = "",
    /** What it's watching, when that isn't obvious. */
    val subtitle: String = "",
    /**
     * The subtitle is for a closer look only (when Claude's usage was read):
     * Windows shows it on hover; here a row leaves it out.
     */
    val subtitleHover: Boolean = false,
    /** When it was updated matters at the moment. */
    val timely: Boolean = false,
    /** When the panel matters most (Unix seconds; from 0 for now, until exclusive, 0 for never); see [relevant]. */
    val relevantFrom: Long = 0,
    val relevantUntil: Long = 0,
    /** A change to its summary isn't news (Reddit's hot posts turning over), so only being relevant or an event lifts it. */
    val calm: Boolean = false,
    /** It only feeds other panels (the mail reader), so it's shown in the settings, not the dashboard. */
    val background: Boolean = false,
    /** Unix seconds; 0 if never polled. */
    val updated: Long = 0,
    val blocks: List<Block> = emptyList(),
    /** The few things most worth seeing at a glance, shown when space is short. */
    val summary: List<Stat> = emptyList(),
    /** "" for tiles, or "chips". */
    val summaryStyle: String = "",
    /** A cut-down block shown instead of [summary] when it's given, e.g. a table's top rows. */
    val summaryBlock: Block? = null,
)

data class Event(
    val id: Long = 0,
    val monitor: String = "",
    val key: String = "",
    val title: String = "",
    val body: String = "",
    val url: String = "",
    val image: String = "",
    /** Unix seconds. */
    val time: Long = 0,
    val priority: Priority = Priority.Normal,
    val quiet: Boolean = false,
    /** If set (Unix seconds), the event is temporary: it's dropped then. */
    val expires: Long = 0,
    /** If set (Unix seconds), when the event stops being relevant: it's in Recent until then, then dropped. */
    val until: Long = 0,
) {
    /** Whether it's past its [expires] or [until] at [now]. */
    fun over(now: Long) = (expires > 0 && expires <= now) || (until > 0 && until <= now)
}

internal val json = Json { ignoreUnknownKeys = true }

// Readers that tolerate a field being missing or another type, as the
// server's JSON is read by hand.
private fun JsonElement?.obj(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())
private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""
private fun JsonObject.flag(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false
private fun JsonObject.array(key: String): List<JsonElement> = this[key] as? JsonArray ?: emptyList()
private fun JsonObject.tone(): Tone = toneNamed(str("tone"))
private fun JsonObject.floats(key: String): List<Float> =
    array(key).mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> !p.isString }?.floatOrNull }
private fun toneNamed(t: String): Tone = when (t) {
    "good" -> Tone.Good
    "bad" -> Tone.Bad
    "warn" -> Tone.Warn
    "muted" -> Tone.Muted
    "accent" -> Tone.Accent
    "rain" -> Tone.Rain
    else -> Tone.None
}

private fun parseStat(e: JsonElement): Stat {
    val j = e.obj()
    return Stat(
        label = j.str("label"),
        value = j.str("value"),
        detail = j.str("detail"),
        tone = j.tone(),
        series = j.floats("series"),
        top = (j["top"] as? JsonPrimitive)?.floatOrNull ?: 0f,
        isPrivate = j.flag("private"),
        icon = j.str("icon"),
        level = (j["level"] as? JsonPrimitive)?.takeIf { !it.isString }?.floatOrNull?.coerceIn(0f, 1f),
        lead = j.flag("lead"),
        bar = j.flag("bar"),
        brief = (j["brief"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
        url = j.str("url"),
        tag = j.str("tag"),
        spark = (j["spark"] as? JsonObject)?.let { sp ->
            Spark(
                sp.floats("points"), sp.floats("ext"),
                (sp["base"] as? JsonPrimitive)?.takeIf { !it.isString }?.floatOrNull,
                toneNamed(sp.str("tone")), toneNamed(sp.str("extTone")), sp.flag("faded"),
            )
        },
    )
}

private fun parseBlock(e: JsonElement): Block {
    val j = e.obj()
    return Block(
        type = j.str("type"),
        title = j.str("title"),
        text = j.str("text"),
        addHint = j.str("addHint"),
        deleteHint = j.str("deleteHint"),
        noDelete = j.flag("noDelete"),
        movable = j.flag("movable"),
        upHint = j.str("upHint"),
        downHint = j.str("downHint"),
        switches = j.flag("switch"),
        buttons = j.flag("buttons"),
        isPrivate = j.flag("private"),
        stats = j.array("stats").map(::parseStat),
        chips = j.flag("chips"),
        views = j.array("views").map { v -> v.obj().let { StatsView(it.str("label"), it.array("stats").map(::parseStat)) } },
        columns = j.array("columns").map {
            val c = it.obj()
            val align = when (c.str("align")) {
                "right" -> Align.Right
                "center" -> Align.Center
                else -> Align.Left
            }
            Column(c.str("title"), align, c.flag("fill"))
        },
        rows = j.array("rows").map { r ->
            (r as? JsonArray ?: emptyList()).map {
                val c = it.obj()
                Cell(
                    c.str("text"), c.str("url"), c.tone(), c.str("detail"),
                    action = (c["action"] as? JsonObject)?.let { a -> CellAction(a.str("type"), a.str("id"), a.str("label")) }
                        ?.takeIf { a -> a.type.isNotEmpty() },
                )
            }
        },
        items = j.array("items").map {
            val i = it.obj()
            Item(
                i.str("title"), i.str("subtitle"), i.str("detail"), i.str("url"), i.str("image"), i.tone(), i.str("id"), i.flag("done"),
                slider = (i["slider"] as? JsonObject)?.let { s ->
                    val v = (s["value"] as? JsonPrimitive)?.takeIf { p -> !p.isString }?.floatOrNull ?: 0f
                    Slider(v.toInt().coerceIn(0, 100), s.str("action"), s.str("hint"))
                },
                colour = (i["colour"] as? JsonObject)?.let { c ->
                    Colour(
                        c.str("value"), c.str("action"), c.flag("wheel"),
                        c.array("presets").map { p -> p.obj().let { Swatch(it.str("label"), it.str("value"), it.str("show")) } },
                    )
                },
            )
        },
    )
}

fun parsePriority(s: String): Priority = when (s) {
    "low" -> Priority.Low
    "high" -> Priority.High
    else -> Priority.Normal
}

fun parseSnapshot(e: JsonElement): Snapshot {
    val j = e.obj()
    return Snapshot(
        monitor = j.str("monitor"),
        kind = j.str("kind"),
        title = j.str("title"),
        error = j.str("error"),
        subtitle = j.str("subtitle"),
        subtitleHover = j.flag("subtitleHover"),
        timely = j.flag("timely"),
        relevantFrom = (j["relevant"] as? JsonObject)?.let { maxOf(0, parseTime(it.str("from"))) } ?: 0,
        relevantUntil = (j["relevant"] as? JsonObject)?.let { maxOf(0, parseTime(it.str("until"))) } ?: 0,
        calm = j.flag("calm"),
        background = j.flag("background"),
        // Go sends its zero time, year 1, for a monitor that hasn't polled yet.
        updated = maxOf(0, parseTime(j.str("updated"))),
        blocks = j.array("blocks").map(::parseBlock),
        summary = j.array("summary").map(::parseStat),
        summaryStyle = j.str("summaryStyle"),
        summaryBlock = (j["summaryBlock"] as? JsonObject)?.let(::parseBlock),
    )
}

fun parseSnapshot(text: String): Snapshot = parseSnapshot(json.parseToJsonElement(text))

fun parseEvent(e: JsonElement): Event {
    val j = e.obj()
    return Event(
        id = (j["id"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull ?: 0,
        monitor = j.str("monitor"),
        key = j.str("key"),
        title = j.str("title"),
        body = j.str("body"),
        url = j.str("url"),
        image = j.str("image"),
        time = parseTime(j.str("time")),
        priority = parsePriority(j.str("priority")),
        quiet = j.flag("quiet"),
        expires = parseTime(j.str("expires")),
        until = parseTime(j.str("until")),
    )
}

fun parseEvent(text: String): Event = parseEvent(json.parseToJsonElement(text))

/** Parses an RFC 3339 time such as "2026-10-10T19:02:00.69+01:00" into Unix seconds, or 0 if it can't. */
fun parseTime(s: String): Long = try {
    OffsetDateTime.parse(s).toEpochSecond()
} catch (_: DateTimeParseException) {
    0
}

/** Describes how long before now a time was: "just now", "4m ago", "2h ago", "3d ago". */
fun ago(then: Long, now: Long): String {
    if (then == 0L) return "never"
    val s = now - then
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60}m ago"
        s < 86400 -> "${s / 3600}h ago"
        else -> "${s / 86400}d ago"
    }
}

/** Whether the server says a snapshot's panel matters most at [now] (a game on, an appointment soon). */
fun Snapshot.relevant(now: Long): Boolean = relevantUntil > now && relevantFrom <= now

/** Whether a monitor sends only a summary, no panel (Claude's limits): it stays a row and never opens. */
fun Snapshot.summaryOnly(): Boolean = blocks.isEmpty() && summary.isNotEmpty() && error.isEmpty()

/** Whether a stat is drawn as a bar. */
val Stat.isBar: Boolean get() = bar && level != null

/** Whether a snapshot has any private blocks or stats. */
fun Snapshot.hasPrivate(): Boolean =
    summary.any { it.isPrivate } || summaryBlock?.isPrivate == true || blocks.any { b -> b.isPrivate || b.stats.any { it.isPrivate } }

/** The snapshot with its private blocks and stats left out, for when the user has hidden them. */
fun Snapshot.withoutPrivate(): Snapshot = copy(
    summary = summary.filterNot { it.isPrivate },
    summaryBlock = summaryBlock?.takeUnless { it.isPrivate },
    blocks = blocks
        .map { it.copy(stats = it.stats.filterNot { s -> s.isPrivate }) }
        // A stats block left with nothing goes too.
        .filterNot { it.isPrivate || (it.type == "stats" && it.stats.isEmpty()) },
)

/** Reads "#rrggbb" as 0xrrggbb, or null if it isn't one. */
fun parseHexColour(s: String): Int? =
    if (s.length == 7 && s[0] == '#') s.substring(1).toIntOrNull(16) else null

/** Writes 0xrrggbb as "#rrggbb". */
fun hexColour(rgb: Int): String = "#%06x".format(rgb and 0xffffff)

/**
 * What a switch's name is a numbered one of: "Hallway" for "Hallway 2"; empty when it doesn't end in a number
 * after a space.
 */
fun switchGroup(title: String): String {
    var i = title.length
    while (i > 0 && title[i - 1].isDigit()) i--
    if (i == title.length || i < 2 || title[i - 1] != ' ') return ""
    return title.substring(0, i - 1)
}

/**
 * The monitor whose short view is a row of switch buttons (the lights), which sits in the Recent column as a
 * card of its own when there is one; null if there's none.
 */
fun switchesPanel(snapshots: List<Snapshot>): Snapshot? = snapshots.firstOrNull {
    val b = it.summaryBlock
    !it.background && b != null && b.type == "checklist" && b.buttons && b.switches && b.items.isNotEmpty()
}

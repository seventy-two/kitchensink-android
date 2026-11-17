package io.github.seventytwo.kitchensink.model

/** The natural sizes of one chip's parts, in pixels. Heights are 0 for a part that's missing. */
data class ChipMeasure(
    val labelW: Float,
    val labelH: Float,
    val valueW: Float,
    val valueH: Float,
    val detailW: Float = 0f,
    val detailH: Float = 0f,
    val icon: Boolean = false,
    /** A lead chip: its label and value as plain text on one line, as wide as they are. */
    val lead: Boolean = false,
    /** Whether it has a price line. */
    val spark: Boolean = false,
    /** Its tag's width, with the room before it; 0 for none. */
    val tagW: Float = 0f,
)

data class ChipLayout(
    /** Each chip's width. */
    val widths: List<Float>,
    /** Each chip's top-left, from the layout's. */
    val x: List<Float>,
    val y: List<Float>,
    /** Whether each chip starts a row. */
    val rowStart: List<Boolean>,
    /** Every chip's height. */
    val height: Float,
    val labelH: Float,
    val valueH: Float,
    val iconSize: Float,
    /** The whole layout's height. */
    val total: Float,
    /** Chips with price lines: the label and value on one line, the line under them. */
    val sparks: Boolean = false,
)

/** The room a chip layout leaves around and between its parts, in pixels. */
data class ChipSpacing(
    val padX: Float,
    val padY: Float,
    val gap: Float,
    /** The narrowest chips may go to share a line. */
    val minShare: Float,
    /** The extra room after a lead chip. */
    val leadGap: Float = 0f,
    /** A price line's height, and the room above it. */
    val sparkH: Float = 0f,
    val sparkGap: Float = 0f,
)

/**
 * Lays out stats as small blocks, all one size so they line up: the label
 * above the value and any detail below; or, when they have price lines,
 * the label and value on one line with the line under them. If they nearly
 * fit on one line, they narrow to do so, shortening details that are too
 * wide, though a chip whose label or value wouldn't fit keeps its own
 * width; if labels or values would still be cut short, they flow onto more
 * lines. A lead chip is as wide as its label and value, with more room
 * after it. A port of the Windows client's App::chips.
 */
fun layoutChips(ms: List<ChipMeasure>, width: Float, sp: ChipSpacing): ChipLayout {
    val n = ms.size
    if (n == 0) return ChipLayout(emptyList(), emptyList(), emptyList(), emptyList(), 0f, 0f, 0f, 0f, 0f)
    val padX = sp.padX
    val gap = sp.gap
    val sparks = ms.any { !it.lead && it.spark }
    // One size for all, from the widest and tallest of each part. need is
    // what each chip's label and value take: a chip that can't fit them in
    // the shared width gets its own.
    var w = 0f
    var labelH = 0f
    var valueH = 0f
    var detailH = 0f
    var beside = 0f // the widest label or value with an icon by it
    var icons = false
    val need = FloatArray(n)
    val own = FloatArray(n) // a lead chip's width, else 0
    var fixed = 0f // the lead chips' together
    var leads = 0
    for ((i, m) in ms.withIndex()) {
        if (m.lead) {
            labelH = maxOf(labelH, m.labelH)
            valueH = maxOf(valueH, m.valueH)
            own[i] = minOf(m.labelW + gap * 1.5f + m.valueW, width)
            fixed += own[i]
            leads++
            continue
        }
        w = maxOf(w, m.labelW, m.detailW, m.valueW)
        need[i] = maxOf(m.labelW, m.valueW)
        labelH = maxOf(labelH, m.labelH)
        detailH = maxOf(detailH, m.detailH)
        valueH = maxOf(valueH, m.valueH)
        if (sparks) {
            val line = m.labelW + gap * 2 + m.valueW + m.tagW
            w = maxOf(w, line)
            need[i] = maxOf(need[i], line)
        }
        // An icon, if any, goes right of the label and value, as tall as both
        // if there's room, as tall as the value at least.
        if (m.icon) {
            icons = true
            beside = maxOf(beside, m.valueW, m.labelW)
            val withIcon = m.valueW + gap + m.valueH * 1.2f
            w = maxOf(w, withIcon)
            need[i] = maxOf(need[i], withIcon)
        }
    }
    if (icons) w = maxOf(w, beside + gap + labelH + valueH)
    w += padX * 2
    // The room after each chip.
    val after = FloatArray(n) { if (ms[it].lead && it + 1 < n) gap + sp.leadGap else gap }
    var gaps = 0f
    for (i in 0 until n - 1) gaps += after[i]
    for (i in 0 until n) need[i] = if (own[i] > 0) own[i] else minOf(need[i] + padX * 2, width)
    val shared = n - leads
    val oneRow = if (shared > 0) (width - gaps - fixed) / shared else width
    val ws = MutableList(n) { if (own[it] > 0) own[it] else minOf(w, width) }
    val natural = gaps + ws.sum()
    if (natural > width + 0.5f && shared > 0 && oneRow >= sp.minShare) {
        // All on one row: the chips that need more than an equal share take
        // it, and the rest share what's left.
        var share = oneRow
        for (pass in 0 until n) {
            var taken = 0f
            var wide = 0
            for (i in 0 until n) if (own[i] == 0f && need[i] > share) { taken += need[i]; wide++ }
            if (wide == shared) break
            val next = (width - gaps - fixed - taken) / (shared - wide)
            if (next == share) break
            share = next
        }
        // If that still leaves a label or value cut short, they flow onto
        // more lines at their full width instead.
        var total = gaps + fixed
        for (i in 0 until n) if (own[i] == 0f) total += maxOf(share, need[i])
        if (share >= sp.minShare && total <= width + 0.5f) {
            w = share
            for (i in 0 until n) if (own[i] == 0f) ws[i] = maxOf(share, need[i])
        }
    }
    val h = if (sparks) sp.padY * 2 + valueH + sp.sparkGap + sp.sparkH + detailH else sp.padY * 2 + labelH + valueH + detailH
    val iconSize = (w - padX * 2 - beside - gap).coerceIn(valueH * 1.2f, maxOf(valueH * 1.2f, labelH + valueH))

    val xs = MutableList(n) { 0f }
    val ys = MutableList(n) { 0f }
    val starts = MutableList(n) { false }
    var x = 0f
    var y = 0f
    for (i in 0 until n) {
        if (x > 0 && x + ws[i] > width + 0.5f) {
            x = 0f
            y += h + gap
        }
        starts[i] = x == 0f
        xs[i] = x
        ys[i] = y
        x += ws[i] + after[i]
    }
    return ChipLayout(ws, xs, ys, starts, h, labelH, valueH, Math.round(iconSize).toFloat(), y + h, sparks)
}

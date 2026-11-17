// Ordering panels by what's new through the day, as the Windows client's
// model.cpp does: what had an event lately or matters now rises, and what's
// had nothing new for a long while sinks, fades and doesn't open by itself.
package io.github.seventytwo.kitchensink.model

import kotlinx.serialization.Serializable

/**
 * When a monitor last had something new: its last event (not a quiet one)
 * and the last time its summary changed, with the summary's [fingerprint]
 * to spot that. Times are Unix seconds.
 */
@Serializable
data class News(val fingerprint: Long = 0, val changed: Long = 0, val event: Long = 0)

/** How relevant a panel is now; see [panelTier]. */
enum class Tier { Top, Middle, Stale }

/** How long an event keeps a panel at the top. */
const val TopFor = 2 * 3600L

/** How long without anything new before a panel is stale. */
const val StaleAfter = 12 * 3600L

// FNV-1a, the same as the Windows client's, so a fingerprint means the same
// on every run (they're kept on disk).
private const val FnvPrime = 1099511628211L

private fun mix(h: Long, s: String): Long {
    var x = h
    for (b in s.toByteArray()) x = (x xor (b.toLong() and 0xff)) * FnvPrime
    return (x xor 0x1f) * FnvPrime // a separator, so "ab","c" isn't "a","bc"
}

/**
 * Sums up what a panel's summary row shows, so a change can be spotted. A
 * short table's rows count as a set, so hot posts trading places isn't news
 * but a new one is. 0 means nothing to go on.
 */
fun summaryFingerprint(s: Snapshot): Long {
    val b = s.summaryBlock
    if (s.summary.isEmpty() && b == null) return 0
    var h = -3750763034362895579L // FNV's offset basis, 14695981039346656037
    for (st in s.summary) {
        h = mix(h, st.label)
        h = mix(h, st.value)
        h = mix(h, st.detail)
    }
    if (b != null) {
        val rows = b.rows.map { r -> r.joinToString("") { it.text + '\u001f' + it.detail + '\u001e' } } +
            b.items.map { it.title + '\u001f' + (if (it.done) "1" else "0") + (it.colour?.value ?: "") } +
            b.stats.map { it.label + '\u001f' + it.value }
        for (r in rows.sorted()) h = mix(h, r)
    }
    return if (h == 0L) 1 else h
}

/**
 * How relevant a panel is at [now]. Top: the server says it matters now or
 * it had an event in the last [TopFor]. Stale: neither an event nor a
 * change to its summary for [StaleAfter]; for a calm panel, whose summary
 * changing isn't news, no event for that long. The rest are Middle.
 */
fun panelTier(s: Snapshot, n: News, now: Long): Tier {
    if (s.relevant(now)) return Tier.Top
    if (n.event > 0 && now - n.event < TopFor) return Tier.Top
    if (s.calm) return if (n.event > 0 && now - n.event < StaleAfter) Tier.Middle else Tier.Stale
    val last = maxOf(n.event, n.changed)
    if (last > 0 && now - last >= StaleAfter) return Tier.Stale
    return Tier.Middle
}

/** Orders items by their tiers, top first and stale last, keeping their order within each tier. */
fun <T> byTier(items: List<T>, tier: (T) -> Tier): List<T> = items.sortedBy { tier(it).ordinal }

/**
 * [news] after a snapshot arrives at [now]: when its summary changes, or
 * is seen for the first time, that counts as new now.
 */
fun noteSummary(news: Map<String, News>, s: Snapshot, now: Long): Map<String, News> {
    if (s.updated == 0L) return news
    val fp = summaryFingerprint(s)
    if (fp == 0L) return news
    val n = news[s.monitor] ?: News()
    if (n.fingerprint == fp) return news
    return news + (s.monitor to n.copy(fingerprint = fp, changed = now))
}

/** [news] after an event: its time counts, cleared or not, history or not, unless it's quiet. */
fun noteEvent(news: Map<String, News>, e: Event): Map<String, News> {
    if (e.quiet || e.monitor.isEmpty() || e.time <= 0) return news
    val n = news[e.monitor] ?: News()
    if (e.time <= n.event) return news
    return news + (e.monitor to n.copy(event = e.time))
}

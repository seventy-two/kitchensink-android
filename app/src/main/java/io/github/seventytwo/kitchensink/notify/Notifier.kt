package io.github.seventytwo.kitchensink.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.seventytwo.kitchensink.MainActivity
import io.github.seventytwo.kitchensink.R
import io.github.seventytwo.kitchensink.model.Event
import io.github.seventytwo.kitchensink.model.Priority

/**
 * Raises a notification for each new event. They're held briefly so a
 * burst, e.g. after being offline, becomes one notification. Each has its
 * own tag, so a later one never replaces one the user hasn't seen yet
 * (a new car at 7am was once wiped out by the 9am chores reminder).
 */
class Notifier(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)
    private val handler = Handler(Looper.getMainLooper())
    private val pending = mutableListOf<Event>()
    private var titleOf: (String) -> String = { it }

    init {
        createChannels(context)
    }

    /** Queues an event; what's queued is shown 1.5 seconds after the first. */
    fun hold(e: Event, titleOf: (String) -> String) {
        this.titleOf = titleOf
        if (pending.isEmpty()) handler.postDelayed(::flush, HOLD_MS)
        pending += e
    }

    /** Removes the event notifications, once the dashboard's in front of the user. */
    fun clearEvents() {
        for (n in manager.activeNotifications) {
            if (n.id == EVENT_ID) manager.cancel(n.tag, n.id)
        }
    }

    /** Removes a sign-in code's notification (its code has expired). */
    fun cancelCode(e: Event) {
        manager.cancel(tagOf(listOf(e)), EVENT_ID)
    }

    private fun flush() {
        if (pending.isEmpty()) return
        val all = pending.toList()
        pending.clear()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            Build.VERSION.SDK_INT >= 33
        ) return
        // Sign-in codes are never folded into a burst: each gets its own.
        val (codes, events) = all.partition { isCode(it) }
        codes.forEach(::notifyCode)
        if (events.isEmpty()) return

        val high = events.any { it.priority == Priority.High }
        val b = NotificationCompat.Builder(context, if (high) CHANNEL_HIGH else CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
        if (events.size == 1) {
            val e = events[0]
            val body = e.body.ifEmpty { titleOf(e.monitor) }
            b.setContentTitle(e.title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body))
            b.setContentIntent(open(e.url))
        } else {
            // Several at once: one summary, newest first.
            val titles = events.reversed().map { it.title }
            val style = NotificationCompat.InboxStyle()
            titles.forEach { style.addLine(it) }
            b.setContentTitle("${events.size} new events").setContentText(titles.first()).setStyle(style)
            b.setContentIntent(open(""))
        }
        try {
            manager.notify(tagOf(events), EVENT_ID, b.build())
        } catch (_: SecurityException) {
        }
    }

    /**
     * A sign-in code from the codes monitor: the code shows only once the
     * phone's unlocked (the lock screen says just who it's from), a Copy
     * button puts it on the clipboard, and it goes when the code expires.
     */
    private fun notifyCode(e: Event) {
        val tag = tagOf(listOf(e))
        val copy = Intent(context, CopyCodeReceiver::class.java)
            .putExtra(CopyCodeReceiver.EXTRA_CODE, e.body)
            .putExtra(CopyCodeReceiver.EXTRA_TAG, tag)
        val copyIntent = PendingIntent.getBroadcast(context, e.id.toInt(), copy, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val public = NotificationCompat.Builder(context, CHANNEL_HIGH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(e.title)
            .setContentText("Unlock to see the code")
            .build()
        val b = NotificationCompat.Builder(context, CHANNEL_HIGH)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentTitle(e.title)
            .setContentText(e.body)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setAutoCancel(true)
            .setContentIntent(open(""))
            .addAction(0, "Copy", copyIntent)
        val left = e.expires * 1000 - System.currentTimeMillis()
        if (e.expires > 0) {
            if (left <= 0) return
            b.setTimeoutAfter(left)
        }
        try {
            manager.notify(tag, EVENT_ID, b.build())
        } catch (_: SecurityException) {
        }
    }

    /** Opens an event's link, or the dashboard if it has none. */
    private fun open(url: String): PendingIntent {
        val intent = if (url.startsWith("https://") || url.startsWith("http://")) {
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        } else {
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return PendingIntent.getActivity(context, url.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        /** isCode says an event is a sign-in code from the codes monitor. */
        fun isCode(e: Event): Boolean = e.key.startsWith("code:")

        /** tagOf names a notification after its events, one or a burst. */
        fun tagOf(events: List<Event>): String =
            if (events.size == 1) "event:${events[0].id}" else "burst:${events[0].id}"

        const val CHANNEL_EVENTS = "events"
        const val CHANNEL_HIGH = "high"
        const val CHANNEL_LIVE = "live"
        const val EVENT_ID = 1
        const val LIVE_ID = 2
        private const val HOLD_MS = 1500L

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannels(
                listOf(
                    NotificationChannel(CHANNEL_EVENTS, "Events", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "What your monitors notice: scores, plays, price moves, reminders"
                    },
                    NotificationChannel(CHANNEL_HIGH, "High-priority events", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Events the server marks high priority"
                    },
                    NotificationChannel(CHANNEL_LIVE, "Staying connected", NotificationManager.IMPORTANCE_MIN).apply {
                        description = "Shown while kitchensink stays connected in the background"
                        setShowBadge(false)
                    },
                ),
            )
        }
    }
}

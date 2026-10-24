package io.github.seventytwo.kitchensink.notify

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import io.github.seventytwo.kitchensink.model.Event
import io.github.seventytwo.kitchensink.model.Priority
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class NotifierTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val nm = app.getSystemService(NotificationManager::class.java)

    private fun post(n: Notifier, vararg events: Event) {
        events.forEach { n.hold(it) { m -> m } }
        ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS)
    }

    @Test
    fun laterEventsDontReplaceEarlierOnes() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val n = Notifier(app)
        post(n, Event(id = 390, monitor = "autotrader", title = "New Mazda3", priority = Priority.High))
        post(n, Event(id = 391, monitor = "chores", title = "Chores today"))
        val active = nm.activeNotifications.sortedBy { it.tag }
        assertEquals(listOf("event:390", "event:391"), active.map { it.tag })
        assertEquals(listOf(Notifier.CHANNEL_HIGH, Notifier.CHANNEL_EVENTS), active.map { it.notification.channelId })

        // A burst is one notification, beside the others.
        post(n, Event(id = 392, title = "a"), Event(id = 393, title = "b"))
        assertEquals(3, nm.activeNotifications.size)

        n.clearEvents()
        assertEquals(0, nm.activeNotifications.size)
    }
}

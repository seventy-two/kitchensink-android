package io.github.seventytwo.kitchensink

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.seventytwo.kitchensink.data.Config
import io.github.seventytwo.kitchensink.data.ConfigStore
import io.github.seventytwo.kitchensink.model.Dashboard
import io.github.seventytwo.kitchensink.model.Event
import io.github.seventytwo.kitchensink.model.News
import io.github.seventytwo.kitchensink.model.noteEvent
import io.github.seventytwo.kitchensink.model.noteSummary
import io.github.seventytwo.kitchensink.model.Snapshot
import io.github.seventytwo.kitchensink.net.ConnState
import io.github.seventytwo.kitchensink.net.Inbound
import io.github.seventytwo.kitchensink.net.StreamClient
import io.github.seventytwo.kitchensink.notify.Notifier
import io.github.seventytwo.kitchensink.service.LiveService
import io.github.seventytwo.kitchensink.widget.LightsStore
import io.github.seventytwo.kitchensink.widget.WidgetStore
import io.github.seventytwo.kitchensink.widget.lightsData
import io.github.seventytwo.kitchensink.widget.widgetData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The app's state: settings, the stream and the dashboard built from it.
 * There's one for the process, shared by the activity, the background
 * service and the notifications. Everything here runs on the main thread.
 */
class Hub(private val context: Context, private val store: ConfigStore, private val http: OkHttpClient) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val inbox = Channel<Inbound>(Channel.UNLIMITED)
    private var dashboard = Dashboard()
    private val notifier = Notifier(context)

    val client = StreamClient(http, scope) { inbox.trySend(it) }
    val connState: StateFlow<ConnState> = client.state
    val connError: StateFlow<String> = client.error

    private val _config = MutableStateFlow(store.load())
    val config: StateFlow<Config> = _config

    private val _snapshots = MutableStateFlow<List<Snapshot>>(emptyList())
    val snapshots: StateFlow<List<Snapshot>> = _snapshots
    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events

    /** Monitors being refreshed by hand, with when they were asked (elapsed milliseconds). */
    private val _refreshing = MutableStateFlow<Map<String, Long>>(emptyMap())
    val refreshing: StateFlow<Map<String, Long>> = _refreshing

    /** Checklist ticks shown before the server confirms them, by "monitor/item"; a snapshot replaces them. */
    private val _ticked = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val ticked: StateFlow<Map<String, Boolean>> = _ticked

    /** When each monitor last had something new, for ordering panels by relevance. */
    private val _news = MutableStateFlow(store.loadNews())
    val news: StateFlow<Map<String, News>> = _news
    private var newsDirty = false
    private var newsSaved = 0L

    /** Until when something new keeps an idle screen from dimming (Unix seconds). */
    private val _wakeUntil = MutableStateFlow(0L)
    val wakeUntil: StateFlow<Long> = _wakeUntil

    private var foreground = false
    private var stopLater: Job? = null
    private var widgetLater: Job? = null

    init {
        dashboard.restoreCleared(_config.value.cleared)
        // A light switched, or its switch over, shows in the widget at once.
        scope.launch { _ticked.collect { refreshLights() } }
        scope.launch {
            for (m in inbox) {
                handle(m)
                // Publish once a burst has been taken in.
                if (inbox.isEmpty) publish()
            }
        }
        // Events drop out of Recent once past their until; look each minute.
        scope.launch {
            while (true) {
                delay(60_000)
                if (dashboard.dropOver(System.currentTimeMillis() / 1000)) publish()
                // What's new is saved now and then, not on every change (the
                // watchlist's figures change every minute).
                if (newsDirty && SystemClock.elapsedRealtime() - newsSaved > 5 * 60_000) saveNews()
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                foreground = true
                stopLater?.cancel()
                notifier.clearEvents()
                ensureRunning()
            }

            override fun onStop(owner: LifecycleOwner) {
                foreground = false
                if (newsDirty) saveNews()
                // Without the background service, let go of the connection
                // a little after leaving, so a quick switch away and back
                // doesn't reconnect.
                if (!_config.value.background) stopLater = scope.launch {
                    delay(15_000)
                    client.stop()
                }
            }
        })
    }

    /** Starts the stream, and the background service if it's wanted, unless they're running. */
    fun ensureRunning() {
        val c = _config.value
        ensureStream()
        if (c.background && c.configured) LiveService.start(context) else LiveService.stop(context)
    }

    /** Starts the stream unless it's running. */
    fun ensureStream() {
        val c = _config.value
        if (!client.running && c.configured) client.start(c.url, c.token)
    }

    private fun handle(m: Inbound) {
        when (m) {
            Inbound.SyncStart -> dashboard.beginSync()
            Inbound.Ready -> dashboard.endSync()
            is Inbound.Snap -> {
                _refreshing.value -= m.snapshot.monitor
                val prefix = m.snapshot.monitor + "/"
                if (_ticked.value.keys.any { it.startsWith(prefix) }) _ticked.value = _ticked.value.filterKeys { !it.startsWith(prefix) }
                noteNews(noteSummary(_news.value, m.snapshot, System.currentTimeMillis() / 1000))
                dashboard.applySnapshot(m.snapshot)
            }
            is Inbound.Ev -> {
                val e = m.event
                // A sign-in code's event rewritten without it: the code's expired.
                if (Notifier.isCode(e) && e.body.isEmpty()) notifier.cancelCode(e)
                noteNews(noteEvent(_news.value, e))
                if (!dashboard.addEvent(e)) return
                // History from before the app started has already been seen
                // (or missed for good); events missed while offline are new.
                if (m.history || e.quiet) return
                _wakeUntil.value = System.currentTimeMillis() / 1000 + 2 * 60
                val c = _config.value
                if (!foreground && c.notify && e.priority >= c.minPriority) notifier.hold(e) { dashboard.titleOf(it) }
            }
        }
    }

    private fun noteNews(n: Map<String, News>) {
        if (n === _news.value) return
        _news.value = n
        newsDirty = true
    }

    private fun saveNews() {
        store.saveNews(_news.value)
        newsDirty = false
        newsSaved = SystemClock.elapsedRealtime()
    }

    private fun publish() {
        _snapshots.value = dashboard.snapshotList
        _events.value = dashboard.eventList
        refreshWidget()
    }

    /**
     * Saves what the home screen widget shows, a moment after the last
     * change so a burst redraws it once. Until the stream has sent
     * something, it keeps showing what it had.
     */
    private fun refreshWidget() {
        widgetLater?.cancel()
        widgetLater = scope.launch {
            delay(2_000)
            val c = _config.value
            if (c.configured && _snapshots.value.isEmpty()) return@launch
            WidgetStore.show(context, widgetData(_snapshots.value, c))
        }
        refreshLights()
    }

    /**
     * Saves what the lights widget shows straight away (it's small), with
     * lights just switched shown so, so a snapshot of something else can't
     * put a bulb back while the switch is on its way.
     */
    private fun refreshLights() {
        val c = _config.value
        if (c.configured && _snapshots.value.isEmpty()) return
        val data = lightsData(_snapshots.value, c, _ticked.value)
        scope.launch { LightsStore.show(context, data) }
    }

    /**
     * A light switched from the widget, which has already drawn it so: sent
     * over the stream's client if it's running, straight to the server if
     * not, and kept as switched until the server's update comes.
     */
    fun toggleFromWidget(monitor: String, id: String, off: Boolean) {
        if (client.running) {
            toggle(monitor, id, off)
            return
        }
        _ticked.value += "$monitor/$id" to off
        scope.launch {
            delay(10_000)
            _ticked.value -= "$monitor/$id"
        }
        val c = _config.value
        if (!c.configured) return
        val body = buildJsonObject { put("type", "toggle"); put("id", id) }.toString()
        val req = Request.Builder().url(c.url.trim().trimEnd('/') + "/api/monitors/$monitor/actions")
            .header("Authorization", "Bearer ${c.token}")
            .post(body.toRequestBody("application/json".toMediaType())).build()
        scope.launch(Dispatchers.IO) {
            try {
                http.newCall(req).execute().close()
            } catch (_: IOException) {
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    /**
     * For a widget with nothing saved yet (just added, or the app updated
     * and not opened since): saves what the dashboard has, connecting first
     * if need be, and lets go again a minute later if the app isn't open and
     * isn't kept connected.
     */
    fun fillWidgets() {
        val c = _config.value
        if (!c.configured) return
        if (_snapshots.value.isNotEmpty()) {
            refreshWidget()
            return
        }
        ensureStream()
        if (!foreground && !c.background) {
            stopLater?.cancel()
            stopLater = scope.launch {
                delay(60_000)
                if (!foreground) client.stop()
            }
        }
    }

    fun update(f: (Config) -> Config) {
        val old = _config.value
        var c = f(old)
        if (c == old) return
        val reconnect = c.url != old.url || c.token != old.token
        // Another server's events have other IDs, so what was cleared goes.
        if (c.url != old.url) c = c.withCleared(io.github.seventytwo.kitchensink.model.Cleared())
        _config.value = c
        store.save(c)
        if (reconnect) {
            client.stop()
            dashboard = Dashboard().apply { restoreCleared(c.cleared) }
            publish()
        }
        // The theme, order and private figures show in the widget too.
        refreshWidget()
        ensureRunning()
    }

    fun refresh(monitor: String) {
        _refreshing.value += monitor to SystemClock.elapsedRealtime()
        client.refresh(monitor)
        // Clears itself if no snapshot comes, so the button comes back.
        scope.launch {
            delay(15_000)
            _refreshing.value -= monitor
        }
    }

    fun toggle(monitor: String, id: String, done: Boolean) {
        _ticked.value += "$monitor/$id" to done
        client.act(monitor, buildJsonObject { put("type", "toggle"); put("id", id) }.toString())
        scope.launch {
            delay(10_000)
            _ticked.value -= "$monitor/$id"
        }
    }

    fun delete(monitor: String, id: String) =
        client.act(monitor, buildJsonObject { put("type", "delete"); put("id", id) }.toString())

    fun move(monitor: String, id: String, up: Boolean) =
        client.act(monitor, buildJsonObject { put("type", "move"); put("id", id); put("text", if (up) "up" else "down") }.toString())

    fun send(monitor: String, type: String, id: String, text: String, done: Boolean?) {
        client.act(monitor, buildJsonObject { put("type", type); put("id", id); put("text", text) }.toString())
        if (done == null) return
        _ticked.value += "$monitor/$id" to done
        scope.launch {
            delay(10_000)
            _ticked.value -= "$monitor/$id"
        }
    }

    fun add(monitor: String, text: String) {
        if (text.isBlank()) return
        client.act(monitor, buildJsonObject { put("type", "add"); put("text", text.trim()) }.toString())
    }

    fun clearEvent(id: Long) {
        dashboard.clear(id)
        update { it.withCleared(dashboard.cleared) }
        publish()
    }

    fun clearAll() {
        dashboard.clearAll()
        update { it.withCleared(dashboard.cleared) }
        publish()
    }

    fun titleOf(monitor: String) = dashboard.titleOf(monitor)
}

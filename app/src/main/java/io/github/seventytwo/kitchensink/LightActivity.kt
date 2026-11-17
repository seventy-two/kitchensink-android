package io.github.seventytwo.kitchensink

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.seventytwo.kitchensink.data.ThemeChoice
import io.github.seventytwo.kitchensink.model.switchesPanel
import io.github.seventytwo.kitchensink.ui.Actions
import io.github.seventytwo.kitchensink.ui.KitchensinkTheme
import io.github.seventytwo.kitchensink.ui.LocalActions
import io.github.seventytwo.kitchensink.ui.LocalPalette
import io.github.seventytwo.kitchensink.ui.LocalPanelState
import io.github.seventytwo.kitchensink.ui.Metrics
import io.github.seventytwo.kitchensink.ui.Palette
import io.github.seventytwo.kitchensink.ui.PanelState
import io.github.seventytwo.kitchensink.ui.SwitchTile
import io.github.seventytwo.kitchensink.ui.openUrl

/**
 * One light's controls (brightness and colour, as in the app's dialog) over
 * whatever's on screen, for the lights widget's bulbs: a widget can't take
 * a long press, so the bulb opens these and the rest of the button
 * switches the light.
 */
class LightActivity : ComponentActivity() {
    private val hub get() = (application as KitchensinkApp).hub

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_ID) ?: return finish()
        setContent { Controls(id) }
    }

    @Composable
    private fun Controls(id: String) {
        val config by hub.config.collectAsStateWithLifecycle()
        val snapshots by hub.snapshots.collectAsStateWithLifecycle()
        val ticked by hub.ticked.collectAsStateWithLifecycle()
        val refreshing by hub.refreshing.collectAsStateWithLifecycle()
        val drafts = remember { mutableStateMapOf<String, String>() }
        val context = LocalContext.current
        LaunchedEffect(Unit) { hub.ensureStream() }
        val dark = when (config.theme) {
            ThemeChoice.System -> isSystemInDarkTheme()
            ThemeChoice.Light -> false
            ThemeChoice.Dark, ThemeChoice.Retro -> true
        }
        val palette = when {
            config.theme == ThemeChoice.Retro -> Palette.Retro
            config.oled.black -> Palette.Oled
            dark -> Palette.Dark
            else -> Palette.Light
        }
        val actions = remember {
            object : Actions {
                override fun open(url: String) = openUrl(context, url)
                override fun refresh(monitor: String) = hub.refresh(monitor)
                override fun toggle(monitor: String, id: String, done: Boolean) = hub.toggle(monitor, id, done)
                override fun delete(monitor: String, id: String) = hub.delete(monitor, id)
                override fun move(monitor: String, id: String, up: Boolean) = hub.move(monitor, id, up)
                override fun add(monitor: String, text: String) = hub.add(monitor, text)
                override fun send(monitor: String, type: String, id: String, text: String, done: Boolean?) =
                    hub.send(monitor, type, id, text, done)
            }
        }
        val s = switchesPanel(snapshots)
        val item = s?.summaryBlock?.items?.firstOrNull { it.id == id }
        KitchensinkTheme(palette, Metrics.of(compact = true, retro = palette.retro)) {
            CompositionLocalProvider(
                LocalActions provides actions,
                LocalPanelState provides PanelState(ticked, refreshing.keys, System.currentTimeMillis() / 1000, drafts),
            ) {
                // Until the dashboard's in (the app wasn't running), nothing shows.
                if (s == null || item == null) return@CompositionLocalProvider
                val p = LocalPalette.current
                Dialog(onDismissRequest = { finish() }) {
                    Box(
                        Modifier.fillMaxWidth().background(p.card, RoundedCornerShape(8.dp))
                            .border(1.dp, p.border, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                    ) { SwitchTile(s.monitor, item, ticked["${s.monitor}/${item.id}"] ?: item.done) }
                }
            }
        }
    }

    companion object {
        const val EXTRA_ID = "light"

        fun intent(context: Context, id: String): Intent =
            Intent(context, LightActivity::class.java).putExtra(EXTRA_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}

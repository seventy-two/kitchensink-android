package io.github.seventytwo.kitchensink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.seventytwo.kitchensink.data.Config
import io.github.seventytwo.kitchensink.data.OledCare
import io.github.seventytwo.kitchensink.model.json
import io.github.seventytwo.kitchensink.model.parseEvent
import io.github.seventytwo.kitchensink.model.parseSnapshot
import io.github.seventytwo.kitchensink.model.parseTime
import io.github.seventytwo.kitchensink.net.ConnState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config as Robo
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the dashboard from a made-up sample at the sizes it has to work
 * at: phones either way up, a big tablet and a TV. The pictures land in
 * app/build/screenshots/ for a look; nothing compares them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Robo(sdk = [36])
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val sample = json.parseToJsonElement(javaClass.getResource("/sample.json")!!.readText()).jsonObject
    private val snapshots = (sample["monitors"] as JsonArray).map(::parseSnapshot)
    private val events = (sample["events"] as JsonArray).map(::parseEvent)
    private val now = parseTime("2026-10-12T15:00:00Z")
    private val configured = Config(url = "https://example.com/kitchensink", token = "x")

    private fun state(config: Config = configured, tv: Boolean = false) =
        ScreenState(config, snapshots, events, ConnState.Connected, "", now, now, 0, tv = tv, overscan = tv, clock = { 30.0 })

    @Composable
    private fun Themed(palette: Palette = Palette.Dark, content: @Composable () -> Unit) {
        KitchensinkTheme(palette, Metrics.of(compact = true)) {
            CompositionLocalProvider(LocalPanelState provides PanelState(now = now, refreshing = setOf("football"))) { content() }
        }
    }

    private fun shot(name: String, palette: Palette = Palette.Dark, s: ScreenState = state(), after: () -> Unit = {}) {
        compose.setContent { Themed(palette) { DashboardScreen(s, ScreenCallbacks.None) } }
        compose.waitForIdle()
        after()
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test @Robo(qualifiers = "w411dp-h891dp-port-xxhdpi")
    fun phonePortrait() = shot("phone-portrait")

    @Test @Robo(qualifiers = "w411dp-h891dp-port-xxhdpi")
    fun phonePortraitRecent() = shot("phone-portrait-recent") {
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitForIdle()
    }

    @Test @Robo(qualifiers = "w411dp-h891dp-port-xxhdpi")
    fun phonePortraitLight() = shot("phone-portrait-light", Palette.Light)

    @Test @Robo(qualifiers = "w411dp-h891dp-port-xxhdpi")
    fun phonePortraitPinned() = shot(
        "phone-portrait-pinned",
        s = state(configured.copy(expanded = setOf("weather"), collapsed = setOf("calendar"), hidePrivate = true)),
    )

    @Test @Robo(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun phoneLandscape() = shot("phone-landscape", s = state(configured.copy(cat = true)))

    @Test @Robo(qualifiers = "w1024dp-h1366dp-port-xhdpi")
    fun tabletPortrait() = shot("tablet-portrait")

    @Test @Robo(qualifiers = "w1280dp-h800dp-land-xhdpi")
    fun tabletLandscape() = shot("tablet-landscape", Palette.Light)

    @Test @Robo(qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun tv() = shot("tv", Palette.Oled, state(configured.copy(cat = true, oled = OledCare(black = true, shift = true)), tv = true))

    @Test @Robo(qualifiers = "w411dp-h891dp-port-xxhdpi")
    fun phoneRetro() = shot("phone-retro", Palette.Retro, state(configured.copy(theme = io.github.seventytwo.kitchensink.data.ThemeChoice.Retro)))

    // TV mode on a TV draws everything a fifth smaller, as the app does.
    @Test @Robo(qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun tvSmaller() {
        compose.setContent {
            val d = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density * 0.8f, d.fontScale)) {
                Themed(Palette.Retro) { DashboardScreen(state(configured.copy(cat = true), tv = true), ScreenCallbacks.None) }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/tv-smaller-retro.png")
    }

    // Tables measure their columns in pixels, which the smaller TV mode makes fractional.
    @Test @Robo(qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun tvSmallerTable() {
        compose.setContent {
            val d = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density * 0.8f, d.fontScale)) {
                Themed { DashboardScreen(state(configured.copy(expanded = setOf("dailies")), tv = true), ScreenCallbacks.None) }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/tv-smaller-table.png")
    }

    // The lights' sliders and colour swatches, then a colour picker open.
    @Test @Robo(qualifiers = "w411dp-h891dp-port-xxhdpi")
    fun lights() {
        val lights = snapshots.first { it.monitor == "lights" }
        compose.setContent {
            Themed {
                Box(Modifier.fillMaxSize().background(LocalPalette.current.bg).padding(16.dp)) {
                    Box(Modifier.fillMaxWidth().background(LocalPalette.current.card).padding(12.dp)) {
                        BlockView("lights", lights.blocks[0])
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/lights.png")
        compose.onAllNodesWithContentDescription("Colour")[1].performClick()
        compose.waitForIdle()
        captureScreenRoboImage("build/screenshots/lights-colour.png")
    }

    @Test @Robo(qualifiers = "w411dp-h891dp-port-xxhdpi")
    fun lightsShort() {
        val lights = snapshots.first { it.monitor == "lights" }
        compose.setContent {
            Themed {
                Box(Modifier.fillMaxSize().background(LocalPalette.current.bg).padding(16.dp)) {
                    Box(Modifier.fillMaxWidth().background(LocalPalette.current.card).padding(12.dp)) {
                        BlockView("lights", lights.blocks[0].copy(buttons = true))
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Study").performClick()
        compose.waitForIdle()
        captureScreenRoboImage("build/screenshots/lights-short.png")
    }

    @Test @Robo(qualifiers = "w411dp-h891dp-port-xxhdpi")
    fun notSetUp() = shot("not-set-up", s = state(Config()).copy(conn = ConnState.NotConfigured, snapshots = emptyList()))

    @Test @Robo(qualifiers = "w411dp-h891dp-port-xxhdpi")
    fun settings() {
        compose.setContent { Themed { SettingsScreen(configured, snapshots, DeviceInfo(unrestricted = false), {}, {}, {}, {}) } }
        compose.onRoot().captureRoboImage("build/screenshots/settings.png")
    }

    // Tall enough to reach the diagnostics at the bottom.
    @Test @Robo(qualifiers = "w411dp-h2400dp-port-xxhdpi")
    fun settingsDiagnostics() {
        compose.setContent { Themed { SettingsScreen(configured, snapshots, DeviceInfo(unrestricted = false), {}, {}, {}, {}) } }
        compose.onNodeWithText("Mail").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/settings-diagnostics.png")
    }

    @Test @Robo(qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun settingsTv() {
        compose.setContent { Themed { SettingsScreen(configured.copy(token = ""), snapshots, DeviceInfo(isTv = true), {}, {}, {}, {}) } }
        compose.onRoot().captureRoboImage("build/screenshots/settings-tv.png")
    }
}

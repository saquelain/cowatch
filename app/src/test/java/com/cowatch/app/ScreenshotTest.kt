package com.cowatch.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.cowatch.app.session.ChatLine
import com.cowatch.app.session.FloatingReaction
import com.cowatch.app.session.Phase
import com.cowatch.app.session.SessionUiState
import com.cowatch.app.settings.AppSettings
import com.cowatch.app.settings.Feature
import com.cowatch.app.ui.screens.ConnectingScreen
import com.cowatch.app.ui.screens.EndedScreen
import com.cowatch.app.ui.screens.HomeScreen
import com.cowatch.app.ui.screens.HostWaitingScreen
import com.cowatch.app.ui.screens.SettingsScreen
import com.cowatch.app.ui.screens.WatchActions
import com.cowatch.app.ui.screens.WatchScreen
import com.cowatch.app.ui.theme.CoWatchTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every screen to PNG under /screenshots. Run with
 * `./gradlew recordRoborazziDebug` and look at the images before pushing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { CoWatchTheme(content) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("../screenshots/$name.png")
    }

    private val connected = SessionUiState(
        phase = Phase.CONNECTED, syncReady = true, partnerName = "Sam", partnerCameraOn = true, partnerMicOn = true,
        hasRemoteVideo = true, hasLocalVideo = true,
    )

    private val fakePlayer: @Composable (Modifier) -> Unit = { m ->
        Box(m.background(Brush.linearGradient(listOf(Color(0xFF0F2027), Color(0xFF2C5364)))), contentAlignment = Alignment.Center) {
            Text("🎬 movie playing", color = Color.White)
        }
    }
    private val fakePartner: @Composable (Modifier) -> Unit = { m ->
        Box(m.background(Brush.verticalGradient(listOf(Color(0xFF6A4C93), Color(0xFF3A2B5C)))), contentAlignment = Alignment.Center) {
            Text("Sam's camera", color = Color.White)
        }
    }
    private val fakeSelf: @Composable (Modifier) -> Unit = { m ->
        Box(m.background(Color(0xFF55607A)), contentAlignment = Alignment.Center) { Text("You", color = Color.White) }
    }

    @Composable
    private fun Watch(state: SessionUiState, settings: AppSettings = AppSettings(), chat: Boolean = false, react: Boolean = false, sourceMenu: Boolean = false) =
        WatchScreen(state, settings, WatchActions(), fakePlayer, fakePartner, fakeSelf, chat, react, sourceMenu)

    @Test fun home() = shot("01_home") {
        HomeScreen(AppSettings(), error = null, onHost = {}, onJoin = {}, onOpenSettings = {})
    }

    @Test fun homeBadCode() = shot("02_home_bad_code") {
        HomeScreen(AppSettings(), error = "No one is waiting with that code.", initialCode = "K7PQ2M", onHost = {}, onJoin = {}, onOpenSettings = {})
    }

    @Test fun hostWaiting() = shot("03_host_code") {
        HostWaitingScreen("K7PQ2M", secondsLeft = 587, onShare = {}, onCopy = {}, onCancel = {}, animate = false)
    }

    @Test fun connecting() = shot("04_connecting") {
        ConnectingScreen("Finding your partner…", onCancel = {}, animate = false)
    }

    @Test fun watchEmpty() = shot("05_watch_pick_movie") { Watch(connected) }

    @Test fun watchPlaying() = shot("06_watch_playing") {
        Watch(connected.copy(movieTitle = "Big Buck Bunny", isPlaying = false, positionMs = 754_000, durationMs = 596_000 * 2,
            toast = "Sam paused", reactions = listOf(FloatingReaction(1, "❤️", false, 0.3f), FloatingReaction(2, "😂", true, 0.6f))))
    }

    @Test fun watchChat() = shot("07_watch_chat") {
        Watch(
            connected.copy(movieTitle = "Big Buck Bunny", isPlaying = true, positionMs = 120_000, durationMs = 596_000,
                chat = listOf(ChatLine(1, false, "this bunny is so cute 😭"), ChatLine(2, true, "wait for the butterfly part"), ChatLine(3, false, "🍿🍿"))),
            chat = true,
        )
    }

    @Test fun watchReactions() = shot("08_watch_reactions") {
        Watch(connected.copy(movieTitle = "Big Buck Bunny", positionMs = 300_000, durationMs = 596_000, micOn = false), react = true)
    }

    @Test fun watchPartnerFile() = shot("09_watch_partner_local_file") {
        Watch(connected.copy(partnerLocalFile = "Before Sunrise (1995).mkv", partnerCameraOn = false))
    }

    @Test fun watchFeaturesOff() = shot("10_watch_minimal_features_off") {
        val s = AppSettings().with(Feature.VIDEO_CALL, false).with(Feature.TEXT_CHAT, false).with(Feature.REACTIONS, false)
        Watch(connected.copy(movieTitle = "Big Buck Bunny", positionMs = 30_000, durationMs = 596_000), s)
    }

    @Config(qualifiers = "+land")
    @Test fun watchLandscape() = shot("11_watch_landscape") {
        Watch(connected.copy(movieTitle = "Big Buck Bunny", positionMs = 754_000, durationMs = 1_192_000))
    }

    @Test fun settings() = shot("12_settings") {
        SettingsScreen(AppSettings().copy(displayName = "Alex"), onToggle = { _, _ -> }, onUpdate = {}, onReset = {}, onBack = {})
    }

    @Test fun settingsDependencies() = shot("13_settings_sync_off") {
        SettingsScreen(AppSettings().with(Feature.PLAYBACK_SYNC, false).with(Feature.VIDEO_CALL, false), onToggle = { _, _ -> }, onUpdate = {}, onReset = {}, onBack = {})
    }

    @Test fun changeVideo() = shot("15_watch_change_video") {
        Watch(connected.copy(movieTitle = "Big Buck Bunny", positionMs = 754_000, durationMs = 1_192_000), sourceMenu = true)
    }

    @Test fun streamingBuffering() = shot("16_watch_streaming_buffering") {
        Watch(connected.copy(movieTitle = "Our Holiday.mp4", isBuffering = true, isPlaying = true, positionMs = 61_000, durationMs = 1_800_000,
            toast = "Playing “Our Holiday.mp4” from Sam's phone"))
    }

    @Test fun linkingUp() = shot("17_watch_linking_up") {
        Watch(connected.copy(syncReady = false))
    }

    @Test fun ended() = shot("14_ended") {
        EndedScreen("Sam ended the watch party.", onDone = {})
    }
}

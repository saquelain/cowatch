package com.cowatch.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.cowatch.app.pairing.PairingCode
import com.cowatch.app.session.Phase
import com.cowatch.app.session.SessionViewModel
import com.cowatch.app.settings.Feature
import com.cowatch.app.ui.CallVideo
import com.cowatch.app.ui.MoviePlayer
import com.cowatch.app.ui.screens.ConnectingScreen
import com.cowatch.app.ui.screens.EndedScreen
import com.cowatch.app.ui.screens.HomeScreen
import com.cowatch.app.ui.screens.HostWaitingScreen
import com.cowatch.app.ui.screens.SettingsScreen
import com.cowatch.app.ui.screens.WatchActions
import com.cowatch.app.ui.screens.WatchScreen
import com.cowatch.app.ui.theme.CoWatchTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: SessionViewModel by viewModels()
    private var deepLinkCode by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always dark, so always use light status/navigation bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        handleIntent(intent)
        setContent { CoWatchTheme { App() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.data?.toString()?.let(PairingCode::fromDeepLink)?.let { deepLinkCode = it }
    }

    @Composable
    private fun App() {
        val state by vm.state.collectAsStateWithLifecycle()
        val settings by vm.settings.collectAsStateWithLifecycle()
        val remoteVideo by vm.remoteVideo.collectAsStateWithLifecycle()
        val localVideo by vm.localVideo.collectAsStateWithLifecycle()
        var showSettings by rememberSaveable { mutableStateOf(false) }
        var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
        var confirmLeave by remember { mutableStateOf(false) }

        val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            pendingAction?.invoke(); pendingAction = null
        }
        fun withCallPermissions(action: () -> Unit) {
            val needed = buildList {
                if (settings.enabled(Feature.VIDEO_CALL)) add(Manifest.permission.CAMERA)
                if (settings.enabled(Feature.VOICE_CHAT)) add(Manifest.permission.RECORD_AUDIO)
            }.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
            if (needed.isEmpty()) action() else { pendingAction = action; permissions.launch(needed.toTypedArray()) }
        }

        val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                vm.openMovie(uri, displayName(uri), isLocalFile = true)
            }
        }

        // Keep the screen awake while watching (if enabled).
        val view = LocalView.current
        val keepOn = state.phase == Phase.CONNECTED && settings.enabled(Feature.KEEP_SCREEN_ON)
        DisposableEffect(keepOn) {
            view.keepScreenOn = keepOn
            onDispose { view.keepScreenOn = false }
        }

        if (showSettings) {
            BackHandler { showSettings = false }
            SettingsScreen(
                settings = settings,
                onToggle = { f, on -> lifecycleScope.launch { vm.settingsRepo.setFeature(f, on); vm.onSettingsChanged() } },
                onUpdate = { t -> lifecycleScope.launch { vm.settingsRepo.update(t); vm.onSettingsChanged() } },
                onReset = { lifecycleScope.launch { vm.settingsRepo.resetToDefaults(); vm.onSettingsChanged() } },
                onBack = { showSettings = false },
            )
            return
        }

        when (state.phase) {
            Phase.IDLE -> HomeScreen(
                settings = settings,
                error = state.error,
                initialCode = deepLinkCode,
                onHost = { vm.clearError(); withCallPermissions { vm.hostSession() } },
                onJoin = { code -> vm.clearError(); withCallPermissions { vm.joinSession(code) } },
                onOpenSettings = { showSettings = true },
            )
            Phase.HOSTING -> {
                BackHandler { vm.hangUp() }
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) { while (true) { delay(1_000); now = System.currentTimeMillis() } }
                val code = state.code.orEmpty()
                HostWaitingScreen(
                    code = code,
                    secondsLeft = state.codeExpiresAtMs?.takeIf { settings.enabled(Feature.CODE_EXPIRY) }
                        ?.let { ((it - now) / 1000).coerceAtLeast(0) },
                    onShare = { shareCode(code) },
                    onCopy = { copyCode(code) },
                    onCancel = { vm.hangUp() },
                )
            }
            Phase.JOINING, Phase.CONNECTING -> {
                BackHandler { vm.hangUp() }
                ConnectingScreen(
                    message = if (state.isHost) "${state.partnerName} is joining…" else "Finding your partner…",
                    onCancel = { vm.hangUp() },
                )
            }
            Phase.CONNECTED -> {
                BackHandler { confirmLeave = true }
                WatchScreen(
                    state = state,
                    settings = settings,
                    actions = WatchActions(
                        onTogglePlay = vm::togglePlay,
                        onSeek = vm::seekTo,
                        onSkip = vm::skip,
                        onOpenLink = { url -> vm.openMovie(Uri.parse(url), titleFromUrl(url), isLocalFile = false) },
                        onPickFile = { pickFile.launch(arrayOf("video/*")) },
                        onDismissPartnerFile = vm::dismissPartnerFile,
                        onCloseMovie = vm::closeMovie,
                        onToggleMic = vm::toggleMic,
                        onToggleCamera = vm::toggleCamera,
                        onSwitchCamera = vm::switchCamera,
                        onReact = vm::sendReaction,
                        onSendChat = vm::sendChat,
                        onChatOpened = vm::markChatRead,
                        onHangUp = { confirmLeave = true },
                        onToastShown = vm::clearToast,
                    ),
                    player = { m -> MoviePlayer(vm.player, m) },
                    partnerVideo = { m -> CallVideo(remoteVideo, mirror = false, overlay = true, modifier = m) },
                    selfVideo = { m -> CallVideo(localVideo, mirror = true, overlay = true, modifier = m) },
                )
                if (confirmLeave) AlertDialog(
                    onDismissRequest = { confirmLeave = false },
                    title = { Text("Leave the watch party?") },
                    text = { Text("This ends the call for both of you.") },
                    confirmButton = { TextButton(onClick = { confirmLeave = false; vm.hangUp() }) { Text("Leave") } },
                    dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Stay") } },
                )
            }
            Phase.ENDED -> {
                BackHandler { vm.dismissEnded() }
                EndedScreen(state.endedReason ?: "", onDone = vm::dismissEnded)
            }
        }
    }

    private fun shareCode(code: String) {
        val text = "Let's watch a movie together on CoWatch 🍿\n" +
            "My code: ${PairingCode.format(code)}\n${PairingCode.deepLink(code)}"
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share code"))
    }

    private fun copyCode(code: String) {
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("CoWatch code", PairingCode.format(code)))
        Toast.makeText(this, "Code copied", Toast.LENGTH_SHORT).show()
    }

    private fun displayName(uri: Uri): String =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "Movie"

    private fun titleFromUrl(url: String): String =
        Uri.parse(url).lastPathSegment?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "Video"
}

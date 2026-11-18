package com.cowatch.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cowatch.app.session.ChatLine
import com.cowatch.app.session.FloatingReaction
import com.cowatch.app.session.SessionUiState
import com.cowatch.app.session.SessionViewModel.Companion.formatTime
import com.cowatch.app.settings.AppSettings
import com.cowatch.app.settings.Feature
import com.cowatch.app.ui.theme.BrandGradient
import com.cowatch.app.ui.theme.Mint
import com.cowatch.app.ui.theme.Muted
import com.cowatch.app.ui.theme.Surface2

val REACTION_EMOJIS = listOf("❤️", "😂", "😮", "😢", "😍", "🔥", "🍿", "👏")
const val SAMPLE_URL = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

class WatchActions(
    val onTogglePlay: () -> Unit = {},
    val onSeek: (Long) -> Unit = {},
    val onSkip: (Long) -> Unit = {},
    val onOpenLink: (url: String) -> Unit = {},
    val onPickFile: () -> Unit = {},
    val onDismissPartnerFile: () -> Unit = {},
    val onToggleMic: () -> Unit = {},
    val onToggleCamera: () -> Unit = {},
    val onSwitchCamera: () -> Unit = {},
    val onReact: (String) -> Unit = {},
    val onSendChat: (String) -> Unit = {},
    val onChatOpened: () -> Unit = {},
    val onHangUp: () -> Unit = {},
    val onToastShown: () -> Unit = {},
)

@Composable
fun WatchScreen(
    state: SessionUiState,
    settings: AppSettings,
    actions: WatchActions,
    player: @Composable (Modifier) -> Unit,
    partnerVideo: @Composable (Modifier) -> Unit,
    selfVideo: @Composable (Modifier) -> Unit,
    initialChatOpen: Boolean = false,
    initialReactOpen: Boolean = false,
) {
    var chatOpen by rememberSaveable { mutableStateOf(initialChatOpen) }
    var reactOpen by rememberSaveable { mutableStateOf(initialReactOpen) }
    var linkDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.toast) {
        if (state.toast != null) { kotlinx.coroutines.delay(2_500); actions.onToastShown() }
    }
    LaunchedEffect(chatOpen, state.chat.size) { if (chatOpen) actions.onChatOpened() }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val landscape = maxWidth > maxHeight
        val playerArea: @Composable (Modifier) -> Unit = { m ->
            PlayerArea(state, settings, actions, player, onOpenLinkDialog = { linkDialog = true }, modifier = m)
        }
        val callBar: @Composable (Modifier) -> Unit = { m ->
            CallControls(
                state, settings, actions, m,
                chatOpen = chatOpen, onChat = { chatOpen = !chatOpen; reactOpen = false },
                onReactMenu = { reactOpen = !reactOpen },
            )
        }

        if (landscape) {
            Row(Modifier.fillMaxSize().navigationBarsPadding()) {
                playerArea(Modifier.weight(1f).fillMaxHeight())
                Column(
                    Modifier.width(220.dp).fillMaxHeight().background(MaterialTheme.colorScheme.background).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PartnerTile(state, settings, partnerVideo, selfVideo, Modifier.fillMaxWidth().weight(1f))
                    callBar(Modifier.fillMaxWidth())
                }
            }
        } else {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
                TopStrip(state)
                playerArea(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                Spacer(Modifier.height(12.dp))
                PartnerTile(
                    state, settings, partnerVideo, selfVideo,
                    Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp),
                )
                callBar(Modifier.fillMaxWidth().padding(12.dp))
            }
        }

        // Floating reactions over everything.
        state.reactions.forEach { r -> key(r.id) { RisingEmoji(r, maxHeight.value) } }

        AnimatedVisibility(
            reactOpen && settings.enabled(Feature.REACTIONS),
            enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
        ) {
            ReactionPicker { actions.onReact(it); reactOpen = false }
        }

        AnimatedVisibility(
            chatOpen && settings.enabled(Feature.TEXT_CHAT),
            enter = slideInVertically { it }, exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ChatSheet(state.chat, state.partnerName, onSend = actions.onSendChat, onClose = { chatOpen = false },
                modifier = Modifier.fillMaxWidth().fillMaxHeight(if (landscape) 0.9f else 0.55f))
        }

        AnimatedVisibility(
            state.toast != null, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 56.dp),
        ) {
            Text(
                state.toast.orEmpty(),
                modifier = Modifier.clip(CircleShape).background(Surface2.copy(alpha = 0.95f)).padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }

    if (linkDialog) OpenLinkDialog(onDismiss = { linkDialog = false }, onOpen = { actions.onOpenLink(it); linkDialog = false })
}

@Composable
private fun key(id: Long, content: @Composable () -> Unit) = androidx.compose.runtime.key(id) { content() }

@Composable
private fun TopStrip(state: SessionUiState) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Mint))
        Text(
            "  Watching with ${state.partnerName}",
            style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        state.movieTitle?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(140.dp)) }
    }
}

@Composable
private fun PlayerArea(
    state: SessionUiState,
    settings: AppSettings,
    actions: WatchActions,
    player: @Composable (Modifier) -> Unit,
    onOpenLinkDialog: () -> Unit,
    modifier: Modifier,
) {
    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(controlsVisible, state.isPlaying) {
        if (controlsVisible && state.isPlaying) { kotlinx.coroutines.delay(4_000); controlsVisible = false }
    }
    Box(modifier.background(Color.Black).clickable { controlsVisible = !controlsVisible }) {
        if (state.movieTitle == null) {
            EmptyPlayer(state, settings, actions, onOpenLinkDialog)
        } else {
            player(Modifier.fillMaxSize())
            if (!state.isPlaying || controlsVisible) {
                PlaybackOverlay(state, actions, Modifier.fillMaxSize())
            }
        }
        state.partnerLocalFile?.let { title ->
            Row(
                Modifier.align(Alignment.TopCenter).padding(8.dp).clip(RoundedCornerShape(14.dp))
                    .background(Surface2).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${state.partnerName} opened “$title”", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f, fill = false))
                TextButton(onClick = actions.onPickFile) { Text("Pick my copy") }
                IconButton(onClick = actions.onDismissPartnerFile) { Icon(Icons.Filled.Close, "Dismiss", Modifier.size(18.dp)) }
            }
        }
    }
}

@Composable
private fun EmptyPlayer(state: SessionUiState, settings: AppSettings, actions: WatchActions, onOpenLinkDialog: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(EmptyGradient).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Movie, null, tint = Color.White, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(6.dp))
        Text("What are we watching?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            if (settings.enabled(Feature.SHARE_LINKS)) "Open a link and it plays on both phones." else "You each open the same movie.",
            color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onOpenLinkDialog) { Icon(Icons.Filled.Link, null, Modifier.size(18.dp)); Text(" Link") }
            FilledTonalButton(onClick = actions.onPickFile) { Icon(Icons.Filled.FolderOpen, null, Modifier.size(18.dp)); Text(" File") }
            FilledTonalButton(onClick = { actions.onOpenLink(SAMPLE_URL) }) { Text("Sample") }
        }
    }
}

private val EmptyGradient = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF3B2470), Color(0xFF1E1730)))

@Composable
private fun PlaybackOverlay(state: SessionUiState, actions: WatchActions, modifier: Modifier) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    Box(modifier.background(Color.Black.copy(alpha = 0.35f))) {
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.onSkip(-10_000) }) { Icon(Icons.Filled.Replay10, "Back 10s", tint = Color.White, modifier = Modifier.size(32.dp)) }
            Box(
                Modifier.size(60.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)).clickable { actions.onTogglePlay() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (state.isPlaying) "Pause" else "Play",
                    tint = Color.Black, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = { actions.onSkip(10_000) }) { Icon(Icons.Filled.Forward10, "Forward 10s", tint = Color.White, modifier = Modifier.size(32.dp)) }
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(if (dragging) dragValue.toLong() else state.positionMs), color = Color.White, fontSize = 12.sp)
            Slider(
                value = if (dragging) dragValue else state.positionMs.toFloat().coerceAtMost(state.durationMs.toFloat().coerceAtLeast(1f)),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = { actions.onSeek(dragValue.toLong()); dragging = false },
                valueRange = 0f..state.durationMs.toFloat().coerceAtLeast(1f),
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(formatTime(state.durationMs), color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PartnerTile(
    state: SessionUiState,
    settings: AppSettings,
    partnerVideo: @Composable (Modifier) -> Unit,
    selfVideo: @Composable (Modifier) -> Unit,
    modifier: Modifier,
) {
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)) {
        val showPartnerVideo = settings.enabled(Feature.VIDEO_CALL) && state.hasRemoteVideo && state.partnerCameraOn
        if (showPartnerVideo) {
            partnerVideo(Modifier.fillMaxSize())
        } else {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(84.dp).clip(CircleShape).background(BrandGradient), contentAlignment = Alignment.Center) {
                    Text(state.partnerName.take(1).uppercase(), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (settings.enabled(Feature.VIDEO_CALL)) "${state.partnerName}'s camera is off" else "Video call is off",
                    color = Muted, style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Row(
            Modifier.align(Alignment.BottomStart).padding(10.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!state.partnerMicOn) Icon(Icons.Filled.MicOff, "Partner muted", tint = Color.White, modifier = Modifier.size(14.dp).padding(end = 2.dp))
            Text(state.partnerName, color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
        if (settings.enabled(Feature.SELF_PREVIEW) && state.hasLocalVideo) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(10.dp).width(84.dp).aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(14.dp)).border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .background(Surface2),
            ) {
                if (state.cameraOn) selfVideo(Modifier.fillMaxSize())
                else Icon(Icons.Filled.VideocamOff, null, tint = Muted, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CallControls(
    state: SessionUiState,
    settings: AppSettings,
    actions: WatchActions,
    modifier: Modifier,
    chatOpen: Boolean,
    onChat: () -> Unit,
    onReactMenu: () -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (settings.enabled(Feature.VOICE_CHAT)) {
            RoundButton(if (state.micOn) Icons.Filled.Mic else Icons.Filled.MicOff, if (state.micOn) "Mute" else "Unmute", active = state.micOn, onClick = actions.onToggleMic)
        }
        if (settings.enabled(Feature.VIDEO_CALL)) {
            RoundButton(if (state.cameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff, "Camera", active = state.cameraOn, onClick = actions.onToggleCamera)
            RoundButton(Icons.Filled.Cameraswitch, "Flip camera", onClick = actions.onSwitchCamera)
        }
        if (settings.enabled(Feature.REACTIONS)) RoundButton(Icons.Filled.EmojiEmotions, "React", onClick = onReactMenu)
        if (settings.enabled(Feature.TEXT_CHAT)) {
            BadgedBox(badge = { if (state.unreadChat > 0 && !chatOpen) Badge { Text("${state.unreadChat}") } }) {
                RoundButton(Icons.Filled.Chat, "Chat", active = !chatOpen, onClick = onChat)
            }
        }
        RoundButton(Icons.Filled.CallEnd, "End", danger = true, onClick = actions.onHangUp)
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, active: Boolean = true, danger: Boolean = false, onClick: () -> Unit) {
    val bg = when {
        danger -> Color(0xFFE5484D)
        active -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.White
    }
    val fg = if (!active && !danger) Color.Black else Color.White
    Box(Modifier.size(50.dp).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = fg)
    }
}

@Composable
private fun ReactionPicker(onPick: (String) -> Unit) {
    Row(
        Modifier.clip(CircleShape).background(Surface2).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        REACTION_EMOJIS.forEach { e ->
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { onPick(e) }, contentAlignment = Alignment.Center) {
                Text(e, fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun RisingEmoji(r: FloatingReaction, maxHeightDp: Float) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(r.id) { progress.animateTo(1f, tween(2_400)) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val x = (maxWidth.value * r.xFraction).toInt()
        val y = (maxHeightDp * (0.8f - 0.6f * progress.value)).toInt()
        Text(
            r.emoji, fontSize = 40.sp,
            modifier = Modifier.offset { IntOffset(x.dp.roundToPx(), y.dp.roundToPx()) }.alpha(1f - progress.value * 0.8f),
        )
    }
}

@Composable
private fun ChatSheet(lines: List<ChatLine>, partnerName: String, onSend: (String) -> Unit, onClose: () -> Unit, modifier: Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex) }
    Column(
        modifier.clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding().imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Chat with $partnerName", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close chat") }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (lines.isEmpty()) item {
                Text("Say hi 👋 — messages won't pause the movie.", color = Muted, modifier = Modifier.padding(8.dp))
            }
            items(lines, key = { it.id }) { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.fromMe) Arrangement.End else Arrangement.Start) {
                    Text(
                        line.text,
                        modifier = Modifier.clip(RoundedCornerShape(18.dp))
                            .background(if (line.fromMe) MaterialTheme.colorScheme.primary else Surface2)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text, onValueChange = { text = it.take(500) },
                placeholder = { Text("Message", color = Muted) },
                singleLine = true, shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend(text); text = "" }),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onSend(text); text = "" }, enabled = text.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun OpenLinkDialog(onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open a video link") },
        text = {
            Column {
                Text("Direct video links (.mp4, .m3u8, .webm…) work best.", color = Muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(url, { url = it.trim() }, singleLine = true, placeholder = { Text("https://…") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (url.isNotBlank()) onOpen(url) }))
            }
        },
        confirmButton = { Button(onClick = { onOpen(url) }, enabled = url.startsWith("http")) { Text("Play together") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

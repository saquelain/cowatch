package com.cowatch.app.session

import android.app.Application
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.cowatch.app.call.WebRtcCall
import com.cowatch.app.pairing.PairingCode
import com.cowatch.app.settings.AppSettings
import com.cowatch.app.settings.Feature
import com.cowatch.app.settings.SettingsRepository
import com.cowatch.app.signaling.PeerJsPayloads
import com.cowatch.app.signaling.PeerJsSignaling
import com.cowatch.app.signaling.PeerJsSignaling.SignalType
import com.cowatch.app.sync.DriftCorrector
import com.cowatch.app.sync.SyncMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import kotlin.math.abs
import kotlin.random.Random

class SessionViewModel(app: Application) : AndroidViewModel(app) {

    val settingsRepo = SettingsRepository(app)
    val settings: StateFlow<AppSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    private val s get() = settings.value

    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state.asStateFlow()

    private val _remoteVideo = MutableStateFlow<VideoTrack?>(null)
    val remoteVideo: StateFlow<VideoTrack?> = _remoteVideo.asStateFlow()
    private val _localVideo = MutableStateFlow<VideoTrack?>(null)
    val localVideo: StateFlow<VideoTrack?> = _localVideo.asStateFlow()

    val player: ExoPlayer = ExoPlayer.Builder(app).build().apply {
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = refreshPlayback()
            override fun onPlaybackStateChanged(playbackState: Int) = refreshPlayback()
        })
    }

    private var signaling: PeerJsSignaling? = null
    private var call: WebRtcCall? = null
    private var partnerPeerId: String? = null
    private var connectionId: String = PeerJsPayloads.newConnectionId()
    private var sessionJob: Job? = null
    private var tickerJob: Job? = null
    private var ids = 0L
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    init {
        tickerJob = viewModelScope.launch {
            var tick = 0
            while (isActive) {
                delay(500)
                refreshPlayback()
                tick++
                val st = _state.value
                if (tick % 6 == 0 && st.phase == Phase.CONNECTED && st.isHost &&
                    s.enabled(Feature.DRIFT_CORRECTION) && st.movieTitle != null
                ) {
                    send(SyncMessage.State(player.currentPosition, player.playWhenReady, System.currentTimeMillis()))
                }
                if (st.phase == Phase.HOSTING && s.enabled(Feature.CODE_EXPIRY)) {
                    val exp = st.codeExpiresAtMs
                    if (exp != null && System.currentTimeMillis() > exp) endSession("Your code expired. Make a new one when you're ready.")
                }
            }
        }
    }

    // ---------------------------------------------------------------- pairing

    fun hostSession() {
        teardown()
        val code = PairingCode.generate()
        _state.value = SessionUiState(
            phase = Phase.HOSTING, isHost = true, code = code,
            codeExpiresAtMs = System.currentTimeMillis() + PairingCode.EXPIRY_MS,
        )
        openSignaling(PairingCode.hostPeerId(code))
    }

    fun joinSession(rawCode: String) {
        val code = PairingCode.normalize(rawCode)
        if (!PairingCode.isValid(code)) {
            _state.update { it.copy(error = "That code doesn't look right. It has 6 letters/numbers.") }
            return
        }
        teardown()
        _state.value = SessionUiState(phase = Phase.JOINING, isHost = false, code = code)
        partnerPeerId = PairingCode.hostPeerId(code)
        openSignaling(PairingCode.guestPeerId())
        sessionJob = viewModelScope.launch {
            delay(25_000)
            if (_state.value.phase == Phase.JOINING || _state.value.phase == Phase.CONNECTING) {
                endSession("Couldn't reach your partner. Check the code and that they're still waiting.")
            }
        }
    }

    private fun openSignaling(myId: String) {
        val sig = PeerJsSignaling(s.signalingHost, myId, object : PeerJsSignaling.Listener {
            override fun onOpen() = onMain { this@SessionViewModel.onSignalingOpen() }
            override fun onIdTaken() = onMain {
                if (_state.value.isHost) hostSession() else endSession("Pairing failed, please try again.")
            }
            override fun onSignal(type: SignalType, src: String, payload: JsonObject) = onMain { this@SessionViewModel.onSignal(type, src, payload) }
            override fun onPeerUnavailable(peerId: String) = onMain {
                if (!_state.value.isHost && _state.value.phase != Phase.CONNECTED) {
                    endSession("No one is waiting with that code. It may have expired or already been used.")
                }
            }
            override fun onError(message: String) = onMain {
                if (_state.value.phase != Phase.CONNECTED && _state.value.phase != Phase.ENDED) {
                    endSession("Couldn't reach the pairing server ($message).")
                }
            }
            override fun onClosed() = Unit
        })
        signaling = sig
        sig.connect()
    }

    private fun onSignalingOpen() {
        if (_state.value.isHost) return // just wait for an offer
        val target = partnerPeerId ?: return
        viewModelScope.launch {
            val c = newCall()
            val offer = runCatching { c.createOffer() }.getOrElse { endSession("Couldn't start the call: ${it.message}"); return@launch }
            _state.update { it.copy(phase = Phase.CONNECTING) }
            signaling?.send(SignalType.OFFER, target, PeerJsPayloads.sdp("offer", offer.description, connectionId, myName()))
        }
    }

    private fun onSignal(type: SignalType, src: String, payload: JsonObject) {
        when (type) {
            SignalType.OFFER -> {
                // One-time code: the first partner to arrive gets in, nobody else.
                if (!_state.value.isHost || partnerPeerId != null) return
                val offer = PeerJsPayloads.readSdp(payload) ?: return
                partnerPeerId = src
                PeerJsPayloads.connectionId(payload)?.let { connectionId = it }
                val sdp = offer.sdp
                val name = offer.name
                _state.update { it.copy(phase = Phase.CONNECTING, partnerName = name ?: it.partnerName) }
                sessionJob?.cancel()
                sessionJob = viewModelScope.launch {
                    delay(30_000)
                    if (_state.value.phase == Phase.CONNECTING) {
                        endSession("Couldn't connect to ${_state.value.partnerName}. If you're on different networks, a TURN server (Settings) may help.")
                    }
                }
                viewModelScope.launch {
                    val c = newCall()
                    val answer = runCatching {
                        c.acceptOffer(SessionDescription(SessionDescription.Type.OFFER, sdp))
                    }.getOrElse { endSession("Couldn't connect: ${it.message}"); return@launch }
                    signaling?.send(SignalType.ANSWER, src, PeerJsPayloads.sdp("answer", answer.description, connectionId, myName()))
                }
            }
            SignalType.ANSWER -> {
                if (src != partnerPeerId) return
                val answer = PeerJsPayloads.readSdp(payload) ?: return
                val sdp = answer.sdp
                answer.name?.let { n ->
                    _state.update { it.copy(partnerName = n) }
                }
                viewModelScope.launch {
                    runCatching { call?.acceptAnswer(SessionDescription(SessionDescription.Type.ANSWER, sdp)) }
                        .onFailure { endSession("Couldn't connect: ${it.message}") }
                }
            }
            SignalType.CANDIDATE -> {
                if (src != partnerPeerId) return
                val c = PeerJsPayloads.readCandidate(payload) ?: return
                call?.addRemoteCandidate(IceCandidate(c.sdpMid, c.sdpMLineIndex, c.candidate))
            }
            SignalType.LEAVE -> if (src == partnerPeerId && _state.value.phase != Phase.CONNECTED) {
                endSession("${_state.value.partnerName} left.")
            }
        }
    }

    private fun newCall(): WebRtcCall {
        val ctx = getApplication<Application>()
        val c = WebRtcCall(
            ctx, s,
            sendVideo = s.enabled(Feature.VIDEO_CALL) && hasPermission(android.Manifest.permission.CAMERA),
            sendAudio = s.enabled(Feature.VOICE_CHAT) && hasPermission(android.Manifest.permission.RECORD_AUDIO),
            events = object : WebRtcCall.Events {
                override fun onIceCandidate(candidate: IceCandidate) {
                    val dst = partnerPeerId ?: return
                    signaling?.send(
                        SignalType.CANDIDATE, dst,
                        PeerJsPayloads.candidate(PeerJsPayloads.Candidate(candidate.sdp, candidate.sdpMid ?: "", candidate.sdpMLineIndex), connectionId),
                    )
                }
                override fun onConnectionChange(state: PeerConnection.PeerConnectionState) = onMain { this@SessionViewModel.onConnectionChange(state) }
                override fun onDataOpen() = onMain { this@SessionViewModel.onDataOpen() }
                override fun onDataMessage(text: String) = onMain { SyncMessage.decode(text)?.let(::onPeerMessage) }
                override fun onRemoteVideo(track: VideoTrack?) {
                    _remoteVideo.value = track
                    _state.update { it.copy(hasRemoteVideo = track != null) }
                }
                override fun onLocalVideo(track: VideoTrack?) {
                    _localVideo.value = track
                    _state.update { it.copy(hasLocalVideo = track != null) }
                }
            },
        )
        c.start()
        call = c
        _state.update { it.copy(micOn = c.hasMic, cameraOn = c.hasCamera) }
        return c
    }

    private fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
        when (state) {
            PeerConnection.PeerConnectionState.CONNECTED -> {
                sessionJob?.cancel()
                _state.update { it.copy(phase = Phase.CONNECTED, code = null, codeExpiresAtMs = null) }
                applyAudioRouting(true)
                applyMovieVolume()
                // Burn the one-time code: drop off the pairing server once we're connected.
                viewModelScope.launch { delay(3_000); signaling?.close(); signaling = null }
            }
            PeerConnection.PeerConnectionState.FAILED ->
                endSession("The connection to ${_state.value.partnerName} was lost.")
            PeerConnection.PeerConnectionState.CLOSED ->
                if (_state.value.phase == Phase.CONNECTED) endSession("${_state.value.partnerName} left.")
            else -> Unit
        }
    }

    private fun onDataOpen() {
        send(SyncMessage.Hello(myName(), Feature.entries.filter { s.enabled(it) }.map { it.name }))
        sendMediaState()
        val st = _state.value
        if (st.isHost && st.movieTitle != null && s.enabled(Feature.PLAYBACK_SYNC)) {
            send(SyncMessage.Load(st.movieTitle, currentUrl()?.takeIf { s.enabled(Feature.SHARE_LINKS) }))
            send(SyncMessage.State(player.currentPosition, player.playWhenReady, System.currentTimeMillis()))
        }
    }

    // ---------------------------------------------------------------- peer messages

    private fun onPeerMessage(msg: SyncMessage) {
        val sync = s.enabled(Feature.PLAYBACK_SYNC)
        val partner = _state.value.partnerName
        when (msg) {
            is SyncMessage.Hello -> _state.update { it.copy(partnerName = msg.name.ifBlank { it.partnerName }) }
            is SyncMessage.MediaState -> _state.update { it.copy(partnerCameraOn = msg.cameraOn, partnerMicOn = msg.micOn) }
            is SyncMessage.Load -> if (sync) {
                if (msg.url != null && s.enabled(Feature.SHARE_LINKS)) {
                    loadInternal(Uri.parse(msg.url), msg.title, broadcast = false)
                    toast("$partner opened “${msg.title}”")
                } else if (_state.value.movieTitle != msg.title) {
                    _state.update { it.copy(partnerLocalFile = msg.title) }
                }
            }
            is SyncMessage.Play -> if (sync) {
                seekIfFar(msg.positionMs); player.play(); toast("$partner pressed play")
            }
            is SyncMessage.Pause -> if (sync) {
                player.pause(); seekIfFar(msg.positionMs); toast("$partner paused")
            }
            is SyncMessage.Seek -> if (sync) { player.seekTo(msg.positionMs); toast("$partner jumped to ${formatTime(msg.positionMs)}") }
            is SyncMessage.State -> if (s.enabled(Feature.DRIFT_CORRECTION) && _state.value.movieTitle != null) {
                DriftCorrector.correction(
                    isHost = _state.value.isHost,
                    localPositionMs = player.currentPosition,
                    localPlaying = player.playWhenReady,
                    remote = msg,
                    nowMs = System.currentTimeMillis(),
                )?.let { fix ->
                    fix.seekToMs?.let(player::seekTo)
                    fix.play?.let { if (it) player.play() else player.pause() }
                }
            }
            is SyncMessage.Chat -> if (s.enabled(Feature.TEXT_CHAT)) {
                _state.update { it.copy(chat = (it.chat + ChatLine(++ids, false, msg.text)).takeLast(200), unreadChat = it.unreadChat + 1) }
                buzz()
            }
            is SyncMessage.Reaction -> if (s.enabled(Feature.REACTIONS)) {
                showReaction(msg.emoji, fromMe = false); buzz()
            }
            SyncMessage.Bye -> endSession("$partner ended the watch party.")
        }
    }

    private fun seekIfFar(positionMs: Long) {
        if (abs(player.currentPosition - positionMs) > 800) player.seekTo(positionMs)
    }

    // ---------------------------------------------------------------- local actions

    fun openMovie(uri: Uri, title: String, isLocalFile: Boolean) {
        loadInternal(uri, title, broadcast = true, isLocalFile = isLocalFile)
    }

    private fun loadInternal(uri: Uri, title: String, broadcast: Boolean, isLocalFile: Boolean = false) {
        player.setMediaItem(
            MediaItem.Builder().setUri(uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
                .build()
        )
        player.prepare()
        _state.update { it.copy(movieTitle = title, partnerLocalFile = null) }
        applyMovieVolume()
        if (broadcast && s.enabled(Feature.PLAYBACK_SYNC)) {
            val shareUrl = if (!isLocalFile && s.enabled(Feature.SHARE_LINKS)) uri.toString() else null
            send(SyncMessage.Load(title, shareUrl))
        }
    }

    private fun currentUrl(): String? = player.currentMediaItem?.localConfiguration?.uri?.toString()
        ?.takeIf { it.startsWith("http") }

    fun togglePlay() {
        if (player.playWhenReady) {
            player.pause(); syncSend(SyncMessage.Pause(player.currentPosition))
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play(); syncSend(SyncMessage.Play(player.currentPosition))
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0))
        syncSend(SyncMessage.Seek(player.currentPosition))
        refreshPlayback()
    }

    fun skip(deltaMs: Long) = seekTo(player.currentPosition + deltaMs)

    fun sendChat(text: String) {
        val t = text.trim()
        if (t.isEmpty() || !s.enabled(Feature.TEXT_CHAT)) return
        send(SyncMessage.Chat(t))
        _state.update { it.copy(chat = (it.chat + ChatLine(++ids, true, t)).takeLast(200)) }
    }

    fun markChatRead() = _state.update { it.copy(unreadChat = 0) }

    fun sendReaction(emoji: String) {
        if (!s.enabled(Feature.REACTIONS)) return
        send(SyncMessage.Reaction(emoji))
        showReaction(emoji, fromMe = true)
    }

    fun toggleMic() {
        val c = call ?: return
        if (!c.hasMic) { toast("Microphone is off in Settings or permission was denied"); return }
        val on = !_state.value.micOn
        c.setMicEnabled(on)
        _state.update { it.copy(micOn = on) }
        sendMediaState()
    }

    fun toggleCamera() {
        val c = call ?: return
        if (!c.hasCamera) { toast("Camera is off in Settings or permission was denied"); return }
        val on = !_state.value.cameraOn
        c.setCameraEnabled(on)
        _state.update { it.copy(cameraOn = on) }
        sendMediaState()
    }

    fun switchCamera() = call?.switchCamera()

    fun hangUp() {
        send(SyncMessage.Bye)
        endSession(null)
        _state.value = SessionUiState()
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearToast() = _state.update { it.copy(toast = null) }
    fun dismissEnded() { _state.value = SessionUiState() }
    fun dismissPartnerFile() = _state.update { it.copy(partnerLocalFile = null) }

    fun onSettingsChanged() {
        applyMovieVolume()
        if (_state.value.phase == Phase.CONNECTED) applyAudioRouting(true)
    }

    // ---------------------------------------------------------------- helpers

    private fun send(msg: SyncMessage) { call?.send(SyncMessage.encode(msg)) }

    private fun syncSend(msg: SyncMessage) { if (s.enabled(Feature.PLAYBACK_SYNC)) send(msg) }

    private fun sendMediaState() {
        val st = _state.value
        send(SyncMessage.MediaState(cameraOn = st.cameraOn && call?.hasCamera == true, micOn = st.micOn && call?.hasMic == true))
    }

    private fun showReaction(emoji: String, fromMe: Boolean) {
        val r = FloatingReaction(++ids, emoji, fromMe, Random.nextFloat() * 0.6f + 0.2f)
        _state.update { it.copy(reactions = (it.reactions + r).takeLast(12)) }
        viewModelScope.launch {
            delay(2_600)
            _state.update { st -> st.copy(reactions = st.reactions.filterNot { it.id == r.id }) }
        }
    }

    private fun toast(text: String) = _state.update { it.copy(toast = text) }

    private fun refreshPlayback() {
        val dur = player.duration.takeIf { it != C.TIME_UNSET } ?: 0
        _state.update {
            it.copy(
                isPlaying = player.playWhenReady,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                positionMs = player.currentPosition,
                durationMs = dur,
            )
        }
    }

    private fun applyMovieVolume() {
        val inCall = _state.value.phase == Phase.CONNECTED && call?.hasMic == true
        player.volume = if (inCall && s.enabled(Feature.DUCK_MOVIE)) 0.6f else 1f
    }

    @Suppress("DEPRECATION")
    private fun applyAudioRouting(active: Boolean) {
        if (!active) {
            if (Build.VERSION.SDK_INT >= 31) audioManager.clearCommunicationDevice()
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
            return
        }
        if (!s.enabled(Feature.VOICE_CHAT)) return
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        val speaker = s.enabled(Feature.SPEAKERPHONE)
        if (Build.VERSION.SDK_INT >= 31) {
            if (speaker) {
                audioManager.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    ?.let(audioManager::setCommunicationDevice)
            } else audioManager.clearCommunicationDevice()
        } else {
            audioManager.isSpeakerphoneOn = speaker
        }
    }

    private fun buzz() {
        if (!s.enabled(Feature.HAPTICS)) return
        val ctx = getApplication<Application>()
        val vib = if (Build.VERSION.SDK_INT >= 31) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION") ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        runCatching { vib.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }

    private fun hasPermission(p: String) =
        getApplication<Application>().checkSelfPermission(p) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun myName() = s.displayName.ifBlank { Build.MODEL ?: "Partner" }

    private fun endSession(reason: String?) {
        teardown()
        player.pause()
        if (reason != null) _state.update {
            it.copy(phase = Phase.ENDED, endedReason = reason, code = null, codeExpiresAtMs = null)
        }
    }

    private fun teardown() {
        sessionJob?.cancel(); sessionJob = null
        // No LEAVE message: the public PeerJS server disconnects clients that send one. Closing is enough.
        signaling?.close(); signaling = null
        call?.close(); call = null
        partnerPeerId = null
        connectionId = PeerJsPayloads.newConnectionId()
        applyAudioRouting(false)
        applyMovieVolume()
    }

    private fun onMain(block: () -> Unit): Unit {
        viewModelScope.launch(Dispatchers.Main) { block() }
    }

    override fun onCleared() {
        send(SyncMessage.Bye)
        teardown()
        tickerJob?.cancel()
        player.release()
    }

    companion object {
        fun formatTime(ms: Long): String {
            val total = (ms / 1000).coerceAtLeast(0)
            val h = total / 3600; val m = (total % 3600) / 60; val sec = total % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
        }
    }
}

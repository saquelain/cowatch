package com.cowatch.app.call

import android.content.Context
import android.util.Log
import com.cowatch.app.settings.AppSettings
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * One peer-to-peer call: audio + video tracks plus a data channel used for
 * playback sync, chat and reactions.
 */
class WebRtcCall(
    context: Context,
    private val settings: AppSettings,
    private val sendVideo: Boolean,
    private val sendAudio: Boolean,
    private val events: Events,
) {
    interface Events {
        fun onIceCandidate(candidate: IceCandidate)
        fun onConnectionChange(state: PeerConnection.PeerConnectionState)
        fun onDataOpen()
        fun onDataMessage(text: String)
        fun onRemoteVideo(track: VideoTrack?)
        fun onLocalVideo(track: VideoTrack?)
    }

    private val appContext = context.applicationContext
    private val factory = RtcEnvironment.factory(appContext)
    private var peer: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private var capturer: CameraVideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private var videoSource: VideoSource? = null
    private var localVideo: VideoTrack? = null
    private var localAudio: AudioTrack? = null
    private val pendingCandidates = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false

    fun start() {
        val ice = buildList {
            if (settings.stunUrl.isNotBlank()) add(PeerConnection.IceServer.builder(settings.stunUrl).createIceServer())
            if (settings.turnUrl.isNotBlank()) add(
                PeerConnection.IceServer.builder(settings.turnUrl)
                    .setUsername(settings.turnUser)
                    .setPassword(settings.turnPassword)
                    .createIceServer()
            )
        }
        val config = PeerConnection.RTCConfiguration(ice).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        peer = factory.createPeerConnection(config, observer)
        if (sendAudio) {
            localAudio = factory.createAudioTrack("audio0", factory.createAudioSource(MediaConstraints()))
        }
        if (sendVideo) startCamera()
    }

    private fun startCamera() {
        val enumerator = Camera2Enumerator(appContext)
        val name = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull() ?: return
        val cam = enumerator.createCapturer(name, null) ?: return
        val helper = SurfaceTextureHelper.create("CameraThread", RtcEnvironment.eglBase.eglBaseContext)
        val source = factory.createVideoSource(false)
        cam.initialize(helper, appContext, source.capturerObserver)
        cam.startCapture(640, 480, 24)
        capturer = cam
        textureHelper = helper
        videoSource = source
        localVideo = factory.createVideoTrack("video0", source).also { events.onLocalVideo(it) }
    }

    /** Called by the guest: add tracks (or receive-only slots) and produce an offer. */
    suspend fun createOffer(): SessionDescription {
        val pc = requireNotNull(peer)
        attachTrack(pc, localAudio, MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO)
        attachTrack(pc, localVideo, MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO)
        dataChannel = pc.createDataChannel("cowatch", DataChannel.Init().apply { ordered = true })
            .also { registerDataChannel(it) }
        val offer = pc.awaitSdp { obs -> pc.createOffer(obs, MediaConstraints()) }
        pc.awaitSet { obs -> pc.setLocalDescription(obs, offer) }
        return offer
    }

    /** Called by the host when the guest's offer arrives. */
    suspend fun acceptOffer(offer: SessionDescription): SessionDescription {
        val pc = requireNotNull(peer)
        pc.awaitSet { obs -> pc.setRemoteDescription(obs, offer) }
        onRemoteDescriptionSet()
        pc.transceivers.forEach { t ->
            val track: MediaStreamTrack? = when (t.mediaType) {
                MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO -> localAudio
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO -> localVideo
                else -> null
            }
            if (track != null) {
                t.sender.setTrack(track, false)
                t.sender.setStreams(listOf(STREAM_ID))
                t.direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV
            }
        }
        val answer = pc.awaitSdp { obs -> pc.createAnswer(obs, MediaConstraints()) }
        pc.awaitSet { obs -> pc.setLocalDescription(obs, answer) }
        return answer
    }

    suspend fun acceptAnswer(answer: SessionDescription) {
        val pc = requireNotNull(peer)
        pc.awaitSet { obs -> pc.setRemoteDescription(obs, answer) }
        onRemoteDescriptionSet()
    }

    fun addRemoteCandidate(candidate: IceCandidate) {
        synchronized(pendingCandidates) {
            if (!remoteDescriptionSet) {
                pendingCandidates += candidate
                return
            }
        }
        peer?.addIceCandidate(candidate)
    }

    private fun onRemoteDescriptionSet() {
        val queued = synchronized(pendingCandidates) {
            remoteDescriptionSet = true
            pendingCandidates.toList().also { pendingCandidates.clear() }
        }
        queued.forEach { peer?.addIceCandidate(it) }
    }

    private fun attachTrack(pc: PeerConnection, track: MediaStreamTrack?, type: MediaStreamTrack.MediaType) {
        if (track != null) {
            pc.addTrack(track, listOf(STREAM_ID))
        } else {
            // Still ask to *receive* this media so the partner can send theirs.
            pc.addTransceiver(type, RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY))
        }
    }

    fun send(text: String): Boolean {
        val dc = dataChannel ?: return false
        if (dc.state() != DataChannel.State.OPEN) return false
        return dc.send(DataChannel.Buffer(ByteBuffer.wrap(text.toByteArray(Charsets.UTF_8)), false))
    }

    val hasCamera: Boolean get() = localVideo != null
    val hasMic: Boolean get() = localAudio != null

    fun setMicEnabled(on: Boolean) { localAudio?.setEnabled(on) }

    fun setCameraEnabled(on: Boolean) {
        localVideo?.setEnabled(on)
        val cam = capturer ?: return
        runCatching { if (on) cam.startCapture(640, 480, 24) else cam.stopCapture() }
    }

    fun switchCamera() { capturer?.switchCamera(null) }

    fun close() {
        runCatching { dataChannel?.unregisterObserver(); dataChannel?.close() }
        runCatching { capturer?.stopCapture() }
        capturer?.dispose()
        peer?.dispose()
        localVideo?.dispose()
        videoSource?.dispose()
        localAudio?.dispose()
        textureHelper?.dispose()
        dataChannel = null; capturer = null; peer = null; localVideo = null
        videoSource = null; localAudio = null; textureHelper = null
        events.onLocalVideo(null)
        events.onRemoteVideo(null)
    }

    private fun registerDataChannel(dc: DataChannel) {
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit
            override fun onStateChange() {
                if (dc.state() == DataChannel.State.OPEN) events.onDataOpen()
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.get(bytes)
                events.onDataMessage(String(bytes, Charsets.UTF_8))
            }
        })
    }

    private val observer = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) = events.onIceCandidate(candidate)
        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) =
            events.onConnectionChange(newState)
        override fun onDataChannel(dc: DataChannel) {
            dataChannel = dc
            registerDataChannel(dc)
            if (dc.state() == DataChannel.State.OPEN) events.onDataOpen()
        }
        override fun onTrack(transceiver: RtpTransceiver) {
            (transceiver.receiver.track() as? VideoTrack)?.let { events.onRemoteVideo(it) }
        }
        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) = Unit
        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) = Unit
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onRenegotiationNeeded() = Unit
    }

    companion object {
        private const val STREAM_ID = "cowatch"
        private const val TAG = "WebRtcCall"
    }
}

private suspend fun PeerConnection.awaitSdp(block: (SdpObserver) -> Unit): SessionDescription =
    suspendCancellableCoroutine { cont ->
        block(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) { if (cont.isActive) cont.resume(sdp) }
            override fun onCreateFailure(error: String?) {
                if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "createSdp failed"))
            }
            override fun onSetSuccess() = Unit
            override fun onSetFailure(error: String?) = Unit
        })
    }

private suspend fun PeerConnection.awaitSet(block: (SdpObserver) -> Unit) =
    suspendCancellableCoroutine { cont ->
        block(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) = Unit
            override fun onCreateFailure(error: String?) = Unit
            override fun onSetSuccess() { if (cont.isActive) cont.resume(Unit) }
            override fun onSetFailure(error: String?) {
                if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "setSdp failed"))
            }
        })
    }

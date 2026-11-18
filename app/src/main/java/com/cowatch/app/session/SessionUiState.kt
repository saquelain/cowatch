package com.cowatch.app.session

enum class Phase { IDLE, HOSTING, JOINING, CONNECTING, CONNECTED, ENDED }

data class ChatLine(val id: Long, val fromMe: Boolean, val text: String)

data class FloatingReaction(val id: Long, val emoji: String, val fromMe: Boolean, val xFraction: Float)

data class SessionUiState(
    val phase: Phase = Phase.IDLE,
    val isHost: Boolean = false,
    val code: String? = null,
    val codeExpiresAtMs: Long? = null,
    val endedReason: String? = null,
    val error: String? = null,
    val partnerName: String = "Partner",
    val partnerCameraOn: Boolean = false,
    val partnerMicOn: Boolean = false,
    val hasRemoteVideo: Boolean = false,
    val hasLocalVideo: Boolean = false,
    val micOn: Boolean = true,
    val cameraOn: Boolean = true,
    val movieTitle: String? = null,
    val partnerLocalFile: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val chat: List<ChatLine> = emptyList(),
    val unreadChat: Int = 0,
    val reactions: List<FloatingReaction> = emptyList(),
    val toast: String? = null,
)

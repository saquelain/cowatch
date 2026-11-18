package com.cowatch.app.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Everything the two phones say to each other over the WebRTC data channel. */
@Serializable
sealed interface SyncMessage {
    @Serializable @SerialName("hello")
    data class Hello(val name: String, val features: List<String>) : SyncMessage

    /**
     * A movie was opened. [url] is set for web links. For a file on the phone,
     * [streamId]/[size] are set when the sender streams it to the partner;
     * if both are null the partner has to pick their own copy.
     */
    @Serializable @SerialName("load")
    data class Load(
        val title: String,
        val url: String?,
        val streamId: String? = null,
        val size: Long? = null,
    ) : SyncMessage

    /** The movie was closed ("Stop video"). */
    @Serializable @SerialName("unload")
    data object Unload : SyncMessage

    @Serializable @SerialName("play")
    data class Play(val positionMs: Long) : SyncMessage

    @Serializable @SerialName("pause")
    data class Pause(val positionMs: Long) : SyncMessage

    @Serializable @SerialName("seek")
    data class Seek(val positionMs: Long) : SyncMessage

    /** Periodic heartbeat used for drift correction. */
    @Serializable @SerialName("state")
    data class State(val positionMs: Long, val playing: Boolean, val sentAtMs: Long) : SyncMessage

    @Serializable @SerialName("chat")
    data class Chat(val text: String) : SyncMessage

    @Serializable @SerialName("react")
    data class Reaction(val emoji: String) : SyncMessage

    @Serializable @SerialName("media")
    data class MediaState(val cameraOn: Boolean, val micOn: Boolean) : SyncMessage

    @Serializable @SerialName("bye")
    data object Bye : SyncMessage

    companion object {
        private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "t" }
        fun encode(msg: SyncMessage): String = json.encodeToString(serializer(), msg)
        fun decode(text: String): SyncMessage? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
    }
}

package com.cowatch.app.signaling

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Timer
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.concurrent.fixedRateTimer

/**
 * Minimal client for a PeerJS signaling server (the free public cloud server
 * at 0.peerjs.com by default, or a self-hosted `peerjs-server`). The server
 * only relays opaque payloads between peer ids, which is all we need to
 * exchange WebRTC offers/answers/ICE candidates. No accounts, no backend.
 */
class PeerJsSignaling(
    private val host: String,
    val myId: String,
    private val listener: Listener,
    private val client: OkHttpClient = defaultClient,
) {
    interface Listener {
        fun onOpen()
        fun onIdTaken()
        fun onSignal(type: SignalType, src: String, payload: JsonObject)
        /** A message we sent could not be delivered (the other peer is not online). */
        fun onPeerUnavailable(peerId: String)
        fun onError(message: String)
        fun onClosed()
    }

    enum class SignalType { OFFER, ANSWER, CANDIDATE, LEAVE }

    private var socket: WebSocket? = null
    private var heartbeat: Timer? = null
    @Volatile private var closedByUs = false

    fun connect() {
        val token = UUID.randomUUID().toString().take(10)
        val request = Request.Builder().url(buildUrl(host, myId, token)).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) = handle(text)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                stopHeartbeat()
                if (!closedByUs) listener.onError(t.message ?: "Connection to the pairing server failed")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                stopHeartbeat()
                if (!closedByUs) listener.onClosed()
            }
        })
        heartbeat = fixedRateTimer("peerjs-heartbeat", daemon = true, initialDelay = 5_000, period = 5_000) {
            socket?.send("""{"type":"HEARTBEAT"}""")
        }
    }

    fun send(type: SignalType, dst: String, payload: JsonObject) {
        val msg = buildJsonObject {
            put("type", JsonPrimitive(type.name))
            put("dst", JsonPrimitive(dst))
            put("payload", payload)
        }
        socket?.send(msg.toString())
    }

    fun close() {
        closedByUs = true
        stopHeartbeat()
        socket?.close(1000, "bye")
        socket = null
    }

    private fun stopHeartbeat() {
        heartbeat?.cancel()
        heartbeat = null
    }

    internal fun handle(text: String) {
        val msg = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        val type = msg["type"]?.jsonPrimitive?.content ?: return
        val src = msg["src"]?.jsonPrimitive?.content.orEmpty()
        val payload = msg["payload"] as? JsonObject ?: JsonObject(emptyMap())
        when (type) {
            "OPEN" -> listener.onOpen()
            "ID-TAKEN" -> listener.onIdTaken()
            "ERROR" -> listener.onError(payload["msg"]?.jsonPrimitive?.content ?: "Pairing server error")
            "EXPIRE" -> listener.onPeerUnavailable(src)
            "OFFER", "ANSWER", "CANDIDATE", "LEAVE" ->
                listener.onSignal(SignalType.valueOf(type), src, payload)
            else -> Unit // HEARTBEAT echoes etc.
        }
    }

    companion object {
        private val defaultClient by lazy {
            OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
        }

        fun buildUrl(host: String, id: String, token: String): String {
            val trimmed = host.trim().removeSuffix("/")
            val base = when {
                trimmed.startsWith("wss://") || trimmed.startsWith("ws://") -> trimmed
                trimmed.startsWith("https://") -> "wss://" + trimmed.removePrefix("https://")
                trimmed.startsWith("http://") -> "ws://" + trimmed.removePrefix("http://")
                else -> "wss://$trimmed"
            }
            return "$base/peerjs?key=peerjs&id=$id&token=$token&version=1.5.4"
        }
    }
}

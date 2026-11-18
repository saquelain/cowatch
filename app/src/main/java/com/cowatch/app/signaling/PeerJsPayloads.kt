package com.cowatch.app.signaling

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The public PeerJS server validates payloads and disconnects clients that
 * don't use the official PeerJS client's shape: `sdp` must be an object
 * `{type, sdp}`, `candidate` an object, and a `type`/`connectionId` must be
 * present. These helpers build and read exactly that shape.
 */
object PeerJsPayloads {
    data class Sdp(val type: String, val sdp: String, val name: String?)
    data class Candidate(val candidate: String, val sdpMid: String, val sdpMLineIndex: Int)

    fun sdp(type: String, sdp: String, connectionId: String, name: String?): JsonObject = buildJsonObject {
        put("sdp", buildJsonObject {
            put("type", JsonPrimitive(type))
            put("sdp", JsonPrimitive(sdp))
        })
        put("type", JsonPrimitive("media"))
        put("connectionId", JsonPrimitive(connectionId))
        if (name != null) put("metadata", buildJsonObject { put("name", JsonPrimitive(name)) })
    }

    fun candidate(c: Candidate, connectionId: String): JsonObject = buildJsonObject {
        put("candidate", buildJsonObject {
            put("candidate", JsonPrimitive(c.candidate))
            put("sdpMid", JsonPrimitive(c.sdpMid))
            put("sdpMLineIndex", JsonPrimitive(c.sdpMLineIndex))
        })
        put("type", JsonPrimitive("media"))
        put("connectionId", JsonPrimitive(connectionId))
    }

    fun readSdp(payload: JsonObject): Sdp? {
        val obj = payload["sdp"] as? JsonObject ?: return null
        val sdp = obj["sdp"]?.jsonPrimitive?.contentOrNull ?: return null
        val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return null
        val name = (payload["metadata"] as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull
        return Sdp(type, sdp, name?.takeIf { it.isNotBlank() })
    }

    fun readCandidate(payload: JsonObject): Candidate? {
        val obj = payload["candidate"] as? JsonObject ?: return null
        val cand = obj["candidate"]?.jsonPrimitive?.contentOrNull ?: return null
        return Candidate(
            cand,
            obj["sdpMid"]?.jsonPrimitive?.contentOrNull ?: "",
            obj["sdpMLineIndex"]?.jsonPrimitive?.intOrNull ?: 0,
        )
    }

    fun connectionId(payload: JsonObject): String? = payload["connectionId"]?.jsonPrimitive?.contentOrNull

    fun newConnectionId(): String = "mc_" + java.util.UUID.randomUUID().toString().replace("-", "").take(12)
}

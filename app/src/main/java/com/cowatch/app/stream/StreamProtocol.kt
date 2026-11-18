package com.cowatch.app.stream

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer

/**
 * Wire format of the file channel.
 *  - request (text):   {"s": streamId, "r": requestId, "p": position, "n": length}
 *  - response (binary): int32 requestId, int32 status (bytes that follow, or -1 = error), data
 */
object StreamProtocol {
    const val CHUNK = 64 * 1024
    const val HEADER = 8
    const val ERROR = -1

    @Serializable
    data class Request(val s: String, val r: Int, val p: Long, val n: Int)

    private val json = Json { ignoreUnknownKeys = true }
    fun encode(r: Request): String = json.encodeToString(Request.serializer(), r)
    fun decode(text: String): Request? = runCatching { json.decodeFromString(Request.serializer(), text) }.getOrNull()

    fun response(requestId: Int, data: ByteArray?, length: Int = data?.size ?: 0): ByteBuffer {
        val buf = ByteBuffer.allocate(HEADER + if (data != null) length else 0)
        buf.putInt(requestId)
        buf.putInt(if (data != null) length else ERROR)
        if (data != null) buf.put(data, 0, length)
        buf.flip()
        return buf
    }
}

/** Something we can read byte ranges from: a file on this phone. */
interface RangeSource : AutoCloseable {
    val size: Long
    /** Reads up to [length] bytes at [position] into [into] at [offset]; returns bytes read (-1 at end). */
    fun read(position: Long, into: ByteArray, offset: Int, length: Int): Int
}

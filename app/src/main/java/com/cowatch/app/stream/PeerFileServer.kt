package com.cowatch.app.stream

import java.nio.ByteBuffer
import java.util.concurrent.Executors

/**
 * Answers the partner's byte-range requests for the video we opened from
 * this phone. Reads happen on one background thread, in request order.
 */
class PeerFileServer(
    private val send: (ByteBuffer) -> Boolean,
    private val bufferedAmount: () -> Long,
) {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "peer-file-server").apply { isDaemon = true } }
    @Volatile private var current: Pair<String, RangeSource>? = null

    /** Start serving [source] under [streamId]; the previous file stops being served. */
    fun share(streamId: String, source: RangeSource) {
        val old = current
        current = streamId to source
        executor.execute { runCatching { old?.second?.close() } }
    }

    fun stop() {
        val old = current
        current = null
        executor.execute { runCatching { old?.second?.close() } }
    }

    fun onRequest(text: String) {
        val req = StreamProtocol.decode(text) ?: return
        executor.execute { serve(req) }
    }

    private fun serve(req: StreamProtocol.Request) {
        val (id, source) = current ?: return reply(req.r, null)
        if (id != req.s || req.p < 0 || req.n <= 0) return reply(req.r, null)
        val want = minOf(req.n, StreamProtocol.CHUNK).toLong().coerceAtMost(source.size - req.p).toInt()
        if (want <= 0) return reply(req.r, ByteArray(0))
        val buf = ByteArray(want)
        var filled = 0
        try {
            while (filled < want) {
                val n = source.read(req.p + filled, buf, filled, want - filled)
                if (n <= 0) break
                filled += n
            }
        } catch (e: Exception) {
            return reply(req.r, null)
        }
        // Back-pressure: don't let the WebRTC send queue grow without bound.
        var waited = 0
        while (bufferedAmount() > MAX_BUFFERED && waited < 10_000) { Thread.sleep(5); waited += 5 }
        send(StreamProtocol.response(req.r, buf, filled))
    }

    private fun reply(requestId: Int, data: ByteArray?) { send(StreamProtocol.response(requestId, data)) }

    fun close() {
        stop()
        executor.shutdown()
    }

    companion object {
        const val MAX_BUFFERED = 4L * 1024 * 1024
    }
}

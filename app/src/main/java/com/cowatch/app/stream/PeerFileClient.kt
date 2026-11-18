package com.cowatch.app.stream

import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Sends byte-range requests to the partner and matches up the answers. */
class PeerFileClient(private val sendText: (String) -> Boolean) {
    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableFuture<ByteArray>>()

    fun request(streamId: String, position: Long, length: Int): Pair<Int, CompletableFuture<ByteArray>> {
        val id = nextId.getAndIncrement()
        val future = CompletableFuture<ByteArray>()
        pending[id] = future
        if (!sendText(StreamProtocol.encode(StreamProtocol.Request(streamId, id, position, length)))) {
            pending.remove(id)
            future.completeExceptionally(IOException("Not connected to your partner"))
        }
        return id to future
    }

    fun cancel(id: Int) { pending.remove(id)?.cancel(false) }

    fun onResponse(buf: ByteBuffer) {
        if (buf.remaining() < StreamProtocol.HEADER) return
        val id = buf.int
        val status = buf.int
        val future = pending.remove(id) ?: return
        if (status < 0) {
            future.completeExceptionally(IOException("Your partner stopped sharing this video"))
        } else {
            val data = ByteArray(minOf(status, buf.remaining()))
            buf.get(data)
            future.complete(data)
        }
    }

    fun failAll() {
        val all = pending.values.toList()
        pending.clear()
        all.forEach { it.completeExceptionally(IOException("Disconnected from your partner")) }
    }
}

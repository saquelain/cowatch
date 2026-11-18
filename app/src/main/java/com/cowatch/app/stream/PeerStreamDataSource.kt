package com.cowatch.app.stream

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import java.io.IOException
import java.io.InterruptedIOException
import java.util.ArrayDeque
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * ExoPlayer data source that reads a video living on the partner's phone,
 * keeping a window of chunk requests in flight so playback stays smooth.
 */
@UnstableApi
class PeerStreamDataSource(
    private val client: PeerFileClient,
    private val streamId: String,
    private val totalSize: Long,
) : BaseDataSource(/* isNetwork = */ true) {

    private var uri: Uri? = null
    private var opened = false
    private var bytesRemaining = 0L
    private var nextRequestPos = 0L
    private var endPos = 0L
    private val window = ArrayDeque<Pair<Int, CompletableFuture<ByteArray>>>()
    private var current: ByteArray? = null
    private var currentOffset = 0

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        if (dataSpec.position > totalSize) {
            throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        }
        val available = totalSize - dataSpec.position
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) minOf(dataSpec.length, available) else available
        nextRequestPos = dataSpec.position
        endPos = dataSpec.position + bytesRemaining
        fill()
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    private fun fill() {
        while (window.size < WINDOW && nextRequestPos < endPos) {
            val n = minOf(StreamProtocol.CHUNK.toLong(), endPos - nextRequestPos).toInt()
            window.addLast(client.request(streamId, nextRequestPos, n))
            nextRequestPos += n
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        var chunk = current
        if (chunk == null || currentOffset >= chunk.size) {
            val (_, future) = window.pollFirst() ?: return C.RESULT_END_OF_INPUT
            chunk = try {
                future.get(TIMEOUT_S, TimeUnit.SECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException()
            } catch (e: TimeoutException) {
                throw IOException("Your partner's phone is not sending the video", e)
            } catch (e: ExecutionException) {
                throw (e.cause as? IOException) ?: IOException(e.cause)
            }
            if (chunk.isEmpty()) return C.RESULT_END_OF_INPUT
            current = chunk
            currentOffset = 0
            fill()
        }
        val n = minOf(length, chunk.size - currentOffset, minOf(bytesRemaining, Int.MAX_VALUE.toLong()).toInt())
        System.arraycopy(chunk, currentOffset, buffer, offset, n)
        currentOffset += n
        bytesRemaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        window.forEach { client.cancel(it.first) }
        window.clear()
        current = null
        uri = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    companion object {
        /** 12 x 64 KB = 768 KB in flight. */
        const val WINDOW = 12
        const val TIMEOUT_S = 20L
        const val SCHEME = "cowatch-stream"

        fun uriFor(streamId: String, title: String): Uri =
            Uri.parse("$SCHEME://peer/$streamId/${Uri.encode(title)}")

        fun streamIdOf(uri: Uri): String? = if (uri.scheme == SCHEME) uri.pathSegments.firstOrNull() else null
    }
}

package com.cowatch.app.stream

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException

/** Sends `cowatch-stream://` URIs to the partner stream, everything else to ExoPlayer's default. */
@UnstableApi
class RoutingDataSourceFactory(
    context: Context,
    private val streamSource: (streamId: String) -> DataSource?,
) : DataSource.Factory {
    private val default = DefaultDataSource.Factory(context)

    override fun createDataSource(): DataSource = Routing(default.createDataSource())

    private inner class Routing(private val fallback: DataSource) : DataSource {
        private var active: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) = fallback.addTransferListener(transferListener)

        override fun open(dataSpec: DataSpec): Long {
            val streamId = PeerStreamDataSource.streamIdOf(dataSpec.uri)
            val target = if (streamId != null) {
                streamSource(streamId) ?: throw IOException("This video is no longer being shared")
            } else fallback
            active = target
            return target.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            active?.read(buffer, offset, length) ?: throw IOException("Not open")

        override fun getUri(): Uri? = active?.uri

        override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

        override fun close() {
            try { active?.close() } finally { active = null }
        }
    }
}

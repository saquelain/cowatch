package com.cowatch.app.stream

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Random access to a video picked from the phone (content:// or file://). */
class ContentRangeSource private constructor(
    private val pfd: ParcelFileDescriptor,
    override val size: Long,
) : RangeSource {
    private val channel: FileChannel = FileInputStream(pfd.fileDescriptor).channel

    override fun read(position: Long, into: ByteArray, offset: Int, length: Int): Int =
        channel.read(ByteBuffer.wrap(into, offset, length), position)

    override fun close() {
        runCatching { channel.close() }
        runCatching { pfd.close() }
    }

    companion object {
        /** Returns null if the file can't be opened for random access or its size is unknown. */
        fun open(context: Context, uri: Uri): ContentRangeSource? = runCatching {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            var size = pfd.statSize
            if (size <= 0) {
                size = context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
                } ?: -1L
            }
            if (size <= 0) { pfd.close(); return null }
            ContentRangeSource(pfd, size)
        }.getOrNull()
    }
}

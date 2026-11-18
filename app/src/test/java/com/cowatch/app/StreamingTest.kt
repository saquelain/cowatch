package com.cowatch.app

import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import com.cowatch.app.stream.PeerFileClient
import com.cowatch.app.stream.PeerFileServer
import com.cowatch.app.stream.PeerStreamDataSource
import com.cowatch.app.stream.RangeSource
import com.cowatch.app.sync.SyncMessage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.random.Random

/** Streams a "video" from one phone's server to the other phone's player data source. */
@RunWith(RobolectricTestRunner::class)
class StreamingTest {
    private val movie = Random(7).nextBytes(3 * 1024 * 1024 + 12_345) // not a multiple of the chunk size

    private class BytesSource(private val bytes: ByteArray) : RangeSource {
        var closed = false
        override val size get() = bytes.size.toLong()
        override fun read(position: Long, into: ByteArray, offset: Int, length: Int): Int {
            if (position >= bytes.size) return -1
            val n = minOf(length.toLong(), bytes.size - position).toInt()
            System.arraycopy(bytes, position.toInt(), into, offset, n)
            return n
        }
        override fun close() { closed = true }
    }

    /** Two ends of a fake data channel; messages hop threads like the real one. */
    private val wire = Executors.newSingleThreadExecutor()
    private lateinit var server: PeerFileServer
    private val client = PeerFileClient { text -> wire.execute { server.onRequest(text) }; true }
    init {
        server = PeerFileServer(send = { buf -> val copy = ByteBuffer.allocate(buf.remaining()).put(buf).flip() as ByteBuffer; wire.execute { client.onResponse(copy) }; true }, bufferedAmount = { 0 })
    }

    private fun readAll(ds: PeerStreamDataSource, spec: DataSpec): ByteArray {
        val out = ByteArrayOutputStream()
        val len = ds.open(spec)
        val buf = ByteArray(10_000)
        while (true) {
            val n = ds.read(buf, 0, buf.size)
            if (n == C.RESULT_END_OF_INPUT) break
            out.write(buf, 0, n)
        }
        ds.close()
        assertEquals(len, out.size().toLong())
        return out.toByteArray()
    }

    @Test fun wholeFileArrivesIntact() {
        server.share("s1", BytesSource(movie))
        val ds = PeerStreamDataSource(client, "s1", movie.size.toLong())
        val got = readAll(ds, DataSpec(PeerStreamDataSource.uriFor("s1", "Movie.mp4")))
        assertArrayEquals(movie, got)
    }

    @Test fun seekingReadsTheRightBytes() {
        server.share("s1", BytesSource(movie))
        val ds = PeerStreamDataSource(client, "s1", movie.size.toLong())
        val uri = PeerStreamDataSource.uriFor("s1", "Movie.mp4")
        // Like ExoPlayer: jump to the end (MP4 index), then the middle, with and without a length.
        val tail = readAll(ds, DataSpec.Builder().setUri(uri).setPosition(movie.size - 5_000L).build())
        assertArrayEquals(movie.copyOfRange(movie.size - 5_000, movie.size), tail)
        val mid = readAll(ds, DataSpec.Builder().setUri(uri).setPosition(1_000_001).setLength(200_000).build())
        assertArrayEquals(movie.copyOfRange(1_000_001, 1_200_001), mid)
    }

    @Test fun stoppedSharingFailsCleanlyInsteadOfHanging() {
        val src = BytesSource(movie)
        server.share("s1", src)
        server.stop()
        val ds = PeerStreamDataSource(client, "s1", movie.size.toLong())
        try {
            readAll(ds, DataSpec(PeerStreamDataSource.uriFor("s1", "Movie.mp4")))
            fail("expected an error")
        } catch (e: IOException) {
            assertTrue(e.message!!, e.message!!.contains("stopped sharing"))
        }
        Thread.sleep(100)
        assertTrue(src.closed)
    }

    @Test fun oldStreamIdIsRejectedAfterSwitchingVideo() {
        server.share("s1", BytesSource(movie))
        server.share("s2", BytesSource(movie))
        val ds = PeerStreamDataSource(client, "s1", movie.size.toLong())
        try { readAll(ds, DataSpec(PeerStreamDataSource.uriFor("s1", "x.mp4"))); fail() } catch (_: IOException) {}
    }

    @Test fun streamUriRoundTrips() {
        val uri = PeerStreamDataSource.uriFor("abc123", "My Movie (2020).mkv")
        assertEquals("abc123", PeerStreamDataSource.streamIdOf(uri))
        assertEquals(null, PeerStreamDataSource.streamIdOf(android.net.Uri.parse("https://x/y.mp4")))
    }

    @Test fun loadMessageCarriesStreamInfoAndStaysCompatible() {
        val m = SyncMessage.Load("Movie.mp4", null, "abc", 123L)
        assertEquals(m, SyncMessage.decode(SyncMessage.encode(m)))
        assertEquals(SyncMessage.Load("x", "https://a"), SyncMessage.decode("""{"t":"load","title":"x","url":"https://a"}"""))
        assertEquals(SyncMessage.Unload, SyncMessage.decode(SyncMessage.encode(SyncMessage.Unload)))
    }
}

/** The real ExoPlayer opens a real MP4 (index at the end) that lives on the "partner's phone". */
@RunWith(RobolectricTestRunner::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class StreamingPlayerTest {
    @Test fun exoPlayerPlaysAndSeeksAStreamedMp4() {
        val clip = javaClass.classLoader!!.getResourceAsStream("clip.mp4").readBytes()
        val wire = Executors.newSingleThreadExecutor()
        lateinit var server: PeerFileServer
        val client = PeerFileClient { text -> wire.execute { server.onRequest(text) }; true }
        server = PeerFileServer(send = { buf -> val c = ByteBuffer.allocate(buf.remaining()).put(buf).flip() as ByteBuffer; wire.execute { client.onResponse(c) }; true }, bufferedAmount = { 0 })
        server.share("s9", object : RangeSource {
            override val size = clip.size.toLong()
            override fun read(position: Long, into: ByteArray, offset: Int, length: Int): Int {
                if (position >= clip.size) return -1
                val n = minOf(length.toLong(), clip.size - position).toInt()
                System.arraycopy(clip, position.toInt(), into, offset, n); return n
            }
            override fun close() {}
        })

        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        var opened = 0
        val player = androidx.media3.test.utils.TestExoPlayerBuilder(ctx)
            // Fake renderers (no decoders in the JVM) still pull every audio/video sample through the stream.
            .setRenderers(
                androidx.media3.test.utils.FakeRenderer(C.TRACK_TYPE_VIDEO),
                androidx.media3.test.utils.FakeRenderer(C.TRACK_TYPE_AUDIO),
            )
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    com.cowatch.app.stream.RoutingDataSourceFactory(ctx) { id ->
                        opened++
                        if (id == "s9") PeerStreamDataSource(client, id, clip.size.toLong()) else null
                    }
                )
            ).build()
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(PeerStreamDataSource.uriFor("s9", "clip.mp4")))
        player.prepare()
        androidx.media3.test.utils.robolectric.TestPlayerRunHelper.run(player).untilState(androidx.media3.common.Player.STATE_READY)
        assertEquals(null, player.playerError)
        assertTrue("duration ${player.duration}", player.duration in 4_900..5_100)
        val types = player.currentTracks.groups.map { it.type }.toSet()
        assertTrue("tracks $types", types.containsAll(setOf(C.TRACK_TYPE_VIDEO, C.TRACK_TYPE_AUDIO)))

        player.seekTo(3_500)
        androidx.media3.test.utils.robolectric.TestPlayerRunHelper.run(player).untilPendingCommandsAreFullyHandled()
        androidx.media3.test.utils.robolectric.TestPlayerRunHelper.run(player).untilState(androidx.media3.common.Player.STATE_READY)
        assertEquals(null, player.playerError)
        assertTrue(player.currentPosition >= 3_000)
        assertTrue("stream was used", opened > 0)
        player.play()
        androidx.media3.test.utils.robolectric.TestPlayerRunHelper.run(player).untilState(androidx.media3.common.Player.STATE_ENDED)
        assertEquals(null, player.playerError)
        player.release()
    }
}

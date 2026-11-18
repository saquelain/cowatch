package com.cowatch.app

import com.cowatch.app.pairing.PairingCode
import com.cowatch.app.settings.AppSettings
import com.cowatch.app.settings.Feature
import com.cowatch.app.signaling.PeerJsPayloads
import com.cowatch.app.signaling.PeerJsSignaling
import com.cowatch.app.sync.DriftCorrector
import com.cowatch.app.sync.SyncMessage
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingCodeTest {
    @Test fun generatedCodesAreValidAndUnambiguous() {
        repeat(500) {
            val c = PairingCode.generate()
            assertTrue(c, PairingCode.isValid(c))
            assertFalse(c.any { it in "01OIL" })
        }
    }

    @Test fun normalizeAcceptsHowPeopleType() {
        assertEquals("K7PQ2M", PairingCode.normalize(" k7p-q2m "))
        assertTrue(PairingCode.isValid(PairingCode.normalize("k7p q2m")))
        assertFalse(PairingCode.isValid("K7P"))
        assertFalse(PairingCode.isValid("K0PQ2M"))
    }

    @Test fun formatAndDeepLinkRoundTrip() {
        assertEquals("K7P-Q2M", PairingCode.format("K7PQ2M"))
        assertEquals("K7PQ2M", PairingCode.fromDeepLink(PairingCode.deepLink("K7PQ2M")))
        assertNull(PairingCode.fromDeepLink("https://example.com/K7PQ2M"))
    }

    @Test fun hostAndGuestIdsNeverCollide() {
        val host = PairingCode.hostPeerId("K7PQ2M")
        assertEquals("cowatch-v1-k7pq2m", host)
        assertTrue(PairingCode.guestPeerId().startsWith("cowatch-v1-guest-"))
    }
}

class SyncMessageTest {
    @Test fun everyMessageRoundTrips() {
        val all = listOf(
            SyncMessage.Hello("Sam", listOf("VIDEO_CALL")),
            SyncMessage.Load("Movie", "https://x/y.mp4"),
            SyncMessage.Load("local.mkv", null),
            SyncMessage.Play(1234), SyncMessage.Pause(99), SyncMessage.Seek(60_000),
            SyncMessage.State(5_000, true, 42),
            SyncMessage.Chat("hi ❤️"), SyncMessage.Reaction("😂"),
            SyncMessage.MediaState(cameraOn = false, micOn = true),
            SyncMessage.Bye,
        )
        all.forEach { assertEquals(it, SyncMessage.decode(SyncMessage.encode(it))) }
    }

    @Test fun unknownOrBrokenMessagesAreIgnored() {
        assertNull(SyncMessage.decode("not json"))
        assertNull(SyncMessage.decode("""{"t":"future-thing"}"""))
        assertNotNull(SyncMessage.decode("""{"t":"play","positionMs":5,"extra":1}"""))
    }
}

class DriftCorrectorTest {
    private fun state(pos: Long, playing: Boolean = true, sent: Long = 1_000) = SyncMessage.State(pos, playing, sent)

    @Test fun hostNeverCorrects() {
        assertNull(DriftCorrector.correction(true, 0, false, state(100_000), 1_000))
    }

    @Test fun smallDriftIsIgnored() {
        assertNull(DriftCorrector.correction(false, 10_500, true, state(10_000), 1_200))
    }

    @Test fun bigDriftSeeksIncludingTransitTime() {
        val fix = DriftCorrector.correction(false, 5_000, true, state(10_000, sent = 1_000), 1_300)!!
        assertEquals(10_300L, fix.seekToMs)
        assertNull(fix.play)
    }

    @Test fun playStateIsAligned() {
        val fix = DriftCorrector.correction(false, 10_000, true, state(10_000, playing = false), 1_000)!!
        assertEquals(false, fix.play)
        assertNull(fix.seekToMs)
    }
}

class SettingsTest {
    @Test fun everyFeatureHasASwitchAndIsOnByDefault() {
        val s = AppSettings()
        Feature.entries.forEach { assertTrue(it.name, s.enabled(it)) }
        assertEquals(Feature.entries.size, Feature.entries.map { it.key }.toSet().size)
    }

    @Test fun childFeaturesFollowTheirParent() {
        val s = AppSettings().with(Feature.PLAYBACK_SYNC, false).with(Feature.VIDEO_CALL, false)
        assertFalse(s.enabled(Feature.DRIFT_CORRECTION))
        assertFalse(s.enabled(Feature.SHARE_LINKS))
        assertFalse(s.enabled(Feature.SELF_PREVIEW))
        assertTrue(s.isOn(Feature.DRIFT_CORRECTION))
    }
}

class PeerJsSignalingTest {
    @Test fun buildsUrlsForHostsAndSelfHostedServers() {
        assertEquals(
            "wss://0.peerjs.com/peerjs?key=peerjs&id=me&token=t&version=1.5.4",
            PeerJsSignaling.buildUrl("0.peerjs.com", "me", "t"),
        )
        assertEquals(
            "ws://192.168.1.5:9000/myapp/peerjs?key=peerjs&id=me&token=t&version=1.5.4",
            PeerJsSignaling.buildUrl("http://192.168.1.5:9000/myapp/", "me", "t"),
        )
    }

    @Test fun parsesServerMessages() {
        val events = mutableListOf<String>()
        val sig = PeerJsSignaling("h", "me", object : PeerJsSignaling.Listener {
            override fun onOpen() { events += "open" }
            override fun onIdTaken() { events += "taken" }
            override fun onSignal(type: PeerJsSignaling.SignalType, src: String, payload: JsonObject) {
                events += "$type:$src:${payload["sdp"]}"
            }
            override fun onPeerUnavailable(peerId: String) { events += "gone:$peerId" }
            override fun onError(message: String) { events += "err:$message" }
            override fun onClosed() { events += "closed" }
        })
        sig.handle("""{"type":"OPEN"}""")
        sig.handle("""{"type":"OFFER","src":"guest","payload":{"sdp":"v=0"}}""")
        sig.handle("""{"type":"EXPIRE","src":"host"}""")
        sig.handle("""{"type":"ID-TAKEN"}""")
        sig.handle("""{"type":"ERROR","payload":{"msg":"bad key"}}""")
        sig.handle("garbage")
        assertEquals(listOf("open", "OFFER:guest:\"v=0\"", "gone:host", "taken", "err:bad key"), events)
    }
}

class PeerJsPayloadsTest {
    /** The public server kicks clients whose payloads don't look like the official PeerJS client's. */
    @Test fun sdpUsesPeerJsShape() {
        val p = PeerJsPayloads.sdp("offer", "v=0", "mc_1", "Sam")
        assertEquals("""{"sdp":{"type":"offer","sdp":"v=0"},"type":"media","connectionId":"mc_1","metadata":{"name":"Sam"}}""", p.toString())
        assertEquals(PeerJsPayloads.Sdp("offer", "v=0", "Sam"), PeerJsPayloads.readSdp(p))
        assertEquals("mc_1", PeerJsPayloads.connectionId(p))
    }

    @Test fun candidateRoundTrips() {
        val c = PeerJsPayloads.Candidate("candidate:1", "0", 1)
        val p = PeerJsPayloads.candidate(c, "mc_1")
        assertTrue(p["candidate"] is JsonObject)
        assertEquals(c, PeerJsPayloads.readCandidate(p))
    }

    @Test fun oldFlatFormatIsRejected() {
        val flat = kotlinx.serialization.json.buildJsonObject { put("sdp", kotlinx.serialization.json.JsonPrimitive("v=0")) }
        assertNull(PeerJsPayloads.readSdp(flat))
    }
}

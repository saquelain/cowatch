package com.cowatch.app

import com.cowatch.app.pairing.PairingCode
import com.cowatch.app.signaling.PeerJsPayloads
import com.cowatch.app.signaling.PeerJsSignaling
import com.cowatch.app.signaling.PeerJsSignaling.SignalType
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Replays the app's pairing exchange against the real public PeerJS server.
 * Skipped by default; run with LIVE_SIGNALING=1.
 */
class LiveSignalingTest {
    @Test fun fullPairingExchangeSurvivesOnPublicServer() {
        assumeTrue(System.getenv("LIVE_SIGNALING") != null)
        val client = okhttp3.OkHttpClient.Builder().apply {
            System.getenv("HTTPS_PROXY")?.let { java.net.URI(it) }?.let {
                proxy(java.net.Proxy(java.net.Proxy.Type.HTTP, java.net.InetSocketAddress(it.host, it.port)))
            }
        }.build()
        val log = Collections.synchronizedList(mutableListOf<String>())
        val code = PairingCode.generate()
        val hostId = PairingCode.hostPeerId(code)
        val guestId = PairingCode.guestPeerId()
        val connId = PeerJsPayloads.newConnectionId()
        val fakeSdp = "v=0\r\n" + "a=candidate:1 1 udp 2122260223 192.168.1.2 50000 typ host\r\n".repeat(60)
        val cand = PeerJsPayloads.Candidate("candidate:1 1 udp 2122260223 192.168.1.2 50000 typ host", "0", 0)
        val hostOpen = CountDownLatch(1); val guestOpen = CountDownLatch(1)
        val gotAnswer = CountDownLatch(1); val hostGotCandidate = CountDownLatch(1); val guestGotCandidate = CountDownLatch(1)
        var hostSawName: String? = null
        lateinit var host: PeerJsSignaling

        fun listener(name: String, onOpen: () -> Unit, onSig: (SignalType, String, JsonObject) -> Unit) = object : PeerJsSignaling.Listener {
            override fun onOpen() { log += "$name OPEN"; onOpen() }
            override fun onIdTaken() { log += "$name ID-TAKEN" }
            override fun onSignal(type: SignalType, src: String, payload: JsonObject) { log += "$name got $type"; onSig(type, src, payload) }
            override fun onPeerUnavailable(peerId: String) { log += "$name EXPIRE" }
            override fun onError(message: String) { log += "$name ERROR $message" }
            override fun onClosed() { log += "$name CLOSED" }
        }
        host = PeerJsSignaling("0.peerjs.com", hostId, listener("host", { hostOpen.countDown() }) { t, src, p ->
            when (t) {
                SignalType.OFFER -> {
                    val offer = PeerJsPayloads.readSdp(p)!!
                    hostSawName = offer.name
                    host.send(SignalType.ANSWER, src, PeerJsPayloads.sdp("answer", fakeSdp, PeerJsPayloads.connectionId(p)!!, "Host"))
                    host.send(SignalType.CANDIDATE, src, PeerJsPayloads.candidate(cand, connId))
                }
                SignalType.CANDIDATE -> if (PeerJsPayloads.readCandidate(p) == cand) hostGotCandidate.countDown()
                else -> Unit
            }
        }, client)
        host.connect()
        assertTrue("host open; log=$log", hostOpen.await(15, TimeUnit.SECONDS))

        val guest = PeerJsSignaling("0.peerjs.com", guestId, listener("guest", { guestOpen.countDown() }) { t, _, p ->
            when (t) {
                SignalType.ANSWER -> if (PeerJsPayloads.readSdp(p)?.sdp == fakeSdp) gotAnswer.countDown()
                SignalType.CANDIDATE -> if (PeerJsPayloads.readCandidate(p) == cand) guestGotCandidate.countDown()
                else -> Unit
            }
        }, client)
        guest.connect()
        assertTrue("guest open; log=$log", guestOpen.await(15, TimeUnit.SECONDS))
        guest.send(SignalType.OFFER, hostId, PeerJsPayloads.sdp("offer", fakeSdp, connId, "Guest"))
        guest.send(SignalType.CANDIDATE, hostId, PeerJsPayloads.candidate(cand, connId))

        assertTrue("answer; log=$log", gotAnswer.await(15, TimeUnit.SECONDS))
        assertTrue("host candidate; log=$log", hostGotCandidate.await(10, TimeUnit.SECONDS))
        assertTrue("guest candidate; log=$log", guestGotCandidate.await(10, TimeUnit.SECONDS))
        assertEquals("Guest", hostSawName)
        Thread.sleep(11_000) // both stay connected through heartbeats
        assertTrue("no errors; log=$log", log.none { "ERROR" in it || "CLOSED" in it })
        println("LOG: " + log.joinToString(" | "))
        guest.close(); host.close()
    }
}

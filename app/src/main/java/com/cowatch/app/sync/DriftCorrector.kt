package com.cowatch.app.sync

import kotlin.math.abs

/**
 * Decides whether to nudge local playback after receiving the partner's
 * [SyncMessage.State]. Pure logic so it is unit-testable.
 */
object DriftCorrector {
    const val THRESHOLD_MS = 1_500L
    /** Only the host is the source of truth, so both sides never chase each other. */
    fun correction(
        isHost: Boolean,
        localPositionMs: Long,
        localPlaying: Boolean,
        remote: SyncMessage.State,
        nowMs: Long,
    ): Correction? {
        if (isHost) return null
        val transit = (nowMs - remote.sentAtMs).coerceIn(0, 2_000)
        val expected = if (remote.playing) remote.positionMs + transit else remote.positionMs
        val playStateFix = when {
            remote.playing && !localPlaying -> true
            !remote.playing && localPlaying -> false
            else -> null
        }
        val seekTo = expected.takeIf { abs(it - localPositionMs) > THRESHOLD_MS }
        if (seekTo == null && playStateFix == null) return null
        return Correction(seekTo, playStateFix)
    }

    data class Correction(val seekToMs: Long?, val play: Boolean?)
}

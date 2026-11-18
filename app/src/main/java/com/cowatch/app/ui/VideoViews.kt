package com.cowatch.app.ui

import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.cowatch.app.R
import com.cowatch.app.call.RtcEnvironment
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun MoviePlayer(player: Player, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView).also { it.player = player }
        },
        update = { it.player = player },
        onRelease = { it.player = null },
    )
}

@Composable
fun CallVideo(track: VideoTrack?, mirror: Boolean, overlay: Boolean, modifier: Modifier) {
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(RtcEnvironment.eglBase.eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
                setMirror(mirror)
                if (overlay) setZOrderMediaOverlay(true)
                renderer = this
            }
        },
        update = { it.setMirror(mirror) },
        onRelease = { it.release() },
    )
    val r = renderer
    DisposableEffect(track, r) {
        if (track != null && r != null) runCatching { track.addSink(r) }
        onDispose { if (track != null && r != null) runCatching { track.removeSink(r) } }
    }
}

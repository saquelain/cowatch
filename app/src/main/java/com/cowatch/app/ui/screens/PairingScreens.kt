package com.cowatch.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cowatch.app.pairing.PairingCode
import com.cowatch.app.ui.theme.BrandGradient
import com.cowatch.app.ui.theme.CodeStyle
import com.cowatch.app.ui.theme.Muted

@Composable
private fun CenteredPage(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

@Composable
private fun PulsingHeart(animate: Boolean = true) {
    val scale = if (animate) {
        val t = rememberInfiniteTransition(label = "pulse")
        val s by t.animateFloat(1f, 1.12f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "s")
        s
    } else 1f
    Box(
        Modifier.size(88.dp).scale(scale).clip(CircleShape).background(BrandGradient),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.size(42.dp)) }
}

@Composable
fun HostWaitingScreen(
    code: String,
    secondsLeft: Long?,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onCancel: () -> Unit,
    animate: Boolean = true,
) = CenteredPage {
    PulsingHeart(animate)
    Spacer(Modifier.height(28.dp))
    Text("Your one-time code", color = Muted)
    Spacer(Modifier.height(8.dp))
    Box(
        Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 28.dp, vertical = 14.dp)
    ) { Text(PairingCode.format(code), style = CodeStyle) }
    Spacer(Modifier.height(12.dp))
    Text(
        "Send this to your partner. It works once — as soon as they join, it's gone.",
        color = Muted, textAlign = TextAlign.Center,
    )
    if (secondsLeft != null) {
        Spacer(Modifier.height(6.dp))
        Text("Expires in %d:%02d".format(secondsLeft / 60, secondsLeft % 60), color = MaterialTheme.colorScheme.primary)
    }
    Spacer(Modifier.height(28.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onShare, shape = RoundedCornerShape(16.dp), modifier = Modifier.height(52.dp)) {
            Icon(Icons.Filled.Share, null); Text("  Share code")
        }
        OutlinedButton(onClick = onCopy, shape = RoundedCornerShape(16.dp), modifier = Modifier.height(52.dp)) {
            Icon(Icons.Filled.ContentCopy, null); Text("  Copy")
        }
    }
    Spacer(Modifier.height(36.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (animate) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Text("  Waiting for your partner…", color = Muted)
    }
    Spacer(Modifier.height(12.dp))
    TextButton(onClick = onCancel) { Text("Cancel") }
}

@Composable
fun ConnectingScreen(message: String, onCancel: () -> Unit, animate: Boolean = true) = CenteredPage {
    PulsingHeart(animate)
    Spacer(Modifier.height(28.dp))
    Text(message, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text("Setting up a private, direct connection between your phones.", color = Muted, textAlign = TextAlign.Center)
    Spacer(Modifier.height(28.dp))
    if (animate) CircularProgressIndicator()
    Spacer(Modifier.height(20.dp))
    TextButton(onClick = onCancel) { Text("Cancel") }
}

@Composable
fun EndedScreen(reason: String, onDone: () -> Unit) = CenteredPage {
    Box(
        Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Filled.HeartBroken, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp)) }
    Spacer(Modifier.height(24.dp))
    Text("Watch party ended", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(8.dp))
    Text(reason, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(28.dp))
    Button(onClick = onDone, shape = RoundedCornerShape(16.dp), modifier = Modifier.height(52.dp)) { Text("Back home") }
}

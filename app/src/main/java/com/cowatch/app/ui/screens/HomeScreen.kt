package com.cowatch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cowatch.app.pairing.PairingCode
import com.cowatch.app.settings.AppSettings
import com.cowatch.app.settings.Feature
import com.cowatch.app.ui.theme.BrandGradient
import com.cowatch.app.ui.theme.Muted

@Composable
fun HomeScreen(
    settings: AppSettings,
    error: String?,
    initialCode: String = "",
    onHost: () -> Unit,
    onJoin: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var code by rememberSaveable(initialCode) { mutableStateOf(initialCode) }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(BrandGradient),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Favorite, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            Text("CoWatch", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 10.dp).weight(1f))
            IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") }
        }

        Spacer(Modifier.height(28.dp))
        Text("Movie night,\ntogether.", style = MaterialTheme.typography.displaySmall, lineHeight = 44.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "Even when you're apart. Share a one-time code with your partner, " +
                "press play at the same moment and see each other while you watch.",
            color = Muted,
        )

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (settings.enabled(Feature.VIDEO_CALL)) Pill(Icons.Filled.Videocam, "Video call")
            if (settings.enabled(Feature.PLAYBACK_SYNC)) Pill(Icons.Filled.Sync, "Synced play")
            if (settings.enabled(Feature.TEXT_CHAT)) Pill(Icons.Filled.Chat, "Chat")
        }

        Spacer(Modifier.height(24.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Start a watch party", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Get a code to send to your partner.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onHost,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Filled.Movie, null)
                    Text("  Create a code")
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Got a code?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Enter the code your partner sent you.", color = Muted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = PairingCode.normalize(it) },
                    placeholder = { Text("ABC-123", color = Muted) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(letterSpacing = 4.sp, textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onJoin(code) }),
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onJoin(code) },
                    enabled = code.length == PairingCode.LENGTH,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Join partner") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Pill(icon: ImageVector, label: String) {
    Row(
        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(" $label", style = MaterialTheme.typography.labelMedium)
    }
}

package com.cowatch.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cowatch.app.settings.AppSettings
import com.cowatch.app.settings.Feature
import com.cowatch.app.ui.theme.Muted

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onToggle: (Feature, Boolean) -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    var showAdvanced by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(
            Modifier.fillMaxSize().imePadding().navigationBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item { SectionTitle("You") }
            item {
                Group {
                    OutlinedTextField(
                        value = settings.displayName,
                        onValueChange = { v -> onUpdate { it.copy(displayName = v.take(24)) } },
                        label = { Text("Your name (shown to your partner)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                    )
                }
            }
            Feature.Section.entries.forEach { section ->
                item { SectionTitle(section.title) }
                item {
                    Group {
                        val features = Feature.entries.filter { it.section == section }
                        features.forEachIndexed { i, f ->
                            FeatureRow(f, settings, onToggle)
                            if (i < features.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        }
                    }
                }
            }
            item { SectionTitle("Connection") }
            item {
                Group {
                    Row(
                        Modifier.fillMaxWidth().clickable { showAdvanced = !showAdvanced }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Servers")
                            Text("Pairing: ${settings.signalingHost}", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(if (showAdvanced) "Hide" else "Edit", color = MaterialTheme.colorScheme.primary)
                    }
                    if (showAdvanced) {
                        Column(Modifier.padding(horizontal = 14.dp)) {
                            ServerField("Pairing server (PeerJS)", settings.signalingHost) { v -> onUpdate { it.copy(signalingHost = v) } }
                            ServerField("STUN server", settings.stunUrl) { v -> onUpdate { it.copy(stunUrl = v) } }
                            ServerField("TURN server (optional)", settings.turnUrl, "turn:your.server:3478") { v -> onUpdate { it.copy(turnUrl = v) } }
                            ServerField("TURN username", settings.turnUser) { v -> onUpdate { it.copy(turnUser = v) } }
                            ServerField("TURN password", settings.turnPassword, password = true) { v -> onUpdate { it.copy(turnPassword = v) } }
                            Text(
                                "A TURN server helps when one of you is on a strict mobile or office network.",
                                color = Muted, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("Reset all settings") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)) {
        content()
    }
}

@Composable
private fun FeatureRow(feature: Feature, settings: AppSettings, onToggle: (Feature, Boolean) -> Unit) {
    val parentOff = settings.isOn(feature) && !settings.enabled(feature)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle(feature, !settings.isOn(feature)) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(if (parentOff) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(feature.title)
            Text(
                if (parentOff) "Turned off because a feature it depends on is off." else feature.description,
                color = Muted, style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = settings.isOn(feature), onCheckedChange = { onToggle(feature, it) })
    }
}

@Composable
private fun ServerField(label: String, value: String, placeholder: String = "", password: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder, color = Muted) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

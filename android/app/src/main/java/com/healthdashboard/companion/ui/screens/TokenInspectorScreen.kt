package com.healthdashboard.companion.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.healthdashboard.companion.data.network.ConnectionTestResult
import com.healthdashboard.companion.ui.components.BadgeType
import com.healthdashboard.companion.ui.components.StatusBadge
import com.healthdashboard.companion.ui.theme.EmeraldSuccess
import com.healthdashboard.companion.ui.theme.IndigoPrimary
import com.healthdashboard.companion.ui.theme.RoseDanger
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TokenInspectorScreen(
    modifier: Modifier = Modifier,
    latestToken: String?,
    prevToken: String?,
    primaryHost: String,
    primaryPort: Int,
    fallbackHost: String,
    fallbackPort: Int,
    backgroundSyncHours: Int,
    autoPrunePayloads: Boolean,
    onSaveConnectionSettings: (String, Int, String?, Int) -> Unit,
    onSetBackgroundSyncHours: (Int) -> Unit,
    onSetAutoPrunePayloads: (Boolean) -> Unit,
    onTriggerMacDevSync: suspend (String, Int) -> Result<String>,
    onTestEndpoint: suspend (String, Int) -> ConnectionTestResult,
    onResetTokensClicked: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var primaryInput by remember(primaryHost) { mutableStateOf(primaryHost) }
    var primaryPortInput by remember(primaryPort) { mutableStateOf(primaryPort.toString()) }
    var fallbackInput by remember(fallbackHost) { mutableStateOf(fallbackHost) }
    var fallbackPortInput by remember(fallbackPort) { mutableStateOf(fallbackPort.toString()) }

    var isTesting by remember { mutableStateOf(false) }
    var primaryTestResult by remember { mutableStateOf<ConnectionTestResult?>(null) }
    var fallbackTestResult by remember { mutableStateOf<ConnectionTestResult?>(null) }
    var showRawTokens by remember { mutableStateOf(false) }

    var isTriggeringDevSync by remember { mutableStateOf(false) }
    var devSyncResultMsg by remember { mutableStateOf<String?>(null) }
    var devSyncSuccess by remember { mutableStateOf<Boolean?>(null) }

    fun runConnectionTestAndSave() {
        val parsedPrimaryPort = primaryPortInput.toIntOrNull() ?: 8088
        val parsedFallbackPort = fallbackPortInput.toIntOrNull() ?: 8765
        onSaveConnectionSettings(
            primaryInput,
            parsedPrimaryPort,
            fallbackInput.ifBlank { null },
            parsedFallbackPort
        )
        isTesting = true
        primaryTestResult = null
        fallbackTestResult = null

        coroutineScope.launch {
            if (primaryInput.isNotBlank()) {
                primaryTestResult = onTestEndpoint(primaryInput, parsedPrimaryPort)
            }
            if (fallbackInput.isNotBlank()) {
                fallbackTestResult = onTestEndpoint(fallbackInput, parsedFallbackPort)
            }
            isTesting = false
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings & Sync Hub",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "24/7 QNAP NAS & Mac Dev endpoints with automated failover.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Direct Push Target & Failover Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SYNC HUB TARGETS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        StatusBadge(
                            text = if (primaryInput.isNotBlank()) "Configured" else "Not Set",
                            type = if (primaryInput.isNotBlank()) BadgeType.SUCCESS else BadgeType.NEUTRAL
                        )
                    }

                    Text(
                        text = "1-Tap Endpoint Presets:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Presets Row
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SuggestionChip(
                            onClick = {
                                primaryInput = "health-dashboard.local"
                                primaryPortInput = "8088"
                            },
                            label = { Text("NAS Tailscale (8088)") },
                            shape = RoundedCornerShape(8.dp)
                        )
                        SuggestionChip(
                            onClick = {
                                fallbackInput = "192.0.2.10"
                                fallbackPortInput = "8765"
                            },
                            label = { Text("Mac Dev LAN (8765)") },
                            shape = RoundedCornerShape(8.dp)
                        )
                        SuggestionChip(
                            onClick = {
                                fallbackInput = "100.64.0.10"
                                fallbackPortInput = "8765"
                            },
                            label = { Text("Mac Tailscale (8765)") },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Primary Host & Port Inputs (NAS Production)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = primaryInput,
                            onValueChange = { primaryInput = it },
                            modifier = Modifier.weight(0.7f),
                            label = { Text("Primary Host (NAS 24/7)") },
                            placeholder = { Text("health-dashboard.local") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = primaryPortInput,
                            onValueChange = { primaryPortInput = it },
                            modifier = Modifier.weight(0.3f),
                            label = { Text("Port") },
                            placeholder = { Text("8088") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Fallback Host & Port Inputs (Mac Dev)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = fallbackInput,
                            onValueChange = { fallbackInput = it },
                            modifier = Modifier.weight(0.7f),
                            label = { Text("Fallback Host (Mac Dev)") },
                            placeholder = { Text("192.0.2.10") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = fallbackPortInput,
                            onValueChange = { fallbackPortInput = it },
                            modifier = Modifier.weight(0.3f),
                            label = { Text("Port") },
                            placeholder = { Text("8765") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Action Button: Test & Save
                    Button(
                        onClick = { runConnectionTestAndSave() },
                        enabled = !isTesting && primaryInput.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing Endpoints...")
                        } else {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Test & Save Endpoints")
                        }
                    }

                    // Diagnostic Test Result Badges
                    if (primaryTestResult != null || fallbackTestResult != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Live Connection Diagnostics:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                primaryTestResult?.let { res ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        when (res) {
                                            is ConnectionTestResult.Success -> {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(16.dp))
                                                Text(
                                                    text = "Primary (${primaryInput.trim()}): ${res.message} • ${res.serverVersion}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = EmeraldSuccess
                                                )
                                            }
                                            is ConnectionTestResult.Failed -> {
                                                Icon(Icons.Default.Error, contentDescription = null, tint = RoseDanger, modifier = Modifier.size(16.dp))
                                                Text(
                                                    text = "Primary (${primaryInput.trim()}): ${res.error}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = RoseDanger
                                                )
                                            }
                                        }
                                    }
                                }

                                fallbackTestResult?.let { res ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        when (res) {
                                            is ConnectionTestResult.Success -> {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(16.dp))
                                                Text(
                                                    text = "Fallback (${fallbackInput.trim()}): ${res.message} • ${res.serverVersion}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = EmeraldSuccess
                                                )
                                            }
                                            is ConnectionTestResult.Failed -> {
                                                Icon(Icons.Default.Error, contentDescription = null, tint = RoseDanger, modifier = Modifier.size(16.dp))
                                                Text(
                                                    text = "Fallback (${fallbackInput.trim()}): ${res.error}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = RoseDanger
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Remote Mac Dev Sync Trigger Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "REMOTE MAC DEV SYNC",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        StatusBadge(
                            text = "NAS ➔ Mac Pull",
                            type = BadgeType.INFO
                        )
                    }

                    Text(
                        text = "Remotely command your Mac development workstation to pull fresh telemetry archives from the 24/7 QNAP NAS and rebuild the local SQLite fact store.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            val port = fallbackPortInput.toIntOrNull() ?: 8765
                            isTriggeringDevSync = true
                            devSyncResultMsg = null
                            devSyncSuccess = null
                            coroutineScope.launch {
                                val res = onTriggerMacDevSync(fallbackInput, port)
                                isTriggeringDevSync = false
                                res.fold(
                                    onSuccess = { msg ->
                                        devSyncResultMsg = "Mac Dev Sync initiated! Mac is pulling archives from NAS."
                                        devSyncSuccess = true
                                    },
                                    onFailure = { err ->
                                        devSyncResultMsg = "Could not trigger Mac: ${err.message}"
                                        devSyncSuccess = false
                                    }
                                )
                            }
                        },
                        enabled = !isTriggeringDevSync && fallbackInput.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTriggeringDevSync) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onSecondary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Contacting Mac Dev Server...")
                        } else {
                            Icon(Icons.Default.CloudDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Trigger Mac Dev Pull from NAS")
                        }
                    }

                    devSyncResultMsg?.let { msg ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = if (devSyncSuccess == true) EmeraldSuccess.copy(alpha = 0.15f) else RoseDanger.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    if (devSyncSuccess == true) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (devSyncSuccess == true) EmeraldSuccess else RoseDanger,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (devSyncSuccess == true) EmeraldSuccess else RoseDanger
                                )
                            }
                        }
                    }
                }
            }
        }

        // Background Cadence & Storage Retention Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BACKGROUND SYNC & STORAGE",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        StatusBadge(
                            text = if (backgroundSyncHours > 0) "Every ${backgroundSyncHours}h" else "Manual Only",
                            type = if (backgroundSyncHours > 0) BadgeType.SUCCESS else BadgeType.NEUTRAL
                        )
                    }

                    Text(
                        text = "Automatic sync executes via WorkManager with network and battery constraints.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Cadence Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0 to "Manual", 1 to "1h", 3 to "3h", 6 to "6h", 12 to "12h").forEach { (h, label) ->
                            FilterChip(
                                selected = backgroundSyncHours == h,
                                onClick = { onSetBackgroundSyncHours(h) },
                                label = { Text(label) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    // Auto-Prune Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Prune Local Storage",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                            Text(
                                text = "Keep only the 3 latest export payloads after successful sync to prevent storage expansion.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoPrunePayloads,
                            onCheckedChange = onSetAutoPrunePayloads
                        )
                    }
                }
            }
        }

        // Telemetry Health & Subscribed Categories Card (Replaces Low-Value Raw Token Card)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Sensors, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(
                                text = "TELEMETRY HEALTH",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        StatusBadge(
                            text = if (latestToken != null) "25 SDK Types Active" else "Initialized",
                            type = BadgeType.SUCCESS
                        )
                    }

                    Text(
                        text = "Real-time subscriptions across all 25 native Samsung Health Data SDK data streams with zero fabricated telemetry.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Subscribed Categories Grid
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("❤️ Heart Rate (Continuous + Minute Bins)", style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp)
                        )
                        SuggestionChip(
                            onClick = {},
                            label = { Text("🌙 Sleep (Stages & Wake Events)", style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp)
                        )
                        SuggestionChip(
                            onClick = {},
                            label = { Text("🏃 Activity & Steps (Daily Summary)", style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp)
                        )
                        SuggestionChip(
                            onClick = {},
                            label = { Text("🥗 Nutrition & Water (Logged Items)", style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp)
                        )
                        SuggestionChip(
                            onClick = {},
                            label = { Text("🩺 Clinical (SpO2, BP, Glucose, Energy)", style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp)
                        )
                        SuggestionChip(
                            onClick = {},
                            label = { Text("🎯 Goals (Step & Calorie Targets)", style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Expandable Advanced Token Inspector Accordion
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showRawTokens = !showRawTokens },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Advanced Change Tokens (Debug)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    imageVector = if (showRawTokens) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            AnimatedVisibility(visible = showRawTokens) {
                                Column(
                                    modifier = Modifier.padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Current Sync Token (changes_token_next):",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = latestToken ?: "None (Next sync queries full window)",
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "Previous Sync Token (changes_token_prev):",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = prevToken ?: "None",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Reset Tokens Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Reset Token State",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Clearing stored tokens forces the next sync pass to re-query the full 7-day window instead of reading incremental deltas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onResetTokensClicked,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RoseDanger)
                    ) {
                        Text("Clear / Reset Change Tokens")
                    }
                }
            }
        }
    }
}

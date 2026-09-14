package com.healthdashboard.companion.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import com.healthdashboard.companion.data.network.ServerStatusPayload
import com.healthdashboard.companion.data.token.SyncHistoryItem
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthdashboard.companion.collector.BackfillHorizon
import com.healthdashboard.companion.collector.CollectionResult
import com.healthdashboard.companion.data.export.PayloadExporter
import com.healthdashboard.companion.data.token.SyncMetadata
import com.healthdashboard.companion.sdk.ConnectionStatus
import com.healthdashboard.companion.sync.BackfillState
import com.healthdashboard.companion.ui.components.BadgeType
import com.healthdashboard.companion.ui.components.StatusBadge
import com.healthdashboard.companion.ui.theme.EmeraldSuccess
import com.healthdashboard.companion.ui.theme.IndigoPrimary
import com.healthdashboard.companion.ui.theme.RoseDanger
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun formatIsoTimestamp(isoString: String?): String {
    if (isoString.isNullOrBlank()) return "Never"
    return try {
        val instant = Instant.parse(isoString)
        val local = instant.atZone(ZoneId.systemDefault())
        val now = ZonedDateTime.now()
        if (local.toLocalDate() == now.toLocalDate()) {
            "Today at " + local.format(DateTimeFormatter.ofPattern("HH:mm"))
        } else {
            local.format(DateTimeFormatter.ofPattern("MMM dd, HH:mm"))
        }
    } catch (_: Exception) {
        isoString.take(16).replace("T", " ")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    connectionStatus: ConnectionStatus,
    collectionResult: CollectionResult,
    backfillState: BackfillState,
    pendingExports: List<File>,
    totalPayloadBytes: Long,
    lastSyncMeta: SyncMetadata,
    lastBackfillTime: String?,
    serverStatus: ServerStatusPayload? = null,
    syncHistory: List<SyncHistoryItem> = emptyList(),
    onSyncClicked: () -> Unit,
    onForceSyncClicked: () -> Unit,
    onStartBackfill: (Int) -> Unit,
    onPauseBackfill: () -> Unit,
    onResumeBackfill: () -> Unit,
    onResetBackfill: () -> Unit,
    onRefreshExports: () -> Unit,
    onDeleteAllPayloads: () -> Unit,
    onDeleteOldestPayloads: () -> Unit,
    onDeletePayload: (File) -> Unit,
    onNavigateToAttestation: () -> Unit = {}
) {
    val context = LocalContext.current
    val exporter = PayloadExporter(context)
    var selectedBackfillDays by remember { mutableIntStateOf(30) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showKeep3Dialog by remember { mutableStateOf(false) }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Delete All Exported Payloads?") },
            text = {
                Text("Permanently delete all ${pendingExports.size} local payload files (${exporter.formatFileSize(totalPayloadBytes)}) from device storage? Ingested data on your Mac database is completely safe and unaffected.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllDialog = false
                        onDeleteAllPayloads()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseDanger)
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showKeep3Dialog) {
        val deleteCount = (pendingExports.size - 3).coerceAtLeast(0)
        AlertDialog(
            onDismissRequest = { showKeep3Dialog = false },
            title = { Text("Keep 3 Most Recent Payloads?") },
            text = {
                Text("Delete the $deleteCount oldest local payload files and keep only the 3 most recent? Ingested data on your Mac database is completely safe and unaffected.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showKeep3Dialog = false
                        onDeleteOldestPayloads()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                ) {
                    Text("Keep 3 Recent")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showKeep3Dialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
                text = "Health Companion",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Samsung Health Data SDK • Local Collector",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Server Freshness Banner
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SERVER TELEMETRY FRESHNESS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        if (serverStatus != null) {
                            val pipeline = serverStatus.telemetry_freshness?.sync_freshness?.pipeline_status ?: "active"
                            val badgeType = when (pipeline.lowercase()) {
                                "active" -> BadgeType.SUCCESS
                                "delayed" -> BadgeType.WARNING
                                else -> BadgeType.ERROR
                            }
                            StatusBadge(
                                text = "Pipeline ${pipeline.replaceFirstChar { it.uppercase() }}",
                                type = badgeType
                            )
                        } else {
                            StatusBadge(text = "Probing...", type = BadgeType.NEUTRAL)
                        }
                    }

                    if (serverStatus != null) {
                        val age = serverStatus.telemetry_freshness?.sync_freshness?.contact_age_hours
                        val ageStr = if (age != null) "${age}h ago" else "Recent"
                        val authorityStr = if (serverStatus.authority == "nas") "24/7 QNAP NAS Hub" else "Local Mac Dev Hub"
                        val sensorFact = serverStatus.telemetry_freshness?.sensor_freshness?.data_current_through?.take(16)?.replace("T", " ") ?: "Live"

                        Text(
                            text = "$authorityStr • Contact: $ageStr",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Latest sensor fact: $sensorFact • Server v${serverStatus.server_version ?: "1.2.0"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Server hub offline or waiting for connection probe.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Connection & SDK Status Card
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SDK ENGINE STATUS",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        when (connectionStatus) {
                            is ConnectionStatus.Connected -> StatusBadge("Connected", BadgeType.SUCCESS)
                            is ConnectionStatus.Connecting -> StatusBadge("Connecting...", BadgeType.WARNING)
                            is ConnectionStatus.Idle -> StatusBadge("Idle", BadgeType.NEUTRAL)
                            is ConnectionStatus.Error -> StatusBadge("Unavailable", BadgeType.ERROR)
                        }
                    }

                    when (connectionStatus) {
                        is ConnectionStatus.Connected -> {
                            Text(
                                text = "Samsung Health v${connectionStatus.version}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (connectionStatus.isDeveloperMode) {
                                    "Developer mode data-read enabled. Native delta change tracking active."
                                } else {
                                    "Developer mode disabled. Enable in Samsung Health -> About -> Tap version 10x."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is ConnectionStatus.Error -> {
                            Text(
                                text = connectionStatus.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {
                            Text(
                                text = "Checking Samsung Health subsystem...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Sync Action Card
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
                    Text(
                        text = "Incremental Sync & Export",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Queries delta changes across all 25 data types and packages an immutable JSON envelope with SHA-256 integrity check.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Last Remote Sync Status Line
                    if (!lastSyncMeta.lastSyncTime.isNullOrBlank()) {
                        val endpointLabel = if (!lastSyncMeta.lastSyncEndpoint.isNullOrBlank()) {
                            " via ${lastSyncMeta.lastSyncEndpoint.removePrefix("http://").removePrefix("https://").split(":")[0]}"
                        } else ""
                        Text(
                            text = "🟢 Last Remote Sync: ${formatIsoTimestamp(lastSyncMeta.lastSyncTime)} (${lastSyncMeta.lastSyncCount} records$endpointLabel)",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldSuccess
                        )
                    } else {
                        Text(
                            text = "⚪ Not synced to Mac yet • Tap Sync Now to connect",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onSyncClicked,
                            enabled = collectionResult !is CollectionResult.InProgress && backfillState !is BackfillState.Running,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                        ) {
                            if (collectionResult is CollectionResult.InProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Syncing...")
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sync Now")
                            }
                        }

                        OutlinedButton(
                            onClick = onForceSyncClicked,
                            enabled = collectionResult !is CollectionResult.InProgress && backfillState !is BackfillState.Running,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Full Pass (7d)")
                        }
                    }
                }
            }
        }

        // Milestone 25: Dose Attestation Quick Action Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = com.healthdashboard.companion.ui.theme.IndigoLight,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Medication,
                                    contentDescription = null,
                                    tint = IndigoPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Protocol Dose Attestation",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "1-Tap confirmation & retrospective logging",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = onNavigateToAttestation,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("Attest", fontSize = 13.sp)
                    }
                }
            }
        }

        // Active Backfill Progress / Paused State Card
        when (backfillState) {
            is BackfillState.Running -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(2.dp, IndigoPrimary)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "CHUNKED BACKFILL STREAMING",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = IndigoPrimary
                                )
                                StatusBadge("Chunk ${backfillState.chunkIndex}/${backfillState.totalChunks}", BadgeType.NEUTRAL)
                            }

                            LinearProgressIndicator(
                                progress = { backfillState.progressPct },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                color = IndigoPrimary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )

                            Text(
                                text = "Window: ${backfillState.currentWindowFrom.take(10)} ──> ${backfillState.currentWindowTo.take(10)}",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Text(
                                text = "Total Synced: ${backfillState.totalRecordsSoFar} records",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = onPauseBackfill,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Pause Backfill")
                            }
                        }
                    }
                }
            }
            is BackfillState.Paused -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "BACKFILL PAUSED / INTERRUPTED",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                StatusBadge("Saved at Chunk ${backfillState.chunkIndex}/${backfillState.totalChunks}", BadgeType.WARNING)
                            }

                            Text(
                                text = backfillState.reason,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )

                            Text(
                                text = "Progress saved: ${backfillState.totalRecordsSoFar} records already safely ingested on Mac.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = onResumeBackfill,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                                ) {
                                    Text("Resume Backfill")
                                }

                                OutlinedButton(
                                    onClick = onResetBackfill,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Reset")
                                }
                            }
                        }
                    }
                }
            }
            is BackfillState.Completed -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "BACKFILL STREAMING COMPLETE",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                StatusBadge("All ${backfillState.totalChunks} Chunks Synced", BadgeType.SUCCESS)
                            }

                            Text(
                                text = "${backfillState.totalRecords} total records ingested in ${backfillState.durationSeconds}s",
                                style = MaterialTheme.typography.titleMedium
                            )

                            OutlinedButton(
                                onClick = onResetBackfill,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }
            else -> {}
        }

        // Historical Backfill Launcher Card (when not actively running)
        if (backfillState !is BackfillState.Running && backfillState !is BackfillState.Paused) {
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
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CHUNKED HISTORICAL BACKFILL",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "100% Raw Granularity Backfill",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Streams historical data in slices with full raw waveforms, live progress tracking, and auto-resume if interrupted.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Responsive FlowRow for horizon selection
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BackfillHorizon.entries.forEach { horizon ->
                                FilterChip(
                                    selected = selectedBackfillDays == horizon.days,
                                    onClick = { selectedBackfillDays = horizon.days },
                                    label = { Text(horizon.label) }
                                )
                            }
                        }

                        if (!lastBackfillTime.isNullOrBlank()) {
                            Text(
                                text = "Last backfilled: ${formatIsoTimestamp(lastBackfillTime)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { onStartBackfill(selectedBackfillDays) },
                            enabled = collectionResult !is CollectionResult.InProgress,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                        ) {
                            Text("Start $selectedBackfillDays-Day Stream")
                        }
                    }
                }
            }
        }

        // Sync Results / In-progress Card
        when (collectionResult) {
            is CollectionResult.Success -> {
                item {
                    val serverResp = collectionResult.serverResponse
                    val ingestSummary = serverResp?.ingest_summary
                    val breakdown = ingestSummary?.breakdown ?: collectionResult.localBreakdown

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LAST SYNC TRANSMISSION",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                val isDirectSyncSuccess = serverResp != null || (collectionResult.pushStatus?.contains("✅") == true)
                                if (isDirectSyncSuccess) {
                                    StatusBadge("Synced & Acknowledged", BadgeType.SUCCESS)
                                } else {
                                    StatusBadge("Exported Locally", BadgeType.NEUTRAL)
                                }
                            }

                            Text(
                                text = "${collectionResult.recordCount} records transmitted",
                                style = MaterialTheme.typography.headlineSmall
                            )

                            if (breakdown.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Record Category Breakdown:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    breakdown.forEach { (type, count) ->
                                        val icon = when (type.uppercase()) {
                                            "SLEEP" -> "🌙"
                                            "HEART_RATE" -> "❤️"
                                            "NUTRITION" -> "🥗"
                                            "EXERCISE" -> "🏋️"
                                            "ACTIVITY_SUMMARY", "ACTIVITY_GOAL" -> "🎯"
                                            "CLINICAL" -> "🩺"
                                            else -> "📊"
                                        }
                                        SuggestionChip(
                                            onClick = {},
                                            label = {
                                                Text(
                                                    text = "$icon $type: $count",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            },
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Mac Hub Acknowledgement & Replay Defense Feedback
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Mac Hub Acknowledgement (${serverResp?.server_version ?: "v1.2.0"}):",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = serverResp?.message ?: "Payload exported and ready for sync.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (ingestSummary != null && ingestSummary.replays_rejected > 0) {
                                        Text(
                                            text = "🛡️ ${ingestSummary.replays_rejected} sliding-clock replays safely filtered by SQLite.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "SHA-256: ${collectionResult.sha256.take(16)}... | ${collectionResult.file.name}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            collectionResult.pushStatus?.let { status ->
                                Text(
                                    text = status,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (status.contains("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
            is CollectionResult.Error -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Sync Failed",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = collectionResult.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            else -> {}
        }

        // Section: Recent Sync Transmissions
        if (syncHistory.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "RECENT SYNC TRANSMISSIONS",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            StatusBadge(
                                text = "Last ${syncHistory.take(5).size}",
                                type = BadgeType.INFO
                            )
                        }

                        syncHistory.take(5).forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${formatIsoTimestamp(item.timestamp)} • ${item.recordCount} records",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                    )
                                    val cleanEndpoint = item.endpoint.removePrefix("http://").removePrefix("https://").split("/")[0]
                                    Text(
                                        text = "Target: $cleanEndpoint ${item.details?.let { "• $it" } ?: ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                StatusBadge(
                                    text = item.status.replaceFirstChar { it.uppercase() },
                                    type = if (item.status == "success") BadgeType.SUCCESS else BadgeType.ERROR
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Exported Payloads in Queue
        item {
            val totalSizeStr = exporter.formatFileSize(totalPayloadBytes)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Exported Payloads (${pendingExports.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (pendingExports.isNotEmpty()) {
                            Text(
                                text = "$totalSizeStr occupied on device",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onRefreshExports) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh exports")
                    }
                }

                if (pendingExports.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Button 1: Keep 3 Most Recent (Deletes older)
                        OutlinedButton(
                            onClick = { showKeep3Dialog = true },
                            modifier = Modifier.weight(1f),
                            enabled = pendingExports.size > 3,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (pendingExports.size > 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val label = if (pendingExports.size > 3) "Keep 3 (-${pendingExports.size - 3})" else "Keep 3 Recent"
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }

                        // Button 2: Delete All
                        OutlinedButton(
                            onClick = { showDeleteAllDialog = true },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = RoseDanger
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Delete All (${pendingExports.size})",
                                color = RoseDanger,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }

        if (pendingExports.isEmpty()) {
            item {
                Text(
                    text = "No exported payload files in queue. Tap 'Sync Now' to create one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(pendingExports) { file ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${exporter.formatFileSize(file.length())} • Ready for data/inbox/",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val shareIntent = exporter.createShareIntent(file)
                                    context.startActivity(Intent.createChooser(shareIntent, "Export Health Payload"))
                                }
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share payload")
                            }
                            IconButton(
                                onClick = { onDeletePayload(file) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete payload",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

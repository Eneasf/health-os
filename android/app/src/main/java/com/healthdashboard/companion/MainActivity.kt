package com.healthdashboard.companion

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.healthdashboard.companion.collector.HealthDataCollector
import com.healthdashboard.companion.data.export.PayloadExporter
import com.healthdashboard.companion.data.network.ServerStatusPayload
import com.healthdashboard.companion.data.network.SyncNetworkClient
import com.healthdashboard.companion.data.token.TokenRepository
import com.healthdashboard.companion.sdk.SamsungHealthClient
import com.healthdashboard.companion.sdk.SamsungHealthPermissionManager
import com.healthdashboard.companion.ui.screens.DoseAttestationScreen
import com.healthdashboard.companion.ui.screens.HomeScreen
import com.healthdashboard.companion.ui.screens.PermissionsScreen
import com.healthdashboard.companion.ui.screens.ScanIngestionScreen
import com.healthdashboard.companion.ui.screens.TokenInspectorScreen
import com.healthdashboard.companion.ui.theme.HealthDashboardCompanionTheme
import com.healthdashboard.companion.worker.HealthSyncWorker
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var healthClient: SamsungHealthClient
    private lateinit var permissionManager: SamsungHealthPermissionManager
    private lateinit var tokenRepository: TokenRepository
    private lateinit var payloadExporter: PayloadExporter
    private lateinit var dataCollector: HealthDataCollector
    private lateinit var backfillManager: com.healthdashboard.companion.sync.BackfillManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        healthClient = SamsungHealthClient(applicationContext)
        permissionManager = SamsungHealthPermissionManager(applicationContext)
        tokenRepository = TokenRepository(applicationContext)
        payloadExporter = PayloadExporter(applicationContext)
        dataCollector = HealthDataCollector(
            context = applicationContext,
            client = healthClient,
            permissionManager = permissionManager,
            tokenRepository = tokenRepository,
            payloadExporter = payloadExporter
        )
        backfillManager = com.healthdashboard.companion.sync.BackfillManager(
            context = applicationContext,
            collector = dataCollector,
            tokenRepository = tokenRepository
        )

        setContent {
            HealthDashboardCompanionTheme {
                MainAppScaffold(
                    healthClient = healthClient,
                    permissionManager = permissionManager,
                    tokenRepository = tokenRepository,
                    payloadExporter = payloadExporter,
                    dataCollector = dataCollector,
                    backfillManager = backfillManager
                )
            }
        }
    }
}

@Composable
fun MainAppScaffold(
    healthClient: SamsungHealthClient,
    permissionManager: SamsungHealthPermissionManager,
    tokenRepository: TokenRepository,
    payloadExporter: PayloadExporter,
    dataCollector: HealthDataCollector,
    backfillManager: com.healthdashboard.companion.sync.BackfillManager
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var selectedTab by remember { mutableIntStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    val connectionStatus by healthClient.status.collectAsState()
    val permissionStates by permissionManager.permissions.collectAsState()
    val collectionState by dataCollector.collectionState.collectAsState()
    val backfillState by backfillManager.state.collectAsState()
    val latestToken by tokenRepository.getLatestTokenFlow().collectAsState(initial = null)
    val primaryHost by tokenRepository.getPrimaryHostFlow().collectAsState(initial = "health-dashboard.local")
    val primaryPort by tokenRepository.getPrimaryPortFlow().collectAsState(initial = 8088)
    val fallbackHost by tokenRepository.getFallbackHostFlow().collectAsState(initial = "192.0.2.10")
    val fallbackPort by tokenRepository.getFallbackPortFlow().collectAsState(initial = 8765)
    val backgroundSyncHours by tokenRepository.getBackgroundSyncHoursFlow().collectAsState(initial = 6)
    val autoPrunePayloads by tokenRepository.getAutoPrunePayloadsFlow().collectAsState(initial = true)
    val syncHistory by tokenRepository.getSyncHistoryFlow().collectAsState(initial = emptyList())
    val lastSyncMeta by tokenRepository.getLastSyncMetadataFlow().collectAsState(initial = com.healthdashboard.companion.data.token.SyncMetadata())
    val lastBackfillTime by tokenRepository.getLastBackfillTimeFlow().collectAsState(initial = null)

    var prevToken by remember { mutableStateOf<String?>(null) }
    var pendingExports by remember { mutableStateOf<List<File>>(emptyList()) }
    var totalPayloadBytes by remember { mutableStateOf(0L) }
    var serverStatus by remember { mutableStateOf<ServerStatusPayload?>(null) }

    fun refreshExports() {
        pendingExports = payloadExporter.listPendingExports()
        totalPayloadBytes = payloadExporter.getTotalPayloadSizeBytes()
    }

    fun refreshServerStatus() {
        coroutineScope.launch {
            val netClient = SyncNetworkClient(context)
            val status = netClient.checkServerFreshness(primaryHost, primaryPort)
                ?: if (fallbackHost.isNotBlank()) netClient.checkServerFreshness(fallbackHost, fallbackPort) else null
            serverStatus = status
        }
    }

    LaunchedEffect(Unit) {
        healthClient.connect()
        permissionManager.refreshGrantedPermissions()
        prevToken = tokenRepository.getPreviousToken()
        refreshExports()
        refreshServerStatus()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Dashboard") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Medication, contentDescription = "Attest") },
                    label = { Text("Attest") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.DocumentScanner, contentDescription = "Scan") },
                    label = { Text("Visual Intake") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Key, contentDescription = "Settings") },
                    label = { Text("Settings & Sync") },
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Security, contentDescription = "Permissions") },
                    label = { Text("Permissions") },
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> HomeScreen(
                    modifier = Modifier.fillMaxSize(),
                    connectionStatus = connectionStatus,
                    collectionResult = collectionState,
                    backfillState = backfillState,
                    pendingExports = pendingExports,
                    totalPayloadBytes = totalPayloadBytes,
                    lastSyncMeta = lastSyncMeta,
                    lastBackfillTime = lastBackfillTime,
                    serverStatus = serverStatus,
                    syncHistory = syncHistory,
                    onSyncClicked = {
                        coroutineScope.launch {
                            dataCollector.runCollectionPass(forceFullSync = false)
                            prevToken = tokenRepository.getPreviousToken()
                            refreshExports()
                            refreshServerStatus()
                        }
                    },
                    onForceSyncClicked = {
                        coroutineScope.launch {
                            dataCollector.runCollectionPass(forceFullSync = true, daysLookback = 7)
                            prevToken = tokenRepository.getPreviousToken()
                            refreshExports()
                            refreshServerStatus()
                        }
                    },
                    onStartBackfill = { days ->
                        backfillManager.startBackfill(days)
                    },
                    onPauseBackfill = {
                        backfillManager.pauseBackfill()
                    },
                    onResumeBackfill = {
                        backfillManager.resumeBackfill()
                    },
                    onResetBackfill = {
                        backfillManager.resetBackfill()
                    },
                    onRefreshExports = { refreshExports() },
                    onDeleteAllPayloads = {
                        payloadExporter.deleteAllPayloads()
                        refreshExports()
                    },
                    onDeleteOldestPayloads = {
                        payloadExporter.deleteOldestPayloads(keepCount = 3)
                        refreshExports()
                    },
                    onDeletePayload = { file ->
                        payloadExporter.deletePayload(file)
                        refreshExports()
                    },
                    onNavigateToAttestation = { selectedTab = 1 }
                )
                1 -> DoseAttestationScreen(
                    primaryHost = primaryHost,
                    primaryPort = primaryPort,
                    fallbackHost = fallbackHost.ifBlank { null },
                    fallbackPort = fallbackPort,
                    modifier = Modifier.fillMaxSize()
                )
                2 -> ScanIngestionScreen(
                    modifier = Modifier.fillMaxSize(),
                    primaryHost = primaryHost,
                    primaryPort = primaryPort,
                    fallbackHost = fallbackHost.ifBlank { null },
                    fallbackPort = fallbackPort
                )
                3 -> TokenInspectorScreen(
                    modifier = Modifier.fillMaxSize(),
                    latestToken = latestToken,
                    prevToken = prevToken,
                    primaryHost = primaryHost,
                    primaryPort = primaryPort,
                    fallbackHost = fallbackHost,
                    fallbackPort = fallbackPort,
                    backgroundSyncHours = backgroundSyncHours,
                    autoPrunePayloads = autoPrunePayloads,
                    onSaveConnectionSettings = { primary, pPort, fallback, fPort ->
                        coroutineScope.launch {
                            tokenRepository.saveConnectionSettings(primary, pPort, fallback, fPort)
                            refreshServerStatus()
                        }
                    },
                    onSetBackgroundSyncHours = { hours ->
                        coroutineScope.launch {
                            tokenRepository.setBackgroundSyncHours(hours)
                            HealthSyncWorker.schedulePeriodicSync(context, hours)
                        }
                    },
                    onSetAutoPrunePayloads = { enabled ->
                        coroutineScope.launch {
                            tokenRepository.setAutoPrunePayloads(enabled)
                        }
                    },
                    onTriggerMacDevSync = { host, port ->
                        SyncNetworkClient(context).triggerMacDevSync(host, port)
                    },
                    onTestEndpoint = { host, port ->
                        SyncNetworkClient(context).testEndpoint(host, port)
                    },
                    onResetTokensClicked = {
                        coroutineScope.launch {
                            tokenRepository.clearTokens()
                            prevToken = null
                        }
                    }
                )
                4 -> PermissionsScreen(
                    modifier = Modifier.fillMaxSize(),
                    permissionStates = permissionStates,
                    onRequestPermissions = { _ ->
                        activity?.let { act ->
                            coroutineScope.launch {
                                permissionManager.requestPermissions(act)
                            }
                        }
                    }
                )
            }
        }
    }
}

package com.healthdashboard.companion.data.token

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "health_tokens")

data class SyncMetadata(
    val lastSyncTime: String? = null,
    val lastSyncCount: Int = 0,
    val lastSyncEndpoint: String? = null
)

@Serializable
data class SyncHistoryItem(
    val timestamp: String,
    val endpoint: String,
    val recordCount: Int,
    val status: String, // "success", "failed", "offline"
    val latencyMs: Long = 0,
    val details: String? = null
)

enum class EndpointPreset(val label: String, val host: String, val port: Int) {
    NAS_TAILSCALE("QNAP NAS (Tailscale)", "health-dashboard.local", 8088),
    NAS_IP("QNAP NAS (Direct IP)", "192.0.2.20", 8088),
    MAC_LAN("Mac Dev (LAN)", "192.0.2.10", 8765),
    MAC_TAILSCALE("Mac Dev (Tailscale)", "100.64.0.10", 8765)
}

class TokenRepository(private val context: Context) {
    private val historyJson = Json { ignoreUnknownKeys = true }

    private fun tokenKey(dataType: String) = stringPreferencesKey("token_$dataType")
    private fun syncTimeKey(dataType: String) = stringPreferencesKey("synctime_$dataType")
    private val globalTokenKey = stringPreferencesKey("global_token_latest")
    private val globalPrevTokenKey = stringPreferencesKey("global_token_prev")

    // Connection Settings
    private val primaryHostKey = stringPreferencesKey("primary_host")
    private val primaryPortKey = intPreferencesKey("primary_port")
    private val fallbackHostKey = stringPreferencesKey("fallback_host")
    private val fallbackPortKey = intPreferencesKey("fallback_port")
    private val serverPortKey = intPreferencesKey("server_port") // legacy fallback
    private val macServerUrlKey = stringPreferencesKey("mac_server_url")

    // Sync Telemetry History
    private val lastSyncTimeKey = stringPreferencesKey("last_successful_sync_time")
    private val lastSyncCountKey = intPreferencesKey("last_successful_sync_count")
    private val lastSyncEndpointKey = stringPreferencesKey("last_successful_sync_endpoint")
    private val lastBackfillTimeKey = stringPreferencesKey("last_successful_backfill_time")
    private val lastBackfillHorizonKey = stringPreferencesKey("last_successful_backfill_horizon")
    private val syncHistoryKey = stringPreferencesKey("sync_history_json")

    // Background & Retention Preferences
    private val backgroundSyncHoursKey = intPreferencesKey("background_sync_hours")
    private val autoPrunePayloadsKey = androidx.datastore.preferences.core.booleanPreferencesKey("auto_prune_payloads")

    fun getPrimaryHostFlow(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[primaryHostKey] ?: "health-dashboard.local"
    }

    suspend fun getPrimaryHost(): String {
        val prefs = context.dataStore.data.first()
        return prefs[primaryHostKey] ?: "health-dashboard.local"
    }

    fun getPrimaryPortFlow(): Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[primaryPortKey] ?: 8088
    }

    suspend fun getPrimaryPort(): Int {
        val prefs = context.dataStore.data.first()
        return prefs[primaryPortKey] ?: 8088
    }

    fun getFallbackHostFlow(): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[fallbackHostKey] ?: "192.0.2.10"
    }

    suspend fun getFallbackHost(): String {
        val prefs = context.dataStore.data.first()
        return prefs[fallbackHostKey] ?: "192.0.2.10"
    }

    fun getFallbackPortFlow(): Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[fallbackPortKey] ?: 8765
    }

    suspend fun getFallbackPort(): Int {
        val prefs = context.dataStore.data.first()
        return prefs[fallbackPortKey] ?: 8765
    }

    fun getServerPortFlow(): Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[primaryPortKey] ?: prefs[serverPortKey] ?: 8088
    }

    suspend fun getServerPort(): Int {
        val prefs = context.dataStore.data.first()
        return prefs[primaryPortKey] ?: prefs[serverPortKey] ?: 8088
    }

    suspend fun saveConnectionSettings(
        primaryHost: String,
        primaryPort: Int,
        fallbackHost: String?,
        fallbackPort: Int
    ) {
        context.dataStore.edit { prefs ->
            val cleanPrimary = primaryHost.trim()
            prefs[primaryHostKey] = cleanPrimary
            prefs[primaryPortKey] = primaryPort
            if (!fallbackHost.isNullOrBlank()) {
                prefs[fallbackHostKey] = fallbackHost.trim()
                prefs[fallbackPortKey] = fallbackPort
            } else {
                prefs.remove(fallbackHostKey)
                prefs.remove(fallbackPortKey)
            }
            prefs[serverPortKey] = primaryPort
            val hostOnly = extractHost(cleanPrimary)
            prefs[macServerUrlKey] = "http://$hostOnly:$primaryPort/api/sync"
        }
    }

    suspend fun saveConnectionSettings(primaryHost: String, fallbackHost: String?, port: Int) {
        saveConnectionSettings(primaryHost, port, fallbackHost, if (port == 8088) 8765 else port)
    }

    fun getBackgroundSyncHoursFlow(): Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[backgroundSyncHoursKey] ?: 6
    }

    suspend fun getBackgroundSyncHours(): Int {
        return context.dataStore.data.first()[backgroundSyncHoursKey] ?: 6
    }

    suspend fun setBackgroundSyncHours(hours: Int) {
        context.dataStore.edit { prefs ->
            prefs[backgroundSyncHoursKey] = hours
        }
    }

    fun getAutoPrunePayloadsFlow(): Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[autoPrunePayloadsKey] ?: true
    }

    suspend fun getAutoPrunePayloads(): Boolean {
        return context.dataStore.data.first()[autoPrunePayloadsKey] ?: true
    }

    suspend fun setAutoPrunePayloads(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[autoPrunePayloadsKey] = enabled
        }
    }

    fun getLastSyncMetadataFlow(): Flow<SyncMetadata> = context.dataStore.data.map { prefs ->
        SyncMetadata(
            lastSyncTime = prefs[lastSyncTimeKey] ?: prefs[syncTimeKey("GLOBAL")],
            lastSyncCount = prefs[lastSyncCountKey] ?: 0,
            lastSyncEndpoint = prefs[lastSyncEndpointKey]
        )
    }

    suspend fun recordSuccessfulSync(count: Int, endpoint: String, timestamp: String = java.time.Instant.now().toString()) {
        context.dataStore.edit { prefs ->
            prefs[lastSyncTimeKey] = timestamp
            prefs[lastSyncCountKey] = count
            prefs[lastSyncEndpointKey] = endpoint
        }
    }

    fun getSyncHistoryFlow(): Flow<List<SyncHistoryItem>> = context.dataStore.data.map { prefs ->
        val raw = prefs[syncHistoryKey] ?: return@map emptyList()
        try {
            historyJson.decodeFromString<List<SyncHistoryItem>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun recordSyncAttempt(
        endpoint: String,
        recordCount: Int,
        status: String,
        latencyMs: Long,
        details: String? = null,
        timestamp: String = java.time.Instant.now().toString()
    ) {
        val newItem = SyncHistoryItem(
            timestamp = timestamp,
            endpoint = endpoint,
            recordCount = recordCount,
            status = status,
            latencyMs = latencyMs,
            details = details
        )
        context.dataStore.edit { prefs ->
            val existingList = try {
                prefs[syncHistoryKey]?.let { historyJson.decodeFromString<List<SyncHistoryItem>>(it) } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
            val updated = (listOf(newItem) + existingList).take(10) // keep last 10 attempts
            prefs[syncHistoryKey] = historyJson.encodeToString(updated)
        }
    }

    suspend fun recordSuccessfulBackfill(horizon: String, timestamp: String = java.time.Instant.now().toString()) {
        context.dataStore.edit { prefs ->
            prefs[lastBackfillTimeKey] = timestamp
            prefs[lastBackfillHorizonKey] = horizon
        }
    }

    fun getLastBackfillTimeFlow(): Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[lastBackfillTimeKey]
    }

    private fun extractHost(urlOrHost: String): String {
        return urlOrHost.removePrefix("http://")
            .removePrefix("https://")
            .split(":")[0]
            .split("/")[0]
            .trim()
    }

    private fun extractPort(urlOrHost: String): Int {
        val withoutProto = urlOrHost.removePrefix("http://").removePrefix("https://").split("/")[0]
        val parts = withoutProto.split(":")
        return if (parts.size > 1) parts[1].toIntOrNull() ?: 8765 else 8765
    }

    fun getLatestTokenFlow(dataType: String = "GLOBAL"): Flow<String?> {
        val key = if (dataType == "GLOBAL") globalTokenKey else tokenKey(dataType)
        return context.dataStore.data.map { prefs -> prefs[key] }
    }

    suspend fun getLatestToken(dataType: String = "GLOBAL"): String? {
        val key = if (dataType == "GLOBAL") globalTokenKey else tokenKey(dataType)
        return context.dataStore.data.first()[key]
    }

    suspend fun getPreviousToken(): String? {
        return context.dataStore.data.first()[globalPrevTokenKey]
    }

    fun getMacServerUrlFlow(): Flow<String> {
        return context.dataStore.data.map { prefs ->
            val primary = prefs[primaryHostKey]
            val port = prefs[serverPortKey] ?: 8765
            if (!primary.isNullOrBlank()) {
                val hostOnly = extractHost(primary)
                "http://$hostOnly:$port/api/sync"
            } else {
                val url = prefs[macServerUrlKey]
                if (url.isNullOrBlank()) "http://100.64.0.10:8765/api/sync" else url
            }
        }
    }

    suspend fun getMacServerUrl(): String {
        val primary = getPrimaryHost()
        val port = getServerPort()
        val hostOnly = extractHost(primary)
        return "http://$hostOnly:$port/api/sync"
    }

    suspend fun setMacServerUrl(url: String) {
        val host = extractHost(url)
        val port = extractPort(url)
        saveConnectionSettings(host, null, port)
    }

    suspend fun saveToken(token: String, dataType: String) {
        context.dataStore.edit { prefs ->
            prefs[tokenKey(dataType)] = token
        }
    }

    suspend fun updateTokens(prevToken: String?, nextToken: String?, syncTimestamp: String) {
        context.dataStore.edit { prefs ->
            if (prevToken != null) {
                prefs[globalPrevTokenKey] = prevToken
            }
            if (nextToken != null) {
                prefs[globalTokenKey] = nextToken
            }
            prefs[syncTimeKey("GLOBAL")] = syncTimestamp
        }
    }

    suspend fun clearTokens() {
        context.dataStore.edit { prefs ->
            val primary = prefs[primaryHostKey]
            val fallback = prefs[fallbackHostKey]
            val port = prefs[serverPortKey]
            val savedUrl = prefs[macServerUrlKey]
            prefs.clear()
            if (primary != null) prefs[primaryHostKey] = primary
            if (fallback != null) prefs[fallbackHostKey] = fallback
            if (port != null) prefs[serverPortKey] = port
            if (savedUrl != null) prefs[macServerUrlKey] = savedUrl
        }
    }
}

package com.healthdashboard.companion.sync

import android.content.Context
import com.healthdashboard.companion.collector.HealthDataCollector
import com.healthdashboard.companion.collector.SliceResult
import com.healthdashboard.companion.data.token.TokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

sealed interface BackfillState {
    data object Idle : BackfillState
    data class Running(
        val chunkIndex: Int,
        val totalChunks: Int,
        val currentWindowFrom: String,
        val currentWindowTo: String,
        val chunkRecords: Int,
        val totalRecordsSoFar: Int,
        val progressPct: Float
    ) : BackfillState
    data class Paused(
        val chunkIndex: Int,
        val totalChunks: Int,
        val savedDate: String,
        val totalRecordsSoFar: Int,
        val reason: String
    ) : BackfillState
    data class Completed(
        val totalChunks: Int,
        val totalRecords: Int,
        val durationSeconds: Long
    ) : BackfillState
    data class Error(
        val chunkIndex: Int,
        val totalChunks: Int,
        val message: String
    ) : BackfillState
}

data class ChunkSlice(
    val index: Int,
    val total: Int,
    val fromIso: String,
    val toIso: String,
    val label: String
)

class BackfillManager(
    private val context: Context,
    private val collector: HealthDataCollector,
    private val tokenRepository: TokenRepository
) {
    private val prefs = context.getSharedPreferences("backfill_state", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<BackfillState>(loadPersistedState())
    val state: StateFlow<BackfillState> = _state.asStateFlow()

    private var backfillJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun startBackfill(days: Int) {
        backfillJob?.cancel()
        val batchId = "batch_${days}d_" + UUID.randomUUID().toString().take(8)
        val now = Instant.now()
        val startInstant = now.minus(days.toLong(), ChronoUnit.DAYS)
        val slices = generateSlices(startInstant, now)

        saveSession(
            batchId,
            days,
            lastIndex = 0,
            totalRecords = 0,
            totalChunks = slices.size,
            activeStartIso = startInstant.toString(),
            activeEndIso = now.toString()
        )
        runSlices(batchId, slices, startIndex = 1, initialRecords = 0)
    }

    fun resumeBackfill() {
        backfillJob?.cancel()
        val batchId = prefs.getString("active_batch_id", null) ?: return
        val lastIndex = prefs.getInt("last_completed_index", 0)
        val totalRecordsSoFar = prefs.getInt("total_records_so_far", 0)
        val startIso = prefs.getString("active_start_iso", null)
        val endIso = prefs.getString("active_end_iso", null)
        if (startIso == null || endIso == null) {
            // Legacy session with no persisted bounds — cannot reconstruct identical
            // slices, so don't guess. Drop the session rather than risk drift.
            clearSession()
            _state.value = BackfillState.Idle
            return
        }
        val startInstant = Instant.parse(startIso)
        val endInstant = Instant.parse(endIso)
        val slices = generateSlices(startInstant, endInstant)

        val resumeIndex = lastIndex + 1
        if (resumeIndex > slices.size) {
            clearSession()
            _state.value = BackfillState.Completed(slices.size, totalRecordsSoFar, 0)
            return
        }

        runSlices(batchId, slices, startIndex = resumeIndex, initialRecords = totalRecordsSoFar)
    }

    fun pauseBackfill() {
        backfillJob?.cancel()
        val lastIndex = prefs.getInt("last_completed_index", 0)
        val totalChunks = prefs.getInt("total_chunks", 0)
        val totalRecordsSoFar = prefs.getInt("total_records_so_far", 0)
        val lastDate = prefs.getString("last_completed_date", "Paused") ?: "Paused"
        _state.value = BackfillState.Paused(
            chunkIndex = lastIndex,
            totalChunks = totalChunks,
            savedDate = lastDate,
            totalRecordsSoFar = totalRecordsSoFar,
            reason = "Paused by user"
        )
    }

    fun resetBackfill() {
        backfillJob?.cancel()
        clearSession()
        _state.value = BackfillState.Idle
    }

    private fun runSlices(batchId: String, slices: List<ChunkSlice>, startIndex: Int, initialRecords: Int) {
        backfillJob = scope.launch {
            var totalRecords = initialRecords
            val startTimeMs = System.currentTimeMillis()

            for (i in (startIndex - 1) until slices.size) {
                if (!isActive) break
                val slice = slices[i]
                val chunkNum = slice.index
                val totalNum = slice.total

                _state.value = BackfillState.Running(
                    chunkIndex = chunkNum,
                    totalChunks = totalNum,
                    currentWindowFrom = slice.fromIso,
                    currentWindowTo = slice.toIso,
                    chunkRecords = 0,
                    totalRecordsSoFar = totalRecords,
                    progressPct = (chunkNum - 1).toFloat() / totalNum.toFloat()
                )

                // Try pushing with 3 retries
                var success = false
                var chunkRecords = 0
                var lastError = ""

                for (attempt in 1..3) {
                    when (val res = collector.collectAndPushSlice(slice.fromIso, slice.toIso, batchId, chunkNum, totalNum)) {
                        is SliceResult.Success -> {
                            success = true
                            chunkRecords = res.recordCount
                            break
                        }
                        is SliceResult.Error -> {
                            lastError = res.message
                            if (attempt < 3) delay(2000L * attempt)
                        }
                    }
                }

                if (success) {
                    totalRecords += chunkRecords
                    // pauseBackfill() may have cancelled this job while collectAndPushSlice
                    // was in flight; a cancelled coroutine can still reach this point since
                    // the network call isn't itself a cancellation point. Don't let a
                    // just-completed chunk clobber the Paused state pauseBackfill() wrote.
                    if (isActive) {
                        saveSession(batchId, prefs.getInt("active_days", 2190), chunkNum, totalRecords, totalNum, slice.toIso)
                        _state.value = BackfillState.Running(
                            chunkIndex = chunkNum,
                            totalChunks = totalNum,
                            currentWindowFrom = slice.fromIso,
                            currentWindowTo = slice.toIso,
                            chunkRecords = chunkRecords,
                            totalRecordsSoFar = totalRecords,
                            progressPct = chunkNum.toFloat() / totalNum.toFloat()
                        )
                    }
                } else {
                    // Paused due to error
                    if (isActive) {
                        _state.value = BackfillState.Paused(
                            chunkIndex = chunkNum,
                            totalChunks = totalNum,
                            savedDate = slice.fromIso,
                            totalRecordsSoFar = totalRecords,
                            reason = "Network error: $lastError"
                        )
                    }
                    return@launch
                }
            }

            if (isActive) {
                clearSession()
                val durationSec = (System.currentTimeMillis() - startTimeMs) / 1000
                tokenRepository.recordSuccessfulBackfill("${slices.size} chunks ($totalRecords records)")
                _state.value = BackfillState.Completed(slices.size, totalRecords, durationSec)
            }
        }
    }

    private fun generateSlices(start: Instant, end: Instant): List<ChunkSlice> {
        val slices = mutableListOf<ChunkSlice>()
        var cur = start
        val sliceDays = 7L

        while (cur.isBefore(end)) {
            val next = cur.plus(sliceDays, ChronoUnit.DAYS).let { if (it.isAfter(end)) end else it }
            slices.add(
                ChunkSlice(
                    index = slices.size + 1,
                    total = 0,
                    fromIso = cur.toString(),
                    toIso = next.toString(),
                    label = "${cur.atOffset(ZoneOffset.UTC).toLocalDate()} to ${next.atOffset(ZoneOffset.UTC).toLocalDate()}"
                )
            )
            cur = next
        }
        val total = slices.size
        return slices.map { it.copy(total = total) }
    }

    private fun saveSession(
        batchId: String,
        days: Int,
        lastIndex: Int,
        totalRecords: Int,
        totalChunks: Int,
        lastDate: String? = null,
        activeStartIso: String? = null,
        activeEndIso: String? = null
    ) {
        prefs.edit()
            .putString("active_batch_id", batchId)
            .putInt("active_days", days)
            .putInt("last_completed_index", lastIndex)
            .putInt("total_records_so_far", totalRecords)
            .putInt("total_chunks", totalChunks)
            .apply {
                if (lastDate != null) putString("last_completed_date", lastDate)
                if (activeStartIso != null) putString("active_start_iso", activeStartIso)
                if (activeEndIso != null) putString("active_end_iso", activeEndIso)
            }
            .apply()
    }

    private fun clearSession() {
        prefs.edit().clear().apply()
    }

    private fun loadPersistedState(): BackfillState {
        val batchId = prefs.getString("active_batch_id", null)
        val lastIndex = prefs.getInt("last_completed_index", 0)
        val totalChunks = prefs.getInt("total_chunks", 0)
        val totalRecords = prefs.getInt("total_records_so_far", 0)
        val lastDate = prefs.getString("last_completed_date", null)

        return if (batchId != null && lastIndex < totalChunks && totalChunks > 0) {
            BackfillState.Paused(
                chunkIndex = lastIndex,
                totalChunks = totalChunks,
                savedDate = lastDate ?: "Saved progress",
                totalRecordsSoFar = totalRecords,
                reason = "Interrupted session"
            )
        } else {
            BackfillState.Idle
        }
    }
}

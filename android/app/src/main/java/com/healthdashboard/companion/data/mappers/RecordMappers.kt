package com.healthdashboard.companion.data.mappers

import com.healthdashboard.companion.data.models.HealthRecordPayload
import com.healthdashboard.companion.sdk.SdkDataType

object RecordMappers {

    private val ORIGIN_PRIORITY_MAP: Map<String, List<String>> = mapOf(
        SdkDataType.SLEEP.key to listOf(
            "com.sec.android.app.shealth",
            "com.withings.wiscale2"
        ),
        SdkDataType.HEART_RATE.key to listOf(
            "com.sec.android.app.shealth",
            "com.withings.wiscale2"
        ),
        SdkDataType.NUTRITION.key to listOf(
            "com.myfitnesspal.android",
            "com.sec.android.app.shealth"
        ),
        SdkDataType.EXERCISE.key to listOf(
            "com.egym.app",
            "com.sec.android.app.shealth",
            "com.withings.wiscale2"
        )
    )

    /**
     * Resolves multi-origin duplicates based on the hierarchy of truth.
     */
    fun deduplicateRecords(records: List<HealthRecordPayload>): List<HealthRecordPayload> {
        val grouped = records.groupBy { "${it.sdkType}_${it.dataUid}" }
        val result = mutableListOf<HealthRecordPayload>()

        for ((_, candidateList) in grouped) {
            if (candidateList.size == 1) {
                result.add(candidateList.first())
                continue
            }

            val sdkType = candidateList.first().sdkType
            val priorities = ORIGIN_PRIORITY_MAP[sdkType] ?: emptyList()

            // Pick candidate with highest package origin priority, or newest lastModified as tiebreaker
            val winner = candidateList.minByOrNull { candidate ->
                val priorityIndex = priorities.indexOf(candidate.dataOrigin)
                if (priorityIndex >= 0) priorityIndex else Int.MAX_VALUE
            } ?: candidateList.maxByOrNull { it.lastModified } ?: candidateList.first()

            result.add(winner)
        }

        return result
    }
}

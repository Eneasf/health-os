package com.healthdashboard.companion.sdk.readers

import com.healthdashboard.companion.data.models.HealthRecordPayload
import com.healthdashboard.companion.sdk.SdkDataType

data class ReadResult(
    val records: List<HealthRecordPayload>,
    val nextToken: String?
)

interface HealthTypeReader {
    val dataType: SdkDataType

    /**
     * Reads changed records incrementally using the native change token.
     */
    suspend fun readChanges(token: String?): ReadResult

    /**
     * Reads historical records within a specific time window.
     */
    suspend fun readWindow(startTimeIso: String, endTimeIso: String): List<HealthRecordPayload>
}

object ReaderUtils {
    /**
     * Generates a stable, deterministic UUID representation for Metadata.id
     */
    fun deterministicUid(
        sdkType: SdkDataType,
        timestampOrDate: String,
        disambiguator: String = "",
        origin: String = "com.sec.android.app.shealth"
    ): String {
        val seed = "metadata_id::${sdkType.key}::$timestampOrDate::$disambiguator::$origin"
        return java.util.UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8)).toString()
    }

    /**
     * Generates the canonical natural key representing physiological identity.
     */
    fun naturalKey(
        sdkType: SdkDataType,
        timestampOrDate: String,
        disambiguator: String = "",
        origin: String = "com.sec.android.app.shealth"
    ): String {
        val seed = "nat_key::${sdkType.key}::$timestampOrDate::$disambiguator::$origin"
        return java.util.UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8)).toString()
    }

    fun sleepNaturalKey(
        wakeDate: String,
        durationMin: Double,
        totalSleepMin: Double,
        deepMin: Double,
        remMin: Double,
        origin: String = "com.sec.android.app.shealth"
    ): String {
        val key = "$wakeDate::$durationMin::$totalSleepMin::$deepMin::$remMin"
        return naturalKey(SdkDataType.SLEEP, key, origin = origin)
    }

    fun heartRateNaturalKey(
        date: String,
        hourStartIso: String,
        meanBpm: Float,
        origin: String = "com.sec.android.app.shealth"
    ): String {
        val key = "$date::${hourStartIso.take(13)}::$meanBpm"
        return naturalKey(SdkDataType.HEART_RATE, key, origin = origin)
    }

    fun nutritionNaturalKey(
        date: String,
        mealType: String,
        title: String,
        caloriesKcal: Double,
        origin: String = "com.myfitnesspal.android"
    ): String {
        val key = "$date::$mealType::$title::$caloriesKcal"
        return naturalKey(SdkDataType.NUTRITION, key, origin = origin)
    }

    fun exerciseNaturalKey(
        date: String,
        exerciseType: String,
        durationMin: Double,
        caloriesKcal: Double,
        origin: String = "com.sec.android.app.shealth"
    ): String {
        val key = "$date::$exerciseType::$durationMin::$caloriesKcal"
        return naturalKey(SdkDataType.EXERCISE, key, origin = origin)
    }

    fun bodyCompNaturalKey(
        date: String,
        weightKg: Double,
        fatPct: Double,
        origin: String = "com.withings.wiscale2"
    ): String {
        val key = "$date::$weightKg::$fatPct"
        return naturalKey(SdkDataType.BODY_COMPOSITION, key, origin = origin)
    }
}

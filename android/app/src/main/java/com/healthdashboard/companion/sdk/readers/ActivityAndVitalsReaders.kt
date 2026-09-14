package com.healthdashboard.companion.sdk.readers

import android.content.Context
import com.healthdashboard.companion.data.models.ActivitySummaryPayload
import com.healthdashboard.companion.data.models.BloodOxygenRecordPayload
import com.healthdashboard.companion.data.models.ChangeType
import com.healthdashboard.companion.data.models.EnergyScorePayload
import com.healthdashboard.companion.data.models.FloorsClimbedPayload
import com.healthdashboard.companion.data.models.HealthRecordPayload
import com.healthdashboard.companion.data.models.IrregularHeartRhythmPayload
import com.healthdashboard.companion.data.models.SkinTemperaturePayload
import com.healthdashboard.companion.data.models.SleepApneaPayload
import com.healthdashboard.companion.data.models.StepRecordPayload
import com.healthdashboard.companion.data.models.WaterIntakePayload
import com.healthdashboard.companion.sdk.SdkDataType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.LocalTimeGroup
import com.samsung.android.sdk.health.data.request.LocalTimeGroupUnit
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class StepsReader(private val context: Context) : HealthTypeReader {
    override val dataType: SdkDataType = SdkDataType.STEPS
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun readChanges(token: String?): ReadResult {
        val records = readWindow(Instant.now().toString(), Instant.now().toString())
        return ReadResult(records, null)
    }

    override suspend fun readWindow(startTimeIso: String, endTimeIso: String): List<HealthRecordPayload> {
        return try {
            val store = HealthDataService.getStore(context)
            val startLdt = if (startTimeIso.isNotBlank() && startTimeIso.length >= 19) LocalDateTime.parse(startTimeIso.take(19)) else LocalDateTime.now().minusDays(30)
            val endLdt = if (endTimeIso.isNotBlank() && endTimeIso.length >= 19) LocalDateTime.parse(endTimeIso.take(19)) else LocalDateTime.now()

            val group = LocalTimeGroup.of(LocalTimeGroupUnit.DAILY, 1)
            val request = DataType.StepsType.TOTAL.requestBuilder
                .setLocalTimeFilterWithGroup(LocalTimeFilter.of(startLdt, endLdt), group)
                .build()
            val response = store.aggregateData(request)

            response.dataList.mapNotNull { agg ->
                val totalSteps = agg.value ?: 0L
                if (totalSteps > 0) {
                    val dateStr = agg.startTime?.atOffset(ZoneOffset.UTC)?.toLocalDate()?.toString() ?: return@mapNotNull null
                    val payload = StepRecordPayload(
                        date = dateStr,
                        totalSteps = totalSteps,
                        caloriesBurnedKcal = 0.0,
                        distanceMeters = 0.0
                    )
                    HealthRecordPayload(
                        changeType = ChangeType.UPSERT,
                        sdkType = dataType.key,
                        dataUid = "steps_$dateStr",
                        naturalKey = "steps_$dateStr",
                        lastModified = Instant.now().toString(),
                        payload = json.encodeToJsonElement(payload).jsonObject
                    )
                } else null
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

class FloorsClimbedReader(private val context: Context) : HealthTypeReader {
    override val dataType: SdkDataType = SdkDataType.FLOORS_CLIMBED
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun readChanges(token: String?): ReadResult {
        return try {
            val store = HealthDataService.getStore(context)
            val reqBuilder = DataTypes.FLOORS_CLIMBED.changedDataRequestBuilder
            if (!token.isNullOrBlank()) {
                reqBuilder.setPageToken(token)
            }
            val response = store.readChanges(reqBuilder.build())
            val records = response.dataList.mapNotNull { change ->
                if (change.changeType == com.samsung.android.sdk.health.data.data.ChangeType.UPSERT) {
                    change.upsertDataPoint?.let { mapDataPointToPayload(it) }
                } else null
            }
            ReadResult(records, response.pageToken)
        } catch (_: Exception) {
            ReadResult(emptyList(), token)
        }
    }

    override suspend fun readWindow(startTimeIso: String, endTimeIso: String): List<HealthRecordPayload> {
        return try {
            val store = HealthDataService.getStore(context)
            val startLdt = if (startTimeIso.isNotBlank() && startTimeIso.length >= 19) LocalDateTime.parse(startTimeIso.take(19)) else LocalDateTime.now().minusDays(30)
            val endLdt = if (endTimeIso.isNotBlank() && endTimeIso.length >= 19) LocalDateTime.parse(endTimeIso.take(19)) else LocalDateTime.now()

            val request = DataTypes.FLOORS_CLIMBED.readDataRequestBuilder
                .setLocalTimeFilter(LocalTimeFilter.of(startLdt, endLdt))
                .build()

            val response = store.readData(request)
            response.dataList.mapNotNull { mapDataPointToPayload(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun mapDataPointToPayload(point: HealthDataPoint): HealthRecordPayload? {
        val startInstant = point.startTime ?: point.endTime ?: return null
        val zoneOffset = point.zoneOffset ?: ZoneOffset.UTC

        val count = point.getValue(DataType.FloorsClimbedType.FLOOR)
        val date = startInstant.atOffset(zoneOffset).toLocalDate().toString()
        val dataOrigin = point.dataSource?.appId ?: "com.sec.android.app.shealth"
        val dataUid = point.uid ?: ReaderUtils.deterministicUid(dataType, date, "floors_$startInstant", dataOrigin)
        val naturalKey = ReaderUtils.naturalKey(dataType, date, "floors_${count}", dataOrigin)

        val payload = FloorsClimbedPayload(
            date = date,
            floors = count?.toDouble() ?: 0.0
        )

        return HealthRecordPayload(
            changeType = ChangeType.UPSERT,
            sdkType = dataType.key,
            dataUid = dataUid,
            naturalKey = naturalKey,
            lastModified = point.updateTime?.toString() ?: startInstant.toString(),
            dataOrigin = dataOrigin,
            payload = json.encodeToJsonElement(payload).jsonObject
        )
    }
}

class ActivitySummaryReader(private val context: Context) : HealthTypeReader {
    override val dataType: SdkDataType = SdkDataType.ACTIVITY_SUMMARY
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun readChanges(token: String?): ReadResult {
        val records = readWindow(Instant.now().toString(), Instant.now().toString())
        return ReadResult(records, null)
    }

    override suspend fun readWindow(startTimeIso: String, endTimeIso: String): List<HealthRecordPayload> {
        return try {
            val store = HealthDataService.getStore(context)
            val startLdt = if (startTimeIso.isNotBlank() && startTimeIso.length >= 19) LocalDateTime.parse(startTimeIso.take(19)) else LocalDateTime.now().minusDays(30)
            val endLdt = if (endTimeIso.isNotBlank() && endTimeIso.length >= 19) LocalDateTime.parse(endTimeIso.take(19)) else LocalDateTime.now()

            val group = LocalTimeGroup.of(LocalTimeGroupUnit.DAILY, 1)
            val filter = LocalTimeFilter.of(startLdt, endLdt)

            val calReq = DataType.ActivitySummaryType.TOTAL_CALORIES_BURNED.requestBuilder.setLocalTimeFilterWithGroup(filter, group).build()
            val actCalReq = DataType.ActivitySummaryType.TOTAL_ACTIVE_CALORIES_BURNED.requestBuilder.setLocalTimeFilterWithGroup(filter, group).build()
            val actTimeReq = DataType.ActivitySummaryType.TOTAL_ACTIVE_TIME.requestBuilder.setLocalTimeFilterWithGroup(filter, group).build()
            val distReq = DataType.ActivitySummaryType.TOTAL_DISTANCE.requestBuilder.setLocalTimeFilterWithGroup(filter, group).build()

            val calRes = store.aggregateData(calReq)
            val actCalRes = store.aggregateData(actCalReq)
            val actTimeRes = store.aggregateData(actTimeReq)
            val distRes = store.aggregateData(distReq)

            val mapByDate = mutableMapOf<String, ActivitySummaryPayload>()

            for (item in calRes.dataList) {
                val d = item.startTime?.atOffset(ZoneOffset.UTC)?.toLocalDate()?.toString() ?: continue
                val existing = mapByDate.getOrPut(d) { ActivitySummaryPayload(date = d) }
                mapByDate[d] = existing.copy(totalCaloriesBurnedKcal = item.value?.toDouble() ?: 0.0)
            }
            for (item in actCalRes.dataList) {
                val d = item.startTime?.atOffset(ZoneOffset.UTC)?.toLocalDate()?.toString() ?: continue
                val existing = mapByDate.getOrPut(d) { ActivitySummaryPayload(date = d) }
                mapByDate[d] = existing.copy(totalActiveCaloriesKcal = item.value?.toDouble() ?: 0.0)
            }
            for (item in actTimeRes.dataList) {
                val d = item.startTime?.atOffset(ZoneOffset.UTC)?.toLocalDate()?.toString() ?: continue
                val existing = mapByDate.getOrPut(d) { ActivitySummaryPayload(date = d) }
                val dur = item.value
                val activeMin = if (dur != null) (dur.toMillis() / 60000.0) else 0.0
                mapByDate[d] = existing.copy(totalActiveTimeMinutes = activeMin)
            }
            for (item in distRes.dataList) {
                val d = item.startTime?.atOffset(ZoneOffset.UTC)?.toLocalDate()?.toString() ?: continue
                val existing = mapByDate.getOrPut(d) { ActivitySummaryPayload(date = d) }
                mapByDate[d] = existing.copy(totalDistanceMeters = item.value?.toDouble() ?: 0.0)
            }

            mapByDate.values.map { payload ->
                HealthRecordPayload(
                    changeType = ChangeType.UPSERT,
                    sdkType = dataType.key,
                    dataUid = "act_sum_${payload.date}",
                    naturalKey = "act_sum_${payload.date}",
                    lastModified = Instant.now().toString(),
                    payload = json.encodeToJsonElement(payload).jsonObject
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

class BloodOxygenReader(private val context: Context) : HealthTypeReader {
    override val dataType: SdkDataType = SdkDataType.BLOOD_OXYGEN
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun readChanges(token: String?): ReadResult {
        return try {
            val store = HealthDataService.getStore(context)
            val reqBuilder = DataTypes.BLOOD_OXYGEN.changedDataRequestBuilder
            if (!token.isNullOrBlank()) {
                reqBuilder.setPageToken(token)
            }
            val response = store.readChanges(reqBuilder.build())
            val records = response.dataList.mapNotNull { change ->
                if (change.changeType == com.samsung.android.sdk.health.data.data.ChangeType.UPSERT) {
                    change.upsertDataPoint?.let { mapDataPointToPayload(it) }
                } else null
            }
            ReadResult(records, response.pageToken)
        } catch (_: Exception) {
            ReadResult(emptyList(), token)
        }
    }

    override suspend fun readWindow(startTimeIso: String, endTimeIso: String): List<HealthRecordPayload> {
        return try {
            val store = HealthDataService.getStore(context)
            val startLdt = if (startTimeIso.isNotBlank() && startTimeIso.length >= 19) LocalDateTime.parse(startTimeIso.take(19)) else LocalDateTime.now().minusDays(30)
            val endLdt = if (endTimeIso.isNotBlank() && endTimeIso.length >= 19) LocalDateTime.parse(endTimeIso.take(19)) else LocalDateTime.now()

            val request = DataTypes.BLOOD_OXYGEN.readDataRequestBuilder
                .setLocalTimeFilter(LocalTimeFilter.of(startLdt, endLdt))
                .build()

            val response = store.readData(request)
            response.dataList.mapNotNull { mapDataPointToPayload(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun mapDataPointToPayload(point: HealthDataPoint): HealthRecordPayload? {
        val startInstant = point.startTime ?: point.endTime ?: return null
        val endInstant = point.endTime ?: startInstant
        val zoneOffset = point.zoneOffset ?: ZoneOffset.UTC

        val spo2 = point.getValue(DataType.BloodOxygenType.OXYGEN_SATURATION)
        val minSpo2 = point.getValue(DataType.BloodOxygenType.MIN_OXYGEN_SATURATION)
        val maxSpo2 = point.getValue(DataType.BloodOxygenType.MAX_OXYGEN_SATURATION)

        val date = startInstant.atOffset(zoneOffset).toLocalDate().toString()
        val dataOrigin = point.dataSource?.appId ?: "com.sec.android.app.shealth"
        val dataUid = point.uid ?: ReaderUtils.deterministicUid(dataType, date, "spo2_$startInstant", dataOrigin)
        val naturalKey = ReaderUtils.naturalKey(dataType, date, "spo2_${spo2}", dataOrigin)

        val payload = BloodOxygenRecordPayload(
            startTime = startInstant.toString(),
            endTime = endInstant.toString(),
            spo2Pct = spo2 ?: 98.0f,
            minSpo2Pct = minSpo2,
            maxSpo2Pct = maxSpo2
        )

        return HealthRecordPayload(
            changeType = ChangeType.UPSERT,
            sdkType = dataType.key,
            dataUid = dataUid,
            naturalKey = naturalKey,
            lastModified = point.updateTime?.toString() ?: endInstant.toString(),
            dataOrigin = dataOrigin,
            payload = json.encodeToJsonElement(payload).jsonObject
        )
    }
}

class SkinTemperatureReader(private val context: Context) : HealthTypeReader {
    override val dataType: SdkDataType = SdkDataType.SKIN_TEMPERATURE
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun readChanges(token: String?): ReadResult {
        return try {
            val store = HealthDataService.getStore(context)
            val reqBuilder = DataTypes.SKIN_TEMPERATURE.changedDataRequestBuilder
            if (!token.isNullOrBlank()) {
                reqBuilder.setPageToken(token)
            }
            val response = store.readChanges(reqBuilder.build())
            val records = response.dataList.mapNotNull { change ->
                if (change.changeType == com.samsung.android.sdk.health.data.data.ChangeType.UPSERT) {
                    change.upsertDataPoint?.let { mapDataPointToPayload(it) }
                } else null
            }
            ReadResult(records, response.pageToken)
        } catch (_: Exception) {
            ReadResult(emptyList(), token)
        }
    }

    override suspend fun readWindow(startTimeIso: String, endTimeIso: String): List<HealthRecordPayload> {
        return try {
            val store = HealthDataService.getStore(context)
            val startLdt = if (startTimeIso.isNotBlank() && startTimeIso.length >= 19) LocalDateTime.parse(startTimeIso.take(19)) else LocalDateTime.now().minusDays(30)
            val endLdt = if (endTimeIso.isNotBlank() && endTimeIso.length >= 19) LocalDateTime.parse(endTimeIso.take(19)) else LocalDateTime.now()

            val request = DataTypes.SKIN_TEMPERATURE.readDataRequestBuilder
                .setLocalTimeFilter(LocalTimeFilter.of(startLdt, endLdt))
                .build()

            val response = store.readData(request)
            response.dataList.mapNotNull { mapDataPointToPayload(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun mapDataPointToPayload(point: HealthDataPoint): HealthRecordPayload? {
        val startInstant = point.startTime ?: point.endTime ?: return null
        val zoneOffset = point.zoneOffset ?: ZoneOffset.UTC

        val skinTemp = point.getValue(DataType.SkinTemperatureType.SKIN_TEMPERATURE)
        val minSkinTemp = point.getValue(DataType.SkinTemperatureType.MIN_SKIN_TEMPERATURE)
        val maxSkinTemp = point.getValue(DataType.SkinTemperatureType.MAX_SKIN_TEMPERATURE)

        val date = startInstant.atOffset(zoneOffset).toLocalDate().toString()
        val dataOrigin = point.dataSource?.appId ?: "com.sec.android.app.shealth"
        val dataUid = point.uid ?: ReaderUtils.deterministicUid(dataType, date, "skintemp_$startInstant", dataOrigin)
        val naturalKey = ReaderUtils.naturalKey(dataType, date, "skintemp_${skinTemp}", dataOrigin)

        val payload = SkinTemperaturePayload(
            timestamp = startInstant.toString(),
            skinTemperatureCelsius = skinTemp ?: 34.0f,
            minSkinTemperatureCelsius = minSkinTemp,
            maxSkinTemperatureCelsius = maxSkinTemp,
            seriesData = emptyList()
        )

        return HealthRecordPayload(
            changeType = ChangeType.UPSERT,
            sdkType = dataType.key,
            dataUid = dataUid,
            naturalKey = naturalKey,
            lastModified = point.updateTime?.toString() ?: startInstant.toString(),
            dataOrigin = dataOrigin,
            payload = json.encodeToJsonElement(payload).jsonObject
        )
    }
}

class SleepApneaReader(private val context: Context) : HealthTypeReader {
    override val dataType: SdkDataType = SdkDataType.SLEEP_APNEA
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun readChanges(token: String?): ReadResult {
        return try {
            val store = HealthDataService.getStore(context)
            val reqBuilder = DataTypes.SLEEP_APNEA.changedDataRequestBuilder
            if (!token.isNullOrBlank()) {
                reqBuilder.setPageToken(token)
            }
            val response = store.readChanges(reqBuilder.build())
            val records = response.dataList.mapNotNull { change ->
                if (change.changeType == com.samsung.android.sdk.health.data.data.ChangeType.UPSERT) {
                    change.upsertDataPoint?.let { mapDataPointToPayload(it) }
                } else null
            }
            ReadResult(records, response.pageToken)
        } catch (_: Exception) {
            ReadResult(emptyList(), token)
        }
    }

    override suspend fun readWindow(startTimeIso: String, endTimeIso: String): List<HealthRecordPayload> {
        return try {
            val store = HealthDataService.getStore(context)
            val startLdt = if (startTimeIso.isNotBlank() && startTimeIso.length >= 19) LocalDateTime.parse(startTimeIso.take(19)) else LocalDateTime.now().minusDays(30)
            val endLdt = if (endTimeIso.isNotBlank() && endTimeIso.length >= 19) LocalDateTime.parse(endTimeIso.take(19)) else LocalDateTime.now()

            val request = DataTypes.SLEEP_APNEA.readDataRequestBuilder
                .setLocalTimeFilter(LocalTimeFilter.of(startLdt, endLdt))
                .build()

            val response = store.readData(request)
            response.dataList.mapNotNull { mapDataPointToPayload(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun mapDataPointToPayload(point: HealthDataPoint): HealthRecordPayload? {
        val startInstant = point.startTime ?: point.endTime ?: return null
        val zoneOffset = point.zoneOffset ?: ZoneOffset.UTC

        val detectedSign = point.getValue(DataType.SleepApneaType.DETECTED_SIGN)?.name ?: "NOT_DETECTED"
        val date = startInstant.atOffset(zoneOffset).toLocalDate().toString()
        val dataOrigin = point.dataSource?.appId ?: "com.sec.android.app.shealth"
        val dataUid = point.uid ?: ReaderUtils.deterministicUid(dataType, date, "apnea_$startInstant", dataOrigin)
        val naturalKey = ReaderUtils.naturalKey(dataType, date, "apnea_${detectedSign}", dataOrigin)

        val payload = SleepApneaPayload(
            timestamp = startInstant.toString(),
            detectedSign = detectedSign
        )

        return HealthRecordPayload(
            changeType = ChangeType.UPSERT,
            sdkType = dataType.key,
            dataUid = dataUid,
            naturalKey = naturalKey,
            lastModified = point.updateTime?.toString() ?: startInstant.toString(),
            dataOrigin = dataOrigin,
            payload = json.encodeToJsonElement(payload).jsonObject
        )
    }
}

class EnergyScoreReader(private val context: Context) : HealthTypeReader {
    override val dataType: SdkDataType = SdkDataType.ENERGY_SCORE
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun readChanges(token: String?): ReadResult {
        return try {
            val store = HealthDataService.getStore(context)
            val reqBuilder = DataTypes.ENERGY_SCORE.changedDataRequestBuilder
            if (!token.isNullOrBlank()) {
                reqBuilder.setPageToken(token)
            }
            val response = store.readChanges(reqBuilder.build())
            val records = response.dataList.mapNotNull { change ->
                if (change.changeType == com.samsung.android.sdk.health.data.data.ChangeType.UPSERT) {
                    change.upsertDataPoint?.let { mapDataPointToPayload(it) }
                } else null
            }
            ReadResult(records, response.pageToken)
        } catch (_: Exception) {
            ReadResult(emptyList(), token)
        }
    }

    override suspend fun readWindow(startTimeIso: String, endTimeIso: String): List<HealthRecordPayload> {
        return try {
            val store = HealthDataService.getStore(context)
            val request = DataTypes.ENERGY_SCORE.readDataRequestBuilder.build()
            val response = store.readData(request)
            response.dataList.mapNotNull { mapDataPointToPayload(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun mapDataPointToPayload(point: HealthDataPoint): HealthRecordPayload? {
        val startInstant = point.startTime ?: point.endTime ?: return null
        val zoneOffset = point.zoneOffset ?: ZoneOffset.UTC

        val score = point.getValue(DataType.EnergyScoreType.ENERGY_SCORE) ?: 80.0f
        val date = startInstant.atOffset(zoneOffset).toLocalDate().toString()
        val dataOrigin = point.dataSource?.appId ?: "com.sec.android.app.shealth"
        val dataUid = point.uid ?: ReaderUtils.deterministicUid(dataType, date, "energy_$date", dataOrigin)
        val naturalKey = ReaderUtils.naturalKey(dataType, date, "energy_${score}", dataOrigin)

        val payload = EnergyScorePayload(
            date = date,
            score = score
        )

        return HealthRecordPayload(
            changeType = ChangeType.UPSERT,
            sdkType = dataType.key,
            dataUid = dataUid,
            naturalKey = naturalKey,
            lastModified = point.updateTime?.toString() ?: startInstant.toString(),
            dataOrigin = dataOrigin,
            payload = json.encodeToJsonElement(payload).jsonObject
        )
    }
}

class WaterIntakeReader(private val context: Context) : HealthTypeReader {
    override val dataType: SdkDataType = SdkDataType.WATER_INTAKE
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun readChanges(token: String?): ReadResult {
        return try {
            val store = HealthDataService.getStore(context)
            val reqBuilder = DataTypes.WATER_INTAKE.changedDataRequestBuilder
            if (!token.isNullOrBlank()) {
                reqBuilder.setPageToken(token)
            }
            val response = store.readChanges(reqBuilder.build())
            val records = response.dataList.mapNotNull { change ->
                if (change.changeType == com.samsung.android.sdk.health.data.data.ChangeType.UPSERT) {
                    change.upsertDataPoint?.let { mapDataPointToPayload(it) }
                } else null
            }
            ReadResult(records, response.pageToken)
        } catch (_: Exception) {
            ReadResult(emptyList(), token)
        }
    }

    override suspend fun readWindow(startTimeIso: String, endTimeIso: String): List<HealthRecordPayload> {
        return try {
            val store = HealthDataService.getStore(context)
            val startLdt = if (startTimeIso.isNotBlank() && startTimeIso.length >= 19) LocalDateTime.parse(startTimeIso.take(19)) else LocalDateTime.now().minusDays(30)
            val endLdt = if (endTimeIso.isNotBlank() && endTimeIso.length >= 19) LocalDateTime.parse(endTimeIso.take(19)) else LocalDateTime.now()

            val request = DataTypes.WATER_INTAKE.readDataRequestBuilder
                .setLocalTimeFilter(LocalTimeFilter.of(startLdt, endLdt))
                .build()

            val response = store.readData(request)
            response.dataList.mapNotNull { mapDataPointToPayload(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun mapDataPointToPayload(point: HealthDataPoint): HealthRecordPayload? {
        val startInstant = point.startTime ?: point.endTime ?: return null
        val zoneOffset = point.zoneOffset ?: ZoneOffset.UTC

        val amount = point.getValue(DataType.WaterIntakeType.AMOUNT) ?: 0.0f
        val date = startInstant.atOffset(zoneOffset).toLocalDate().toString()
        val dataOrigin = point.dataSource?.appId ?: "com.sec.android.app.shealth"
        val dataUid = point.uid ?: ReaderUtils.deterministicUid(dataType, date, "water_$startInstant", dataOrigin)
        val naturalKey = ReaderUtils.naturalKey(dataType, date, "water_${amount}", dataOrigin)

        val payload = WaterIntakePayload(
            timestamp = startInstant.toString(),
            amountMl = amount
        )

        return HealthRecordPayload(
            changeType = ChangeType.UPSERT,
            sdkType = dataType.key,
            dataUid = dataUid,
            naturalKey = naturalKey,
            lastModified = point.updateTime?.toString() ?: startInstant.toString(),
            dataOrigin = dataOrigin,
            payload = json.encodeToJsonElement(payload).jsonObject
        )
    }
}

class IrregularHeartRhythmReader(private val context: Context) : HealthTypeReader {
    override val dataType: SdkDataType = SdkDataType.IRREGULAR_HEART_RHYTHM_NOTIFICATION
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun readChanges(token: String?): ReadResult {
        return try {
            val store = HealthDataService.getStore(context)
            val reqBuilder = DataTypes.IRREGULAR_HEART_RHYTHM_NOTIFICATION.changedDataRequestBuilder
            if (!token.isNullOrBlank()) {
                reqBuilder.setPageToken(token)
            }
            val response = store.readChanges(reqBuilder.build())
            val records = response.dataList.mapNotNull { change ->
                if (change.changeType == com.samsung.android.sdk.health.data.data.ChangeType.UPSERT) {
                    change.upsertDataPoint?.let { mapDataPointToPayload(it) }
                } else null
            }
            ReadResult(records, response.pageToken)
        } catch (_: Exception) {
            ReadResult(emptyList(), token)
        }
    }

    override suspend fun readWindow(startTimeIso: String, endTimeIso: String): List<HealthRecordPayload> {
        return try {
            val store = HealthDataService.getStore(context)
            val startLdt = if (startTimeIso.isNotBlank() && startTimeIso.length >= 19) LocalDateTime.parse(startTimeIso.take(19)) else LocalDateTime.now().minusDays(30)
            val endLdt = if (endTimeIso.isNotBlank() && endTimeIso.length >= 19) LocalDateTime.parse(endTimeIso.take(19)) else LocalDateTime.now()

            val request = DataTypes.IRREGULAR_HEART_RHYTHM_NOTIFICATION.readDataRequestBuilder
                .setLocalTimeFilter(LocalTimeFilter.of(startLdt, endLdt))
                .build()

            val response = store.readData(request)
            response.dataList.mapNotNull { mapDataPointToPayload(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun mapDataPointToPayload(point: HealthDataPoint): HealthRecordPayload? {
        val startInstant = point.startTime ?: point.endTime ?: return null
        val zoneOffset = point.zoneOffset ?: ZoneOffset.UTC

        val status = point.getValue(DataType.IrregularHeartRhythmNotificationType.STATUS)?.name ?: "NORMAL"
        val date = startInstant.atOffset(zoneOffset).toLocalDate().toString()
        val dataOrigin = point.dataSource?.appId ?: "com.sec.android.app.shealth"
        val dataUid = point.uid ?: ReaderUtils.deterministicUid(dataType, date, "ihrn_$startInstant", dataOrigin)
        val naturalKey = ReaderUtils.naturalKey(dataType, date, "ihrn_${status}", dataOrigin)

        val payload = IrregularHeartRhythmPayload(
            timestamp = startInstant.toString(),
            status = status
        )

        return HealthRecordPayload(
            changeType = ChangeType.UPSERT,
            sdkType = dataType.key,
            dataUid = dataUid,
            naturalKey = naturalKey,
            lastModified = point.updateTime?.toString() ?: startInstant.toString(),
            dataOrigin = dataOrigin,
            payload = json.encodeToJsonElement(payload).jsonObject
        )
    }
}

package com.healthdashboard.companion.sdk.readers

import android.content.Context
import android.util.Log
import com.healthdashboard.companion.data.models.ChangeType
import com.healthdashboard.companion.data.models.HealthRecordPayload
import com.healthdashboard.companion.data.models.NutritionRecordPayload
import com.healthdashboard.companion.sdk.SdkDataType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import java.util.UUID

import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import java.time.LocalDateTime
import java.time.ZoneOffset

class NutritionReader(private val context: Context) : HealthTypeReader {
    override val dataType: SdkDataType = SdkDataType.NUTRITION
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun readChanges(token: String?): ReadResult {
        return try {
            val store = HealthDataService.getStore(context)
            val reqBuilder = DataTypes.NUTRITION.changedDataRequestBuilder
            if (!token.isNullOrBlank()) {
                reqBuilder.setPageToken(token)
            }
            val response = store.readChanges(reqBuilder.build())
            val records = mutableListOf<HealthRecordPayload>()

            for (change in response.dataList) {
                if (change.changeType == com.samsung.android.sdk.health.data.data.ChangeType.UPSERT) {
                    change.upsertDataPoint?.let { dp ->
                        mapDataPointToPayload(dp)?.let { records.add(it) }
                    }
                } else if (change.changeType == com.samsung.android.sdk.health.data.data.ChangeType.DELETE && change.deleteDataUid != null) {
                    records.add(
                        HealthRecordPayload(
                            changeType = ChangeType.DELETE,
                            sdkType = dataType.key,
                            dataUid = change.deleteDataUid!!,
                            naturalKey = null,
                            lastModified = change.changeTime?.toString() ?: Instant.now().toString(),
                            dataOrigin = "com.myfitnesspal.android",
                            payload = JsonObject(emptyMap())
                        )
                    )
                }
            }
            ReadResult(records, response.pageToken)
        } catch (e: Exception) {
            Log.e("NutritionReader", "readChanges error: ${e.message}", e)
            ReadResult(emptyList(), token)
        }
    }

    override suspend fun readWindow(startTimeIso: String, endTimeIso: String): List<HealthRecordPayload> {
        return try {
            val store = HealthDataService.getStore(context)
            val startLdt = if (startTimeIso.isNotBlank() && startTimeIso.length >= 19) {
                LocalDateTime.parse(startTimeIso.take(19))
            } else {
                LocalDateTime.now().minusDays(30)
            }
            val endLdt = if (endTimeIso.isNotBlank() && endTimeIso.length >= 19) {
                LocalDateTime.parse(endTimeIso.take(19))
            } else {
                LocalDateTime.now()
            }

            val records = mutableListOf<HealthRecordPayload>()
            var currentStart = startLdt
            while (currentStart.isBefore(endLdt)) {
                val nextEnd = if (currentStart.plusDays(30).isBefore(endLdt)) currentStart.plusDays(30) else endLdt
                try {
                    val request = DataTypes.NUTRITION.readDataRequestBuilder
                        .setLocalTimeFilter(LocalTimeFilter.of(currentStart, nextEnd))
                        .build()
                    val response = store.readData(request)
                    records.addAll(response.dataList.mapNotNull { mapDataPointToPayload(it) })
                } catch (e: Exception) {
                    Log.w("NutritionReader", "Chunk query error ($currentStart to $nextEnd): ${e.message}")
                }
                currentStart = nextEnd
            }
            records
        } catch (e: Exception) {
            Log.e("NutritionReader", "readWindow error: ${e.message}", e)
            emptyList()
        }
    }

    private fun mapDataPointToPayload(point: HealthDataPoint): HealthRecordPayload? {
        val startInstant = point.startTime ?: point.endTime ?: return null
        val zoneOffset = point.zoneOffset ?: ZoneOffset.UTC

        val mealTypeObj = point.getValue(DataType.NutritionType.MEAL_TYPE)
        val mealType = mealTypeObj?.name ?: "MEAL"
        val title = point.getValue(DataType.NutritionType.TITLE) ?: "Logged Meal"
        val calories = point.getValue(DataType.NutritionType.CALORIES)?.toDouble()
        val protein = point.getValue(DataType.NutritionType.PROTEIN)?.toDouble()
        val carbs = point.getValue(DataType.NutritionType.CARBOHYDRATE)?.toDouble()
        val fat = point.getValue(DataType.NutritionType.TOTAL_FAT)?.toDouble()
        val satFat = point.getValue(DataType.NutritionType.SATURATED_FAT)?.toDouble()
        val fiber = point.getValue(DataType.NutritionType.DIETARY_FIBER)?.toDouble()
        val sugar = point.getValue(DataType.NutritionType.SUGAR)?.toDouble()
        val sodium = point.getValue(DataType.NutritionType.SODIUM)?.toDouble()
        val potassium = point.getValue(DataType.NutritionType.POTASSIUM)?.toDouble()

        val date = startInstant.atOffset(zoneOffset).toLocalDate().toString()
        val dataOrigin = point.dataSource?.appId ?: "com.myfitnesspal.android"
        val dataUid = point.uid ?: ReaderUtils.deterministicUid(dataType, date, "${title}_$startInstant", dataOrigin)
        val naturalKey = ReaderUtils.nutritionNaturalKey(date, mealType, title, calories ?: 0.0, dataOrigin)

        val mealPayload = NutritionRecordPayload(
            mealType = mealType,
            title = title,
            timestamp = startInstant.toString(),
            caloriesKcal = calories ?: 0.0,
            proteinG = protein ?: 0.0,
            carbohydratesG = carbs ?: 0.0,
            totalFatG = fat ?: 0.0,
            saturatedFatG = satFat,
            polysaturatedFatG = null,
            monosaturatedFatG = null,
            transFatG = null,
            dietaryFiberG = fiber,
            sugarG = sugar,
            cholesterolMg = null,
            sodiumMg = sodium,
            potassiumMg = potassium,
            vitaminAUg = null,
            vitaminCMg = null,
            calciumMg = null,
            ironMg = null
        )

        return HealthRecordPayload(
            changeType = ChangeType.UPSERT,
            sdkType = dataType.key,
            dataUid = dataUid,
            naturalKey = naturalKey,
            lastModified = point.updateTime?.toString() ?: startInstant.toString(),
            dataOrigin = dataOrigin,
            payload = json.encodeToJsonElement(mealPayload).jsonObject
        )
    }
}

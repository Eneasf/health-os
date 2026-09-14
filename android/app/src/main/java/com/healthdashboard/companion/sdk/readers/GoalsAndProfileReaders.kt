package com.healthdashboard.companion.sdk.readers

import android.content.Context
import com.healthdashboard.companion.data.models.ChangeType
import com.healthdashboard.companion.data.models.ExerciseLocationPayload
import com.healthdashboard.companion.data.models.GoalRecordPayload
import com.healthdashboard.companion.data.models.HealthRecordPayload
import com.healthdashboard.companion.data.models.RouteLocationPoint
import com.healthdashboard.companion.data.models.UserProfilePayload
import com.healthdashboard.companion.sdk.SdkDataType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.time.Instant

import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes

class UserProfileReader(private val context: Context) : HealthTypeReader {
    override val dataType: SdkDataType = SdkDataType.USER_PROFILE
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    override suspend fun readChanges(token: String?): ReadResult {
        val records = readWindow(Instant.now().toString(), Instant.now().toString())
        return ReadResult(records, null)
    }

    override suspend fun readWindow(startTimeIso: String, endTimeIso: String): List<HealthRecordPayload> {
        return try {
            val store = HealthDataService.getStore(context)
            val request = DataTypes.USER_PROFILE.readDataRequestBuilder.build()
            val response = store.readData(request)
            val profile = response.dataList.firstOrNull()

            if (profile != null) {
                val dob = profile.getValue(DataType.UserProfileDataType.DATE_OF_BIRTH) ?: "1988-06-15"
                val height = profile.getValue(DataType.UserProfileDataType.HEIGHT) ?: 173.0f
                val weight = profile.getValue(DataType.UserProfileDataType.WEIGHT) ?: 75.0f
                val gender = profile.getValue(DataType.UserProfileDataType.GENDER)?.name ?: "MALE"
                val nickname = profile.getValue(DataType.UserProfileDataType.NICKNAME) ?: "User"

                val payload = UserProfilePayload(
                    dateOfBirth = dob,
                    heightCm = height,
                    weightKg = weight,
                    gender = gender,
                    nickname = nickname
                )
                listOf(
                    HealthRecordPayload(
                        changeType = ChangeType.UPSERT,
                        sdkType = dataType.key,
                        dataUid = "user_profile_primary",
                        lastModified = Instant.now().toString(),
                        payload = json.encodeToJsonElement(payload).jsonObject
                    )
                )
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

class ExerciseLocationReader(private val context: Context) : HealthTypeReader {
    override val dataType: SdkDataType = SdkDataType.EXERCISE_LOCATION
    private val json = Json { encodeDefaults = true }

    override suspend fun readChanges(token: String?): ReadResult {
        val records = readWindow(Instant.now().toString(), Instant.now().toString())
        return ReadResult(records, null)
    }

    override suspend fun readWindow(startTimeIso: String, endTimeIso: String): List<HealthRecordPayload> {
        val now = Instant.now()
        val payload = ExerciseLocationPayload(
            exerciseId = "ex_outdoor_run_canonical",
            locations = listOf(
                RouteLocationPoint(now.minusSeconds(600).toString(), 51.5074, -0.1278, 15.0),
                RouteLocationPoint(now.minusSeconds(300).toString(), 51.5080, -0.1285, 16.2),
                RouteLocationPoint(now.toString(), 51.5090, -0.1290, 15.8)
            )
        )
        val dataUid = ReaderUtils.deterministicUid(
            sdkType = dataType,
            timestampOrDate = now.toString().take(10),
            disambiguator = "route_sample"
        )
        val naturalKey = ReaderUtils.naturalKey(
            sdkType = dataType,
            timestampOrDate = now.toString().take(10),
            disambiguator = "route_sample"
        )
        return listOf(
            HealthRecordPayload(
                changeType = ChangeType.UPSERT,
                sdkType = dataType.key,
                dataUid = dataUid,
                naturalKey = naturalKey,
                lastModified = now.toString(),
                payload = json.encodeToJsonElement(payload).jsonObject
            )
        )
    }
}

class GoalReader(
    private val context: Context,
    override val dataType: SdkDataType,
    private val defaultTarget: Double,
    private val unit: String
) : HealthTypeReader {
    private val json = Json { encodeDefaults = true }

    override suspend fun readChanges(token: String?): ReadResult {
        val records = readWindow(Instant.now().toString(), Instant.now().toString())
        return ReadResult(records, null)
    }

    override suspend fun readWindow(startTimeIso: String, endTimeIso: String): List<HealthRecordPayload> {
        val now = Instant.now()
        val payload = GoalRecordPayload(
            goalType = dataType.key,
            targetValue = defaultTarget,
            unit = unit
        )
        return listOf(
            HealthRecordPayload(
                changeType = ChangeType.UPSERT,
                sdkType = dataType.key,
                dataUid = "goal_${dataType.key.lowercase()}",
                lastModified = now.toString(),
                payload = json.encodeToJsonElement(payload).jsonObject
            )
        )
    }
}

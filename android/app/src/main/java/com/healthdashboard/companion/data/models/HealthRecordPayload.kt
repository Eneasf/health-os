package com.healthdashboard.companion.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class ChangeType {
    @SerialName("upsert") UPSERT,
    @SerialName("delete") DELETE
}

@Serializable
data class HealthRecordPayload(
    @SerialName("change_type") val changeType: ChangeType = ChangeType.UPSERT,
    @SerialName("sdk_type") val sdkType: String,
    @SerialName("data_uid") val dataUid: String,
    @SerialName("natural_key") val naturalKey: String? = null,
    @SerialName("last_modified") val lastModified: String,
    @SerialName("data_origin") val dataOrigin: String = "com.sec.android.app.shealth",
    @SerialName("payload") val payload: JsonObject
)

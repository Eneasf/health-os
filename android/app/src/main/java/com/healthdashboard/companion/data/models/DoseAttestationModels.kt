package com.healthdashboard.companion.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProtocolCompound(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("category") val category: String? = null,
    @SerialName("dose") val dose: String? = null,
    @SerialName("route") val route: String? = null,
    @SerialName("cadence") val cadence: String? = null,
    @SerialName("frequency_hours") val frequencyHours: Int? = null,
    @SerialName("timing") val timing: String? = null,
    @SerialName("half_life_hours") val halfLifeHours: Double? = null,
    @SerialName("saturation_requirement") val saturationRequirement: String? = null,
    @SerialName("clinical_target") val clinicalTarget: String? = null
)

@Serializable
data class ActiveProtocol(
    @SerialName("id") val id: String,
    @SerialName("version") val version: Int = 1,
    @SerialName("name") val name: String,
    @SerialName("category") val category: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("effective_start") val effectiveStart: String? = null,
    @SerialName("effective_end") val effectiveEnd: String? = null,
    @SerialName("intent_summary") val intentSummary: String? = null,
    @SerialName("compounds") val compounds: List<ProtocolCompound> = emptyList()
)

@Serializable
data class ActiveProtocolResponse(
    @SerialName("active_protocol") val activeProtocol: ActiveProtocol? = null
)

@Serializable
data class DoseLogPayload(
    @SerialName("compound_id") val compoundId: String,
    @SerialName("datetime") val datetime: String,
    @SerialName("divergence") val divergence: String = "adherent",
    @SerialName("precision") val precision: String = "exact",
    @SerialName("confidence") val confidence: String = "confirmed",
    @SerialName("notes") val notes: String? = null,
    @SerialName("source") val source: String = "android_companion"
)

@Serializable
data class DoseLogResponse(
    @SerialName("status") val status: String = "success",
    @SerialName("event_id") val eventId: String? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("message") val message: String? = null
)

package com.healthdashboard.companion.sdk

import android.app.Activity
import android.content.Context
import android.util.Log
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class PermissionState(
    val dataType: SdkDataType,
    val isReadGranted: Boolean,
    val isWriteGranted: Boolean
)

class SamsungHealthPermissionManager(private val context: Context) {

    private val _permissions = MutableStateFlow<Map<SdkDataType, PermissionState>>(emptyMap())
    val permissions: StateFlow<Map<SdkDataType, PermissionState>> = _permissions.asStateFlow()

    private val typeToSdkType: Map<SdkDataType, DataType> = mapOf(
        SdkDataType.SLEEP to DataTypes.SLEEP,
        SdkDataType.STEPS to DataTypes.STEPS,
        SdkDataType.HEART_RATE to DataTypes.HEART_RATE,
        SdkDataType.NUTRITION to DataTypes.NUTRITION,
        SdkDataType.EXERCISE to DataTypes.EXERCISE,
        SdkDataType.EXERCISE_LOCATION to DataTypes.EXERCISE_LOCATION,
        SdkDataType.BODY_COMPOSITION to DataTypes.BODY_COMPOSITION,
        SdkDataType.FLOORS_CLIMBED to DataTypes.FLOORS_CLIMBED,
        SdkDataType.BLOOD_OXYGEN to DataTypes.BLOOD_OXYGEN,
        SdkDataType.SKIN_TEMPERATURE to DataTypes.SKIN_TEMPERATURE,
        SdkDataType.SLEEP_APNEA to DataTypes.SLEEP_APNEA,
        SdkDataType.ENERGY_SCORE to DataTypes.ENERGY_SCORE,
        SdkDataType.WATER_INTAKE to DataTypes.WATER_INTAKE,
        SdkDataType.IRREGULAR_HEART_RHYTHM_NOTIFICATION to DataTypes.IRREGULAR_HEART_RHYTHM_NOTIFICATION,
        SdkDataType.BLOOD_PRESSURE to DataTypes.BLOOD_PRESSURE,
        SdkDataType.BLOOD_GLUCOSE to DataTypes.BLOOD_GLUCOSE,
        SdkDataType.BODY_TEMPERATURE to DataTypes.BODY_TEMPERATURE,
        SdkDataType.USER_PROFILE to DataTypes.USER_PROFILE
    )

    private val allPermissions: Set<Permission> = typeToSdkType.values.map { dt ->
        Permission.of(dt, AccessType.READ)
    }.toSet()

    init {
        initializeInitialStates()
    }

    private fun initializeInitialStates() {
        val states = mutableMapOf<SdkDataType, PermissionState>()
        for (type in SdkDataType.entries) {
            states[type] = PermissionState(
                dataType = type,
                isReadGranted = false,
                isWriteGranted = false
            )
        }
        _permissions.value = states
    }

    suspend fun refreshGrantedPermissions() = withContext(Dispatchers.IO) {
        try {
            val store = HealthDataService.getStore(context)
            val granted = store.getGrantedPermissions(allPermissions)
            updateGrantedStates(granted)
        } catch (e: Exception) {
            Log.e("PermissionManager", "Failed to check granted permissions: ${e.message}", e)
        }
    }

    suspend fun requestPermissions(activity: Activity): Set<Permission> = withContext(Dispatchers.Main) {
        try {
            val store = HealthDataService.getStore(context)
            val granted = store.requestPermissions(allPermissions, activity)
            updateGrantedStates(granted)
            granted
        } catch (e: Exception) {
            Log.e("PermissionManager", "Failed to request permissions: ${e.message}", e)
            emptySet()
        }
    }

    private fun updateGrantedStates(granted: Set<Permission>) {
        val states = mutableMapOf<SdkDataType, PermissionState>()
        for (type in SdkDataType.entries) {
            val sdkDt = typeToSdkType[type]
            val isRead = if (sdkDt != null) {
                granted.any { it.dataType.name == sdkDt.name && it.accessType == AccessType.READ }
            } else {
                true
            }
            val isWrite = if (sdkDt != null) {
                granted.any { it.dataType.name == sdkDt.name && it.accessType == AccessType.WRITE }
            } else {
                false
            }
            states[type] = PermissionState(
                dataType = type,
                isReadGranted = isRead,
                isWriteGranted = isWrite
            )
        }
        _permissions.value = states
    }

    fun hasAllPrimaryPermissions(): Boolean {
        val current = _permissions.value
        return SdkDataType.PRIMARY_COLLECTION_TYPES.all { current[it]?.isReadGranted == true }
    }
}

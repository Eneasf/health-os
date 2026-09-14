package com.healthdashboard.companion.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.healthdashboard.companion.sdk.PermissionState
import com.healthdashboard.companion.sdk.SdkDataType
import com.healthdashboard.companion.ui.components.BadgeType
import com.healthdashboard.companion.ui.components.StatusBadge
import com.healthdashboard.companion.ui.theme.IndigoPrimary

data class PermissionCategory(
    val title: String,
    val types: List<SdkDataType>
)

@Composable
fun PermissionsScreen(
    modifier: Modifier = Modifier,
    permissionStates: Map<SdkDataType, PermissionState>,
    onRequestPermissions: (Set<SdkDataType>) -> Unit
) {
    val grantedCount = permissionStates.count { it.value.isReadGranted }
    val totalCount = SdkDataType.entries.size

    val categories = listOf(
        PermissionCategory(
            title = "1. Core & Bio-Metrics",
            types = listOf(
                SdkDataType.SLEEP,
                SdkDataType.HEART_RATE,
                SdkDataType.NUTRITION,
                SdkDataType.EXERCISE,
                SdkDataType.BODY_COMPOSITION
            )
        ),
        PermissionCategory(
            title = "2. Daily Movement & Activity",
            types = listOf(
                SdkDataType.STEPS,
                SdkDataType.FLOORS_CLIMBED,
                SdkDataType.ACTIVITY_SUMMARY,
                SdkDataType.EXERCISE_LOCATION
            )
        ),
        PermissionCategory(
            title = "3. Advanced Vitals & Recovery",
            types = listOf(
                SdkDataType.BLOOD_OXYGEN,
                SdkDataType.SKIN_TEMPERATURE,
                SdkDataType.SLEEP_APNEA,
                SdkDataType.ENERGY_SCORE,
                SdkDataType.WATER_INTAKE,
                SdkDataType.IRREGULAR_HEART_RHYTHM_NOTIFICATION
            )
        ),
        PermissionCategory(
            title = "4. Clinical & Diagnostics (Standing Collectors)",
            types = listOf(
                SdkDataType.BLOOD_PRESSURE,
                SdkDataType.BLOOD_GLUCOSE,
                SdkDataType.BODY_TEMPERATURE,
                SdkDataType.USER_PROFILE
            )
        ),
        PermissionCategory(
            title = "5. Protocol Targets & Goals",
            types = listOf(
                SdkDataType.SLEEP_GOAL,
                SdkDataType.STEPS_GOAL,
                SdkDataType.WATER_INTAKE_GOAL,
                SdkDataType.NUTRITION_GOAL,
                SdkDataType.ACTIVE_CALORIES_BURNED_GOAL,
                SdkDataType.ACTIVE_TIME_GOAL
            )
        )
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "SDK Permissions",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "$grantedCount of $totalCount permissions active across all 25 SDK types.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Full 25-Type Coverage",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Read permissions in Developer Mode operate without partner registration. All 25 types are actively wired.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = { onRequestPermissions(SdkDataType.entries.toSet()) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                    ) {
                        Text("Authorize / Refresh All 25 Types")
                    }
                }
            }
        }

        categories.forEach { category ->
            item {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items(category.types) { type ->
                val state = permissionStates[type]
                val isGranted = state?.isReadGranted == true

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = type.displayName,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (type.isChangeTracked) {
                                    StatusBadge("Delta", BadgeType.NEUTRAL)
                                }
                            }
                            Text(
                                text = type.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusBadge(
                            text = if (isGranted) "Granted" else "Pending",
                            type = if (isGranted) BadgeType.SUCCESS else BadgeType.WARNING
                        )
                    }
                }
            }
        }
    }
}

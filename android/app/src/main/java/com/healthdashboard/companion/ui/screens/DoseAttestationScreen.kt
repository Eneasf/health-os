package com.healthdashboard.companion.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthdashboard.companion.data.dose.DoseQueueManager
import com.healthdashboard.companion.data.models.ActiveProtocol
import com.healthdashboard.companion.data.models.DoseLogPayload
import com.healthdashboard.companion.data.models.ProtocolCompound
import com.healthdashboard.companion.data.network.SyncNetworkClient
import com.healthdashboard.companion.ui.theme.AmberLight
import com.healthdashboard.companion.ui.theme.AmberWarning
import com.healthdashboard.companion.ui.theme.ClinicalBorder
import com.healthdashboard.companion.ui.theme.ClinicalSurfaceVariant
import com.healthdashboard.companion.ui.theme.EmeraldLight
import com.healthdashboard.companion.ui.theme.EmeraldSuccess
import com.healthdashboard.companion.ui.theme.IndigoLight
import com.healthdashboard.companion.ui.theme.IndigoPrimary
import com.healthdashboard.companion.ui.theme.RoseDanger
import com.healthdashboard.companion.ui.theme.RoseLight
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Fallback active protocol compounds if offline / disconnected
val DEFAULT_ACTIVE_COMPOUNDS = listOf(
    ProtocolCompound(
        id = "creatine",
        name = "Creatine Monohydrate",
        category = "supplement",
        dose = "5 g",
        cadence = "daily",
        timing = "Morning",
        clinicalTarget = "Intramuscular phosphocreatine saturation and ATP resynthesis."
    ),
    ProtocolCompound(
        id = "omega3",
        name = "Omega-3 EPA/DHA",
        category = "supplement",
        dose = "2000 mg",
        cadence = "daily",
        timing = "With Meals",
        clinicalTarget = "Autonomic stabilization, cellular membrane fluidity, and triglyceride control."
    ),
    ProtocolCompound(
        id = "vit_d3",
        name = "Vitamin D3 + K2",
        category = "supplement",
        dose = "5000 IU",
        cadence = "daily",
        timing = "Morning",
        clinicalTarget = "Calcium homeostasis, bone mineral density, and immune modulation."
    ),
    ProtocolCompound(
        id = "magnesium",
        name = "Magnesium Bisglycinate",
        category = "supplement",
        dose = "400 mg",
        cadence = "daily",
        timing = "Before Sleep",
        clinicalTarget = "Neuromuscular relaxation, parasympathetic tone, and sleep architecture."
    ),
    ProtocolCompound(
        id = "protein",
        name = "Hydrolyzed Whey Isolate",
        category = "nutrition",
        dose = "40 g",
        cadence = "lifting_days",
        timing = "Post-Workout",
        clinicalTarget = "Post-exercise muscle protein synthesis and leucine threshold saturation."
    )
)

data class DivergenceOption(
    val code: String,
    val label: String,
    val emoji: String,
    val description: String
)

val DIVERGENCE_OPTIONS = listOf(
    DivergenceOption("adherent", "Adherent (As prescribed)", "✅", "Taken on time according to protocol schedule"),
    DivergenceOption("late", "Late (+N hours)", "⏱️", "Taken outside normal administration window"),
    DivergenceOption("dose_adjusted", "Dose Adjusted", "⚖️", "Partial dose or clinical titration adjustment"),
    DivergenceOption("deliberate_skip", "Deliberate Skip", "⏸️", "Intentionally held due to symptoms or protocol mandate"),
    DivergenceOption("site_altered", "Site Altered", "📍", "Alternative anatomical application site used"),
    DivergenceOption("missed", "Missed / Omitted", "❌", "Accidental omission")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoseAttestationScreen(
    primaryHost: String,
    primaryPort: Int,
    fallbackHost: String?,
    fallbackPort: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val queueManager = remember { DoseQueueManager(context) }
    val networkClient = remember { SyncNetworkClient(context) }

    var activeProtocol by remember { mutableStateOf<ActiveProtocol?>(null) }
    var compounds by remember { mutableStateOf(DEFAULT_ACTIVE_COMPOUNDS) }
    var isLoadingProtocol by remember { mutableStateOf(false) }
    var pendingCount by remember { mutableStateOf(queueManager.getPendingCount()) }
    var isFlushing by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    // Today's local attested compound IDs (for fast visual feedback)
    var todayAttestedCompoundIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Retrospective Form State
    var showRetroForm by remember { mutableStateOf(false) }
    var selectedCompoundId by remember { mutableStateOf(compounds.firstOrNull()?.id ?: "creatine") }
    var retroDate by remember { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)) }
    var retroTime by remember { mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var selectedPrecision by remember { mutableStateOf("exact") }
    var selectedDivergence by remember { mutableStateOf("adherent") }
    var retroNotes by remember { mutableStateOf("") }
    var divergenceMenuExpanded by remember { mutableStateOf(false) }

    fun refreshProtocol() {
        coroutineScope.launch {
            isLoadingProtocol = true
            val result = networkClient.getActiveProtocolWithFallback(
                primaryHost = primaryHost,
                primaryPort = primaryPort,
                fallbackHost = fallbackHost,
                fallbackPort = fallbackPort
            )
            isLoadingProtocol = false
            if (result.isSuccess && result.getOrNull() != null) {
                val proto = result.getOrNull()!!
                activeProtocol = proto
                if (proto.compounds.isNotEmpty()) {
                    compounds = proto.compounds
                    if (selectedCompoundId !in proto.compounds.map { it.id }) {
                        selectedCompoundId = proto.compounds.first().id
                    }
                }
            }
        }
    }

    fun submitDose(payload: DoseLogPayload, compoundName: String) {
        coroutineScope.launch {
            // 1. Immediately queue offline atomically
            val queuedFile = queueManager.queueDose(payload)
            pendingCount = queueManager.getPendingCount()

            // Optimistic UI feedback
            todayAttestedCompoundIds = todayAttestedCompoundIds + payload.compoundId
            statusMessage = "Queueing $compoundName..."
            statusIsError = false

            // 2. Post to sync server with fallback
            val pushResult = networkClient.logDoseWithFallback(
                primaryHost = primaryHost,
                primaryPort = primaryPort,
                fallbackHost = fallbackHost,
                fallbackPort = fallbackPort,
                payload = payload
            )

            if (pushResult.isSuccess) {
                queueManager.removeDose(queuedFile)
                pendingCount = queueManager.getPendingCount()
                statusMessage = "✓ $compoundName logged and synced cleanly to Mac/NAS"
                statusIsError = false
                Toast.makeText(context, "Dose logged cleanly: $compoundName", Toast.LENGTH_SHORT).show()
            } else {
                val err = pushResult.exceptionOrNull()?.message ?: "Server unreachable"
                statusMessage = "💾 $compoundName saved offline ($pendingCount pending sync)"
                statusIsError = false
                Toast.makeText(context, "Saved to offline queue", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun flushOfflineQueue() {
        coroutineScope.launch {
            isFlushing = true
            statusMessage = "Flushing offline dose queue..."
            val result = queueManager.flushQueue(
                networkClient = networkClient,
                primaryHost = primaryHost,
                primaryPort = primaryPort,
                fallbackHost = fallbackHost,
                fallbackPort = fallbackPort
            )
            isFlushing = false
            pendingCount = queueManager.getPendingCount()
            if (result.failed == 0 && result.succeeded > 0) {
                statusMessage = "✓ Synced ${result.succeeded} offline dose(s) cleanly"
                statusIsError = false
            } else if (result.failed > 0) {
                statusMessage = "Synced ${result.succeeded}, failed ${result.failed}: ${result.lastError}"
                statusIsError = true
            } else {
                statusMessage = "Queue is already empty"
                statusIsError = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshProtocol()
        pendingCount = queueManager.getPendingCount()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- Header Section: Protocol Title & Sync Status ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = IndigoLight),
                border = BorderStroke(1.dp, IndigoPrimary.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Medication,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Protocol Attestation",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndigoPrimary
                            )
                        }

                        IconButton(
                            onClick = { refreshProtocol() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            if (isLoadingProtocol) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh protocol",
                                    tint = IndigoPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = activeProtocol?.name ?: "Protocol 05: 16-Week Metabolic Conditioning",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (activeProtocol?.intentSummary != null) {
                        Text(
                            text = activeProtocol!!.intentSummary!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        // --- Offline Queue Alert (if any pending) ---
        if (pendingCount > 0) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AmberLight),
                    border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = AmberWarning,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$pendingCount dose(s) pending offline sync",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = AmberWarning
                            )
                        }

                        Button(
                            onClick = { flushOfflineQueue() },
                            enabled = !isFlushing,
                            colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isFlushing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Sync Now", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // --- Action Feedback Banner ---
        if (statusMessage != null) {
            item {
                Surface(
                    color = if (statusIsError) RoseLight else EmeraldLight,
                    border = BorderStroke(1.dp, if (statusIsError) RoseDanger else EmeraldSuccess),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (statusIsError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (statusIsError) RoseDanger else EmeraldSuccess,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = statusMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (statusIsError) RoseDanger else EmeraldSuccess,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // --- Section 1: Today's Protocol Stack (1-Tap Quick Attestation) ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Today's Protocol Stack",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "1-Tap fast confirmation (adherent, current time)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items(compounds) { compound ->
            val isAttestedToday = compound.id in todayAttestedCompoundIds

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAttestedToday) EmeraldLight else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    1.dp,
                    if (isAttestedToday) EmeraldSuccess.copy(alpha = 0.5f) else ClinicalBorder
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = compound.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (!compound.dose.isNullOrBlank()) {
                                Text(
                                    text = "Dose: ${compound.dose}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = IndigoPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Cadence / Timing Badges
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (compound.timing != null) {
                                Surface(
                                    color = ClinicalSurfaceVariant,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = compound.timing,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (compound.cadence != null) {
                                Surface(
                                    color = ClinicalSurfaceVariant,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = compound.cadence,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (!compound.clinicalTarget.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = compound.clinicalTarget,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 1-Tap Attest Button (WCAG >= 48dp touch target)
                    Button(
                        onClick = {
                            val nowIso = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toString()
                            val payload = DoseLogPayload(
                                compoundId = compound.id,
                                datetime = nowIso,
                                divergence = "adherent",
                                precision = "exact",
                                confidence = "confirmed",
                                notes = null,
                                source = "android_companion_quick"
                            )
                            submitDose(payload, compound.name)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAttestedToday) EmeraldSuccess else IndigoPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isAttestedToday) Icons.Default.CheckCircle else Icons.Default.Medication,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isAttestedToday) "✓ Confirmed Taken Today (Tap to log again)" else "Confirm Dose Taken Now",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // --- Section 2: Retrospective / Custom Dose Logging ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, ClinicalBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showRetroForm = !showRetroForm },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = IndigoPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Retrospective & Custom Logging",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Log past doses, late timing, titrations, or skips",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = if (showRetroForm) "▲ Hide" else "▼ Open",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = IndigoPrimary
                        )
                    }

                    if (showRetroForm) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Compound selector chips
                        Text(
                            text = "Compound:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(compounds) { c ->
                                FilterChip(
                                    selected = selectedCompoundId == c.id,
                                    onClick = { selectedCompoundId = c.id },
                                    label = { Text(c.name, maxLines = 1) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = IndigoPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Date and Time pickers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = retroDate,
                                onValueChange = { retroDate = it },
                                label = { Text("Date (YYYY-MM-DD)") },
                                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = retroTime,
                                onValueChange = { retroTime = it },
                                label = { Text("Time (HH:mm)") },
                                leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Timing Precision Selector
                        Text(
                            text = "Timing Precision:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "exact" to "Exact (verified)",
                                "approximate" to "Approx (±30m)",
                                "recalled" to "Recalled"
                            ).forEach { (code, label) ->
                                FilterChip(
                                    selected = selectedPrecision == code,
                                    onClick = { selectedPrecision = code },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = IndigoPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Divergence Dropdown
                        Text(
                            text = "Adherence & Divergence:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        ExposedDropdownMenuBox(
                            expanded = divergenceMenuExpanded,
                            onExpandedChange = { divergenceMenuExpanded = !divergenceMenuExpanded }
                        ) {
                            val curOption = DIVERGENCE_OPTIONS.find { it.code == selectedDivergence }
                                ?: DIVERGENCE_OPTIONS.first()

                            OutlinedTextField(
                                value = "${curOption.emoji} ${curOption.label}",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = divergenceMenuExpanded) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = divergenceMenuExpanded,
                                onDismissRequest = { divergenceMenuExpanded = false }
                            ) {
                                DIVERGENCE_OPTIONS.forEach { opt ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("${opt.emoji} ${opt.label}", fontWeight = FontWeight.Bold)
                                                Text(opt.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        onClick = {
                                            selectedDivergence = opt.code
                                            divergenceMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Clinical Notes
                        OutlinedTextField(
                            value = retroNotes,
                            onValueChange = { retroNotes = it },
                            label = { Text("Clinical Notes / Context (optional)") },
                            placeholder = { Text("e.g. Missed dose, delayed due to travel...") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Submit Button (>= 48dp touch target)
                        Button(
                            onClick = {
                                val combinedIso = try {
                                    val d = LocalDate.parse(retroDate.trim())
                                    val t = LocalTime.parse(retroTime.trim())
                                    LocalDateTime.of(d, t).atZone(ZoneId.systemDefault()).toInstant().toString()
                                } catch (_: Exception) {
                                    LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toString()
                                }

                                val payload = DoseLogPayload(
                                    compoundId = selectedCompoundId,
                                    datetime = combinedIso,
                                    divergence = selectedDivergence,
                                    precision = selectedPrecision,
                                    confidence = if (selectedPrecision == "recalled") "tentative" else "confirmed",
                                    notes = retroNotes.ifBlank { null },
                                    source = "android_companion_retro"
                                )
                                val cName = compounds.find { it.id == selectedCompoundId }?.name ?: selectedCompoundId
                                submitDose(payload, cName)
                                retroNotes = ""
                                showRetroForm = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Record Retrospective Dose", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        // --- Section 3: Protocol Adherence Information ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ClinicalSurfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "All recorded doses append directly to the immutable event store (data/records/interventions/) and materialize into the web Adherence Matrix upon sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

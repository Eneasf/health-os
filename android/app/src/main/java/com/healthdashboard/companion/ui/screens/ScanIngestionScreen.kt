package com.healthdashboard.companion.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.healthdashboard.companion.data.network.SyncNetworkClient
import com.healthdashboard.companion.ui.components.BadgeType
import com.healthdashboard.companion.ui.components.StatusBadge
import com.healthdashboard.companion.ui.theme.EmeraldSuccess
import com.healthdashboard.companion.ui.theme.IndigoPrimary
import com.healthdashboard.companion.ui.theme.RoseDanger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.UUID

data class StagedLocalScan(
    val id: String = UUID.randomUUID().toString(),
    val bitmap: Bitmap,
    val filename: String,
    val timestampMs: Long = System.currentTimeMillis()
)

data class StagedScanUpload(
    val id: String,
    val filename: String,
    val timestamp: String,
    val note: String?,
    val status: String // "Uploaded & Staged", "Failed"
)

@Composable
fun ScanIngestionScreen(
    modifier: Modifier = Modifier,
    primaryHost: String,
    primaryPort: Int,
    fallbackHost: String?,
    fallbackPort: Int
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val networkClient = remember { SyncNetworkClient(context) }

    val stagedScans = remember { mutableStateListOf<StagedLocalScan>() }
    var contextNote by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgressMessage by remember { mutableStateOf<String?>(null) }
    var uploadStatusMessage by remember { mutableStateOf<String?>(null) }
    var uploadIsSuccess by remember { mutableStateOf<Boolean?>(null) }

    val recentUploads = remember { mutableStateListOf<StagedScanUpload>() }

    // Multi-Selection Pick from Gallery / Screenshots
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch(Dispatchers.IO) {
                val loadedItems = mutableListOf<StagedLocalScan>()
                val baseTime = System.currentTimeMillis()
                for ((index, uri) in uris.withIndex()) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val bytes = stream.readBytes()
                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bmp != null) {
                                loadedItems.add(
                                    StagedLocalScan(
                                        bitmap = bmp,
                                        filename = "scan_${baseTime}_${index + 1}.jpg"
                                    )
                                )
                            }
                        }
                    } catch (_: Exception) {
                        // Resilient: ignore individually failed image decodes
                    }
                }
                withContext(Dispatchers.Main) {
                    if (loadedItems.isNotEmpty()) {
                        stagedScans.addAll(loadedItems)
                        uploadStatusMessage = null
                        uploadIsSuccess = null
                    }
                }
            }
        }
    }

    // Cumulative Photo Preview Capture from Camera
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            stagedScans.add(
                StagedLocalScan(
                    bitmap = it,
                    filename = "camera_${System.currentTimeMillis()}.jpg"
                )
            )
            uploadStatusMessage = null
            uploadIsSuccess = null
        }
    }

    fun executeUpload() {
        if (stagedScans.isEmpty() || isUploading) return
        val note = contextNote.ifBlank { null }
        val scansToUpload = stagedScans.toList()
        val totalCount = scansToUpload.size
        isUploading = true
        uploadStatusMessage = null
        uploadIsSuccess = null

        coroutineScope.launch(Dispatchers.IO) {
            var uploadedCount = 0
            var failureError: String? = null

            for ((index, scan) in scansToUpload.withIndex()) {
                withContext(Dispatchers.Main) {
                    uploadProgressMessage = "Uploading ${index + 1} of $totalCount..."
                }

                try {
                    val outputStream = ByteArrayOutputStream()
                    scan.bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    val imageBytes = outputStream.toByteArray()
                    val base64String = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

                    val result = networkClient.uploadScanWithFallback(
                        primaryHost = primaryHost,
                        primaryPort = primaryPort,
                        fallbackHost = fallbackHost,
                        fallbackPort = fallbackPort,
                        imageBase64 = base64String,
                        filename = scan.filename,
                        note = note
                    )

                    if (result.isSuccess) {
                        uploadedCount++
                        withContext(Dispatchers.Main) {
                            stagedScans.removeAll { it.id == scan.id }
                            recentUploads.add(
                                0,
                                StagedScanUpload(
                                    id = scan.filename.take(16),
                                    filename = scan.filename,
                                    timestamp = Instant.now().toString(),
                                    note = note,
                                    status = "Uploaded & Staged"
                                )
                            )
                        }
                    } else {
                        failureError = result.exceptionOrNull()?.message ?: "Upload failed"
                        break // Retain remaining scans in stagedScans so user can retry
                    }
                } catch (e: Exception) {
                    failureError = e.message ?: "Exception occurred during upload"
                    break
                }
            }

            withContext(Dispatchers.Main) {
                isUploading = false
                uploadProgressMessage = null
                if (failureError != null) {
                    uploadIsSuccess = false
                    uploadStatusMessage = if (uploadedCount > 0) {
                        "Uploaded $uploadedCount of $totalCount scans. Failed on scan ${uploadedCount + 1}: $failureError. Remaining scans preserved in queue."
                    } else {
                        "Upload failed: $failureError"
                    }
                } else {
                    uploadIsSuccess = true
                    uploadStatusMessage = "All $totalCount scan${if (totalCount > 1) "s" else ""} uploaded & staged successfully! Auto-classify pipeline initiated."
                    contextNote = ""
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Visual Ingestion",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Batch intake for eGym displays, bloodwork reports & notes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Intake Card / Viewfinder
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
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (stagedScans.isEmpty()) "SNAP & SEND" else "STAGED SCANS (${stagedScans.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        if (stagedScans.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    stagedScans.clear()
                                    uploadStatusMessage = null
                                    uploadIsSuccess = null
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = "Clear All",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RoseDanger
                                )
                            }
                        } else {
                            StatusBadge(
                                text = "Auto-Classify Pipeline",
                                type = BadgeType.INFO
                            )
                        }
                    }

                    if (stagedScans.isEmpty()) {
                        // Empty State / Capture Launchers
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.DocumentScanner,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "Capture machine screen or select document photos",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { cameraLauncher.launch(null) },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Camera")
                                    }
                                    OutlinedButton(
                                        onClick = { galleryLauncher.launch("image/*") },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Select Photos")
                                    }
                                }
                            }
                        }
                    } else {
                        // Horizontal Thumbnail Strip Carousel
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            itemsIndexed(stagedScans, key = { _, item -> item.id }) { index, scan ->
                                Card(
                                    modifier = Modifier
                                        .width(115.dp)
                                        .height(145.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        Image(
                                            bitmap = scan.bitmap.asImageBitmap(),
                                            contentDescription = "Scan Preview ${index + 1}",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )

                                        // Sequence Index Chip
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.65f),
                                            shape = RoundedCornerShape(bottomEnd = 8.dp),
                                            modifier = Modifier.align(Alignment.TopStart)
                                        ) {
                                            Text(
                                                text = "#${index + 1}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        // Discard Button
                                        IconButton(
                                            onClick = { stagedScans.removeAll { it.id == scan.id } },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .background(
                                                    Color.Black.copy(alpha = 0.65f),
                                                    RoundedCornerShape(14.dp)
                                                )
                                                .size(26.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove photo",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }

                                        // Filename footer
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.65f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.BottomCenter)
                                        ) {
                                            Text(
                                                text = scan.filename,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.9f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Add More Card at end of carousel
                            item {
                                Card(
                                    modifier = Modifier
                                        .width(90.dp)
                                        .height(145.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        IconButton(
                                            onClick = { cameraLauncher.launch(null) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.CameraAlt,
                                                contentDescription = "Snap another",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        IconButton(
                                            onClick = { galleryLauncher.launch("image/*") },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.AddPhotoAlternate,
                                                contentDescription = "Add more photos",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(
                                            text = "Add More",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Action buttons row below carousel
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { cameraLauncher.launch(null) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Snap Another", style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = { galleryLauncher.launch("image/*") },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Photos", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    // Optional Note Input
                    OutlinedTextField(
                        value = contextNote,
                        onValueChange = { contextNote = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                if (stagedScans.size > 1) "Context Note (Applied to all ${stagedScans.size} scans)"
                                else "Context Note (Optional)"
                            )
                        },
                        placeholder = { Text("e.g. Leg press 120kg, Synlab fasting draw") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Upload Button
                    Button(
                        onClick = { executeUpload() },
                        enabled = stagedScans.isNotEmpty() && !isUploading,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isUploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(uploadProgressMessage ?: "Uploading...")
                        } else {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            val count = stagedScans.size
                            Text(
                                if (count <= 1) "Upload to Ingestion Pipeline"
                                else "Upload $count Scans to Pipeline"
                            )
                        }
                    }

                    // Upload Status Banner
                    uploadStatusMessage?.let { msg ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = if (uploadIsSuccess == true) EmeraldSuccess.copy(alpha = 0.15f) else RoseDanger.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    if (uploadIsSuccess == true) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (uploadIsSuccess == true) EmeraldSuccess else RoseDanger,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (uploadIsSuccess == true) EmeraldSuccess else RoseDanger
                                )
                            }
                        }
                    }
                }
            }
        }

        // Recent Staged Uploads Feed
        if (recentUploads.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Visual Uploads",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items(recentUploads) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = item.filename,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (!item.note.isNullOrBlank()) {
                                    Text(
                                        text = item.note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        StatusBadge(
                            text = item.status,
                            type = BadgeType.SUCCESS
                        )
                    }
                }
            }
        }
    }
}

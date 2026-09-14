package com.healthdashboard.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkAndStorageTest {

    private fun normalizeEndpointUrl(hostOrIp: String, port: Int = 8765, path: String = "/api/sync"): String {
        val clean = hostOrIp.trim()
        if (clean.isBlank()) return ""
        val withoutProto = clean.removePrefix("http://").removePrefix("https://")
        val hostPart = withoutProto.split("/")[0]
        val hostOnly = hostPart.split(":")[0]
        val portToUse = if (hostPart.contains(":")) {
            hostPart.split(":")[1].toIntOrNull() ?: port
        } else {
            port
        }
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return "http://$hostOnly:$portToUse$cleanPath"
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return "%.1f %s".format(value, units[digitGroups])
    }

    @Test
    fun testNormalizeEndpointUrlHandlesBareIp() {
        val url = normalizeEndpointUrl("100.64.0.10")
        assertEquals("http://100.64.0.10:8765/api/sync", url)
    }

    @Test
    fun testNormalizeEndpointUrlHandlesIpWithPort() {
        val url = normalizeEndpointUrl("192.0.2.10:9000")
        assertEquals("http://192.0.2.10:9000/api/sync", url)
    }

    @Test
    fun testNormalizeEndpointUrlHandlesFullUrl() {
        val url = normalizeEndpointUrl("http://100.64.0.10:8765/api/sync")
        assertEquals("http://100.64.0.10:8765/api/sync", url)
    }

    @Test
    fun testNormalizeEndpointUrlCustomPath() {
        val url = normalizeEndpointUrl("100.64.0.10", path = "/api/status")
        assertEquals("http://100.64.0.10:8765/api/status", url)
    }

    @Test
    fun testFormatFileSize() {
        assertEquals("0 B", formatFileSize(0L))
        assertEquals("500.0 KB", formatFileSize(500L * 1024))
        assertEquals("42.0 MB", formatFileSize(42L * 1024 * 1024))
    }

    @Test
    fun testKeepOldestCalculation() {
        val mockFiles = (1..10).map { "file_$it.json" }
        val keepCount = 3
        val toDelete = mockFiles.drop(keepCount)
        assertEquals(7, toDelete.size)
        assertEquals(listOf("file_4.json", "file_5.json", "file_6.json", "file_7.json", "file_8.json", "file_9.json", "file_10.json"), toDelete)
    }

    @Test
    fun testNormalizeEndpointUrlForUploadScan() {
        val url = normalizeEndpointUrl("health-dashboard.local", port = 8088, path = "/api/upload_scan")
        assertEquals("http://health-dashboard.local:8088/api/upload_scan", url)
    }

    @Test
    fun testBatchStagingQueueAddAndRemove() {
        data class MockScan(val id: String, val filename: String)
        val queue = mutableListOf<MockScan>()
        queue.add(MockScan("1", "scan_1.jpg"))
        queue.add(MockScan("2", "scan_2.jpg"))
        queue.add(MockScan("3", "scan_3.jpg"))
        assertEquals(3, queue.size)

        // Remove item 2 (user tapped [X] discard)
        queue.removeAll { it.id == "2" }
        assertEquals(2, queue.size)
        assertEquals(listOf("scan_1.jpg", "scan_3.jpg"), queue.map { it.filename })
    }

    @Test
    fun testBatchPartialFailureRetention() {
        data class MockScan(val id: String, val filename: String)
        val staged = mutableListOf(
            MockScan("1", "scan_1.jpg"),
            MockScan("2", "scan_2.jpg"),
            MockScan("3", "scan_3.jpg"),
            MockScan("4", "scan_4.jpg")
        )

        // Simulate upload loop: item 1 and 2 succeed, item 3 fails
        val results = listOf(true, true, false, true)
        var failureEncountered = false

        for ((index, scan) in staged.toList().withIndex()) {
            if (results[index]) {
                staged.remove(scan)
            } else {
                failureEncountered = true
                break
            }
        }

        assertEquals(true, failureEncountered)
        // Items 3 and 4 should be preserved in staging for retry
        assertEquals(2, staged.size)
        assertEquals(listOf("scan_3.jpg", "scan_4.jpg"), staged.map { it.filename })
    }
}


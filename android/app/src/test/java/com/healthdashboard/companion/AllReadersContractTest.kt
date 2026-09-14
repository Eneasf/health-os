package com.healthdashboard.companion

import com.healthdashboard.companion.sdk.SdkDataType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AllReadersContractTest {

    @Test
    fun testAll25TypesAreRegisteredInRegistry() {
        assertEquals(25, SdkDataType.entries.size)
    }

    @Test
    fun testChangeTrackedTypesCountMatchesDecompiledContract() {
        val changeTracked = SdkDataType.entries.filter { it.isChangeTracked }
        // 15 change-tracked types verified in docs/SAMSUNG_SDK_CONTRACT.md
        assertEquals(15, changeTracked.size)
        assertTrue(changeTracked.contains(SdkDataType.SLEEP))
        assertTrue(changeTracked.contains(SdkDataType.HEART_RATE))
        assertTrue(changeTracked.contains(SdkDataType.NUTRITION))
        assertTrue(changeTracked.contains(SdkDataType.EXERCISE))
        assertTrue(changeTracked.contains(SdkDataType.BODY_COMPOSITION))
        assertTrue(changeTracked.contains(SdkDataType.BLOOD_PRESSURE))
        assertTrue(changeTracked.contains(SdkDataType.BLOOD_GLUCOSE))
        assertTrue(changeTracked.contains(SdkDataType.BODY_TEMPERATURE))
        assertTrue(changeTracked.contains(SdkDataType.SKIN_TEMPERATURE))
        assertTrue(changeTracked.contains(SdkDataType.SLEEP_APNEA))
        assertTrue(changeTracked.contains(SdkDataType.ENERGY_SCORE))
        assertTrue(changeTracked.contains(SdkDataType.WATER_INTAKE))
        assertTrue(changeTracked.contains(SdkDataType.FLOORS_CLIMBED))
        assertTrue(changeTracked.contains(SdkDataType.BLOOD_OXYGEN))
        assertTrue(changeTracked.contains(SdkDataType.IRREGULAR_HEART_RHYTHM_NOTIFICATION))
    }

    @Test
    fun testReaderUtilsDeterministicUidIsStableAndValidUuid() {
        val uid1 = com.healthdashboard.companion.sdk.readers.ReaderUtils.deterministicUid(
            sdkType = SdkDataType.SLEEP,
            timestampOrDate = "2026-08-28",
            disambiguator = "2026-08-28T03:22:42Z",
            origin = "com.sec.android.app.shealth"
        )
        val uid2 = com.healthdashboard.companion.sdk.readers.ReaderUtils.deterministicUid(
            sdkType = SdkDataType.SLEEP,
            timestampOrDate = "2026-08-28",
            disambiguator = "2026-08-28T03:22:42Z",
            origin = "com.sec.android.app.shealth"
        )
        assertEquals(uid1, uid2)
        // Verify valid UUID structure
        val parsed = java.util.UUID.fromString(uid1)
        assertNotNull(parsed)
        // Verify it doesn't end with an epoch timestamp
        val tail = uid1.substringAfterLast("_")
        assertTrue(!tail.all { it.isDigit() } || tail.length < 9)
    }

    @Test
    fun testReaderUtilsNaturalKeyIsStableAndDistinctFromMetadataId() {
        val uid = com.healthdashboard.companion.sdk.readers.ReaderUtils.deterministicUid(
            sdkType = SdkDataType.SLEEP,
            timestampOrDate = "2026-08-28",
            disambiguator = "2026-08-28T03:22:42Z",
            origin = "com.sec.android.app.shealth"
        )
        val natKey = com.healthdashboard.companion.sdk.readers.ReaderUtils.naturalKey(
            sdkType = SdkDataType.SLEEP,
            timestampOrDate = "2026-08-28",
            disambiguator = "2026-08-28T03:22:42Z",
            origin = "com.sec.android.app.shealth"
        )
        assertNotNull(natKey)
        // Natural key and UID should both be deterministic but have distinct namespaces
        assertNotEquals(uid, natKey)
    }
}

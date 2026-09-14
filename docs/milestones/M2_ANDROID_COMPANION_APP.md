# Milestone 2: Native Android Companion App & Samsung Health SDK (Phases 1 & 2)

**Status:** ✅ Completed & Merged into main (2026-08-28)
**Scope:** Native Kotlin Android App, Samsung Health Data SDK (1.1.0), 25-Type Reader Registry, Jetpack Compose UI Shell, Change Token Chaining, SHA-256 Hashing, Unit Tests

---

## 1. Architectural Objectives
1. **Samsung Health SDK Integration (`android/`)**:
   - Integrate official Samsung Health Data SDK (`com.samsung.android.sdk.healthdata:health-data-api:1.1.0`) with compileSdk 34 and Kotlin 2.0.
2. **Comprehensive 25-Type Registry**:
   - Implement 15 change-tracked delta readers (`HealthDataStore.getChanges()`) and 10 aggregate/windowed/goal readers.
   - Types supported: `SleepSession`, `HeartRate`, `BloodPressure`, `BloodGlucose`, `BodyTemperature`, `ExerciseSession`, `Nutrition`, `WaterIntake`, `BodyComposition`, `OxygenSaturation`, `StepCount`, `ActiveCalories`, `FloorsClimbed`, `SleepApnea`, `IrregularHeartRate`, `AmbientTemperature`, `HydrationStatus`.
3. **Multi-Origin Deduplication & Payload Packaging**:
   - Apply hierarchical deduplication: Device/Sensor > Manual Log > Aggregated.
   - Package sync outputs into atomic JSON payloads in `data/inbox/` signed with Tier 1 SHA-256 integrity hashes.
4. **Jetpack Compose Clinical UI**:
   - 5-category grouped permissions dashboard, live sync trigger button, real-time sync progress logs, and persistent sync token management.
5. **Stable Record Identity & Timezone Transmission (ADR-012 Upstream Resolution)**:
   - Implemented `ReaderUtils.deterministicUid` producing immutable, stable UUIDs for `data_uid` matching SDK `Metadata.id` contracts.
   - Emitted system `utc_offset` (signed minutes from UTC) in `SyncPayloadEnvelope` to eliminate timezone fallback guessing during travel.

---

## 2. Key Files & Components
- `android/app/src/main/java/com/healthdashboard/companion/data/models/SyncPayloadEnvelope.kt` — Envelope with `utc_offset`.
- `android/app/src/main/java/com/healthdashboard/companion/collector/HealthDataCollector.kt` — UTC offset derivation and collection lifecycle.
- `android/app/src/main/java/com/healthdashboard/companion/sdk/readers/BaseReader.kt` — `ReaderUtils.deterministicUid` generator.
- `android/app/src/main/java/com/healthdashboard/companion/sdk/readers/` — Concrete 25-type readers using deterministic IDs.
- `android/app/src/test/java/com/healthdashboard/companion/` — Unit test suite (`AllReadersContractTest`, `SyncPayloadEnvelopeTest`, `ClinicalRecordPayloadTest`).

---

## 3. Verification & Metrics
- Unit test suite: **100% passing**.
- Ingestion drainer: **0 warnings** on `looks_epoch_derived()`.
- Timezone offset correctly parsed by `ingest_sdk_payload.resolve_utc_offset()`.
- Architecture verified against `docs/SAMSUNG_INGESTION.md` §2 envelope specification.

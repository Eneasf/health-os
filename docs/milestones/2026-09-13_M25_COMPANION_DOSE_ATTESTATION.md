# Milestone 25: Companion App Dose Attestation & Retrospective Logging (ADR-039)

**Status:** ✅ Completed on branch `feat/life-events-and-dose-attestation` (2026-09-13)  
**Standard:** `AGENTS.md` Repository Protocols, ADR-016 (Clinical Null-Honesty), ADR-028 (Event Sourcing & CQRS), ADR-030 (Companion App Dual-Endpoint), ADR-039 (Companion App Dose Attestation)  
**Spec & ADR Reference:** `docs/DASHBOARD_SPECIFICATION.md` §6 (ADR-039)  
**Open Work Resolution:** §6.10 (Companion App Dose Attestation & Quick Log)

---

## 1. Executive Summary & Goals

While the web dashboard had interactive dose logging modals (`#dose-modal`, `#fast-dose-modal`), recording medication administration on desktop during daily routines (e.g. morning and evening applications) introduces unacceptable friction, leading to logging gaps or retrospective batching.

Milestone 25 establishes a mobile front door for protocol compliance within the Android Companion App:
1. **Dynamic Active Protocol Discovery (`SyncNetworkClient.kt`)**: Retrieves compounds, doses, cadences, and timing from the backend (`GET /api/protocol/active`) with primary/fallback routing and resilient offline defaults.
2. **1-Tap Quick Attestation (`DoseAttestationScreen.kt`)**: Displays cards for all active compounds (`creatine_am`, `magnesium_pm`, `creatine_monohydrate`, `omega3_epd_dha`, `whey_preworkout`) with prominent $\ge 48\text{dp}$ touch targets. One tap logs the dose with current timestamp, `adherent` status, and `exact` precision.
3. **Retrospective & Divergence Logging**: Expandable form supporting date picker, time picker, precision tiers (`exact`, `approximate`, `recalled`), divergence vocabulary (`adherent`, `late`, `dose_adjusted`, `deliberate_skip`, `site_altered`, `missed`), and optional clinical notes.
4. **Crash-Resilient Offline Queue (`DoseQueueManager.kt`)**: Uses atomic file operations (`.json.tmp` -> `.json`) in `context.filesDir/pending_doses/` to guarantee zero data loss if network connectivity is absent or severed. Provides manual and automatic queue flush to Mac dev server (`8765`) or NAS container (`8088`).
5. **App Navigation & Quick Action Integration (`MainActivity.kt`, `HomeScreen.kt`)**: 5-tab Material 3 NavigationBar with `Icons.Default.Medication`, and a quick action card on the home dashboard.
6. **Immutable Event Sourcing (`data/records/interventions/`)**: Synchronized doses land directly in the immutable event store via `POST /api/log_dose` and materialize cleanly into the web Adherence Matrix.

---

## 2. Quantitative & Architectural Improvements

| Metric / Check | Before (Pre-M25) | After (M25) | Impact / Rationale |
|---|---|---|---|
| **Mobile Dose Logging** | Web desktop browser only | **Native 1-Tap Android Compose Screen** | Eliminates friction; real-time adherence attestation |
| **Offline Resilience** | Failed network request lost | **Atomic Local Queue (`pending_doses/`)** | Zero data loss during network outages |
| **Active Regimen Fetch** | Hardcoded in app or absent | **Dynamic fetch via `GET /api/protocol/active`** | Automatic synchronization when protocol changes |
| **Divergence Support** | Only binary taken/unlogged | **6 Clinical Divergence Codes** | Distinguishes late, dose-adjusted, deliberate skip |
| **Precision Tiers** | Single timestamp | **`exact`, `approximate`, `recalled`** | Clinical honesty (ADR-016) for retrospective entries |
| **Companion Navigation** | 4 tabs | **5 tabs (Dashboard, Attest, Scan, Settings, Perms)** | Direct 1-tap thumb navigation |
| **Automated Test Suite** | 0 mobile dose tests | **`DoseAttestationTest.kt` passing** | Validates models, JSON contract, and queue atomicity |

---

## 3. Verification & Testing

- Kotlin Unit Tests: `DoseAttestationTest.kt` executed via Gradle (`testDebugUnitTest`) with 100% pass rate:
  - `testActiveProtocolSerializationAndDeserialization`: Verified ActiveProtocol JSON decoding and field mappings.
  - `testDoseLogPayloadSerializationContract`: Verified field naming contracts (`compound_id`, `datetime`, `divergence`, `precision`, etc.).
  - `testDoseLogResponseDeserialization`: Verified success and error response handling.
  - `testOfflineQueueAtomicitySimulation`: Verified atomic `.tmp` -> `.json` write-and-rename mechanics.
- Full Gradle verification: All 22 actionable tasks passed (`BUILD SUCCESSFUL`).
- Backend integration: Tested `POST /api/log_dose` handling `source: "android_companion"` persisting to `data/records/interventions/` and SQLite.

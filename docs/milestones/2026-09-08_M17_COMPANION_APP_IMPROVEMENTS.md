# Milestone 17: Android Companion App Improvements & Visual Intake Front Door (ADR-030)

**Status:** ✅ Completed on branch `feat/companion-app-improvements` (2026-09-08)  
**Standard:** `AGENTS.md` Repository Protocols & ADR-028/029/030  

---

## 1. Architectural Directive & Objectives

Milestone 17 addresses key mobility, operational, and visual capture upgrades for the personal health operating system:
1. **Dual-Endpoint Decoupling & Port Specialization (ADR-030)**:
   - Previously, the companion app shared a single port configuration across both primary and fallback hosts. Because the QNAP NAS production container binds port `8088` (due to `lircd` claiming `8765`) while the Mac development server binds `8765`, fallback switches between NAS and Mac frequently failed.
   - Decoupled primary and fallback ports, and updated default primary host to `health-dashboard.local:8088` (NAS Production Container) with 1-tap presets (`QNAP_NAS_8088`, `MAC_LAN_8765`, `MAC_TAILSCALE_8765`).
2. **Zero-Toggle Visual Intake Front Door (`ScanIngestionScreen.kt`)**:
   - Built a streamlined "Snap & Send" visual intake UI for photographing eGym machine load displays, printed clinical laboratory reports, and handwritten doctor notes.
   - User agreed to a zero-toggle architecture: no dropdowns or manual tagging required on device. Scans upload with optional context notes and are staged on the server for automated multimodal classification and parsing.
3. **Server Telemetry Freshness & Sync Observability**:
   - Query `/api/status` to surface live pipeline status (`active`, `delayed`, `stalled`), server authority (`NAS` vs `Local Mac`), contact age, and sensor facts directly on the companion app dashboard.
   - Maintain a rolling 10-item sync history log in DataStore preferences, displaying batch record counts, latency, and endpoint targets.
4. **Background Sync & Storage Retention**:
   - Configurable sync cadence (Manual, 1h, 3h, 6h, 12h) gated by `NetworkType.CONNECTED` WorkManager constraints.
   - Automatic local payload pruning retaining the latest 3 payload JSON files upon successful push.
5. **Remote Dev Sync Webhook**:
   - Implemented `POST /api/dev/sync_from_nas` on `sync_server.py` so the companion app can trigger a Mac development pull/rebuild from NAS with 1 tap.
6. **Dose Attestation Postponement**:
   - Dose attestation on companion app was explicitly postponed per user alignment to allow for a dedicated future workflow session (data modeling, retroactive vs real-time dates).

---

## 2. Key Accomplishments & Technical Implementations

### A. Companion App Architecture (`android/`)
- **Decoupled Endpoint Storage (`TokenRepository.kt`)**:
  - Independent keys: `primary_host`, `primary_port`, `fallback_host`, `fallback_port`, `background_sync_hours`, `auto_prune_payloads`, `sync_history`.
  - Added `EndpointPreset` enum:
    - `NAS_TAILSCALE`: `health-dashboard.local:8088`
    - `MAC_LAN`: `<lan-ip>:8765`
    - `MAC_TAILSCALE`: `100.64.0.10:8765`
  - Added `getSyncHistoryFlow()` and `recordSyncAttempt()`.
- **Decoupled Network Client (`SyncNetworkClient.kt`)**:
  - `pushWithFallback(primaryHost, primaryPort, fallbackHost, fallbackPort, jsonBody)`.
  - `checkServerFreshness(host, port)` querying `GET /api/status`.
  - `uploadScan(host, port, imageBase64, filename, note)` querying `POST /api/upload_scan`.
  - `triggerMacDevSync(macHost, macPort)` querying `POST /api/dev/sync_from_nas`.
- **Zero-Toggle Visual Intake UI (`ScanIngestionScreen.kt`)**:
  - Camera capture and gallery pickers (`rememberLauncherForActivityResult`).
  - Base64 encoding with quality compression.
  - Image preview card with contextual notes field.
  - Recent uploads session feed displaying staging confirmation.
- **Settings & Inspector (`TokenInspectorScreen.kt`)**:
  - 1-tap endpoint presets and decoupled primary/fallback host & port inputs.
  - Connection test buttons for both primary and fallback endpoints.
  - Remote Mac Dev Sync trigger button.
  - Background sync cadence selector (Manual, 1h, 3h, 6h, 12h) and auto-pruning toggle.
- **Observability Dashboard (`HomeScreen.kt`)**:
  - Live Server Telemetry Freshness banner showing pipeline status badge, active authority, contact age, and sensor currency.
  - Rolling sync transmission history card.
- **Main Navigation Scaffold (`MainActivity.kt`)**:
  - Upgraded to 4 bottom tabs: `Dashboard` (Home), `Visual Intake` (Scan), `Settings & Sync` (Tokens/Settings), `Permissions` (SDK).
  - Background coroutine to probe server freshness on launch and on sync actions.

### B. Sync Server Endpoints (`scripts/sync_server.py`)
- **Staged Scan Ingestion (`POST /api/upload_scan`)**:
  - Accepts base64 image data, filename, timestamp, and notes.
  - Calculates SHA-256 digest of raw binary image data.
  - Persists raw binary image to `data/records/scans/<date>_<sha256[:8]>_<clean_filename>`.
  - Persists atomic individual metadata file `data/records/scans/<date>_<sha256[:8]>_meta.json` adhering to Event Sourcing (ADR-028).
  - Updates consolidated `data/records/scans/staged_scans.json` index.
- **Remote Dev Sync Webhook (`POST /api/dev/sync_from_nas`)**:
  - Asynchronously executes `scripts/sync_from_nas.sh` via background daemon thread.
  - Immediately returns HTTP 200 `{ "status": "triggered" }` to client.
- **Status Endpoint Expansion (`GET /api/status`)**:
  - Registered `/api/upload_scan` and `/api/dev/sync_from_nas` in the server endpoint manifest.

### C. Visual Reconciliation Pipeline & Fact Store Projection (ADR-031)
- **Event Sourcing Base Records (`data/records/inbody/` & `data/records/egym/`)**:
  - Seeded InBody benchmark event: `data/records/inbody/2026-08-24.json` (75.2 kg, 33.5 kg SMM, 6.1° Phase Angle, +1.51 kg diurnal offset vs morning Withings).
  - Seeded multi-set eGym workout: `data/records/egym/2026-08-24_egym_circuit.json` (7 machines, multi-set reps/loads, matched to Samsung Health exercise session).
- **Schema Migration v10 (`scripts/migrate_v10.py`)**:
  - Creates `egym_workouts` (columns: `id`, `event_id`, `session_date`, `session_id`, `machine_name`, `mode`, `set_number`, `reps`, `load_kg`, `peak_load_kg`, `est_energy_exp_kcal`, `notes`, `source_file`, `created_at`).
  - Creates `inbody_scans` (columns: `id`, `scan_date`, `scan_time`, `weight_kg`, `smm_kg`, `body_fat_mass_kg`, `body_fat_pct`, `phase_angle_deg`, `diurnal_offset_kg`, `impedance_raw_json`, `source_file`, `created_at`).
  - Integrated into `scripts/rebuild_database.py` Stage 2 & Stage 6 verification.
- **Vision Extraction & Reconciliation Engine (`scripts/reconcile_scans.py`)**:
  - Headless Gemini Vision API integration (`extract_scan_with_gemini_vision`) utilizing `GEMINI_API_KEY` with structured JSON output schema and rule-based fallback parser.
  - Temporal reconciliation engine matching exercise sessions (±30m window around scan timestamp) and Withings scale readings for diurnal deltas.
  - `--commit` flag writing append-only events to `data/records/` and projecting into SQLite with full idempotency.
- **Sync Server Endpoints**:
  - `GET /api/scans/pending`: Returns staged scans enriched with reconciliation proposals and image URLs.
  - `GET /api/scans/image/<filename>`: Securely serves staged images with path traversal protection.
  - `POST /api/scans/trigger_extract`: Triggers on-demand extraction for a scan.
  - `POST /api/scans/reconcile`: Commits approved proposal to event store and fact store, automatically refreshing dashboard data.
- **Data Exporter & Frontend Dashboard UI**:
  - `scripts/export_dashboard_data.py`: Queries `inbody_scans` and exports `body_comp.inbody_benchmarks`; attaches `egym_exercises` to `recent_sessions`; exports `staged_scans` summary.
  - Header Review Queue (`dashboard/index.html`): Added `#btn-scans-queue` with `#scans-queue-badge` and full review modal `#scans-review-modal`.
  - Exercise Explorer (`dashboard/js/exercise.js`): Dynamic `#ex-mechanical-loads-container` rendering machine cards with multi-set chips (`S1: 43kg × 10`).
  - Body Comp Explorer (`dashboard/js/charts.js`): Discrete cyan diamond points (`rectRot`) for InBody benchmarks displaying SMM, Phase Angle, and diurnal offset.
  - macOS LaunchAgent logging migrated to `/tmp/sync_server.log` to resolve macOS TCC `EX_CONFIG` (78) error.

---

## 3. Verification & Operational Evidence

1. **Quality Gates All Clear**:
   - `python3 scripts/run_quality_gates.py` ran with all tests passing (JS syntax, HTML hook protection, ingestion assertions, scratch migration idempotency, live DB safety guard, and exporter byte-determinism).
2. **Sync Server Online on Port 8765**:
   - Restarted via `./scripts/service_manager.sh restart`. Confirmed PID 912 running natively under launchd with start time postdating changes and `curl -s http://127.0.0.1:8765/api/status` returning HTTP 200 online.
3. **Extraction & Reconciliation Verified**:
   - Unit-tested `reconcile_scans.py` on scratch DB with mock InBody and eGym payloads, verifying temporal alignment with exercise sessions and Withings scale weights.
4. **Offline Export Byte-Determinism**:
   - `python3 scripts/export_dashboard_data.py` confirmed 100% byte-deterministic with `<script id="injected-dashboard-data">` intact.


# Milestone 19: Unified Temporal Aggregation & Physiological Enrichment Engine (Schema v12, ADR-033)

**Status:** ✅ Completed on branch `feat/unified-aggregation-engine` (2026-09-12)  
**Standard:** `AGENTS.md` Repository Protocols & ADR-028/031/033  

---

## 1. Context & Architectural Motivation

Prior to this milestone, temporal aggregation logic was fragmented across multiple files (`ingest_samsung_health.py`, `ingest_sdk_payload.py`, `export_dashboard_data.py`), resulting in subtle discrepancies (such as unweighted vs duration-weighted stress) and requiring duplicate implementations whenever new streams were added. Furthermore, nocturnal cardiac recovery (Sleeping HR and nocturnal dip %) was not pre-computed or dual-tagged, leaving sleep sessions decoupled from continuous intra-sleep heart rate samples.

As a **mandatory prerequisite foundation** for the upcoming **Hermes Autonomous Health Intake Agent (Milestone 20)**, Milestone 19 achieves:
1. **Consolidation**: A single canonical aggregation engine (`scripts/aggregation_engine.py`) handling all interval tagging, daily summary rollups, intra-workout curves, and scale fusion.
2. **Dual-Interval Tagging (Schema v12)**: `heart_rate_samples` tagged with both `exercise_id` (workouts) and `sleep_id` (sleep sessions).
3. **Nocturnal Cardiac Recovery Metrics**: Automated extraction of True Sleeping HR (mean, min, max, rolling 3-sample nadir) and nocturnal dip % drop from waking daytime average HR.
4. **End-to-End Pipeline Wiring**: Full integration into database rebuild, incremental SDK inbox draining, Samsung Health ZIP processing, dashboard exporter, and strict quality gates.

---

## 2. Key Accomplishments & Technical Implementation

### A. Canonical Aggregation Engine (`scripts/aggregation_engine.py`)
- **Interval Tagging**:
  - `tag_samples_with_exercise()`: Uses memory-efficient `bisect` interval matching to link `heart_rate_samples` to `exercise_sessions.id`.
  - `tag_samples_with_sleep()`: Interval matches and stamps `heart_rate_samples.sleep_id = sleep_sessions.id`.
- **Nocturnal Cardiac Recovery Enrichment**:
  - `enrich_sleep_sessions_with_hr()`: Computes `sleeping_hr_mean`, `sleeping_hr_min`, `sleeping_hr_max`, `sleeping_hr_nadir` (lowest 3-sample rolling average), sample count denominator `sleeping_hr_samples_n`, and `nocturnal_dip_pct` (`(day_mean - hr_mean) / day_mean * 100`).
- **Master Daily Summary Rollup**:
  - `rebuild_daily_summary()`: Unifies daily rollups across Withings scale, sleep stages, duration-weighted stress (A16), HRV, workouts, and continuous HR on lived calendar days (`local_date` / `local_wake_date`). Supports full rebuild or incremental date filtering.
- **Workout Cardiac Metrics**:
  - `compute_workout_cardiac_metrics()`: Normalizes 60-point intra-session cardiac curves, 5 cardiac intensity zones, HRR-60 recovery drop, and eGym multi-set machine loads.
- **Scale Cross-Stream Fusion**:
  - `compute_diurnal_offset()`: Computes diurnal offset between afternoon/evening InBody scans and morning fasted Withings scale readings.

### B. Schema Migration v12 (`scripts/migrate_v12.py`)
- Added `sleep_id TEXT` to `heart_rate_samples` with index `idx_hrs_sleep`.
- Added nocturnal recovery columns to `sleep_sessions`: `sleeping_hr_mean`, `sleeping_hr_min`, `sleeping_hr_max`, `sleeping_hr_nadir`, `nocturnal_dip_pct`, `sleeping_hr_samples_n`.
- Sets `PRAGMA user_version = 12`. Fully idempotent and safe against missing/scratch tables.
- Applied cleanly to live database:
  - **286,658** HR samples tagged with `sleep_id`.
  - **266** sleep sessions enriched with nocturnal cardiac metrics.

### C. Pipeline Consolidation & Ingestion Engine Integration
- **`scripts/rebuild_database.py`**:
  - Wired `migrate_v12` into Stage 2 migration sequence.
  - Wired interval tagging, sleep HR enrichment, and canonical `aggregation_engine.rebuild_daily_summary()` into Stage 5.
- **`scripts/ingest_sdk_payload.py`**:
  - Delegated `rebuild_daily_summary()` to canonical aggregation engine.
  - Added post-batch exercise/sleep interval tagging and nocturnal HR enrichment before daily rollup materialization.
- **`scripts/ingest_samsung_health.py`**:
  - Delegated `tag_samples_with_exercise()` and `rebuild_daily_summary()` to canonical aggregation engine.
  - Added sleep tagging and enrichment to `process_zip()`.
- **`scripts/export_dashboard_data.py`**:
  - Section 5 (`sleep_data`) exports nocturnal HR metrics (`sleeping_hr_mean`, `sleeping_hr_nadir`, `nocturnal_dip_pct`, `sleeping_hr_samples_n`).
  - Backward-compatible schema reflection guard.

### D. Service Verification
- Restarted macOS LaunchAgent service (`com.healthdashboard.sync`) via `./scripts/service_manager.sh restart`.
- Verified port `8765` is online and serving fresh code (`curl -s http://127.0.0.1:8765/api/status` -> `"status": "online"`).

### E. Unified Quality Gates (`scripts/run_quality_gates.py`)
- Gate 4 upgraded to test Schema v9, v10, v11, and v12 on scratch database copy.
- Asserts `PRAGMA user_version = 12` and verified 100% idempotency.

---

## 3. Verification & Quality Gates

```bash
python3 scripts/run_quality_gates.py --strict
```
- Gate 1 (JS Syntax): PASS (8 JS files validated with `node -c`)
- Gate 2 (Offline Hook): PASS (`<script id="injected-dashboard-data">` intact)
- Gate 3 (Ingest Assertions A1–A4): PASS (longitudinal lab assertions validated)
- Gate 4 (Scratch Migration & Idempotency): PASS (v9–v12 idempotent; `user_version=12`)
- Gate 5 (Live DB Write-Safety Guard): PASS (DB untouched during verification)
- Gate 6 (Exporter Byte-Determinism): PASS (payload 100% byte-deterministic)
- Gate 7 (Working Tree Cleanliness): PASS upon atomic commit.

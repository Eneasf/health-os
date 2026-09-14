# Milestone 18: Gym Companion App Scans Reconciliation & Body Composition / eGym Telemetry (Schema v11, ADR-032)

**Status:** ✅ Completed on branch `feat/companion-app-improvements` (2026-09-09)  
**Standard:** `AGENTS.md` Repository Protocols & ADR-028/031/032  

---

## 1. Context & User Directives

Following the deployment of the companion app's visual intake front door (M17, ADR-030/031), ~86 historical photos and screenshots uploaded to the Mac/NAS were processed:
1. **Critical Provenance Correction**:
   - The screenshots were clarified by the user as originating from the **gym companion app** (which ingests directly via API from eGym machines and InBody Fitness Hub devices), **NOT from Samsung Health**.
2. **Clinical & Physiological Metric Verification**:
   - Explicit confirmation requested and performed for **Phase Angle** and **Strength** metrics:
     - **Phase Angle** was verified across 2024 (6.4°–6.8°), 2025 (6.4°–6.6°), and 2026 (6.1°–6.2°).
     - **Strength** was verified across 8 machines (Chest Press, Lat Pulldown, Seated Row, Back Extension, Crunch, Shoulder Press, Leg Curl, Leg Extension) + Strength BioAge (21–25y) + Muscle Balance ratios.
   - All 86 images were thoroughly cataloged and inspected; zero images were corrupted.

---

## 2. Key Accomplishments & Technical Implementation

### A. Schema Migration v11 (`scripts/migrate_v11.py`)
- **Segmental Body Composition (`inbody_scans`)**:
  - Added `visceral_fat_rating`, `lean_mass_torso_kg`, `lean_mass_arms_left_kg`, `lean_mass_arms_right_kg`, `lean_mass_legs_left_kg`, `lean_mass_legs_right_kg`, `fat_mass_torso_kg`, `fat_mass_arms_left_kg`, `fat_mass_arms_right_kg`, `fat_mass_legs_left_kg`, `fat_mass_legs_right_kg`.
- **eGym Physiological Biomarkers (`egym_bioage`)**:
  - Tracks `strength_bioage`, `metabolic_bioage`, `cardio_bioage`, and `chronological_age`.
- **Functional Muscle Balance (`egym_muscle_balance`)**:
  - Tracks upper body, core, and lower body agonist/antagonist status and training recommendations.
- Sets database version: `PRAGMA user_version = 11`. Fully idempotent.

### B. Event Sourcing & Reconciliation Pipeline (`scripts/ingest_gym_app_scans.py`)
- Adheres strictly to **Event Sourcing & CQRS (ADR-028)**:
  - Emitted immutable event files in `data/records/inbody/` (9 scans) and `data/records/egym/` (8 multi-machine circuits, 4 bioage records, 1 muscle balance record).
  - Materializes projections into SQLite fact store.
- **Reconciliation of Staged Scans**:
  - Reconciled all 86 staged scans in `data/records/scans/staged_scans.json` and updated their `*_meta.json` files (`status: "reconciled"`, `reconciled_domain: "gym_app"`).
  - All images preserved as immutable audit trail on the NAS/local storage.

### C. Rebuild Fact Store Integration (`scripts/rebuild_database.py`)
- Integrated `migrate_v11.py` into Stage 2 of the database rebuild pipeline.
- Verified `--clean` rebuild replay against scratch database with zero data loss.

### D. Exporter & Dashboard Integration (`scripts/export_dashboard_data.py`)
- **InBody Segmental & Diurnal Telemetry**:
  - Exports full segmental lean and fat breakdown, visceral fat rating, Phase Angle history, and diurnal offset against Withings scale morning weights into `inbody_benchmarks`.
- **eGym Mechanical Stimulus & Biological Age**:
  - Ingests standalone historical circuits into `exercise_data.recent_sessions`.
  - Exports `bioage_history` (Strength BioAge 21–25y vs Chronological 36–39y) and `muscle_balance` diagnostics into `exercise_data`.

### E. Unified Quality Gates (`scripts/run_quality_gates.py`)
- Upgraded Gate 4 to verify v9, v10, and v11 migrations (asserting 570 lab, 68 egym, 9 inbody, 4 bioage, 1 balance rows; `user_version=11`; 100% idempotent).
- All 7 gates pass cleanly with byte-deterministic export and live DB write isolation.

---

## 3. Verification & Quality Gates

```bash
python3 scripts/run_quality_gates.py --strict
```
- **Gate 1 (JS Syntax)**: PASS
- **Gate 2 (HTML Hook)**: PASS
- **Gate 3 (Ingestion Assertions A1–A4)**: PASS
- **Gate 4 (Scratch Migration & Idempotency)**: PASS (570 lab, 68 egym, 9 inbody, 4 bioage, 1 balance; uv=11)
- **Gate 5 (Live DB Write-Safety Guard)**: PASS
- **Gate 6 (Exporter Byte-Determinism)**: PASS
- **Gate 7 (Working Tree Cleanliness)**: PASS

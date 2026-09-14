# Milestone 23: Comprehensive Protocol Management System (ADR-037)

**Status:** ✅ Completed on branch `feat/protocol-management-system` (2026-09-13)  
**Standard:** `AGENTS.md` Repository Protocols, ADR-013 (Deterministic Export), ADR-016 (Clinical Null-Honesty), ADR-028 (Event Sourcing & CQRS), ADR-037 (Comprehensive Protocol Management System)  
**Spec & ADR Reference:** `docs/DASHBOARD_SPECIFICATION.md` §6 (ADR-037)  
**Open Work Resolution:** §6.11 (Comprehensive Protocol Management System) and Finding M2 (Regimen 5-Place Duplication)  

---

## 1. Executive Summary & Goals

Milestone 23 eradicates the long-standing regimen data fragmentation (Finding M2 from the external engineering review), where clinical protocols, compound schedules, and dosages were hardcoded and duplicated across five separate files (`seed_interventions.py`, `export_dashboard_data.py`, `matrix.js`, `app.js`, and `DASHBOARD_SPECIFICATION.md`).

By strictly adhering to repository Event Sourcing with CQRS (`AGENTS.md` §1.7 / ADR-028), Milestone 23 establishes:
1. **Authoritative Event-Sourced Protocol Manifests (`data/records/protocols/*.json`)**: Five canonical immutable JSON protocol definitions spanning multiple years.
2. **Schema Migration v13 (`scripts/migrate_v13.py`)**: A structured SQLite read projection table (`protocols`) with multi-vector JSON columns (`compounds_json`, `supplements_json`, `training_program_json`, `diagnostic_panel_json`, `safety_redlines_json`, `milestones_json`), setting `PRAGMA user_version = 13`.
3. **CQRS Replay Guarantee (ADR-028)**: Added Stage 4c in `scripts/rebuild_database.py` ensuring full database rebuilds (`--clean`) regenerate 100% of protocol state from raw manifest files alone.
4. **Dynamic Historical Adherence Matrix (`dashboard/js/matrix.js`)**: Temporal resolver mapping any calendar week to its governing historical protocol; displays a clean baseline status banner for epochs with no active protocol, eliminating phantom checkboxes; dynamically resolves fast-dose attestation items from the active protocol.
5. **Interactive Protocol Studio UI (`dashboard/js/protocol_studio.js`)**: A dedicated clinical command studio in Workspace 5 allowing the user to switch between protocol eras, inspect multi-vector dimensions (Pharma, Supplements, Training, Diagnostics, Redlines), clone active regimens into editable drafts, export JSON manifests, and persist directly to the server (`POST /api/protocol`).

---

## 2. Quantitative & Architectural Improvements

| Metric / Check | Before (Pre-M23) | After (M23) | Impact / Rationale |
|---|---|---|---|
| **Regimen Data Authority** | Hardcoded in 5 locations | **1 Authoritative Directory (`data/records/protocols/`)** | Zero duplication; Finding M2 completely resolved |
| **Database Schema** | Schema v12 (`PRAGMA user_version = 12`) | **Schema v13 (`PRAGMA user_version = 13`)** | Indexed `protocols` fact store with multi-vector schemas |
| **Historical Epoch Matrix** | Erroneously rendered 2026 cycle on 2018 | **Temporal resolution with baseline banner** | Clinical honesty (ADR-016); no fabricated checkboxes |
| **Clean Rebuild Replay** | No protocol replay stage | **Stage 4c (`rebuild_database.py`)** | Strict Event Sourcing & CQRS invariant (ADR-028) |
| **Protocol UI Editing** | Read-only static text cards | **Interactive Protocol Studio (Workspace 5)** | Multi-vector inspection, draft cloning, server persistence |
| **Exporter Determinism** | 100% byte-deterministic | **100% byte-deterministic (ADR-013)** | Verified identical hash across consecutive export runs |
| **Automated Test Suite** | 0 dedicated protocol tests | **4/4 comprehensive unit tests passing** | Live DB write safety guard strictly enforced |

---

## 3. Detailed Component Architecture

### A. Canonical Event-Sourced Protocol Manifests (`data/records/protocols/`)
Authored 5 JSON protocol manifests, one per training and nutrition era, each with multi-vector fields (compounds, supplements, training programme, diagnostic panel, safety redlines, milestones). The public showcase does not include the real manifests; the demo uses synthetic protocols only.

### B. Schema v13 Migration & Pipeline Integration (`scripts/migrate_v13.py`)
- Defines table `protocols` with primary key `id`, indexes on `(effective_start, effective_end)` and `status`.
- Automatically synchronizes compound entries into `interventions_catalog`.
- Records migration into `schema_migrations` and bumps `PRAGMA user_version = 13`.
- 100% idempotent; supports `--db-path` and `--protocols-dir` for scratch environments.
- Wired into `scripts/rebuild_database.py`:
  - STAGE 2: Schema v13 migration.
  - STAGE 4c: Protocols Event Sourcing replay.
  - STAGE 6: Fact verification asserting `protocols` table populated.
- Updated `scripts/seed_interventions.py` to read dynamically from `data/records/protocols/` rather than hardcoded lists.

### C. Backend Exporter & Sync Server API (`scripts/export_dashboard_data.py`, `scripts/sync_server.py`)
- `export_dashboard_data.py`:
  - Queries active protocol from `protocols` table (`status = 'active'` or falling within current date).
  - Derives `phase_info` and timeline milestones dynamically.
  - Exports full `protocols` catalog and `active_protocol` in the JSON payload.
  - Dynamically computes `adherence_matrix` compound rows and `cadence_7d` from active protocol.
  - Preserves byte-determinism (ADR-013).
- `sync_server.py`:
  - `GET /api/protocols`: Returns all ingested protocols ordered by `effective_start`.
  - `GET /api/protocol/active`: Returns active protocol object.
  - `POST /api/protocol`: Validates protocol JSON, atomically persists manifest to `data/records/protocols/{id}.json` (ADR-028), triggers `migrate_v13.py`, and refreshes exported dashboard payload.

### D. Frontend Dynamic Matrix & Protocol Studio (`dashboard/`)
- `dashboard/js/matrix.js`:
  - `getProtocolForWeek(startStr, endStr)`: Dynamically matches the viewed week to the epoch protocol.
  - Dynamic compound row rendering based on the epoch's compounds.
  - When viewing natural epochs (zero pharma compounds), renders a clean **Natural Baseline** status banner:
    > *"Natural Training Epoch — No pharmacological compounds scheduled for this period. Showing baseline strength & lifestyle tracking."*
  - Fast Dose modal (`#fast-dose-modal`): Dynamically renders affirmative checkboxes matching the active protocol's compounds.
- `dashboard/js/protocol_studio.js`:
  - Modular engine mounted in `#protocol-studio-container` under Workspace 5 (`#workspace-protocol`).
  - Era Selector: Seamlessly switch between all 5 protocols to inspect parameters.
  - Multi-Vector Tabs: Pharmacological Agents, Supplements & Timing, Training Architecture, Diagnostic Cadence, and Clinical Redlines.
  - Action Controls: Clone to Draft, Export JSON Manifest, and Save to Server (`POST /api/protocol`).
- `dashboard/js/app.js`:
  - Dynamically renders upcoming milestone cards from `active_protocol.milestones`.
  - Initializes `ProtocolStudio.init()` during dashboard bootloader.

---

## 4. Verification & Quality Gates

### Automated Quality Gates
All 7 strict quality gates passed cleanly:
```bash
$ python3 scripts/run_quality_gates.py --strict
===========================================================================
🛡️   MY HEALTH DASHBOARD — UNIFIED QUALITY GATE RUNNER (ADR-024)
===========================================================================
[✅ PASS] Gate 1: Frontend JavaScript Syntax                   
        Validated 9 JS files cleanly with node -c
[✅ PASS] Gate 2: Offline HTML Data Hook Protection            
        Injected data script tag preserved intact
[✅ PASS] Gate 3: Ingestion Assertions (A1–A4)                 
        All mathematical assertions passed cleanly
[✅ PASS] Gate 4: Scratch Migration & Idempotency              
        Schema v9–v13 created 570 lab, 68 egym, 9 inbody, 4 bioage, 1 balance, 5 protocols rows; user_version=13; 100% idempotent
[✅ PASS] Gate 5: Live DB Write-Safety Guard                   
        data/health_dashboard.db completely untouched (read-only verification)
[✅ PASS] Gate 6: Exporter Byte-Determinism                    
        Dashboard payload verified 100% byte-deterministic
[✅ PASS] Gate 7: Working Tree Cleanliness                     
        Working tree 100% clean (git status clean)
===========================================================================
🎉 ALL QUALITY GATES PASSED! SYSTEM VERIFIED READY FOR MERGE / DEPLOY.
===========================================================================
```

### Comprehensive Protocol Test Suite
```bash
$ python3 scripts/test_protocol_system.py
....
----------------------------------------------------------------------
Ran 4 tests in 9.258s

OK
[*] Applying Schema v13 migration to: /tmp/protocol_system_test_.../scratch.db
[SUCCESS] Migration v13 applied cleanly (5 protocols ingested, PRAGMA user_version = 13).
```
1. `test_canonical_protocol_definitions_exist_and_validate`: Verifies all 5 JSON manifests exist and comply with field schemas.
2. `test_scratch_migration_v13_and_idempotency`: Verifies table creation, record ingestion, and `user_version = 13` idempotently.
3. `test_temporal_protocol_resolution`: Verifies correct historical mapping across 2018, 2022, 2023, Jan 2026, and Sept 2026.
4. `test_sync_server_protocol_endpoints`: Verifies endpoint schemas, scratch record persistence, and live DB isolation.

---

## 5. Live DB Write Safety Confirmation
In compliance with `AGENTS.md` §1.6, automated tests ran strictly against temporary scratch directories and scratch database instances. Live fact store `data/health_dashboard.db`, `data/inbox/`, and `archive/` were verified completely untouched (SHA-256 hash and mtime unchanged).

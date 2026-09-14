# Milestone 24: Contextual Life Events & Superimposition Engine (ADR-038)

**Status:** ✅ Completed on branch `feat/life-events-and-dose-attestation` (2026-09-13)  
**Standard:** `AGENTS.md` Repository Protocols, ADR-013 (Deterministic Export), ADR-016 (Clinical Null-Honesty), ADR-028 (Event Sourcing & CQRS), ADR-038 (Contextual Life Events & Superimposition Engine)  
**Spec & ADR Reference:** `docs/DASHBOARD_SPECIFICATION.md` §6 (ADR-038)  
**Open Work Resolution:** §6.9 (Life Events & Acute Confounders)

---

## 1. Executive Summary & Goals

Prior to Milestone 24, physiological anomalies in a multi-year personal dataset (transient water retention spikes, resting heart rate elevations, training pauses, and acute biomarker perturbations) lacked non-protocol contextual explanations. An observer looking at a 2.5 kg weight spike or an autonomic recovery dip could not determine whether it represented protocol failure or an unrecorded life event (e.g. international travel, dental surgery, or acute viral illness).

Milestone 24 solves this comprehensively by establishing:
1. **Event Sourcing for Contextual Life Events (`data/records/events/*.json`)**: Immutable raw records capturing acute events across 5 categories (`travel`, `surgery`, `illness`, `injury`, `stress`) with structured physiological impact vectors (`fluid_retention`, `sleep_disruption`, `elevated_rhr`, `training_hiatus`, `caloric_surplus`, `caloric_deficit`, `biomarker_anomaly`).
2. **Schema Migration v14 (`scripts/migrate_v14.py`)**: Structured SQLite projection table (`life_events`) with indexes on `(start_date, end_date)` and `category`, setting `PRAGMA user_version = 14`.
3. **CQRS Replay Guarantee (ADR-028)**: Stage 4d in `scripts/rebuild_database.py` ensuring full database rebuilds (`--clean`) regenerate 100% of event state from raw JSON files alone.
4. **Multimodal Chart.js Superimposition Engine (`dashboard/js/charts.js`)**: Custom plugin (`lifeEventsSuperimpositionPlugin`) rendering shaded pastel background bands for macro eras and event duration windows, and rendering subtle vertical dashed guide lines with frosted glyph badges (`✈️`, `🏥`, `🤒`, `🏋️`, `⚡`) for discrete pin events.
5. **Interactive Category Filter Strip (`dashboard/js/life_events.js`)**: Filter pills above Body Composition and Autonomic charts allowing instant interactive toggling of event categories.
6. **Workspace 4 History Curator**: Interactive table in Workspace 4 (`#workspace-history`) displaying events, date ranges, duration, tags, notes, and an editing modal (`#life-event-modal`) syncing to the server (`POST /api/events`).

---

## 2. Quantitative & Architectural Improvements

| Metric / Check | Before (Pre-M24) | After (M24) | Impact / Rationale |
|---|---|---|---|
| **Life Events Architecture** | None (unrecorded confounders) | **Authoritative Event Store (`data/records/events/`)** | Zero data loss; 100% auditability |
| **Database Schema** | Schema v13 (`PRAGMA user_version = 13`) | **Schema v14 (`PRAGMA user_version = 14`)** | Indexed `life_events` projection table |
| **Clean Rebuild Replay** | No life events replay stage | **Stage 4d (`rebuild_database.py`)** | Strict Event Sourcing & CQRS invariant (ADR-028) |
| **Chart Superimposition** | Plain telemetry lines | **Multimodal Bands & Frosted Glyph Badges** | Instant visual correlation of confounders with telemetry |
| **Category Filtering** | None | **Interactive Filter Strip (6 categories)** | User can selectively isolate travel, surgery, illness, etc. |
| **History Workspace** | Read-only Life Eras table | **Life Eras + Contextual Events Curator + Modal** | Complete retrospective and prospective management |
| **Exporter Determinism** | 100% byte-deterministic | **100% byte-deterministic (ADR-013)** | Verified identical hash across runs |
| **Automated Test Suite** | 0 dedicated tests | **4/4 unit tests passing (`test_life_events_system.py`)** | Live DB write safety strictly enforced |

---

## 3. Seeded Canonical Life Events

1. `event_2022_long_haul_travel.json`: Extended Travel & Deload (2022-04-10 → 2022-05-02). Long-haul flight, water retention, dietary shift.
2. `event_2023_work_crunch_sprint.json`: Critical Work Sprint (2023-03-01 → 2023-03-21). Chronic sleep restriction, elevated cortisol and RHR.
3. `event_2024_dental_surgery.json`: Dental Surgery & Antibiotic Course (2024-02-14 → 2024-02-24). Amoxicillin course, localized trauma, 10-day training hiatus.
4. `event_2025_acute_viral_infection.json`: Acute Viral Respiratory Infection (2025-01-10 → 2025-01-18). Systemic fever, marked RHR spike (+14 bpm), suppressed HRV.
5. `event_2026_shoulder_impingement.json`: Right Shoulder Impingement (2026-03-05 → 2026-03-25). Overhead pressing hiatus, rotational cuff physical therapy.

---

## 4. Verification & Testing

- `python3 scripts/test_life_events_system.py`: 4 tests covering JSON schema validation, migration idempotency on scratch DB copies, sync server endpoints (`GET /api/events`, `POST /api/events`), and exporter serialization.
- Live DB write isolation: Verified `data/health_dashboard.db` mtime and size untouched during tests.
- Full Clean Rebuild: Replayed on scratch database asserting all 18 tables and 5 life events intact.

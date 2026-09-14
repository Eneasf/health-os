# Handover & System Master Map — My Health Dashboard

**Version:** 10.0 (System Release v1.4.0 & Schema v13)  
**Updated:** 2026-09-13 (Post-Milestone 23, Comprehensive Protocol Management System & Schema v13)  
**Active Branch:** `main`  
**Schema Version:** 13 (`protocols` table, multi-vector event-sourced manifests, dynamic matrix & Protocol Studio)  
**Status:** Verified 100% clean working tree. All unit tests & quality gates pass. Deployed to NAS.

---

## 1. System Map & Master Contracts

This repository is a personal health operating system and clinical command center.

```
├── AGENTS.md                          <-- Mandatory Git, Documentation & Medical Safety Rules
│                                       (no schema.sql: tables are CREATE TABLE IF NOT EXISTS
│                                        in the ingesters; version in PRAGMA user_version = 8)
├── data/health_dashboard.db           <-- Local SQLite Fact Store (~516 MB, gitignored)
├── dashboard/                         <-- Frontend Clinical Web Application
│   ├── index.html                     <-- Clinical UI Shell & Injected Offline Data Hook
│   └── js/
│       ├── theme.js                   <-- Clinical Light / Refined Dark Theme Manager
│       ├── matrix.js                  <-- Weekly Protocol Adherence Matrix & Dose Modal
│       ├── charts.js                  <-- Body Comp 12-Dataset Engine, Corridors & Crosshairs
│       ├── widgets.js                 <-- Movable/Collapsible Layout Studio & Viewport Tabs
│       ├── bloodwork.js               <-- Bloodwork Command Matrix, Clinical Brief & Biomarker Dynamics
│       └── app.js                     <-- Multi-Tier Bootloader & HUD State Synchronization
├── android/                           <-- Native Kotlin Companion App (Samsung Health Data SDK)
├── scripts/                           <-- Mac Ingestion, Ingest Drainer & Exporter Pipelines
└── docs/                              <-- Architecture Specifications & Historical Records
    ├── DASHBOARD_SPECIFICATION.md     <-- Authoritative Clinical UI Rules & Full ADR Log
    ├── SYSTEM_DESIGN.md               <-- Core System Design & Pipeline Architecture
    ├── SAMSUNG_INGESTION.md           <-- Ingestion Envelope & Deduplication Spec
    ├── NAS_DEPLOYMENT_RECON.md        <-- QNAP Environment & Domain Recon (2026-08-31, evidence for §6.5)
    └── milestones/                    <-- Detailed Milestone Slice Documents (Permanent Archive)
```

---

## 2. Master Decision Index (ADR Summary)

*For complete technical rationale and clinical equations, see [`docs/DASHBOARD_SPECIFICATION.md`](docs/DASHBOARD_SPECIFICATION.md) §6.*

| ADR | Date | Title & Core Decision | Status |
|---|---|---|---|
| `ADR-001` | 2026-08-28 | **7-Day Protocol Adherence Matrix & Date Navigation** — Retroactive dose audit & backfilling. | ✅ Adopted |
| `ADR-002` | 2026-08-28 | **Co-Located Weekly Matrix & Infinite Week Navigation** — Prev/Next/Today & Date Picker binding. | ✅ Adopted |
| `ADR-003` | 2026-08-28 | **User Demographics Registry & BMI/FFMI Thresholds** — Height and date of birth in Schema v6. | ✅ Adopted |
| `ADR-004` | 2026-08-28 | **FFMI Primary Muscularity Metric & Target Corridors** — Athletic (20.0) & Cycle Target (21.5). | ✅ Adopted |
| `ADR-005` | 2026-08-28 | **Clinical Light/Dark Theme Engine & Focus Tabs** — Light mode default (`#F8FAFC`) + 4 focus tabs. | ✅ Adopted |
| `ADR-006` | 2026-08-28 | **Body Comp Time-Horizon Engine & Period Pagers** — All, Year, Quarter, Month, Week zoom. | ✅ Adopted |
| `ADR-007` | 2026-08-28 | **Proportional Tissue Mass Decomposition** — Fat Mass (kg), Muscle Mass (kg), Total Weight (kg). | ✅ Adopted |
| `ADR-008` | 2026-08-28 | **Dynamic Physiological Reference Envelopes** — Muscle (74–87%) & Fat (11–22%) normative corridors. | ✅ Adopted |
| `ADR-009` | 2026-08-28 | **Global 0–95 kg Mass Grounding & FFMI Target Band** — Unified Y-scale & 30d EMA corridor smoothing. | ✅ Adopted |
| `ADR-010` | 2026-08-28 | **Compact Frosted Tooltip & Zero-Dot EMA Sleekness** — 70% smaller tooltip & borderless shaded fills. | ✅ Adopted |
| `ADR-011` | 2026-08-28 | **Constant Vertical Grid, Crossing Chips & Top HUD Sync** — Dashed vertical grid, inline chips, live scrub. | ✅ Adopted |
| `ADR-012` | 2026-08-28 | **SDK Record Identity & Computed Adherence** — Natural-key dedup scoped to SDK rows, `utc_offset`/`local_date` persisted on the SDK path, adherence computed from `intervention_events` rather than hardcoded. | ✅ Adopted |
| `ADR-013` | 2026-08-28 | **Deterministic Export** — `data_current_through` (newest measurement) replaces wall-clock `generated_at`, so running the exporter no longer dirties the tree. | ✅ Adopted |
| `ADR-014` | 2026-08-28 | **Dual-Key Deletes (`natural_key` Persisted & Honoured)** — Added and indexed `natural_key` on 6 delete tables; unmatched returns `DELETE_NOOP`. | ✅ Adopted |
| `ADR-015` | 2026-08-28 | **Height-Normalized FFMI (Kouri et al. 1995) & Clinical Reference Bands** — Standardizes FFMI with height normalization (+0.44 delta for 178 cm), literature tiers, and 14–18% longevity fat band. | ✅ Adopted |
| `ADR-016` | 2026-08-29 | **Data-Anchored Rolling Windows & Clinical Null-Honesty** — All exporter windows anchor to `MAX(date)` in the data (never wall-clock or literals); clinical values derive from logged data or export `null`/`unlogged`, never fabricated; time-aware day-based EMAs. | ✅ Adopted |
| `ADR-017` | 2026-08-29 | **Enforcing Envelope SHA-256 & Inbox Quarantine** — `content_sha256` verified by rehashing the exact records byte-span (proven 99/99 archived payloads); un-ingestable payloads quarantine to `data/inbox/failed/`; per-record error isolation. | ✅ Adopted |
| `ADR-018` | 2026-09-04 | **NAS 24/7 Source of Truth (Topology B), Autonomous Withings Polling & Reverse Dev Sync** — QNAP NAS as 24/7 ingestion receiver, Withings 6h scheduler + ad-hoc `POST /api/sync/withings` & UI button, `deploy_to_nas.sh` `--restart`/`--rebuild-db`/load check, and `sync_from_nas.sh` dev pull. | ✅ Adopted |
| `ADR-019` | 2026-09-04 | **Movable & Collapsible Widget Engine, Column Spanning & LocalStorage Persistence** — Native drag-and-drop, accessible move buttons, 1-col/2-col width toggle, clinical collapsed preview chips, explicit Save/Reset controls, and Chart.js reflow. | ✅ Adopted |
| `ADR-020` | 2026-09-04 | **Outcome-Driven 3-Tier Hierarchy & Macro-Cycle Milestone Bar Relocation** — Tier 1 Body Comp (Outcomes), Tier 2 Recovery (Readiness), Tier 3 Protocol Compliance (Inputs); cycle progress bar relocated to Tier 3. | ✅ Adopted |
| `ADR-021` | 2026-09-04 | **Unified 2-Tier Master Architecture, Side-by-Side Packing & Domain Viewport Tabs** — Consolidates explorers into `#zone-explorers` with Nutrition & Autonomic packed side-by-side (zero row voids, +550px vertical real estate), Tier 2 for Protocol Operations, and domain viewport tabs. | ✅ Adopted |
| `ADR-022` | 2026-09-05 | **Longitudinal Bloodwork Ingestion, Draw Readiness & Biomarker Matrix (Schema v9)** — 38 months of lab results ingested (86 analytes, 18 categories, 21 draws); Draw Readiness front door (Universal 12-Biomarker Preventive Panel & LC-MS/MS warnings); Clinical consultation brief (SI Metric vs Conventional); 18-category bullet graph matrix with triage sorting and discrete scatter drill-downs; and longitudinal biomarker clearance dynamics. | ✅ Adopted |
| `ADR-023` | 2026-09-05 | **Haematocrit Telemetry Surveillance Refutation & Pure Laboratory Anchoring** — Telemetry query spike across 2.47M samples proved RHR and stress do NOT correlate with haematocrit ($r = +0.084, p = 0.794$). Autonomic proxy gauge killed to prevent dangerous false reassurance. Pure laboratory surveillance upheld. | ✅ Adopted |
| `ADR-024` | 2026-09-05 | **Subagent Delegation, Model Tier Routing, Parallelism & Unified Quality Gates** — 3-tier subagent routing based on Sept 2026 frontier benchmarks (Tier 1 `flash_lite` with 1-strike failsafe, Tier 2 `flash`, Tier 3 `inherit` on Gemini 3.8 Flash High); authorized parallel specialist subagents with zero-collision disjoint write scopes; mandatory pre-flight visible chat disclosure; mandatory Agent Topology section in design plans; standardized automated gate runner in `scripts/run_quality_gates.py`. | ✅ Adopted |
| `ADR-025` | 2026-09-05 | **Responsive Design Architecture, Mobile Reframing Directive & Viewport Ergonomics** — Root overflow-x containment, adaptive canvas isolation (`relative w-full min-w-0 overflow-hidden chart-container-adaptive`), two-phase RAF + 150ms reframing engine (`responsive.js`), weekly adherence matrix sticky frozen column, mobile command strip with studio drawer, touch target standards (>=40px), and automated V0–V5 responsive validator. | ✅ Adopted |
| `ADR-026` | 2026-09-05 | **Physical Adaptation Triad, Exercise Telemetry, Decoupled Protocol Hub & Fast Affirmative Attestation** — De-prioritizes dose logging to establish Body Comp (Outcome), Nutrition (Fuel), and Exercise (Stimulus) as primary landing workspace (`#workspace-adaptation`), resolving vertical scroll bloat into 5 modular workspaces (`Physical Adaptation`, `Recovery & Sleep`, `Labs & Bloods`, `History & Eras`, and `Protocol`); evicts Life Eras to dedicated history tab; engineers Exercise Explorer with 7d cadence, 15-workout interactive pager, intra-session Chart.js HR curves, 5 cardiac zones, and HRR-60 recovery drop; builds header `[ ⚡ Log Dose 💊 ]` and smart affirmative attestation modal with pre-checked cadence intelligence; upgrades `WidgetManager` to layout v5. | ✅ Adopted |
| `ADR-027` | 2026-09-05 | **Clean Fact Store Rebuild Protocol, SQLite Multi-File Cleanup & Daemon Isolation** — Enforces `--clean` rebuild semantics removing all SQLite artifacts (`.db`, `.db-wal`, `.db-shm`, `.db-journal`) to prevent salt collisions and `disk I/O error`; confirms historical assertions require starting from empty schema; implements macOS LaunchAgent daemon pausing during rebuild in `sync_from_nas.sh` to prevent lock contention; syncs live `withings_sync_status.json` from NAS. | ✅ Adopted |
| `ADR-028` | 2026-09-05 | **Durable Intervention Event Store, Append-Only JSON Records & Repository-Wide Event Sourcing/CQRS Policy** — Enforces Event Sourcing with CQRS as mandatory repository policy (`AGENTS.md` §1.7); raw append-only files are sole system of record; adds atomic raw JSON persistence to `sync_server.py` (`POST /api/log_dose`); adds Stage 4b replay to `rebuild_database.py`; syncs `data/records/interventions/` across NAS and local. | ✅ Adopted |
| `ADR-029` | 2026-09-07 | **Dual Telemetry Freshness, Background Polling & Withings 503 Resiliency** — Decouples Sensor Freshness from Sync Freshness, Command Strip dual freshness badge (<4h/4–12h/>12h), Withings 503 exponential retry backoff & auth revocation isolation, `/api/status` freshness telemetry, and 3-minute tab-guarded client polling. | ✅ Adopted |
| `ADR-030` | 2026-09-08 | **Companion App Dual-Endpoint Decoupling, Visual Intake (Zero-Toggle) & Remote Dev Sync Webhook** — Decouples primary/fallback ports, adds zero-toggle visual capture front door, live server freshness, background sync cadence, and remote Mac dev sync trigger. | ✅ Adopted |
| `ADR-031` | 2026-09-08 | **Multi-Set eGym & InBody Visual Telemetry Ingestion, Temporal Reconciliation & Review Queue** — Event Sourcing base records (`data/records/`), Schema v10 (`egym_workouts`, `inbody_scans`), Gemini Vision extraction + temporal reconciliation (`reconcile_scans.py`), review queue modal (`#scans-review-modal`), multi-set mechanical cards, InBody benchmark diamonds, and LaunchAgent `/tmp/` log migration. | ✅ Adopted |
| `ADR-032` | 2026-09-09 | **Gym Companion App Historical Scans Reconciliation, Segmental Body Composition & Resistance Benchmarks (Schema v11)** — Reconciled 86 historical scans from the gym companion app; Schema v11 with segmental lean/fat mass, visceral fat rating, `egym_bioage`, and `egym_muscle_balance`; 9 InBody scans (Phase Angle 6.1°–6.8° preserved across 2024–2026), 8 eGym circuits (68 sets), 4 BioAge records, and Muscle Balance diagnostics; exporter & quality gates updated. | ✅ Adopted |
| `ADR-033` | 2026-09-12 | **Unified Temporal Aggregation Engine, Nocturnal Cardiac Recovery & Dual-Tagged Physiological Intervals (Schema v12)** — Single canonical aggregation engine (`aggregation_engine.py`), Schema v12 with `sleep_id` on `heart_rate_samples` and nocturnal HR recovery on `sleep_sessions`, pipeline wiring (`rebuild_database.py`, `ingest_sdk_payload.py`, `ingest_samsung_health.py`), and foundation for Hermes Health Intake Agent. | ✅ Adopted |
| `ADR-034` | 2026-09-12 | **Autonomous Health Intake Agent, Gated Autonomy & Cost Accounting Isolation** — Hybrid Architecture: Dashboard-Native Intake Engine (`scripts/health_intake_agent.py`) + Hermes Ambient Ambassador (`scripts/hermes_skills/health_intake.py`); gated autonomy with high-confidence auto-writes (`reconciled_by: 'agent_auto'`) and review staging; isolated API key (`HEALTH_AGENT_GEMINI_KEY`) and usage ledger (`agent_usage_ledger.jsonl`); physiological plausibility gates (eGym load deltas vs historical PBs, InBody diurnal offset vs morning scale); and lean two-channel intake (companion app upload + ambient chat drops). | ✅ Adopted |
| `ADR-035` | 2026-09-13 | **Health OS UX Overhaul: Mobile Navigation Dock, Studio Mode Gating, Chart De-Cluttering & Clinical Information Hierarchy** — DOM persistence bug fix, `#mobile-bottom-nav` with 5 touch targets, Studio Mode gating, Weekly Matrix dot/pill/dash states & adherence summary, chart hover chips & default 3-series legend, compact sleep gating banner, 1,089-day gap row, double payload elimination, and universal typography upgrade. | ✅ Adopted |
| `ADR-036` | 2026-09-13 | **Health OS Code Review Remediation** — Remediated 21 findings across intake fallback zero-fabrication, 845x SQL range tagging, historical SDK workout sample tagging (24,947 samples), atomic export write/threading locks, M20 review modal UX, and clinical null-honesty sweep. | ✅ Adopted |
| `ADR-037` | 2026-09-13 | **Comprehensive Protocol Management System, Event-Sourced Manifests & Schema v13** — Unifies regimen definition into immutable event-sourced manifests (`data/records/protocols/`), Schema v13 `protocols` projection table with multi-vector columns, dynamic matrix epoch resolution with natural baseline banners, interactive Protocol Studio UI in Workspace 5, and Stage 4c CQRS replay. | ✅ Adopted |
| `ADR-038` | 2026-09-13 | **Contextual Life Events, Multimodal Chart Superimposition & Schema v14** — Event-sourced life events (`data/records/events/*.json`), Schema v14 `life_events` projection table, Stage 4d CQRS replay, multimodal Chart.js pastel bands & frosted glyph pins, category filter strip, and Workspace 4 History Curator. | ✅ Adopted |
| `ADR-039` | 2026-09-13 | **Companion App Protocol Dose Attestation, Retrospective Logging & Resilient Offline Queue** — Native Android Compose 1-tap quick attestation (`DoseAttestationScreen.kt`), dynamic active protocol discovery (`GET /api/protocol/active`), retrospective date/time/divergence logging, atomic offline queue (`pending_doses/`), and 5-tab NavigationBar. | ✅ Adopted |

---

## 3. Milestone Index & Repository History

*Each milestone is fully archived in its dedicated slice document containing test commands, schema versions, and verification details.*

| Milestone | Date | Scope Summary | Slice Document | Status |
|---|---|---|---|---|
| **M1** | 2026-08-27 | **Dual Storage, Stress & HRV Parsers (Schema v4)** — 2,406 daily JSON event logs (~805 MB), 23k stress records (958k samples), 1,145 HRV windows (91k samples). | [`docs/milestones/M1_DUAL_STORAGE_STRESS_HRV.md`](docs/milestones/M1_DUAL_STORAGE_STRESS_HRV.md) | ✅ Merged |
| **M2** | 2026-08-28 | **Native Android Companion App & SDK Scaffolding** — Kotlin 2.0 app (`android/`), Samsung Health Data SDK (1.1.0), 25-type reader registry, SHA-256 signatures. | [`docs/milestones/M2_ANDROID_COMPANION_APP.md`](docs/milestones/M2_ANDROID_COMPANION_APP.md) | ✅ Merged |
| **M3** | 2026-08-28 | **Mac-Side Ingestion Pipeline & SDK Payload Drainer** — `scripts/ingest_sdk_payload.py`, Tier 1 SHA-256 validation, Tier 2 change token chaining, idempotent UPSERTs. | [`docs/milestones/M3_MAC_INGESTION_PIPELINE.md`](docs/milestones/M3_MAC_INGESTION_PIPELINE.md) | ✅ Merged |
| **M4** | 2026-08-28 | **Frontend Modularization & Protocol Matrix** — Decoupled 4 JS modules, Monday-start Weekly Protocol Matrix, dose modal, focus tabs, dynamic corridors (ADR-001..008). | [`docs/milestones/M4_FRONTEND_MODULARIZATION.md`](docs/milestones/M4_FRONTEND_MODULARIZATION.md) | ✅ Merged |
| **M5** | 2026-08-28 | **Clinical Charting UX, Grounded Scales & HUD Time-Travel** — ADR-009..011 mass grounding, crossing chips, live HUD scrub, 60fps canvas render optimizations. | [`docs/milestones/M5_CLINICAL_CHARTING_UX.md`](docs/milestones/M5_CLINICAL_CHARTING_UX.md) | ✅ Merged |
| **M6** | 2026-08-28 | **SDK Data Integrity Repair** — ADR-012. Fixed `daily_summary` reporting 28.7 h of sleep caused by SDK identity churn; purged test fixtures from the live database; adherence no longer reports 100% from an empty log. | `scripts/migrate_v7.py` | ✅ Merged |
| **M8** | 2026-08-29 | **Live Samsung Health SDK Integration & Companion App v1.1.0** — Replaced all synthetic fixture generators across all 25 SDK readers with live `HealthDataService.getStore(context)` queries (`readData` and `readChanges`), added rich sync transmission observability card on Android, established API version handshake (`1.1.0`), and implemented `REPLAY_REJECTED` quarantine guard. | `android/` & `scripts/` | ✅ Merged |
| **M9** | 2026-09-04 | **NAS 24/7 Source of Truth (Topology B), Live Production Deployment & Autonomous Withings Polling** — Deployed containerized sync hub to QNAP Container Station, automated Withings scale 6h background scheduler + ad-hoc sync button, executed full database rebuild (2,554 days, 2.46M HR samples), and built `deploy_to_nas.sh` and `sync_from_nas.sh` reverse dev sync tooling (ADR-018). | [`docs/DASHBOARD_SPECIFICATION.md`](docs/DASHBOARD_SPECIFICATION.md) §6 | ✅ Deployed |
| **M10** | 2026-09-04 | **Movable & Collapsible Widget Engine (ADR-019)** — Zone orchestration (`zone-tier1-actions`, `zone-explorers`, `widget-adherence-matrix`), drag-and-drop, accessible move buttons, 1-col/2-col width toggle, clinical collapsed preview chips, explicit Save/Reset controls, and Chart.js reflow. | [`docs/milestones/M10_MOVABLE_COLLAPSIBLE_WIDGETS.md`](docs/milestones/M10_MOVABLE_COLLAPSIBLE_WIDGETS.md) | ✅ Merged |
| **M11** | 2026-09-04 | **Unified 2-Tier Master Architecture, Side-by-Side Packing & Viewport Tabs (ADR-020, ADR-021)** — Unified Tier 1 Telemetry (`#zone-explorers`) with side-by-side Nutrition & Autonomic packing (+550px saved), Tier 2 Protocol Operations, 16-week milestone progress track, Domain Viewport Tabs, and v4 layout engine. | [`docs/milestones/M11_OUTCOME_DRIVEN_TIERS.md`](docs/milestones/M11_OUTCOME_DRIVEN_TIERS.md) | ✅ Merged |
| **M12** | 2026-09-05 | **Longitudinal Bloodwork & Clinical Biomarker Radar (ADR-022, ADR-023)** — 38 months of lab data (570 records across 86 analytes in Schema v9 `lab_results`), Draw Readiness card (Universal 12-Biomarker Preventive Panel), Biomarker Command Matrix (bullet graphs, triage sort), Clinical Consultation Brief (SI Metric vs Conventional), Longitudinal Biomarker Clearance view, and HCT wearable proxy refutation. | [`docs/milestones/M12_BLOODWORK_RADAR.md`](docs/milestones/M12_BLOODWORK_RADAR.md) | ✅ Merged |
| **M13** | 2026-09-05 | **Responsive Design Directive, Mobile Reframing Architecture & Viewport Ergonomics (ADR-025)** — Root overflow containment, adaptive canvas isolation (`relative w-full min-w-0 overflow-hidden chart-container-adaptive`), two-phase orientation reframing engine (`responsive.js`), weekly adherence matrix sticky frozen column, mobile command strip with studio drawer, touch target standards (>=40px), and automated V0–V5 responsive validator. | [`docs/milestones/M13_RESPONSIVE_DESIGN_DIRECTIVE.md`](docs/milestones/M13_RESPONSIVE_DESIGN_DIRECTIVE.md) | ✅ Merged |
| **M14** | 2026-09-05 | **Physical Adaptation Triad, Exercise Telemetry, 5 Modular Workspaces & Fast Affirmative Attestation (ADR-026)** — 5-workspace architecture, Exercise Explorer (1,478 workouts, intra-session HR curves, 5 zones, HRR-60), header quick-dose button with smart pre-checked affirmative attestation modal, Life Eras eviction to history tab, and v5 layout engine. | [`docs/milestones/M14_PHYSICAL_ADAPTATION_WORKSPACES.md`](docs/milestones/M14_PHYSICAL_ADAPTATION_WORKSPACES.md) | ✅ Ready |
| **M16** | 2026-09-07 | **Telemetry Freshness & Withings Ingestion Hardening (ADR-029)** — Dual Telemetry Freshness decoupling sensor vs sync currency, Command Strip badge with 3-tier age coloring, Withings 503 exponential retry backoff without token refresh, NAS token-authoritative model (ADR-018), non-blocking toast alerts, dynamic Withings button states, and 3-minute tab-guarded background polling. | [`docs/milestones/M16_TELEMETRY_FRESHNESS_WITHINGS.md`](docs/milestones/M16_TELEMETRY_FRESHNESS_WITHINGS.md) | ✅ Ready |
| **M17** | 2026-09-08 | **Companion App Improvements & Visual Telemetry Reconciliation Pipeline (ADR-030, ADR-031)** — Dual-endpoint decoupling (NAS `8088` vs Mac `8765`), 1-tap presets, zero-toggle "Snap & Send" visual intake (`ScanIngestionScreen.kt`), server telemetry freshness badge, WorkManager cadence & auto-pruning, remote Mac dev sync trigger, Schema v10 (`egym_workouts`, `inbody_scans`), Gemini Vision & temporal reconciliation engine (`reconcile_scans.py`), and full UI wiring (review queue modal, multi-set mechanical loads, InBody benchmark diamonds). | [`docs/milestones/2026-09-08_M17_COMPANION_APP_IMPROVEMENTS.md`](docs/milestones/2026-09-08_M17_COMPANION_APP_IMPROVEMENTS.md) | ✅ Ready |
| **M18** | 2026-09-09 | **Gym Companion App Historical Scans Reconciled, Segmental Body Comp & eGym Resistance Benchmarks (Schema v11, ADR-032)** — Reconciled 86 historical scans from the gym companion app; Schema v11 with segmental lean/fat mass, visceral fat rating, `egym_bioage`, and `egym_muscle_balance`; 9 InBody scans (Phase Angle 6.1°–6.8° preserved across 2024–2026), 8 eGym circuits (68 sets), 4 BioAge records, and Muscle Balance diagnostics; exporter & quality gates updated. | [`docs/milestones/2026-09-09_M18_GYM_APP_SCANS_RECONCILIATION.md`](docs/milestones/2026-09-09_M18_GYM_APP_SCANS_RECONCILIATION.md) | ✅ Ready |
| **M19** | 2026-09-12 | **Unified Temporal Aggregation Engine, Nocturnal Cardiac Recovery & Dual Interval Tagging (Schema v12, ADR-033)** — Canonical `aggregation_engine.py`, Schema v12 dual-tagging (`exercise_id` + `sleep_id`), nocturnal HR recovery (`sleeping_hr_mean`, `sleeping_hr_nadir`, `nocturnal_dip_pct`), pipeline consolidation, and Quality Gate 4 updated to `user_version=12`. | [`docs/milestones/2026-09-12_M19_UNIFIED_AGGREGATION_ENGINE.md`](docs/milestones/2026-09-12_M19_UNIFIED_AGGREGATION_ENGINE.md) | ✅ Ready |
| **M20** | 2026-09-12 | **Hermes Autonomous Health Intake Agent, Gated Autonomy & Cost Isolation (ADR-034)** — Dashboard-Native Intake Engine (`health_intake_agent.py`), Hermes Ambient Ambassador (`hermes_skills/health_intake.py`), gated autonomy (auto-write $\ge \text{threshold}$, review staging), isolated API key (`HEALTH_AGENT_GEMINI_KEY`), event-sourced usage ledger (`agent_usage_ledger.jsonl`), domain plausibility gates, automated watcher, and async sync server thread. | [`docs/milestones/2026-09-12_M20_HERMES_HEALTH_INTAKE_AGENT.md`](docs/milestones/2026-09-12_M20_HERMES_HEALTH_INTAKE_AGENT.md) | ✅ Ready |
| **M21** | 2026-09-13 | **Health OS UX Overhaul & Audit Implementation (ADR-035)** — Full implementation of 11 external design review findings (P1.1–P1.3, P2.1–P2.4, P3.1–P3.4): Tier 2 DOM scoping fix, mobile bottom navigation dock, Studio Mode toggle, Adherence Matrix semantic cell states & adherence summary, Chart.js hover chips & legend declutter, compact sleep banner, Life Eras gap row, double payload elimination, universal typography upgrade, and automated responsive layout validation suite. | [`docs/milestones/2026-09-13_M21_HEALTH_OS_UX_OVERHAUL.md`](docs/milestones/2026-09-13_M21_HEALTH_OS_UX_OVERHAUL.md) | ✅ Ready |
| **M22** | 2026-09-13 | **Health OS Code Review Remediation (ADR-036)** — Remediated 21 external audit findings across intake safety (C1, M7, L3), indexed SQL range tagging & 24.9k HR samples tagged (C3, H1, M8), concurrency & contracts (C2, H4, H5, H6, M1, M9), and frontend honesty & review UX (H2, H3, M3, M6, L2). | [`docs/milestones/2026-09-13_M22_HEALTH_OS_CODE_REVIEW_REMEDIATION.md`](docs/milestones/2026-09-13_M22_HEALTH_OS_CODE_REVIEW_REMEDIATION.md) | ✅ Ready |
| **M23** | 2026-09-13 | **Comprehensive Protocol Management System, Event Sourcing & Protocol Studio (Schema v13, ADR-037)** — Unified regimen definition from 5 duplicate locations into immutable JSON records (`data/records/protocols/`), Schema v13 `protocols` table with multi-vector columns, dynamic matrix epoch resolution with natural baseline banners, interactive Protocol Studio UI in Workspace 5, and Stage 4c CQRS replay. | [`docs/milestones/2026-09-13_M23_PROTOCOL_MANAGEMENT_SYSTEM.md`](docs/milestones/2026-09-13_M23_PROTOCOL_MANAGEMENT_SYSTEM.md) | ✅ Ready |
| **M24** | 2026-09-13 | **Contextual Life Events & Superimposition Engine (Schema v14, ADR-038)** — Event-sourced life events (`data/records/events/`), Schema v14 `life_events` projection table, Stage 4d CQRS replay, multimodal Chart.js pastel bands & frosted glyph pins, interactive category filter strip, and Workspace 4 History Curator. | [`docs/milestones/2026-09-13_M24_CONTEXTUAL_LIFE_EVENTS.md`](docs/milestones/2026-09-13_M24_CONTEXTUAL_LIFE_EVENTS.md) | ✅ Ready |
| **M25** | 2026-09-13 | **Companion App Protocol Dose Attestation & Retrospective Logging (ADR-039)** — Native Android Compose 1-tap quick attestation (`DoseAttestationScreen.kt`), dynamic active protocol discovery (`GET /api/protocol/active`), retrospective date/time/divergence logging, atomic offline queue (`pending_doses/`), and 5-tab NavigationBar. | [`docs/milestones/2026-09-13_M25_COMPANION_DOSE_ATTESTATION.md`](docs/milestones/2026-09-13_M25_COMPANION_DOSE_ATTESTATION.md) | ✅ Ready |

---

## 4. Core Safety Boundaries & Deliberate Conventions

1. **Safety Boundary**: The dashboard surfaces objective telemetry against protocol rules and prompts for subjective confirmation; **it never prescribes or recommends medication dose changes**.
2. **First-Class Coverage Denominators**: Every clinical aggregate displays its backing sample size and data completeness denominator (e.g., *"from 33 of 89 complete days"*).
3. **Coverage Gating**: Sparse modules (such as sleep with $<4\text{ of }7$ nights) display an actionable behavioral banner rather than interpolating lines through missing data.
4. **Sleep Date Attribution**: Attributed strictly to **wake date**.
5. **Nutrition Completeness**: Defined as `>1200 kcal AND >=3 logged items`.
6. **Offline HTML Hook Protection**: `<script id="injected-dashboard-data">` in `dashboard/index.html` must always be preserved for offline `file://` execution and `scripts/export_dashboard_data.py`.

---

## 5. Verified Database & Pipeline State

- **Database Size**: ~945 MB / ~2.42M fact rows & samples in `data/health_dashboard.db`.
- **Primary Fact Rows**: 28,173 HR hourly records, 1,297,310 HR intra-hour samples (286,658 dual-tagged with `sleep_id`, 25,344 tagged with `exercise_id`), 23,251 stress records, 958,740 stress samples, 1,145 HRV windows, 91,267 HRV samples, 1,157 sleep sessions (266 enriched with nocturnal cardiac recovery metrics: sleeping HR mean/nadir/dip), 1,131 Withings readings, 2,530 daily summary rollups, 570 lab results (Schema v9), 68 eGym workout sets (Schema v10), 9 InBody scans (Schema v11), 4 eGym BioAge records, 1 eGym Muscle Balance record, 5 protocols (Schema v13), 5 life events (Schema v14).
- **Schema Version**: `PRAGMA user_version = 14`.
- **Daily Rollups**: 2,531 rows in `daily_summary`.
- **Verification Command**: `python3 scripts/run_quality_gates.py --strict` (Exit Code 0). All 7 quality gates pass cleanly (ADR-024). Byte-identical export (ADR-013).
- **Integrity Invariants** (all must read 0 — re-verified 2026-08-29): F0 sleep-duration violations; orphan HR samples; HR samples outside their parent hour; stale nutrition rows; NULL `local_date`; orphan recovery points; implausible bpm; **days reporting >1440 min sleep**; **`test_*` fixture rows in any table**.
- **Export Baseline** (must not change when SDK data is ingested): 1,297,310 HR samples / 28,173 HR records / 1,157 sleep / 1,441 exercise / 822 food items / 958,740 stress samples, all at `source='samsung_export'`.

---

## 6. Open Work & Deferred Items

Items that are known, deliberate, and **not** done. Anything closed here must move to §2 or §3.

### 6.1 ✅ Android — Live SDK Migration & Full-Chain Observability (COMPLETED)

**Owner stream: Android companion app & Ingestion Pipeline** (see [`docs/milestones/M2_ANDROID_COMPANION_APP.md`](milestones/M2_ANDROID_COMPANION_APP.md))

**Resolved 2026-08-29:**
1. **Live `HealthDataStore` Reader Integration**:
   - Replaced all mock/sample generators in `SleepReader.kt`, `HeartRateReader.kt`, `NutritionReader.kt`, `ExerciseReader.kt`, `BodyCompositionReader.kt`, `ActivityAndVitalsReaders.kt`, `ClinicalReaders.kt`, and `GoalsAndProfileReaders.kt` with live `HealthDataService.getStore(context).readData()` and `readChanges()`.
   - Real upstream `Metadata.id` UUIDs are extracted directly from `HealthDataPoint.uid`.
   - Natural keys are seeded from stable physiological invariants and local calendar date.
2. **Server-Side Structured Response & Version Negotiation**:
   - `scripts/sync_server.py` and `scripts/ingest_sdk_payload.py` return HTTP 200 JSON with `server_version: "1.1.0"` and structured breakdown (`records_received`, `records_ingested`, `replays_rejected`, `breakdown`).
   - `REPLAY_REJECTED` guard intercepts sliding clock replays to prevent phantom sleep records from polluting SQLite.
3. **Companion App UI Observability**:
   - `HomeScreen.kt` displays a rich **LAST SYNC TRANSMISSION** card showing live acknowledgement from the Mac server, record category chips with counts, and rejected replay alerts.
4. **Daemon Lifecycle**:
   - Mac LaunchAgent daemon restarted and running PID 66172 with latest v1.1.0 codebase.

### 6.2 🟠 Adherence is unmeasurable — `intervention_events` is empty

`meds_compliance_pct` now renders `null` / "No doses logged" rather than a phantom 100% (ADR-012). But the table has **zero rows**, so nothing in the intent-vs-fact model can be computed: no 72 h cadence_compound timer against reality, no 48h Interval Counter, no divergence vocabulary in use.

This is an input problem, not an architectural one. Nutrition logging sits at ~35% of days using an app easier than anything specified. Until doses are logged, the adherence half of the dashboard is scaffolding.

### 6.3 ✅ eGym mechanical loads & InBody visual telemetry (RESOLVED 2026-09-08 / 2026-09-09)

Resolved across ADR-031 and ADR-032 (Milestones M17 & M18):
1. **Schema v10 & v11 Projections**: Created `egym_workouts`, `inbody_scans` (with segmental lean/fat mass and Phase Angle), `egym_bioage`, and `egym_muscle_balance`.
2. **Visual Intake & Reconciliation**: 86 historical scans from the gym companion app cataloged, validated, and ingested (`scripts/ingest_gym_app_scans.py`).
3. **Event Sourcing Invariant (ADR-028)**: Stored as immutable event JSON files in `data/records/inbody/` and `data/records/egym/` with 100% deterministic replay in `scripts/rebuild_database.py`.
4. **Dashboard Telemetry**: Multi-set mechanical load cards in Exercise Explorer, discrete InBody Gold Benchmark points with diurnal offset, BioAge history, and Muscle Balance diagnostics exported into dashboard payload.

### 6.4 🟡 Audit remediation — Waves 1–2 landed, Wave 3 landed, residue registered (2026-08-31)

Four external AI audits were adjudicated claim-by-claim in [`docs/audits/2026-08-29_AUDIT_ADJUDICATION.md`](audits/2026-08-29_AUDIT_ADJUDICATION.md) (canonical — several raw-audit claims are refuted there; never implement from the raw audit files). Fixes executed on `fix/audit-remediation` per [`docs/audits/REMEDIATION_PLAN.md`](audits/REMEDIATION_PLAN.md): Waves 1–2 complete (clinical integrity ADR-016, SHA enforcement ADR-017, server hardening, backfill fidelity, EMA correctness, ingest performance). Wave 3 (incremental `daily_summary`, Withings incremental sync, gitignore fixes) complete.

**Deliberately deferred, in priority order:**
1. ✅ **Daemon restarted (2026-08-29, post-merge)** — the LaunchAgent now runs the remediated code on port **8765** (verified: `/api/status` 200 on 8765, 8080 dead, traversal probe 403). **Remaining half:** The app source endpoint has been updated to `http://<lan-ip>:8765`, but the app **still needs a rebuild and reinstall on the physical device** to pick up the port change, backfill, and token fixes. Until then, its stored URL still points at 8080; pushes fail gracefully and no longer advance change tokens, so no data is lost while it's stale, but nothing syncs.
2. **Docker/QNAP deployment** — deferred by owner decision (2026-08-29); **environment and domain reconnaissance completed 2026-08-31, see §6.5**. Of the three blockers recorded in the adjudication (NEW-5), only one still stands: **the server has no authentication**, which gates any public exposure path. The `schema.sql` copy was resolved 2026-08-31 (dead `COPY` removed). The "container `PORT` env unread" claim is stale — `scripts/sync_server.py:286` does read it.
3. **Withings local-date attribution (A18)** — measurements are dated by UTC day; fixing attribution would shift historical rows across days and change exports, so it needs a deliberate backfill design, not a casual patch.
4. **Single-threaded server (A15)** — kept single-threaded deliberately; it serializes ingest. Revisit only with the ingest lock + WAL already in place (done) and a real concurrency need.
5. **Parallel SDK readers (A27)** — blocked on proof that `HealthDataStore` is thread-safe.
6. Low-tier: dual-storage rewrite skip (A24), CSV-ingester fallback ids / per-stage commits / bare excepts (A29), LaunchAgent `ThrottleInterval`, `migrate_v6` users-table guard (A30).

### 6.5 🟢 NAS deployment — reconnaissance complete, path recommended, nothing built (2026-08-31)

Full evidence in [`docs/NAS_DEPLOYMENT_RECON.md`](NAS_DEPLOYMENT_RECON.md) — verified NAS
environment, host port map, Namecheap domain inventory, the rejected options and why, and
per-step verification criteria. Summary only here; that document is canonical.

**Nothing was built or changed.** Verified at session close: the deploy directory does not exist,
no dashboard image or container exists, Docker build cache is 0 B, nothing listens on 8088,
Tailscale was still `Enable = FALSE` at that point, `git status` was unchanged, and no DNS or
registrar setting was modified. (Tailscale was enabled and joined later the same day — see below.)

**Capability confirmed.** QNAP TS-X82, QTS 5.2.10, Docker 27.1.2 + Compose v2.29.1, 367 GB free on
`DATA_VOLUME`. Three gotchas worth carrying forward: the `docker` binary is **not on `$PATH`**
over SSH; **port 8765 is taken by `lircd`**, so the Mac daemon's port cannot be reused on the NAS;
and there is **no usable native Python 3** — containerise, never target the interpreter buried in
HybridBackup.

**Path taken: Tailscale — enabled and verified working 2026-08-31.** The NAS is on tailnet
`tailnet.local` as **`health-dashboard`** (`<lan-ip>`), reachable from a peer over a direct
LAN path at ~2.6 ms with MagicDNS resolving. Chosen on failure mode: with no authentication in
`sync_server.py`, a public-hostname model fails **open** on a single Access-policy mistake, while a
tailnet fails **closed**. It also needs no DNS change at all — which matters while Namecheap account
2FA is off. Cloudflare Tunnel on `health.example.com` is **deferred, not rejected**; the two coexist.
**Still no ADR — connectivity is proven, but nothing is deployed behind it yet.**

Two Tailscale traps are recorded in the recon doc §2.2 and cost time on 2026-08-31: the CLI needs
`--socket=/tmp/tailscale/tailscaled.sock` and is not on `$PATH`; and interrupting `tailscale up`
leaves the node authenticated but `WantRunning=false`, which presents as a registered-but-offline
machine that is *not* a failed login.

**Blocking any path:** the app has no authentication (Tailscale removes the exposure, it does not
add auth). The `schema.sql` build blocker is **resolved (2026-08-31)** — the `COPY` was dead weight,
removed from `docker/Dockerfile`; nothing ever read it and the schema is self-bootstrapping.

**Dev/prod topology settled and implemented via ADR-018 (2026-09-04):** Development stays on the Mac; the NAS runs the 24/7 production container under Container Station (`health-dashboard.local:8088`). **Topology B adopted:** Phone pushes directly to the NAS 24/7 over Tailscale, and Withings OAuth tokens reside exclusively on the NAS volume (`/app/config/withings_tokens.json`) with automated 6h background polling and ad-hoc UI trigger. Code deploys from a **clean tree with commit stamping, pre-flight load checking, and container restart** via `deploy_to_nas.sh --execute --restart`. Local development pulls fresh telemetry archives from the NAS and materializes the local database via `scripts/sync_from_nas.sh` (one-way archive sync; SQLite `.db` binary transfer strictly forbidden).

**Production Network & Mesh VPN:** Configured with zero public attack surface, relying on private mesh overlay VPN and automated reverse proxying.

### 6.6 ✅ Milestone 11: Unified 2-Tier Master Architecture, Side-by-Side Packing & Viewport Tabs (ADR-020, ADR-021) (COMPLETED)

* **Status:** Completed on branch `feat/outcome-driven-tiers` (2026-09-04); slice documented in [`docs/milestones/M11_OUTCOME_DRIVEN_TIERS.md`](milestones/M11_OUTCOME_DRIVEN_TIERS.md).
* **Accomplishments:**
  1. **Unified 2-Tier Master Architecture & Side-by-Side Packing (`ADR-021`)**:
     - Consolidated all 5 explorer modules into a unified 2-column grid (`#zone-explorers`) in Tier 1 (`#tier-1-section`).
     - Placed Nutrition Partitioning (`exp-nutrition`, 1-col) and Autonomic Balance (`exp-autonomic`, 1-col) as twin side-by-side cards on Row 2, completely eliminating 50% row voids and intermediate tier banners, recovering ~550px of vertical screen real estate.
     - Tier 2 (`#tier-2-section`) houses all protocol operations, compliance, and safety controls (`#macro-milestone-bar`, `#zone-tier2-matrix`, `#e2-alert-banner`, `#zone-tier2-actions`).
  2. **Macro-Protocol Milestone Bar**: Constructed clinical progress track at head of Tier 2 with 16-week timeline, discrete milestone nodes (W0 Baseline, W4 Adaptive, W6 Deload & Steady-State Bloods, W8 Mid-Bloods, W16 Peak Target), and active compound summary.
  3. **Global Header Cleanup**: Stripped `#header-phase-badge` to establish clean executive branding.
  4. **Domain Viewport Tabs**: Added `[ All Tiers | Body Comp | Recovery | Protocol ]` centered in `#layout-control-banner` with active card filtering in `#zone-explorers`, tier switching, auto-expansion, and Chart.js reflow.
  5. **Layout Engine v4 (`WidgetManager`)**: Upgraded layout engine to v4 schema (`tier1`, `tier2`, `explorers`, `actions`), added backwards-compatible migration for v1/v2/v3 states, implemented 2-tier reordering (`moveTier`), fixed save button layout shift/scrollbar, and added rich collapsed preview chips.

### 6.7 ✅ Milestone 16: Telemetry Freshness & Withings Ingestion Hardening (ADR-029) (COMPLETED)

* **Status:** Completed on branch `feat/m16-telemetry-freshness-withings` (2026-09-07); slice documented in [`docs/milestones/M16_TELEMETRY_FRESHNESS_WITHINGS.md`](milestones/M16_TELEMETRY_FRESHNESS_WITHINGS.md).
* **Accomplishments:**
  1. **Dual Telemetry Freshness Architecture (`ADR-029`)**:
     - Decoupled "Sensor Freshness" (latest biometric measurement fact in SQLite, `data_current_through`) from "Sync Freshness / Pipeline Contact" (timestamp of newest payload received from Android companion or Withings scale, `last_sdk_sync`, `last_scale_sync`).
     - Upgraded Command Strip HUD cluster with Dual Freshness badge (`#telemetry-freshness-badge`, `#freshness-status-dot`, `#generated-timestamp`, `#freshness-sync-chip`) with 3-tier age coloring (<4h emerald, 4–12h amber, >12h rose pulse) and detailed hover telemetry breakdown.
  2. **Withings Resiliency & NAS Token Authority (`scripts/sync_withings.py`)**:
     - Added transient HTTP detection (503, 502, 504, 429) with exponential retry backoff.
     - Prevented transient outages from falsely triggering token refreshes (which burn single-use rotating tokens).
     - Handled permanent token revocation (`invalid refresh_token`) cleanly as `auth_revoked` without crashes.
     - Preserved `last_success` across failed sync attempts.
     - Enforced NAS token authority (ADR-018): local sync reports NAS authority and proxies requests to NAS container (`http://health-dashboard.local:8088/api/sync/withings`).
     - Proven with 5/5 unit tests in `scripts/test_withings_resilience.py`.
  3. **Sync Server Status Endpoint (`scripts/sync_server.py`)**:
     - Added `build_status_payload()` serving structured `telemetry_freshness` on `GET /api/status`.
     - Implemented NAS reverse proxying on `POST /api/sync/withings` with proper HTTP status propagation (200, 503, 401).
  4. **Frontend Dynamic Telemetry Polling & Toast Notifications (`dashboard/js/app.js`)**:
     - Replaced invasive modal `alert()` popups with non-blocking toast notification system (`#toast-container`).
     - Added visual sync states on Withings scale button (`Scale Synced`, `Scale (503)`, `Auth Expired`, `NAS Scale Hub`).
     - Upgraded `loadData(forceFetch)` to fetch live `/dashboard_data.json?_ts=...` over HTTP before falling back to embedded `window.__DASHBOARD_DATA__`.
     - Implemented 3-minute background telemetry polling with `document.visibilityState` tab pause/resume guard.

### 6.8 🟠 `life_eras` Historical Gap & Review Input (NEEDS REVIEW)

* **Evidence:** The `life_eras` table has a multi-year unassigned gap that covers most historical blood draws, so biomarker changes in that window cannot yet be attributed to an era.
* **Resolution Plan:** Do not unilaterally alter historical labels. Marked as `[NEEDS_REVIEW]`. An interactive life-era manager will be built in a future milestone so the user can record historical epochs directly.

### 6.9 ✅ Milestone 12: Longitudinal Bloodwork & Clinical Biomarker Radar (COMPLETED)

* **Adjudication Reference:** Full claim-by-claim adjudication and phased implementation plan recorded in [`docs/audits/2026-09-05_LAB_INGESTION_ADJUDICATION.md`](audits/2026-09-05_LAB_INGESTION_ADJUDICATION.md).
* **Slice Document:** [`docs/milestones/M12_BLOODWORK_RADAR.md`](milestones/M12_BLOODWORK_RADAR.md).
* **Accomplishments:**
  1. **Data Sanitization & Assertions (Stage 0)**: Fixed platelets ($/mm^3 \div 1000 \rightarrow 10^9/L$) and a single-draw testosterone typo in the source workbook. Built `scripts/ingest_lab_results.py` with assertions A1–A4 (215 conversion pairs, 31 endocrine checks, 535 range checks). Emitted byte-deterministic JSON files in `data/records/bloodwork/`.
  2. **Byte-Deterministic Storage & Schema v9 (Stage 1)**: Built `scripts/migrate_v9.py` creating table `lab_results` with 17 canonical/provenance columns, comparator separation (`<0.3`), and secondary indexes. Integrated with `scripts/rebuild_database.py`. Extended `scripts/export_dashboard_data.py` with `MAX(draw_date)`-anchored bloodwork section.
  3. **Draw Readiness & Safety Radar (Stage 2)**: Built head card in `dashboard/js/bloodwork.js` (`exp-bloodwork`) featuring the Universal 12-Biomarker Preventive Panel checklist vs the last draw, a missing-baseline alert, an LC-MS/MS assay standardization warning, and draw preparation countdown.
  4. **Biomarker Command Matrix & Clinical Consultation Brief (Stage 3)**: Implemented 18-category matrix with bullet graphs showing reported reference intervals, triage sorting (out-of-range floats to top), recency age badges, and discrete scatter plot drill-downs with backing sample dates (strictly zero continuous lines across multi-month gaps per `AGENTS.md` §3.5). Built one-tap SI Metric (`nmol/L`, `g/L`, `mmol/L`) vs Conventional (`ng/dL`, `g/dL`, `mg/dL`) Clinical Consultation Brief.
  5. **Longitudinal Biomarker Clearance View (Stage 4)**: Specialized interactive module visualizing empirical multi-draw clearance dynamics and biomarker ratios.
  6. **Haematocrit Telemetry Query Spike & Refutation (Stage 5)**: Query-only analysis across continuous biometric samples and longitudinal HCT draws proved wearable RHR and stress do NOT correlate with haematocrit ($r = +0.084, p = 0.794$). Wearable proxy gauge permanently killed (ADR-023) to eliminate life-threatening false reassurance.

### 6.10 🟠 Clinical Lab Residue: Preventive Panel Baseline & Assay Standardization

* **Evidence:** Ingested longitudinal bloodwork shows at least one mandatory marker with no baseline, and some hormone assays were run by standard immunoassay rather than LC-MS/MS, introducing inter-assay variability.
* **Actionable Next Step:** For the next scheduled draw:
  1. Requisition any mandatory marker that has no baseline yet.
  2. Mandate LC-MS/MS assay methodology with the phlebotomy provider.

### 6.11 ✅ Milestone 23: Comprehensive Protocol Management System (COMPLETED)

* **Slice Document:** [`docs/milestones/2026-09-13_M23_PROTOCOL_MANAGEMENT_SYSTEM.md`](milestones/2026-09-13_M23_PROTOCOL_MANAGEMENT_SYSTEM.md).
* **ADR Reference:** `ADR-037` in [`docs/DASHBOARD_SPECIFICATION.md`](DASHBOARD_SPECIFICATION.md) §6.
* **Accomplishments:**
  1. **Event Sourced Protocol Manifests (`data/records/protocols/`)**: Authored 5 canonical immutable JSON protocol definitions, one per era. Unified regimen definitions from 5 duplicate locations into one authoritative directory, completely resolving Finding M2.
  2. **Schema Migration v13 (`scripts/migrate_v13.py`)**: Created `protocols` table with multi-vector JSON columns (`compounds_json`, `supplements_json`, `training_program_json`, `diagnostic_panel_json`, `safety_redlines_json`, `milestones_json`), synced compounds with `interventions_catalog`, and set `PRAGMA user_version = 13`.
  3. **Fact Store Rebuild & CQRS Replay (ADR-028)**: Added Stage 4c in `scripts/rebuild_database.py` to replay protocol manifests from disk into SQLite on clean rebuilds, ensuring 100% state regeneration.
  4. **Dynamic Weekly Adherence Matrix (`dashboard/js/matrix.js`)**: Temporal resolver maps viewed week to exact historical protocol; displays Natural Baseline status banner for natural epochs (2017–2023), eliminating phantom checkboxes; fast dose attestation modal derives items dynamically from active protocol.
  5. **Protocol Studio UI (`dashboard/js/protocol_studio.js`)**: Interactive management studio in Workspace 5 with protocol selector, 5 multi-vector tabs (Pharma, Supplements, Training, Diagnostics, Safety Redlines), draft cloning, JSON export, and server save (`POST /api/protocol`).
  6. **Quality Gates & Test Suite**: All 7 strict quality gates pass (`python3 scripts/run_quality_gates.py --strict`); comprehensive unit test suite (`scripts/test_protocol_system.py`) validates canonical manifests, scratch migration, temporal resolution, and endpoint logic with 100% offline live DB safety.

### 6.12 ✅ Milestone 24: Contextual Life Events & Superimposition Engine (COMPLETED)

* **Slice Document:** [`docs/milestones/2026-09-13_M24_CONTEXTUAL_LIFE_EVENTS.md`](milestones/2026-09-13_M24_CONTEXTUAL_LIFE_EVENTS.md).
* **ADR Reference:** `ADR-038` in [`docs/DASHBOARD_SPECIFICATION.md`](DASHBOARD_SPECIFICATION.md) §6.
* **Accomplishments:**
  1. **Event Sourced Life Event Records (`data/records/events/`)**: Authored 5 canonical immutable JSON event records (`travel`, `surgery`, `illness`, `injury`, `stress`) with physiological impact vectors (`fluid_retention`, `sleep_disruption`, `elevated_rhr`, `training_hiatus`).
  2. **Schema Migration v14 (`scripts/migrate_v14.py`)**: Structured SQLite projection table `life_events` with temporal and category indexes, setting `PRAGMA user_version = 14`.
  3. **Fact Store Rebuild & CQRS Replay (ADR-028)**: Added Stage 4d in `scripts/rebuild_database.py` to replay life events on clean rebuilds.
  4. **Multimodal Chart.js Superimposition Engine (`charts.js`)**: Custom plugin `lifeEventsSuperimpositionPlugin` rendering shaded pastel background bands for macro eras and event duration windows in `beforeDraw`, and subtle vertical dashed pins with frosted category glyph badges (`✈️`, `🏥`, `🤒`, `🏋️`, `⚡`) in `afterDraw`.
  5. **Interactive Filter Strip (`life_events.js`)**: Category filter pills above Body Comp and Autonomic charts with instant canvas re-render.
  6. **Workspace 4 History Curator**: Contextual Life Events table in Workspace 4 with date bounds, duration, tags, notes, and modal editor (`#life-event-modal`) syncing to `POST /api/events`.
  7. **Automated Test Suite (`test_life_events_system.py`)**: 4 unit tests passing cleanly with 100% offline live DB write safety guard.

### 6.13 ✅ Milestone 25: Companion App Dose Attestation & Retrospective Logging (COMPLETED)

* **Slice Document:** [`docs/milestones/2026-09-13_M25_COMPANION_DOSE_ATTESTATION.md`](milestones/2026-09-13_M25_COMPANION_DOSE_ATTESTATION.md).
* **ADR Reference:** `ADR-039` in [`docs/DASHBOARD_SPECIFICATION.md`](DASHBOARD_SPECIFICATION.md) §6.
* **Accomplishments:**
  1. **Dynamic Protocol Discovery**: Android `SyncNetworkClient.kt` retrieves active compounds, doses, and cadences from `GET /api/protocol/active` with network fallback and offline cache.
  2. **1-Tap Fast Confirmation (`DoseAttestationScreen.kt`)**: Large touch targets ($\ge 48\text{dp}$) for active compounds, 1-tap attestation with current timestamp, optimistic today-attested feedback, and zero manual typing friction.
  3. **Retrospective & Divergence Logging**: DatePicker, TimePicker, precision tiers (`exact`, `approximate`, `recalled`), 6 clinical divergence codes (`adherent`, `late`, `dose_adjusted`, `deliberate_skip`, `site_altered`, `missed`), and optional notes.
  4. **Crash-Resilient Offline Queue (`DoseQueueManager.kt`)**: Atomic `.json.tmp` -> `.json` write-and-rename mechanics in `context.filesDir/pending_doses/` guaranteeing zero data loss during offline usage, with manual and automatic queue flush.
  5. **Companion Navigation Integration**: 5-tab Material 3 NavigationBar in `MainActivity.kt` with `Icons.Default.Medication`, and 1-tap quick action card on `HomeScreen.kt`.
  6. **Mobile Test Suite (`DoseAttestationTest.kt`)**: Gradle unit tests passing cleanly covering serialization, schema contract, and queue atomicity.

### 6.14 ✅ Milestone 20: Hermes Autonomous Health Intake Agent (COMPLETED)

* **Document:** [`docs/HERMES_HEALTH_AGENT_DESIGN.md`](HERMES_HEALTH_AGENT_DESIGN.md) and slice [`docs/milestones/2026-09-12_M20_HERMES_HEALTH_INTAKE_AGENT.md`](milestones/2026-09-12_M20_HERMES_HEALTH_INTAKE_AGENT.md).
* **Accomplishments:**
  1. **Dashboard-Native Intake Engine (`scripts/health_intake_agent.py`)**: Gated autonomy pipeline with domain plausibility checks (eGym loads vs historical PBs, InBody diurnal delta vs morning Withings scale readings, Phase Angle checks, and continuous wearable cardiac curve fusion). Writes immutable Event Sourcing JSON to `data/records/{egym,inbody}/` (ADR-028) and projects into SQLite fact store.
  2. **Dedicated API Key & Cost Accounting**: Complete provider billing isolation via `HEALTH_AGENT_GEMINI_KEY`. Appends every scan extraction to `data/records/telemetry/agent_usage_ledger.jsonl` with input/output tokens, execution time, confidence, and estimated USD cost based on `gemini-2.5-flash` pricing ($0.10/$0.40 per 1M tokens).
  3. **Gated Autonomy**: High confidence ($\ge 0.90$ eGym, $\ge 0.95$ InBody) + plausibility checks pass $\rightarrow$ autonomous commit (`reconciled_by: 'agent_auto'`); anomalous or low-confidence scans are staged (`staged_pending_review`) for 1-click confirmation in `#scans-review-modal` or conversational resolution via Hermes.
  4. **Intake Channels**: Door 1 (Android Companion App) uploads trigger immediate asynchronous processing in `sync_server.py`; Door 2 (Ambient Messaging) enables WhatsApp/Telegram drops via Hermes Ambassador Skill (`scripts/hermes_skills/health_intake.py`).
  5. **Verification & Testing**: Comprehensive unit tests (`scripts/test_health_intake_agent.py`) pass 8/8 with 100% offline live DB write safety guard. All 7 strict quality gates pass.

### 6.15 ✅ Milestone 21: Health OS UX Overhaul & Design Audit (COMPLETED)

* **Adjudication Reference:** [`docs/audits/2026-09-13_HEALTH_OS_DESIGN_REVIEW_ADJUDICATION.md`](audits/2026-09-13_HEALTH_OS_DESIGN_REVIEW_ADJUDICATION.md).
* **Slice Document:** [`docs/milestones/2026-09-13_M21_HEALTH_OS_UX_OVERHAUL.md`](milestones/2026-09-13_M21_HEALTH_OS_UX_OVERHAUL.md).
* **Accomplishments:**
  1. Resolved DOM leak where Tier 2 persisted beneath all tabs; mobile bottom navigation dock (`#mobile-bottom-nav`) with 5 touch targets.
  2. Studio Mode gating (`WidgetManager.toggleStudioMode()`) hiding reorder handles in reading mode.
  3. Weekly Adherence Matrix semantic states (future dot `·`, today pill `Log`, past dash `—`) and adherence summary count.
  4. De-cluttered Chart.js hover chips and 3-series default legend; compact 1-line sleep gating banner.
  5. Surfaced 1,089-day unassigned historical gap in Life Eras table; eliminated redundant payload fetching via `/api/status` probe.
  6. Automated responsive layout validator (`scripts/verify_responsive_layout.py`) verifying V0–V4.

### 6.16 ✅ Milestone 22: Health OS Code Review Remediation (COMPLETED)

* **Adjudication Reference:** [`docs/audits/2026-09-13_HEALTH_OS_CODE_REVIEW_ADJUDICATION.md`](audits/2026-09-13_HEALTH_OS_CODE_REVIEW_ADJUDICATION.md).
* **Slice Document:** [`docs/milestones/2026-09-13_M22_HEALTH_OS_CODE_REVIEW_REMEDIATION.md`](milestones/2026-09-13_M22_HEALTH_OS_CODE_REVIEW_REMEDIATION.md).
* **Accomplishments:**
  1. **Intake Pipeline Safety (C1, M7, L3)**: Eradicated synthetic fallback data (`confidence: 0.0`, empty payload, staged for review); token ledger records failed attempts with 0 cost; exact scan lookup in `get_staged_scan()`.
  2. **Ingestion & Tagging (C3, H1, M8)**: Direct indexed SQL range tagging (`idx_hr_samples_ts`) reduced interval tagging from >10 minutes to 0.71s (~845x speedup); tagged 24,947 historical companion workout HR samples; auto-derived missing exercise `end_time`.
  3. **Concurrency & Contracts (C2, H4, H5, H6, M1, M9)**: Fixed `do_POST` 404 routing bug; protected export writes (`tempfile` + `os.replace` + `threading.Lock`) and Withings token refresh; synchronized dual-freshness payload format between server and frontend; unique event IDs (`short_id`) preventing same-day file overwrites; dynamic Quality Gate 4 row counts; synced telemetry ledger in `sync_from_nas.sh`.
  4. **Frontend Honesty & Review UX (H2, H3, M3, M6, L2)**: `#scans-review-modal` renders M20 proposals with `escapeHtml` and 1-click reconcile; purged hardcoded literals (`62.1 kg`, `78.4 kg`, `19.9% BF`, `78.0g Prot`, `HRV ~40ms`, `RHR 80–92 bpm`, `?? 10`) across `app.js`, `widgets.js`, `charts.js`, and `bloodwork.js`; fast dose attestation validates `res.ok`.

### 6.17 📋 Deferred Audit Items (M2, M4, M5)

* **M2 (Regimen 5-Place Duplication)**: ✅ **Resolved 2026-09-13 in Milestone 23 (ADR-037)**. Regimen schedule unified into event-sourced manifests under `data/records/protocols/`, driving dynamic matrix rendering and Schema v13 fact store.
* **M4 (Unauthenticated Mutating Endpoints)**: `POST /api/scans/reconcile` and `/api/log_dose` are unauthenticated. Requires shared-secret header synchronization with Android companion app; server currently bound to local/Tailscale interface.
* **M5 (Tailwind CDN vs Static Build)**: Dashboard uses Tailwind Play CDN with inline configuration. Offline `file://` execution is preserved via pre-rendered static CSS fallback; moving to offline Tailwind CLI build deferred to build toolchain modernization.







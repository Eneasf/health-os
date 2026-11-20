# Architecture Decision Records (ADRs) — Health OS (Preview / V1)

Canonical decision index extracted from legacy handover (`docs/archive/HANDOVER_LEGACY.md`) under `GADR-033`. Full technical rationale and clinical equations live in [`docs/DASHBOARD_SPECIFICATION.md`](DASHBOARD_SPECIFICATION.md) §6; milestone verification logs live in [`docs/milestones/`](milestones/).

## Master Decision Index (ADR Summary)

| ADR | Date | Title & Core Decision | Status |
| :--- | :--- | :--- | :--- |
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
| `ADR-012` | 2026-08-28 | **SDK Record Identity & Computed Adherence** — Natural-key dedup scoped to SDK rows, `utc_offset`/`local_date` persisted, adherence computed from `intervention_events`. | ✅ Adopted |
| `ADR-013` | 2026-08-28 | **Deterministic Export** — `data_current_through` replaces wall-clock `generated_at` for byte-deterministic tracked outputs. | ✅ Adopted |
| `ADR-014` | 2026-08-28 | **Dual-Key Deletes (`natural_key` Persisted & Honoured)** — Indexed `natural_key` on 6 delete tables; unmatched returns `DELETE_NOOP`. | ✅ Adopted |
| `ADR-015` | 2026-08-28 | **Height-Normalized FFMI (Kouri et al. 1995) & Clinical Reference Bands** — Standardized FFMI height normalization, literature tiers, and 14–18% longevity fat band. | ✅ Adopted |
| `ADR-016` | 2026-08-29 | **Data-Anchored Rolling Windows & Clinical Null-Honesty** — Windows anchor to `MAX(date)` in data; unlogged values render `null`/`unlogged`, never fabricated; day-based EMAs. | ✅ Adopted |
| `ADR-017` | 2026-08-29 | **Enforcing Envelope SHA-256 & Inbox Quarantine** — Rehash exact `"records"` byte-span; quarantine failures to `data/inbox/failed/`; per-record error isolation. | ✅ Adopted |
| `ADR-018` | 2026-09-04 | **NAS 24/7 Source of Truth (Topology B), Autonomous Withings Polling & Reverse Dev Sync** — QNAP NAS receiver, 6h Withings scheduler, `deploy_to_nas.sh`, and `sync_from_nas.sh`. | ✅ Adopted |
| `ADR-019` | 2026-09-04 | **Movable & Collapsible Widget Engine** — Native drag-and-drop, accessible move controls, 1/2-col span toggle, collapsed preview chips, LocalStorage persistence, Chart.js reflow. | ✅ Adopted |
| `ADR-020` | 2026-09-04 | **Outcome-Driven Hierarchy & Macro-Cycle Milestone Bar Relocation** — Outcomes first, Recovery second, Protocol Compliance inputs third. | ✅ Adopted |
| `ADR-021` | 2026-09-04 | **Unified 2-Tier Master Architecture, Side-by-Side Packing & Domain Viewport Tabs** — `#zone-explorers` side-by-side packing and domain viewport tabs. | ✅ Adopted |
| `ADR-022` | 2026-09-05 | **Longitudinal Bloodwork Ingestion, Draw Readiness & Biomarker Matrix (Schema v9)** — 86 analytes across 18 categories, Draw Readiness panel, SI/Conventional brief, discrete scatter drill-downs. | ✅ Adopted |
| `ADR-023` | 2026-09-05 | **Haematocrit Telemetry Surveillance Refutation & Pure Laboratory Anchoring** — Wearable RHR/stress uncorrelated with HCT (`r = +0.084, p = 0.794`); wearable proxy killed; lab-only surveillance upheld. | ✅ Adopted |
| `ADR-024` | 2026-09-05 | **Subagent Delegation, Parallelism & Unified Quality Gates** — Disjoint write scopes, pre-flight disclosure, and standardized `scripts/run_quality_gates.py`. | ✅ Adopted *(model tiers updated per `GADR-034`)* |
| `ADR-025` | 2026-09-05 | **Responsive Design Architecture & Mobile Reframing Directive** — Root `overflow-x` containment, adaptive canvas isolation, RAF reframing (`responsive.js`), sticky matrix column, V0–V5 validator. | ✅ Adopted |
| `ADR-026` | 2026-09-05 | **Physical Adaptation Triad, Exercise Telemetry & 5 Modular Workspaces** — Body Comp/Nutrition/Exercise landing workspace, Exercise Explorer (HR curves/zones/HRR-60), quick-dose attestation modal, layout v5. | ✅ Adopted |
| `ADR-027` | 2026-09-05 | **Clean Fact Store Rebuild Protocol & Daemon Isolation** — `--clean` deletes `.db`/`.db-wal`/`.db-shm`/`.db-journal`; pause LaunchAgent during rebuilds. | ✅ Adopted |
| `ADR-028` | 2026-09-05 | **Durable Event Sourcing & CQRS Policy** — Immutable raw files (`data/records/`, `archive/`) are sole system of record; SQLite is strictly a disposable read projection rebuilt via `rebuild_database.py`. | ✅ Adopted |
| `ADR-029` | 2026-09-07 | **Dual Telemetry Freshness, Background Polling & Withings 503 Resiliency** — Decoupled sensor vs sync freshness badge, 503 exponential backoff without burning refresh tokens, 3-min tab-guarded polling. | ✅ Adopted |
| `ADR-030` | 2026-09-08 | **Companion App Dual-Endpoint Decoupling & Visual Intake** — Decoupled NAS (`8088`) vs Mac (`8765`), zero-toggle Snap & Send intake, server freshness badge, remote dev sync webhook. | ✅ Adopted |
| `ADR-031` | 2026-09-08 | **Multi-Set eGym & InBody Visual Telemetry Ingestion (Schema v10)** — Event-sourced `data/records/`, Gemini Vision extraction & temporal reconciliation (`reconcile_scans.py`), `#scans-review-modal`. | ✅ Adopted |
| `ADR-032` | 2026-09-09 | **Gym App Historical Scans Reconciliation & Segmental Body Comp (Schema v11)** — 86 historical scans reconciled; segmental lean/fat mass, Phase Angle, BioAge, and Muscle Balance diagnostics. | ✅ Adopted |
| `ADR-033` | 2026-09-12 | **Unified Temporal Aggregation Engine & Nocturnal Cardiac Recovery (Schema v12)** — Single `aggregation_engine.py`, `sleep_id` + `exercise_id` dual-tagging on HR samples, nocturnal dip metrics. | ✅ Adopted |
| `ADR-034` | 2026-09-12 | **Autonomous Health Intake Agent, Gated Autonomy & Cost Isolation** — `health_intake_agent.py` + Hermes ambassador, high-confidence auto-write vs review staging, `HEALTH_AGENT_GEMINI_KEY`, usage ledger. | ✅ Adopted |
| `ADR-035` | 2026-09-13 | **Health OS UX Overhaul** — Tier 2 DOM scoping fix, `#mobile-bottom-nav`, Studio Mode gating, matrix semantic cell states, chart legend declutter, compact sleep banner, double payload elimination. | ✅ Adopted |
| `ADR-036` | 2026-09-13 | **Health OS Code Review Remediation** — 21 audit fixes across zero-fabrication intake fallback, indexed SQL range tagging (~845x speedup), atomic export writes, and hardcoded literal purge. | ✅ Adopted |
| `ADR-037` | 2026-09-13 | **Comprehensive Protocol Management System (Schema v13)** — Event-sourced manifests (`data/records/protocols/`), `protocols` table, dynamic matrix epoch resolver, Protocol Studio UI, Stage 4c replay. | ✅ Adopted |
| `ADR-038` | 2026-09-13 | **Contextual Life Events & Chart Superimposition (Schema v14)** — Event-sourced `data/records/events/`, `life_events` table, Stage 4d replay, Chart.js era bands & category pins, Workspace 4 History Curator. | ✅ Adopted |
| `ADR-039` | 2026-09-13 | **Companion App Protocol Dose Attestation & Offline Queue** — Compose 1-tap `DoseAttestationScreen.kt`, `GET /api/protocol/active`, retrospective/divergence logging, atomic `pending_doses/` queue. | ✅ Adopted |

---

## Historical Archive

- **Legacy Handover (v10.0, M1–M25):** [`docs/archive/HANDOVER_LEGACY.md`](archive/HANDOVER_LEGACY.md)
- **Milestone Slice Reports (`M1`–`M25`):** [`docs/milestones/`](milestones/)
- **Full Clinical Specs & ADR Bodies:** [`docs/DASHBOARD_SPECIFICATION.md`](DASHBOARD_SPECIFICATION.md) §6

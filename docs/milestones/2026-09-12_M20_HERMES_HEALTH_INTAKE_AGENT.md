# Milestone 20: Hermes Autonomous Health Intake Agent (ADR-034)

**Status:** ✅ Completed on branch `feat/hermes-health-agent` (2026-09-12)  
**Standard:** `AGENTS.md` Repository Protocols, ADR-028 (Event Sourcing), ADR-031 (Visual Telemetry), ADR-034 (Autonomous Gated Intake)  
**Design Reference:** `docs/HERMES_HEALTH_AGENT_DESIGN.md`  

---

## 1. Executive Summary & Goals

Milestone 20 transitions health scan reconciliation from manual / semi-automated scripts to an **autonomous, gated clinical intake pipeline** under the agreed **Hybrid Architecture**:
- **Dashboard-Native Health Intake Engine (`scripts/health_intake_agent.py`)**: Executes vision extraction, queries historical Personal Bests (PB) and morning scale weigh-ins, evaluates physiological plausibility, commits immutable Event Sourcing JSON files (`data/records/{egym,inbody}/`), and projects into SQLite.
- **Hermes Ambient Ambassador (`scripts/hermes_skills/health_intake.py`)**: Lightweight ambient skill running on the QNAP NAS (`/share/Hermes/agents/health_intake.py`) that receives incoming photos from WhatsApp/Telegram chat drops, passes them to the engine, and responds with human-friendly conversational summaries.
- **Provider-Level Billing Isolation & Token Accounting**: Uses dedicated `HEALTH_AGENT_GEMINI_KEY` (completely decoupled from Hermes general usage), recording every scan transaction with input/output tokens and estimated USD cost into `data/records/telemetry/agent_usage_ledger.jsonl`.
- **Gated Autonomy**: High-confidence, physiologically sound scans auto-ingest instantly (`reconciled_by: "agent_auto"`); anomalous or ambiguous scans pause (`staged_pending_review`) with explicit reasons for 1-click confirmation in the `#scans-review-modal` dashboard queue or conversational resolution via Hermes.

---

## 2. Key Architecture & Deliverables

### A. Dedicated Key Isolation & Non-Git Secrets Protocol
- Added `config/health_agent.env`, `config/health_agent_key.json`, and `config/*.env` to `.gitignore` and `scripts/deploy_to_nas.sh` rsync excludes.
- Provided tracked configuration template in `config/health_agent.env.example`.
- API key resolution cascade:
  1. `HEALTH_AGENT_GEMINI_KEY` in environment
  2. `config/health_agent.env`
  3. `config/health_agent_key.json`
  4. `.env` in repository root
  5. Fallback: `GEMINI_API_KEY` (with logged advisory on billing isolation)

### B. Dashboard-Native Intake Engine (`scripts/health_intake_agent.py`)
- **Multimodal Vision Extraction**: Prompt optimized against 86 gym companion app visual benchmarks. Extracts multi-set resistance loads (reps, load kg, mode, energy kcal, timestamp) and InBody body composition diagnostics (SMM, Phase Angle, impedance, visceral fat, segmental lean/fat breakdown).
- **Domain Plausibility Gates**:
  - **eGym Gate**: Queries historical Personal Best (PB) loads in `egym_workouts`. Enforces load boundaries ($\text{Load} \le \text{PB} \times 1.25$ and $\ge 5.0\text{ kg}$) and rep brackets ($4 \le \text{reps} \le 35$). Fuses intra-workout cardiac curves from `exercise_sessions` matching session date and time.
  - **InBody Gate**: Queries same-day morning fasted weigh-in from `withings_readings`. Computes diurnal offset $\Delta = \text{InBody\_Weight} - \text{Morning\_Weight}$, asserting $+0.2\text{ kg} \le \Delta \le +3.0\text{ kg}$. Enforces Phase Angle reference corridor ($5.5^\circ \le \text{Phase Angle} \le 7.5^\circ$, historical $6.1^\circ–6.8^\circ$) and tissue mass sanity.
- **Event Sourcing & CQRS (ADR-028)**:
  - Commits immutable JSON events to `data/records/egym/<date>_<machine>.json` and `data/records/inbody/<date>.json`.
  - Materializes projections into `egym_workouts` and `inbody_scans` SQLite fact tables.
  - Marks scan metadata as `reconciled` (`reconciled_by: "agent_auto"`).
- **Token & Cost Ledger**: Appends atomic JSON Lines records to `data/records/telemetry/agent_usage_ledger.jsonl`. Evaluates token costs using `gemini-2.5-flash` rate table ($0.10 / $0.40 per 1M tokens).

### C. Intake Channels (Lean Two-Door Model)
- **Door 1 (Companion App Mobile Intake)**: Android app "Snap & Send" uploads via `POST /api/upload_scan`. `scripts/sync_server.py` immediately spawns an asynchronous background thread invoking `health_intake_agent.process_scan`, completing extraction and reconciliation in 3–5 seconds.
- **Door 2 (Ambient Messaging Intake)**: Hermes Ambassador skill (`scripts/hermes_skills/health_intake.py`) processes direct photo uploads from WhatsApp or Telegram, returning conversational summaries and cost metrics.
- **Automated Watcher**: CLI `--watch` flag continuously monitors `data/records/scans/` every 5 seconds for unattended batch reconciliation.

---

## 3. Verification & Quality Gates

### Automated Test Suite (`scripts/test_health_intake_agent.py`)
```bash
python3 -m unittest scripts/test_health_intake_agent.py -v
```
- `test_api_key_resolution_priority`: PASS (Key resolution cascade)
- `test_cost_calculation`: PASS (Gemini pricing math)
- `test_telemetry_ledger_recording_and_summary`: PASS (Atomic ledger append & totals)
- `test_egym_plausibility_pass_and_fail`: PASS (PB jump, rep bounds & cardiac fusion)
- `test_inbody_plausibility_pass_and_fail`: PASS (Diurnal offset vs morning scale & Phase Angle)
- `test_gated_autonomy_auto_ingest_egym`: PASS (High confidence auto-commit to Event Sourcing + SQLite)
- `test_gated_autonomy_low_confidence_staged_pending_review`: PASS (Anomalous scan staged with proposal)
- `test_hermes_ambient_skill_replies`: PASS (Ambient conversational replies)

### Strict Unified Quality Gates
```bash
python3 scripts/run_quality_gates.py --strict
```
- **Gate 1 (Frontend JS Syntax)**: PASS (Validated 8 JS files cleanly with node -c)
- **Gate 2 (Offline HTML Data Hook Protection)**: PASS (Injected data tag intact)
- **Gate 3 (Ingestion Assertions A1–A4)**: PASS (Mathematical assertions verified)
- **Gate 4 (Scratch Migration & Idempotency)**: PASS (570 lab, 68 egym, 9 inbody, 4 bioage, 1 balance; uv=12)
- **Gate 5 (Live DB Write-Safety Guard)**: PASS (`data/health_dashboard.db` untouched)
- **Gate 6 (Exporter Byte-Determinism)**: PASS (100% byte-deterministic output)
- **Gate 7 (Working Tree Cleanliness)**: PASS (Working tree 100% clean upon commit)

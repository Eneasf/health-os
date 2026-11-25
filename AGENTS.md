# Agent Guidelines & Repository Protocols — Health OS (Preview / V1)

Personal health data operating system and clinical command center.

> **Size budget (`GADR-033` / `GADR-037`):** Keep root `AGENTS.md` `<= 10,000 B`. Full ADR narratives live in `docs/DASHBOARD_SPECIFICATION.md` §6 (indexed in `docs/DECISIONS.md`), open deferred items in `docs/BACKLOG.md`, and topography in `docs/REPO_MAP.md`.

---

## 1. Execution Contract & Git Hygiene

| Parameter | Value |
| :--- | :--- |
| **`PROJECT_TYPE`** | `fullstack-web-mobile` (Python pipeline + static JS dashboard + Kotlin Android companion) |
| **`QUALITY_GATE_CMD`** | `python3 scripts/run_quality_gates.py --strict` |
| **`DAEMON_RESTART_CMD`** | `./scripts/service_manager.sh restart && curl -s http://127.0.0.1:8765/api/status` |
| **`BACKLOG_SOURCE`** | `docs/BACKLOG.md` (delete-on-close) |
| **`MILESTONES_DIR`** | `docs/milestones/` |

1. **Branch Transparency & Scoping**: State `Active Branch: <branch>` in updates. Propose `feat/<name>` before architectural changes, schema migrations, or multi-file refactors.
2. **Atomic Commits & Clean Tree**: Use Conventional Commits (`feat`, `fix`, `docs`, `refactor`, `test`). Keep `git status` 100% clean at every commit/merge. Keep `.gitignore` blocking `data/health_dashboard.db`, runtime Withings token JSON files under `config/` (template at `config/withings_tokens.json.example`), and IDE/scratch artifacts.
3. **Byte-Deterministic Generated Artifacts (`ADR-013`)**: Scripts writing tracked files must produce byte-identical output from identical inputs — no wall-clock timestamps, unordered keys, or run-scoped IDs.
4. **Daemon Restart & Port Probe (`ADR-014` / `ADR-027`)**:
   - `scripts/sync_server.py` runs as a LaunchAgent and caches imports at startup. After changing any imported module, run `./scripts/service_manager.sh restart`, confirm start time postdates edits (`ps -o lstart= -p $(pgrep -f sync_server.py)`), and verify `curl -s http://127.0.0.1:8765/api/status`.
   - Before `scripts/rebuild_database.py --clean`, stop the daemon (`./scripts/service_manager.sh stop`) so `.db`/`.db-wal`/`.db-shm` release locks cleanly, then restart afterward (automated by the upstream NAS sync wrapper in the private production repository).
5. **Live Database Write Safety in Tests**: Never write to `data/health_dashboard.db`, `data/inbox/`, or `archive/` from tests. Monkeypatch `DB_PATH`, `INBOX_DIR`, `ARCHIVE_BASE_DIR`, and `LOCK_FILE_PATH` onto scratch copies and confirm production paths remain untouched.
6. **Event Sourcing & CQRS Invariant (`ADR-028`)**:
   - Immutable raw files (`data/records/<domain>/`, `archive/`) are the **sole system of record**; SQLite is strictly a disposable read projection. Direct-to-SQL mutations without a raw event write are prohibited.
   - Every writable domain must implement a deterministic replay stage in `scripts/rebuild_database.py --clean`.
7. **Review & Merge Gate**: Run `python3 scripts/run_quality_gates.py --strict` cleanly before proposing commits or merges into `main`.

---

## 2. Documentation & Audit Governance (`GADR-033`)

1. **Decisions (`docs/DECISIONS.md` & `docs/DASHBOARD_SPECIFICATION.md` §6)**: Log full technical rationale in `docs/DASHBOARD_SPECIFICATION.md` §6 and add a 1-line index entry to `docs/DECISIONS.md`.
2. **Backlog (`docs/BACKLOG.md`)**: Track known, actionable open/deferred items only; delete rows on close. `HANDOVER.md` is retired (archived at `docs/archive/HANDOVER_LEGACY.md`).
3. **Milestones (`docs/milestones/`)**: Durable narrative of completed work lives in milestone slice documents. Delete or archive branch-scoped plan files on merge.
4. **Offline HTML Hook Protection**: Never remove or break `<script id="injected-dashboard-data">` in `dashboard/index.html` (contract for `scripts/export_dashboard_data.py` and offline `file://` execution).
5. **External Audits Adjudicated First**: Verify external review findings claim-by-claim against code in `docs/audits/` before implementing any fix.
6. **Context Boundaries**: After finalizing and committing a major milestone, stop and prompt the user for a fresh session rather than starting another multi-file milestone in a stale thread.

---

## 3. Core Safety & Medical Boundaries (Non-Negotiable)

1. **No Prescribing**: Surface objective telemetry against protocol rules and prompt for confirmation; **never prescribe or recommend medication dose changes**.
2. **Coverage Denominators**: Every clinical aggregate must display its backing sample size and completeness denominator (e.g., *"from 33 of 89 complete days"*).
3. **Coverage Gating**: Sparse modules (e.g., sleep `< 4 of 7` nights) must render an actionable banner rather than interpolating across missing data.
4. **No Fabricated Clinical Values (`ADR-016`)**: Every displayed value derives from logged data or renders as explicit `null` / `unlogged` — zero placeholder or baseline schedules in exporter or JS. Absence never reads as compliance.
5. **Laboratory & Biomarker Invariants (`ADR-022` / `ADR-023`)**:
   - Preserve original reporting lab reference intervals and units alongside canonical conversions.
   - Store non-numeric comparators (e.g. `<0.3`) as separate operator + numeric value; never coerce blindly to float.
   - Plot bloodwork strictly as discrete points with sample dates; never draw continuous interpolated curves across multi-month gaps.

---

## 4. Android Companion & Health SDK Protocols

1. **Live Device Logs First**: Check `adb logcat -d -b crash` and `adb logcat -d | grep -i "healthdashboard"` before speculating on empty reader output.
2. **Local AAR Transitive Dependencies**: Flat `.aar` files in `app/libs/` do not resolve Maven dependencies automatically; declare runtime deps (e.g. `com.google.code.gson:gson`) explicitly in `build.gradle.kts`.
3. **Multi-Year Backfill Invariants**: Keep `android:largeHeap="true"`, chunk high-frequency streams (Heart Rate) into **max 7-day IPC slices** (1 MB Binder ceiling), and query aggregates (`STEPS`, `ACTIVITY_SUMMARY`) via `store.aggregateData()`.
4. **Two-Sided Envelope SHA-256 Contract (`ADR-017`)**:
   - Android hashes the compact `"records"` JSON (`Json { encodeDefaults = true }` in `HealthDataCollector.kt`) and serializes the envelope with the same instance.
   - `sync_server.py` persists POST bodies to `data/inbox/` **verbatim** (no re-serialization or pretty-printing). Never change one side without the other; re-verify against `archive/samsung_health_sdk/`.
5. **Server Port & App Endpoint Contract**: Mac daemon port (`8765`) and companion app stored URL drift independently. Any port change requires updating both sides and confirming via the post-restart `/api/status` probe.

---

## 5. Subagent Delegation & Fleet Ingress

1. **Disjoint Scopes & Pre-Flight Disclosure (`ADR-024`)**: Parallel subagents must never touch overlapping source files or test DB paths. State Role, Model, Disjoint Scope, and Rationale before delegating; follow global routing defaults (`GADR-034`).
2. **Sovereign Fleet & Edge Ingress**: Cataloged in Nodes Hub (`faleiros.app`). Persistent host deployments (OCI/QNAP), port allocations, and tunnels follow `fleet-node-ops` (`fleet status` / `fleet env <app>`).

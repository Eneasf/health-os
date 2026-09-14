# Agent Guidelines & Repository Protocols

This repository is a personal health data operating system and clinical command center.

> **Size budget:** this file is injected into agent context every turn by some tools — keep it under **~150 lines / ~4k tokens**. When a new rule would push past that, its incident detail moves to the ADR log (`docs/DASHBOARD_SPECIFICATION.md` §6) with a one-line pointer here. Rules stay; stories compress first.

---

## 1. Git Workflow & Hygiene Standards

All AI agents working on this codebase must strictly observe the following git lifecycle rules:

1. **Active Branch Transparency**:
   - Always state the currently active branch in conversation updates (e.g. `Active Branch: <branch-name>`).

2. **Branch Creation & Scoping**:
   - **Explicit User Request**: If the user requests a new branch for a material change, create it immediately.
   - **Proactive Proposal**: If a requested task involves significant architectural changes, new subsystems, schema migrations, or multi-file refactoring, proactively propose creating a dedicated feature branch (`feat/<feature-name>`) before writing code.

3. **Commit Cadence & Cleanliness**:
   - Create focused, atomic commits using the Conventional Commits format (`feat(...)`, `fix(...)`, `docs(...)`, `style(...)`, `refactor(...)`, `test(...)`).
   - Keep the working tree 100% clean (`git status`) **at every commit and merge point**. Never leave untracked scratch files, temporary logs, or unignored IDE configurations (`.idea/`, `.gradle/`, `*.iml`). (Work-in-progress files the owner is actively staging are exempt until their branch lands.)
   - Maintain `.gitignore` to prevent database derived artifacts (`data/health_dashboard.db`), credential secrets (`config/withings_tokens.json`), and temporary extraction dumps from entering git history.
   - **Generated artefacts must be byte-deterministic.** Any script that writes a tracked file must produce an identical file from identical inputs — no wall-clock stamps, no unordered keys, no run-scoped ids. Non-determinism puts §1.3 (clean tree) in direct conflict with §1.5 (run the exporter before merging) and produces content-free "refresh timestamp" commits. Settled in ADR-013; do not reintroduce a run-time stamp.

4. **Long-Running Services Must Be Restarted After Code Changes**:
   - `scripts/sync_server.py` runs as a LaunchAgent daemon and **imports `ingest_sdk_payload` once at start**. Python caches imported modules, so a running daemon keeps executing the code as it was when the process launched — edits on disk have no effect until restart.
   - **Restart the daemon after changing any module it imports**, and state that you have done so:
     ```
     ./scripts/service_manager.sh restart
     ```
   - This is not theoretical. On 2026-08-28 the daemon was started at 12:13, ADR-014 merged at 15:31, and syncs at 15:43 and 15:49 were handled by the pre-fix ingester — producing duplicate rows that looked like a failure of the new code.
   - Before drawing any conclusion from ingested data, confirm the daemon's start time postdates the last change to its imports (`ps -o lstart= -p $(pgrep -f sync_server.py)`).
   - After a restart, confirm the server answers on the **expected port** (`curl -s http://127.0.0.1:8765/api/status`). A restart that "succeeds" onto a stale or wrong port passes silently otherwise — the 2026-08-29 remediation merge changed the default from 8080 to 8765, and only the post-restart probe surfaced which code was actually serving.
   - During full SQLite fact store rebuilds (`scripts/rebuild_database.py --clean`), pause the local daemon (`./scripts/service_manager.sh stop`) to release locks on WAL shared memory (`.db-shm`), purge all temporary artifacts (`.db*`), and restart after (ADR-027). `scripts/sync_from_nas.sh` manages this automatically.

5. **Review & Merge Protocol**:
   - Explicitly notify the user when a logical milestone is complete and ready to commit or merge into `main`.
   - Verify all quality gates pass cleanly (`python3 scripts/run_quality_gates.py --strict`) before proposing a merge.

6. **Live Database Write Safety in Testing**:
   - **Never write to `data/health_dashboard.db` from a test.** Any test that exercises a write path (ingestion, migration, rebuild) runs against a scratch **copy** of the DB, with `DB_PATH` — and, for ingestion tests, `INBOX_DIR`/`ARCHIVE_BASE_DIR`/`LOCK_FILE_PATH` — monkeypatched on the imported module. Never edit the path constants in the source to point at test locations.
   - Before reporting a write-path task done, confirm the real DB, `data/inbox/`, and `archive/` are untouched (mtime or content hash unchanged), and say so.

7. **Event Sourcing & CQRS Invariant (ADR-028)**:
   - Any new functionality saving or retrieving clinical, behavioral, or protocol data **must adhere to Event Sourcing with CQRS**.
   - The raw, append-only files (`data/records/<domain>/`, `archive/`) are the **sole system of record**; SQLite is strictly a disposable read projection. Direct-to-SQL mutations without an immutable raw event store are strictly prohibited.
   - Every writable domain must implement an explicit replay stage in `scripts/rebuild_database.py` so running a `--clean` rebuild regenerates 100% of the state from raw files alone.

---

## 2. Documentation & Handover Integrity Protocol

To maintain complete cross-agent coherence across long-running sessions, agents must observe the following documentation rules at the end of each milestone:

1. **Architectural Decisions (`docs/DASHBOARD_SPECIFICATION.md`)**:
   - Every settled UI convention, clinical rule, or data contract change must be formally recorded in the **Architectural Decision Record (ADR)** table in §6.
2. **Repository Handover (`docs/HANDOVER.md`)**:
   - Record the completed milestone in the **Milestone Index** (§3), backed by a dedicated slice document in `docs/milestones/` containing test commands, schema versions, and verification details.
   - Mirror any newly adopted ADRs into the **Master Decision Index** (§2) as one-line summaries — the full rationale lives only in the spec's ADR log (§2.1 above), never duplicated.
3. **Open Work Register (`docs/HANDOVER.md` §6)**:
   - Record work that is **known and deliberately not done** — upstream dependencies, deferred features, blocked items — in the **Open Work & Deferred Items** section. Anything closed there must move to the ADR table or the Milestone Index.
   - Each entry must be **actionable without the originating session's context**: the evidence, the required change, and how to verify it is done.
   - This exists because §2.1–2.2 only capture *completed* work. ADR-012's upstream requirement (the Android collector must send `Metadata.id`) initially lived only inside an ADR sentence and a runtime warning, which is unfindable to an agent picking up the Android stream separately.
   - Where a fix is a **workaround rather than a resolution**, say so and state what it costs. Recording the workaround as though it were the fix is how a known gap becomes an invisible one.

4. **Milestone Slice Documents Are the Record**:
   - The durable narrative of completed work lives in `docs/milestones/` slice documents, not in root-level plan files. Branch-scoped planning files, if a session uses them at all, must be **deleted or archived when the branch merges** — a stale "active roadmap" describing merged work misleads the next agent worse than no roadmap. (The former root-level `implementation_plan.md`/`walkthrough.md` went stale exactly this way and are archived under `docs/archive/`.)
5. **Offline HTML Data Hook Protection**:
   - Never remove or break the `<script id="injected-dashboard-data">` tag in `dashboard/index.html`, which is the contract for `scripts/export_dashboard_data.py` and offline `file://` execution.
6. **External Reviews & Audits Are Adjudicated Before Implementation**:
   - Findings from external review documents (other AI models' audits included) are never implemented directly. They are first verified claim-by-claim against the code and recorded in `docs/audits/` with per-claim verdicts; fixes are implemented only from the adjudicated document. Raw audits contain refuted claims and wrong fixes (see `docs/audits/2026-08-29_AUDIT_ADJUDICATION.md` and `docs/audits/2026-09-05_LAB_INGESTION_ADJUDICATION.md`).
7. **Proactive Context Boundary & Milestone Handover**:
   - Agents must actively monitor session context length and compaction events.
   - When a logical milestone is completed and merged (or after a context compaction checkpoint has fired), the agent must **never embark on a major new multi-file milestone in the same thread**.
   - The agent is responsible for calling the boundary: finalize the current milestone, commit with a 100% clean working tree, update handover docs, write the upcoming milestone's implementation plan, and **proactively instruct the user to start a fresh session** to prevent prompt drift and context degradation.

---

## 3. Core Safety & Medical Boundaries

1. **Safety Boundary**: The dashboard evaluates and surfaces objective telemetry against protocol rules, prompting for subjective confirmation; **it never prescribes or recommends medication dose changes**.
2. **First-Class Coverage Denominators**: Every clinical calculation and aggregate must display its backing sample size and data completeness denominator (e.g., *"from 33 of 89 complete days"*).
3. **Coverage Gating**: Sparse modules (such as sleep with <4 of 7 nights) must display an actionable behavioral banner rather than interpolating lines through missing data.
4. **No Fabricated Clinical Values**: Every displayed clinical value derives from logged data or renders as an explicit `null` / `unlogged` state. No placeholder, demonstration, or "baseline schedule" values — in the exporter **or** in dashboard JS. Fabrication has appeared in both layers at once (an exporter "demonstration schedule" and a duplicated client-side copy in `matrix.js` rendered unlogged doses as taken until 2026-08-29). Settled in ADR-016; absence must never read as compliance.
5. **Laboratory Data & Biomarker Invariants**:
   - Lab records must preserve the reporting lab's original reference intervals and units alongside canonical conversions.
   - Non-numeric comparator results (e.g. `<0.3`) must store operator and numerical value separately, never coerced to float.
   - Bloodwork displays must never draw interpolated continuous curves across multi-month testing gaps; all plots must be discrete data points with backing sample dates and denominators.

---

## 4. Android Companion & Health SDK Protocols

1. **Live Device Diagnostics First**:
   - Never speculate when reader data returns empty or fails. Check on-device runtime logs directly:
     ```bash
     adb logcat -d -b crash
     adb logcat -d | grep -i "healthdashboard"
     ```

2. **Local AAR Transitive Dependencies**:
   - Flat `.aar` files in `app/libs/` do not resolve Maven dependencies automatically. Always declare SDK runtime dependencies (e.g., `com.google.code.gson:gson`) in `build.gradle.kts` to prevent silent `NoClassDefFoundError` during IPC Binder calls.

3. **Multi-Year Backfill Invariants**:
   - Keep `android:largeHeap="true"` in `AndroidManifest.xml`.
   - Chunk high-frequency streams (Heart Rate) into **max 7-day IPC slices** to prevent Android 1 MB Binder buffer and heap exhaustion (`OutOfMemoryError`).
   - Query aggregate types (`STEPS`, `ACTIVITY_SUMMARY`) via `store.aggregateData()`, not raw `readData()`.

4. **Envelope Hash Contract (ADR-017) — Two Sides, One Invariant**:
   - SHA-256 validation is **enforcing**: the Mac rejects payloads whose `content_sha256` does not match a rehash of the exact `"records": [...]` byte span in the stored envelope. That works only because of two invariants that must change **together or not at all**:
     - Android hashes the **compact** serialization of the record list — `Json { encodeDefaults = true }` in `HealthDataCollector.kt` — before embedding it in the envelope, and serializes the envelope with the same `Json` instance.
     - `sync_server.py` persists the POST body to `data/inbox/` **verbatim** — no re-serialization, no pretty-printing, no normalization.
   - Changing either side alone (a serializer flag, a prettified write) silently breaks all ingestion with SHA-mismatch rejections. Re-prove against archived payloads in `archive/samsung_health_sdk/` after touching either side.

5. **Server Port & App Endpoint Are One Contract**:
   - The sync server's port (currently **8765**) and the companion app's stored Mac endpoint drift independently — the daemon binds whatever the code says on restart, while the app keeps its saved URL. Any change to the server port must state that the app endpoint needs the same change, and the post-restart probe (§1.4) confirms which port is actually serving. Failed pushes are survivable (payload saved locally, change tokens not advanced) but sync silently stops until the endpoint matches.

---

## 5. Subagent Delegation, Model Tiers & Parallelism Protocols (ADR-024)

1. **Parallel Execution & Disjoint Scopes**:
   - Autonomous parallel specialist subagents are explicitly authorized and encouraged for disjoint tasks.
   - **Zero Collision Invariant**: Parallel subagents must never write to overlapping source files or test DB paths concurrently.

2. **3-Tier Model Allocation Standard**:
   - **Tier 1 (Mechanical Execution)**: `Model: "flash_lite"` (Gemini Flash Lite). Zero-ambiguity execution: spreadsheet patching, raw log grepping, JSON formatting, deterministic unit tests. **1-Strike Failsafe**: On any tool error or loop, terminate immediately and escalate to Tier 2.
   - **Tier 2 (Standard Engineering)**: `Model: "flash"` (Gemini 3.8 Flash platform default). Standard for code authoring, migrations, rebuild scripts, SQL spikes, and data pipelines.
   - **Tier 3 (Complex Architecture & UI)**: `Model: "inherit"` (Gemini 3.8 Flash High) or `Model: "pro"`. For multi-tab frontend state managers, layout engines, SVG charting math, and clinical protocol rules.

3. **Mandatory Pre-Flight Disclosure**:
   - Every subagent invocation MUST be preceded by visible text output in chat reporting: Agent Role, Model Parameter (`flash_lite` | `flash` | `inherit` | `pro`), Reasoning Level, Disjoint Scope, and Task Rationale. Spawning subagents silently or retroactively is strictly prohibited.

4. **Mandatory Planning Topology Section**:
   - Every `implementation_plan.md` must include an explicit `## Agent Topology & Delegation Strategy` section defining whether multiagents are used, why/why not (e.g. single-file coupling vs disjoint write scopes), and model tier assignments.


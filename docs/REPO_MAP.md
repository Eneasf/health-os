# Repository Map — Health OS (Preview / V1)

System layout and verified pipeline baseline extracted from `docs/archive/HANDOVER_LEGACY.md` under `GADR-033`.

## Directory Structure

```text
├── AGENTS.md                          # Lean repository contract (<= 10 KB)
├── index.html                         # Root static preview entry
├── data/health_dashboard.db           # Disposable SQLite read projection (gitignored; Schema v14)
├── dashboard/                         # Clinical web application
│   ├── index.html                     # UI shell & <script id="injected-dashboard-data"> hook
│   └── js/                            # theme, matrix, charts, widgets, bloodwork, protocol_studio, life_events, app
├── js/                                # Root static preview JS mirror
├── android/                           # Native Kotlin/Compose companion app (Samsung Health Data SDK 1.1.0)
├── protocols/                         # Protocol configuration assets
├── config/                            # Runtime config templates (tokens gitignored)
├── scripts/                           # Mac/NAS ingestion, rebuild, aggregation, mock demo & quality gates
│   ├── service_manager.sh             # LaunchAgent daemon controller (Mac :8765)
│   ├── sync_server.py                 # HTTP sync receiver (Mac :8765 / NAS :8088)
│   ├── rebuild_database.py            # Deterministic CQRS replay from raw event files (--clean)
│   ├── export_dashboard_data.py       # Byte-deterministic JSON + HTML data injector (ADR-013)
│   ├── aggregation_engine.py          # Unified temporal aggregation & nocturnal HR recovery
│   ├── generate_mock_dashboard.py     # Synthetic demo data & preview generator ("Alex Rivers")
│   ├── capture_dashboard_screenshots.py # Automated showcase screenshot capture utility
│   └── run_quality_gates.py           # Strict 7-gate verification runner (--strict)
└── docs/
    ├── DECISIONS.md                   # Canonical ADR index (ADR-001..ADR-039)
    ├── BACKLOG.md                     # Delete-on-close open work register
    ├── REPO_MAP.md                    # This file
    ├── DASHBOARD_SPECIFICATION.md     # Authoritative clinical UI rules & full ADR log (§6)
    ├── SYSTEM_DESIGN.md               # Core system design & pipeline architecture
    ├── SAMSUNG_INGESTION.md           # Ingestion envelope & deduplication spec
    ├── milestones/                    # Permanent M1–M25 verification slice records
    └── archive/HANDOVER_LEGACY.md     # Archived legacy handover (v10.0)
```

## Pipeline & Verification Baseline

- **Schema Version:** `PRAGMA user_version = 14` (SQLite projection rebuilt deterministically from `data/records/` + `archive/`).
- **Quality Gate Command:** `python3 scripts/run_quality_gates.py --strict` (7 gates, byte-identical export per `ADR-013`).
- **Local Daemon:** `./scripts/service_manager.sh restart` → verify `curl -s http://127.0.0.1:8765/api/status`.
- **NAS Production Topology (`ADR-018`):** QNAP Container Station over Tailscale (`health-dashboard.local:8088`); in the upstream private repository, dedicated NAS deployment and archive-pull scripts synchronize raw event archives without ever transferring `.db` binaries.

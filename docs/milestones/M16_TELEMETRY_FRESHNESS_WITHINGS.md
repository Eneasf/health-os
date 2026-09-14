# Milestone 16: Telemetry Freshness & Withings Ingestion Hardening (ADR-029)

**Status:** ✅ Completed on branch `feat/m16-telemetry-freshness-withings` (2026-09-07)  
**Standard:** `vdone` Acceptance Criteria (V0–V5 in `VDONE.md`) & `AGENTS.md`  

---

## 1. Architectural Directive & Objectives

Milestone 16 addresses real-time telemetry observation and Withings ingestion resilience across local development and 24/7 NAS production environments (ADR-018):
1. **Telemetry Freshness Decoupling**: Historically, the Command Strip HUD displayed a single timestamp (`• Data current through <time>`) anchored exclusively to the latest physical biometric fact in SQLite (ADR-013). This created an ambiguity: an idle watch left on a charger (<4h sync transmission, older heart rate sample) looked identical to a completely stalled sync daemon (>24h without contact).
2. **Withings 503 & Transient Resilience**: The Withings public API occasionally returns 503 Service Unavailable, 502 Bad Gateway, 504 Gateway Timeout, or 429 Rate Limit. In naive implementations, HTTP errors trigger token refresh attempts, which burn single-use rotating refresh tokens and permanently revoke authentication.
3. **NAS Token Authority (ADR-018)**: Withings tokens reside authoritatively on the NAS container (`/app/config/withings_tokens.json`). Local development runs must not attempt independent token refreshes that desynchronize production credentials.
4. **Live Client Telemetry & Non-Blocking Feedback**: Replace invasive browser `alert()` dialogs with non-blocking toast alerts, elevate the Withings scale button with live visual states, and implement lightweight background polling against `/api/status` with visibility guards.

---

## 2. Key Accomplishments & Technical Implementations

### A. Dual Telemetry Freshness Architecture (ADR-029)
- **Conceptual Decoupling**:
  - **Sensor Freshness (`sensor_current_through`)**: Timestamp of the latest biometric measurement fact stored in SQLite (e.g. `2026-09-05T06:43:40+00:00`).
  - **Sync Freshness (`sync_freshness`)**: Timestamp of the newest contact/payload transmission from the Android companion app (`last_sdk_sync`) or Withings scale (`last_scale_sync`).
- **Command Strip HUD Badge**:
  - Upgraded `#telemetry-freshness-badge` with `#freshness-status-dot`, `#generated-timestamp`, and `#freshness-sync-chip`.
  - **3-Tier Freshness Dot**:
    - `< 4h` contact age: `bg-emerald-500 animate-pulse` (Live / Fresh).
    - `4h – 12h` contact age: `bg-amber-400` (Stale / Waiting).
    - `> 12h` contact age: `bg-rose-500 animate-pulse` (Stalled / Check Device).
  - **Hover Tooltip**: Surfaces full audit breakdown: Sensor currency, companion sync timestamp and device model, scale sync timestamp, and token authority.

### B. Withings Resiliency & Ingestion Hardening (`scripts/sync_withings.py`)
- **Transient Error Classification**: Added `WithingsTransientError` for HTTP 503, 502, 504, and 429 status codes, with network request timeout set to 15 seconds.
- **Exponential Retry Backoff**: Retries transient failures up to 3 attempts with exponential delay (`1.5 * (attempt + 1)`), without exhausting retry attempts on unrecoverable errors.
- **Token Refresh Isolation**: Transient errors explicitly bypass `refresh_access_token()`. Token refreshes fire only on genuine HTTP 401 Unauthorized errors.
- **Permanent Revocation Handling**: When Withings rejects a refresh token (`invalid refresh_token` / `invalid_grant`), the error is captured cleanly as `auth_revoked` without crashing the daemon.
- **Atomic Token Persistence**: `save_tokens()` writes atomically via temporary file rename (`.tmp` to `.json`), preventing corrupted token state during unexpected process termination.
- **Last Success Preservation**: Sync errors preserve the previously successful sync timestamp (`last_success`) in `withings_sync_status.json`.
- **NAS Token Authority Guard**: `get_token_authority()` detects whether the process is running on the NAS container or local dev machine. Local dev runs cleanly report NAS authority and avoid token collisions.

### C. Sync Server Status & Proxy Endpoints (`scripts/sync_server.py`)
- **Modular Status Payload (`build_status_payload`)**:
  - Refactored `GET /api/status` into a modular helper returning `telemetry_freshness` containing both `sensor_freshness` and `sync_freshness` with contact age calculations.
- **Reverse Proxying for Local Dev (`POST /api/sync/withings`)**:
  - When invoked in local dev environments where tokens reside on the NAS, `sync_server.py` proxies the sync trigger directly to the NAS production container (`http://health-dashboard.local:8088/api/sync/withings`), forwarding status codes (200, 503, 401) and response payloads cleanly.

### D. Frontend Background Polling & Non-Blocking Toasts (`dashboard/js/app.js`)
- **Non-Blocking Toast System (`showToast`)**:
  - Attached to `<div id="toast-container">` in `dashboard/index.html`.
  - Supports `success` (emerald), `error` (rose), `warning` (amber), and `info` (slate/cyan) toasts with slide-in animation and auto-dismissal.
  - Replaced all modal `alert()` popups in Withings sync routines.
- **Dynamic Withings Button States (`updateWithingsStatusUI`)**:
  - `Scale Synced` (emerald dot): Displays record count and sync time.
  - `NAS Scale Hub` (cyan dot): Indicates NAS authoritative token management (ADR-018).
  - `Scale (503)` (amber dot): Indicates upstream Withings service maintenance.
  - `Auth Expired` (amber pulsing dot): Prompts for re-authorization on NAS portal.
  - `Scale Error` (rose pulsing dot): Indicates unhandled ingestion error with tooltip diagnostics.
- **Cache-Busting Live Data Loading (`loadData(forceFetch)`)**:
  - In HTTP environments or when `forceFetch` is true, fetches `/dashboard_data.json?_ts=<now>` to guarantee live updates, cleanly falling back to embedded `window.__DASHBOARD_DATA__` in offline or `file://` mode.
- **Background Telemetry Polling (`startTelemetryPolling`)**:
  - Polls `/api/status` every 3 minutes (180,000 ms).
  - **Tab Visibility Guard**: Checks `document.visibilityState`; active polling pauses when tab is hidden and polls immediately upon tab reactivation if interval has elapsed.
  - Automatically calls `loadData(true)` and refreshes dashboard components when newer telemetry is available on the server.

---

## 3. Verification & Acceptance Gates (VDONE.md)

| Gate | Target | Command | Result |
|---|---|---|---|
| **V0** | Module Imports & Attributes | `python3 -c "import sys; sys.path.insert(0, 'scripts'); import sync_withings; assert hasattr(sync_withings, 'run_sync')"` | ✅ PASS |
| **V1** | Withings Resilience Scenarios | `python3 scripts/test_withings_resilience.py` | ✅ PASS (5/5 tests in 0.020s) |
| **V2** | Server Dual Freshness Contract | `python3 -c "import sys; sys.path.insert(0, 'scripts'); import sync_server; res = sync_server.build_status_payload(); assert 'telemetry_freshness' in res"` | ✅ PASS |
| **V3** | Frontend JavaScript Syntax | `node -c dashboard/js/*.js` | ✅ PASS (8 JS files validated) |
| **V4** | Exporter Determinism & Invariant Safety | `python3 scripts/export_dashboard_data.py` | ✅ PASS (`[SKIP] unchanged; no write needed`) |
| **V5** | Strict Unified Quality Gates | `python3 scripts/run_quality_gates.py` | ✅ PASS (All operational gates passed) |

# Milestone 3: Mac-Side Ingestion Pipeline & SDK Payload Drainer (Phase 3)

**Status:** ✅ Completed & Merged into main (2026-08-28)
**Scope:** `scripts/ingest_sdk_payload.py`, Ingest Inbox Drainer, Tier 1 SHA-256 Validation, Tier 2 Token Chaining, Idempotent SQLite UPSERTs, Daily Summary Rematerialization, Payload Archival

---

## 1. Architectural Objectives
1. **Inbox Polling & Ingestion Drainer (`data/inbox/`)**:
   - Continuously poll and drain JSON companion app payloads dropped via USB, WiFi sync server, or manual drop.
2. **Cryptographic & Chaining Integrity**:
   - Enforce Tier 1 SHA-256 checksum verification against payload header hash.
   - Enforce Tier 2 change token monotonicity chaining (`sdk_sync_state`) to prevent lost deltas or duplicate replays.
3. **Idempotent 25-Type SQLite UPSERT Engine**:
   - Ingest data into dedicated tables: `sleep_sessions`, `heart_rate_records`, `heart_rate_samples`, `food_log_items`, `daily_nutrition`, `exercise_sessions`, `clinical_vitals`, `activity_and_goals`.
4. **Daily Summary Rematerialization & Archiving**:
   - Recalculate daily summary metrics upon ingestion.
   - Archive processed payloads to `archive/samsung_health_sdk/{YYYY-MM}/`.

---

## 2. Key Files & Components
- `scripts/ingest_sdk_payload.py` — Core ingestion and drainer engine.
- `schema.sql` — Table definitions and index structures for SDK tables.
- `data/inbox/` — Ingestion drop folder (gitignored).
- `archive/samsung_health_sdk/` — Processed payload archive.

---

## 3. Verification & Metrics
- Synthetic end-to-end payload ingestion verified with zero errors.
- Token chaining verified across multiple incremental payload syncs.

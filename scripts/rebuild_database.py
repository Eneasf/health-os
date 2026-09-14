#!/usr/bin/env python3
"""
Rebuild Database Engine for Health Dashboard.
Reconstructs the derived SQLite fact store (data/health_dashboard.db) from authoritative raw archives:
1. Withings, Samsung Base & SDK tables initialization
2. Samsung Health ZIP export(s) in archive/samsung_health/
3. Schema migrations v5, v6, v7, v8 & interventions catalog seed
4. Incremental Samsung Health SDK JSON payloads in archive/samsung_health_sdk/
5. Withings scale daily measurement records in data/records/withings/
6. Full daily_summary materialization and dashboard JSON export.

100% Python standard library.
"""

import os
import sys
import glob
import json
import sqlite3
import argparse
from pathlib import Path

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCRIPTS_DIR = os.path.join(BASE_DIR, "scripts")
DEFAULT_DB_PATH = os.path.join(BASE_DIR, "data", "health_dashboard.db")
SAMSUNG_ARCHIVE_DIR = os.path.join(BASE_DIR, "archive", "samsung_health")
SDK_ARCHIVE_DIR = os.path.join(BASE_DIR, "archive", "samsung_health_sdk")
WITHINGS_RECORDS_DIR = os.path.join(BASE_DIR, "data", "records", "withings")
INTERVENTIONS_RECORDS_DIR = os.path.join(BASE_DIR, "data", "records", "interventions")
EGYM_RECORDS_DIR = os.path.join(BASE_DIR, "data", "records", "egym")
INBODY_RECORDS_DIR = os.path.join(BASE_DIR, "data", "records", "inbody")
PROTOCOLS_RECORDS_DIR = os.path.join(BASE_DIR, "data", "records", "protocols")
EVENTS_RECORDS_DIR = os.path.join(BASE_DIR, "data", "records", "events")

sys.path.insert(0, SCRIPTS_DIR)
import ingest_samsung_health
import ingest_sdk_payload
import export_dashboard_data
import migrate_v5
import migrate_v6
import migrate_v7
import migrate_v8
import migrate_v9
import migrate_v10
import migrate_v11
import migrate_v12
import migrate_v13
import migrate_v14
import aggregation_engine
import seed_interventions


def init_withings_table(conn: sqlite3.Connection):
    cur = conn.cursor()
    cur.execute("""
        CREATE TABLE IF NOT EXISTS withings_readings (
            id TEXT PRIMARY KEY,
            user_id TEXT,
            user_name TEXT,
            timestamp DATETIME NOT NULL,
            date TEXT NOT NULL,
            weight_kg REAL,
            fat_free_mass_kg REAL,
            fat_ratio_pct REAL,
            fat_mass_weight_kg REAL,
            muscle_mass_kg REAL,
            hydration_tbw_kg REAL,
            bone_mass_kg REAL,
            pulse_wave_velocity_ms REAL,
            heart_rate_bpm REAL,
            visceral_fat_index REAL,
            source TEXT DEFAULT 'withings_api',
            raw_json TEXT,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
        )
    """)
    cur.execute("CREATE INDEX IF NOT EXISTS idx_withings_date ON withings_readings(date)")
    conn.commit()


def rebuild_database(target_db_path: str, clean: bool = False):
    print("=" * 70)
    print("🏗️  Health Dashboard Fact Store Rebuild Engine")
    print(f"🎯 Target Database: {target_db_path}")
    print("=" * 70)

    if clean:
        for suffix in ["", "-wal", "-shm", "-journal"]:
            p = target_db_path + suffix
            if os.path.exists(p):
                print(f"[*] Removing existing target database artifact: {p}")
                os.remove(p)

    os.makedirs(os.path.dirname(os.path.abspath(target_db_path)), exist_ok=True)
    conn = sqlite3.connect(target_db_path)
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA busy_timeout=5000")

    # -------------------------------------------------------------------------
    # STAGE 1: Tables Initialization & Base Samsung Health ZIP Ingestion
    # -------------------------------------------------------------------------
    print("\n📦 STAGE 1: Initializing tables & ingesting Samsung Health ZIP export(s)...")
    init_withings_table(conn)
    ingest_samsung_health.init_samsung_tables(conn)
    ingest_sdk_payload.init_sdk_tables(conn)

    zip_files = sorted(glob.glob(os.path.join(SAMSUNG_ARCHIVE_DIR, "**", "*.zip"), recursive=True))
    if not zip_files:
        print(f"  ⚠️  No Samsung Health ZIP exports found in {SAMSUNG_ARCHIVE_DIR}")
    else:
        for zpath in zip_files:
            print(f"  -> Processing ZIP: {os.path.basename(zpath)}")
            ingest_samsung_health.process_zip(zpath, conn)

    conn.commit()

    # -------------------------------------------------------------------------
    # STAGE 2: Schema Migrations & Interventions Catalog Seeding
    # -------------------------------------------------------------------------
    print("\n⚙️  STAGE 2: Applying schema migrations & seeds...")
    
    orig_v5_path = migrate_v5.DB_PATH
    orig_v6_path = migrate_v6.DB_PATH
    orig_v7_path = migrate_v7.DB_PATH
    orig_v8_path = migrate_v8.DB_PATH
    orig_v9_path = migrate_v9.DB_PATH
    orig_v10_path = migrate_v10.DB_PATH
    orig_v10_egym = migrate_v10.EGYM_RECORDS_DIR
    orig_v10_inbody = migrate_v10.INBODY_RECORDS_DIR
    orig_v11_path = migrate_v11.DB_PATH
    orig_v11_egym = migrate_v11.EGYM_RECORDS_DIR
    orig_v11_inbody = migrate_v11.INBODY_RECORDS_DIR
    orig_v12_path = migrate_v12.DB_PATH
    orig_v13_path = migrate_v13.DB_PATH
    orig_v13_protocols = migrate_v13.PROTOCOLS_DIR
    orig_v14_path = migrate_v14.DB_PATH
    orig_v14_events = migrate_v14.EVENTS_DIR
    orig_seed_path = seed_interventions.DB_PATH

    try:
        migrate_v5.DB_PATH = target_db_path
        migrate_v5.BACKUP_PATH = target_db_path + ".bak"
        migrate_v6.DB_PATH = target_db_path
        migrate_v6.BAK_PATH = target_db_path + ".bak"
        migrate_v7.DB_PATH = target_db_path
        migrate_v8.DB_PATH = target_db_path
        migrate_v9.DB_PATH = target_db_path
        migrate_v10.DB_PATH = target_db_path
        migrate_v10.EGYM_RECORDS_DIR = EGYM_RECORDS_DIR
        migrate_v10.INBODY_RECORDS_DIR = INBODY_RECORDS_DIR
        migrate_v11.DB_PATH = target_db_path
        migrate_v11.EGYM_RECORDS_DIR = EGYM_RECORDS_DIR
        migrate_v11.INBODY_RECORDS_DIR = INBODY_RECORDS_DIR
        migrate_v12.DB_PATH = target_db_path
        migrate_v13.DB_PATH = target_db_path
        migrate_v13.PROTOCOLS_DIR = PROTOCOLS_RECORDS_DIR
        migrate_v14.DB_PATH = target_db_path
        migrate_v14.EVENTS_DIR = EVENTS_RECORDS_DIR
        seed_interventions.DB_PATH = target_db_path

        migrate_v5.migrate()
        migrate_v6.migrate_v6()
        migrate_v7.main()
        migrate_v8.main()
        migrate_v9.main()
        migrate_v10.main()
        migrate_v11.main()
        migrate_v12.migrate(target_db_path)
        migrate_v13.migrate(target_db_path, PROTOCOLS_RECORDS_DIR)
        migrate_v14.migrate(target_db_path, EVENTS_RECORDS_DIR)
        seed_interventions.seed_interventions()

        if os.path.exists(target_db_path + ".bak"):
            os.remove(target_db_path + ".bak")
    finally:
        migrate_v5.DB_PATH = orig_v5_path
        migrate_v6.DB_PATH = orig_v6_path
        migrate_v7.DB_PATH = orig_v7_path
        migrate_v8.DB_PATH = orig_v8_path
        migrate_v9.DB_PATH = orig_v9_path
        migrate_v10.DB_PATH = orig_v10_path
        migrate_v10.EGYM_RECORDS_DIR = orig_v10_egym
        migrate_v10.INBODY_RECORDS_DIR = orig_v10_inbody
        migrate_v11.DB_PATH = orig_v11_path
        migrate_v11.EGYM_RECORDS_DIR = orig_v11_egym
        migrate_v11.INBODY_RECORDS_DIR = orig_v11_inbody
        migrate_v12.DB_PATH = orig_v12_path
        migrate_v13.DB_PATH = orig_v13_path
        migrate_v13.PROTOCOLS_DIR = orig_v13_protocols
        migrate_v14.DB_PATH = orig_v14_path
        migrate_v14.EVENTS_DIR = orig_v14_events
        seed_interventions.DB_PATH = orig_seed_path

    # -------------------------------------------------------------------------
    # STAGE 3: Samsung Health SDK Payload Archival Replay
    # -------------------------------------------------------------------------
    print("\n📱 STAGE 3: Ingesting incremental Samsung Health SDK payloads...")
    sdk_payload_files = sorted(glob.glob(os.path.join(SDK_ARCHIVE_DIR, "**", "*.json"), recursive=True))
    sdk_record_count = 0
    utc_offset_cache = {}
    affected_dates = set()

    for ppath in sdk_payload_files:
        try:
            with open(ppath, "r", encoding="utf-8") as pf:
                raw_text = pf.read()
            envelope = json.loads(raw_text)
            records = envelope.get("records", [])
            device_id = envelope.get("device_id", "samsung-companion")
            collected_at = envelope.get("collected_at", "")
            next_token = envelope.get("changes_token_next")

            for r in records:
                try:
                    ingest_sdk_payload.ingest_record(
                        conn, r, source_name="samsung_health_sdk",
                        utc_offset_cache=utc_offset_cache,
                        affected_dates=affected_dates
                    )
                    sdk_record_count += 1
                except Exception as ex:
                    print(f"    ⚠️  Record ingest warning ({r.get('sdk_type')}): {ex}")

            if device_id and next_token:
                conn.execute("""
                    INSERT INTO sdk_sync_state (device_id, last_token, last_synced_at, total_payloads, total_records)
                    VALUES (?, ?, ?, 1, ?)
                    ON CONFLICT(device_id) DO UPDATE SET
                        last_token=excluded.last_token,
                        last_synced_at=excluded.last_synced_at,
                        total_payloads=sdk_sync_state.total_payloads + 1,
                        total_records=sdk_sync_state.total_records + excluded.total_records,
                        updated_at=CURRENT_TIMESTAMP
                """, (device_id, next_token, collected_at, len(records)))
        except Exception as e:
            print(f"    ⚠️  Failed reading payload {ppath}: {e}")

    conn.commit()
    print(f"  -> Ingested {sdk_record_count} records from {len(sdk_payload_files)} SDK archive payloads.")

    # -------------------------------------------------------------------------
    # STAGE 4: Withings Measurement Records Ingestion
    # -------------------------------------------------------------------------
    print("\n⚖️  STAGE 4: Ingesting Withings measurement records from data/records/withings/...")
    cur = conn.cursor()
    withings_files = sorted(glob.glob(os.path.join(WITHINGS_RECORDS_DIR, "*.json")))
    withings_count = 0
    for wpath in withings_files:
        try:
            with open(wpath, "r", encoding="utf-8") as wf:
                rec = json.load(wf)
            cur.execute("""
                INSERT OR REPLACE INTO withings_readings (
                    id, user_id, user_name, timestamp, date,
                    weight_kg, fat_free_mass_kg, fat_ratio_pct, fat_mass_weight_kg,
                    muscle_mass_kg, hydration_tbw_kg, bone_mass_kg,
                    pulse_wave_velocity_ms, heart_rate_bpm, visceral_fat_index, source, raw_json
                ) VALUES (
                    :id, :user_id, :user_name, :timestamp, :date,
                    :weight_kg, :fat_free_mass_kg, :fat_ratio_pct, :fat_mass_weight_kg,
                    :muscle_mass_kg, :hydration_tbw_kg, :bone_mass_kg,
                    :pulse_wave_velocity_ms, :heart_rate_bpm, :visceral_fat_index, 'withings_api', :raw_json
                )
            """, rec)
            withings_count += 1
        except Exception as e:
            print(f"    ⚠️  Error loading withings record {wpath}: {e}")

    conn.commit()
    print(f"  -> Ingested {withings_count} Withings scale readings.")

    # -------------------------------------------------------------------------
    # STAGE 4b: Intervention Administration Events (ADR-028 Event Sourcing)
    # -------------------------------------------------------------------------
    print("\n💊 STAGE 4b: Ingesting intervention dose events from data/records/interventions/...")
    intervention_files = sorted(glob.glob(os.path.join(INTERVENTIONS_RECORDS_DIR, "*.json")))
    intervention_count = 0
    for ipath in intervention_files:
        try:
            with open(ipath, "r", encoding="utf-8") as inf:
                irec = json.load(inf)
            cur.execute("""
                INSERT OR REPLACE INTO intervention_events (
                    id, intervention_id, timestamp, date, event_type,
                    divergence_code, precision, confidence, notes
                ) VALUES (
                    :id, :intervention_id, :timestamp, :date, :event_type,
                    :divergence_code, :precision, :confidence, :notes
                )
            """, irec)
            intervention_count += 1
        except Exception as e:
            print(f"    ⚠️  Error loading intervention record {ipath}: {e}")

    conn.commit()
    print(f"  -> Ingested {intervention_count} intervention administration events.")

    # -------------------------------------------------------------------------
    # STAGE 4c: Protocols Event Sourcing (ADR-028, ADR-037)
    # -------------------------------------------------------------------------
    print("\n📋 STAGE 4c: Ingesting protocol definitions from data/records/protocols/...")
    protocols_ingested = migrate_v13.migrate(target_db_path, PROTOCOLS_RECORDS_DIR)
    print(f"  -> Synchronized {protocols_ingested} protocols into fact store.")

    # -------------------------------------------------------------------------
    # STAGE 4d: Life Events Event Sourcing (ADR-028, ADR-038)
    # -------------------------------------------------------------------------
    print("\n🗺️  STAGE 4d: Ingesting life events from data/records/events/...")
    events_ingested = migrate_v14.migrate(target_db_path, EVENTS_RECORDS_DIR)
    print(f"  -> Synchronized {events_ingested} life events into fact store.")

    # -------------------------------------------------------------------------
    # STAGE 5: Daily Summary Rollup Materialization & Physiological Enrichment
    # -------------------------------------------------------------------------
    print("\n📅 STAGE 5: Interval tagging & materializing full daily_summary rollup...")
    tagged_ex = aggregation_engine.tag_samples_with_exercise(conn)
    tagged_sl = aggregation_engine.tag_samples_with_sleep(conn)
    enriched_sl = aggregation_engine.enrich_sleep_sessions_with_hr(conn)
    print(f"  -> Tagged {tagged_ex} HR samples to exercise, {tagged_sl} to sleep.")
    print(f"  -> Enriched {enriched_sl} sleep sessions with nocturnal cardiac recovery metrics.")
    aggregation_engine.rebuild_daily_summary(conn, target_dates=None)
    cur.execute("SELECT COUNT(*) FROM daily_summary")
    summary_count = cur.fetchone()[0]
    print(f"  -> Materialized {summary_count} daily rollup days in daily_summary.")

    # Verify key fact table counts
    print("\n📊 STAGE 6: Fact Store Verification:")
    for table_name in [
        "heart_rate_records", "heart_rate_samples", "sleep_sessions",
        "exercise_sessions", "clinical_vitals", "food_log_items",
        "withings_readings", "intervention_events", "stress_samples", "hrv_samples", "daily_summary",
        "lab_results", "egym_workouts", "inbody_scans", "egym_bioage", "egym_muscle_balance", "protocols", "life_events"
    ]:
        try:
            cur.execute(f"SELECT COUNT(*) FROM {table_name}")
            cnt = cur.fetchone()[0]
            print(f"  · {table_name:<25}: {cnt:>10,}")
        except Exception:
            pass

    conn.close()

    # -------------------------------------------------------------------------
    # STAGE 7: Exporter
    # -------------------------------------------------------------------------
    print("\n🎨 STAGE 7: Exporting dashboard payload...")
    orig_export_db = export_dashboard_data.DB_PATH
    try:
        export_dashboard_data.DB_PATH = target_db_path
        export_dashboard_data.export_data()
    finally:
        export_dashboard_data.DB_PATH = orig_export_db

    print("\n" + "=" * 70)
    print("✅ DATABASE REBUILD COMPLETE!")
    print("=" * 70)


def main():
    parser = argparse.ArgumentParser(description="Rebuild Health Dashboard SQLite Database from scratch.")
    parser.add_argument("--db-path", default=DEFAULT_DB_PATH, help=f"Path to SQLite DB (default: {DEFAULT_DB_PATH})")
    parser.add_argument("--clean", action="store_true", help="Delete target DB first if it exists")
    args = parser.parse_args()

    rebuild_database(args.db_path, clean=args.clean)


if __name__ == "__main__":
    main()

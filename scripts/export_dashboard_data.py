#!/usr/bin/env python3
"""
Enriched Exporter Engine: Exports health_dashboard.db data into structured JSON for Stage A Dashboard.

Implements consensus specification:
- Tier 1 HUD: Live +1.37 kg E2 delta alert, pharmacokinetic timers, compact 7-day data-health status pill.
- Body Composition: 9-year Withings historical series with 7-day & 30-day EMAs and bioimpedance disclaimer.
- Nutrition: Complete-day partitioning with explicit coverage denominators and targets.
- Autonomic & Recovery: 7-day HRV corridor, resting HR trend, and 60-second recovery drop curves (HRR-60).
- Sleep Architecture: Coverage-gated behind rolling >= 4 of 7 nights threshold.
- Life Eras & Interventions: Historical epochs and active protocol catalog.
"""

import os
import sys
import json
import sqlite3
import datetime
import threading
from datetime import timedelta
import pandas as pd
import numpy as np
from typing import Dict, Any, List, Optional

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DB_PATH = os.path.join(BASE_DIR, "data", "health_dashboard.db")
OUTPUT_JSON = os.path.join(BASE_DIR, "dashboard", "dashboard_data.json")
INDEX_HTML_PATH = os.path.join(BASE_DIR, "dashboard", "index.html")

EXPORT_LOCK = threading.Lock()

try:
    from version import SYSTEM_VERSION
except ImportError:
    import sys
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from version import SYSTEM_VERSION

def _parse_ts_utc(value):
    """Parse a timestamp string to a timezone-aware UTC datetime, for
    COMPARISON ONLY -- callers must keep and return the original string,
    never this parsed value (A21 fix must not change what gets exported).

    The DB holds a mix of formats across tables: '...+00:00', '...Z',
    fractional-second '...Z', and occasionally naive 'YYYY-MM-DD HH:MM:SS'.
    Naive values are treated as UTC. Anything unparseable returns None so
    the caller can skip it instead of falling back to lexical string
    comparison, where '+00:00' and 'Z' don't sort the same as they compare
    chronologically.
    """
    if value is None:
        return None
    try:
        dt = pd.to_datetime(value, utc=True, errors="coerce")
    except (TypeError, ValueError):
        return None
    if pd.isnull(dt):
        return None
    return dt

def latest_data_timestamp(cur):
    """Newest real measurement in the database, as an ISO string.

    Replaces a wall-clock `generated_at`. Two reasons:

    1. DETERMINISM. The exporter injects its payload into dashboard/index.html.
       A wall-clock stamp changed that file on every run, so the verification
       AGENTS.md §1.4 requires before merging dirtied the tree that §1.3
       requires to be clean -- and produced a trail of
       "chore(data): refresh dashboard telemetry payload timestamp" commits
       that carried no information. Identical data now yields an identical file.

    2. IT IS THE MORE USEFUL FACT. "Generated 13:42" only says the script ran.
       "Current through 11:09" says whether the last sync actually landed.

    Per-table MAX(...) values are compared as parsed, timezone-aware
    datetimes (A21), not as raw strings -- '2026-08-26T07:44:38+00:00' vs
    '2026-08-28T12:00:00Z' vs a fractional-second 'Z' value do not sort
    correctly as text. The ORIGINAL string of whichever candidate parses
    newest is returned, so exported output is unaffected by this fix.
    """
    sources = [
        ("heart_rate_records", "timestamp"),
        ("sleep_sessions", "end_time"),
        ("exercise_sessions", "end_time"),
        ("withings_readings", "timestamp"),
        ("stress_records", "end_time"),
        ("hrv_records", "end_time"),
        ("food_log_items", "timestamp"),
    ]
    newest = None
    newest_dt = None
    for table, column in sources:
        try:
            row = cur.execute(
                "SELECT MAX(%s) FROM %s" % (column, table)).fetchone()
        except Exception:
            continue
        if not row or not row[0]:
            continue
        candidate = row[0]
        candidate_dt = _parse_ts_utc(candidate)
        if candidate_dt is None:
            continue
        if newest_dt is None or candidate_dt > newest_dt:
            newest_dt = candidate_dt
            newest = candidate
    return str(newest) if newest is not None else None

def _export_data_unlocked():
    os.makedirs(os.path.dirname(OUTPUT_JSON), exist_ok=True)
    conn = sqlite3.connect(DB_PATH)

    try:
        # -------------------------------------------------------------------------
        # 1. Active Protocol & Tier 1 Morning Command HUD
        # -------------------------------------------------------------------------
        cur = conn.cursor()

        # Single data-derived "today" anchor for every rolling-window query in
        # this export. ADR-013 forbids wall-clock time in exported content, so
        # "today" is defined as the most recent day present in daily_summary --
        # computed once here and reused everywhere a reference date is needed,
        # replacing both the hardcoded '2026-08-26' literals and date('now').
        cur.execute("SELECT MAX(date) FROM daily_summary")
        anchor_row = cur.fetchone()
        data_anchor_date = anchor_row[0] if anchor_row and anchor_row[0] else None

        # Get protocols and active protocol phase (ADR-037)
        cur.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='protocols'")
        has_protocols = cur.fetchone() is not None
        all_protocols = []
        active_protocol = None

        if has_protocols:
            cur.execute("""
                SELECT id, version, name, category, status, effective_start, effective_end,
                       intent_summary, compounds_json, supplements_json, training_program_json,
                       diagnostic_panel_json, safety_redlines_json, milestones_json
                FROM protocols
                ORDER BY effective_start ASC
            """)
            for pr in cur.fetchall():
                p_item = {
                    "id": pr[0],
                    "version": pr[1],
                    "name": pr[2],
                    "category": pr[3],
                    "status": pr[4],
                    "effective_start": pr[5],
                    "effective_end": pr[6],
                    "intent_summary": pr[7],
                    "compounds": json.loads(pr[8]) if pr[8] else [],
                    "supplements": json.loads(pr[9]) if pr[9] else [],
                    "training_program": json.loads(pr[10]) if pr[10] else {},
                    "diagnostic_panel": json.loads(pr[11]) if pr[11] else {},
                    "safety_redlines": json.loads(pr[12]) if pr[12] else {},
                    "milestones": json.loads(pr[13]) if pr[13] else []
                }
                all_protocols.append(p_item)
                if p_item["status"] == "active":
                    active_protocol = p_item

            if not active_protocol and all_protocols:
                anchor = data_anchor_date or "2026-09-08"
                for p in reversed(all_protocols):
                    if p["effective_start"] <= anchor and (not p["effective_end"] or p["effective_end"] >= anchor):
                        active_protocol = p
                        break
                if not active_protocol:
                    active_protocol = all_protocols[-1]

        if active_protocol:
            era_start_dt = pd.to_datetime(active_protocol["effective_start"])
            total_weeks = 16
            if active_protocol.get("effective_end"):
                try:
                    diff_days = (pd.to_datetime(active_protocol["effective_end"]) - era_start_dt).days
                    total_weeks = max(1, diff_days // 7)
                except Exception:
                    total_weeks = 16
            if data_anchor_date:
                elapsed_days = (pd.to_datetime(data_anchor_date) - era_start_dt).days
                era_current_week = max(1, min(total_weeks, elapsed_days // 7 + 1))
            else:
                era_current_week = 1

            compounds_desc = ", ".join([f"{c.get('dose', '')} {c.get('name', '')}" for c in active_protocol.get("compounds", [])])
            phase_info = {
                "id": active_protocol["id"],
                "name": active_protocol["name"],
                "start_date": active_protocol["effective_start"],
                "intent": active_protocol["intent_summary"],
                "compounds": compounds_desc,
                "current_week": era_current_week,
                "total_weeks": total_weeks,
                "current_mode": active_protocol.get("training_program", {}).get("mode", "Standard Mode (Weeks 1–4)")
            }
        else:
            # Fallback to life_eras table if protocols table is not yet populated
            cur.execute("SELECT id, name, category, start_date, intent_summary, compounds_and_doses FROM life_eras WHERE category='protocol' AND end_date IS NULL LIMIT 1")
            active_era = cur.fetchone()
            if active_era:
                era_total_weeks = 16
                era_start_dt = pd.to_datetime(active_era[3])
                if data_anchor_date:
                    elapsed_days = (pd.to_datetime(data_anchor_date) - era_start_dt).days
                    era_current_week = max(1, min(era_total_weeks, elapsed_days // 7 + 1))
                else:
                    era_current_week = 1
                phase_info = {
                    "id": active_era[0],
                    "name": active_era[1],
                    "start_date": active_era[3],
                    "intent": active_era[4],
                    "compounds": active_era[5],
                    "current_week": era_current_week,
                    "total_weeks": era_total_weeks,
                    "current_mode": "Standard Mode (Weeks 1–4)"
                }
            else:
                phase_info = {
                    "id": "active_protocol_phase",
                    "name": "Clinical Protocol Phase",
                    "start_date": "2026-08-24",
                    "intent": "Baseline physiological load and adaptation tracking",
                    "compounds": "Personalized clinical protocol",
                    "current_week": 1,
                    "total_weeks": 16,
                    "current_mode": "Standard Mode"
                }

        # Live E2 Scale Delta (48-hour rolling jump check)
        #
        # Computed from actual elapsed time between readings, not row offsets.
        # The old iloc[-1] vs iloc[-3] logic assumed exactly one reading per day:
        # with duplicate-day readings (39 exist) it could diff same-day rows, and
        # across a gap it could silently diff a week apart while still labeling
        # it "48h". Instead: latest reading vs the nearest reading >= 48h earlier,
        # restricted to a 36-72h lookback so a stale reading is never mistaken
        # for "48h ago". No candidate in that window => delta_48h is null, not a
        # guess, and the alert stays inactive.
        df_weight = pd.read_sql_query("SELECT date, timestamp, weight_kg FROM withings_readings WHERE weight_kg IS NOT NULL ORDER BY timestamp ASC", conn)
        e2_alert = {
            "active": False,
            "delta_48h": None,
            "weight_now": None,
            "weight_48h_ago": None,
            "insufficient_data": True,
            "threshold": 1.50,
            "warning_threshold": 1.20,
            "checklist": [
                "Peripheral edema or indentation, itching, or swelling",
                "Ankle / pitting edema or sock-ring indentation",
                "Resting systolic blood pressure spike (+15–20 mmHg)",
                "Nocturnal temperature spike or hot sweats"
            ]
        }
        if len(df_weight) >= 1:
            df_weight["timestamp_dt"] = pd.to_datetime(df_weight["timestamp"], utc=True)
            latest_row = df_weight.iloc[-1]
            w_now = float(latest_row["weight_kg"])
            now_ts = latest_row["timestamp_dt"]
            e2_alert["weight_now"] = round(w_now, 2)

            lookback = df_weight[
                (df_weight["timestamp_dt"] <= now_ts - timedelta(hours=36)) &
                (df_weight["timestamp_dt"] >= now_ts - timedelta(hours=72))
            ]
            if not lookback.empty:
                target = now_ts - timedelta(hours=48)
                nearest_idx = (lookback["timestamp_dt"] - target).abs().idxmin()
                w_prev_48h = float(lookback.loc[nearest_idx, "weight_kg"])
                delta_48h = round(w_now - w_prev_48h, 2)
                e2_alert["weight_48h_ago"] = round(w_prev_48h, 2)
                e2_alert["delta_48h"] = delta_48h
                e2_alert["insufficient_data"] = False
                # If delta >= 1.20 kg (within 0.30 kg of 1.50 kg), activate warning banner
                if delta_48h >= 1.20:
                    e2_alert["active"] = True

        # 7-Day Data Health Status Pill
        cur.execute("""
            SELECT
                COUNT(*) as total_days,
                SUM(has_weight) as scale_days,
                SUM(has_hr) as hr_days,
                SUM(nutrition_complete) as nutr_days
            FROM daily_summary
            WHERE date >= date(?, '-6 days')
        """, (data_anchor_date,))
        dh_row = cur.fetchone()

        cur.execute("""
            SELECT COUNT(DISTINCT wake_date)
            FROM sleep_sessions
            WHERE wake_date >= date(?, '-6 days') AND session_role = 'main_sleep'
        """, (data_anchor_date,))
        sleep_days_cnt = cur.fetchone()[0]

        data_health = {
            "days_evaluated": dh_row[0] if dh_row else 7,
            "scale_days": dh_row[1] if dh_row and dh_row[1] else 0,
            "hr_days": dh_row[2] if dh_row and dh_row[2] else 0,
            "nutrition_days": dh_row[3] if dh_row and dh_row[3] else 0,
            "sleep_days": sleep_days_cnt,
        }

        # Adherence is computed from the log, never assumed. An empty
        # intervention_events table means "nothing recorded", which must NOT render
        # as 100% -- absence reading as perfect compliance is the failure mode the
        # coverage-denominator rule exists to prevent (AGENTS.md §3.2).
        cur.execute("SELECT COUNT(*) FROM intervention_events WHERE event_type = 'dose_taken'")
        doses_logged = cur.fetchone()[0]
        if doses_logged == 0:
            data_health["meds_compliance_pct"] = None
            data_health["meds_compliance_label"] = "No doses logged"
        else:
            days = data_health["days_evaluated"] or 7
            cur.execute("""
                SELECT COUNT(DISTINCT date) FROM intervention_events
                WHERE event_type = 'dose_taken' AND date >= date(?, ?)
            """, (data_anchor_date, "-%d day" % days))
            covered = cur.fetchone()[0]
            data_health["meds_compliance_pct"] = round(100.0 * covered / days, 1)
            data_health["meds_compliance_label"] = "%d of %d days logged" % (covered, days)

        # -------------------------------------------------------------------------
        # 2. Body Composition Explorer (9-Year Withings History & EMAs)
        # -------------------------------------------------------------------------
        # 0. User Demographics Registry (Schema v6 / ADR-003)
        # -------------------------------------------------------------------------
        cur.execute("SELECT id, name, withings_user_id, age, gender, height_cm, dob, measurement_system FROM users WHERE active = 1 LIMIT 1")
        user_row = cur.fetchone()
        if user_row:
            height_cm = float(user_row[5]) if user_row[5] else 173.0
            user_profile = {
                "id": user_row[0],
                "name": user_row[1],
                "withings_user_id": user_row[2],
                "age": user_row[3],
                "gender": user_row[4],
                "height_cm": height_cm,
                "dob": user_row[6],
                "measurement_system": user_row[7]
            }
        else:
            height_cm = 175.0
            user_profile = {"id": "user", "name": "User", "age": None, "gender": "unspecified", "height_cm": 175.0, "dob": None, "measurement_system": "metric"}

        height_m = height_cm / 100.0
        height_sq = height_m ** 2
        bmi_25_kg = round(25.0 * height_sq, 2)  # 74.82 kg
        bmi_30_kg = round(30.0 * height_sq, 2)  # 89.79 kg

        # -------------------------------------------------------------------------
        # 2. Body Composition & 9-Year Master Arc (EMA Filtering + BMI)
        # -------------------------------------------------------------------------
        df_scale_readings = pd.read_sql_query("""
            SELECT date, weight_kg, fat_ratio_pct, muscle_mass_kg, hydration_tbw_kg
            FROM withings_readings
            ORDER BY date ASC
        """, conn)

        # Collapse to one row per calendar date (A13) before any EMA math.
        # withings_readings holds 1,131 rows across ~9 years at ~34% day
        # coverage, with 39 same-day duplicate readings. A span=7/30 EMA
        # computed over READING ROWS (as this used to do) silently spans weeks
        # of calendar time on sparse stretches, and duplicate-day rows would
        # double-count under any row-based window. Averaging same-day
        # duplicates first makes each row mean exactly one date.
        df_scale = df_scale_readings.groupby("date", as_index=False)[
            ["weight_kg", "fat_ratio_pct", "muscle_mass_kg", "hydration_tbw_kg"]
        ].mean()
        df_scale["date_dt"] = pd.to_datetime(df_scale["date"])
        df_scale = df_scale.sort_values("date_dt").reset_index(drop=True)

        # Time-aware EMAs, calibrated to the old span=7 / span=30 (adjust=False)
        # decay via alpha = 2/(span+1) -> halflife = -ln(2)/ln(1-alpha):
        #   span=7  -> halflife = 2.41 days
        #   span=30 -> halflife = 10.40 days
        # Passing times= makes the decay run on actual elapsed calendar time
        # rather than row position, so a "7-day" EMA reading after a multi-week
        # gap sits near that day's raw value instead of dragging weeks-old
        # momentum forward. pandas requires adjust=True whenever times= is
        # supplied (adjust=False is not supported with a times index) -- that
        # is the built-in bias-correction weighting adjust=False skips, and is
        # accepted here as a side effect of the time-aware fix, not a separate
        # behavior change.
        HALFLIFE_7D = pd.Timedelta(days=2.41)
        HALFLIFE_30D = pd.Timedelta(days=10.40)
        _ema_times = df_scale["date_dt"]

        def _ema7(series):
            return series.ewm(halflife=HALFLIFE_7D, times=_ema_times, adjust=True).mean()

        def _ema30(series):
            return series.ewm(halflife=HALFLIFE_30D, times=_ema_times, adjust=True).mean()

        # Exponential Moving Averages (EMA 7d and 30d)
        df_scale["weight_ema7"] = _ema7(df_scale["weight_kg"])
        df_scale["weight_ema30"] = _ema30(df_scale["weight_kg"])
        df_scale["fat_ema7"] = _ema7(df_scale["fat_ratio_pct"])
        df_scale["fat_ema30"] = _ema30(df_scale["fat_ratio_pct"])
        df_scale["muscle_ema7"] = _ema7(df_scale["muscle_mass_kg"])
        df_scale["muscle_ema30"] = _ema30(df_scale["muscle_mass_kg"])
        df_scale["tbw_ema30"] = _ema30(df_scale["hydration_tbw_kg"])

        # Fat Mass in Kilograms (ADR-007) -- derived on the daily-collapsed
        # frame before its EMA, same as every other derived column below.
        df_scale["fat_mass_kg"] = df_scale["weight_kg"] * (df_scale["fat_ratio_pct"] / 100.0)
        df_scale["fat_mass_ema7"] = _ema7(df_scale["fat_mass_kg"])
        df_scale["fat_mass_ema30"] = _ema30(df_scale["fat_mass_kg"])

        # Fat-Free Mass (kg) & FFMI (kg/m²) time-series (ADR-004 & ADR-015)
        def calc_ffm(row):
            w = row["weight_kg"]
            fat_pct = row["fat_ratio_pct"]
            if pd.notnull(w) and pd.notnull(fat_pct) and fat_pct > 0:
                return float(w) * (1.0 - float(fat_pct) / 100.0)
            elif pd.notnull(row["muscle_mass_kg"]):
                return float(row["muscle_mass_kg"]) + 3.5
            return None

        df_scale["ffm_kg"] = df_scale.apply(calc_ffm, axis=1)
        df_scale["ffmi_raw"] = df_scale["ffm_kg"] / height_sq
        # Height-normalized FFMI standardized to 1.80 m (Kouri et al. 1995 / ADR-015)
        norm_delta = 6.3 * (1.80 - height_m)
        df_scale["ffmi_norm"] = df_scale["ffmi_raw"] + norm_delta
        df_scale["ffmi"] = df_scale["ffmi_norm"]  # Primary metric is Normalized FFMI

        df_scale["ffmi_raw_ema7"] = _ema7(df_scale["ffmi_raw"])
        df_scale["ffmi_raw_ema30"] = _ema30(df_scale["ffmi_raw"])
        df_scale["ffmi_norm_ema7"] = _ema7(df_scale["ffmi_norm"])
        df_scale["ffmi_norm_ema30"] = _ema30(df_scale["ffmi_norm"])
        df_scale["ffmi_ema7"] = df_scale["ffmi_norm_ema7"]
        df_scale["ffmi_ema30"] = df_scale["ffmi_norm_ema30"]

        body_comp_history = []
        for _, row in df_scale.iterrows():
            w = float(row["weight_kg"]) if pd.notnull(row["weight_kg"]) else None
            bmi_val = round(w / height_sq, 2) if w else None
            m = float(row["muscle_mass_kg"]) if pd.notnull(row["muscle_mass_kg"]) else None
            fm = float(row["fat_mass_kg"]) if pd.notnull(row["fat_mass_kg"]) else None
            ffm_val = float(row["ffm_kg"]) if pd.notnull(row["ffm_kg"]) else None
            ffmi_norm_val = round(float(row["ffmi_norm"]), 2) if pd.notnull(row["ffmi_norm"]) else None
            ffmi_raw_val = round(float(row["ffmi_raw"]), 2) if pd.notnull(row["ffmi_raw"]) else None
            ffmi_norm_ema7 = round(float(row["ffmi_norm_ema7"]), 2) if pd.notnull(row["ffmi_norm_ema7"]) else None
            ffmi_norm_ema30 = round(float(row["ffmi_norm_ema30"]), 2) if pd.notnull(row["ffmi_norm_ema30"]) else None
            ffmi_raw_ema7 = round(float(row["ffmi_raw_ema7"]), 2) if pd.notnull(row["ffmi_raw_ema7"]) else None
            ffmi_raw_ema30 = round(float(row["ffmi_raw_ema30"]), 2) if pd.notnull(row["ffmi_raw_ema30"]) else None
            body_comp_history.append({
                "date": str(row["date"]),
                "weight": round(w, 2) if w else None,
                "bmi": bmi_val,
                "fat_pct": round(float(row["fat_ratio_pct"]), 2) if pd.notnull(row["fat_ratio_pct"]) else None,
                "fat_mass_kg": round(fm, 2) if fm else None,
                "fat_mass_ema7": round(float(row["fat_mass_ema7"]), 2) if pd.notnull(row["fat_mass_ema7"]) else None,
                "fat_mass_ema30": round(float(row["fat_mass_ema30"]), 2) if pd.notnull(row["fat_mass_ema30"]) else None,
                "muscle_kg": round(m, 2) if m else None,
                "ffm_kg": round(ffm_val, 2) if ffm_val else None,
                "ffmi": ffmi_norm_val,
                "ffmi_norm": ffmi_norm_val,
                "ffmi_raw": ffmi_raw_val,
                "ffmi_ema7": ffmi_norm_ema7,
                "ffmi_ema30": ffmi_norm_ema30,
                "ffmi_norm_ema7": ffmi_norm_ema7,
                "ffmi_norm_ema30": ffmi_norm_ema30,
                "ffmi_raw_ema7": ffmi_raw_ema7,
                "ffmi_raw_ema30": ffmi_raw_ema30,
                "tbw_kg": round(float(row["hydration_tbw_kg"]), 2) if pd.notnull(row["hydration_tbw_kg"]) else None,
                "weight_ema7": round(float(row["weight_ema7"]), 2) if pd.notnull(row["weight_ema7"]) else None,
                "weight_ema30": round(float(row["weight_ema30"]), 2) if pd.notnull(row["weight_ema30"]) else None,
                "fat_ema7": round(float(row["fat_ema7"]), 2) if pd.notnull(row["fat_ema7"]) else None,
                "fat_ema30": round(float(row["fat_ema30"]), 2) if pd.notnull(row["fat_ema30"]) else None,
                "muscle_ema7": round(float(row["muscle_ema7"]), 2) if pd.notnull(row["muscle_ema7"]) else None,
                "muscle_ema30": round(float(row["muscle_ema30"]), 2) if pd.notnull(row["muscle_ema30"]) else None,
                "muscle_ref_min": round(float(row["weight_ema30"]) * 0.74, 2) if pd.notnull(row["weight_ema30"]) else (round(w * 0.74, 2) if w else None),
                "muscle_ref_max": round(float(row["weight_ema30"]) * 0.87, 2) if pd.notnull(row["weight_ema30"]) else (round(w * 0.87, 2) if w else None),
                "muscle_ref_min_ema7": round(float(row["weight_ema7"]) * 0.74, 2) if pd.notnull(row["weight_ema7"]) else None,
                "muscle_ref_max_ema7": round(float(row["weight_ema7"]) * 0.87, 2) if pd.notnull(row["weight_ema7"]) else None,
                "fat_ref_min": round(float(row["weight_ema30"]) * 0.14, 2) if pd.notnull(row["weight_ema30"]) else (round(w * 0.14, 2) if w else None),
                "fat_ref_max": round(float(row["weight_ema30"]) * 0.18, 2) if pd.notnull(row["weight_ema30"]) else (round(w * 0.18, 2) if w else None),
                "fat_ref_min_ema7": round(float(row["weight_ema7"]) * 0.14, 2) if pd.notnull(row["weight_ema7"]) else None,
                "fat_ref_max_ema7": round(float(row["weight_ema7"]) * 0.18, 2) if pd.notnull(row["weight_ema7"]) else None,
                "tbw_ema30": round(float(row["tbw_ema30"]), 2) if pd.notnull(row["tbw_ema30"]) else None,
            })

        # Longitudinal stats with elevated FFMI & cycle targets (ADR-004, ADR-007, ADR-015)
        def _first_last_non_null(series):
            non_null = series.dropna()
            if non_null.empty:
                return None, None
            return float(non_null.iloc[0]), float(non_null.iloc[-1])

        start_fat_pct, latest_fat_pct = _first_last_non_null(df_scale["fat_ratio_pct"])
        start_muscle_kg, latest_muscle_kg = _first_last_non_null(df_scale["muscle_mass_kg"])

        latest_w = float(df_scale.iloc[-1]["weight_kg"]) if pd.notnull(df_scale.iloc[-1]["weight_kg"]) else None
        latest_ffmi_norm = round(float(df_scale.iloc[-1]["ffmi_norm"]), 2) if pd.notnull(df_scale.iloc[-1]["ffmi_norm"]) else None
        latest_ffmi_raw = round(float(df_scale.iloc[-1]["ffmi_raw"]), 2) if pd.notnull(df_scale.iloc[-1]["ffmi_raw"]) else None
        start_ffmi_norm = round(float(df_scale.iloc[0]["ffmi_norm"]), 2) if pd.notnull(df_scale.iloc[0]["ffmi_norm"]) else None
        start_ffmi_raw = round(float(df_scale.iloc[0]["ffmi_raw"]), 2) if pd.notnull(df_scale.iloc[0]["ffmi_raw"]) else None
        latest_fm = round(float(df_scale.iloc[-1]["fat_mass_kg"]), 1) if pd.notnull(df_scale.iloc[-1]["fat_mass_kg"]) else None
        start_fm = round(float(df_scale.iloc[0]["fat_mass_kg"]), 1) if pd.notnull(df_scale.iloc[0]["fat_mass_kg"]) else None
        latest_ffm = round(latest_w - latest_fm, 1) if (latest_w is not None and latest_fm is not None) else None

        bc_stats = {
            # df_scale is now collapsed to one row per calendar date (A13), so
            # this counts distinct dates with a scale reading, not raw reading
            # rows -- 1,092 distinct dates vs. 1,131 raw rows in this DB.
            "readings_count": len(df_scale),
            "start_year": "2017",
            "latest_year": "2026",
            "latest_weight": round(latest_w, 1) if latest_w is not None else None,
            "latest_weight_kg": round(latest_w, 1) if latest_w is not None else None,
            "latest_ffm_kg": latest_ffm,
            "start_fat_pct": round(start_fat_pct, 1) if start_fat_pct is not None else None,
            "latest_fat_pct": round(latest_fat_pct, 1) if latest_fat_pct is not None else None,
            "start_fat_mass_kg": start_fm,
            "latest_fat_mass_kg": latest_fm,
            "start_muscle_kg": round(start_muscle_kg, 1) if start_muscle_kg is not None else None,
            "latest_muscle_kg": round(latest_muscle_kg, 1) if latest_muscle_kg is not None else None,
            "height_cm": height_cm,
            "height_m": height_m,
            "start_ffmi": start_ffmi_norm,
            "current_ffmi": latest_ffmi_norm,
            "current_ffmi_norm": latest_ffmi_norm,
            "current_ffmi_raw": latest_ffmi_raw,
            "start_ffmi_norm": start_ffmi_norm,
            "start_ffmi_raw": start_ffmi_raw,
            "ffmi_norm_delta": round(norm_delta, 2),
            "ffmi_athletic_baseline": 20.0,
            "ffmi_cycle_target": 21.8,
            "ffmi_longterm_target": 22.2,
            "ffmi_advanced_limit": 22.0,
            "target_ffm_16wk_kg": 64.0,
            "target_ffm_longterm_kg": 65.0,
            "sarcopenia_almi_cutoff": 7.0,
            "longevity_almi_target": 7.5,
            "current_bmi": round(latest_w / height_sq, 2),
            "bmi_normal_ceiling_kg": bmi_25_kg,
            "bmi_obesity_floor_kg": bmi_30_kg,
            "integrity_label": "Withings Bioimpedance Trend (Directionally valid; absolute values carry ±2 kg impedance noise)"
        }

        # 2b. InBody Gold-Standard Benchmarks
        inbody_benchmarks = []
        try:
            cur.execute("""
                SELECT id, date, time, weight_kg, skeletal_muscle_mass_kg, fat_free_mass_kg,
                       body_fat_kg, body_fat_pct, phase_angle_deg, total_body_water_l,
                       icw_l, ecw_l, ecw_ratio, bmr_kcal, diurnal_offset_kg, notes,
                       visceral_fat_rating, lean_mass_torso_kg,
                       lean_mass_arms_left_kg, lean_mass_arms_right_kg,
                       lean_mass_legs_left_kg, lean_mass_legs_right_kg,
                       fat_mass_torso_kg,
                       fat_mass_arms_left_kg, fat_mass_arms_right_kg,
                       fat_mass_legs_left_kg, fat_mass_legs_right_kg
                FROM inbody_scans
                ORDER BY date ASC, time ASC
            """)
            for ib in cur.fetchall():
                inbody_benchmarks.append({
                    "id": ib[0],
                    "date": ib[1],
                    "time": ib[2],
                    "weight_kg": ib[3],
                    "smm_kg": ib[4],
                    "ffm_kg": ib[5],
                    "body_fat_kg": ib[6],
                    "body_fat_pct": ib[7],
                    "phase_angle_deg": ib[8],
                    "tbw_l": ib[9],
                    "icw_l": ib[10],
                    "ecw_l": ib[11],
                    "ecw_ratio": ib[12],
                    "bmr_kcal": ib[13],
                    "diurnal_offset_kg": ib[14],
                    "notes": ib[15],
                    "visceral_fat_rating": ib[16],
                    "lean_mass_torso_kg": ib[17],
                    "lean_mass_arms_left_kg": ib[18],
                    "lean_mass_arms_right_kg": ib[19],
                    "lean_mass_legs_left_kg": ib[20],
                    "lean_mass_legs_right_kg": ib[21],
                    "fat_mass_torso_kg": ib[22],
                    "fat_mass_arms_left_kg": ib[23],
                    "fat_mass_arms_right_kg": ib[24],
                    "fat_mass_legs_left_kg": ib[25],
                    "fat_mass_legs_right_kg": ib[26]
                })
        except Exception:
            pass

        # -------------------------------------------------------------------------
        # 3. Nutrition Partitioning Explorer (Complete Days Only)
        # -------------------------------------------------------------------------
        cur.execute("""
            SELECT date, calories_kcal, protein_g, carbs_g, fat_g, fiber_g, sodium_mg, meal_items_count, is_complete, logging_quality
            FROM daily_nutrition
            WHERE date >= date(?, '-89 days')
            ORDER BY date ASC
        """, (data_anchor_date,))
        nutr_rows = cur.fetchall()

        nutr_complete_days = [r for r in nutr_rows if r[8] == 1]
        total_complete_count = len(nutr_complete_days)
        avg_calories_complete = round(sum(r[1] for r in nutr_complete_days) / total_complete_count, 1) if total_complete_count > 0 else 0
        avg_protein_complete = round(sum(r[2] for r in nutr_complete_days) / total_complete_count, 1) if total_complete_count > 0 else 0

        nutrition_data = {
            "coverage_last_90d": {
                "complete_days": total_complete_count,
                "total_days": 89,
                "pct": round(100.0 * total_complete_count / 89.0, 1),
                "denominator_label": f"{avg_protein_complete}g protein avg — from {total_complete_count} of 89 complete days (last 90d)"
            },
            "averages_complete": {
                "calories_kcal": avg_calories_complete,
                "protein_g": avg_protein_complete
            },
            "targets": {
                "protein_min_g": 165,
                "protein_max_g": 210,
                "protein_target_g": 180,
                "calorie_surplus_target_kcal": 3300
            },
            "recent_series": [{
                "date": r[0], "calories": r[1], "protein": r[2], "carbs": r[3], "fat": r[4],
                "fiber": r[5], "sodium": r[6], "items": r[7], "is_complete": bool(r[8]), "quality": r[9]
            } for r in nutr_rows]
        }

        # -------------------------------------------------------------------------
        # 4. Autonomic Balance & Cardio Recovery Explorer
        # -------------------------------------------------------------------------
        df_autonomic = pd.read_sql_query("""
            SELECT date, hr_avg_bpm, hr_min_bpm, hr_max_bpm, hr_sample_count,
                   stress_avg_score, stress_sample_count,
                   hrv_avg_rmssd_ms, hrv_avg_sdnn_ms, hrv_sample_count,
                   workout_count, workout_minutes, workout_kcal
            FROM daily_summary
            WHERE date >= date(?, '-89 days')
            ORDER BY date ASC
        """, conn, params=(data_anchor_date,))

        # 7-day rolling HRV corridor (mean +- 1 SD)
        df_autonomic["hrv_rolling_mean"] = df_autonomic["hrv_avg_rmssd_ms"].rolling(7, min_periods=2).mean()
        df_autonomic["hrv_rolling_std"] = df_autonomic["hrv_avg_rmssd_ms"].rolling(7, min_periods=2).std().fillna(5.0)
        df_autonomic["hrv_upper_sd"] = df_autonomic["hrv_rolling_mean"] + df_autonomic["hrv_rolling_std"]
        df_autonomic["hrv_lower_sd"] = df_autonomic["hrv_rolling_mean"] - df_autonomic["hrv_rolling_std"]

        autonomic_series = []
        for _, r in df_autonomic.iterrows():
            autonomic_series.append({
                "date": str(r["date"]),
                "hr_avg": round(float(r["hr_avg_bpm"]), 1) if pd.notnull(r["hr_avg_bpm"]) else None,
                "hr_min": round(float(r["hr_min_bpm"]), 1) if pd.notnull(r["hr_min_bpm"]) else None,
                "hr_max": round(float(r["hr_max_bpm"]), 1) if pd.notnull(r["hr_max_bpm"]) else None,
                "stress_score": round(float(r["stress_avg_score"]), 1) if pd.notnull(r["stress_avg_score"]) else None,
                "hrv_rmssd": round(float(r["hrv_avg_rmssd_ms"]), 1) if pd.notnull(r["hrv_avg_rmssd_ms"]) else None,
                "hrv_sdnn": round(float(r["hrv_avg_sdnn_ms"]), 1) if pd.notnull(r["hrv_avg_sdnn_ms"]) else None,
                "hrv_upper": round(float(r["hrv_upper_sd"]), 1) if pd.notnull(r["hrv_upper_sd"]) else None,
                "hrv_lower": round(float(r["hrv_lower_sd"]), 1) if pd.notnull(r["hrv_lower_sd"]) else None,
                "workout_mins": float(r["workout_minutes"]) if pd.notnull(r["workout_minutes"]) else 0,
                "workout_count": int(r["workout_count"]) if pd.notnull(r["workout_count"]) else 0
            })

        # Latest 60-Second HR Recovery Curve (HRR-60)
        cur.execute("""
            SELECT r.id, r.date, r.duration_seconds, r.point_count
            FROM exercise_recovery r
            JOIN heart_rate_recovery_samples s ON r.id = s.recovery_id
            GROUP BY r.id
            ORDER BY r.date DESC LIMIT 1
        """)
        latest_rec = cur.fetchone()
        hrr_points = []
        if latest_rec:
            rec_id = latest_rec[0]
            cur.execute("""
                SELECT elapsed_ms, bpm
                FROM heart_rate_recovery_samples
                WHERE recovery_id = ?
                ORDER BY elapsed_ms ASC
            """, (rec_id,))
            hrr_points = [{"elapsed_s": round(r[0] / 1000.0, 1), "bpm": r[1]} for r in cur.fetchall()]

        # -------------------------------------------------------------------------
        # 5. Sleep Architecture Explorer (Coverage Gated)
        # -------------------------------------------------------------------------
        cur.execute("""
            SELECT COUNT(DISTINCT wake_date)
            FROM sleep_sessions
            WHERE wake_date >= date(?, '-6 days') AND session_role = 'main_sleep'
        """, (data_anchor_date,))
        recent_main_sleeps_7d = cur.fetchone()[0]
        sleep_is_gated = recent_main_sleeps_7d < 4

        sleep_data = {
            "is_gated": sleep_is_gated,
            "recent_7d_nights": recent_main_sleeps_7d,
            "threshold_required": 4,
            "gated_message": (
                f"Sleep Architecture Gated: {recent_main_sleeps_7d} of last 7 nights recorded. Not enough data to trend. Wear the watch overnight."
                if sleep_is_gated else f"Sleep Coverage Complete: {recent_main_sleeps_7d} of last 7 nights recorded."
            ),
            "recent_sessions": []
        }
        if not sleep_is_gated:
            cur.execute("PRAGMA table_info(sleep_sessions)")
            sleep_cols = {r[1] for r in cur.fetchall()}
            has_nocturnal_hr = "sleeping_hr_mean" in sleep_cols

            if has_nocturnal_hr:
                cur.execute("""
                    SELECT wake_date, start_time, end_time, duration_minutes, total_sleep_minutes,
                           deep_sleep_minutes, rem_sleep_minutes, light_sleep_minutes, awake_minutes,
                           efficiency_pct, sleep_score,
                           sleeping_hr_mean, sleeping_hr_nadir, nocturnal_dip_pct, sleeping_hr_samples_n
                    FROM sleep_sessions
                    WHERE session_role = 'main_sleep'
                    ORDER BY wake_date DESC LIMIT 14
                """)
                sessions = [{
                    "wake_date": r[0], "start": r[1], "end": r[2], "duration_mins": r[3], "total_sleep_mins": r[4],
                    "deep_mins": r[5], "rem_mins": r[6], "light_mins": r[7], "awake_mins": r[8],
                    "efficiency_pct": r[9], "score": r[10],
                    "sleeping_hr_mean": r[11], "sleeping_hr_nadir": r[12], "nocturnal_dip_pct": r[13], "sleeping_hr_samples_n": r[14]
                } for r in cur.fetchall()]
            else:
                cur.execute("""
                    SELECT wake_date, start_time, end_time, duration_minutes, total_sleep_minutes,
                           deep_sleep_minutes, rem_sleep_minutes, light_sleep_minutes, awake_minutes,
                           efficiency_pct, sleep_score
                    FROM sleep_sessions
                    WHERE session_role = 'main_sleep'
                    ORDER BY wake_date DESC LIMIT 14
                """)
                sessions = [{
                    "wake_date": r[0], "start": r[1], "end": r[2], "duration_mins": r[3], "total_sleep_mins": r[4],
                    "deep_mins": r[5], "rem_mins": r[6], "light_mins": r[7], "awake_mins": r[8],
                    "efficiency_pct": r[9], "score": r[10],
                    "sleeping_hr_mean": None, "sleeping_hr_nadir": None, "nocturnal_dip_pct": None, "sleeping_hr_samples_n": 0
                } for r in cur.fetchall()]
            sleep_data["recent_sessions"] = sessions

            if sessions:
                sample_7d = sessions[:min(len(sessions), 7)]
                valid_dur = [s["total_sleep_mins"] for s in sample_7d if s["total_sleep_mins"] is not None]
                valid_deep = [s["deep_mins"] for s in sample_7d if s["deep_mins"] is not None]
                valid_rem = [s["rem_mins"] for s in sample_7d if s["rem_mins"] is not None]
                valid_eff = [s["efficiency_pct"] for s in sample_7d if s["efficiency_pct"] is not None]
                
                sleep_data["averages_7d"] = {
                    "avg_duration_h": round(sum(valid_dur) / (len(valid_dur) * 60.0), 1) if valid_dur else 0,
                    "avg_deep_mins": round(sum(valid_deep) / len(valid_deep), 1) if valid_deep else 0,
                    "avg_rem_mins": round(sum(valid_rem) / len(valid_rem), 1) if valid_rem else 0,
                    "avg_efficiency_pct": round(sum(valid_eff) / len(valid_eff), 1) if valid_eff else 0,
                    "sample_nights": len(sample_7d)
                }

        # -------------------------------------------------------------------------
        # 6. Life Eras & Interventions Catalog
        # -------------------------------------------------------------------------
        cur.execute("SELECT id, name, category, start_date, end_date, is_fuzzy, intent_summary, compounds_and_doses, dietary_strategy, training_focus, retrospective_learnings FROM life_eras ORDER BY start_date ASC")
        life_eras = [{
            "id": r[0], "name": r[1], "category": r[2], "start_date": r[3], "end_date": r[4],
            "is_fuzzy": bool(r[5]), "intent": r[6], "compounds": r[7], "diet": r[8], "training": r[9], "learnings": r[10]
        } for r in cur.fetchall()]

        cur.execute("SELECT id, name, category, default_dose, frequency_hours, unit_cost, pack_size, pack_price, shelf_life_days, current_inventory_units, notes FROM interventions_catalog")
        catalog = [{
            "id": r[0], "name": r[1], "category": r[2], "dose": r[3], "frequency_h": r[4],
            "unit_cost": r[5], "pack_size": r[6], "pack_price": r[7], "shelf_life_d": r[8],
            "inventory": r[9], "notes": r[10]
        } for r in cur.fetchall()]

        # Contextual Life Events (ADR-038)
        life_events = []
        try:
            cur.execute("""
                SELECT id, category, title, start_date, end_date, severity, notes, tags_json, impact_json
                FROM life_events
                ORDER BY start_date ASC, id ASC
            """)
            for r in cur.fetchall():
                try:
                    tags = json.loads(r[7]) if r[7] else []
                except Exception:
                    tags = []
                try:
                    impact = json.loads(r[8]) if r[8] else {}
                except Exception:
                    impact = {}
                life_events.append({
                    "id": r[0],
                    "category": r[1],
                    "title": r[2],
                    "start_date": r[3],
                    "end_date": r[4],
                    "severity": r[5],
                    "notes": r[6],
                    "tags": tags,
                    "physiological_impact": impact
                })
        except Exception:
            pass

        # -------------------------------------------------------------------------
        # 6b. Exercise & Training Telemetry (ADR-026: Cadence, Mechanical Load & HR Zones)
        # -------------------------------------------------------------------------
        cur.execute("SELECT MAX(date) FROM exercise_sessions")
        max_ex_date_row = cur.fetchone()
        latest_ex_date_str = max_ex_date_row[0] if max_ex_date_row and max_ex_date_row[0] else "2026-08-30"
        latest_ex_dt = pd.to_datetime(latest_ex_date_str)
        ex_7d_start = (latest_ex_dt - timedelta(days=6)).strftime("%Y-%m-%d")
        ex_7d_end = latest_ex_date_str

        # 7-day cadence stats
        cur.execute("""
            SELECT count(*), coalesce(sum(duration_minutes), 0), coalesce(sum(calorie_burn_kcal), 0)
            FROM exercise_sessions
            WHERE date >= ? AND date <= ?
        """, (ex_7d_start, ex_7d_end))
        ex_7d_stats = cur.fetchone()

        cur.execute("""
            SELECT count(*)
            FROM exercise_sessions
            WHERE date >= ? AND date <= ?
              AND (exercise_name LIKE '%eGym%' OR exercise_name = 'WEIGHT_MACHINE' OR exercise_name = 'CIRCUIT_TRAINING')
        """, (ex_7d_start, ex_7d_end))
        res_7d_count = cur.fetchone()[0]

        # Recent 15 exercise sessions with intra-workout HR samples
        cur.execute("""
            SELECT id, exercise_name, exercise_type, start_time, end_time, date,
                   duration_minutes, calorie_burn_kcal, mean_hr_bpm, max_hr_bpm, notes
            FROM exercise_sessions
            ORDER BY start_time DESC
            LIMIT 35
        """)
        raw_sessions = cur.fetchall()

        recent_sessions = []
        for s in raw_sessions:
            s_id, s_name, s_type, s_start, s_end, s_date, s_dur, s_cal, s_mean_hr, s_max_hr, s_notes = s

            # Fetch intra-workout HR samples (tagged or time-matched)
            cur.execute("""
                SELECT timestamp, bpm
                FROM heart_rate_samples
                WHERE exercise_id = ?
                ORDER BY timestamp ASC
            """, (s_id,))
            hr_rows = cur.fetchall()

            if not hr_rows and s_start and s_end:
                cur.execute("""
                    SELECT timestamp, bpm
                    FROM heart_rate_samples
                    WHERE timestamp >= ? AND timestamp <= ?
                    ORDER BY timestamp ASC
                """, (s_start, s_end))
                hr_rows = cur.fetchall()

            hr_curve = []
            hr_zones = {"zone1_warmup": 0.0, "zone2_aerobic": 0.0, "zone3_tempo": 0.0, "zone4_threshold": 0.0, "zone5_peak": 0.0}
            if hr_rows and s_start:
                s_start_dt = pd.to_datetime(s_start, utc=True)
                stride = max(1, len(hr_rows) // 60)
                sampled_hr_rows = hr_rows[::stride]
                for r in sampled_hr_rows:
                    r_dt = pd.to_datetime(r[0], utc=True)
                    offset_m = round((r_dt - s_start_dt).total_seconds() / 60.0, 1)
                    bpm_val = float(r[1]) if r[1] is not None else 0.0
                    hr_curve.append({"offset_m": max(0.0, offset_m), "bpm": bpm_val})

                z1 = z2 = z3 = z4 = z5 = 0
                for r in hr_rows:
                    b = float(r[1]) if r[1] is not None else 0.0
                    if b < 100:
                        z1 += 1
                    elif b <= 118:
                        z2 += 1
                    elif b <= 134:
                        z3 += 1
                    elif b <= 151:
                        z4 += 1
                    else:
                        z5 += 1

                total_samples = len(hr_rows)
                if total_samples > 0:
                    hr_zones = {
                        "zone1_warmup": round((z1 / total_samples) * 100.0, 1),
                        "zone2_aerobic": round((z2 / total_samples) * 100.0, 1),
                        "zone3_tempo": round((z3 / total_samples) * 100.0, 1),
                        "zone4_threshold": round((z4 / total_samples) * 100.0, 1),
                        "zone5_peak": round((z5 / total_samples) * 100.0, 1),
                    }

            # Check for vagal recovery drop (HRR-60)
            cur.execute("SELECT duration_seconds, point_count FROM exercise_recovery WHERE exercise_id = ?", (s_id,))
            rec_row = cur.fetchone()
            hrr_meta = {"duration_s": rec_row[0], "points": rec_row[1]} if rec_row else None

            # Query attached eGym machines and multi-set loads
            egym_machines = []
            try:
                cur.execute("""
                    SELECT exercise_name, mode, set_number, reps, load_kg, peak_load_kg, est_energy_exp_kcal
                    FROM egym_workouts
                    WHERE session_id = ? OR (session_id IS NULL AND date = ?)
                    ORDER BY exercise_name, set_number
                """, (s_id, s_date))
                egym_rows = cur.fetchall()
                if egym_rows:
                    machines_dict = {}
                    for er in egym_rows:
                        m_name = er[0]
                        if m_name not in machines_dict:
                            machines_dict[m_name] = {
                                "machine_name": m_name,
                                "mode": er[1],
                                "sets": [],
                                "peak_load_kg": er[5],
                                "est_energy_exp_kcal": er[6]
                            }
                        machines_dict[m_name]["sets"].append({
                            "set_number": er[2],
                            "reps": er[3],
                            "load_kg": er[4]
                        })
                        if er[5] and (machines_dict[m_name]["peak_load_kg"] is None or er[5] > machines_dict[m_name]["peak_load_kg"]):
                            machines_dict[m_name]["peak_load_kg"] = er[5]
                    egym_machines = list(machines_dict.values())
            except Exception:
                pass

            recent_sessions.append({
                "id": s_id,
                "name": s_name,
                "type": s_type,
                "date": s_date,
                "start_time": s_start,
                "end_time": s_end,
                "duration_m": round(s_dur, 1) if s_dur is not None else None,
                "calories": round(s_cal, 1) if s_cal is not None else None,
                "mean_hr": round(s_mean_hr, 1) if s_mean_hr is not None else None,
                "max_hr": round(s_max_hr, 1) if s_max_hr is not None else None,
                "notes": s_notes,
                "hr_curve": hr_curve,
                "hr_zones_pct": hr_zones,
                "hrr_meta": hrr_meta,
                "egym_exercises": egym_machines
            })

        # Integrate standalone eGym sessions (historical benchmark circuits)
        logged_dates = {s["date"] for s in recent_sessions}
        try:
            cur.execute("""
                SELECT DISTINCT date, session_id, mode, raw_scan_id, notes
                FROM egym_workouts
                ORDER BY date DESC
            """)
            for e_sess in cur.fetchall():
                e_date, e_sid, e_mode, e_scan_id, e_notes = e_sess
                if e_date not in logged_dates:
                    cur.execute("""
                        SELECT exercise_name, mode, set_number, reps, load_kg, peak_load_kg, est_energy_exp_kcal
                        FROM egym_workouts
                        WHERE date = ?
                        ORDER BY exercise_name, set_number
                    """, (e_date,))
                    egym_rows = cur.fetchall()
                    machines_dict = {}
                    for er in egym_rows:
                        m_name = er[0]
                        if m_name not in machines_dict:
                            machines_dict[m_name] = {
                                "machine_name": m_name,
                                "mode": er[1] or e_mode,
                                "sets": [],
                                "peak_load_kg": er[5],
                                "est_energy_exp_kcal": er[6]
                            }
                        machines_dict[m_name]["sets"].append({
                            "set_number": er[2],
                            "reps": er[3],
                            "load_kg": er[4]
                        })
                        if er[5] and (machines_dict[m_name]["peak_load_kg"] is None or er[5] > machines_dict[m_name]["peak_load_kg"]):
                            machines_dict[m_name]["peak_load_kg"] = er[5]

                    recent_sessions.append({
                        "id": e_sid or f"egym_{e_date}",
                        "name": "eGym Circuit Workout",
                        "type": "RESISTANCE",
                        "date": e_date,
                        "start_time": f"{e_date}T17:30:00+00:00",
                        "end_time": f"{e_date}T18:15:00+00:00",
                        "duration_m": 45.0,
                        "calories": None,
                        "mean_hr": None,
                        "max_hr": None,
                        "notes": e_notes or "eGym circuit at the gym",
                        "hr_curve": [],
                        "hr_zones_pct": {"zone1_warmup": 0.0, "zone2_aerobic": 0.0, "zone3_tempo": 0.0, "zone4_threshold": 0.0, "zone5_peak": 0.0},
                        "hrr_meta": None,
                        "egym_exercises": list(machines_dict.values())
                    })
                    logged_dates.add(e_date)
            recent_sessions.sort(key=lambda s: s.get("start_time") or s["date"], reverse=True)
        except Exception:
            pass

        # Query eGym BioAge history
        egym_bioage_history = []
        try:
            cur.execute("SELECT date, strength_bioage, metabolic_bioage, chronological_age FROM egym_bioage ORDER BY date ASC")
            for b_row in cur.fetchall():
                egym_bioage_history.append({
                    "date": b_row[0],
                    "strength_bioage": b_row[1],
                    "metabolic_bioage": b_row[2],
                    "chronological_age": b_row[3]
                })
        except Exception:
            pass

        # Query latest eGym Muscle Balance
        latest_muscle_balance = None
        try:
            cur.execute("""
                SELECT date, upper_body_status, upper_body_recommendation,
                       core_status, core_recommendation, lower_body_status, lower_body_recommendation
                FROM egym_muscle_balance ORDER BY date DESC LIMIT 1
            """)
            mb_row = cur.fetchone()
            if mb_row:
                latest_muscle_balance = {
                    "date": mb_row[0],
                    "upper_body": {"status": mb_row[1], "recommendation": mb_row[2]},
                    "core": {"status": mb_row[3], "recommendation": mb_row[4]},
                    "lower_body": {"status": mb_row[5], "recommendation": mb_row[6]}
                }
        except Exception:
            pass

        target_cad = active_protocol.get("training_program", {}).get("target_cadence", 4) if active_protocol else 4
        del_week = active_protocol.get("training_program", {}).get("deload_week", 6) if active_protocol else 6
        train_phase = phase_info.get("name", "Phase 1: Metabolic Conditioning")
        train_mode = phase_info.get("current_mode", "Standard Mode")
        cur_week = phase_info.get("current_week", 2)

        exercise_data = {
            "cadence_7d": {
                "start_date": ex_7d_start,
                "end_date": ex_7d_end,
                "total_sessions": ex_7d_stats[0],
                "resistance_sessions": res_7d_count,
                "target_sessions": target_cad,
                "total_duration_m": round(ex_7d_stats[1], 1),
                "total_calories": round(ex_7d_stats[2], 1),
                "training_phase": train_phase,
                "mode": train_mode,
                "deload_due_week": del_week,
                "current_week": cur_week
            },
            "recent_sessions": recent_sessions,
            "bioage_history": egym_bioage_history,
            "muscle_balance": latest_muscle_balance
        }

        # 7-Day Protocol Adherence Matrix (ADR-001)
        latest_date_dt = pd.to_datetime(df_weight.iloc[-1]["date"])
        adherence_dates = [(latest_date_dt - timedelta(days=i)).strftime("%Y-%m-%d") for i in range(6, -1, -1)]

        cur.execute("""
            SELECT date, intervention_id, event_type, divergence_code, precision, confidence, notes
            FROM intervention_events
            WHERE date >= ? AND date <= ?
        """, (adherence_dates[0], adherence_dates[-1]))
        logged_events = cur.fetchall()
        logged_map = {(r[0], r[1]): {"event_type": r[2], "divergence": r[3], "precision": r[4], "confidence": r[5], "notes": r[6]} for r in logged_events}

        if active_protocol and active_protocol.get("compounds"):
            matrix_compounds = [
                {
                    "id": c["id"],
                    "name": c["name"],
                    "freq": "q72h" if c.get("frequency_hours") == 72 else ("Daily" if c.get("frequency_hours") == 24 else (c.get("cadence") or "as_needed"))
                }
                for c in active_protocol["compounds"]
            ]
        else:
            matrix_compounds = [
                {"id": "creatine", "name": "Creatine Monohydrate (5g)", "freq": "Daily"},
                {"id": "omega3", "name": "Omega-3 EPA/DHA (2000mg)", "freq": "Daily"},
                {"id": "vit_d3", "name": "Vitamin D3 + K2 (5000 IU)", "freq": "Daily"},
                {"id": "magnesium", "name": "Magnesium Bisglycinate (400mg)", "freq": "Daily"},
                {"id": "protein", "name": "Whey Isolate Shake (40g)", "freq": "Daily"}
            ]

        adherence_rows = []
        for comp in matrix_compounds:
            row_days = []
            for d in adherence_dates:
                event = logged_map.get((d, comp["id"]))
                if event:
                    row_days.append({
                        "date": d,
                        "status": "done" if event["divergence"] in ("adherent", "late") else "skipped",
                        "divergence": event["divergence"],
                        "confidence": event["confidence"]
                    })
                else:
                    # No intervention_events row for this compound-day. Absence
                    # must not read as compliance (AGENTS.md §3.2) -- export a
                    # status distinct from done/skipped/rest so the UI renders it
                    # as explicitly not logged, never as taken.
                    row_days.append({"date": d, "status": "unlogged", "divergence": None})
            adherence_rows.append({
                "compound_id": comp["id"],
                "compound_name": comp["name"],
                "frequency": comp["freq"],
                "days": row_days
            })

        adherence_matrix = {
            "dates": adherence_dates,
            "day_labels": [pd.to_datetime(d).strftime("%a %d") for d in adherence_dates],
            "rows": adherence_rows
        }

        # HUD dose timers: derived strictly from the latest logged
        # intervention_events row per compound. No logged dose for a compound =>
        # null, never a fabricated countdown (AGENTS.md §3.2). "Now" is anchored
        # to the newest real measurement in the DB (data_current_through), not
        # wall-clock time, per ADR-013.
        data_current_through = latest_data_timestamp(cur)
        now_ref = pd.to_datetime(data_current_through, utc=True, errors="coerce") if data_current_through else None

        def _latest_dose_ts(intervention_id):
            cur.execute("""
                SELECT timestamp FROM intervention_events
                WHERE intervention_id = ? AND event_type = 'dose_taken'
                ORDER BY timestamp DESC LIMIT 1
            """, (intervention_id,))
            row = cur.fetchone()
            return row[0] if row and row[0] else None

        def _hours_remaining_in_cycle(intervention_id, cycle_hours):
            last_ts = _latest_dose_ts(intervention_id)
            if not last_ts or now_ref is None:
                return None
            last_dt = pd.to_datetime(last_ts, utc=True, errors="coerce")
            if pd.isnull(last_dt):
                return None
            elapsed_h = (now_ref - last_dt).total_seconds() / 3600.0
            return round(cycle_hours - elapsed_h, 1)

        def _days_since_dose(intervention_id):
            last_ts = _latest_dose_ts(intervention_id)
            if not last_ts or now_ref is None:
                return None
            last_dt = pd.to_datetime(last_ts, utc=True, errors="coerce")
            if pd.isnull(last_dt):
                return None
            return round((now_ref - last_dt).total_seconds() / 86400.0, 1)

        hud_timers = {
            "hours_remaining": _hours_remaining_in_cycle("act-cadence", 72),
            "interval_days": _days_since_dose("act-interval"),
            "saturation_window_active": bool(phase_info["current_week"] <= 4)
        }

        # -------------------------------------------------------------------------
        # 7. Longitudinal Bloodwork & Safety Radar (Milestone 12, ADR-022)
        # -------------------------------------------------------------------------
        bloodwork_data = None
        cur.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='lab_results'")
        if cur.fetchone():
            cur.execute("SELECT MAX(draw_date) FROM lab_results")
            latest_draw_row = cur.fetchone()
            latest_draw_date = latest_draw_row[0] if latest_draw_row and latest_draw_row[0] else None

            if latest_draw_date:
                cur.execute("SELECT DISTINCT draw_date FROM lab_results ORDER BY draw_date ASC")
                draw_dates = [r[0] for r in cur.fetchall()]

                days_since_last_draw = None
                if data_anchor_date and latest_draw_date:
                    try:
                        dt_anchor = datetime.strptime(data_anchor_date, "%Y-%m-%d")
                        dt_draw = datetime.strptime(latest_draw_date, "%Y-%m-%d")
                        days_since_last_draw = (dt_anchor - dt_draw).days
                    except Exception:
                        days_since_last_draw = None

                mandatory_panel = active_protocol.get("diagnostic_panel", {}).get("mandatory_markers") if active_protocol else None
                if not mandatory_panel:
                    mandatory_panel = [
                        "Fasting Glucose", "HbA1c", "ApoB", "hsCRP",
                        "Haematocrit", "Haemoglobin", "ALT", "AST", "eGFR", "Total Cholesterol", "Creatinine", "Vitamin D"
                    ]
                draw_readiness = {
                    "mandatory_panel": mandatory_panel,
                    "baseline_confirmed": True,
                    "assay_methods_confirmed": True,
                    "days_since_last_draw": days_since_last_draw
                }

                def _calc_is_oor(r):
                    sv = r.get("source_value")
                    if sv is None:
                        sv = r.get("value")
                    if sv is None:
                        return False
                    try:
                        num = float(sv)
                    except (ValueError, TypeError):
                        return False
                    lo = r.get("ref_low_reported")
                    hi = r.get("ref_high_reported")
                    comp = r.get("comparator")
                    if lo is not None and num < lo:
                        return True
                    if hi is not None and num > hi:
                        return True
                    if comp == "<" and lo is not None and num <= lo:
                        return True
                    if comp == ">" and hi is not None and num >= hi:
                        return True
                    return False

                cur.execute("""
                    SELECT draw_date, analyte_code, analyte_name, category, value, comparator,
                           unit_canonical, source_value, source_unit, value_br, unit_br,
                           ref_low_reported, ref_high_reported, lab_provider
                    FROM lab_results
                    ORDER BY category ASC, analyte_code ASC, draw_date ASC
                """)
                cols = [col[0] for col in cur.description]
                rows = [dict(zip(cols, r)) for r in cur.fetchall()]

                from collections import defaultdict
                grouped = defaultdict(lambda: defaultdict(list))
                for r in rows:
                    grouped[r["category"]][r["analyte_code"]].append(r)

                categories = []
                brief_analytes = []

                for cat in sorted(grouped.keys()):
                    analytes_list = []
                    for code in sorted(grouped[cat].keys()):
                        history_rows = grouped[cat][code]
                        latest_r = history_rows[-1]

                        history = [
                            {
                                "date": hr["draw_date"],
                                "value": hr["value"],
                                "comparator": hr["comparator"],
                                "value_br": hr["value_br"],
                                "ref_low": hr["ref_low_reported"],
                                "ref_high": hr["ref_high_reported"],
                                "lab_provider": hr["lab_provider"]
                            }
                            for hr in history_rows
                        ]

                        is_oor = _calc_is_oor(latest_r)

                        analyte_obj = {
                            "analyte_code": code,
                            "analyte_name": latest_r["analyte_name"],
                            "canonical_unit": latest_r["unit_canonical"],
                            "br_unit": latest_r["unit_br"],
                            "latest_value": latest_r["value"],
                            "latest_comparator": latest_r["comparator"],
                            "latest_draw_date": latest_r["draw_date"],
                            "ref_low_reported": latest_r["ref_low_reported"],
                            "ref_high_reported": latest_r["ref_high_reported"],
                            "is_out_of_range": is_oor,
                            "history": history
                        }
                        analytes_list.append(analyte_obj)

                        canonical_vals = [hr["value"] for hr in history_rows if hr["value"] is not None]
                        br_vals = [hr["value_br"] for hr in history_rows if hr["value_br"] is not None]

                        last_5 = [
                            {
                                "date": hr["draw_date"],
                                "value": hr["value"],
                                "comparator": hr["comparator"],
                                "value_br": hr["value_br"],
                                "ref_low": hr["ref_low_reported"],
                                "ref_high": hr["ref_high_reported"],
                                "is_out_of_range": _calc_is_oor(hr),
                                "lab_provider": hr["lab_provider"]
                            }
                            for hr in history_rows[-5:]
                        ]

                        brief_analytes.append({
                            "analyte_code": code,
                            "analyte_name": latest_r["analyte_name"],
                            "category": cat,
                            "canonical_unit": latest_r["unit_canonical"],
                            "br_unit": latest_r["unit_br"],
                            "latest_value": latest_r["value"],
                            "latest_value_br": latest_r["value_br"],
                            "latest_comparator": latest_r["comparator"],
                            "latest_draw_date": latest_r["draw_date"],
                            "ref_low_reported": latest_r["ref_low_reported"],
                            "ref_high_reported": latest_r["ref_high_reported"],
                            "latest_out_of_range": is_oor,
                            "min_canonical": min(canonical_vals) if canonical_vals else None,
                            "max_canonical": max(canonical_vals) if canonical_vals else None,
                            "min_br": min(br_vals) if br_vals else None,
                            "max_br": max(br_vals) if br_vals else None,
                            "total_draws": len(history_rows),
                            "last_5_draws": last_5
                        })

                    categories.append({
                        "category": cat,
                        "analytes": analytes_list
                    })

                jan_dates = ["2026-01-05", "2026-01-09", "2026-01-16", "2026-01-19", "2026-01-26", "2026-01-29"]
                jan_titration = []
                for jd in jan_dates:
                    cur.execute("SELECT analyte_code, value, value_br, unit_canonical, unit_br FROM lab_results WHERE draw_date = ?", (jd,))
                    jd_recs = {r[0]: r for r in cur.fetchall()}

                    tt = jd_recs.get("testosterone", (None, None, None))[1]
                    tt_br = jd_recs.get("testosterone", (None, None, None))[2]
                    ft = jd_recs.get("free_testosterone_calc", (None, None, None))[1]
                    ft_br = jd_recs.get("free_testosterone_calc", (None, None, None))[2]
                    shbg = jd_recs.get("shbg", (None, None, None))[1]
                    shbg_br = jd_recs.get("shbg", (None, None, None))[2]
                    e2 = jd_recs.get("oestradiol", (None, None, None))[1]
                    e2_br = jd_recs.get("oestradiol", (None, None, None))[2]

                    ratio_molar = round(tt * 1000.0 / e2, 1) if (tt is not None and e2 is not None and e2 > 0) else None
                    ratio_mass = round(tt_br / e2_br, 1) if (tt_br is not None and e2_br is not None and e2_br > 0) else None

                    jan_titration.append({
                        "date": jd,
                        "total_t": tt,
                        "total_t_unit": "nmol/L",
                        "free_t": ft,
                        "free_t_unit": "nmol/L",
                        "shbg": shbg,
                        "shbg_unit": "nmol/L",
                        "e2": e2,
                        "e2_unit": "pmol/L",
                        "t_e2_ratio": ratio_molar,
                        "total_t_br": tt_br,
                        "total_t_br_unit": "ng/dL",
                        "free_t_br": ft_br,
                        "free_t_br_unit": "ng/dL",
                        "shbg_br": shbg_br,
                        "shbg_br_unit": "nmol/L",
                        "e2_br": e2_br,
                        "e2_br_unit": "pg/mL",
                        "t_e2_ratio_mass": ratio_mass
                    })

                consultation_brief = {
                    "analytes": brief_analytes,
                    "latest_out_of_range": [a for a in brief_analytes if a["latest_out_of_range"]],
                    "total_analytes": len(brief_analytes),
                    "out_of_range_count": len([a for a in brief_analytes if a["latest_out_of_range"]])
                }

                bloodwork_data = {
                    "latest_draw_date": latest_draw_date,
                    "draw_dates": draw_dates,
                    "draw_readiness": draw_readiness,
                    "categories": categories,
                    "jan_2026_titration": jan_titration,
                    "consultation_brief": consultation_brief
                }

        # Telemetry Freshness Metadata (ADR-029) - 100% Deterministic Fact Anchors
        try:
            cur.execute("SELECT MAX(last_synced_at), device_id FROM sdk_sync_state")
            sync_row = cur.fetchone()
            last_sdk_sync = sync_row[0] if sync_row else None
            sdk_device = sync_row[1] if sync_row else None
        except Exception:
            last_sdk_sync = None
            sdk_device = None

        try:
            cur.execute("SELECT MAX(timestamp) FROM withings_readings")
            w_row = cur.fetchone()
            last_scale_sync = w_row[0] if w_row else None
        except Exception:
            last_scale_sync = None

        telemetry_freshness = {
            "sensor_current_through": data_current_through,
            "last_sdk_sync": last_sdk_sync,
            "sdk_device": sdk_device,
            "last_scale_sync": last_scale_sync
        }

        # Staged Scans Queue Status (ADR-030)
        staged_scans_summary = {"pending_count": 0, "scans": []}
        try:
            import reconcile_scans
            staged_all = reconcile_scans.get_staged_scans()
            pending_items = [s for s in staged_all if s.get("status") in ("staged", "ready_for_review", "staged_pending_review")]
            staged_scans_summary = {
                "pending_count": len(pending_items),
                "total_count": len(staged_all),
                "scans": [
                    {
                        "id": s.get("id"),
                        "date": (s.get("timestamp") or "")[:10],
                        "filename": s.get("filename"),
                        "notes": s.get("notes"),
                        "status": s.get("status", "staged")
                    }
                    for s in pending_items
                ]
            }
        except Exception:
            pass

        cur.execute("PRAGMA user_version;")
        uv_row = cur.fetchone()
        schema_version = uv_row[0] if uv_row else 11

        # Assemble Payload
        payload = {
            "system_version": SYSTEM_VERSION,
            "schema_version": schema_version,
            "data_current_through": data_current_through,
            "user_profile": user_profile,
            "hud": {
                "phase": phase_info,
                "e2_alert": e2_alert,
                "data_health": data_health,
                "adherence_matrix": adherence_matrix,
                "timers": hud_timers,
                "telemetry_freshness": telemetry_freshness
            },
            "body_comp": {
                "history": body_comp_history,
                "stats": bc_stats,
                "inbody_benchmarks": inbody_benchmarks
            },
            "nutrition": nutrition_data,
            "autonomic": {
                "series": autonomic_series,
                "latest_hrr_60": hrr_points,
                "latest_hrr_meta": {"date": latest_rec[1], "duration_s": latest_rec[2]} if latest_rec else None
            },
            "sleep": sleep_data,
            "exercise": exercise_data,
            "life_eras": life_eras,
            "life_events": life_events,
            "interventions_catalog": catalog,
            "protocols": all_protocols,
            "active_protocol": active_protocol,
            "bloodwork": bloodwork_data,
            "staged_scans": staged_scans_summary
        }

        tmp_json = OUTPUT_JSON + ".tmp"
        with open(tmp_json, "w") as f:
            json.dump(payload, f, indent=2)
        os.replace(tmp_json, OUTPUT_JSON)

        # In addition, inject data into index.html for instant file:// & offline usage
        if INDEX_HTML_PATH and os.path.exists(INDEX_HTML_PATH):
            index_html_path = INDEX_HTML_PATH
            try:
                with open(index_html_path, "r", encoding="utf-8") as f:
                    html_content = f.read()

                import re
                json_str = json.dumps(payload)
                pattern = r'<script id="injected-dashboard-data">.*?</script>'
                new_html = re.sub(
                    pattern,
                    lambda m: f'<script id="injected-dashboard-data">\n    window.__DASHBOARD_DATA__ = {json_str};\n  </script>',
                    html_content,
                    flags=re.DOTALL
                )

                # Skip the write when the rendered content is byte-identical to
                # what's already on disk (A26). index.html is ~936 KB; without
                # this guard it gets rewritten on every export even when the
                # underlying data hasn't changed, which dirties a tracked file
                # for nothing. Comparing the full rendered HTML (not just the
                # JSON payload) also covers a template-only change with no data
                # change.
                if new_html == html_content:
                    print(f"[SKIP] {index_html_path} unchanged; no write needed")
                else:
                    tmp_html = index_html_path + ".tmp"
                    with open(tmp_html, "w", encoding="utf-8") as f:
                        f.write(new_html)
                    os.replace(tmp_html, index_html_path)
                    print(f"[SUCCESS] Injected payload directly into {index_html_path}")
            except Exception as e:
                print(f"[NOTICE] Could not inject into index.html: {e}")

        print(f"[SUCCESS] Exported Stage A dashboard payload to {OUTPUT_JSON}")
    finally:
        conn.close()


def export_data():
    """Thread-safe entry point for Stage A dashboard data export (H5)."""
    with EXPORT_LOCK:
        return _export_data_unlocked()


if __name__ == "__main__":
    export_data()

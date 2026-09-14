#!/usr/bin/env python3
"""
scripts/aggregation_engine.py

Unified Temporal Aggregation & Physiological Enrichment Engine (ADR-028, ADR-031).
Single canonical source of truth for:
1. Interval Tagging:
   - tag_samples_with_exercise(): Links heart_rate_samples to exercise_sessions.id
   - tag_samples_with_sleep(): Links heart_rate_samples to sleep_sessions.id
2. Nocturnal Cardiac Recovery Enrichment:
   - enrich_sleep_sessions_with_hr(): Computes sleeping_hr_mean, sleeping_hr_min,
     sleeping_hr_max, sleeping_hr_nadir, nocturnal_dip_pct, and sample counts.
3. Master Daily Summary Rollup:
   - rebuild_daily_summary(): Canonical daily multi-stream rollup supporting full
     rebuild and incremental date-filtered recalculation.
4. Workout Cardiac Metrics:
   - compute_workout_cardiac_metrics(): Normalized 60-point cardiac curves and 5-zone distributions.
5. Cross-Stream Scale Fusion:
   - compute_diurnal_offset(): InBody weight vs. morning fasted Withings reading.

100% Python standard library + sqlite3. Zero external dependencies.
"""

import os
import sys
import math
import bisect
import sqlite3
from typing import Dict, Any, List, Optional, Tuple, Set

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DB_PATH = os.path.join(BASE_DIR, "data", "health_dashboard.db")


# -----------------------------------------------------------------------------
# 1. INTERVAL TAGGING (EXERCISE & SLEEP)
# -----------------------------------------------------------------------------

def tag_samples_with_exercise(
    conn: sqlite3.Connection,
    target_session_ids: Optional[List[str]] = None,
    target_dates: Optional[List[str]] = None
) -> int:
    """
    Links heart_rate_samples to exercise_sessions via indexed SQL interval matching.
    Leverages idx_hr_samples_ts (timestamp range scan) per session for sub-second execution.
    """
    cur = conn.cursor()
    if target_session_ids:
        placeholders = ",".join("?" * len(target_session_ids))
        sessions = cur.execute(f"""
            SELECT id, start_time, end_time FROM exercise_sessions
            WHERE id IN ({placeholders}) AND start_time IS NOT NULL AND end_time IS NOT NULL AND end_time > start_time
            ORDER BY start_time ASC
        """, target_session_ids).fetchall()
    elif target_dates:
        placeholders = ",".join("?" * len(target_dates))
        sessions = cur.execute(f"""
            SELECT id, start_time, end_time FROM exercise_sessions
            WHERE (date IN ({placeholders}) OR local_date IN ({placeholders}))
              AND start_time IS NOT NULL AND end_time IS NOT NULL AND end_time > start_time
            ORDER BY start_time ASC
        """, target_dates * 2).fetchall()
    else:
        sessions = cur.execute("""
            SELECT id, start_time, end_time FROM exercise_sessions
            WHERE start_time IS NOT NULL AND end_time IS NOT NULL AND end_time > start_time
            ORDER BY start_time ASC
        """).fetchall()

    if not sessions:
        return 0

    total_tagged = 0
    for sid, st, et in sessions:
        cur.execute("""
            UPDATE heart_rate_samples
            SET exercise_id = ?
            WHERE timestamp >= ? AND timestamp <= ?
        """, (sid, st, et))
        total_tagged += cur.rowcount

    conn.commit()
    return total_tagged


def tag_samples_with_sleep(
    conn: sqlite3.Connection,
    target_sleep_ids: Optional[List[str]] = None,
    target_dates: Optional[List[str]] = None
) -> int:
    """
    Links heart_rate_samples to sleep_sessions via indexed SQL interval matching.
    Stamps heart_rate_samples.sleep_id = sleep_sessions.id.
    """
    cur = conn.cursor()
    if target_sleep_ids:
        placeholders = ",".join("?" * len(target_sleep_ids))
        sessions = cur.execute(f"""
            SELECT id, start_time, end_time FROM sleep_sessions
            WHERE id IN ({placeholders}) AND start_time IS NOT NULL AND end_time IS NOT NULL AND end_time > start_time
            ORDER BY start_time ASC
        """, target_sleep_ids).fetchall()
    elif target_dates:
        placeholders = ",".join("?" * len(target_dates))
        sessions = cur.execute(f"""
            SELECT id, start_time, end_time FROM sleep_sessions
            WHERE (date IN ({placeholders}) OR local_date IN ({placeholders}))
              AND start_time IS NOT NULL AND end_time IS NOT NULL AND end_time > start_time
            ORDER BY start_time ASC
        """, target_dates * 2).fetchall()
    else:
        sessions = cur.execute("""
            SELECT id, start_time, end_time FROM sleep_sessions
            WHERE start_time IS NOT NULL AND end_time IS NOT NULL AND end_time > start_time
            ORDER BY start_time ASC
        """).fetchall()

    if not sessions:
        return 0

    total_tagged = 0
    for sid, st, et in sessions:
        cur.execute("""
            UPDATE heart_rate_samples
            SET sleep_id = ?
            WHERE timestamp >= ? AND timestamp <= ?
        """, (sid, st, et))
        total_tagged += cur.rowcount

    conn.commit()
    return total_tagged


# -----------------------------------------------------------------------------
# 2. NOCTURNAL CARDIAC RECOVERY ENRICHMENT
# -----------------------------------------------------------------------------

def enrich_sleep_sessions_with_hr(conn: sqlite3.Connection, target_wake_dates: Optional[List[str]] = None) -> int:
    """
    Calculates nocturnal HR metrics for sleep sessions with tagged heart_rate_samples:
    - sleeping_hr_mean: average HR strictly while asleep
    - sleeping_hr_min: nocturnal nadir
    - sleeping_hr_max: peak HR during sleep
    - sleeping_hr_nadir: robust nadir (lowest 3-sample rolling average)
    - nocturnal_dip_pct: % drop from waking daytime average HR to sleeping HR mean
    - sleeping_hr_samples_n: sample count denominator

    Updates sleep_sessions table directly (Schema v12).
    """
    cur = conn.cursor()

    # Verify that sleep_sessions has v12 columns
    cur.execute("PRAGMA table_info(sleep_sessions)")
    cols = {r[1] for r in cur.fetchall()}
    if "sleeping_hr_mean" not in cols:
        # v12 columns not yet added; skip gracefully
        return 0

    if target_wake_dates:
        placeholders = ",".join("?" * len(target_wake_dates))
        cur.execute(f"""
            SELECT id, wake_date, local_wake_date, start_time, end_time
            FROM sleep_sessions
            WHERE (wake_date IN ({placeholders}) OR local_wake_date IN ({placeholders}))
              AND session_role = 'main_sleep'
        """, target_wake_dates + target_wake_dates)
    else:
        cur.execute("""
            SELECT id, wake_date, local_wake_date, start_time, end_time
            FROM sleep_sessions
            WHERE session_role = 'main_sleep'
        """)

    sessions = cur.fetchall()
    if not sessions:
        return 0

    enriched_count = 0
    updates = []

    for s_id, wake_date, local_wake_date, start_time, end_time in sessions:
        effective_date = local_wake_date or wake_date

        # Fetch HR samples during this sleep session
        cur.execute("""
            SELECT bpm FROM heart_rate_samples
            WHERE sleep_id = ?
            ORDER BY timestamp ASC
        """, (s_id,))
        bpms = [float(r[0]) for r in cur.fetchall() if r[0] is not None]

        if not bpms:
            # Fallback to timestamp range if sleep_id not yet tagged
            cur.execute("""
                SELECT bpm FROM heart_rate_samples
                WHERE timestamp >= ? AND timestamp <= ?
                ORDER BY timestamp ASC
            """, (start_time, end_time))
            bpms = [float(r[0]) for r in cur.fetchall() if r[0] is not None]

        if not bpms:
            continue

        n_samples = len(bpms)
        hr_mean = round(sum(bpms) / n_samples, 1)
        hr_min = round(min(bpms), 1)
        hr_max = round(max(bpms), 1)

        # Robust nadir: lowest 3-sample rolling average (or min if < 3 samples)
        if n_samples >= 3:
            hr_nadir = round(min((bpms[i] + bpms[i+1] + bpms[i+2]) / 3.0 for i in range(n_samples - 2)), 1)
        else:
            hr_nadir = hr_min

        # Compute daytime waking HR for this date (samples on effective_date where sleep_id IS NULL)
        cur.execute("""
            SELECT AVG(bpm) FROM heart_rate_samples
            WHERE local_date = ? AND sleep_id IS NULL
        """, (effective_date,))
        day_row = cur.fetchone()
        day_mean = float(day_row[0]) if (day_row and day_row[0] is not None) else None

        # Calculate nocturnal dipping ratio: (Day Mean - Sleep Mean) / Day Mean * 100
        nocturnal_dip_pct = None
        if day_mean and day_mean > 0:
            dip = ((day_mean - hr_mean) / day_mean) * 100.0
            nocturnal_dip_pct = round(dip, 1)

        updates.append((hr_mean, hr_min, hr_max, hr_nadir, nocturnal_dip_pct, n_samples, s_id))
        enriched_count += 1

    for i in range(0, len(updates), 500):
        cur.executemany("""
            UPDATE sleep_sessions
            SET sleeping_hr_mean = ?,
                sleeping_hr_min = ?,
                sleeping_hr_max = ?,
                sleeping_hr_nadir = ?,
                nocturnal_dip_pct = ?,
                sleeping_hr_samples_n = ?
            WHERE id = ?
        """, updates[i:i + 500])

    conn.commit()
    return enriched_count


# -----------------------------------------------------------------------------
# 3. MASTER DAILY SUMMARY ROLLUP (CQRS ENRICHED PROJECTION)
# -----------------------------------------------------------------------------

def rebuild_daily_summary(conn: sqlite3.Connection, target_dates: Optional[Any] = None) -> int:
    """
    Canonical, unified daily summary rollup (F1-F10).
    Aggregates Withings scale, sleep stages, duration-weighted stress, HRV,
    workouts, and continuous HR by local lived calendar date.

    If target_dates is provided (and does not contain None), updates only those dates incrementally.
    Otherwise performs a full clean rebuild of daily_summary.
    """
    cur = conn.cursor()
    cur.execute("""
        CREATE TABLE IF NOT EXISTS daily_summary (
            date TEXT PRIMARY KEY,
            user_id TEXT DEFAULT 'USER_DEFAULT',
            weight_kg REAL,
            fat_ratio_pct REAL,
            muscle_mass_kg REAL,
            hydration_tbw_kg REAL,
            sleep_total_minutes REAL,
            sleep_deep_minutes REAL,
            sleep_rem_minutes REAL,
            sleep_light_minutes REAL,
            sleep_awake_minutes REAL,
            sleep_efficiency_pct REAL,
            sleep_score REAL,
            main_sleep_count INTEGER DEFAULT 0,
            nap_count INTEGER DEFAULT 0,
            fragment_count INTEGER DEFAULT 0,
            calories_kcal REAL,
            protein_g REAL,
            carbs_g REAL,
            fat_g REAL,
            nutrition_complete INTEGER DEFAULT 0,
            hr_avg_bpm REAL,
            hr_min_bpm REAL,
            hr_max_bpm REAL,
            hr_sample_count INTEGER DEFAULT 0,
            workout_count INTEGER DEFAULT 0,
            workout_minutes REAL DEFAULT 0,
            workout_kcal REAL DEFAULT 0,
            recovery_window_count INTEGER DEFAULT 0,
            stress_avg_score REAL,
            stress_sample_count INTEGER DEFAULT 0,
            hrv_avg_rmssd_ms REAL,
            hrv_avg_sdnn_ms REAL,
            hrv_sample_count INTEGER DEFAULT 0,
            has_weight INTEGER DEFAULT 0,
            has_sleep INTEGER DEFAULT 0,
            has_hr INTEGER DEFAULT 0,
            has_stress INTEGER DEFAULT 0,
            has_hrv INTEGER DEFAULT 0,
            rebuilt_at DATETIME DEFAULT CURRENT_TIMESTAMP
        )
    """)
    conn.commit()

    full_rebuild = target_dates is None or (isinstance(target_dates, (set, list, tuple)) and None in target_dates)

    date_filter = ""
    params: List[Any] = []
    if not full_rebuild:
        dates = sorted(str(d) for d in target_dates if d is not None)
        if not dates:
            return cur.execute("SELECT COUNT(*) FROM daily_summary").fetchone()[0]
        placeholders = ",".join("?" * len(dates))
        cur.execute(f"DELETE FROM daily_summary WHERE date IN ({placeholders})", dates)
        date_filter = f" AND d.date IN ({placeholders})"
        params = list(dates)
    else:
        cur.execute("DELETE FROM daily_summary")

    rollup_sql = f"""
        INSERT OR REPLACE INTO daily_summary (
            date,
            weight_kg, fat_ratio_pct, muscle_mass_kg, hydration_tbw_kg,
            sleep_total_minutes, sleep_deep_minutes, sleep_rem_minutes,
            sleep_light_minutes, sleep_awake_minutes, sleep_efficiency_pct, sleep_score,
            main_sleep_count, nap_count, fragment_count,
            calories_kcal, protein_g, carbs_g, fat_g, nutrition_complete,
            hr_avg_bpm, hr_min_bpm, hr_max_bpm, hr_sample_count,
            workout_count, workout_minutes, workout_kcal,
            recovery_window_count,
            stress_avg_score, stress_sample_count,
            hrv_avg_rmssd_ms, hrv_avg_sdnn_ms, hrv_sample_count,
            has_weight, has_sleep, has_hr, has_stress, has_hrv
        )
        SELECT d.date,
            w.weight_kg, w.fat_ratio_pct, w.muscle_mass_kg, w.hydration_tbw_kg,
            sl.tot, sl.deep, sl.rem, sl.light, sl.awake, sl.eff, sl.score,
            COALESCE(sl.mains, 0), COALESCE(sl.naps, 0), COALESCE(sl.frags, 0),
            n.calories_kcal, n.protein_g, n.carbs_g, n.fat_g, COALESCE(n.is_complete, 0),
            hr.avg_bpm, hr.min_bpm, hr.max_bpm, COALESCE(hr.n, 0),
            COALESCE(ex.n, 0), COALESCE(ex.mins, 0), COALESCE(ex.kcal, 0),
            COALESCE(rc.n, 0),
            st.avg_score, COALESCE(st.n, 0),
            hv.avg_rmssd, hv.avg_sdnn, COALESCE(hv.n, 0),
            CASE WHEN w.weight_kg IS NOT NULL THEN 1 ELSE 0 END,
            CASE WHEN sl.tot IS NOT NULL THEN 1 ELSE 0 END,
            CASE WHEN hr.n IS NOT NULL THEN 1 ELSE 0 END,
            CASE WHEN st.avg_score IS NOT NULL THEN 1 ELSE 0 END,
            CASE WHEN hv.avg_rmssd IS NOT NULL THEN 1 ELSE 0 END
        FROM (
            SELECT date FROM withings_readings
            UNION SELECT local_wake_date FROM sleep_sessions
            UNION SELECT date FROM daily_nutrition
            UNION SELECT local_date FROM exercise_sessions
            UNION SELECT local_date FROM heart_rate_samples
            UNION SELECT local_date FROM stress_samples
            UNION SELECT local_date FROM hrv_samples
        ) d
        LEFT JOIN (
            SELECT date, AVG(weight_kg) weight_kg, AVG(fat_ratio_pct) fat_ratio_pct,
                   AVG(muscle_mass_kg) muscle_mass_kg, AVG(hydration_tbw_kg) hydration_tbw_kg
            FROM withings_readings GROUP BY date
        ) w ON w.date = d.date
        LEFT JOIN (
            SELECT local_wake_date AS wake_date,
                SUM(CASE WHEN session_role='main_sleep' THEN total_sleep_minutes END) tot,
                SUM(CASE WHEN session_role='main_sleep' THEN deep_sleep_minutes END) deep,
                SUM(CASE WHEN session_role='main_sleep' THEN rem_sleep_minutes END) rem,
                SUM(CASE WHEN session_role='main_sleep' THEN light_sleep_minutes END) light,
                SUM(CASE WHEN session_role='main_sleep' THEN awake_minutes END) awake,
                AVG(CASE WHEN session_role='main_sleep' THEN efficiency_pct END) eff,
                MAX(CASE WHEN session_role='main_sleep' THEN sleep_score END) score,
                SUM(session_role='main_sleep') mains,
                SUM(session_role='nap') naps,
                SUM(session_role='fragment') frags
            FROM sleep_sessions GROUP BY local_wake_date
        ) sl ON sl.wake_date = d.date
        LEFT JOIN daily_nutrition n ON n.date = d.date
        LEFT JOIN (
            SELECT local_date AS date, ROUND(AVG(bpm),1) avg_bpm, MIN(min_bpm) min_bpm,
                   MAX(max_bpm) max_bpm, COUNT(*) n
            FROM heart_rate_samples GROUP BY local_date
        ) hr ON hr.date = d.date
        LEFT JOIN (
            SELECT local_date AS date, COUNT(*) n, ROUND(SUM(duration_minutes),1) mins,
                   ROUND(SUM(calorie_burn_kcal),0) kcal
            FROM exercise_sessions GROUP BY local_date
        ) ex ON ex.date = d.date
        LEFT JOIN (
            SELECT local_date AS date, COUNT(*) n FROM exercise_recovery WHERE has_data=1 GROUP BY local_date
        ) rc ON rc.date = d.date
        LEFT JOIN (
            -- Duration-weighted mean for stress samples (ADR-016 / A16)
            SELECT local_date AS date,
                   ROUND(
                       SUM(score * COALESCE(duration_seconds, 60))
                       / NULLIF(SUM(COALESCE(duration_seconds, 60)), 0)
                   , 1) avg_score,
                   COUNT(*) n
            FROM stress_samples GROUP BY local_date
        ) st ON st.date = d.date
        LEFT JOIN (
            SELECT local_date AS date, ROUND(AVG(rmssd_ms),2) avg_rmssd, ROUND(AVG(sdnn_ms),2) avg_sdnn, COUNT(*) n
            FROM hrv_samples GROUP BY local_date
        ) hv ON hv.date = d.date
        WHERE d.date IS NOT NULL AND d.date >= '2000-01-01'{date_filter}
    """

    cur.execute(rollup_sql, params)
    conn.commit()

    count = cur.execute("SELECT COUNT(*) FROM daily_summary").fetchone()[0]
    return count


# -----------------------------------------------------------------------------
# 4. WORKOUT CARDIAC METRICS & CROSS-STREAM ENRICHMENT
# -----------------------------------------------------------------------------

def compute_workout_cardiac_metrics(
    conn: sqlite3.Connection,
    session_id: str,
    start_time: Optional[str] = None,
    end_time: Optional[str] = None,
) -> Dict[str, Any]:
    """
    Computes 60-point intra-workout cardiac curve and 5 cardiac intensity zones
    for a given workout session. Binds attached eGym machine loads and recovery.
    """
    cur = conn.cursor()

    # Query HR samples by tagged exercise_id first
    cur.execute("""
        SELECT timestamp, bpm
        FROM heart_rate_samples
        WHERE exercise_id = ?
        ORDER BY timestamp ASC
    """, (session_id,))
    hr_rows = cur.fetchall()

    # Fallback to timestamp range if exercise_id not yet tagged
    if not hr_rows and start_time and end_time:
        cur.execute("""
            SELECT timestamp, bpm
            FROM heart_rate_samples
            WHERE timestamp >= ? AND timestamp <= ?
            ORDER BY timestamp ASC
        """, (start_time, end_time))
        hr_rows = cur.fetchall()

    hr_curve: List[Dict[str, float]] = []
    hr_zones = {
        "zone1_warmup": 0.0,
        "zone2_aerobic": 0.0,
        "zone3_tempo": 0.0,
        "zone4_threshold": 0.0,
        "zone5_peak": 0.0,
    }

    if hr_rows and start_time:
        import datetime
        try:
            s_start_dt = datetime.datetime.fromisoformat(start_time.replace("Z", "+00:00"))
        except Exception:
            s_start_dt = None

        stride = max(1, len(hr_rows) // 60)
        sampled_hr_rows = hr_rows[::stride]
        for r in sampled_hr_rows:
            offset_m = 0.0
            if s_start_dt:
                try:
                    r_dt = datetime.datetime.fromisoformat(r[0].replace("Z", "+00:00"))
                    offset_m = round((r_dt - s_start_dt).total_seconds() / 60.0, 1)
                except Exception:
                    offset_m = 0.0
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

    # Query vagal recovery drop (HRR-60)
    cur.execute("SELECT duration_seconds, point_count FROM exercise_recovery WHERE exercise_id = ?", (session_id,))
    rec_row = cur.fetchone()
    hrr_meta = {"duration_s": rec_row[0], "points": rec_row[1]} if rec_row else None

    # Query attached eGym machines
    cur.execute("""
        SELECT exercise_name, mode, set_number, reps, load_kg, peak_load_kg, est_energy_exp_kcal
        FROM egym_workouts
        WHERE session_id = ?
        ORDER BY exercise_name, set_number
    """, (session_id,))
    egym_rows = cur.fetchall()
    machines_dict: Dict[str, Any] = {}
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

    return {
        "hr_curve": hr_curve,
        "hr_zones_pct": hr_zones,
        "hrr_meta": hrr_meta,
        "egym_exercises": list(machines_dict.values())
    }


# -----------------------------------------------------------------------------
# 5. SCALE CROSS-STREAM FUSION (DIURNAL DELTA)
# -----------------------------------------------------------------------------

def compute_diurnal_offset(conn: sqlite3.Connection, date_str: str, inbody_weight_kg: float) -> Optional[float]:
    """
    Computes diurnal offset: InBody Weight - Morning Fasted Withings Weight.
    """
    cur = conn.cursor()
    cur.execute("""
        SELECT weight_kg FROM withings_readings
        WHERE date = ?
        ORDER BY timestamp ASC
        LIMIT 1
    """, (date_str,))
    row = cur.fetchone()
    if row and row[0] is not None:
        morning_weight = float(row[0])
        return round(inbody_weight_kg - morning_weight, 2)
    return None

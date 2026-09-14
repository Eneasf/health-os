#!/usr/bin/env python3
"""
Generate Mock Dashboard Data & Preview Shells (Zero Privacy Leakage)

Produces 100% synthetic, clinically plausible mock data for Health OS:
- Synthetic profile: "Alex Rivers", 38yo male, 178 cm, 78.4 kg
- Complete data across all 5 workspaces:
  1. Physical Adaptation (Body Comp, Nutrition, Exercise Triad)
  2. Recovery & Sleep (Autonomic Corridor, HRR-60, Sleep Architecture)
  3. Labs & Bloods (18-category matrix, ApoB/HCT optimal, doctor brief)
  4. History & Eras (Life events superimposition)
  5. Protocol Studio (Multi-vector regimen manifests)
- Generates dedicated light-mode HTML preview files for automated screenshot capture.
"""

import os
import re
import json
import math
from datetime import datetime, timedelta

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
INDEX_HTML = os.path.join(BASE_DIR, "dashboard", "index.html")
OUTPUT_DIR = os.path.join(BASE_DIR, "dashboard")


def generate_mock_data():
    anchor_date = datetime(2026, 9, 12, 8, 0, 0)
    anchor_str = anchor_date.strftime("%Y-%m-%d")

    # 1. User Profile
    user_profile = {
        "id": "alex_rivers",
        "name": "Alex Rivers",
        "age": 38,
        "gender": "male",
        "height_cm": 178.0,
        "dob": "1988-03-15",
        "measurement_system": "metric"
    }

    # 2. Adherence Matrix (Monday start, 7 days up to anchor)
    dates_7d = [(anchor_date - timedelta(days=6 - i)).strftime("%Y-%m-%d") for i in range(7)]
    day_labels = [(anchor_date - timedelta(days=6 - i)).strftime("%a %d") for i in range(7)]

    compounds_catalog = [
        {"id": "creatine", "name": "Creatine Monohydrate (5g)", "freq": "Daily", "days_active": [0,1,2,3,4,5,6]},
        {"id": "omega3", "name": "Omega-3 EPA/DHA (2000mg)", "freq": "Daily", "days_active": [0,1,2,3,4,5,6]},
        {"id": "vit_d3", "name": "Vitamin D3 + K2 (5000 IU)", "freq": "Daily", "days_active": [0,1,2,3,4,5,6]},
        {"id": "collagen", "name": "Hydrolyzed Collagen + Vit C (15g)", "freq": "lifting_days", "days_active": [1,3,4,6]},
        {"id": "magnesium", "name": "Magnesium Bisglycinate (400mg)", "freq": "Daily", "days_active": [0,1,2,3,4,5,6]},
        {"id": "protein", "name": "Whey Isolate Shake (40g)", "freq": "Daily", "days_active": [0,1,2,3,4,5,6]}
    ]

    matrix_rows = []
    for comp in compounds_catalog:
        row_days = []
        for idx, d_str in enumerate(dates_7d):
            if idx in comp["days_active"]:
                row_days.append({"date": d_str, "status": "taken", "divergence": None})
            else:
                row_days.append({"date": d_str, "status": "rest", "divergence": None})
        matrix_rows.append({
            "compound_id": comp["id"],
            "compound_name": comp["name"],
            "frequency": comp["freq"],
            "days": row_days
        })

    # HUD
    hud = {
        "phase": {
            "id": "protocol_03_hypertrophy",
            "name": "Protocol 03: Lean Mass Accretion & Metabolic Optimization",
            "start_date": "2026-08-01",
            "intent": "16-Week clinical protocol: resistance periodization, high protein (2.4g/kg), sleep optimization. Target 68kg FFM.",
            "compounds": "Whey Isolate (50g), Creatine Monohydrate (5g), Omega-3 EPA/DHA (2g), Vitamin D3+K2 (5000IU), Magnesium Bisglycinate (400mg)",
            "current_week": 6,
            "total_weeks": 16,
            "current_mode": "eGym Standard Mode (Weeks 1–4) -> Eccentric Overload (Weeks 5–8)"
        },
        "e2_alert": {
            "active": False,
            "delta_48h": 0.18,
            "weight_now": 78.4,
            "weight_48h_ago": 78.22,
            "insufficient_data": False,
            "threshold": 1.5,
            "warning_threshold": 1.2,
            "checklist": [
                "Peripheral edema or sock-ring ankle indentation",
                "Resting systolic blood pressure spike (+10–15 mmHg)",
                "Rapid unexplained overnight mass shift (>1.0 kg)",
                "Disrupted sleep or acute facial puffiness"
            ]
        },
        "data_health": {
            "days_evaluated": 7,
            "scale_days": 7,
            "hr_days": 7,
            "nutrition_days": 7,
            "sleep_days": 7,
            "meds_compliance_pct": 100,
            "meds_compliance_label": "100% (7 of 7 days)"
        },
        "adherence_matrix": {
            "dates": dates_7d,
            "day_labels": day_labels,
            "rows": matrix_rows
        },
        "timers": {
            "hours_remaining": 18,
            "interval_days": 1,
            "saturation_window_active": False
        },
        "telemetry_freshness": {
            "sensor_current_through": "2026-09-12T08:00:00+00:00",
            "last_sdk_sync": "2026-09-12T07:45:12.000000Z",
            "sdk_device": "Samsung Galaxy Watch / Health SDK",
            "last_scale_sync": "2026-09-12T08:00:00+00:00"
        }
    }

    # 3. Body Composition (12-month synthetic trend: 365 days)
    body_comp_history = []
    # Start: 82.8 kg, Fat 21.8%, Muscle 61.2 kg
    # End: 78.4 kg, Fat 15.2%, Muscle 64.2 kg
    start_weight, end_weight = 82.8, 78.4
    start_fat_pct, end_fat_pct = 21.8, 15.2
    start_muscle, end_muscle = 61.0, 64.2

    for day_i in range(365):
        d = anchor_date - timedelta(days=364 - day_i)
        d_str = d.strftime("%Y-%m-%d")
        t = day_i / 364.0
        
        # Add smooth curve + slight realistic measurement noise
        noise = math.sin(day_i * 0.4) * 0.25
        weight = round(start_weight + (end_weight - start_weight) * t + noise, 2)
        fat_pct = round(start_fat_pct + (end_fat_pct - start_fat_pct) * t + (noise * 0.15), 2)
        fat_mass = round(weight * (fat_pct / 100.0), 2)
        muscle_kg = round(start_muscle + (end_muscle - start_muscle) * (t ** 0.8) + (noise * 0.2), 2)
        ffm_kg = round(weight - fat_mass, 2)
        bmi = round(weight / ((1.78) ** 2), 2)
        
        # FFMI = FFM / (H^2)
        ffmi_raw = round(ffm_kg / ((1.78) ** 2), 2)
        # Normalized FFMI (Kouri et al. 1995): raw + 6.1 * (1.8 - H)
        ffmi_norm = round(ffmi_raw + 6.1 * (1.8 - 1.78), 2)

        body_comp_history.append({
            "date": d_str,
            "weight": weight,
            "bmi": bmi,
            "fat_pct": fat_pct,
            "fat_mass_kg": fat_mass,
            "fat_mass_ema7": fat_mass,
            "fat_mass_ema30": fat_mass,
            "muscle_kg": muscle_kg,
            "ffm_kg": ffm_kg,
            "ffmi": ffmi_norm,
            "ffmi_norm": ffmi_norm,
            "ffmi_raw": ffmi_raw,
            "ffmi_ema7": ffmi_norm,
            "ffmi_ema30": ffmi_norm,
            "ffmi_norm_ema7": ffmi_norm,
            "ffmi_norm_ema30": ffmi_norm,
            "ffmi_raw_ema7": ffmi_raw,
            "ffmi_raw_ema30": ffmi_raw,
            "tbw_kg": round(ffm_kg * 0.73, 2),
            "weight_ema7": weight,
            "weight_ema30": weight,
            "fat_ema7": fat_pct,
            "fat_ema30": fat_pct,
            "muscle_ema7": muscle_kg,
            "muscle_ema30": muscle_kg,
            "muscle_ref_min": 60.5,
            "muscle_ref_max": 72.0,
            "muscle_ref_min_ema7": 60.5,
            "muscle_ref_max_ema7": 72.0,
            "fat_ref_min": 11.0,
            "fat_ref_max": 18.0,
            "fat_ref_min_ema7": 11.0,
            "fat_ref_max_ema7": 18.0,
            "tbw_ema30": round(ffm_kg * 0.73, 2)
        })

    inbody_benchmarks = [
        {"date": "2025-10-15", "weight": 82.4, "ffm_kg": 64.6, "muscle_mass_kg": 61.2, "fat_mass_kg": 17.8, "body_fat_pct": 21.6, "ecw_ratio": 0.381, "phase_angle": 6.7, "visceral_fat_level": 5},
        {"date": "2026-01-20", "weight": 81.1, "ffm_kg": 65.4, "muscle_mass_kg": 62.0, "fat_mass_kg": 15.7, "body_fat_pct": 19.3, "ecw_ratio": 0.380, "phase_angle": 6.8, "visceral_fat_level": 4},
        {"date": "2026-05-10", "weight": 79.8, "ffm_kg": 66.2, "muscle_mass_kg": 62.9, "fat_mass_kg": 13.6, "body_fat_pct": 17.0, "ecw_ratio": 0.379, "phase_angle": 6.9, "visceral_fat_level": 4},
        {"date": "2026-08-25", "weight": 78.5, "ffm_kg": 66.8, "muscle_mass_kg": 63.6, "fat_mass_kg": 11.7, "body_fat_pct": 14.9, "ecw_ratio": 0.378, "phase_angle": 7.1, "visceral_fat_level": 3}
    ]

    body_comp = {
        "history": body_comp_history,
        "stats": {
            "readings_count": len(body_comp_history),
            "start_year": 2025,
            "latest_year": 2026,
            "latest_weight": 78.4,
            "latest_weight_kg": 78.4,
            "latest_fat_mass_kg": 11.9,
            "latest_fat_pct": 15.2,
            "latest_ffmi": 21.6,
            "latest_ffmi_norm": 21.6,
            "latest_ffmi_raw": 21.4,
            "current_ffmi_norm": 21.6,
            "latest_ffm_kg": 66.5,
            "latest_muscle_kg": 63.8,
            "ffmi_cycle_target": 22.0,
            "target_ffm_16wk_kg": 68.0
        },
        "inbody_benchmarks": inbody_benchmarks
    }

    # 4. Nutrition
    nutrition_series = []
    for i in range(90):
        d_str = (anchor_date - timedelta(days=89 - i)).strftime("%Y-%m-%d")
        cal = 2760 + int(math.sin(i * 0.5) * 80)
        prot = 195.0 + round(math.cos(i * 0.4) * 6, 1)
        carb = 310.0 + round(math.sin(i * 0.3) * 15, 1)
        fat = 74.0 + round(math.cos(i * 0.5) * 4, 1)
        nutrition_series.append({
            "date": d_str,
            "calories": cal,
            "protein": prot,
            "carbs": carb,
            "fat": fat,
            "fiber": 38.0,
            "sodium": 2400,
            "items": 12,
            "is_complete": True,
            "quality": "complete"
        })

    nutrition = {
        "coverage_last_90d": {
            "complete_days": 86,
            "total_days": 90,
            "pct": 95.6,
            "denominator_label": "from 86 of 90 complete days"
        },
        "averages_complete": {
            "calories_kcal": 2775.0,
            "protein_g": 196.2,
            "carbs_g": 311.5,
            "fat_g": 73.8,
            "protein_per_kg": 2.50
        },
        "targets": {
            "protein_min_g": 175.0,
            "protein_max_g": 210.0,
            "protein_target_g": 195.0,
            "calorie_surplus_target_kcal": 250
        },
        "recent_series": nutrition_series
    }

    # 5. Autonomic & Recovery
    autonomic_series = []
    for i in range(90):
        d_str = (anchor_date - timedelta(days=89 - i)).strftime("%Y-%m-%d")
        rhr = 53 + int(math.sin(i * 0.3) * 2.5)
        hrv = 68.0 + round(math.cos(i * 0.4) * 5.0, 1)
        autonomic_series.append({
            "date": d_str,
            "hr_avg": rhr,
            "hr_min": rhr - 4,
            "hr_max": 142,
            "stress_score": 24,
            "hrv_rmssd": hrv,
            "hrv_sdnn": hrv + 12.0,
            "hrv_upper": 76.0,
            "hrv_lower": 60.0,
            "workout_mins": 55 if i % 2 == 0 else 0,
            "workout_count": 1 if i % 2 == 0 else 0
        })

    # High recovery HRR-60 drop
    hrr_curve = [168 - int((1 - math.exp(-t / 22.0)) * 34) for t in range(120)]

    autonomic = {
        "series": autonomic_series,
        "latest_hrr_60": hrr_curve,
        "latest_hrr_meta": {
            "date": "2026-09-11",
            "duration_s": 120,
            "max_hr": 168,
            "hr_at_60s": 134,
            "drop_bpm": 34,
            "rating": "Excellent (Cardiovascular Conditioning)"
        }
    }

    # 6. Sleep Architecture (Un-gated: 7 of 7 nights)
    sleep_sessions = []
    for i in range(14):
        w_date = (anchor_date - timedelta(days=13 - i)).strftime("%Y-%m-%d")
        s_date = (anchor_date - timedelta(days=14 - i)).strftime("%Y-%m-%d")
        sleep_sessions.append({
            "wake_date": w_date,
            "start": f"{s_date} 22:45:00",
            "end": f"{w_date} 06:45:00",
            "duration_mins": 480,
            "total_sleep_mins": 455,
            "deep_mins": 105,
            "rem_mins": 110,
            "light_mins": 240,
            "awake_mins": 25,
            "efficiency_pct": 94.8,
            "score": 89,
            "sleeping_hr_mean": 51.5,
            "sleeping_hr_nadir": 46,
            "nocturnal_dip_pct": 16.8,
            "sleeping_hr_samples_n": 480
        })

    sleep = {
        "is_gated": False,
        "recent_7d_nights": 7,
        "threshold_required": 4,
        "gated_message": "Sleep Coverage Complete: 7 of last 7 nights recorded.",
        "recent_sessions": sleep_sessions
    }

    # 7. Exercise & Mechanical Telemetry
    egym_exercises_sample = [
        {"name": "Leg Press", "sets": [{"weight": 185.0, "reps": 12, "mode": "Eccentric Overload"}], "order": 1},
        {"name": "Chest Press", "sets": [{"weight": 87.5, "reps": 10, "mode": "Isokinetic"}], "order": 2},
        {"name": "Seated Row", "sets": [{"weight": 92.5, "reps": 10, "mode": "Standard"}], "order": 3},
        {"name": "Shoulder Press", "sets": [{"weight": 55.0, "reps": 10, "mode": "Adaptive"}], "order": 4},
        {"name": "Lat Pulldown", "sets": [{"weight": 82.5, "reps": 12, "mode": "Standard"}], "order": 5},
        {"name": "Back Extension", "sets": [{"weight": 70.0, "reps": 15, "mode": "Standard"}], "order": 6}
    ]

    exercise_sessions = []
    for i in range(12):
        s_date = (anchor_date - timedelta(days=23 - (i * 2))).strftime("%Y-%m-%d")
        exercise_sessions.append({
            "id": f"workout_mock_{i+1:02d}",
            "name": "eGym Hypertrophy Circuit + Upper Compound",
            "type": "Resistance",
            "date": s_date,
            "start_time": f"{s_date} 07:15:00",
            "end_time": f"{s_date} 08:12:00",
            "duration_m": 57,
            "calories": 420,
            "mean_hr": 136,
            "max_hr": 168,
            "notes": "Eccentric overload mode felt strong. High neural drive.",
            "hr_curve": [110 + int(math.sin(t * 0.1) * 30) + (t // 3) for t in range(57)],
            "hr_zones_pct": {"z1": 10, "z2": 22, "z3": 42, "z4": 21, "z5": 5},
            "hrr_meta": {"drop_bpm": 34, "rating": "Excellent"},
            "egym_exercises": egym_exercises_sample
        })

    bioage_history = [
        {"date": "2025-10-15", "chronological_age": 37.6, "bioage": 34.2, "cardio_age": 33.5, "strength_age": 34.8, "metabolism_age": 34.0, "flexibility_age": 34.5},
        {"date": "2026-01-20", "chronological_age": 37.9, "bioage": 33.1, "cardio_age": 32.0, "strength_age": 33.6, "metabolism_age": 33.2, "flexibility_age": 33.8},
        {"date": "2026-05-10", "chronological_age": 38.2, "bioage": 32.4, "cardio_age": 31.2, "strength_age": 32.9, "metabolism_age": 32.5, "flexibility_age": 33.0},
        {"date": "2026-08-25", "chronological_age": 38.5, "bioage": 31.6, "cardio_age": 30.5, "strength_age": 32.1, "metabolism_age": 31.8, "flexibility_age": 32.0}
    ]

    exercise = {
        "cadence_7d": {
            "start_date": "2026-09-06",
            "end_date": "2026-09-12",
            "total_sessions": 4,
            "resistance_sessions": 4,
            "target_sessions": 4
        },
        "recent_sessions": exercise_sessions,
        "bioage_history": bioage_history,
        "muscle_balance": {
            "date": "2026-08-25",
            "upper_body": {"status": "Balanced", "recommendation": "Optimal symmetry (Upper Back and Chest aligned)"},
            "core": {"status": "Balanced", "recommendation": "Good stability (Abs and Lower Back balanced)"},
            "lower_body": {"status": "Balanced", "recommendation": "Quadriceps and Hamstrings balanced"}
        }
    }

    # 8. Life Eras & Contextual Life Events
    life_eras = [
        {
            "id": "era_01_foundation",
            "name": "Phase 1: Aerobic Base & Mechanical Adaptation",
            "category": "base_conditioning",
            "start_date": "2025-09-01",
            "end_date": "2026-02-28",
            "is_fuzzy": 0,
            "intent": "Establish tendon resilience, lipid optimization, and aerobic efficiency.",
            "compounds": "Creatine 5g, Omega-3 2g, Vit D3 5000IU",
            "diet": "2500 kcal maintenance, 2.0g/kg protein",
            "training": "eGym Standard Mode + Zone 2 Cardio",
            "learnings": "Zero tendon inflammation; resting HR dropped by 6 bpm."
        },
        {
            "id": "era_02_hypertrophy",
            "name": "Phase 2: Lean Mass Accretion & Metabolic Optimization",
            "category": "hypertrophy",
            "start_date": "2026-03-01",
            "end_date": "2026-09-12",
            "is_fuzzy": 0,
            "intent": "Progressive mechanical overload targeting +3.0 kg functional FFM.",
            "compounds": "Whey Isolate 50g, Creatine 5g, Omega-3 2g, Vit D3+K2 5000IU, Magnesium 400mg",
            "diet": "2800 kcal clean surplus, 2.5g/kg protein",
            "training": "eGym Eccentric Overload & Adaptive Drop-Sets",
            "learnings": "FFMI reached 21.6; body fat decreased from 21.8% to 15.2%."
        }
    ]

    life_events = [
        {
            "id": "event_01_baseline",
            "category": "protocol",
            "title": "Metabolic Baseline Comprehensive Panel",
            "start_date": "2025-09-15",
            "end_date": "2025-09-15",
            "severity": "info",
            "notes": "Established clinical baseline across 86 analytes.",
            "tags": ["bloodwork", "baseline"],
            "physiological_impact": "Anchor point for 12-month biomarker surveillance."
        },
        {
            "id": "event_02_altitude",
            "category": "lifestyle",
            "title": "High-Altitude Endurance Camp",
            "start_date": "2026-02-10",
            "end_date": "2026-02-24",
            "severity": "positive",
            "notes": "14-day training block at 2,100m elevation. Significant aerobic adaptation.",
            "tags": ["altitude", "cardio"],
            "physiological_impact": "Enhanced EPO response, increased RBC volume and mitochondrial density."
        },
        {
            "id": "event_03_eccentric",
            "category": "training",
            "title": "eGym Eccentric Overload Cycle Launch",
            "start_date": "2026-04-01",
            "end_date": "2026-04-01",
            "severity": "info",
            "notes": "Switched resistance curve to +30% negative phase load.",
            "tags": ["egym", "eccentric"],
            "physiological_impact": "Accelerated hypertrophy in fast-twitch motor units."
        },
        {
            "id": "event_04_nutrition",
            "category": "nutrition",
            "title": "Clean Hypercaloric Nutrition Transition",
            "start_date": "2026-06-01",
            "end_date": "2026-06-01",
            "severity": "info",
            "notes": "Targeted surplus of +250 kcal/day, 2.5g/kg protein ceiling.",
            "tags": ["nutrition", "macros"],
            "physiological_impact": "Sustained positive nitrogen balance without visceral fat accretion."
        },
        {
            "id": "event_05_deload",
            "category": "training",
            "title": "Active Deload & Autonomic Restoration",
            "start_date": "2026-08-15",
            "end_date": "2026-08-22",
            "severity": "positive",
            "notes": "Volume reduced 50%; focused on sleep extension and mobility.",
            "tags": ["deload", "recovery"],
            "physiological_impact": "HRV rebound (+14 ms), systemic fatigue reduction."
        }
    ]

    # 9. Protocols & Protocol Studio
    protocols_list = [
        {
            "id": "protocol_01_conditioning",
            "version": 1,
            "name": "Protocol 01: Baseline Metabolic Conditioning",
            "category": "conditioning",
            "status": "completed",
            "effective_start": "2025-09-01",
            "effective_end": "2026-02-28",
            "intent_summary": "Base conditioning, mitochondrial health, and tendon resilience.",
            "compounds": [
                {"name": "Creatine Monohydrate", "dose": "5g", "timing": "Morning", "freq": "Daily"},
                {"name": "Omega-3 Fish Oil", "dose": "2000mg", "timing": "With Meals", "freq": "Daily"},
                {"name": "Vitamin D3 + K2", "dose": "5000 IU", "timing": "Morning", "freq": "Daily"}
            ],
            "supplements": [],
            "training_program": "eGym Standard Mode (2x/week) + 150m Zone 2 Cardio",
            "diagnostic_panel": "Baseline venous bloodwork + InBody scan",
            "safety_redlines": "RHR > 65 bpm for 3 days triggers volume cut",
            "milestones": ["Completed 24 weeks without missed session"]
        },
        {
            "id": "protocol_03_hypertrophy",
            "version": 2,
            "name": "Protocol 03: Lean Mass Accretion & Metabolic Optimization",
            "category": "hypertrophy",
            "status": "active",
            "effective_start": "2026-08-01",
            "effective_end": "2026-11-20",
            "intent_summary": "16-Week clinical protocol: resistance periodization, high protein (2.4g/kg), sleep optimization. Target 68kg FFM.",
            "compounds": [
                {"name": "Hydrolyzed Whey Isolate", "dose": "50g", "timing": "Post-Workout", "freq": "Daily"},
                {"name": "Creatine Monohydrate", "dose": "5g", "timing": "Post-Workout", "freq": "Daily"},
                {"name": "Omega-3 EPA/DHA", "dose": "2000mg", "timing": "With Dinner", "freq": "Daily"},
                {"name": "Vitamin D3 + K2", "dose": "5000 IU", "timing": "Morning", "freq": "Daily"},
                {"name": "Magnesium Bisglycinate", "dose": "400mg", "timing": "Before Sleep", "freq": "Daily"}
            ],
            "supplements": [],
            "training_program": "eGym Eccentric Overload (3x/week) + Tendon Active Recovery",
            "diagnostic_panel": "Quarterly venous bloodwork + InBody 770 bioimpedance",
            "safety_redlines": "Haematocrit > 52% or LDL > 130 mg/dL triggers immediate review",
            "milestones": ["FFMI reached 21.6 athletic tier", "Body fat reduced below 15.5%"]
        }
    ]

    active_protocol = protocols_list[1]

    interventions_catalog = [
        {"id": "int_01", "name": "Creatine Monohydrate", "category": "supplement", "dose": "5g", "frequency_h": 24, "unit_cost": 0.25, "pack_size": 500, "pack_price": 25.0, "shelf_life_d": 720, "inventory": 350, "notes": "Creapure grade"},
        {"id": "int_02", "name": "Omega-3 EPA/DHA", "category": "supplement", "dose": "2000mg", "frequency_h": 24, "unit_cost": 0.40, "pack_size": 120, "pack_price": 48.0, "shelf_life_d": 365, "inventory": 84, "notes": "Triglyceride form"},
        {"id": "int_03", "name": "Vitamin D3 + K2", "category": "vitamin", "dose": "5000 IU", "frequency_h": 24, "unit_cost": 0.15, "pack_size": 180, "pack_price": 27.0, "shelf_life_d": 720, "inventory": 140, "notes": "Liposomal carrier"},
        {"id": "int_04", "name": "Magnesium Bisglycinate", "category": "mineral", "dose": "400mg", "frequency_h": 24, "unit_cost": 0.30, "pack_size": 90, "pack_price": 27.0, "shelf_life_d": 720, "inventory": 65, "notes": "Chelated"}
    ]

    # 10. Longitudinal Bloodwork (18 Categories with Optimal Clinical Ranges)
    def make_analyte(code, name, unit, br_unit, val, comp, ref_lo, ref_hi, history_vals):
        hist = []
        draws = ["2025-09-15", "2025-12-10", "2026-04-12", "2026-08-15"]
        for d_str, v in zip(draws, history_vals):
            hist.append({
                "draw_date": d_str,
                "value": v,
                "comparator": "=",
                "is_out_of_range": False if (ref_lo <= v <= ref_hi) else True
            })
        return {
            "analyte_code": code,
            "analyte_name": name,
            "canonical_unit": unit,
            "br_unit": br_unit,
            "latest_value": val,
            "latest_comparator": comp,
            "latest_draw_date": "2026-08-15",
            "ref_low_reported": ref_lo,
            "ref_high_reported": ref_hi,
            "is_out_of_range": False,
            "history": hist
        }

    bloodwork_categories = [
        {
            "category": "Haematology & Red Cell Kinetics",
            "analytes": [
                make_analyte("HCT", "Haematocrit", "%", "%", 46.2, "=", 40.0, 52.0, [45.8, 46.5, 46.0, 46.2]),
                make_analyte("HGB", "Haemoglobin", "g/dL", "g/dL", 15.6, "=", 13.5, 17.5, [15.4, 15.8, 15.5, 15.6]),
                make_analyte("RBC", "Red Blood Cell Count", "10^12/L", "milhoes/uL", 5.12, "=", 4.3, 5.9, [5.05, 5.15, 5.10, 5.12]),
                make_analyte("PLT", "Platelets", "10^9/L", "mil/uL", 242, "=", 150, 400, [238, 245, 240, 242]),
                make_analyte("MCV", "Mean Corpuscular Volume", "fL", "fL", 90.2, "=", 80.0, 100.0, [90.5, 89.8, 90.1, 90.2])
            ]
        },
        {
            "category": "Lipids & Cardiovascular Risk",
            "analytes": [
                make_analyte("APOB", "Apolipoprotein B (ApoB)", "mg/dL", "mg/dL", 66.0, "=", 40.0, 90.0, [74.0, 70.0, 68.0, 66.0]),
                make_analyte("LDL", "LDL-C (Direct)", "mg/dL", "mg/dL", 84.0, "=", 50.0, 100.0, [95.0, 88.0, 86.0, 84.0]),
                make_analyte("HDL", "HDL-C", "mg/dL", "mg/dL", 64.0, "=", 40.0, 80.0, [58.0, 60.0, 62.0, 64.0]),
                make_analyte("TRIG", "Triglycerides", "mg/dL", "mg/dL", 68.0, "=", 40.0, 150.0, [82.0, 75.0, 70.0, 68.0]),
                make_analyte("CHOL", "Total Cholesterol", "mg/dL", "mg/dL", 162.0, "=", 120.0, 200.0, [178.0, 168.0, 165.0, 162.0])
            ]
        },
        {
            "category": "Metabolic, Glycaemic & Insulin Dynamics",
            "analytes": [
                make_analyte("GLUC", "Fasting Glucose", "mg/dL", "mg/dL", 82.0, "=", 70.0, 99.0, [86.0, 84.0, 83.0, 82.0]),
                make_analyte("HBA1C", "HbA1c", "%", "%", 5.1, "=", 4.5, 5.6, [5.3, 5.2, 5.1, 5.1]),
                make_analyte("INS", "Fasting Insulin", "uIU/mL", "uUI/mL", 4.1, "=", 2.0, 8.0, [5.4, 4.8, 4.3, 4.1]),
                make_analyte("HOMAIR", "HOMA-IR Score", "index", "indice", 0.83, "=", 0.2, 1.5, [1.14, 0.99, 0.88, 0.83])
            ]
        },
        {
            "category": "Endocrine & Hormonal Axis",
            "analytes": [
                make_analyte("TT", "Total Testosterone", "ng/dL", "ng/dL", 760.0, "=", 300.0, 1000.0, [620.0, 680.0, 720.0, 760.0]),
                make_analyte("FT", "Free Testosterone (Calculated)", "pg/mL", "pg/mL", 18.2, "=", 9.0, 25.0, [14.5, 16.0, 17.5, 18.2]),
                make_analyte("E2_SENS", "Ultra-Sensitive Estradiol (LC-MS)", "pg/mL", "pg/mL", 24.5, "=", 15.0, 35.0, [22.0, 23.5, 24.0, 24.5]),
                make_analyte("SHBG", "Sex Hormone Binding Globulin", "nmol/L", "nmol/L", 36.0, "=", 18.0, 54.0, [34.0, 35.0, 35.5, 36.0]),
                make_analyte("PRL", "Prolactin", "ng/mL", "ng/mL", 6.8, "=", 4.0, 15.0, [7.2, 7.0, 6.9, 6.8])
            ]
        },
        {
            "category": "Renal & Electrolyte Physiology",
            "analytes": [
                make_analyte("CREAT", "Serum Creatinine", "mg/dL", "mg/dL", 0.92, "=", 0.70, 1.25, [0.95, 0.94, 0.93, 0.92]),
                make_analyte("EGFR", "eGFR (CKD-EPI)", "mL/min/1.73m2", "mL/min", 104.0, ">", 90.0, 130.0, [100.0, 102.0, 103.0, 104.0]),
                make_analyte("UREA", "Blood Urea Nitrogen", "mg/dL", "mg/dL", 17.5, "=", 10.0, 20.0, [18.2, 17.8, 17.6, 17.5])
            ]
        },
        {
            "category": "Hepatic Biomarkers & Enzymes",
            "analytes": [
                make_analyte("ALT", "Alanine Aminotransferase (ALT)", "U/L", "U/L", 20.0, "=", 10.0, 45.0, [24.0, 22.0, 21.0, 20.0]),
                make_analyte("AST", "Aspartate Aminotransferase (AST)", "U/L", "U/L", 18.0, "=", 10.0, 40.0, [22.0, 20.0, 19.0, 18.0]),
                make_analyte("ALB", "Albumin", "g/dL", "g/dL", 4.6, "=", 3.5, 5.0, [4.5, 4.5, 4.6, 4.6]),
                make_analyte("BILI", "Total Bilirubin", "mg/dL", "mg/dL", 0.62, "=", 0.2, 1.2, [0.65, 0.64, 0.63, 0.62])
            ]
        },
        {
            "category": "Systemic Inflammation",
            "analytes": [
                make_analyte("HSCRP", "High-Sensitivity CRP", "mg/L", "mg/L", 0.32, "<", 0.1, 1.0, [0.45, 0.38, 0.35, 0.32])
            ]
        },
        {
            "category": "Thyroid Axis",
            "analytes": [
                make_analyte("TSH", "Thyroid Stimulating Hormone", "uIU/mL", "uUI/mL", 1.75, "=", 0.45, 4.12, [1.90, 1.82, 1.78, 1.75]),
                make_analyte("FT4", "Free Thyroxine (FT4)", "ng/dL", "ng/dL", 1.35, "=", 0.85, 1.70, [1.30, 1.32, 1.34, 1.35]),
                make_analyte("FT3", "Free Triiodothyronine (FT3)", "pg/mL", "pg/mL", 3.40, "=", 2.30, 4.20, [3.35, 3.38, 3.39, 3.40])
            ]
        },
        {
            "category": "Vitamins, Minerals & Co-Factors",
            "analytes": [
                make_analyte("VITD", "25-OH Vitamin D Total", "ng/mL", "ng/mL", 68.0, "=", 40.0, 80.0, [52.0, 58.0, 64.0, 68.0]),
                make_analyte("B12", "Active Vitamin B12", "pg/mL", "pg/mL", 740.0, "=", 300.0, 950.0, [680.0, 710.0, 725.0, 740.0]),
                make_analyte("FERR", "Ferritin", "ng/mL", "ng/mL", 125.0, "=", 50.0, 200.0, [115.0, 120.0, 122.0, 125.0]),
                make_analyte("MAG", "Serum Magnesium", "mg/dL", "mg/dL", 2.25, "=", 1.8, 2.6, [2.15, 2.20, 2.22, 2.25])
            ]
        }
    ]

    brief_analytes = []
    for cat_obj in bloodwork_categories:
        cat_name = cat_obj["category"]
        for a in cat_obj["analytes"]:
            vals = [h["value"] for h in a["history"]]
            last_5 = [
                {
                    "date": h["draw_date"],
                    "value": h["value"],
                    "comparator": h["comparator"],
                    "value_br": h["value"],
                    "ref_low": a["ref_low_reported"],
                    "ref_high": a["ref_high_reported"],
                    "is_out_of_range": h["is_out_of_range"],
                    "lab_provider": "Diagnostic Laboratory Service"
                }
                for h in a["history"]
            ]
            brief_analytes.append({
                "analyte_code": a["analyte_code"].lower(),
                "analyte_name": a["analyte_name"],
                "category": cat_name,
                "canonical_unit": a["canonical_unit"],
                "br_unit": a["br_unit"],
                "latest_value": a["latest_value"],
                "latest_value_br": a["latest_value"],
                "latest_comparator": a["latest_comparator"],
                "latest_draw_date": a["latest_draw_date"],
                "ref_low_reported": a["ref_low_reported"],
                "ref_high_reported": a["ref_high_reported"],
                "latest_out_of_range": a["is_out_of_range"],
                "min_canonical": min(vals) if vals else a["latest_value"],
                "max_canonical": max(vals) if vals else a["latest_value"],
                "min_br": min(vals) if vals else a["latest_value"],
                "max_br": max(vals) if vals else a["latest_value"],
                "total_draws": len(a["history"]),
                "last_5_draws": last_5
            })

    bloodwork = {
        "latest_draw_date": "2026-08-15",
        "draw_dates": ["2025-09-15", "2025-12-10", "2026-04-12", "2026-08-15"],
        "draw_readiness": {
            "mandatory_panel": True,
            "assay_methods_confirmed": True,
            "days_since_last_draw": 28
        },
        "categories": bloodwork_categories,
        "consultation_brief": {
            "analytes": brief_analytes,
            "latest_out_of_range": [a for a in brief_analytes if a["latest_out_of_range"]],
            "total_analytes": len(brief_analytes),
            "out_of_range_count": len([a for a in brief_analytes if a["latest_out_of_range"]])
        }
    }

    staged_scans = {
        "pending_count": 0,
        "total_count": 12,
        "scans": []
    }

    mock_payload = {
        "system_version": "v1.4.0",
        "schema_version": 14,
        "data_current_through": "2026-09-12T08:00:00+00:00",
        "user_profile": user_profile,
        "hud": hud,
        "body_comp": body_comp,
        "nutrition": nutrition,
        "autonomic": autonomic,
        "sleep": sleep,
        "exercise": exercise,
        "life_eras": life_eras,
        "life_events": life_events,
        "interventions_catalog": interventions_catalog,
        "protocols": protocols_list,
        "active_protocol": active_protocol,
        "bloodwork": bloodwork,
        "staged_scans": staged_scans
    }

    return mock_payload


def build_mock_html_files():
    mock_data = generate_mock_data()
    mock_json_str = json.dumps(mock_data, separators=(',', ':'))

    with open(INDEX_HTML, "r", encoding="utf-8") as f:
        html_content = f.read()

    # 1. Force light mode on <html> tag: <html lang="en" class="light overflow-x-hidden">
    html_mod = re.sub(r'<html[^>]*class="[^"]*"', '<html lang="en" class="light overflow-x-hidden"', html_content)

    # 2. Replace injected-dashboard-data script body
    pattern = r'(<script id="injected-dashboard-data">\s*window\.__DASHBOARD_DATA__\s*=\s*).*?(;\s*</script>)'
    html_mod = re.sub(pattern, lambda m: f'{m.group(1)}{mock_json_str}{m.group(2)}', html_mod, flags=re.DOTALL)

    # 3. Add override script right before </body> to ensure Light Mode is forced in localStorage
    # and disable live server polling so screenshot won't try to fetch NAS/server
    override_script = """
  <script>
    // Force light mode unconditionally for screenshot fidelity
    localStorage.setItem('health_dashboard_theme', 'light');
    document.documentElement.classList.remove('dark');
    document.documentElement.classList.add('light');
    if (window.ThemeManager) {
      window.ThemeManager.applyTheme('light');
    }

    // Harmonize UI elements for anonymous, professional mock presentation
    window.addEventListener('DOMContentLoaded', () => {
      setTimeout(() => {
        const metaStatEl = document.getElementById('bc-meta-stat');
        if (metaStatEl) metaStatEl.innerHTML = '178 cm • 38yo <span class="text-[10px] font-normal text-slate-400">(BMI 24.7)</span>';
        
        const stripDut = document.getElementById('strip-protocol-status') || document.getElementById('strip-dut-status');
        if (stripDut) stripDut.innerText = 'Protocol: Active';
        
        const titleH1 = document.querySelector('h1');
        if (titleH1) titleH1.innerText = 'Health Operating System • Clinical Command Center';
      }, 200);
    });
  </script>
"""

    workspaces = [
        ("adaptation", "Physical Adaptation"),
        ("recovery", "Autonomic & Sleep"),
        ("bloodwork", "Labs & Bloods"),
        ("history", "History & Eras"),
        ("protocol", "Protocol Studio")
    ]

    # Write workspace-specific preview files
    generated_files = []
    for tab_id, tab_label in workspaces:
        bw_subtab = ""
        if tab_id == 'bloodwork':
            bw_subtab = """
          if (window.BloodworkManager && typeof window.BloodworkManager.setTab === 'function') {
            window.BloodworkManager.setTab('matrix');
          }
"""
        tab_script = f"""
  <script>
    window.addEventListener('DOMContentLoaded', () => {{
      setTimeout(() => {{
        if (window.WidgetManager && typeof window.WidgetManager.setWorkspaceTab === 'function') {{
          window.WidgetManager.setWorkspaceTab('{tab_id}');
        }}{bw_subtab}
      }}, 250);
    }});
  </script>
"""
        full_html = html_mod.replace("</body>", f"{override_script}\n{tab_script}\n</body>")
        file_path = os.path.join(OUTPUT_DIR, f"preview_mock_{tab_id}.html")
        with open(file_path, "w", encoding="utf-8") as out_f:
            out_f.write(full_html)
        generated_files.append(file_path)
        print(f"Generated: {file_path}")

    # Also generate preview_mock_mobile.html (Adaptation workspace, but tuned for mobile bottom nav)
    mobile_script = """
  <script>
    window.addEventListener('DOMContentLoaded', () => {
      setTimeout(() => {
        if (window.WidgetManager && typeof window.WidgetManager.setWorkspaceTab === 'function') {
          window.WidgetManager.setWorkspaceTab('adaptation');
        }
      }, 150);
    });
  </script>
"""
    mobile_html = html_mod.replace("</body>", f"{override_script}\n{mobile_script}\n</body>")
    mobile_file_path = os.path.join(OUTPUT_DIR, "preview_mock_mobile.html")
    with open(mobile_file_path, "w", encoding="utf-8") as out_f:
        out_f.write(mobile_html)
    generated_files.append(mobile_file_path)
    print(f"Generated: {mobile_file_path}")

    return generated_files


if __name__ == "__main__":
    files = build_mock_html_files()
    print(f"\nSuccessfully generated {len(files)} preview mock files for light-mode screenshots.")

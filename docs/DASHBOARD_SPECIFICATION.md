# Personal Health Dashboard — Master Specification

**Document:** `docs/DASHBOARD_SPECIFICATION.md`  
**Version:** 1.0  
**Date:** 2026-08-28  
**Status:** Active Master Specification (Consensus between Gemini & Claude)  
**Database Basis:** `data/health_dashboard.db` (Schema v8 as of 2026-08-29; v4 $\rightarrow$ v5 at authoring, 2,530 days, ~2.33M fact/sample rows)

---

## 1. Core Architectural Principles

1. **Protocol Evaluation & Personal Health OS:**
   The dashboard is a **decision and evaluation engine**, not a passive metrics viewer. It connects continuous passive telemetry (Samsung continuous HR/stress/HRV, Withings 9-year scale readings, MyFitnessPal meal tracking) with active clinical protocols (`protocols/01`–`05`).

2. **The Non-Negotiable Safety Boundary:**
   The dashboard surfaces the measurable half of a clinical rule and prompts for the subjective confirmation half; **it never prescribes or recommends a dose change**.
   * *Example:* When the scale detects $+1.37\text{ kg}$ in 48 hours, the system does not say *"increase omega3_epd_dha"*; it outputs: *"Weight delta +1.37 kg / 48h (Approaching 1.5 kg High-E2 trigger). Check: areola itching, pitting edema, sock-ring indentation, systolic blood pressure."*

3. **First-Class Coverage Denominators:**
   Every reported figure, average, and chart must carry its backing sample size and data completeness denominator (e.g., *"1,894 kcal avg — from 33 of 89 days"*). The system must never divide by calendar days when data was unlogged.

4. **Coverage Gating (Not Merely Labeling):**
   Modules with sparse data stay dark below declared reliability thresholds and display an actionable behavioral instruction instead of interpolating lines through absence.
   * *Sleep Architecture Gate:* Requires $\ge 4\text{ of the last 7 nights}$ recorded. Below this threshold, the module displays: *"Sleep: 1 of last 7 nights recorded. Not enough data to trend. Wear the watch overnight."*

5. **Cross-Cutting Hedge Preservation & Confidence Ladders:**
   Natural language input and user speech hedges (*"about 200 quid"*, *"took test around 9-ish"*) must be preserved in schema metadata (`confidence: recalled`, `precision: approximate`). Automated agents and parsers are strictly prohibited from laundering fuzzy hedges into artificial precision.

6. **Closed Divergence Taxonomy:**
   Dose logging uses a structured divergence vocabulary (`adherent`, `late`, `missed`, `dose_changed`, `substituted`, `depleted`, `deliberate_skip`). `deliberate_skip` is treated as clinical compliance (mandated under Recovery Mode dry joint pain or deloads), not as a missed dose.

---

## 2. The 3-Tier Information Architecture

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│ TIER 1: THE TODAY & PROTOCOL READINESS COMMAND BAR (Morning HUD)                 │
│ • Active Phase: Week 1 of 16 (Standard Mode)  • Live Fluid Alert (Checklist)      │
│ • 72h Dose Timer + 60m Absorption Window      • 48h Interval Counter        │
│ • 1-Tap Dose Logging with Divergence Codes    • Compact Data-Health Status Pill  │
├──────────────────────────────────────────────────────────────────────────────────┤
│ TIER 2: TABBED CORE DOMAIN EXPLORERS (Cross-Synchronized Time Scrubbing)        │
│ ┌────────────────────────┐ ┌────────────────────────┐ ┌────────────────────────┐ │
│ │ 🏋️ Body Composition    │ │ 🫀 Autonomic & Cardio  │ │ 🥗 Nutrition           │ │
│ │ 9-Yr EMA Trend & TBW   │ │ 7d HRV & HRR-60 Curve  │ │ Complete Days Only     │ │
│ └────────────────────────┘ └────────────────────────┘ └────────────────────────┘ │
│ ┌────────────────────────┐ ┌────────────────────────┐ ┌────────────────────────┐ │
│ │ 😴 Sleep Architecture  │ │ 💈 Scalp Protocol      │ │ 🩸 Bloodwork Radar     │ │
│ │ Gated (≥4/7 nights)    │ │ Routine A/B Split      │ │ Milestones & HCT Limit │ │
│ └────────────────────────┘ └────────────────────────┘ └────────────────────────┘ │
├──────────────────────────────────────────────────────────────────────────────────┤
│ TIER 3: MULTI-YEAR LIFE ERAS & LONGITUDINAL AUTOBIOGRAPHY (2014–2026)            │
│ • 9-Year Master Recomposition Arc (Fat 27.5% → 20.6%, Muscle 60.6 → 56.0 kg)     │
│ • Shaded Historical Life Eras (e.g. "2022 Deload & Recomp", "2023 Work Bulk")   │
│ • Retrospective Intent vs. Reality Evaluation Sandbox                            │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2.1 Workspace Hierarchy, Design System & Naming Taxonomy (ADR-019)

To ensure clinical coherence, predictable interaction, and consistent vocabulary across all agents and documentation, the dashboard interface adheres to a strict 4-level structural hierarchy:

```
Level 0: App Shell & Command Strip
 ├── App Navigation Header   (Fixed Top: 2px gradient hairline, Executive Slate-800, Logo, Scale Sync, Theme)
 └── Workspace Command Strip (Sticky Sub-Dock: Slate-Grey #EAEFF5 derivative, Telemetry Cluster + Layout Studio Actions, Strict Flex-Nowrap)

Level 1: Tiers (Clinical Functional Groups — Physiological Telemetry vs. Protocol Operations / ADR-020, ADR-021)
 ├── Tier 1: Physiological Telemetry & Core Explorers (Outcomes & Recovery: Body Comp, Nutrition, Autonomic, Sleep, Life Eras)
 └── Tier 2: Protocol Operations, Compliance & Safety (Inputs & Cadence: Macro-Cycle Milestone Bar, Weekly Matrix, Action & Safety Deck)

Level 2: Widget Zones (Grid Flow Containers)
 ├── zone-explorers     (2-column responsive analytical canvas with side-by-side packing: Nutrition & Autonomic twin cards)
 ├── zone-tier2-matrix  (Full-width Protocol Adherence Matrix container)
 └── zone-tier2-actions (4-column compact action card deck)

Level 3: Widgets (Self-Contained Functional Cards)
 ├── Domain Explorers (Body Comp [2-col], Nutrition [1-col], Autonomic [1-col], Sleep [2-col], Life Eras [2-col, collapsed])
 ├── Protocol Adherence Matrix
 └── Action Cards (cadence_compound, omega3_epd_dha, Pre-Workout, Deload, High-E2 Alert)
```

### Canonical Definitions & Interaction Contracts:

1. **App Navigation Header & Workspace Command Strip (`#main-header`, `#layout-control-banner`)**:
   * **Visual Hierarchy & Palette**: Executive Slate-800 (`#1E293B`) top app bar with top 2px gradient hairline, paired with an ultra-slim (~32px) slate-grey derivative dock (`#EAEFF5` in light mode, `#131C28` in dark mode) directly beneath it (~74px combined height).
   * **Real Estate Protection**: The Command Strip enforces strict `flex-nowrap overflow-x-auto`, guaranteeing single-row compactness with zero accidental vertical wrapping on any screen width.
   * **Left-Hand Side (Telemetry Pod)**: Displays Sensor Pipeline Health (`#data-health-pill` with Scale, HR, Nutr, Sleep counts) and Sensor Telemetry Freshness (`Data current through <time>` via `#generated-timestamp`), indicating the timestamp of the newest recorded fact in the database.
   * **Center (Domain Viewport Tabs)**: Viewport Tabs (`[ All Tiers | Body Comp | Recovery | Protocol ]`) provide one-tap clinical domain isolation, dynamically toggling card visibility within `#zone-explorers` and auto-expanding target tiers.
   * **Right-Hand Side (Layout Studio Pod)**: **Layout Studio** global toolbar (`⊞ All`, `⊟ Fold`, `↺ Reset`, `💾 Save`, `#save-status`).
   * **Dynamic Sticky Offset**: `WidgetManager.syncHeaderOffset()` dynamically measures header height at runtime to ensure seamless docking with zero overlap or gap.

2. **The 2-Tier Master Architecture & Side-by-Side Packing (`ADR-020`, `ADR-021`)**:
   * Consolidates physiological telemetry into a unified high-density Tier 1, with protocol operations in Tier 2:
     * **Tier 1 (Physiological Telemetry & Core Explorers)**: Houses all 5 explorer modules in a unified 2-column grid (`#zone-explorers`).
       * **Row 1**: Body Composition (`exp-bodycomp`, 2-cols full width).
       * **Row 2 (Side-by-Side Twin Packing)**: Nutrition Partitioning (`exp-nutrition`, 1-col) and Autonomic Balance (`exp-autonomic`, 1-col) sit side-by-side as twin 1-column cards, eliminating empty 50% row voids and recovering ~550px of vertical screen real estate.
       * **Row 3**: Sleep Architecture (`exp-sleep`, 2-cols full width).
       * **Row 4**: Longitudinal Life Eras (`exp-lifeeras`, 2-cols full width, collapsed by default).
     * **Tier 2 (Protocol Operations, Compliance & Safety)**: Houses therapeutic interventions and cadence controls:
       * **Macro-Cycle Milestone Bar**: Clinical progress track (`Week 2 of 16 • 12.5% Elapsed • Deload Due W6 • Bloods Due W8`).
       * **7-Day Protocol Adherence Matrix**: Weekly compound adherence grid with infinite historical navigation.
       * **Action & Safety Deck**: Acute dose countdowns (Cadence q24h, Interval q48h) and the live High-E2 Scale Jump alert (+1.37 kg / 48h check).
   * **Collapsible Compliance Efficiency**: Both tiers feature dense summary preview chips when collapsed (Tier 1: `78.4 kg • FFMI 21.2 • 19.9% BF • 78.0g Prot • HRV ~40ms • Sleep Gated`; Tier 2: `Week 2 • Saturation • 100% Adherent • Cadence in 18h • E2 Stable`).
   * **Interaction Rule**: Tiers can swap blocks with each other via header nudge buttons or grab handles `⠿`. Micro-widgets reorder within their zone.

3. **Widget Zone (`[data-widget-zone]`)**:
   * A CSS layout container inside a Tier that governs how child widgets flow, wrap, and reorder.
   * Native HTML5 drag-and-drop reordering is scoped within each zone.

4. **Widget (`[data-widget-id]`)**:
   * An individual, self-contained interactive card visualizing a specific biomarker stream, protocol timer, or clinical matrix.
   * **Standard Anatomy**:
     * **Grab Handle (`⠿`)**: Active on mousedown to arm card drag without ghosting text.
     * **Clinical Preview Badge**: Dense summary chip rendered only when collapsed (e.g. `78.4 kg • FFMI 21.2 • 19.9% BF`), guaranteeing situational awareness with minimal vertical height.
     * **Width Toggle (`⇲/⇱`)**: Interactively expands to 2-columns (full width) or shrinks to 1-column (half width) with automatic Chart.js canvas reflow.
     * **Collapse Toggle (`⌵`)**: Hides/shows `.widget-body`.

---

## 3. Detailed Surface Specifications

### Tier 1: Morning Command HUD

1. **Active Cycle Badge:**
   * `Week 1 of 16 — Metabolic Conditioning (Standard Mode)`.
   * Deload countdown: `Active Deload due at Week 6 (-30% intensity)`.

2. **Live Safety Rule: High-E2 Scale Trigger (Protocol 01 §3):**
   * Computes 48-hour rolling weight delta from Withings scale.
   * **Live Status:** Current delta is **$+1.37\text{ kg} / 48\text{h}$** (Aug 24: 73.69 kg $\rightarrow$ Aug 26: 75.06 kg).
   * **Visual Alert Card:** Amber banner (*"Weight delta +1.37 kg / 48h — within 0.13 kg of 1.5 kg E2 trigger"*).
   * **Confirmation Checklist:**
     - [ ] Peripheral edema or indentation
     - [ ] Ankle/pitting edema or sock-ring indentation
     - [ ] Resting systolic blood pressure spike (+15–20 mmHg)
     - [ ] Nocturnal temperature/sweating

3. **Pharmacokinetic Timers & Action Chips:**
   * **24h Cadence Timer:** Countdown since last dose + mandatory *60-minute saturation window* before AM Supplement.
   * **48h Interval Counter:** Days since last dose + 10-day hold countdown on dosage changes.
   * **1-Tap Dose Action Chips:** Single-tap chips for AM Supplements, cadence_compound, omega3_epd_dha, and Pre-Workout stack, with divergence dropdown (`Adherent`, `Late (+N hours)`, `Deliberate Skip`, `Dose Changed`).

4. **Compact Data-Health Status Pill:**
   * Header status bar tracking 7-day input integrity:
     `[ 🟢 Scale 7/7d • 🟢 HR 7/7d • 🟡 Nutrition 4/7d • 🔴 Sleep 1/7d • 🟢 Meds 100% ]`

5. **Co-Located Protocol Adherence Matrix & Week/Date Navigation (`ADR-002`):**
   * **Visual Co-Location & Top-Down Hierarchy:** Placed at the very top of Tier 1 directly beneath the title bar, unifying the date navigation and weekly adherence grid into a single cohesive calendar header.
   * **Multi-Week / Arbitrary Date Navigation:** Controls `[ ◀ Prev Week | 📅 20–26 Aug 2026 | Next Week ▶ | Today ]` allow infinite time-travel across weeks/months rather than hitting a 7-day wall.
   * **Active Day Selection Binding:** Clicking any day column in the matrix highlights that date and dynamically updates the specific safety alerts, timers, and action cards below for that selected date.

---

### Tier 2: Core Domain Explorers

1. **Body Composition Explorer (Protocol 01 & Withings):**
   * **9-Year Historical Dataset:** 995 readings carrying bioimpedance.
   * **Time-Horizon Granularity & Time-Travel Engine (`ADR-006`):**
     - **Granularity Presets:** `[ All ]`, `[ 1 Year ]`, `[ Quarter (3M) ]`, `[ Month (1M) ]`, `[ Week (7D) ]`.
     - **Contextual Time-Travel Pager:** `[ ◀ Prev Period | 📅 Current Range | Next Period ▶ | Today ]` allowing rapid pagination across years, quarters, months, and weeks.
     - **Smart Resolution Scaling:** In micro-views (*Week / Month / Quarter*), automatically emphasizes **7-day EMA and discrete daily weigh-in points** for high-fidelity fluid tracking; in macro-views (*Year / All*), emphasizes **30-day EMA smoothing** with raw points suppressed.
   * **Dynamic Physiological Reference Envelopes (`ADR-008`):**
     - **Muscle Mass Normal Corridor ($74.0\%\text{ to }87.0\%\text{ of Weight}$):** Renders a shaded translucent green normative band behind the Muscle Mass curve. As body weight changes over time, the corridor shifts dynamically, providing instant visual feedback on whether muscularity is within or exceeding reference standards ($54.5\text{–}65.3\text{ kg}$).
     - **Fat Mass Normal Corridor ($11.0\%\text{ to }22.0\%\text{ of Weight}$):** Renders a shaded normative band behind Fat Mass ($8.1\text{–}16.5\text{ kg}$), indicating proximity to the optimal clinical body fat threshold.
   * **Proportional Tissue Mass Decomposition & Adaptive Y-Scale (`ADR-007`):**
     - **First-Class Fat Mass ($\text{kg}$):** Computes and plots absolute fat mass in physical kilograms ($\text{Weight} \times \text{Fat}\% \approx 14.9\text{ kg}$) alongside Muscle Mass ($56.5\text{ kg}$) and Total Weight ($75.0\text{ kg}$).
     - **Adaptive Y-Axis Floor with Breathing Room:**
       - `[ 🏋️ Weight & Muscle ]` view: Sets Y-axis minimum to **`40 kg`**, giving ~16 kg of visual breathing room below Muscle Mass so it sits comfortably in the canvas rather than squished against the bottom border.
       - `[ 📉 Fat Mass & % ]` view: Sets Y-axis floor to **`0 kg`**, framing Fat Mass ($14.9\text{ kg}$) with dual-axis Fat % ($19.9\%$).
       - `[ 🌐 Overview ]` view: Sets Y-axis from **`0 to 90 kg`**, rendering the full anatomical 3-tier tissue breakdown (Fat $\rightarrow$ Muscle $\rightarrow$ Total Weight) in true physical proportion.
   * **De-Noised Dual Filtering & Focus Tabs (`ADR-005`):**
     - Raw scale weigh-ins hidden by default in macro-views (`hidden: true`) to eliminate visual noise; available on-demand via legend click.
     - **Focus View Switcher Tabs:**
       - `[ 🏋️ Weight & Muscle ]`: Clean 2-line view of Total Weight and Muscle Mass with 40 kg floor.
       - `[ 🧬 FFMI & Cycle Target ]`: Focused view of FFMI with Athletic ($20.0$) and 16-Wk Target ($21.5$) benchmark corridors.
       - `[ 📉 Fat Mass & % ]`: Focused view of Fat Mass ($\text{kg}$) and Fat % reduction.
       - `[ 🌐 Overview ]`: Multi-metric view.
   * **FFMI Primary Muscularity Metric & Cycle Targets (`ADR-004`):**
     - User height stored dynamically in `users` registry.
     - **FFMI Curve (kg/m²):** Tracks pure contractile lean tissue development over 9 years, eliminating misleading BMI "overweight" false positives.
     - **Athletic Baseline Corridor ($20.0\text{ kg/m}^2$):** Benchmark line representing well-trained athletic status.
     - **16-Week Cycle Target Corridor ($21.5\text{ kg/m}^2$):** Target boundary representing $+2.1\text{ kg}$ dry muscle gain goal.
     - **Advanced Athletic Threshold ($22.0\text{ kg/m}^2$):** Long-term recomposition ceiling.
     - **Demographics Pill:** BMI retained as a compact read-only value (`BMI 25.1`) in the header stats for general medical records.
   * **Integrity Label:** *"Withings Bioimpedance Trend (Directionally valid; absolute values carry $\pm 2\text{ kg}$ impedance noise)"*.

2. **Nutrition Partitioning Explorer (Protocol 03 & MyFitnessPal):**
   * **Target Overlays:** Protein target $2.2\text{–}2.8\text{ g/kg}$ (~165–210 g/day) and Caloric Surplus target ($+500\text{ to }+800\text{ kcal}$).
   * **Strict Complete-Day Partitioning:** Averages computed exclusively over verified complete days (`is_complete = 1`, $>1200\text{ kcal} \land \ge 3\text{ items}$).
   * **Coverage Denominator:** Prominently displays: *"77.8g protein avg — from 33 of 89 complete days"*.
   * **Recent Compliance Callout:** Highlights recent on-target performance (Aug 24: 150.6 g, Aug 25: 171.1 g).

3. **Autonomic Balance & Cardio Recovery Explorer (Protocol 02 & Samsung):**
   * **Rolling 7-Day HRV Corridor:** Continuous RMSSD with dynamic $\pm 1.0\text{ SD}$ normal baseline envelope.
   * **Resting Heart Rate Trend:** Nighttime resting HR tracking autonomic recovery.
   * **60-Second Post-Exercise HR Recovery (HRR-60):** Interactive recovery curves from `heart_rate_recovery_samples` tracking vagal reactivation and cardiovascular fitness post-preworkout_compound.

4. **Sleep Architecture Explorer (Gated):**
   * **Activation Gate:** Unlocks only when $\ge 4\text{ of the last 7 nights}$ are recorded in `sleep_sessions`.
   * **Gated State:** Displays: *"Sleep: 1 of last 7 nights recorded. Not enough data to trend. Wear the watch overnight."*
   * **Unlocked State:** Stacked stage hypnograms: Deep Sleep ($\ge 60\text{m}$ target for GH/repair), REM Sleep ($\ge 75\text{m}$), Light Sleep, and Nocturnal Awakenings.

5. **Bloodwork & Safety Radar (active protocol):**
   * **Milestone Countdown:** Week 0 (Baseline), Week 6 (Steady-State Bloods), Week 16 (Peak Load), Week 24 (Maintenance).
   * **Hematocrit (HCT) Safety Gauge:** Hard red line at **$>54\%$** (Clinical prompt: urgent prescriber review / phlebotomy evaluation trigger; dashboard never prescribes medication changes per AGENTS.md §3.1).

---

### Tier 3: Multi-Year Longitudinal Autobiography (2014–2026)

* **The 9-Year Master Arc:** Visualizing the core physical transformation from 2017 to 2026: **Fat 27.5% $\rightarrow$ 20.6%, Muscle 60.6 $\rightarrow$ 56.0 kg**.
* **Life Eras Overlay:** Shaded background bands representing tagged historical epochs (`life_eras`), connecting legacy telemetry with retrospective intent.

---

## 4. Database Schema Specification (Schema v5)

```sql
-- 1. Historical Life Eras & Contextual Epochs
CREATE TABLE IF NOT EXISTS life_eras (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,                  -- e.g. '2022 Deload & Recomp', '16-Week Mass Cycle 2026'
    category TEXT NOT NULL,              -- 'hypertrophy', 'cutting', 'maintenance', 'travel', 'injury', 'protocol'
    start_date TEXT NOT NULL,            -- 'YYYY-MM-DD'
    end_date TEXT,                       -- 'YYYY-MM-DD' (NULL if ongoing)
    is_fuzzy INTEGER DEFAULT 0,          -- 1 if estimated retrospectively, 0 if exact
    intent_summary TEXT,                 -- Goals and strategy
    compounds_and_doses TEXT,            -- e.g. 'Creatine 5g, Omega-3 2g' or 'None'
    dietary_strategy TEXT,               -- e.g. 'Low-carb, -400 kcal deficit'
    training_focus TEXT,                 -- e.g. 'eGym Adaptive', 'Walking + Bodyweight'
    retrospective_learnings TEXT         -- Observed physiological adaptations
);

-- 2. Interventions & Compounds Catalog
CREATE TABLE IF NOT EXISTS interventions_catalog (
    id TEXT PRIMARY KEY,                 -- 'creatine_monohydrate', 'omega3_epd_dha', 'creatine_am', 'preworkout_compound'
    name TEXT NOT NULL,                  -- 'Creatine Monohydrate Solution'
    category TEXT NOT NULL,              -- 'pharma', 'supplement', 'behavior', 'diagnostic'
    default_dose TEXT,                   -- '1 capsule (5 g)'
    frequency_hours INTEGER,          -- 72 for interval compounds, 24 for daily supplements
    unit_cost REAL,                      -- Cost per dose / purchase (£)
    pack_size INTEGER,                   -- Total doses/units per purchase
    pack_price REAL,                     -- Purchase price (£)
    shelf_life_days INTEGER,             -- Expiration window post-opening
    current_inventory_units REAL,        -- Current doses in stock
    notes TEXT                           -- Clinical/storage instructions
);

-- 3. Administration & Event Log (Fact Layer)
CREATE TABLE IF NOT EXISTS intervention_events (
    id TEXT PRIMARY KEY,                 -- UUID
    intervention_id TEXT NOT NULL,       -- FK -> interventions_catalog
    timestamp DATETIME NOT NULL,         -- When taken / performed
    date TEXT NOT NULL,                  -- 'YYYY-MM-DD' (local day)
    event_type TEXT NOT NULL,            -- 'dose_taken', 'deliberate_skip', 'purchase', 'symptom_logged'
    actual_dose TEXT,                    -- Dose administered
    divergence_code TEXT NOT NULL,       -- 'adherent', 'late', 'missed', 'deliberate_skip', 'dose_changed', 'depleted'
    precision TEXT NOT NULL,             -- 'exact', 'hour', 'morning', 'approximate'
    confidence TEXT NOT NULL,            -- 'exact', 'recalled', 'inferred'
    total_cost REAL,                     -- Populated on purchase events (£)
    receipt_file_path TEXT,              -- Pointer to archived receipt/invoice
    notes TEXT,
    FOREIGN KEY(intervention_id) REFERENCES interventions_catalog(id)
);

-- 4. User Profile & Demographics Registry (Schema v6)
CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,                 -- e.g. 'eneas', 'USER_DEFAULT'
    name TEXT NOT NULL,
    withings_user_id TEXT,
    dob TEXT,                            -- 'YYYY-MM-DD'
    age INTEGER,
    height_cm REAL NOT NULL,
    gender TEXT DEFAULT 'male',
    measurement_system TEXT DEFAULT 'metric',
    active INTEGER DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME
);
```

---

## 5. Stage A Implementation Boundaries

### Included in Stage A (Immediate Build):
1. **Schema v6 Migration:** Apply `users` profile enhancements (`height_cm`, `dob`, `measurement_system`), `life_eras`, `interventions_catalog`, and `intervention_events` to `health_dashboard.db`. Seed the user profile (height, date of birth).
2. **Data Exporter Engine (`scripts/export_dashboard_data.py`):** Compute 7d/30d EMAs, BMI threshold boundaries (25.0 and 30.0 kg/m²), FFMI athletic context, coverage ratios, rolling sleep gating, 7-day adherence history, and Tier 1 HUD state.
3. **Local Dashboard UI (`dashboard/index.html`):**
   * Tier 1 Morning Command HUD (Weekly protocol matrix, multi-week navigator, $+1.37\text{ kg}$ E2 warning, 72h/3d timers, 1-tap dose chips, data-health status pill).
   * Tier 2 Body Comp (9-year EMA with BMI 25/30 threshold reference bands and FFMI badge), Nutrition (complete-days only), Autonomic/HRR-60, and Gated Sleep module.
   * Tier 3 9-Year Historical Timeline with Life Eras.

### Explicitly Deferred from Stage A:
* ❌ Abstract dynamic rules DSL / compiler.
* ❌ Multimodal automated receipt OCR pipeline.
* ❌ Causal sandbox simulation.
* ❌ Micro-cost-per-kg optimization ratios.
* ❌ Arbitrary Historical Milestone Annotations: Deferred to evidence-based study; distinguishing Peak Scale Weight (e.g. 93.4 kg in 2020) vs Peak FFMI / Pure Contractile Lean Mass vs Protocol Starts requires rigorous clinical definition and explicit owner confirmation before pinning to graph canvas.

---

## 6. Architectural Decision Records (ADR Log)

| ADR ID | Date | Decision & Feature Added | Clinical & UX Rationale | Status |
|---|---|---|---|---|
| `ADR-001` | 2026-08-28 | **7-Day Protocol Adherence Matrix & Historical Date Navigation** | Eliminates the "Today-only" blindspot, enabling retroactive dose inspection, audit, and backfilling with `confidence: recalled`. | ✅ Adopted |
| `ADR-002` | 2026-08-28 | **Co-Located Weekly Protocol Matrix & Infinite Week Navigation** | Solves the spatial disconnect between detached header controls and the matrix; removes the 7-day wall by enabling infinite week-by-week and date picker navigation with selected-day detail binding. | ✅ Adopted |
| `ADR-003` | 2026-08-28 | **User Demographics Registry (Schema v6) & Dynamic BMI/FFMI Thresholds** | Formalizes core user demographics (height, date of birth, measurement system) in `users` table; overlays BMI 25.0 and BMI 30.0 reference lines and FFMI athletic context on 9-Year Body Comp chart. | ✅ Adopted |
| `ADR-004` | 2026-08-28 | **FFMI Primary Muscularity Metric & Target Corridors on Body Comp Explorer** | Replaces arbitrary BMI graph threshold lines with FFMI 30d EMA curve, Athletic baseline corridor ($20.0$), and 16-Week cycle target ($21.5\text{ kg/m}^2$), retaining BMI purely as a compact read-only demographic value. | ✅ Adopted |
| `ADR-005` | 2026-08-28 | **Clinical Light/Dark Theme Engine & Body Comp De-Noising Focus Tabs** | Eliminates "heavy black" eye strain with a clean Apple-Health-style Clinical Light Mode default (`#F8FAFC`) + Refined Dark Mode toggle; de-noises the Body Comp chart with `[ Weight & Muscle ]`, `[ FFMI & Target ]`, `[ Fat % ]`, and `[ Overview ]` focus tabs, hiding raw point noise by default. | ✅ Adopted |
| `ADR-006` | 2026-08-28 | **Body Comp Time-Horizon Granularity Engine & Time-Travel Navigator** | Implements multi-horizon zoom toggles (`All`, `Year`, `Quarter`, `Month`, `Week`) with interactive period pagers (`[ ◀ | 📅 Range | ▶ ]`) and smart resolution adaptation (7d EMA + daily dots in micro-views; 30d EMA in macro-views). | ✅ Adopted |
| `ADR-007` | 2026-08-28 | **Proportional Tissue Mass Decomposition (Fat Mass kg) & Adaptive Y-Scale** | Computes and displays absolute Fat Mass in physical kilograms (~14.9 kg) alongside Muscle Mass (~56.5 kg) and Total Weight (~75.0 kg); dynamically expands Y-axis bottom floor (40 kg for Weight/Muscle to prevent floor squishing; 0–90 kg for All-in-One proportional tissue view). | ✅ Adopted |
| `ADR-008` | 2026-08-28 | **Dynamic Physiological Reference Envelopes (Muscle 74%–87% & Fat 11%–22%)** | Implements dynamic normative background shading corridors for Muscle Mass ($74.0\%\text{–}87.0\%\text{ of weight}$) and Fat Mass ($11.0\%\text{–}22.0\%\text{ of weight}$) that shift organically as body weight changes, visualizing tissue status against clinical benchmarks. | ✅ Adopted |
| `ADR-009` | 2026-08-28 | **Global 0–95 kg Mass Scale Grounding, 30d EMA Corridor Smoothing & Shaded FFMI Target Band** | (1) Unifies left Y-axis to 0–95 kg across all mass views (`Weight & Muscle`, `Fat Mass & %`, `Overview`), eliminating legend toggle clipping and scale disorientation. (2) Computes physiological envelopes from 30d/7d EMA weight, eliminating jagged tooth noise from raw weigh-in fluctuations. (3) Connects FFMI Athletic Baseline (20.0) and Cycle Target (21.5) with a dedicated shaded target growth corridor. | ✅ Adopted |
| `ADR-011` | 2026-08-28 | **Constant Vertical Grid Lines, Direct Curve Crossing Chips & Dynamic Top HUD Sync** | (1) Renders constant subtle vertical dashed guide lines at every plotted X-axis date tick. (2) Implements `crossingValueChipsPlugin` rendering frosted micro-pill badges directly at curve intersections with automatic vertical collision avoidance (<16px separation). (3) Maintains dynamic hover guide line with live top HUD 5-card synchronization during scrub, resetting on mouse leave. (4) Defers arbitrary milestone pins to future evidence-based study with owner confirmation. | ✅ Adopted |
| `ADR-012` | 2026-08-28 | **SDK Record Identity, Natural-Key Deduplication & Computed Adherence** | (1) The SDK payload contract MUST carry `Metadata.id` as `data_uid`. Payloads minting uids from the sync epoch caused one night of sleep to be stored three times, and `daily_summary` reported 1722.5 min — 28.7 h. (2) `ingest_sdk_payload.resolve_canonical_id()` matches incoming records on PHYSIOLOGICAL identity (local day + measured values), not timestamp, since the collector varies timestamps between syncs as well as uids. Keys mirror `SDK_DUPLICATE_KEYS` in `migrate_v7.py` exactly. (3) All dedup is scoped to `source='samsung_health_sdk'`; export rows legitimately repeat per hour across devices (387 such groups) and are never merged. (4) SDK rows persist `utc_offset` and derive `local_date`, with a documented fallback to the last known offset when the payload omits timezone. (5) `meds_compliance_pct` is computed from `intervention_events` and renders `null` / "No doses logged" when empty — absence must never read as 100%. | ✅ Adopted |
| `ADR-013` | 2026-08-28 | **Deterministic Export — `data_current_through` replaces `generated_at`** | The exporter injects its payload into `dashboard/index.html`. A wall-clock `generated_at` changed that tracked file on every run, so the verification AGENTS.md §1.4 mandates before merging dirtied the tree §1.3 requires to be clean — and generated a trail of content-free `chore(data): refresh dashboard telemetry payload timestamp` commits. The payload now carries **`data_current_through`**, the newest real measurement across the fact tables, which is stable when the data has not changed and is the more useful fact: *"generated 13:42"* only says the script ran, *"current through 11:09"* says whether the last sync landed. Verified byte-identical across consecutive runs. **Do not reintroduce a run-time stamp** — see AGENTS.md §1.3. | ✅ Adopted |
| `ADR-014` | 2026-08-28 | **Dual-Key Deletes — `natural_key` Persisted and Honoured** | Completes ADR-012's Mac half. The collector emitted `natural_key` alongside `data_uid`; the ingester never read it and no table stored it, so deletes matched on `data_uid` alone. Verified before the fix: a SLEEP tombstone carrying a real `Metadata.id` left the table at 1158 rows **and still returned `"DELETED"`** — reporting success into an empty result set, which is how the broken path stayed invisible. A HEART_RATE tombstone raised `OperationalError: no such column: record_id` (the column is `parent_id`). Schema v8 adds and indexes `natural_key` on all six delete-target tables; deletes now match `id OR natural_key`; a tombstone matching nothing returns **`DELETE_NOOP`** and logs it; and `resolve_by_natural_key()` is preferred over the per-table physiological keys, retiring the bespoke-key-per-type problem. | ✅ Adopted |
| `ADR-015` | 2026-08-28 | **Height-Normalized FFMI (Kouri et al. 1995), Literature Muscularity Tiers & Longevity Corridors** | (1) Standardizes FFMI using Kouri et al.'s allometric height normalization ($\text{FFMI}_{\text{norm}} = \text{FFMI}_{\text{raw}} + 6.3 \times (1.80 - \text{Height})$), eliminating systematic muscularity underestimation (+0.44 kg/m² for height 178 cm). (2) Stratifies muscularity tiers against clinical literature: Sedentary (<18.0), Active (18.0–19.9), Intermediate (20.0–21.9), Advanced (22.0–22.9), Elite (23.0–24.9), Ceiling (25.0). (3) Recalibrates Body Fat reference envelope to the Optimal Fitness & Longevity Band ($14.0\%–18.0\%$). (4) Embeds EWGSOP2 sarcopenia staging thresholds ($<7.0\text{ kg/m}^2$) and longevity buffer target ($>7.5\text{ kg/m}^2$). (5) Decouples and calibrates the discrete 16-week Hypertrophy Cycle Goal to **$64.0\text{ kg}$ FFM / Normalized FFMI $21.8\text{ kg/m}^2$** ($+3.8\text{ kg}$ lean accrual) with long-term target at **$65.0\text{ kg}$ FFM / FFMI $22.2\text{ kg/m}^2$** (*Advanced Natural Lifter*). | ✅ Adopted |
| `ADR-016` | 2026-08-29 | **Data-Anchored Rolling Windows & Clinical Null-Honesty** | Extends ADR-013. (1) Every rolling-window query in the exporter anchors to the **data itself** (`MAX(date) FROM daily_summary`), never to a hardcoded literal or `date('now')` — a wall-clock anchor would change output at midnight with no data change, breaking the byte-determinism §1.3/§1.4 verification depends on, while a literal silently freezes (four sites had frozen at `'2026-08-26'`). (2) Clinical display values are **derived or null, never fabricated**: unlogged compound-days export `status: "unlogged"` and render "Not Logged" (a client-side duplicate of the fabricated demonstration schedule was removed from `matrix.js` in the same change); dose timers derive from the latest `intervention_events` row or export `null`; the E2 delta is computed from ≥48 h of actual elapsed time inside a 36–72 h validity window with an `insufficient_data` flag, replacing the row-offset "48 h" that could compare same-day or week-apart readings. (3) EMAs run on elapsed calendar time over daily-collapsed readings (`halflife` 2.41 d / 10.40 d ≡ span 7/30) so a post-gap EMA sits on the raw value instead of dragging weeks-old momentum. | ✅ Adopted |
| `ADR-017` | 2026-08-29 | **Enforcing Envelope SHA-256 via Records Byte-Span Rehash & Inbox Quarantine** | The Tier-1 integrity check had validated only the *presence* of `content_sha256`. Now enforcing: the device hashes the compact kotlinx serialization of the record list before embedding it, the server persists the POST body verbatim, so the `"records": [...]` byte span inside the stored envelope is byte-identical to what the device hashed — rehashing that exact span (string/escape-aware bracket scan) reproduces the hash without reimplementing kotlinx JSON in Python. Proven against **99/99 real archived payloads** before enforcement; the two hand-crafted test fixtures with fabricated hashes correctly fail. Payloads that can never ingest (SHA mismatch, invalid JSON, schema-invalid, or every record erroring) move to `data/inbox/failed/` instead of lingering in the inbox re-failing on every run; single bad records are isolated per-record (`INGEST_ERROR`) without aborting the batch. | ✅ Adopted |
| `ADR-018` | 2026-09-04 | **NAS 24/7 Source of Truth (Topology B), Autonomous Withings Polling & Reverse Dev Sync** | (1) Adopts Topology B: QNAP NAS is the 24/7 ingestion source of truth over Tailscale (`health-dashboard.local:8088`), eliminating ingestion gaps when the Mac sleeps or travels. (2) Withings OAuth tokens reside exclusively on the NAS volume (`/app/config/withings_tokens.json`) to prevent rotating refresh token collisions; `sync_server.py` runs a 6h background polling loop, exposes `POST /api/sync/withings`, records failure state in `withings_sync_status.json` and `/api/status`, and provides an interactive "Sync Scale" trigger on the dashboard HUD. (3) `scripts/deploy_to_nas.sh` gains `--restart`, `--rebuild-db`, pre-flight QNAP load average guard (>4.0 soft check), commit stamping, and `/api/status` post-deploy verification. (4) `scripts/sync_from_nas.sh` implements one-way pull of raw archives (`archive/samsung_health_sdk/` and `data/records/withings/`) with local DB rebuild (~40s), strictly forbidding `.db` binary transfer over the network. | ✅ Adopted |
| `ADR-019` | 2026-09-04 | **Movable & Collapsible Widget Engine, Column Spanning, Top Control Banner & LocalStorage Persistence** | (1) Structures dashboard into layout-aware tier containers (`tier-1-section` and `tier-2-section`) and modular widget zones (`zone-tier1-actions`, `zone-explorers`, `widget-adherence-matrix`). (2) Establishes a dedicated stationary Top Layout Studio Control Banner below main navigation, isolating global actions (Save, Reset, Expand/Collapse All, Swap Tiers) from tier headers. (3) Native HTML5 drag-and-drop with handle mousedown activation, direct atomic `moveBefore()` DOM slot positioning (avoiding grid jitter), and accessible one-tap nudge buttons (`◀`/`▶` and `▲`/`▼`). (4) Full Tier-level swapping (`swapTiers()`) via toolbar and header buttons. (5) Dynamic 1-col / 2-col column span toggle (`⇲`/`⇱`) with `ChartsManager.resizeCharts()` canvas reflow. (6) Clinical collapsed summary preview chips (e.g. Weight/FFMI/BF%, protein avg, sleep gating). Persists under `health_dashboard_widget_layout_v1`. | ✅ Adopted |
| `ADR-020` | 2026-09-04 | **Outcome-Driven 3-Tier Hierarchy (Outcomes → Readiness → Inputs) & Macro-Cycle Milestone Bar Relocation** | (1) Transitions system from an initial input-centric logging interface to an outcome-driven clinical command center: Tier 1 is Physical Adaptation & Body Composition (Outcomes); Tier 2 is Autonomic Recovery & Sleep Architecture (Readiness); Tier 3 is Protocol Operations, Compliance & Safety (Inputs). (2) Relocates the active protocol cycle badge from the global app header to the head of Tier 3, upgrading it from a static text pill into a rich clinical Macro-Protocol Milestone Bar with a visual progress track and timeline milestones (Deload Due W6, Bloods Due W8). (3) Global header remains a pure executive bar (Logo, Title, Scale Sync, Theme Switcher), while Command Strip anchors live telemetry freshness and Layout Studio. (4) Enables Tier 3 to remain collapsed during analytical reviews with a dense summary preview chip, focusing 90% of screen real estate on physiological telemetry. | ✅ Adopted |
| `ADR-021` | 2026-09-04 | **Unified 2-Tier Master Architecture, Side-by-Side Packing (Nutrition & Autonomic) & Domain Viewport Tabs** | (1) Consolidates explorer cards into a single 2-column grid (`#zone-explorers`) within Tier 1 (`#tier-1-section`), allowing 1-column cards (`exp-nutrition` and `exp-autonomic`) to pack side-by-side without 50% row voids or intermediate tier banners, recovering ~550px of vertical screen real estate. (2) Tier 2 (`#tier-2-section`) houses all protocol operations, compliance, and safety controls (Milestone Bar, Adherence Matrix, Day Detail, High-E2 Alert, and Action Cards). (3) Command Strip Viewport Tabs (`[ All Tiers | Body Comp | Recovery | Protocol ]`) provide domain-scoped filtering directly within `#zone-explorers` by dynamically toggling card visibility while auto-expanding relevant tiers. (4) WidgetManager layout engine v4 provides backwards-compatible migration for v1/v2/v3 localStorage payloads. | ✅ Adopted |
| `ADR-022` | 2026-09-05 | **Longitudinal Bloodwork Ingestion, Draw Readiness & Biomarker Matrix (Schema v9)** | (1) Ingests multi-year venous bloodwork (86 analytes, 18 categories) into `lab_results` (Schema v9) with 17 canonical/provenance columns, comparator separation (`<0.3`), and clinical assertions A1–A4. (2) Draw Readiness card surfaces the Universal 12-Biomarker Preventive Panel status, LC-MS/MS assay standardization recommendation, and periodic draw preparation. (3) Clinical Consultation Briefing Sheet toggling SI Metric (`nmol/L`, `g/L`, `mmol/L`) vs Conventional (`ng/dL`, `g/dL`, `mg/dL`) with last 5 draws, 3-yr min/max, and accredited lab bounds. (4) Biomarker Matrix with bullet graphs, triage sorting, recency badges, and discrete scatter drill-downs (zero interpolated continuous curves per `AGENTS.md` §3.5). (5) Longitudinal biomarker clearance module tracking empirical clearance dynamics. | ✅ Adopted |
| `ADR-023` | 2026-09-05 | **Haematocrit Telemetry Surveillance Refutation & Pure Laboratory Anchoring** | (1) Stage 5 analytical query spike across continuous biometric samples and longitudinal HCT draws decisively refuted wearable autonomic surveillance ($r = +0.084, p = 0.794$ for RHR; $r = +0.043, p = 0.896$ for stress). Even during haematocrit peaks, resting HR remained within its usual band and stress was normal to tranquil. (2) Speculative wearable proxy gauge permanently killed to eliminate life-threatening false reassurance. (3) Haematocrit surveillance remains 100% anchored to laboratory venous blood draws in `lab_results`. | ✅ Adopted |
| `ADR-024` | 2026-09-05 | **Subagent Delegation, Model Tier Routing, Parallelism & Unified Quality Gates** | (1) Codifies subagent delegation based on Sept 2026 frontier benchmarks: Gemini 3.8 Flash High (73.7% DeepSWE v1.1, ~90% Terminal-Bench 2.1) is Google's primary software engineering model, outperforming legacy Gemini 3.1 Pro on coding. (2) 3-tier allocation: Tier 1 (`flash_lite`) for mechanical data execution with 1-strike escalation; Tier 2 (`flash`) for standard code/scripts; Tier 3 (`inherit` / `pro`) for architecture and multi-tab state UI. (3) Authorizes autonomous parallel specialist subagents with the Zero Collision Invariant (disjoint write scopes). Mandatory pre-flight chat disclosure (Role, Tier, Scope, Rationale) before invocation; mandatory Agent Topology section in all `implementation_plan.md` design plans. (4) Standardizes engineering release gating under `scripts/run_quality_gates.py` (JS syntax, HTML hook protection, scratch migration idempotency, live DB isolation, exporter determinism, and clean tree check). | ✅ Adopted |
| `ADR-025` | 2026-09-05 | **Responsive Design Architecture, Mobile Reframing Directive & Viewport Ergonomics** | (1) Adopts "Responsive Design as a Directive" eliminating horizontal viewport bleed on mobile devices: root `overflow-x: hidden`, canvas isolation in `relative w-full min-w-0 overflow-hidden chart-container-adaptive`, and grid containment. (2) Two-phase reframing engine in `ResponsiveManager` (`dashboard/js/responsive.js`) responding to `screen.orientation` and `orientationchange` with immediate RAF reflow + 150ms settling timer, dynamically synchronizing header offset and recalculating Chart.js canvas dimensions. (3) Sticky frozen compound column (`sticky left-0`) on the Weekly Adherence Matrix with solid background isolation and touch auto-scroll to today. (4) Mobile-ergonomic Command Strip: 4-grid segmented Viewport Tabs, horizontally scrolling telemetry chips, and collapsible mobile studio drawer (`#mobile-studio-drawer`). (5) Touch target ergonomics (WCAG 2.2 AA / Apple HIG >= 40px bounding box), hiding desktop-only drag handles on coarse pointers to eliminate trapped swipes, and guarding Chart.js resize against hidden elements (`offsetParent !== null`). | ✅ Adopted |
| `ADR-026` | 2026-09-05 | **Physical Adaptation Triad, Exercise Telemetry, Decoupled Protocol Hub & Fast Affirmative Attestation** | (1) De-prioritizes dose logging and establishes the Physical Adaptation Triad (Body Composition as Outcome, Nutrition as Fuel, Exercise as Mechanical Stimulus) as the primary landing workspace (`#workspace-adaptation`), resolving single-page vertical scroll bloat (~4,800px) into 5 focused modular workspaces (`Physical Adaptation`, `Recovery & Sleep`, `Labs & Bloods`, `History & Eras`, and `Protocol Operations`). (2) Evicts retrospective autobiography to a dedicated `History & Eras` workspace. (3) Engineers Exercise & Training Explorer (`exp-exercise`, `dashboard/js/exercise.js`) combining 7-day volume cadence (total sessions vs 4-session goal, lifting sessions, active kcal burn, W6 deload radar), mechanical split details, 15-session interactive workout pager, intra-workout Chart.js HR curves, 5 cardiac zones, and post-exercise vagal recovery drop (HRR-60). (4) Implements fast daily protocol administration via global header button `[ ⚡ Log Dose 💊 ]` and smart affirmative attestation modal (`#fast-dose-modal`, `dashboard/js/matrix.js`) with pre-checked cadence intelligence, writing to `intervention_events` only upon human click in compliance with `AGENTS.md` §3.4. (5) Upgrades `WidgetManager` to layout v5 supporting workspace switching (`setWorkspaceTab()`) and cross-workspace card protection. | ✅ Adopted |
| `ADR-027` | 2026-09-05 | **Clean Fact Store Rebuild Protocol, SQLite Multi-File Cleanup & Daemon Isolation** | (1) Enforces `--clean` rebuild semantics: `scripts/rebuild_database.py` deletes all SQLite artifacts (`.db`, `.db-wal`, `.db-shm`, `.db-journal`) to eliminate salt/header index collisions and `OperationalError: disk I/O error` when rebuilding from WAL mode. (2) Re-proves that historical ingestion assertions (`assert written_sleep == len(sessions)`) require starting from an empty schema state, upholding the architectural invariant that `data/health_dashboard.db` is a derived artifact reconstructed from authoritative raw inputs. (3) `scripts/sync_from_nas.sh` manages local daemon isolation: automatically pauses the macOS LaunchAgent (`com.healthdashboard.sync` via `service_manager.sh stop`) during rebuilds to avoid lock contention on `.db-shm`, resuming (`start`) upon completion. (4) Pulls live `withings_sync_status.json` from NAS to preserve sync telemetry on dev machines. | ✅ Adopted |
| `ADR-028` | 2026-09-05 | **Durable Intervention Event Store, Append-Only JSON Records & Repository-Wide Event Sourcing/CQRS Policy** | (1) Adopts mandatory repository policy: Event Sourcing with CQRS is an absolute architectural invariant (`AGENTS.md` §1.7). The raw append-only files (`data/records/` and `archive/`) are the sole System of Record; SQLite is strictly a disposable read projection that must be safely wipeable with zero data loss. (2) Closes the dose logging persistence gap: `scripts/sync_server.py` (`POST /api/log_dose`) atomically persists canonical raw JSON events to `data/records/interventions/<date>_<compound_id>_<event_id_prefix>.json` before materializing into SQLite. (3) `scripts/rebuild_database.py` adds Stage 4b replaying all intervention JSON records into `intervention_events` on `--clean` rebuilds. (4) `scripts/sync_from_nas.sh` syncs `data/records/interventions/` across devices so doses logged on the NAS/phone are preserved on local development environments. | ✅ Adopted |
| `ADR-029` | 2026-09-07 | **Dual Telemetry Freshness, Background Polling & Withings 503 Resiliency** | (1) Decouples Sensor Freshness (latest biometric measurement fact in SQLite, `data_current_through`) from Sync Freshness / Pipeline Contact (timestamp of last payload ingestion from Android companion app or Withings scale, `last_sdk_sync`, `last_scale_sync`), distinguishing an idle watch on charger from a broken pipeline. (2) Command Strip features Dual Freshness badge (`#telemetry-freshness-badge`, `#freshness-status-dot`, `#generated-timestamp`, `#freshness-sync-chip`) with 3-tier age coloring (<4h emerald, 4–12h amber, >12h rose pulse) and detailed hover telemetry breakdown. (3) `scripts/sync_withings.py` hardened against Withings API 503/transient outages with exponential retry backoff, prevents transient errors from triggering invalid token refreshes, handles permanent token revocation cleanly as `auth_revoked`, preserves `last_success` across failures, and enforces NAS token authority (ADR-018). (4) `sync_server.py` exposes `telemetry_freshness` on `GET /api/status` and proxies local dev Withings sync requests to the NAS hub. (5) `dashboard/js/app.js` adds non-blocking `#toast-container` alerts, dynamic Withings button states (`Scale Synced`, `Scale (503)`, `Auth Expired`, `NAS Scale Hub`), live cache-busting HTTP loading (`loadData(forceFetch)`), and 3-minute background telemetry polling with `document.visibilityState` tab pause/resume guard. | ✅ Adopted |
| `ADR-030` | 2026-09-08 | **Companion App Dual-Endpoint Decoupling, Visual Intake (Zero-Toggle) & Remote Dev Sync Webhook** | (1) Decouples primary and fallback ports in Android `TokenRepository` and `SyncNetworkClient`, establishing `health-dashboard.local:8088` (NAS container) as primary and Mac dev server (`8765`) as fallback with 1-tap presets (`NAS_TAILSCALE`, `MAC_LAN`, `MAC_TAILSCALE`). (2) Builds zero-toggle "Snap & Send" visual capture front door (`ScanIngestionScreen.kt`), allowing camera/gallery intake with optional notes without mobile friction, staging raw images to `data/records/scans/` and recording immutable JSON events (`ADR-028`). (3) Surfaces live server telemetry freshness badges, pipeline health (`active`, `delayed`, `stalled`), and 10-item rolling sync history on companion dashboard. (4) Gates periodic sync with `NetworkType.CONNECTED`, adds configurable cadence (Manual, 1h, 3h, 6h, 12h), and implements auto-pruning to keep 3 latest payloads. (5) Exposes `POST /api/dev/sync_from_nas` on Mac `sync_server.py` to trigger reverse dev sync with 1 tap from companion. (6) Explicitly postpones companion dose attestation to dedicated workflow session. | ✅ Adopted |
| `ADR-031` | 2026-09-08 | **Multi-Set eGym & InBody Visual Telemetry Ingestion, Temporal Reconciliation & Review Queue** | (1) Establishes Event Sourcing base records for visual telemetry (`data/records/inbody/` and `data/records/egym/`) adhering to ADR-028 with 100% clean rebuild replay in `scripts/rebuild_database.py`. (2) Schema Migration v10 (`scripts/migrate_v10.py`) creates `egym_workouts` (supporting multi-set loads per machine console) and `inbody_scans` (SMM, Phase Angle, impedance, diurnal delta against morning Withings weight). (3) Visual Intake Engine (`scripts/reconcile_scans.py`) pairs Gemini Vision API headless extraction with temporal reconciliation matching Samsung Health `exercise_sessions` (±30m window) and Withings scale readings. (4) Exposes `/api/scans/pending`, `/api/scans/trigger_extract`, and `/api/scans/reconcile` on sync server with path-traversal-guarded image serving (`/api/scans/image/<file>`). (5) Exporter and Dashboard UI wired: header visual scans review queue modal (`#scans-review-modal`), Exercise Explorer multi-set mechanical stimulus cards (`#ex-mechanical-loads-container`), and Body Comp Explorer discrete InBody Gold Benchmark diamond points (SMM + Phase Angle + diurnal offset). (6) Migrates LaunchAgent stdout/stderr logging to `/tmp/sync_server.log` to resolve macOS TCC/com.apple.macl `EX_CONFIG` (78) spawning errors. | ✅ Adopted |
| `ADR-032` | 2026-09-09 | **Gym Companion App Historical Scans Reconciliation, Segmental Body Composition & Resistance Benchmarks (Schema v11)** | (1) Reconciles 86 historical scans from the gym companion app (connected via API to eGym machines and InBody Fitness Hub, NOT Samsung Health). (2) Schema Migration v11 (`scripts/migrate_v11.py`) adds segmental lean/fat mass columns and visceral fat rating to `inbody_scans`, and creates `egym_bioage` and `egym_muscle_balance` projection tables. (3) Historical data extraction via `scripts/ingest_gym_app_scans.py` ingests 9 InBody scans (preserving Phase Angle across 2024–2026 at 6.1°–6.8°), 8 multi-machine eGym circuits (68 sets across 8 machines), 4 BioAge records (Strength BioAge 21–25y vs chronological 36–39y), and latest Muscle Balance assessment. (4) Event Sourcing & CQRS invariant (ADR-028) strictly maintained via immutable event files in `data/records/inbody/` and `data/records/egym/`, with Stage 2 replay integrated into `scripts/rebuild_database.py`. (5) Exporter (`scripts/export_dashboard_data.py`) and Quality Gates (`scripts/run_quality_gates.py`) updated to verify v9, v10, and v11 schemas (570 lab, 68 egym, 9 inbody, 4 bioage, 1 balance rows; user_version=11). | ✅ Adopted |
| `ADR-033` | 2026-09-12 | **Unified Temporal Aggregation Engine, Nocturnal Cardiac Recovery & Dual-Tagged Physiological Intervals (Schema v12)** | (1) Consolidates fragmented temporal aggregation across the codebase into a single canonical engine (`scripts/aggregation_engine.py`): in-memory bisect interval matching, duration-weighted daily rollups (A16), 60-point workout curves, and diurnal scale fusion. (2) Schema Migration v12 (`scripts/migrate_v12.py`) adds `sleep_id TEXT` to `heart_rate_samples` with index `idx_hrs_sleep`, completing dual-tagging (`exercise_id` for workouts, `sleep_id` for sleep). (3) Extends `sleep_sessions` with nocturnal recovery metrics: `sleeping_hr_mean`, `sleeping_hr_min`, `sleeping_hr_max`, `sleeping_hr_nadir` (robust rolling 3-sample nadir), `nocturnal_dip_pct` (% drop from waking daytime average HR), and `sleeping_hr_samples_n`. (4) Wires canonical engine into `scripts/rebuild_database.py` (Stage 2 migration + Stage 5 aggregation), `scripts/ingest_sdk_payload.py` (incremental daily summary delegation + post-batch tagging/enrichment), and `scripts/ingest_samsung_health.py` (delegation of `tag_samples_with_exercise` and `rebuild_daily_summary`). (5) Exporter (`scripts/export_dashboard_data.py`) surfaces nocturnal HR recovery fields in `sleep_data`, and Quality Gates (`scripts/run_quality_gates.py`) verify v9–v12 idempotency asserting `user_version=12`. Serves as the prerequisite foundation for the Hermes Autonomous Health Intake Agent (Milestone 20). | ✅ Adopted |
| `ADR-034` | 2026-09-12 | **Autonomous Health Intake Agent, Gated Autonomy & Cost Accounting Isolation** | (1) Adopts Hybrid Architecture for visual scan extraction and reconciliation: a Dashboard-Native Health Intake Engine (`scripts/health_intake_agent.py`) paired with the Hermes Ambient Ambassador (`/share/Hermes/agents/health_intake.py` on NAS / `scripts/hermes_skills/health_intake.py`). (2) Gated Autonomy: Autonomous write authority (`reconciled_by: 'agent_auto'`) and Event Sourcing JSON emission (`data/records/{egym,inbody}/`) when confidence $\ge$ threshold (0.90 for eGym, 0.95 for InBody) and all domain plausibility checks pass; low confidence or anomalous scans are paused (`staged_pending_review`) with targeted review reasons for 1-click confirmation in the `#scans-review-modal` dashboard queue or conversational disambiguation via Hermes chat. (3) Cost & Billing Isolation: Uses dedicated API key (`HEALTH_AGENT_GEMINI_KEY`) isolated from Hermes general usage, recording every transaction's exact token count and USD cost into `data/records/telemetry/agent_usage_ledger.jsonl`. Configurable vision model (`HEALTH_AGENT_MODEL`, defaulting to `gemini-2.5-flash` at $0.10/1M input, $0.40/1M output). (4) Domain Plausibility Gates: eGym compares loads against historical Personal Bests ($\le \text{PB} \times 1.25$), checks rep brackets (4–35), and fuses mechanical loads with wearable `exercise_sessions` cardiac curves; InBody calculates diurnal offset ($\Delta$) against same-day morning Withings scale readings, bounding $+0.2\text{ kg} \le \Delta \le +3.0\text{ kg}$, and checks Phase Angle ($5.5^\circ–7.5^\circ$). (5) Lean Multi-Channel Intake (No Syncthing): Door 1 (Companion App) with automated watcher and asynchronous `sync_server.py` thread; Door 2 (Ambient Messaging) via Hermes skill for WhatsApp/Telegram drops. | ✅ Adopted |
| `ADR-035` | 2026-09-13 | **Health OS UX Overhaul: Mobile Navigation Dock, Studio Mode Gating, Chart De-Cluttering & Clinical Information Hierarchy** | (1) Resolves DOM persistence bug (P1.1) in `widgets.js` where Tier 2 (1,041px) leaked under all workspace tabs by removing root `<main>` DOM extraction. (2) Implements mobile bottom navigation dock (`#mobile-bottom-nav`) with 5 touch targets ($\ge 44$px bounding box) and thumb-zone accessibility, hiding desktop workspace tabs on mobile. (3) Studio Mode Gating (`WidgetManager.toggleStudioMode()`): drag handles and reorder buttons are hidden by default in reading mode, activated exclusively via Studio Mode toggle button (`#btn-toggle-studio-mode`). (4) Weekly Protocol Matrix cell states overhauled: future dates show subtle dot (`·`), today unlogged shows accent pill with `Log` prompt, past unlogged shows muted dash (`—`), and header displays `#matrix-weekly-adherence-summary` ("X of Y doses logged this week"). (5) Chart de-cluttering: `crossingValueChipsPlugin` throttled to crosshair hover and latest point; Body Comp legend filtered to 3 core series by default. (6) Sleep gating card converted to compact 1-line actionable banner. (7) Life Eras historical gap row (1,089 days) surfaced; duplicate subtitle removed. (8) Double payload elimination: `/api/status` probe skips redundant 1.7 MB JSON download when cache is fresh. (9) Universal typography upgrade: eliminates all `text-[9px]` and `text-[10px]` sub-minimum sizes across the interface. | ✅ Adopted |
| `ADR-036` | 2026-09-13 | **Health OS Code Review Remediation: Pipeline Safety, Concurrency, Dynamic Quality Gates & Clinical Honesty** | Remediates 21 findings from external engineering review. (1) Pipeline Safety & Fallback Integrity: `fallback_rule_extract` in `health_intake_agent.py` emits `confidence: 0.0` and empty payload when Gemini key is missing/fails, eliminating synthetic 60kg egym loads and 75kg InBody auto-commits (C1, M7); isolates failed usage accounting; exact scan lookup in `get_staged_scan` (L3). (2) Ingestion & Range Tagging: Replaces quadratic Python bisect interval tagging with indexed SQL range updates (`idx_hr_samples_ts`) reducing DB tagging from >10min to 0.71s (C3); tags 24,947 historical SDK workout samples (H1); derives missing exercise `end_time` from `start_time + duration` (M8). (3) Concurrency & Endpoint Safety: Fixes `do_POST` 404 falling through on reconcile (C2); protects export writes (`tempfile` + `os.replace` + `threading.Lock`) and Withings token refresh (H5); aligns `/api/status` dual-freshness contract between server and `app.js` (H4); introduces unique event filenames (`{date}_{clean_name}_{short_id}.json`) to prevent same-day overwrite (H6). (4) Dynamic Quality Gates: Gate 4 dynamically asserts expected rows against on-disk JSON records (M1). (5) Frontend Clinical Honesty & Modal UX: Purges all hardcoded clinical values (`62.1 kg`, `78.4 kg`, `19.9% BF`, `78.0g`, `HRV ~40ms`, `RHR 80–92 bpm`, `?? 10`) across `app.js`, `widgets.js`, `charts.js`, and `bloodwork.js`, falling back to honest `null` / `—` (H3, L2); upgrades `#scans-review-modal` to render M20 proposal payloads with HTML sanitization (`escapeHtml`) and 1-click confirmation (H2, M6); validates `res.ok` in fast dose attestation before state updates (M3). | ✅ Adopted |
| `ADR-037` | 2026-09-13 | **Comprehensive Protocol Management System, Event-Sourced Manifests & Schema v13** | (1) Unifies protocol definition into immutable event-sourced manifests (`data/records/protocols/*.json`), resolving Finding M2 (regimen previously duplicated across 5 codebases). (2) Schema Migration v13 (`scripts/migrate_v13.py`) establishes `protocols` projection table with multi-vector JSON columns (`compounds_json`, `supplements_json`, `training_program_json`, `diagnostic_panel_json`, `safety_redlines_json`, `milestones_json`), populates 5 historical epochs, synchronizes `interventions_catalog`, and sets `PRAGMA user_version = 13`. (3) Dynamic Matrix Rendering (`matrix.js`): Temporal resolver matches viewed week to historical protocol; displays Natural Baseline status banner for drug-free epochs (2017–2023) and derives fast-dose attestation items dynamically from active protocol compounds. (4) Protocol Studio UI (`protocol_studio.js`): Interactive command center in Workspace 5 with 5 multi-vector inspection tabs, draft clone engine, JSON export, and server persistence (`POST /api/protocol`). (5) CQRS Replay: Stage 4c in `scripts/rebuild_database.py` guarantees 100% state regeneration on `--clean` rebuilds from raw JSON manifests alone. | ✅ Adopted |
| `ADR-038` | 2026-09-13 | **Contextual Life Events, Multimodal Chart Superimposition & Schema v14** | (1) Establishes event-sourced life events architecture (`data/records/events/*.json`) capturing non-protocol acute confounders (travel, surgery, acute illness, orthopedic injury, work sprints) with multi-vector physiological impact tags (`fluid_retention`, `sleep_disruption`, `elevated_rhr`, `training_hiatus`, `caloric_surplus`, `caloric_deficit`, `biomarker_anomaly`). (2) Schema Migration v14 (`scripts/migrate_v14.py`) adds `life_events` projection table with temporal and category indexing and sets `PRAGMA user_version = 14`. (3) CQRS Replay: Stage 4d in `scripts/rebuild_database.py` guarantees 100% state regeneration on `--clean` rebuilds. (4) Multimodal Chart.js Superimposition Engine (`lifeEventsSuperimpositionPlugin` in `dashboard/js/charts.js`): renders shaded pastel bands across macro eras and event duration windows in `beforeDraw`, and renders vertical dashed pins with frosted category glyph badges in `afterDraw`. (5) Interactive filter strip above charts allows dynamic category toggling with instant canvas re-render. (6) Workspace 4 History Curator (`dashboard/js/life_events.js`) enables tabular review, tag inspection, and creation/editing of events via modal with optimistic UI and server sync (`POST /api/events`). | ✅ Adopted |
| `ADR-039` | 2026-09-13 | **Companion App Protocol Dose Attestation, Retrospective Logging & Resilient Offline Queue** | (1) Implements mobile protocol compliance front door in Android companion app (`DoseAttestationScreen.kt`), eliminating web desktop barrier for medication adherence. (2) Dynamic Active Protocol Discovery: `SyncNetworkClient.kt` fetches active protocol compounds and cadences from `GET /api/protocol/active` with network fallback and offline cache. (3) 1-Tap Fast Confirmation: prominent $\ge 48\text{dp}$ touch targets for active regimen compounds (`creatine_am`, `magnesium_pm`, `creatine_monohydrate`, `omega3_epd_dha`, `whey_preworkout`), optimistic today-attested badge, and automatic `exact` / `adherent` timestamp generation. (4) Retrospective & Divergence Logging: DatePicker/TimePicker, precision tiers (`exact`, `approximate`, `recalled`), divergence vocabulary (`adherent`, `late`, `dose_adjusted`, `deliberate_skip`, `site_altered`, `missed`), and optional clinical notes. (5) Crash-Resilient Offline Queue (`DoseQueueManager.kt`): writes atomic `.json.tmp` -> `.json` payloads in `context.filesDir/pending_doses/` before attempting network dispatch; automatically preserves un-synced doses across app restarts or offline status with manual and automatic queue flush. (6) Bottom Navigation & Dashboard Integration: 5-tab NavigationBar in `MainActivity.kt` with `Icons.Default.Medication`, and 1-tap quick action card on `HomeScreen.kt`. | ✅ Adopted |















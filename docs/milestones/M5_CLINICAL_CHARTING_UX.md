# Milestone 5: Clinical Charting UX, Grounded Scales & HUD Time-Travel (ADR-009 to ADR-011)

**Status:** ✅ Completed & Merged into `main` (`2026-08-28`)
**Scope:** ADR-009 Grounded 0-95 kg Mass Baseline, ADR-010 Compact Glassmorphic Tooltip, ADR-011 Constant Vertical Grid & Direct Curve Crossing Value Chips, 60fps Canvas Render Optimizations

---

## 1. Architectural Objectives
1. **Grounded Anatomical Mass Scale (ADR-009)**:
   - Unified primary Y-axis to 0-95 kg across all mass views, eliminating bottom-floor clipping and scale jumping on tab switch.
   - Derived physiological reference envelopes from 30d/7d EMA weight to eliminate jagged noise.
   - Shaded FFMI target growth corridor between 20.0 (Athletic Baseline) and 21.5 (16-Week Target).
2. **Visual De-Cluttering & Glassmorphism (ADR-010)**:
   - Filtered out 6 static calculation lines from hover tooltips, reducing height by ~70% with frosted glass theme.
   - Replaced dashed corridor borders with borderless translucent shaded bands.
   - Removed stacked EMA point dots in micro-views for sleek vector curves.
   - Defaulted startup view to `Month (30D)` with `localStorage` memory.
3. **Constant Vertical Grid & Direct Crossing Value Chips (ADR-011)**:
   - Always-visible vertical dashed guide lines on every X-axis date tick.
   - `crossingValueChipsPlugin` rendering frosted micro-pill badges directly at curve intersections with automatic collision avoidance (<16px separation).
   - Dynamic hover guide line with live 5-card top HUD synchronization during scrub, resetting on mouse leave.
4. **Performance & Memory Hygiene**:
   - Single-pass preallocated O(N) dataset extraction in `updateBodyCompChart()`.
   - Sub-pixel hover draw guard reducing canvas redraw calls by >80% during scrubbing.
   - Hoisted 2D canvas font assignments out of inner rendering loops.
   - Safe `try/catch` wrapper around `localStorage` operations.

---

## 2. Key Files & Components
- `dashboard/js/charts.js` — Unified 0-95 kg scale, crossingValueChipsPlugin, crosshairPlugin, and single-pass extraction.
- `dashboard/index.html` — Dynamic HUD stats strip markup and injected telemetry payload.
- `docs/DASHBOARD_SPECIFICATION.md` — ADR-009, ADR-010, and ADR-011 records.

---

## 3. Verification & Metrics
- Exporter `python3 scripts/export_dashboard_data.py` passes cleanly (exit code 0).
- Working tree 100% clean.

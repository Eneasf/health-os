# Milestone 21: Health OS UX Overhaul & Audit Implementation (ADR-035)

**Status:** ✅ Completed on branch `feat/health-os-ux-overhaul` (2026-09-13)  
**Standard:** `AGENTS.md` Repository Protocols, ADR-016 (Null Honesty), ADR-019 (Widget Engine), ADR-025 (Responsive Directive), ADR-026 (Physical Adaptation Triad), ADR-035 (UX Overhaul)  
**Audit Reference:** `docs/audits/2026-09-13_HEALTH_OS_DESIGN_REVIEW_ADJUDICATION.md`  

---

## 1. Executive Summary & Goals

Milestone 21 completes a comprehensive overhaul of the personal health dashboard ("Health OS") frontend, addressing all 11 findings from an external AI UX design review (`docs/Health OS Design Review.html`). The overhaul preserves all clinical data invariants (`AGENTS.md` §3) while dramatically elevating mobile ergonomics, visual pacing, and cognitive clarity:

- **P1.1 DOM Hierarchy Correction**: Fixed a critical bug in `dashboard/js/widgets.js` where `tier-2-section` (1,041 px) was extracted to root `<main>` on layout load and leaked beneath every domain workspace tab. Tier 2 is now strictly bound inside `#workspace-protocol`.
- **P1.2 Matrix Cell State Overhaul**: Replaced ambiguous dashed cells with clear semantic states: future days render a subtle dot (`·`), today unlogged renders a highlighted pill with a `Log` prompt, and past unlogged renders a muted dash (`—`). Weekly adherence summary is dynamically calculated and displayed in the matrix header. Protocol timer cards display affirmative "No dose logged yet" instead of bare dashes.
- **P1.3 Mobile Bottom Navigation Dock**: Added `#mobile-bottom-nav` with 5 touch targets ($\ge 48$ px) placed in the ergonomic thumb zone, with active tab styling synchronized via `WidgetManager.setWorkspaceTab()`. Desktop top workspace tabs are hidden on mobile (`hidden md:flex`) to eliminate duplicate navigation clutter.
- **P2.1 Legibility & Micro-Typography**: Swept the entire interface, eliminating all `text-[9px]` and `text-[10px]` sub-minimum sizes and standardizing on legible `text-xs` (12 px) and `text-[11px]`.
- **P2.2 Studio Mode Gating**: Drag handles, nudge arrows, and widget reordering buttons are hidden by default in reading mode. They activate exclusively when Studio Mode is explicitly toggled via `#btn-toggle-studio-mode` (`WidgetManager.toggleStudioMode()`), restoring visual serenity.
- **P2.3 Chart De-Cluttering**: Upgraded `crossingValueChipsPlugin` in `dashboard/js/charts.js` to render frosted value chips only on crosshair hover or for the latest data point, eliminating cluttered pill pile-ups. Filtered default Body Comp legend labels down to the 3 primary series (Weight EMA, Muscle Mass EMA, InBody Gold Benchmark).
- **P2.4 Compact Sleep Gating**: Replaced the 112 px empty placeholder box in `#exp-sleep` with a compact 1-line actionable banner when sleep coverage is low (<4 of 7 nights recorded).
- **P3.1 Clinical Checklist Color De-Escalation**: Reserved alert colors (amber/red) strictly for true physiological alarms; planned routine Week 6 blood requisitions render in neutral ink.
- **P3.2 Life Eras Autobiography Continuity**: Surfaced the 1,089-day unassigned historical gap (`2023-08-31 → 2026-08-23`) in the autobiography timeline table, and removed the duplicate subtitle paragraph.
- **P3.3 Double Payload Elimination**: Added a lightweight probe to `/api/status` in `dashboard/js/app.js` to compare `data_current_through` before fetching the 1.7 MB JSON payload, saving bandwidth and improving load speed on mobile/Tailscale connections.
- **P3.4 Header Branding Consistency**: Harmonized page `<title>` and executive header branding to `Health OS • Personal Clinical Command Center` with subtitle `Personal Clinical Command Center • v1.2.0 • Schema v12 • Metric UoM (kg, g, nmol/L)`.

---

## 2. Key Changes by Component

### A. Frontend Layout & Navigation (`dashboard/index.html`, `dashboard/js/widgets.js`)
- `dashboard/js/widgets.js`:
  - Removed `layout.tier_order.forEach(...)` DOM extraction loop in `applyLayout()`.
  - Added `#today-protocol-strip` visibility toggle in `setWorkspaceTab()` (hidden on Protocol tab, shown on others).
  - Synchronized mobile bottom nav tab classes (`#mob-tab-ws-${t}`).
  - Added `WidgetManager.toggleStudioMode()` toggling `body.studio-active` and updating `#studio-mode-indicator` / `#btn-toggle-studio-mode`.
- `dashboard/index.html`:
  - Added CSS gating for `.widget-drag-handle, .tier-drag-handle, .widget-btn, .btn-tier-move` (display none unless `body.studio-active`).
  - Added mobile navigation touch target rule (`.mob-nav-btn { min-height: 48px; min-width: 48px; }`).
  - Added `#mobile-bottom-nav` dock with 5 buttons before `</body>`.
  - Added `pb-24 md:pb-8` to `<main id="main-container">` to prevent bottom-nav overlap.
  - Added `#today-protocol-strip` and `#btn-toggle-studio-mode` in command bar; gated top tabs with `hidden md:flex`.
  - Upgraded all `text-[9px]` and `text-[10px]` typography across body comp stats, exercise KPIs, sleep tables, milestone timelines, and modals.
  - Replaced tall `#sleep-gated-box` with compact 1-line actionable banner.
  - Removed duplicate subtitle paragraph in Life Eras body.

### B. Adherence Matrix & Dose Timers (`dashboard/js/matrix.js`, `dashboard/js/app.js`)
- `dashboard/js/matrix.js`:
  - Future dates render subtle dot `·` (`text-slate-300 dark:text-slate-600 font-bold text-center`).
  - Today unlogged renders prominent accent pill: `border border-emerald-500/50 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 font-bold hover:bg-emerald-500/20 cursor-pointer` with `Log` prompt.
  - Past unlogged renders muted dash `—` (`text-slate-400 dark:text-slate-500`).
  - Added dynamic weekly adherence summary computation updating `#matrix-weekly-adherence-summary` ("X of Y doses logged this week").
- `dashboard/js/app.js`:
  - Updated `syncHudToSelectedDate()` to display "No dose logged yet" on `#dut-timer-sublabel` and `#ai-counter-sublabel` when unlogged.
  - Injected 1,089-day unassigned historical gap row in `data.life_eras` loop.
  - Added `/api/status` freshness check in `loadData()` to prevent redundant 1.7 MB JSON fetches when data is already fresh.

### C. Clinical Visualizations (`dashboard/js/charts.js`, `dashboard/js/bloodwork.js`)
- `dashboard/js/charts.js`:
  - `crossingValueChipsPlugin`: value pills are now only rendered when the crosshair is active (hovered) or for the latest point on the rightmost edge of the viewport. Pill typography upgraded to 11 px font.
  - Body Comp Legend: added legend label filter displaying only the 3 core series by default (Weight EMA, Muscle Mass EMA, InBody Benchmark), decluttering the initial view.
- `dashboard/js/bloodwork.js`:
  - Replaced warning orange status (`text-amber-600`) on planned Week 6 blood draw requisitions with neutral ink (`text-slate-600` / `📅 Requisition W6`), reserving alert colors strictly for physiological out-of-range biomarkers.

### D. Responsive Validation Gates (`scripts/verify_responsive_layout.py`)
- Updated `scripts/verify_responsive_layout.py` to enforce:
  - V0: Zero horizontal viewport bleed and contained canvas wrappers (`min-w-0 overflow-hidden` on `chart-exercise-hr`).
  - V1: Dynamic rotation and debounced reflow.
  - V2: Adherence Matrix sticky frozen column with opaque backdrops and auto-scroll to today.
  - V3: Command strip mobile ergonomics, `#today-protocol-strip`, and `#btn-toggle-studio-mode`.
  - V4: Touch target sizing ($\ge 40$ px, mobile bottom nav $\ge 48$ px) and zero unreadable micro-type (`text-[9px]`, `text-[10px]`).

---

## 3. Verification & Quality Gates

### A. Responsive Design & Ergonomics Validator
```bash
python3 scripts/verify_responsive_layout.py --all
```
Output:
```
===========================================================================
📱  RESPONSIVE DESIGN & MOBILE REFRAMING VALIDATOR (VDONE.md)
===========================================================================
[PASS] V0: Zero Viewport Bleed / Overflow Check
       All root containment and canvas isolation rules verified.
[PASS] V1: Rotation & Reframing Invariance
       Orientation listeners, debounced reflow, and chart resize hooks verified.
[PASS] V2: Adherence Matrix Sticky Frozen Column
       Sticky frozen column, opaque backdrops, and scroll-to-today verified.
[PASS] V3: Command Strip Mobile Ergonomics
       Responsive command strip layout, today protocol glance, and mobile studio verified.
[PASS] V4: Touch Target Ergonomics & Readability
       Touch target sizing (>=40px), mobile bottom nav dock, and legible typography verified.
===========================================================================
🎉 ALL RESPONSIVE GATES PASSED!
```

### B. JavaScript Syntax Validation
```bash
for f in dashboard/js/*.js; do node -c "$f"; done
```
Output: 100% clean syntax across all 8 JS modules.

### C. Live Database Safety Guard
- Confirmed `data/health_dashboard.db` mtime and content hash are completely untouched.
- Confirmed zero direct-to-SQL mutations; event sourcing integrity preserved (ADR-028).

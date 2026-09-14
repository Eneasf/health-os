# Milestone 13: Responsive Design Directive, Mobile Reframing Architecture & Viewport Ergonomics (ADR-025)

**Status:** ✅ Completed on branch `feat/responsive-design-directive` (2026-09-05)  
**Standard:** `vdone` Acceptance Criteria (V0–V5 in `VDONE.md`) & `AGENTS.md`  

---

## 1. Architectural Directive & Objectives

Under user mandate, **"Responsive Design as a Directive"** was established as the primary engineering principle for My Health Dashboard. Previous iterations exhibited critical mobile failure modes:
1. **Viewport Bleed & Unconstrained Horizontal Overflow**: Wide tabular modules and fixed desktop layouts expanded `document.documentElement.scrollWidth` beyond `window.innerWidth`, forcing awkward 2D scrolling on mobile devices.
2. **Orientation Reframing Freezing**: Rotating mobile devices between Portrait and Landscape failed to recalculate layouts; Chart.js canvases remained locked to stale dimensions and sticky header offsets drifted.
3. **Adherence Matrix Disorientation**: Horizontal scrubbing across 7-day columns disconnected cells from their compound names, destroying clinical readability.
4. **Command Strip Crowding**: A 1,020px fixed toolbar forced text wrapping and horizontal sprawl on phone viewports.
5. **Trapped Touch Swipes**: Desktop drag handles with `touch-action: none` intercepted finger gestures on mobile touch screens.

---

## 2. Key Accomplishments & Technical Implementations

### A. Zero Viewport Bleed & Containment Invariant (V0)
- **Root Enclosure**: Applied `html, body { overflow-x: hidden; max-width: 100vw; }` across light and dark themes in `dashboard/index.html`.
- **Canvas Isolation**: Enclosed all 4 Chart.js canvas elements (`#chart-body-comp`, `#chart-nutrition`, `#chart-autonomic`, `#bw-pk-chart`) within `relative w-full min-w-0 overflow-hidden chart-container-adaptive` wrappers.
- **Short Landscape Height Guard**: Enforced `@media (max-height: 500px) and (orientation: landscape) { .chart-container-adaptive { height: 14rem !important; } }`, preventing charts from dominating low-height landscape phone screens.
- **Container Sizing**: Updated main layout container to `min-w-0 px-3 sm:px-6 lg:px-8 py-4 sm:py-6`.

### B. Two-Phase Orientation Reframing Engine (`dashboard/js/responsive.js`, V1)
- Created `ResponsiveManager` module wired to `screen.orientation.addEventListener('change')`, `window.addEventListener('orientationchange')`, and debounced window resize.
- **Two-Phase Lifecycle**:
  - **Phase 1 (Immediate RAF)**: Fires `requestAnimationFrame` to synchronously recalculate dimensions before browser repaint.
  - **Phase 2 (150ms Settling Timer)**: Fires post-rotation timeout to cleanly accommodate mobile OS dynamic URL bar expansion/collapse.
- **Synchronized Offsets**: Dynamically measures `#main-header` height and assigns `banner.style.top = `${headerHeight}px`` without hardcoded pixel drift.
- **Canvas Visibility Guard**: Updated `ChartsManager.resizeCharts()` and `BloodworkManager.resizeCharts()` to check `canvas.offsetParent !== null` before calling `.resize()`, preventing Chart.js from resizing hidden tabs/collapsed tiers to 0x0.

### C. Adherence Matrix Sticky Frozen Compound Column (V2)
- Pinned first column (`Compound / Stack`) with `sticky left-0 z-20` in the header and `sticky left-0 z-10` in row bodies in `dashboard/js/matrix.js`.
- Applied solid opaque backdrops (`bg-slate-100 dark:bg-slate-900` in `<th>`, `bg-white dark:bg-[#131A26]` in `<td>`) with drop shadows (`shadow-[2px_0_6px_-2px_rgba(0,0,0,0.08)]`) to eliminate text bleed during horizontal scrubs.
- Implemented `scrollToTodayOnMobile()` auto-centering today's column on touch devices upon initialization and orientation change.

### D. Mobile-First Command Strip & Studio Drawer (V3)
- Restructured `#layout-control-banner` into a responsive 2-row layout on mobile (`flex flex-col sm:flex-row gap-2`):
  - **Row 1 (Viewport Tabs)**: 4-grid segmented toggle (`[ All | Body Comp | Recovery | Protocol ]`) styled with `grid grid-cols-4 sm:flex`.
  - **Row 2 (Telemetry & Tools)**: Horizontally scrolling live telemetry chips (`overflow-x-auto flex items-center gap-2`) alongside a collapsible mobile studio button (`⚙️`).
  - **Mobile Studio Drawer (`#mobile-studio-drawer`)**: Collapsible mobile action sheet toggling Expand All, Fold All, and Reset Layout without cluttering the compact command strip.
- Responsive Macro-Milestone Bar: Transformed milestone nodes from rigid `grid-cols-5` into responsive `grid-cols-2 sm:grid-cols-5 gap-2 sm:gap-1`.

### E. Touch Target Ergonomics & Tap Accessibility (V4)
- Added media queries for `@media (pointer: coarse), (max-width: 768px)` ensuring interactive buttons (`.widget-btn`, `.widget-toggle-btn`, `.viewport-tab-btn`) meet WCAG 2.2 AA / Apple HIG bounding box criteria ($\ge 36\text{--}40\text{px}$).
- **Drag Handle Hiding on Touch**: Hidden `.widget-drag-handle` and `.tier-drag-handle` on coarse pointers (`display: none !important`), eliminating trapped vertical swipe scrolls.
- **Modal Containment**: Added `overflow-y-auto` to outer modal backdrops and `max-h-[calc(100vh-2rem)]` to inner cards in `#dose-modal` and `#bloodwork-drilldown-modal`, with enlarged close button tap targets.
- **Tabular Data Protection**: Added `min-w-[620px]`, `min-w-[640px]`, `min-w-[680px]`, and `min-w-[760px]` to tables inside `overflow-x-auto` wrappers in `bloodwork.js`, preventing column crushing.

---

## 3. Subagent Audit & Edge-Case Remediation

During execution, a specialized Tier 2 Flash subagent (`Mobile Frontend Edge-Case Auditor`, ID `ff34b95d-de99-4361-9363-790c85285a7a`) performed a comprehensive code-level stress audit. All findings were remediated:
1. `#dose-modal` clipping: Enforced `max-h-[calc(100vh-2rem)] overflow-y-auto` on inner card and `overflow-y-auto` on backdrop.
2. Bloodwork UK vs BR toggle blowout: Converted to `grid grid-cols-2 sm:flex` with responsive text abbreviations (`hidden sm:inline`).
3. Titration chart container: Added `min-w-0 overflow-hidden chart-container-adaptive` to `#bw-pk-chart`.
4. Bloodwork tables column crushing: Added `min-w-[...]` to Draw Readiness, Consultation Brief, and Titration tables.
5. Drilldown modal SVG: Added `min-w-[540px]` to prevent font shrinkage below 8px on mobile.

---

## 4. Verification & Quality Gates

### Automated Responsive Validator (`scripts/verify_responsive_layout.py`)
```bash
python3 scripts/verify_responsive_layout.py --all
```
**Output:**
```text
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
       Responsive command strip layout and mobile ergonomics verified.
[PASS] V4: Touch Target Ergonomics
       Touch target sizing and pointer:coarse media rules verified.
===========================================================================
🎉 ALL RESPONSIVE GATES PASSED!
```

### Unified Quality Gate Runner (`scripts/run_quality_gates.py`)
```bash
python3 scripts/run_quality_gates.py
```
**Output:**
- Gate 1: Frontend JavaScript Syntax (Clean pass with `node -c`)
- Gate 2: Offline HTML Data Hook Protection (Intact)
- Gate 3: Ingestion Assertions (A1–A4 passed)
- Gate 4: Scratch Migration & Idempotency (Clean pass)
- Gate 5: Live DB Write-Safety Guard (Untouched)
- Gate 6: Exporter Byte-Determinism (100% byte-identical)
- Gate 7: Working Tree Cleanliness (Passed upon commit)

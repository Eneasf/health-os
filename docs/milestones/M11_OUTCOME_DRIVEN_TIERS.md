# Milestone 11: Unified 2-Tier Master Architecture, Side-by-Side Packing & Viewport Tabs (ADR-020, ADR-021)

**Status:** ✅ Completed on branch `feat/outcome-driven-tiers` (2026-09-04)  
**Scope:** Root DOM reorganization into the Unified 2-Tier Master Architecture (Tier 1 Physiological Telemetry & Core Explorers, Tier 2 Protocol Operations, Compliance & Safety), side-by-side twin packing of Nutrition Partitioning (`exp-nutrition`) and Autonomic Balance (`exp-autonomic`) recovering ~550px of vertical real estate, Macro-Protocol Milestone Bar construction at the head of Tier 2 with a 16-week progress track, executive header cleanup, Domain Viewport Tabs (`[ All Tiers | Body Comp | Recovery | Protocol ]`) anchored in the Workspace Command Strip, multi-tier layout orchestration in `WidgetManager` (v4), in-place Save Status fix eliminating layout shifts/scrollbars, and clinical collapsed preview chips.

---

## 1. Architectural Objectives & Accomplishments

1. **Unified 2-Tier Master Architecture & Side-by-Side Packing (`ADR-020`, `ADR-021`)**:
   - Resolved the screen real-estate inefficiency identified in user testing, where isolating 1-column cards into separate tier containers forced 50% blank voids on their rows and pushed Autonomic Recovery below the fold.
   - Consolidated the 5 explorer modules into a single 2-column responsive CSS grid (`#zone-explorers`, `grid grid-cols-1 lg:grid-cols-2 gap-6`) inside Tier 1 (`#tier-1-section`):
     - **Row 1**: Body Composition (`exp-bodycomp`, `lg:col-span-2` full width).
     - **Row 2 (Twin Card Side-by-Side Packing)**: Nutrition Partitioning (`exp-nutrition`, `lg:col-span-1`) and Autonomic Balance (`exp-autonomic`, `lg:col-span-1`) sit directly side-by-side. Completely eliminates blank column voids and saves ~550px of vertical scroll height.
     - **Row 3**: Sleep Architecture (`exp-sleep`, `lg:col-span-2` full width).
     - **Row 4**: Longitudinal Life Eras (`exp-lifeeras`, `lg:col-span-2` full width, collapsed by default).
   - Structured Tier 2 (`#tier-2-section`) as the operational command center:
     - **Macro-Protocol Milestone Bar** (`#macro-milestone-bar`) with 16-week progress track.
     - **7-Day Protocol Adherence Matrix** (`#zone-tier2-matrix`, `#widget-adherence-matrix`).
     - **Selected-Day Detail Banner**.
     - **High-E2 Scale Jump Alert** (`#e2-alert-banner`).
     - **Action Cards Deck** (`#zone-tier2-actions`: cadence_compound, omega3_epd_dha, Pre-Workout, Deload).
   - Enabled flexible whole-tier swapping (`WidgetManager.moveTier('tier1' | 'tier2', 'up' | 'down')`) via accessible header nudge buttons and drag handles.

2. **Command Strip Layout Studio & In-Place Save Status**:
   - Fixed user-reported layout shift and unwanted scrollbars when clicking "Save":
     - Replaced dynamic DOM insertion of new buttons with a reserved, stationary inline `#save-status` container inside the Command Strip.
     - Enforced strict horizontal boundaries and transitions on `#save-status` (`max-w-0 opacity-0` -> `max-w-[200px] opacity-100`) without triggering layout reflow or horizontal scrollbars.

3. **Macro-Protocol Milestone Bar**:
   - Relocated the cycle badge out of the global header into a dedicated, clinical-grade progress card heading Tier 2 (`#macro-milestone-bar`).
   - Features:
     - Phase name and mode chips: `16-Week Metabolic Conditioning (Cycle 1)`, `Week 2 of 16`, `Standard Mode (Weeks 1–4)`.
     - 16-week visual progress track with dynamic fill percentage (`12.5% Elapsed`) and elapsed day count (`(Day 12 / 112)`).
     - Discrete clinical milestone nodes: W0 Baseline (Complete ✓), W4 Transition (Adaptive Mode), W6 Deload & Bloods (Recovery & Labs), W8 Mid-Check (Telemetry Audit), W16 Target (End of Phase).
     - Active regimen summary footnote and next-milestone countdown badge.

4. **Global Navigation Header Cleanup**:
   - Stripped `#header-phase-badge` from `#main-header`, establishing an uncluttered executive bar housing the brand icon, title, subtitle, Withings scale sync button, and light/dark theme toggle.

5. **Domain Viewport Tabs (`[ All Tiers | Body Comp | Recovery | Protocol ]`)**:
   - Anchored directly in the center of the Workspace Command Strip (`#layout-control-banner`), maintaining strict `flex-nowrap overflow-x-auto` with zero vertical wrapping.
   - Dynamic clinical filtering:
     - `All Tiers`: Displays both tiers, revealing all 5 cards in `#zone-explorers`.
     - `Body Comp`: Displays Tier 1, revealing `exp-bodycomp` and `exp-nutrition` while hiding Autonomic, Sleep, Life Eras, and Tier 2.
     - `Recovery`: Displays Tier 1, revealing `exp-autonomic`, `exp-sleep`, and `exp-lifeeras` while hiding Body Comp, Nutrition, and Tier 2.
     - `Protocol`: Hides Tier 1, reveals Tier 2 (Milestone Bar, Matrix, Actions).
   - Automatically auto-expands target tiers upon domain selection and triggers Chart.js canvas reflow (`ChartsManager.resizeCharts()`).

6. **Multi-Tier Layout Engine Upgrades (`dashboard/js/widgets.js`)**:
   - Upgraded `DEFAULT_LAYOUT` to version 4 with `tier_order: ['tier1', 'tier2']`, `explorers: [...]`, and `actions: [...]`.
   - Built backwards-compatible migration for existing v1, v2, and v3 `localStorage` states.
   - Implemented dense summary preview chips for both tiers:
     - Tier 1: `78.4 kg • FFMI 21.2 • 19.9% BF • 78.0g Prot • HRV ~40ms • Sleep Gated`
     - Tier 2: `Week 2 • Saturation • 100% Adherent • Cadence in 18h • E2 Stable`

---

## 2. Key Modified Files

- `dashboard/index.html` — Restructured root DOM into 2 tiers with unified `#zone-explorers`, packed Nutrition and Autonomic side-by-side, added Domain Viewport Tabs to Command Strip, built `#macro-milestone-bar`, and cleaned executive header.
- `dashboard/js/widgets.js` — Upgraded layout engine to v4, implemented `moveTier`, `setViewportTab` with card-level filtering, zone state capture/apply for explorers/actions, stationary `#save-status`, and tier preview chips.
- `dashboard/js/app.js` — Populated Macro-Protocol Milestone Bar with live phase telemetry, calculated cycle elapsed progress, and updated architecture comments.
- `docs/DASHBOARD_SPECIFICATION.md` — Formally codified Level 1/2/3 hierarchy, ADR-020, and ADR-021.
- `docs/HANDOVER.md` — Updated Master Decision Index (§2), Milestone Index (§3), and closed Open Work item (§6.6).

---

## 3. Verification & Clinical Boundaries

- **Byte-Determinism**: `python3 scripts/export_dashboard_data.py` completes cleanly with zero changes to `dashboard/index.html` (`[SKIP] ... unchanged; no write needed`).
- **Offline HTML Data Hook Protection**: `<script id="injected-dashboard-data">` tag untouched and fully operational in `file://` mode.
- **Fact Store Isolation**: Verified zero writes to `data/health_dashboard.db`, `data/inbox/`, or `archive/`.
- **Engine Logic Verification**: `scratch/test_widgets.js` passed all tier reordering, card filtering, v3-to-v4 migration, and persistence assertions in Node.js.
- **Syntax Check**: `node -c dashboard/js/*.js` validated 100% error-free.

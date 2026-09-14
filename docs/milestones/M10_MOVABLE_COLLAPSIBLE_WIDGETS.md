# Milestone 10: Movable & Collapsible Widget Architecture (ADR-019)

**Status:** ✅ Merged into `main` (2026-09-04)  
**Scope:** Movable & Collapsible Widget Engine (`dashboard/js/widgets.js`), Multi-Tier & Multi-Zone Layout Orchestration (Tier 1 HUD vs Tier 2 Explorers Swapping, Tier 1 Actions & Domain Explorers), Jitter-Free Drag-and-Drop with Grab Handles, Accessible Reorder & Swap Buttons, 1-Col/2-Col Width Toggle, Clinical Collapsed Preview Chips, Layout Persistence (`localStorage`), and Chart.js Dimension Synchronizer.

---

## 1. Architectural Objectives

1. **Tier-Level Swapping & Reordering**:
   - Structured the root layout into layout-aware tier sections (`#tier-1-section` and `#tier-2-section`) inside `#main-container`.
   - Enabled full swapping of Tier 1 (Morning Command HUD & Protocols) and Tier 2 (Core Domain Explorers) via:
     - Header buttons: `▼ Move Below Tier 2` on Tier 1, `▲ Move Above Tier 1` on Tier 2.
     - Top Toolbar button: `⇄ Swap Tiers`.
     - Direct drag-and-drop of tier sections using section grab handles (`.tier-drag-handle`).
   - Saved layout persists `tier_order: ['tier1', 'tier2']` or `['tier2', 'tier1']`.
2. **Jitter-Free Drag-and-Drop Engine**:
   - Resolved handle inconsistency by activating card draggable attribute on handle `mousedown`, preventing browser drag ghosts of isolated handle text.
   - Replaced jitter-prone temporary placeholder DOM insertion with direct non-thrashing DOM slot positioning via atomic `moveDomElement()` guarded by `(draggedCard !== targetNode && draggedCard.nextElementSibling !== targetNode)`. Total child count remains constant; grid layout never bounces or flickers.
   - Supported both intra-zone and cross-zone card dragging.
3. **Accessible One-Tap Reorder Controls**:
   - One-tap nudge buttons (`◀`/`▶` for horizontal action cards, `▲`/`▼` for domain explorer cards) for touch, mobile, and keyboard navigation.
4. **Responsive Width Adaptation (Column Spanning)**:
   - Dynamic 1-column / 2-column span toggle (`⇲` / `⇱`) for explorer cards, allowing side-by-side or full-width analytical deep dives.
   - Real-time chart canvas reflow via `ChartsManager.resizeCharts()` using `requestAnimationFrame`.
5. **Clinical Collapsed Preview Chips**:
   - Minimized widgets display high-value telemetry preview chips (e.g. Body Comp: latest weight, FFMI, body fat %; Nutrition: complete protein average; Sleep: coverage gating / duration; Life Eras: epoch count; Matrix: active week; Timers: remaining hours), maintaining situational awareness without taking vertical space.
   - Both Tier 1 and Tier 2 root sections can also be individually collapsed or expanded.
6. **State Persistence & User Control**:
   - Canonical clinical defaults: Life Eras starts collapsed by default; Tier 1 and Tier 2 start expanded.
   - Top layout toolbar: dedicated "💾 Save Layout" button with visual feedback (`#save-status`), "⇄ Swap Tiers", "⊞ Expand All", "⊟ Collapse All", and "↺ Reset" to canonical defaults.
   - Keyed storage in `localStorage` under `health_dashboard_widget_layout_v1`.

---

## 2. Key Files & Components

- `dashboard/js/widgets.js` — Modular `WidgetManager` coordinating tier swapping, layout state, drag-and-drop, width toggles, preview chips, and persistence.
- `dashboard/js/charts.js` — Extended with `ChartsManager.resizeCharts()` and `window.resizeCharts`.
- `dashboard/js/app.js` — Integrated `WidgetManager.init()` and real-time preview chip synchronization during `populateDashboard()`.
- `dashboard/index.html` — Tier sections, zone containers, widget headers, drag handles, preview badges, layout action toolbar, and `widgets.js` script tag.
- `docs/DASHBOARD_SPECIFICATION.md` — ADR-019 specification.
- `docs/HANDOVER.md` — Milestone Index and Master Decision Index updates.

---

## 3. Verification & Clinical Boundaries

- **Byte-Determinism**: `python3 scripts/export_dashboard_data.py` completes cleanly with zero changes to `dashboard/index.html` (`[SKIP] ... unchanged; no write needed`).
- **Offline Integrity**: `<script id="injected-dashboard-data">` tag untouched and fully functional in `file://` mode.
- **Fact Store Isolation**: Zero writes to `data/health_dashboard.db`, `data/inbox/`, or `archive/`.
- **Clinical Null-Honesty**: No synthetic baseline or placeholder values used in preview chips; all values derive strictly from ingested telemetry or render as honest null / unlogged states.

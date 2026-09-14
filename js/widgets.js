/**
 * Personal Health Dashboard — Movable & Collapsible Widget Engine (ADR-019)
 * 
 * Features:
 * 1. Multi-Tier & Multi-Zone layout orchestration:
 *    - Tier-Level Reordering & Swapping: Tier 1 (Morning HUD) and Tier 2 (Explorers) can be swapped/dragged
 *    - Tier 1 Protocol Adherence Matrix (collapsible)
 *    - Tier 1 Action Cards (reorderable & collapsible)
 *    - Tier 2 & 3 Domain & Longitudinal Explorers (reorderable, collapsible, 1-col/2-col width toggleable)
 * 2. Robust, jitter-free drag-and-drop:
 *    - Mousedown handle activation preventing browser drag ghosts of isolated text
 *    - Direct DOM slot positioning without placeholder layout shifting
 *    - Atomic DOM reordering with moveBefore() and insertBefore fallback
 * 3. Accessible one-tap reorder & swap controls:
 *    - Tier Swap button (⇄ Swap Tiers) in toolbar and headers
 *    - Up/Down/Left/Right nudge buttons on every card
 * 4. Clinical Collapsed Preview Chips showing high-value numbers even when minimized
 * 5. Layout persistence via localStorage with explicit Save and Reset controls
 * 6. Chart.js dimension recalculation via ChartsManager.resizeCharts()
 */

const WidgetManager = (() => {
  const STORAGE_KEY = 'health_dashboard_widget_layout_v1';

  // Canonical clinical default layout (ADR-026 Refined: Physical Adaptation Triad & Workspaces)
  const DEFAULT_LAYOUT = {
    version: 5,
    tier_order: ['tier1', 'tier2'],
    tier1_collapsed: false,
    tier2_collapsed: false,
    active_workspace: 'adaptation',
    active_tab: 'adaptation',
    matrix_collapsed: false,
    explorers: [
      { id: 'exp-bodycomp', collapsed: false, span: 2 },
      { id: 'exp-nutrition', collapsed: false, span: 1 },
      { id: 'exp-exercise', collapsed: false, span: 1 },
      { id: 'exp-autonomic', collapsed: false, span: 1 },
      { id: 'exp-sleep', collapsed: false, span: 2 },
      { id: 'exp-bloodwork', collapsed: false, span: 2 },
      { id: 'exp-lifeeras', collapsed: false, span: 2 }
    ],
    actions: [
      { id: 'act-cadence', collapsed: false },
      { id: 'act-interval', collapsed: false },
      { id: 'act-preworkout', collapsed: false },
      { id: 'act-deload', collapsed: false }
    ]
  };

  let currentLayout = null;
  let draggedCard = null;

  function loadSavedLayout() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (parsed) {
          // Tier order migration to 2 tiers (tier1: physiology, tier2: protocol)
          let tier_order = (Array.isArray(parsed.tier_order) && parsed.tier_order.length > 0)
            ? parsed.tier_order.filter(t => t === 'tier1' || t === 'tier2')
            : [...DEFAULT_LAYOUT.tier_order];
          if (!tier_order.includes('tier1')) tier_order.push('tier1');
          if (!tier_order.includes('tier2')) tier_order.push('tier2');

          // Explorers migration (from v3 outcomes+readiness or v2/v1 explorers)
          let mergedExplorers = [];
          if (Array.isArray(parsed.explorers) && parsed.explorers.length > 0) {
            mergedExplorers = [...parsed.explorers];
          } else {
            const out = Array.isArray(parsed.outcomes) ? parsed.outcomes : [];
            const rdy = Array.isArray(parsed.readiness) ? parsed.readiness : [];
            mergedExplorers = [...out, ...rdy];
          }

          DEFAULT_LAYOUT.explorers.forEach(def => {
            if (!mergedExplorers.some(e => e.id === def.id)) {
              mergedExplorers.push({ ...def });
            }
          });

          const mergedActions = Array.isArray(parsed.actions) ? [...parsed.actions] : [];
          // Migrate legacy action IDs
          mergedActions.forEach(a => {
            if (a.id && (a.id.includes('dut') || a.id === 'act-cadence')) a.id = 'act-cadence';
            if (a.id && (a.id.includes('ana') || a.id === 'act-interval')) a.id = 'act-interval';
          });
          DEFAULT_LAYOUT.actions.forEach(def => {
            if (!mergedActions.some(a => a.id === def.id)) {
              mergedActions.push({ ...def });
            }
          });

          return {
            version: 5,
            tier_order: tier_order,
            tier1_collapsed: !!parsed.tier1_collapsed,
            tier2_collapsed: typeof parsed.tier2_collapsed === 'boolean' ? parsed.tier2_collapsed : (!!parsed.tier3_collapsed),
            active_workspace: parsed.active_workspace || parsed.active_tab || 'adaptation',
            active_tab: parsed.active_workspace || parsed.active_tab || 'adaptation',
            matrix_collapsed: typeof parsed.matrix_collapsed === 'boolean' ? parsed.matrix_collapsed : DEFAULT_LAYOUT.matrix_collapsed,
            explorers: mergedExplorers,
            actions: mergedActions
          };
        }
      }
    } catch (e) {
      console.warn('Failed to parse saved widget layout:', e);
    }
    return JSON.parse(JSON.stringify(DEFAULT_LAYOUT));
  }

  function saveLayout() {
    currentLayout = captureCurrentState();
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(currentLayout));
      showSaveNotification('Layout Saved ✓');
    } catch (e) {
      console.error('Failed to save layout to localStorage:', e);
      showSaveNotification('Save Failed ✕', true);
    }
  }

  function resetLayout() {
    try {
      localStorage.removeItem(STORAGE_KEY);
      currentLayout = JSON.parse(JSON.stringify(DEFAULT_LAYOUT));
      applyLayout(currentLayout);
      showSaveNotification('Layout Reset ✓');
    } catch (e) {
      console.error('Failed to reset layout:', e);
    }
  }

  let saveNotificationTimeout = null;
  let resetNotificationTimeout = null;

  function showSaveNotification(msg, isError = false) {
    const statusEl = document.getElementById('save-status');
    if (statusEl) {
      statusEl.innerText = msg;
    }

    const isReset = msg.toLowerCase().includes('reset');
    const targetBtn = isReset
      ? document.getElementById('btn-reset-layout')
      : document.getElementById('btn-save-layout');

    if (!targetBtn) return;

    if (isReset) {
      if (resetNotificationTimeout) clearTimeout(resetNotificationTimeout);
      const originalHtml = '↺ Reset';
      const originalClasses = 'px-2 py-0.5 rounded-md border border-slate-300/90 dark:border-slate-700 bg-white/90 dark:bg-slate-800/90 text-slate-700 dark:text-slate-200 font-medium hover:bg-white dark:hover:bg-slate-700 text-xs shadow-xs transition min-w-[58px] text-center';

      targetBtn.innerHTML = isError ? '✕ Error' : '✓ Reset';
      targetBtn.className = isError
        ? 'px-2 py-0.5 rounded-md border border-rose-300 dark:border-rose-800 bg-rose-50 dark:bg-rose-950/50 text-rose-600 dark:text-rose-400 font-bold text-xs shadow-xs transition min-w-[58px] text-center'
        : 'px-2 py-0.5 rounded-md border border-emerald-400 dark:border-emerald-700 bg-emerald-50 dark:bg-emerald-950/50 text-emerald-700 dark:text-emerald-300 font-bold text-xs shadow-xs transition min-w-[58px] text-center';

      resetNotificationTimeout = setTimeout(() => {
        targetBtn.innerHTML = originalHtml;
        targetBtn.className = originalClasses;
        resetNotificationTimeout = null;
      }, 2000);
    } else {
      if (saveNotificationTimeout) clearTimeout(saveNotificationTimeout);
      const originalHtml = '💾 Save';
      const originalClasses = 'px-2.5 py-0.5 rounded-md bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-xs shadow-xs transition min-w-[68px] text-center';

      targetBtn.innerHTML = isError ? '✕ Error' : '✓ Saved';
      targetBtn.className = isError
        ? 'px-2.5 py-0.5 rounded-md bg-rose-600 hover:bg-rose-500 text-white font-bold text-xs shadow-xs transition min-w-[68px] text-center'
        : 'px-2.5 py-0.5 rounded-md bg-emerald-700 text-white font-bold text-xs shadow-xs transition min-w-[68px] text-center ring-2 ring-emerald-400/40';

      saveNotificationTimeout = setTimeout(() => {
        targetBtn.innerHTML = originalHtml;
        targetBtn.className = originalClasses;
        saveNotificationTimeout = null;
      }, 2000);
    }
  }

  function captureCurrentState() {
    const state = {
      version: 5,
      tier_order: [],
      tier1_collapsed: false,
      tier2_collapsed: false,
      active_workspace: currentLayout?.active_workspace || currentLayout?.active_tab || 'adaptation',
      active_tab: currentLayout?.active_workspace || currentLayout?.active_tab || 'adaptation',
      matrix_collapsed: false,
      explorers: [],
      actions: []
    };

    // 1. Tiers order in <main>
    const main = document.getElementById('main-container') || document.querySelector('main');
    if (main) {
      const tiers = main.querySelectorAll('[data-tier-id]');
      tiers.forEach(t => state.tier_order.push(t.getAttribute('data-tier-id')));
    }
    if (state.tier_order.length === 0) {
      state.tier_order = ['tier1', 'tier2'];
    }

    // 2. Tier collapsed states
    const t1 = document.getElementById('tier-1-section');
    if (t1) state.tier1_collapsed = t1.classList.contains('is-collapsed');
    const t2 = document.getElementById('tier-2-section');
    if (t2) state.tier2_collapsed = t2.classList.contains('is-collapsed');

    // 3. Matrix
    const matrixEl = document.getElementById('widget-adherence-matrix');
    if (matrixEl) {
      state.matrix_collapsed = matrixEl.classList.contains('is-collapsed');
    }

    // 4. Explorers (Body Comp, Nutrition, Exercise, Autonomic, Sleep, Life Eras, Bloodwork)
    const explorerCards = document.querySelectorAll('[data-widget-id^="exp-"]');
    explorerCards.forEach(card => {
      state.explorers.push({
        id: card.getAttribute('data-widget-id'),
        collapsed: card.classList.contains('is-collapsed'),
        span: card.classList.contains('lg:col-span-2') ? 2 : 1
      });
    });

    // 5. Actions Zone (Cadence, Interval, Pre-Workout, Deload)
    const actionCards = document.querySelectorAll('[data-widget-id^="act-"]');
    actionCards.forEach(card => {
      state.actions.push({
        id: card.getAttribute('data-widget-id'),
        collapsed: card.classList.contains('is-collapsed')
      });
    });

    return state;
  }

  // Modern atomic DOM movement: moveBefore() with insertBefore fallback
  function moveDomElement(container, nodeToMove, referenceNode) {
    if (!container || !nodeToMove) return;
    if (typeof Element !== 'undefined' && 'moveBefore' in Element.prototype && typeof container.moveBefore === 'function') {
      container.moveBefore(nodeToMove, referenceNode);
    } else {
      container.insertBefore(nodeToMove, referenceNode);
    }
  }

  function moveTier(tierId, direction) {
    const main = document.getElementById('main-container') || document.querySelector('main');
    const tierEl = document.querySelector(`[data-tier-id="${tierId}"]`);
    if (!main || !tierEl) return;

    if (direction === 'up' || direction === 'prev') {
      const prev = tierEl.previousElementSibling;
      if (prev && prev.hasAttribute('data-tier-id')) {
        moveDomElement(main, tierEl, prev);
      }
    } else if (direction === 'down' || direction === 'next') {
      const next = tierEl.nextElementSibling;
      if (next && next.hasAttribute('data-tier-id')) {
        moveDomElement(main, tierEl, next.nextElementSibling);
      }
    }

    if (window.ChartsManager && typeof window.ChartsManager.resizeCharts === 'function') {
      window.ChartsManager.resizeCharts();
    }
  }

  function swapTiers() {
    const main = document.getElementById('main-container') || document.querySelector('main');
    if (!main) return;
    const firstTier = main.querySelector('[data-tier-id]');
    if (firstTier) {
      moveDomElement(main, firstTier, null);
      if (window.ChartsManager && typeof window.ChartsManager.resizeCharts === 'function') {
        window.ChartsManager.resizeCharts();
      }
    }
  }

  function setWorkspaceTab(tabName) {
    // Normalise legacy / alias tab names
    if (tabName === 'all' || tabName === 'bodycomp') tabName = 'adaptation';
    if (!['adaptation', 'recovery', 'bloodwork', 'history', 'protocol'].includes(tabName)) {
      tabName = 'adaptation';
    }

    const tabs = ['adaptation', 'recovery', 'bloodwork', 'history', 'protocol'];
    tabs.forEach(t => {
      // 1. Workspace container visibility toggle
      const wsEl = document.getElementById(`workspace-${t}`);
      if (wsEl) {
        if (t === tabName) {
          wsEl.classList.remove('hidden');
        } else {
          wsEl.classList.add('hidden');
        }
      }

      // 2. Workspace navigation tab button styling
      const btn = document.getElementById(`tab-ws-${t}`);
      if (btn) {
        if (t === tabName) {
          btn.className = 'workspace-tab-btn px-2 sm:px-2.5 py-1 sm:py-0.5 rounded-md font-semibold transition text-slate-900 bg-white dark:bg-slate-800 dark:text-white shadow-xs text-center whitespace-nowrap';
        } else {
          btn.className = 'workspace-tab-btn px-2 sm:px-2.5 py-1 sm:py-0.5 rounded-md font-medium text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white transition text-center whitespace-nowrap';
        }
      }

      // 3. Mobile Bottom Navigation Bar styling sync (Finding P1.3)
      const mobBtn = document.getElementById(`mob-tab-ws-${t}`);
      if (mobBtn) {
        if (t === tabName) {
          mobBtn.className = 'mob-nav-btn flex flex-col items-center justify-center flex-1 py-1 text-purple-600 dark:text-purple-400 font-bold transition';
        } else {
          mobBtn.className = 'mob-nav-btn flex flex-col items-center justify-center flex-1 py-1 text-slate-500 dark:text-slate-400 font-medium hover:text-slate-800 dark:hover:text-slate-200 transition';
        }
      }
    });

    // 4. Toggle 1-line Today Protocol Strip in non-protocol workspaces (Finding P1.1)
    const todayStrip = document.getElementById('today-protocol-strip');
    if (todayStrip) {
      if (tabName === 'protocol') {
        todayStrip.classList.add('hidden');
      } else {
        todayStrip.classList.remove('hidden');
      }
    }

    if (currentLayout) {
      currentLayout.active_workspace = tabName;
      currentLayout.active_tab = tabName;
    }

    // Trigger Chart.js canvas resize across all modules
    if (window.ChartsManager && typeof window.ChartsManager.resizeCharts === 'function') {
      window.ChartsManager.resizeCharts();
    }
    if (window.ExerciseManager && typeof window.ExerciseManager.resizeCharts === 'function') {
      window.ExerciseManager.resizeCharts();
    }
    if (window.BloodworkManager && typeof window.BloodworkManager.resizeCharts === 'function') {
      window.BloodworkManager.resizeCharts();
    }
  }

  function setViewportTab(tabName) {
    setWorkspaceTab(tabName);
  }

  function applyLayout(layout) {
    if (!layout) return;

    // 1. Tier Order: Tier 2 is strictly scoped inside #workspace-protocol (Finding P1.1)
    // Preserving Tier 2 within workspace-protocol prevents bleeding into other tabs.


    // 2. Tier Collapsed States
    const t1 = document.getElementById('tier-1-section');
    if (t1) setWidgetCollapsed(t1, !!layout.tier1_collapsed);
    const t2 = document.getElementById('tier-2-section');
    if (t2) setWidgetCollapsed(t2, !!layout.tier2_collapsed);

    // 3. Matrix
    const matrixEl = document.getElementById('widget-adherence-matrix');
    if (matrixEl) {
      setWidgetCollapsed(matrixEl, !!layout.matrix_collapsed);
    }

    // 4. Explorers Zone Reorder, State & Span
    const explorersZone = document.getElementById('zone-explorers') || document.getElementById('zone-tier1-outcomes');
    // 4. Explorers State & Span (Workspace-Scoped)
    if (Array.isArray(layout.explorers)) {
      layout.explorers.forEach(item => {
        const el = document.querySelector(`[data-widget-id="${item.id}"]`);
        if (el) {
          setWidgetCollapsed(el, !!item.collapsed);
          setWidgetSpan(el, item.span || 2);
        }
      });
    }

    // 5. Actions Zone Reorder & State
    const actionsZone = document.getElementById('zone-tier2-actions') || document.getElementById('zone-tier3-actions');
    if (actionsZone && Array.isArray(layout.actions)) {
      layout.actions.forEach(item => {
        const el = document.querySelector(`[data-widget-id="${item.id}"]`);
        if (el) {
          moveDomElement(actionsZone, el, null);
          setWidgetCollapsed(el, !!item.collapsed);
        }
      });
    }

    // 6. Active Workspace Tab
    const activeTab = layout.active_workspace || layout.active_tab || 'adaptation';
    setWorkspaceTab(activeTab);

    // Trigger Chart.js canvas resize
    if (window.ChartsManager && typeof window.ChartsManager.resizeCharts === 'function') {
      window.ChartsManager.resizeCharts();
    }
    if (window.ExerciseManager && typeof window.ExerciseManager.resizeCharts === 'function') {
      window.ExerciseManager.resizeCharts();
    }
    if (window.BloodworkManager && typeof window.BloodworkManager.resizeCharts === 'function') {
      window.BloodworkManager.resizeCharts();
    }
  }

  function setWidgetCollapsed(widgetEl, isCollapsed) {
    if (!widgetEl) return;
    const bodyEl = widgetEl.querySelector('.widget-body');
    const toggleBtn = widgetEl.querySelector('.widget-toggle-btn');
    const previewEl = widgetEl.querySelector('.widget-collapsed-preview');

    if (isCollapsed) {
      widgetEl.classList.add('is-collapsed');
      if (bodyEl) bodyEl.classList.add('hidden');
      if (previewEl) previewEl.classList.remove('hidden');
      if (toggleBtn) {
        toggleBtn.setAttribute('aria-expanded', 'false');
        toggleBtn.classList.add('rotate-180');
      }
    } else {
      widgetEl.classList.remove('is-collapsed');
      if (bodyEl) bodyEl.classList.remove('hidden');
      if (previewEl) previewEl.classList.add('hidden');
      if (toggleBtn) {
        toggleBtn.setAttribute('aria-expanded', 'true');
        toggleBtn.classList.remove('rotate-180');
      }
    }
  }

  function toggleWidgetCollapse(widgetId) {
    const el = document.querySelector(`[data-widget-id="${widgetId}"]`) || document.getElementById(widgetId);
    if (!el) return;
    const isCurrentlyCollapsed = el.classList.contains('is-collapsed');
    setWidgetCollapsed(el, !isCurrentlyCollapsed);

    if (isCurrentlyCollapsed) {
      if (window.ChartsManager && typeof window.ChartsManager.resizeCharts === 'function') {
        window.ChartsManager.resizeCharts();
      }
    }
  }

  function toggleTierCollapse(tierId) {
    const el = document.getElementById(tierId) || document.querySelector(`[data-tier-id="${tierId}"]`);
    if (!el) return;
    const isCurrentlyCollapsed = el.classList.contains('is-collapsed');
    setWidgetCollapsed(el, !isCurrentlyCollapsed);
    if (isCurrentlyCollapsed) {
      if (window.ChartsManager && typeof window.ChartsManager.resizeCharts === 'function') {
        window.ChartsManager.resizeCharts();
      }
    }
  }

  function setWidgetSpan(widgetEl, span) {
    if (!widgetEl) return;
    const spanBtn = widgetEl.querySelector('.widget-span-btn');
    if (span === 2) {
      widgetEl.classList.remove('lg:col-span-1');
      widgetEl.classList.add('lg:col-span-2');
      if (spanBtn) {
        spanBtn.innerHTML = '⇱';
        spanBtn.title = 'Shrink to Half Width (1-Column)';
      }
    } else {
      widgetEl.classList.remove('lg:col-span-2');
      widgetEl.classList.add('lg:col-span-1');
      if (spanBtn) {
        spanBtn.innerHTML = '⇲';
        spanBtn.title = 'Expand to Full Width (2-Columns)';
      }
    }
  }

  function toggleWidgetSpan(widgetId) {
    const el = document.querySelector(`[data-widget-id="${widgetId}"]`);
    if (!el) return;
    const isSpan2 = el.classList.contains('lg:col-span-2');
    setWidgetSpan(el, isSpan2 ? 1 : 2);

    if (window.ChartsManager && typeof window.ChartsManager.resizeCharts === 'function') {
      window.ChartsManager.resizeCharts();
    }
  }

  function moveWidget(widgetId, direction) {
    const el = document.querySelector(`[data-widget-id="${widgetId}"]`);
    if (!el || !el.parentElement) return;

    const container = el.parentElement;
    if (direction === 'up' || direction === 'prev') {
      const prev = el.previousElementSibling;
      if (prev && prev.hasAttribute('data-widget-id')) {
        moveDomElement(container, el, prev);
      }
    } else if (direction === 'down' || direction === 'next') {
      const next = el.nextElementSibling;
      if (next && next.hasAttribute('data-widget-id')) {
        moveDomElement(container, el, next.nextElementSibling);
      }
    }

    if (window.ChartsManager && typeof window.ChartsManager.resizeCharts === 'function') {
      window.ChartsManager.resizeCharts();
    }
  }

  function expandAll() {
    document.querySelectorAll('[data-widget-id], [data-tier-id], #widget-adherence-matrix').forEach(el => {
      setWidgetCollapsed(el, false);
    });
    if (window.ChartsManager && typeof window.ChartsManager.resizeCharts === 'function') {
      window.ChartsManager.resizeCharts();
    }
  }

  function collapseAll() {
    document.querySelectorAll('[data-widget-id], [data-tier-id], #widget-adherence-matrix').forEach(el => {
      setWidgetCollapsed(el, true);
    });
  }

  // ---------------------------------------------------------------------------
  // Robust Drag and Drop Engine (Jitter-free DOM positioning)
  // ---------------------------------------------------------------------------
  function initDragAndDrop() {
    // 1. Setup Drop Zones (including #main-container for Tier swapping)
    const zones = document.querySelectorAll('[data-widget-zone]');
    zones.forEach(zone => {
      zone.addEventListener('dragover', handleDragOver);
      zone.addEventListener('drop', handleDrop);
    });

    // 2. Setup Grab Handles: activates card draggable on mousedown
    const handles = document.querySelectorAll('.widget-drag-handle, .tier-drag-handle');
    handles.forEach(handle => {
      handle.setAttribute('draggable', 'false'); // Handle is not dragged; parent card/tier is!
      
      handle.addEventListener('mousedown', () => {
        const card = handle.closest('[data-widget-id], [data-tier-id]');
        if (card) {
          card.setAttribute('draggable', 'true');
        }
      });

      handle.addEventListener('mouseleave', () => {
        if (!draggedCard) {
          const card = handle.closest('[data-widget-id], [data-tier-id]');
          if (card) card.removeAttribute('draggable');
        }
      });
    });

    // 3. Attach drag events to all cards and tier sections
    const draggables = document.querySelectorAll('[data-widget-id], [data-tier-id]');
    draggables.forEach(card => {
      card.addEventListener('dragstart', handleDragStart);
      card.addEventListener('dragend', handleDragEnd);
    });
  }

  function handleDragStart(e) {
    const card = e.target.closest('[data-widget-id], [data-tier-id]');
    if (!card || card.getAttribute('draggable') !== 'true') {
      e.preventDefault();
      return;
    }

    draggedCard = card;
    card.classList.add('opacity-40', 'scale-[0.99]', 'ring-2', 'ring-emerald-500');

    e.dataTransfer.effectAllowed = 'move';
    const id = card.getAttribute('data-widget-id') || card.getAttribute('data-tier-id') || 'dragged';
    e.dataTransfer.setData('text/plain', id);
  }

  function handleDragOver(e) {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
    if (!draggedCard) return;

    const zone = e.currentTarget;

    // CASE 1: Dragging an entire Tier Section (Tier 1 vs Tier 2)
    if (draggedCard.hasAttribute('data-tier-id')) {
      if (zone.getAttribute('data-widget-zone') !== 'tier-sections') return;
      const overTier = e.target.closest('[data-tier-id]');
      if (overTier && overTier !== draggedCard && overTier.parentElement === zone) {
        const rect = overTier.getBoundingClientRect();
        const isBefore = e.clientY < (rect.top + rect.height / 2);
        const targetNode = isBefore ? overTier : overTier.nextElementSibling;
        if (draggedCard !== targetNode && draggedCard.nextElementSibling !== targetNode) {
          moveDomElement(zone, draggedCard, targetNode);
        }
      }
      return;
    }

    // CASE 2: Dragging a Widget Card (Inside Zone or Cross-Zone)
    if (zone.getAttribute('data-widget-zone') === 'tier-sections') return;

    const overCard = e.target.closest('[data-widget-id]');
    if (overCard && overCard !== draggedCard) {
      const rect = overCard.getBoundingClientRect();
      const isGrid = zone.classList.contains('grid');
      let isBefore = false;

      if (isGrid) {
        const isSameRow = Math.abs(e.clientY - (rect.top + rect.height / 2)) < rect.height / 3;
        if (isSameRow) {
          isBefore = e.clientX < (rect.left + rect.width / 2);
        } else {
          isBefore = e.clientY < (rect.top + rect.height / 2);
        }
      } else {
        isBefore = e.clientY < (rect.top + rect.height / 2);
      }

      const targetNode = isBefore ? overCard : overCard.nextElementSibling;
      if (draggedCard !== targetNode && draggedCard.nextElementSibling !== targetNode) {
        moveDomElement(zone, draggedCard, targetNode);
      }
    } else if (!overCard && (e.target === zone || e.target.parentElement === zone)) {
      // Dragging over empty space in zone
      if (draggedCard.parentElement !== zone || draggedCard.nextElementSibling !== null) {
        moveDomElement(zone, draggedCard, null);
      }
    }
  }

  function handleDrop(e) {
    e.preventDefault();
    cleanupDragState();
  }

  function handleDragEnd() {
    cleanupDragState();
  }

  function cleanupDragState() {
    if (draggedCard) {
      draggedCard.classList.remove('opacity-40', 'scale-[0.99]', 'ring-2', 'ring-emerald-500');
      draggedCard.removeAttribute('draggable');
      draggedCard = null;
    }
    document.querySelectorAll('[draggable="true"]').forEach(el => el.removeAttribute('draggable'));
    if (window.ChartsManager && typeof window.ChartsManager.resizeCharts === 'function') {
      window.ChartsManager.resizeCharts();
    }
    if (window.BloodworkManager && typeof window.BloodworkManager.resizeCharts === 'function') {
      window.BloodworkManager.resizeCharts();
    }
  }

  // ---------------------------------------------------------------------------
  // Telemetry Summary Chips (Real Data Synchronizer)
  // ---------------------------------------------------------------------------
  function updatePreviewChips(data) {
    if (!data) return;

    // 1. Body Composition Preview
    const bcPreview = document.getElementById('preview-exp-bodycomp');
    if (bcPreview && data.body_comp && data.body_comp.stats) {
      const st = data.body_comp.stats;
      const wt = (st.latest_weight ?? st.latest_weight_kg) ? `${st.latest_weight ?? st.latest_weight_kg} kg` : '—';
      const ffmi = st.current_ffmi_norm ?? st.current_ffmi ?? '—';
      const fat = st.latest_fat_pct ? `${st.latest_fat_pct}% BF` : '—';
      bcPreview.innerHTML = `<span class="text-slate-700 dark:text-slate-300 font-mono font-bold">${wt}</span> • <span class="text-cyan-600 dark:text-cyan-400 font-bold">FFMI ${ffmi}</span> • <span class="text-emerald-600 dark:text-emerald-400">${fat}</span>`;
    }

    // 2. Nutrition Preview
    const nutrPreview = document.getElementById('preview-exp-nutrition');
    if (nutrPreview && data.nutrition) {
      const avg = data.nutrition.avg_protein_complete_90d != null ? `${data.nutrition.avg_protein_complete_90d}g` : '—';
      nutrPreview.innerHTML = `<span class="text-amber-600 dark:text-amber-400 font-mono font-bold">${avg}</span> Complete Avg • <span class="text-slate-500 dark:text-slate-400">Target 180g</span>`;
    }

    // 2b. Exercise Preview (ADR-026)
    const exPreview = document.getElementById('preview-exp-exercise');
    if (exPreview && data.exercise) {
      const c = data.exercise.cadence_7d;
      const latest = data.exercise.recent_sessions && data.exercise.recent_sessions[0];
      const latestStr = latest ? `${latest.name.split(' ')[0]} ${Math.round(latest.duration_m || 0)}m` : 'Workouts';
      exPreview.innerHTML = `<span class="text-slate-700 dark:text-slate-300 font-mono font-bold">${c.total_sessions}/${c.target_sessions} Sessions</span> • <span class="text-emerald-600 dark:text-emerald-400 font-mono font-bold">${Math.round(c.total_calories)} kcal</span> • <span class="text-slate-500 dark:text-slate-400">${latestStr}</span>`;
    }

    // 3. Autonomic Preview
    const autoPreview = document.getElementById('preview-exp-autonomic');
    if (autoPreview) {
      const series = data.autonomic?.series || [];
      const latestAuto = series.length > 0 ? series[series.length - 1] : null;
      const hrv = latestAuto?.hrv_rmssd != null ? `HRV ${Math.round(latestAuto.hrv_rmssd)}ms` : 'HRV —';
      const rhr = latestAuto?.resting_heart_rate != null ? `RHR ${Math.round(latestAuto.resting_heart_rate)} bpm` : 'RHR —';
      const hrrVal = data.autonomic?.latest_hrr_meta?.duration_s != null ? `HRR-60 Active` : (data.autonomic?.latest_hrr_60?.length ? `HRR-60 -15bpm` : 'HRR-60 —');
      autoPreview.innerHTML = `<span class="text-cyan-600 dark:text-cyan-400 font-mono font-bold">${hrv}</span> • <span class="text-slate-700 dark:text-slate-300">${rhr}</span> • <span class="text-emerald-600 dark:text-emerald-400">${hrrVal}</span>`;
    }

    // 4. Sleep Preview
    const sleepPreview = document.getElementById('preview-exp-sleep');
    if (sleepPreview && data.sleep) {
      if (data.sleep.is_gated) {
        sleepPreview.innerHTML = `<span class="text-amber-600 dark:text-amber-400 font-bold">Coverage Gated</span> (${data.sleep.recent_7d_nights || 0}/7 nights)`;
      } else {
        const avgH = data.sleep.averages_7d?.avg_duration_h != null ? `${data.sleep.averages_7d.avg_duration_h}h` : '—';
        const eff = data.sleep.averages_7d?.avg_efficiency_pct != null ? `${data.sleep.averages_7d.avg_efficiency_pct}%` : '—';
        sleepPreview.innerHTML = `<span class="text-purple-600 dark:text-purple-400 font-mono font-bold">${avgH}</span> Avg • <span class="text-emerald-600 dark:text-emerald-400 font-bold">${eff} Eff</span>`;
      }
    }

    // 5. Bloodwork Preview
    const bwPreview = document.getElementById('preview-exp-bloodwork');
    if (bwPreview && data.bloodwork) {
      const rawDate = data.bloodwork.latest_draw_date;
      let dateLabel = '—';
      if (rawDate) {
        try {
          const parts = rawDate.split('-');
          const d = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
          dateLabel = d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
        } catch (e) {
          dateLabel = rawDate;
        }
      }
      const oor = data.bloodwork.consultation_brief?.out_of_range_count;
      const oorStr = (oor !== null && oor !== undefined) ? `${oor} Out of Range` : '0 Out of Range';
      bwPreview.innerHTML = `<span class="text-slate-700 dark:text-slate-300 font-mono font-bold">Latest: ${dateLabel}</span> • <span class="text-amber-600 dark:text-amber-400 font-bold">${oorStr}</span> • <span class="text-purple-600 dark:text-purple-400">Next Review: Q4</span>`;
    }

    // 6. Life Eras Preview
    const lifePreview = document.getElementById('preview-exp-lifeeras');
    if (lifePreview && data.life_eras) {
      lifePreview.innerHTML = `<span class="text-slate-700 dark:text-slate-300 font-medium">${data.life_eras.length} Historical Epochs (2014–2026)</span>`;
    }

    // 6. Action Cards Previews
    const cadencePreview = document.getElementById('preview-act-cadence');
    if (cadencePreview) {
      const h = data.hud?.timers?.hours_remaining ?? data.hud?.timers?.cadence_hours_remaining ?? (data.hud?.timers ? Object.values(data.hud.timers).find(v => typeof v === 'number') : null);
      cadencePreview.innerText = h !== null && h !== undefined ? `${h}h remaining` : 'Cadence Target';
    }

    const intervalPreview = document.getElementById('preview-act-interval');
    if (intervalPreview) {
      const d = data.hud?.timers?.interval_days;
      intervalPreview.innerText = d !== null && d !== undefined ? `Day ${d}` : 'Interval Target';
    }

    const prePreview = document.getElementById('preview-act-preworkout');
    if (prePreview) {
      prePreview.innerText = 'T-60m Stack';
    }

    const deloadPreview = document.getElementById('preview-act-deload');
    if (deloadPreview) {
      deloadPreview.innerText = 'Standard Mode • Deload W6';
    }

    // 7. Matrix Preview
    const matPreview = document.getElementById('preview-tier1-matrix');
    if (matPreview && data.hud?.phase) {
      matPreview.innerText = `Week ${data.hud.phase.current_week || 1} • 7-Day Adherence Matrix`;
    }

    // 8. Tier 1 (Physiological Telemetry & Core Explorers) Summary Preview Chip
    const t1Preview = document.getElementById('preview-tier1-section');
    if (t1Preview && data.body_comp && data.body_comp.stats) {
      const st = data.body_comp.stats;
      const wt = (st.latest_weight ?? st.latest_weight_kg) ? `${st.latest_weight ?? st.latest_weight_kg} kg` : '—';
      const ffmi = st.current_ffmi_norm ?? st.current_ffmi ?? '—';
      const fat = st.latest_fat_pct ? `${st.latest_fat_pct}% BF` : '—';
      const prot = data.nutrition?.avg_protein_complete_90d != null ? `${data.nutrition.avg_protein_complete_90d}g Prot` : '— Prot';
      const sleepText = data.sleep?.is_gated ? `Sleep Gated (${data.sleep.recent_7d_nights || 0}/7n)` : `${data.sleep?.averages_7d?.avg_duration_h ?? '—'}h Sleep`;
      const series = data.autonomic?.series || [];
      const latestAuto = series.length > 0 ? series[series.length - 1] : null;
      const hrvStr = latestAuto?.hrv_rmssd != null ? `HRV ${Math.round(latestAuto.hrv_rmssd)}ms` : 'HRV —';
      t1Preview.innerHTML = `<span class="text-slate-700 dark:text-slate-300 font-mono font-bold">${wt}</span> • <span class="text-cyan-600 dark:text-cyan-400 font-bold">FFMI ${ffmi}</span> • <span class="text-emerald-600 dark:text-emerald-400 font-medium">${fat}</span> • <span class="text-amber-600 dark:text-amber-400 font-mono">${prot}</span> • <span class="text-cyan-600 dark:text-cyan-400 font-mono font-bold">${hrvStr}</span> • <span class="text-purple-600 dark:text-purple-400 font-medium">${sleepText}</span>`;
    }

    // 9. Tier 2 (Protocol Operations, Compliance & Safety) Summary Preview Chip
    const t2Preview = document.getElementById('preview-tier2-section');
    if (t2Preview) {
      const week = data.hud?.phase?.current_week || 1;
      const cadenceH = data.hud?.timers?.hours_remaining ?? data.hud?.timers?.cadence_hours_remaining;
      const cadenceText = (cadenceH !== null && cadenceH !== undefined) ? `Protocol in ${cadenceH}h` : 'Protocol Active';
      const e2Active = data.hud?.e2_alert?.active;
      const e2Text = e2Active ? `⚠️ Fluid Alert (+${data.hud.e2_alert.delta_48h}kg)` : 'Fluid Stable';
      t2Preview.innerHTML = `<span class="text-purple-700 dark:text-purple-400 font-bold">Week ${week}</span> • <span class="text-slate-700 dark:text-slate-300">Active Phase</span> • <span class="text-cyan-600 dark:text-cyan-400">${cadenceText}</span> • <span class="text-slate-600 dark:text-slate-400">${e2Text}</span>`;
    }

    // 10. Today Strip Summary (Finding P1.1)
    const stripDoses = document.getElementById('today-strip-doses');
    const stripMilestone = document.getElementById('today-strip-milestone');
    if (stripDoses && data.hud?.adherence_matrix?.rows) {
      const todayIso = new Date().toISOString().slice(0, 10);
      let done = 0;
      const total = data.hud.adherence_matrix.rows.length;
      data.hud.adherence_matrix.rows.forEach(r => {
        const d = r.days ? r.days.find(x => x.date === todayIso) : null;
        if (d && d.status === 'done') done++;
      });
      stripDoses.innerText = `${done} of ${total} doses logged today`;
    }
    if (stripMilestone && data.hud?.phase) {
      const p = data.hud.phase;
      stripMilestone.innerText = `${p.name || 'Cycle 1'} (Week ${p.current_week || 1} of ${p.total_weeks || 16})`;
    }
  }

  // ---------------------------------------------------------------------------
  // Studio Mode Gating (Finding P2.2)
  // ---------------------------------------------------------------------------
  let isStudioModeActive = false;
  function toggleStudioMode(forceState = null) {
    if (forceState !== null) {
      isStudioModeActive = !!forceState;
    } else {
      isStudioModeActive = !isStudioModeActive;
    }
    if (isStudioModeActive) {
      document.body.classList.add('studio-active');
    } else {
      document.body.classList.remove('studio-active');
    }
    const indicator = document.getElementById('studio-mode-indicator');
    if (indicator) {
      indicator.className = isStudioModeActive 
        ? 'w-2 h-2 rounded-full bg-purple-500 animate-pulse' 
        : 'w-2 h-2 rounded-full bg-slate-400';
    }
    const btn = document.getElementById('btn-toggle-studio-mode');
    if (btn) {
      if (isStudioModeActive) {
        btn.classList.add('border-purple-400', 'bg-purple-50', 'text-purple-700', 'dark:bg-purple-950/50', 'dark:text-purple-300');
      } else {
        btn.classList.remove('border-purple-400', 'bg-purple-50', 'text-purple-700', 'dark:bg-purple-950/50', 'dark:text-purple-300');
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Lifecycle Bootstrapper
  // ---------------------------------------------------------------------------
  function syncHeaderOffset() {
    const header = document.getElementById('main-header') || document.querySelector('header');
    const banner = document.getElementById('layout-control-banner');
    if (header && banner) {
      banner.style.top = `${header.offsetHeight}px`;
    }
  }

  function init() {
    initDragAndDrop();
    initKeyboardShortcuts();
    currentLayout = loadSavedLayout();
    applyLayout(currentLayout);
    syncHeaderOffset();
    window.addEventListener('resize', syncHeaderOffset);

    if (window.__DASHBOARD_DATA__ || window.globalDashboardData) {
      updatePreviewChips(window.__DASHBOARD_DATA__ || window.globalDashboardData);
    }
  }

  return {
    init,
    saveLayout,
    resetLayout,
    swapTiers,
    moveTier,
    setWorkspaceTab,
    setViewportTab,
    expandAll,
    collapseAll,
    toggleTierCollapse,
    toggleWidgetCollapse,
    toggleWidgetSpan,
    moveWidget,
    updatePreviewChips,
    toggleStudioMode
  };
})();

// Attach globally for inline handlers and app coordination
window.WidgetManager = WidgetManager;
window.toggleStudioMode = WidgetManager.toggleStudioMode;
window.swapTiers = WidgetManager.swapTiers;
window.moveTier = WidgetManager.moveTier;
window.setWorkspaceTab = WidgetManager.setWorkspaceTab;
window.setViewportTab = WidgetManager.setViewportTab;
window.toggleTierCollapse = WidgetManager.toggleTierCollapse;
window.toggleWidgetCollapse = WidgetManager.toggleWidgetCollapse;
window.toggleWidgetSpan = WidgetManager.toggleWidgetSpan;
window.moveWidget = WidgetManager.moveWidget;
window.saveWidgetLayout = WidgetManager.saveLayout;
window.resetWidgetLayout = WidgetManager.resetLayout;
window.expandAllWidgets = WidgetManager.expandAll;
window.collapseAllWidgets = WidgetManager.collapseAll;

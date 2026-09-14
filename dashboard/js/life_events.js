/**
 * dashboard/js/life_events.js
 * 
 * Milestone 24: Contextual Life Events & Superimposition Engine (ADR-038).
 * Manages acute life events (travel, surgery, illness, injury, stress),
 * dynamic chart superimposition filters, and the History Workspace Curator.
 */

const LifeEventsManager = (function() {
  'use strict';

  // Category visual metadata & styling
  const CATEGORIES = {
    travel: {
      name: 'Travel & Vacations',
      icon: '✈️',
      color: 'rgba(14, 165, 233, 0.12)',
      border: 'rgba(14, 165, 233, 0.65)',
      text: '#0284c7',
      bgBadge: 'bg-sky-100 dark:bg-sky-900/40 text-sky-800 dark:text-sky-300'
    },
    surgery: {
      name: 'Surgery & Medical',
      icon: '🏥',
      color: 'rgba(239, 68, 68, 0.12)',
      border: 'rgba(239, 68, 68, 0.65)',
      text: '#dc2626',
      bgBadge: 'bg-rose-100 dark:bg-rose-900/40 text-rose-800 dark:text-rose-300'
    },
    illness: {
      name: 'Illness & Infection',
      icon: '🤒',
      color: 'rgba(245, 158, 11, 0.12)',
      border: 'rgba(245, 158, 11, 0.65)',
      text: '#d97706',
      bgBadge: 'bg-amber-100 dark:bg-amber-900/40 text-amber-800 dark:text-amber-300'
    },
    injury: {
      name: 'Injury & Deload',
      icon: '🏋️',
      color: 'rgba(168, 85, 247, 0.12)',
      border: 'rgba(168, 85, 247, 0.65)',
      text: '#9333ea',
      bgBadge: 'bg-purple-100 dark:bg-purple-900/40 text-purple-800 dark:text-purple-300'
    },
    stress: {
      name: 'Stress Sprint',
      icon: '⚡',
      color: 'rgba(234, 179, 8, 0.12)',
      border: 'rgba(234, 179, 8, 0.65)',
      text: '#ca8a04',
      bgBadge: 'bg-yellow-100 dark:bg-yellow-900/40 text-yellow-800 dark:text-yellow-300'
    }
  };

  // Active filter state
  const filterState = {
    eras: true,
    travel: true,
    surgery: true,
    illness: true,
    injury: true,
    stress: true
  };

  let eventsCache = [];

  /**
   * Initializes LifeEventsManager with data from injected dashboard payload.
   */
  function init(data) {
    if (data && Array.isArray(data.life_events)) {
      eventsCache = data.life_events.slice();
    } else if (window.__DASHBOARD_DATA__ && Array.isArray(window.__DASHBOARD_DATA__.life_events)) {
      eventsCache = window.__DASHBOARD_DATA__.life_events.slice();
    }
    renderFilterStrip('life-events-filter-strip');
    renderCuratorTable();
  }

  /**
   * Returns all events currently loaded.
   */
  function getEvents() {
    return eventsCache;
  }

  /**
   * Returns active events matching the current filter state.
   */
  function getActiveEvents() {
    return eventsCache.filter(evt => {
      const cat = evt.category || 'travel';
      return filterState[cat] !== false;
    });
  }

  /**
   * Checks whether life eras background overlay is enabled.
   */
  function areErasEnabled() {
    return filterState.eras !== false;
  }

  /**
   * Toggles a category filter and triggers chart refresh.
   */
  function toggleCategory(cat) {
    if (filterState[cat] !== undefined) {
      filterState[cat] = !filterState[cat];
      renderFilterStrip('life-events-filter-strip');
      if (window.ChartRenderer && typeof window.ChartRenderer.refreshActiveCharts === 'function') {
        window.ChartRenderer.refreshActiveCharts();
      }
    }
  }

  /**
   * Renders the interactive toggle filter strip into the target DOM element.
   */
  function renderFilterStrip(containerId) {
    const container = document.getElementById(containerId);
    if (!container) return;

    let html = `
      <div class="flex flex-wrap items-center gap-1.5 text-xs">
        <span class="text-slate-500 dark:text-slate-400 font-medium mr-1 select-none">Overlay:</span>
        <button type="button" onclick="LifeEventsManager.toggleCategory('eras')"
          class="px-2.5 py-1 rounded-lg border transition font-medium flex items-center gap-1 ${
            filterState.eras
              ? 'bg-purple-100/80 dark:bg-purple-900/40 border-purple-300 dark:border-purple-700 text-purple-800 dark:text-purple-300 shadow-sm'
              : 'bg-slate-100/60 dark:bg-slate-800/40 border-slate-200 dark:border-slate-800 text-slate-400 opacity-60'
          }" title="Toggle Macro Life Eras">
          <span>📜</span> <span>Eras</span>
        </button>
    `;

    Object.keys(CATEGORIES).forEach(cat => {
      const meta = CATEGORIES[cat];
      const isActive = filterState[cat] !== false;
      html += `
        <button type="button" onclick="LifeEventsManager.toggleCategory('${cat}')"
          class="px-2.5 py-1 rounded-lg border transition font-medium flex items-center gap-1 ${
            isActive
              ? `${meta.bgBadge} border-current shadow-sm`
              : 'bg-slate-100/60 dark:bg-slate-800/40 border-slate-200 dark:border-slate-800 text-slate-400 opacity-60'
          }" title="Toggle ${meta.name}">
          <span>${meta.icon}</span> <span>${meta.name.split(' ')[0]}</span>
        </button>
      `;
    });

    html += `</div>`;
    container.innerHTML = html;
  }

  /**
   * Formats physiological impact flags as clean UI badges.
   */
  function formatImpactBadges(impact) {
    if (!impact || typeof impact !== 'object') return '<span class="text-slate-400">—</span>';
    const badges = [];
    if (impact.fluid_retention) {
      badges.push('<span class="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-sky-100 dark:bg-sky-950/60 text-sky-800 dark:text-sky-300" title="Expected +1.5 to +3.0kg fluid retention">💧 Fluid Shift</span>');
    }
    if (impact.elevated_rhr) {
      badges.push('<span class="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-rose-100 dark:bg-rose-950/60 text-rose-800 dark:text-rose-300" title="Nocturnal RHR elevation">📈 RHR Spike</span>');
    }
    if (impact.sleep_disruption) {
      badges.push('<span class="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-indigo-100 dark:bg-indigo-950/60 text-indigo-800 dark:text-indigo-300" title="Autonomic dip / sleep disturbance">📉 Sleep/HRV</span>');
    }
    if (impact.training_hiatus) {
      badges.push('<span class="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-amber-100 dark:bg-amber-950/60 text-amber-800 dark:text-amber-300" title="Training deload or temporary cessation">⏸️ Deload</span>');
    }
    if (impact.ast_alt_spike) {
      badges.push('<span class="px-1.5 py-0.5 rounded text-[10px] font-semibold bg-orange-100 dark:bg-orange-950/60 text-orange-800 dark:text-orange-300" title="Transient hepatic transaminase elevation">🧪 Transaminases</span>');
    }
    return badges.length > 0 ? badges.join(' ') : '<span class="text-slate-400 text-xs">—</span>';
  }

  /**
   * Renders the Life Events table in Workspace 4 History Curator.
   */
  function renderCuratorTable() {
    const tbody = document.getElementById('life-events-tbody');
    if (!tbody) return;

    if (!eventsCache.length) {
      tbody.innerHTML = `
        <tr>
          <td colspan="6" class="p-6 text-center text-slate-500 dark:text-slate-400 italic">
            No contextual life events recorded yet. Click "+ Add Life Event" to log an acute episode.
          </td>
        </tr>
      `;
      return;
    }

    let rowsHtml = '';
    const sorted = eventsCache.slice().sort((a, b) => (b.start_date || '').localeCompare(a.start_date || ''));

    sorted.forEach(evt => {
      const cat = CATEGORIES[evt.category] || CATEGORIES.travel;
      const startD = new Date(evt.start_date);
      const endD = evt.end_date ? new Date(evt.end_date) : null;
      const durationDays = endD ? Math.max(1, Math.round((endD - startD) / (1000 * 60 * 60 * 24))) : 1;

      const dateStr = evt.end_date && evt.end_date !== evt.start_date
        ? `${evt.start_date} &rarr; ${evt.end_date} <span class="text-slate-400 font-sans">(${durationDays}d)</span>`
        : `${evt.start_date} <span class="text-slate-400 font-sans">(Single Day)</span>`;

      rowsHtml += `
        <tr class="hover:bg-slate-50 dark:hover:bg-slate-900/40 transition">
          <td class="p-3 font-semibold text-slate-900 dark:text-white flex items-center gap-2">
            <span class="text-base">${cat.icon}</span>
            <div>
              <div>${evt.title}</div>
              ${evt.tags && evt.tags.length ? `
                <div class="flex flex-wrap gap-1 mt-0.5">
                  ${evt.tags.map(t => `<span class="text-[10px] text-slate-400 font-mono">#${t}</span>`).join(' ')}
                </div>
              ` : ''}
            </div>
          </td>
          <td class="p-3">
            <span class="px-2 py-0.5 rounded text-xs font-semibold uppercase ${cat.bgBadge}">
              ${evt.category}
            </span>
          </td>
          <td class="p-3 text-slate-500 dark:text-slate-400 font-mono text-xs whitespace-nowrap">
            ${dateStr}
          </td>
          <td class="p-3">
            ${formatImpactBadges(evt.physiological_impact)}
          </td>
          <td class="p-3 text-slate-600 dark:text-slate-300 text-xs max-w-xs">
            ${evt.notes || '<span class="text-slate-400 italic">No notes</span>'}
          </td>
          <td class="p-3 text-right">
            <button type="button" onclick="LifeEventsManager.openModal('${evt.id}')"
              class="px-2 py-1 rounded text-xs text-purple-600 dark:text-purple-400 hover:bg-purple-50 dark:hover:bg-purple-950/30 transition">
              Edit
            </button>
          </td>
        </tr>
      `;
    });

    tbody.innerHTML = rowsHtml;
  }

  /**
   * Opens the Life Event creation/editing modal.
   */
  function openModal(eventId = null) {
    const modal = document.getElementById('life-event-modal');
    if (!modal) return;

    const titleEl = document.getElementById('event-modal-header');
    const idInput = document.getElementById('event-input-id');
    const categoryInput = document.getElementById('event-input-category');
    const titleInput = document.getElementById('event-input-title');
    const startInput = document.getElementById('event-input-start');
    const endInput = document.getElementById('event-input-end');
    const severityInput = document.getElementById('event-input-severity');
    const notesInput = document.getElementById('event-input-notes');
    const tagsInput = document.getElementById('event-input-tags');

    const impFluid = document.getElementById('event-impact-fluid');
    const impRhr = document.getElementById('event-impact-rhr');
    const impSleep = document.getElementById('event-impact-sleep');
    const impTraining = document.getElementById('event-impact-training');
    const impAst = document.getElementById('event-impact-ast');

    if (eventId) {
      const evt = eventsCache.find(e => e.id === eventId);
      if (!evt) return;
      if (titleEl) titleEl.textContent = 'Edit Contextual Life Event';
      if (idInput) idInput.value = evt.id;
      if (categoryInput) categoryInput.value = evt.category || 'travel';
      if (titleInput) titleInput.value = evt.title || '';
      if (startInput) startInput.value = evt.start_date || '';
      if (endInput) endInput.value = evt.end_date || '';
      if (severityInput) severityInput.value = evt.severity || 'moderate';
      if (notesInput) notesInput.value = evt.notes || '';
      if (tagsInput) tagsInput.value = (evt.tags || []).join(', ');

      const imp = evt.physiological_impact || {};
      if (impFluid) impFluid.checked = !!imp.fluid_retention;
      if (impRhr) impRhr.checked = !!imp.elevated_rhr;
      if (impSleep) impSleep.checked = !!imp.sleep_disruption;
      if (impTraining) impTraining.checked = !!imp.training_hiatus;
      if (impAst) impAst.checked = !!imp.ast_alt_spike;
    } else {
      if (titleEl) titleEl.textContent = 'Add Contextual Life Event';
      if (idInput) idInput.value = '';
      if (categoryInput) categoryInput.value = 'travel';
      if (titleInput) titleInput.value = '';
      const today = new Date().toISOString().split('T')[0];
      if (startInput) startInput.value = today;
      if (endInput) endInput.value = today;
      if (severityInput) severityInput.value = 'moderate';
      if (notesInput) notesInput.value = '';
      if (tagsInput) tagsInput.value = '';

      if (impFluid) impFluid.checked = false;
      if (impRhr) impRhr.checked = false;
      if (impSleep) impSleep.checked = false;
      if (impTraining) impTraining.checked = false;
      if (impAst) impAst.checked = false;
    }

    modal.classList.remove('hidden');
    modal.classList.add('flex');
  }

  /**
   * Closes the Life Event modal.
   */
  function closeModal() {
    const modal = document.getElementById('life-event-modal');
    if (!modal) return;
    modal.classList.add('hidden');
    modal.classList.remove('flex');
  }

  /**
   * Saves the Life Event from form inputs to POST /api/events.
   */
  async function saveFromForm() {
    const idInput = document.getElementById('event-input-id');
    const categoryInput = document.getElementById('event-input-category');
    const titleInput = document.getElementById('event-input-title');
    const startInput = document.getElementById('event-input-start');
    const endInput = document.getElementById('event-input-end');
    const severityInput = document.getElementById('event-input-severity');
    const notesInput = document.getElementById('event-input-notes');
    const tagsInput = document.getElementById('event-input-tags');

    const impFluid = document.getElementById('event-impact-fluid');
    const impRhr = document.getElementById('event-impact-rhr');
    const impSleep = document.getElementById('event-impact-sleep');
    const impTraining = document.getElementById('event-impact-training');
    const impAst = document.getElementById('event-impact-ast');

    const title = (titleInput?.value || '').trim();
    const startDate = (startInput?.value || '').trim();
    if (!title || !startDate) {
      alert('Please provide both an event title and a start date.');
      return;
    }

    const category = categoryInput?.value || 'travel';
    let eventId = idInput?.value || '';
    if (!eventId) {
      const cleanTitle = title.toLowerCase().replace(/[^a-z0-9]/g, '_').substring(0, 24);
      eventId = `event_${startDate.substring(0, 4)}_${cleanTitle}`;
    }

    const rawTags = (tagsInput?.value || '').split(',').map(t => t.trim().toLowerCase()).filter(Boolean);

    const payload = {
      id: eventId,
      category: category,
      title: title,
      start_date: startDate,
      end_date: endInput?.value ? endInput.value.trim() : null,
      severity: severityInput?.value || 'moderate',
      notes: notesInput?.value ? notesInput.value.trim() : '',
      tags: rawTags,
      physiological_impact: {
        fluid_retention: !!impFluid?.checked,
        elevated_rhr: !!impRhr?.checked,
        sleep_disruption: !!impSleep?.checked,
        training_hiatus: !!impTraining?.checked,
        ast_alt_spike: !!impAst?.checked
      }
    };

    try {
      const resp = await fetch('/api/events', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (!resp.ok) {
        throw new Error(`Server returned status ${resp.status}`);
      }

      const resData = await resp.json();
      console.log('[LifeEventsManager] Event saved cleanly:', resData);

      // Update local cache
      const existingIdx = eventsCache.findIndex(e => e.id === eventId);
      if (existingIdx >= 0) {
        eventsCache[existingIdx] = payload;
      } else {
        eventsCache.push(payload);
      }

      closeModal();
      renderCuratorTable();
      if (window.ChartRenderer && typeof window.ChartRenderer.refreshActiveCharts === 'function') {
        window.ChartRenderer.refreshActiveCharts();
      }
    } catch (err) {
      console.warn('[LifeEventsManager] Failed to persist to server (offline or file://):', err);
      // Fallback local update
      const existingIdx = eventsCache.findIndex(e => e.id === eventId);
      if (existingIdx >= 0) {
        eventsCache[existingIdx] = payload;
      } else {
        eventsCache.push(payload);
      }
      closeModal();
      renderCuratorTable();
      if (window.ChartRenderer && typeof window.ChartRenderer.refreshActiveCharts === 'function') {
        window.ChartRenderer.refreshActiveCharts();
      }
    }
  }

  return {
    CATEGORIES,
    init,
    getEvents,
    getActiveEvents,
    areErasEnabled,
    toggleCategory,
    renderFilterStrip,
    renderCuratorTable,
    openModal,
    closeModal,
    saveFromForm
  };
})();

if (typeof window !== 'undefined') {
  window.LifeEventsManager = LifeEventsManager;
}

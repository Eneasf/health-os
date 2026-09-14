/**
 * Protocol Studio — Multi-Vector Clinical Protocol Inspector & Drafter (Milestone 23, ADR-037)
 * Manages:
 * 1. Protocol selection (active protocol, historical protocols, drafts)
 * 2. Multi-vector inspection: Pharmacological, Supplements, Training, Diagnostics, Redlines
 * 3. Interactive Protocol Drafter, JSON Export & Server Synchronization (POST /api/protocol)
 */

const ProtocolStudio = (() => {
  let selectedProtocolId = null;
  let activeTab = 'pharma'; // 'pharma', 'supplements', 'training', 'diagnostics', 'redlines', 'drafter'
  let draftProtocol = null;

  function escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function getProtocols() {
    const data = window.dashboardData || window.__DASHBOARD_DATA__ || window.globalDashboardData || {};
    let list = data.protocols || [];
    if (!list.length && data.active_protocol) {
      list = [data.active_protocol];
    }
    return list;
  }

  function getActiveProtocol() {
    const data = window.dashboardData || window.__DASHBOARD_DATA__ || window.globalDashboardData || {};
    return data.active_protocol || getProtocols().find(p => p.status === 'active') || getProtocols()[0] || null;
  }

  function getSelectedProtocol() {
    const protocols = getProtocols();
    if (selectedProtocolId) {
      const found = protocols.find(p => p.id === selectedProtocolId);
      if (found) return found;
    }
    const act = getActiveProtocol();
    if (act) {
      selectedProtocolId = act.id;
      return act;
    }
    return protocols[0] || null;
  }

  function init() {
    const protocols = getProtocols();
    if (!selectedProtocolId) {
      const act = getActiveProtocol();
      if (act) selectedProtocolId = act.id;
      else if (protocols.length) selectedProtocolId = protocols[0].id;
    }
    render();
  }

  function selectProtocol(protoId) {
    selectedProtocolId = protoId;
    if (activeTab === 'drafter' && draftProtocol && draftProtocol.id !== protoId) {
      activeTab = 'pharma';
    }
    render();
  }

  function switchTab(tabKey) {
    activeTab = tabKey;
    render();
  }

  function cloneToDraft() {
    const current = getSelectedProtocol();
    if (!current) return;
    draftProtocol = JSON.parse(JSON.stringify(current));
    draftProtocol.id = `protocol_${Date.now().toString().slice(-6)}_draft`;
    draftProtocol.name = `Draft: Copy of ${current.name}`;
    draftProtocol.status = 'draft';
    draftProtocol.effective_start = new Date().toISOString().split('T')[0];
    draftProtocol.effective_end = null;
    activeTab = 'drafter';
    render();
  }

  function render() {
    const container = document.getElementById('protocol-studio-container');
    if (!container) return;

    const protocols = getProtocols();
    const current = (activeTab === 'drafter' && draftProtocol) ? draftProtocol : getSelectedProtocol();

    if (!current && protocols.length === 0) {
      container.innerHTML = `
        <div class="p-6 text-center text-slate-500 dark:text-slate-400 bg-white dark:bg-[#131A26] rounded-2xl border border-slate-200 dark:border-slate-800">
          No protocol definitions loaded.
        </div>
      `;
      return;
    }

    const isCurrentActive = current.status === 'active';
    const statusColor = isCurrentActive 
      ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-300 border-emerald-300 dark:border-emerald-700' 
      : (current.status === 'draft' 
        ? 'bg-purple-50 text-purple-700 dark:bg-purple-500/20 dark:text-purple-300 border-purple-300 dark:border-purple-700'
        : 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300 border-slate-300 dark:border-slate-700');

    container.innerHTML = `
      <div class="bg-white dark:bg-[#131A26] border border-slate-200 dark:border-slate-800 rounded-2xl p-4 sm:p-5 card-glow space-y-4 shadow-sm dark:shadow-none transition-all">
        
        <!-- Studio Header & Selector Controls -->
        <div class="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 dark:border-slate-800 pb-3.5">
          <div class="flex items-center gap-3 min-w-0">
            <div class="w-8 h-8 rounded-xl bg-purple-50 dark:bg-purple-950/60 border border-purple-200 dark:border-purple-800 flex items-center justify-center text-base flex-shrink-0">
              🧬
            </div>
            <div>
              <div class="flex items-center gap-2 flex-wrap">
                <h3 class="text-sm sm:text-base font-bold text-slate-900 dark:text-white tracking-tight">
                  Protocol Studio
                </h3>
                <span class="px-2 py-0.5 text-xs font-mono font-bold uppercase rounded-md border ${statusColor}">
                  ${escapeHtml(current.status)}
                </span>
                <span class="text-xs font-mono text-slate-500 dark:text-slate-400">
                  ${escapeHtml(current.effective_start)} → ${current.effective_end ? escapeHtml(current.effective_end) : 'Open / Present'}
                </span>
              </div>
              <p class="text-xs text-slate-500 dark:text-slate-400 mt-0.5 max-w-2xl truncate">
                ${escapeHtml(current.intent_summary || 'Multi-vector clinical protocol manifest')}
              </p>
            </div>
          </div>

          <!-- Actions & Protocol Dropdown -->
          <div class="flex flex-wrap items-center gap-2 text-xs">
            <select id="protocol-studio-select" onchange="ProtocolStudio.selectProtocol(this.value)" class="bg-slate-50 dark:bg-slate-900 border border-slate-300 dark:border-slate-700 text-slate-800 dark:text-slate-200 rounded-lg px-2.5 py-1.5 font-medium text-xs focus:ring-1 focus:ring-purple-500 focus:outline-none">
              ${protocols.map(p => `
                <option value="${escapeHtml(p.id)}" ${p.id === current.id ? 'selected' : ''}>
                  ${escapeHtml(p.name)} (${p.status})
                </option>
              `).join('')}
              ${draftProtocol ? `<option value="${draftProtocol.id}" ${draftProtocol.id === current.id ? 'selected' : ''}>[Draft] ${escapeHtml(draftProtocol.name)}</option>` : ''}
            </select>

            <button onclick="ProtocolStudio.cloneToDraft()" class="px-2.5 py-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700 text-slate-700 dark:text-slate-300 font-semibold border border-slate-300 dark:border-slate-700 transition flex items-center gap-1" title="Clone into draft">
              <span>📋</span> Clone
            </button>

            <button onclick="ProtocolStudio.downloadProtocolJson()" class="px-2.5 py-1.5 rounded-lg bg-purple-50 hover:bg-purple-100 dark:bg-purple-950/60 dark:hover:bg-purple-900/60 text-purple-700 dark:text-purple-300 font-semibold border border-purple-300 dark:border-purple-700 transition flex items-center gap-1" title="Export JSON">
              <span>📥</span> JSON
            </button>
          </div>
        </div>

        <!-- Section Navigation Tabs -->
        <div class="flex items-center gap-1.5 overflow-x-auto custom-scrollbar border-b border-slate-100 dark:border-slate-800 pb-2 text-xs">
          ${[
            { id: 'pharma', label: '💊 Pharmacological Stack', count: current.compounds?.length || 0 },
            { id: 'supplements', label: '🌿 Supplements & Adjuncts', count: current.supplements?.length || 0 },
            { id: 'training', label: '🏋️ Periodization Program', count: null },
            { id: 'diagnostics', label: '🔬 Diagnostic Panel', count: current.diagnostic_panel?.mandatory_markers?.length || 0 },
            { id: 'redlines', label: '🛡️ Safety Redlines', count: null },
            { id: 'drafter', label: '✏️ Protocol Drafter', count: null }
          ].map(t => {
            const isActive = activeTab === t.id;
            return `
              <button onclick="ProtocolStudio.switchTab('${t.id}')" class="px-3 py-1.5 rounded-lg font-semibold whitespace-nowrap transition flex items-center gap-1.5 ${
                isActive 
                  ? 'bg-purple-100 dark:bg-purple-500/20 text-purple-800 dark:text-purple-300 border border-purple-300 dark:border-purple-700' 
                  : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800/60'
              }">
                <span>${t.label}</span>
                ${t.count !== null ? `<span class="px-1.5 py-0.2 rounded-full text-[10px] font-mono font-bold ${isActive ? 'bg-purple-200 dark:bg-purple-900/80 text-purple-900 dark:text-purple-200' : 'bg-slate-200 dark:bg-slate-800 text-slate-700 dark:text-slate-400'}">${t.count}</span>` : ''}
              </button>
            `;
          }).join('')}
        </div>

        <!-- Section Content Renderers -->
        <div class="pt-1">
          ${renderSectionContent(current)}
        </div>

      </div>
    `;
  }

  function renderSectionContent(current) {
    switch (activeTab) {
      case 'pharma':
        return renderPharmaSection(current);
      case 'supplements':
        return renderSupplementsSection(current);
      case 'training':
        return renderTrainingSection(current);
      case 'diagnostics':
        return renderDiagnosticsSection(current);
      case 'redlines':
        return renderRedlinesSection(current);
      case 'drafter':
        return renderDrafterSection(current);
      default:
        return renderPharmaSection(current);
    }
  }

  function renderPharmaSection(current) {
    const compounds = current.compounds || [];
    if (compounds.length === 0) {
      return `
        <div class="p-8 text-center bg-slate-50/50 dark:bg-slate-900/30 rounded-xl">
          <div class="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800 text-xs font-bold mb-2">
            <span>🌿</span> Natural Baseline
          </div>
          <p class="text-xs text-slate-600 dark:text-slate-300 max-w-md mx-auto">
            No pharmacological interventions or specialized compound stacks active in this protocol epoch.
          </p>
        </div>
      `;
    }

    return `
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
        ${compounds.map(c => `
          <div class="p-3.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50/40 dark:bg-slate-900/40 space-y-2.5">
            <div class="flex items-start justify-between gap-2">
              <div>
                <h4 class="text-xs font-bold text-slate-900 dark:text-white">${escapeHtml(c.name)}</h4>
                <span class="text-[11px] font-mono text-purple-600 dark:text-purple-400 font-semibold">${escapeHtml(c.dose || '')}</span>
              </div>
              <span class="px-2 py-0.5 text-[10px] font-mono font-bold uppercase rounded bg-purple-100 dark:bg-purple-950/60 text-purple-700 dark:text-purple-300 border border-purple-200 dark:border-purple-800">
                ${escapeHtml(c.frequency_hours === 72 ? 'q72h' : (c.frequency_hours === 24 ? 'Daily' : (c.cadence || 'Custom')))}
              </span>
            </div>

            <div class="grid grid-cols-2 gap-2 text-[11px] font-mono pt-1 border-t border-slate-200/60 dark:border-slate-800/60 text-slate-600 dark:text-slate-400">
              <div><span class="text-slate-400">Route:</span> <strong class="text-slate-700 dark:text-slate-300">${escapeHtml(c.route || 'Oral')}</strong></div>
              <div><span class="text-slate-400">Timing:</span> <strong class="text-slate-700 dark:text-slate-300">${escapeHtml(c.timing || 'AM')}</strong></div>
              <div><span class="text-slate-400">Half-Life:</span> <strong class="text-slate-700 dark:text-slate-300">${c.half_life_hours ? c.half_life_hours + 'h' : '—'}</strong></div>
              <div><span class="text-slate-400">Category:</span> <strong class="text-slate-700 dark:text-slate-300">${escapeHtml(c.category || 'Pharma')}</strong></div>
            </div>

            ${c.saturation_requirement ? `
              <div class="p-2 rounded-lg bg-cyan-50/70 dark:bg-cyan-950/30 border border-cyan-200 dark:border-cyan-800 text-[11px] text-cyan-800 dark:text-cyan-300">
                <span class="font-bold">⏱️ Protocol Rule:</span> ${escapeHtml(c.saturation_requirement)}
              </div>
            ` : ''}

            ${c.clinical_target ? `
              <div class="text-[11px] text-slate-500 dark:text-slate-400">
                <span class="font-semibold text-slate-700 dark:text-slate-300">Target:</span> ${escapeHtml(c.clinical_target)}
              </div>
            ` : ''}
          </div>
        `).join('')}
      </div>
    `;
  }

  function renderSupplementsSection(current) {
    const supplements = current.supplements || [];
    if (supplements.length === 0) {
      return `
        <div class="p-6 text-center text-slate-400 dark:text-slate-500 text-xs">
          No dedicated supplement stacks recorded for this protocol.
        </div>
      `;
    }

    return `
      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
        ${supplements.map(s => `
          <div class="p-3.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50/40 dark:bg-slate-900/40 space-y-2">
            <div class="flex items-start justify-between gap-2">
              <div>
                <h4 class="text-xs font-bold text-slate-900 dark:text-white">${escapeHtml(s.name)}</h4>
                <span class="text-[11px] font-mono text-emerald-600 dark:text-emerald-400 font-semibold">${escapeHtml(s.dose || '')}</span>
              </div>
              <span class="px-2 py-0.5 text-[10px] font-mono font-bold rounded bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800">
                ${escapeHtml(s.timing || 'Daily')}
              </span>
            </div>
            <p class="text-[11px] text-slate-600 dark:text-slate-400">
              <span class="font-semibold text-slate-700 dark:text-slate-300">Indication:</span> ${escapeHtml(s.indication || 'Nutritional adjunct')}
            </p>
          </div>
        `).join('')}
      </div>
    `;
  }

  function renderTrainingSection(current) {
    const tp = current.training_program || {};
    return `
      <div class="grid grid-cols-1 md:grid-cols-2 gap-3.5">
        <div class="p-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50/40 dark:bg-slate-900/40 space-y-2.5">
          <div class="flex items-center justify-between">
            <span class="text-xs font-bold text-slate-900 dark:text-white">Training Engine & Mode</span>
            <span class="px-2 py-0.5 text-[10px] font-bold rounded bg-purple-50 dark:bg-purple-950/40 text-purple-700 dark:text-purple-300 border border-purple-200 dark:border-purple-800">
              ${escapeHtml(tp.target_cadence ? `${tp.target_cadence}x / week` : 'Cadence —')}
            </span>
          </div>
          <div class="text-sm font-extrabold text-purple-700 dark:text-purple-300 font-mono">
            ${escapeHtml(tp.mode || 'Standard Mode')}
          </div>
          <p class="text-xs text-slate-600 dark:text-slate-300">
            ${escapeHtml(tp.split_description || 'Periodized resistance training')}
          </p>
        </div>

        <div class="p-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50/40 dark:bg-slate-900/40 space-y-2.5">
          <div class="flex items-center justify-between">
            <span class="text-xs font-bold text-slate-900 dark:text-white">Deload & Safety Constraints</span>
            ${tp.deload_week ? `<span class="px-2 py-0.5 text-[10px] font-bold rounded bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-300 border border-amber-200 dark:border-amber-800">Week ${tp.deload_week} Deload</span>` : ''}
          </div>
          <p class="text-xs text-amber-700 dark:text-amber-400 font-medium">
            ${escapeHtml(tp.deload_protocol || 'Deload scheduled according to macro-cycle nodes.')}
          </p>
          ${tp.safety_restrictions && tp.safety_restrictions.length > 0 ? `
            <div class="pt-1.5 border-t border-slate-200/60 dark:border-slate-800/60 space-y-1">
              ${tp.safety_restrictions.map(r => `
                <div class="flex items-center gap-1.5 text-[11px] text-rose-600 dark:text-rose-400 font-semibold">
                  <span>🚫</span> <span>${escapeHtml(r)}</span>
                </div>
              `).join('')}
            </div>
          ` : ''}
        </div>
      </div>
    `;
  }

  function renderDiagnosticsSection(current) {
    const diag = current.diagnostic_panel || {};
    const markers = diag.mandatory_markers || [];
    const methods = diag.methodology_requirements || [];

    return `
      <div class="space-y-3">
        <div class="p-4 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50/40 dark:bg-slate-900/40 space-y-3">
          <div class="flex flex-wrap items-center justify-between gap-2">
            <div>
              <span class="text-xs font-bold text-slate-900 dark:text-white block">Mandatory Biomarker Surveillance Panel</span>
              <span class="text-[11px] text-slate-500 dark:text-slate-400">Required requisitions for steady-state safety monitoring</span>
            </div>
            ${diag.next_milestone_target_date ? `
              <span class="px-2.5 py-1 text-xs font-mono font-bold rounded bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-300 border border-amber-200 dark:border-amber-800">
                Target: ${escapeHtml(diag.next_milestone_target_date)} (Week ${diag.next_milestone_week || '?'})
              </span>
            ` : ''}
          </div>

          <div class="flex flex-wrap gap-1.5 pt-1">
            ${markers.map(m => `
              <span class="px-2.5 py-1 rounded-lg bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-800 dark:text-slate-200 text-xs font-semibold">
                ${escapeHtml(m)}
              </span>
            `).join('')}
          </div>
        </div>

        ${methods.length > 0 ? `
          <div class="p-3.5 rounded-xl border border-cyan-200 dark:border-cyan-800/80 bg-cyan-50/40 dark:bg-cyan-950/20 space-y-1.5">
            <span class="text-xs font-bold text-cyan-900 dark:text-cyan-300 block">Assay & Methodology Directives</span>
            <ul class="space-y-1 text-xs text-cyan-800 dark:text-cyan-400">
              ${methods.map(met => `
                <li class="flex items-center gap-1.5">
                  <span>🔬</span> <span>${escapeHtml(met)}</span>
                </li>
              `).join('')}
            </ul>
          </div>
        ` : ''}
      </div>
    `;
  }

  function renderRedlinesSection(current) {
    const red = current.safety_redlines || {};
    const syms = red.e2_symptom_checklist || [];

    return `
      <div class="grid grid-cols-1 md:grid-cols-3 gap-3">
        <div class="p-3.5 rounded-xl border border-rose-200 dark:border-rose-900/60 bg-rose-50/40 dark:bg-rose-950/20 space-y-1.5">
          <div class="flex items-center justify-between text-xs font-bold text-rose-800 dark:text-rose-300">
            <span>Haematocrit (HCT) Redline</span>
            <span class="font-mono text-sm">${red.hct_danger_threshold_pct || 54.0}%</span>
          </div>
          <p class="text-[11px] text-slate-600 dark:text-slate-400">
            Hard clinical cutoff. Phlebotomy/therapeutic venesection triggered above 54.0% to prevent hyperviscosity.
          </p>
        </div>

        <div class="p-3.5 rounded-xl border border-amber-200 dark:border-amber-900/60 bg-amber-50/40 dark:bg-amber-950/20 space-y-1.5">
          <div class="flex items-center justify-between text-xs font-bold text-amber-800 dark:text-amber-300">
            <span>48h Scale Jump Trigger</span>
            <span class="font-mono text-sm">+${red.weight_delta_48h_threshold_kg || 1.50} kg</span>
          </div>
          <p class="text-[11px] text-slate-600 dark:text-slate-400">
            Fluid retention threshold prompting symptom audit before protocol titration.
          </p>
        </div>

        <div class="p-3.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-slate-50/40 dark:bg-slate-900/40 space-y-1.5">
          <span class="text-xs font-bold text-slate-900 dark:text-white block">Fluid Symptom Audit Checklist</span>
          <div class="flex flex-wrap gap-1 text-[10px] font-mono">
            ${syms.map(s => `
              <span class="px-2 py-0.5 rounded bg-slate-200 dark:bg-slate-800 text-slate-700 dark:text-slate-300">
                ${escapeHtml(s.replace(/_/g, ' '))}
              </span>
            `).join('')}
          </div>
        </div>
      </div>
    `;
  }

  function renderDrafterSection(current) {
    const p = draftProtocol || current;
    return `
      <div class="p-4 rounded-xl border border-purple-200 dark:border-purple-800/80 bg-purple-50/20 dark:bg-purple-950/10 space-y-4">
        <div class="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200/60 dark:border-slate-800/60 pb-2.5">
          <div>
            <h4 class="text-xs font-bold text-slate-900 dark:text-white">Protocol Drafter & Manifest Builder</h4>
            <p class="text-[11px] text-slate-500 dark:text-slate-400">Edit JSON specification below and commit directly to event-sourced storage (ADR-028)</p>
          </div>
          <div class="flex items-center gap-2">
            <button onclick="ProtocolStudio.saveProtocolDraft()" class="px-3 py-1.5 text-xs font-bold rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white shadow-sm transition">
              💾 Save to Server
            </button>
          </div>
        </div>

        <div class="space-y-3">
          <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
            <div>
              <label class="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Protocol ID</label>
              <input type="text" id="drafter-id" value="${escapeHtml(p.id)}" class="w-full bg-white dark:bg-slate-900 border border-slate-300 dark:border-slate-700 rounded-lg p-2 font-mono text-xs">
            </div>
            <div>
              <label class="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Protocol Name</label>
              <input type="text" id="drafter-name" value="${escapeHtml(p.name)}" class="w-full bg-white dark:bg-slate-900 border border-slate-300 dark:border-slate-700 rounded-lg p-2 text-xs">
            </div>
            <div>
              <label class="block font-semibold text-slate-700 dark:text-slate-300 mb-1">Status</label>
              <select id="drafter-status" class="w-full bg-white dark:bg-slate-900 border border-slate-300 dark:border-slate-700 rounded-lg p-2 text-xs">
                <option value="draft" ${p.status === 'draft' ? 'selected' : ''}>Draft</option>
                <option value="active" ${p.status === 'active' ? 'selected' : ''}>Active</option>
                <option value="completed" ${p.status === 'completed' ? 'selected' : ''}>Completed</option>
              </select>
            </div>
          </div>

          <div>
            <label class="block font-semibold text-slate-700 dark:text-slate-300 mb-1 text-xs">Intent Summary</label>
            <input type="text" id="drafter-intent" value="${escapeHtml(p.intent_summary || '')}" class="w-full bg-white dark:bg-slate-900 border border-slate-300 dark:border-slate-700 rounded-lg p-2 text-xs">
          </div>

          <div>
            <label class="block font-semibold text-slate-700 dark:text-slate-300 mb-1 text-xs">Raw Protocol JSON Specification</label>
            <textarea id="drafter-json" rows="12" class="w-full bg-slate-900 text-cyan-300 border border-slate-700 rounded-xl p-3 font-mono text-xs focus:ring-1 focus:ring-purple-500 focus:outline-none custom-scrollbar">${escapeHtml(JSON.stringify(p, null, 2))}</textarea>
          </div>
        </div>
      </div>
    `;
  }

  async function saveProtocolDraft() {
    const jsonEl = document.getElementById('drafter-json');
    if (!jsonEl) return;

    let payload = null;
    try {
      payload = JSON.parse(jsonEl.value);
    } catch (e) {
      alert(`Invalid JSON format: ${e.message}`);
      return;
    }

    const idInput = document.getElementById('drafter-id')?.value;
    const nameInput = document.getElementById('drafter-name')?.value;
    const statusInput = document.getElementById('drafter-status')?.value;
    const intentInput = document.getElementById('drafter-intent')?.value;

    if (idInput) payload.id = idInput;
    if (nameInput) payload.name = nameInput;
    if (statusInput) payload.status = statusInput;
    if (intentInput) payload.intent_summary = intentInput;

    try {
      const res = await fetch('/api/protocol', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      if (res.ok) {
        const resData = await res.json();
        alert(`✅ Protocol "${payload.name}" saved and synchronized successfully.`);
        if (window.App && typeof window.App.loadData === 'function') {
          window.App.loadData(true);
        }
      } else {
        alert(`Failed to save protocol: ${res.statusText}`);
      }
    } catch (err) {
      console.error('Error saving protocol draft:', err);
      alert(`Error saving protocol draft: ${err.message}`);
    }
  }

  function downloadProtocolJson() {
    const current = getSelectedProtocol();
    if (!current) return;
    const jsonStr = JSON.stringify(current, null, 2);
    const blob = new Blob([jsonStr], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${current.id}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }

  return {
    init,
    render,
    selectProtocol,
    switchTab,
    cloneToDraft,
    saveProtocolDraft,
    downloadProtocolJson,
    getSelectedProtocol,
    getProtocols
  };
})();

window.ProtocolStudio = ProtocolStudio;

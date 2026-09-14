/**
 * Weekly Protocol Cadence Matrix & Dose Logging Engine (ADR-001 & ADR-002)
 * Manages:
 * 1. Monday-start 7-day calendar window generation (Mon -> Sun)
 * 2. Infinite multi-week pagination (Prev, Next, Today, Date Picker)
 * 3. Dynamic Selected-Day column binding and HUD synchronization
 * 4. Interactive dose logging modal with divergence vocabulary and confidence ladders
 */

const MatrixManager = (() => {
  function formatDateIso(d) {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  function parseIsoDate(dateIsoStr) {
    if (!dateIsoStr) return new Date();
    const parts = dateIsoStr.split('-');
    return new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
  }

  function getTodayIso() {
    return formatDateIso(new Date());
  }

  let anchorEndDate = getTodayIso();
  let selectedDate = getTodayIso();

  function getProtocolForWeek(weekDays) {
    const data = window.dashboardData || window.__DASHBOARD_DATA__ || window.globalDashboardData || {};
    const protocols = data.protocols || [];
    if (!protocols.length && data.active_protocol) {
      protocols.push(data.active_protocol);
    }
    const mondayIso = weekDays[0];
    const sundayIso = weekDays[6];

    for (let i = protocols.length - 1; i >= 0; i--) {
      const p = protocols[i];
      if (p.effective_start <= sundayIso && (!p.effective_end || p.effective_end >= mondayIso)) {
        return p;
      }
    }
    return data.active_protocol || null;
  }

  function getCompoundsForWeek(weekDays) {
    const proto = getProtocolForWeek(weekDays);
    if (!proto) {
      return [
        { id: "creatine", name: "Creatine Monohydrate (5g)", freq: "Daily" },
        { id: "omega3", name: "Omega-3 EPA/DHA (2000mg)", freq: "Daily" },
        { id: "vit_d3", name: "Vitamin D3 + K2 (5000 IU)", freq: "Daily" },
        { id: "magnesium", name: "Magnesium Bisglycinate (400mg)", freq: "Daily" },
        { id: "protein", name: "Whey Isolate Shake (40g)", freq: "Daily" }
      ];
    }
    if (!proto.compounds || proto.compounds.length === 0) {
      return [];
    }
    return proto.compounds.map(c => ({
      id: c.id,
      name: c.name,
      freq: c.frequency_hours === 72 ? 'q72h' : (c.frequency_hours === 24 ? 'Daily' : (c.cadence || 'as_needed')),
      dose: c.dose,
      route: c.route,
      saturation: c.saturation_requirement,
      target: c.clinical_target
    }));
  }

  // Compute array of 7 ISO dates for the Monday-start week containing dateIsoStr
  function getWeekDays(dateIsoStr) {
    const d = parseIsoDate(dateIsoStr);
    const day = d.getDay(); // 0 is Sun, 1 is Mon, ..., 6 is Sat
    const diffToMonday = (day === 0 ? -6 : 1) - day;
    const monday = new Date(d);
    monday.setDate(d.getDate() + diffToMonday);

    const days = [];
    for (let i = 0; i < 7; i++) {
      const cur = new Date(monday);
      cur.setDate(monday.getDate() + i);
      days.push(formatDateIso(cur));
    }
    return days;
  }

  function getSelectedDate() {
    return selectedDate;
  }

  function getAnchorEndDate() {
    return anchorEndDate;
  }

  function selectDate(dateIso) {
    selectedDate = dateIso;
    const d = parseIsoDate(dateIso);
    const todayIso = getTodayIso();
    const isToday = dateIso === todayIso;
    const dayName = d.toLocaleDateString('en-US', { weekday: 'long', month: 'short', day: 'numeric', year: 'numeric' });
    
    const labelEl = document.getElementById('selected-day-label');
    if (labelEl) {
      labelEl.innerText = `${dayName} ${isToday ? '(Today)' : ''}`;
    }

    renderMatrix();

    if (window.App && typeof window.App.syncHudToSelectedDate === 'function') {
      window.App.syncHudToSelectedDate(selectedDate);
    }
  }

  function navigateWeek(deltaWeeks) {
    const cur = parseIsoDate(anchorEndDate);
    cur.setDate(cur.getDate() + (deltaWeeks * 7));
    const nextDays = getWeekDays(formatDateIso(cur));
    const todayIso = getTodayIso();
    if (deltaWeeks > 0 && nextDays[0] > todayIso) {
      anchorEndDate = todayIso;
    } else {
      anchorEndDate = formatDateIso(cur);
    }
    selectDate(anchorEndDate);
  }

  function jumpToToday() {
    const today = getTodayIso();
    anchorEndDate = today;
    selectDate(today);
  }

  function jumpToDate(dateIso) {
    if (!dateIso) return;
    anchorEndDate = dateIso;
    selectDate(dateIso);
  }

  function renderMatrix() {
    const currentDays = getWeekDays(anchorEndDate);
    const startStr = currentDays[0];
    const endStr = currentDays[6];
    const startDt = parseIsoDate(startStr);
    const endDt = parseIsoDate(endStr);
    const startLabel = startDt.toLocaleDateString('en-US', { weekday: 'short', day: 'numeric', month: 'short' });
    const endLabel = endDt.toLocaleDateString('en-US', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });
    
    const rangeLabelEl = document.getElementById('matrix-week-range-label');
    const datePickerEl = document.getElementById('matrix-date-picker');
    const nextWeekBtn = document.getElementById('btn-next-week');
    const todayIso = getTodayIso();
    const isAtCurrentWeek = endStr >= todayIso;

    if (rangeLabelEl) rangeLabelEl.innerText = `${startLabel} → ${endLabel}`;
    if (datePickerEl) {
      datePickerEl.value = selectedDate;
      datePickerEl.max = todayIso;
    }
    if (nextWeekBtn) {
      if (isAtCurrentWeek) {
        nextWeekBtn.disabled = true;
        nextWeekBtn.classList.add('opacity-40', 'cursor-not-allowed');
      } else {
        nextWeekBtn.disabled = false;
        nextWeekBtn.classList.remove('opacity-40', 'cursor-not-allowed');
      }
    }

    const hudAdherence = window.dashboardData?.hud?.adherence_matrix
      || window.__DASHBOARD_DATA__?.hud?.adherence_matrix
      || window.globalDashboardData?.hud?.adherence_matrix
      || window.dashboardData?.protocol_matrix
      || window.__DASHBOARD_DATA__?.protocol_matrix;
    const matrixRows = hudAdherence?.rows || [];

    // 1. Render Header Days
    const headTr = document.getElementById('matrix-head-tr');
    if (headTr) {
      headTr.innerHTML = `
        <th class="p-2.5 rounded-l-lg w-48 sticky left-0 z-20 bg-slate-100 dark:bg-slate-900 shadow-[2px_0_6px_-2px_rgba(0,0,0,0.08)] text-slate-700 dark:text-slate-300">Compound / Stack</th>
        <th class="p-2.5 w-24">Cadence</th>
      ` + currentDays.map(dIso => {
        const dt = parseIsoDate(dIso);
        const weekdayName = dt.toLocaleDateString('en-US', { weekday: 'short' });
        const dayNum = dt.getDate();
        const monthShort = dt.toLocaleDateString('en-US', { month: 'short' });
        const isSelected = dIso === selectedDate;
        return `
          <th onclick="MatrixManager.selectDate('${dIso}')" class="p-2 text-center cursor-pointer min-w-[76px] transition ${isSelected ? 'bg-cyan-100 dark:bg-cyan-500/20 text-cyan-800 dark:text-cyan-300 font-bold border-b-2 border-cyan-500' : 'hover:bg-slate-200/60 dark:hover:bg-slate-800/60'}">
            <div class="text-[10px] uppercase font-bold tracking-wider opacity-75">${weekdayName}</div>
            <div class="text-xs font-mono font-bold">${dayNum} ${monthShort}</div>
          </th>
        `;
      }).join('');
    }

    // 2. Render Rows
    const tbody = document.getElementById('matrix-tbody');
    const activeWeekProto = getProtocolForWeek(currentDays);
    const activeCompounds = getCompoundsForWeek(currentDays);
    const summaryEl = document.getElementById('matrix-weekly-adherence-summary');

    if (tbody) {
      if (activeCompounds.length === 0) {
        const protoName = activeWeekProto ? activeWeekProto.name : 'Natural Baseline';
        const protoIntent = activeWeekProto ? activeWeekProto.intent_summary : 'No pharmacological protocol active during this calendar epoch.';
        tbody.innerHTML = `
          <tr>
            <td colspan="9" class="p-8 text-center bg-slate-50/50 dark:bg-slate-900/30 rounded-xl">
              <div class="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800 text-xs font-bold mb-2">
                <span>🌿</span> ${protoName}
              </div>
              <p class="text-xs text-slate-600 dark:text-slate-300 max-w-xl mx-auto font-sans">${protoIntent}</p>
              <div class="mt-3 text-[11px] text-slate-400 dark:text-slate-500 font-mono">Natural Epoch • No prescription compounds administered</div>
            </td>
          </tr>
        `;
        if (summaryEl) {
          summaryEl.innerText = 'Natural Baseline (0 scheduled doses)';
        }
      } else {
        tbody.innerHTML = activeCompounds.map(comp => {
          const row = matrixRows.find(r => r.compound_id === comp.id);
          return `
          <tr class="hover:bg-slate-50 dark:hover:bg-slate-900/40 transition">
            <td class="p-2.5 font-semibold text-slate-900 dark:text-white sticky left-0 z-10 bg-white dark:bg-[#131A26] shadow-[2px_0_6px_-2px_rgba(0,0,0,0.08)]">${comp.name}</td>
            <td class="p-2.5 text-slate-500 dark:text-slate-400 font-sans">${comp.freq}</td>
            ${currentDays.map(dIso => {
              const dayData = row && row.days ? row.days.find(d => d.date === dIso) : null;
              const status = (dayData && dayData.status) ? dayData.status : 'unlogged';

              let badge = `<span class="text-slate-400 dark:text-slate-600">—</span>`;
              if (status === 'done') {
                badge = `<span class="px-2.5 py-1 rounded bg-emerald-100 dark:bg-emerald-500/20 text-emerald-800 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-500/30 text-xs font-semibold inline-block">Done</span>`;
              } else if (status === 'due') {
                badge = `<span class="px-2.5 py-1 rounded bg-blue-100 dark:bg-blue-500/20 text-blue-800 dark:text-blue-300 border border-blue-200 dark:border-blue-500/30 text-xs font-semibold inline-block animate-pulse">Due</span>`;
              } else if (status === 'skipped') {
                badge = `<span class="px-2.5 py-1 rounded bg-amber-100 dark:bg-amber-500/20 text-amber-800 dark:text-amber-300 border border-amber-200 dark:border-amber-500/30 text-xs font-semibold inline-block">Skipped</span>`;
              } else if (status === 'late') {
                badge = `<span class="px-2.5 py-1 rounded bg-orange-100 dark:bg-orange-500/20 text-orange-800 dark:text-orange-300 border border-orange-200 dark:border-orange-500/30 text-xs font-semibold inline-block">Late</span>`;
              } else if (status === 'missed') {
                badge = `<span class="px-2.5 py-1 rounded bg-rose-100 dark:bg-rose-500/20 text-rose-800 dark:text-rose-300 border border-rose-200 dark:border-rose-500/30 text-xs font-semibold inline-block">Missed</span>`;
              } else if (status === 'unlogged') {
                const todayIso = getTodayIso();
                if (dIso > todayIso) {
                  badge = `<span class="text-slate-300 dark:text-slate-700 text-xs font-mono select-none">·</span>`;
                } else if (dIso === todayIso) {
                  badge = `<span class="px-2.5 py-0.5 rounded-md bg-purple-50 hover:bg-purple-100 dark:bg-purple-950/60 dark:hover:bg-purple-900/60 text-purple-700 dark:text-purple-300 border border-purple-300 dark:border-purple-700 text-xs font-bold inline-flex items-center gap-1 shadow-xs transition"><span class="w-1.5 h-1.5 rounded-full bg-purple-500"></span>Log</span>`;
                } else {
                  badge = `<span class="text-slate-400 dark:text-slate-500 text-xs font-mono select-none" title="Unlogged historical dose">—</span>`;
                }
              }

              const isSelected = dIso === selectedDate;
              return `
                <td class="p-2 text-center cursor-pointer hover:bg-slate-100 dark:hover:bg-slate-800/80 rounded transition ${isSelected ? 'bg-cyan-50 dark:bg-cyan-500/10' : ''}"
                    onclick="MatrixManager.openDoseModal('${comp.id}', '${comp.name}', '', '${dIso}')"
                    title="Click to inspect/log for ${dIso}">
                  ${badge}
                </td>
              `;
            }).join('')}
          </tr>
        `;
        }).join('');

        // Update weekly adherence summary text
        if (summaryEl) {
          let doneCount = 0;
          let totalDoses = 0;
          activeCompounds.forEach(comp => {
            const row = matrixRows.find(r => r.compound_id === comp.id);
            currentDays.forEach(dIso => {
              totalDoses++;
              const d = row && row.days ? row.days.find(x => x.date === dIso) : null;
              if (d && d.status === 'done') doneCount++;
            });
          });
          summaryEl.innerText = `${doneCount} of ${totalDoses} doses logged this week`;
        }
      }
    }

    // On mobile screens, ensure today/selected is visible in the scroll container
    scrollToTodayOnMobile();
  }

  function scrollToTodayOnMobile() {
    if (window.innerWidth >= 768) return;
    const tableContainer = document.querySelector('#widget-adherence-matrix .overflow-x-auto');
    const selectedTh = document.querySelector('#matrix-head-tr th.border-b-2');
    if (tableContainer && selectedTh) {
      const stickyWidth = 192; // w-48
      const cellLeft = selectedTh.offsetLeft;
      if (cellLeft > stickyWidth) {
        tableContainer.scrollTo({
          left: Math.max(0, cellLeft - stickyWidth - 12),
          behavior: 'smooth'
        });
      }
    }
  }

  // =========================================================================
  // Dose Logging Modal Management
  // =========================================================================

  function openDoseModal(compoundId, compoundName, defaultDose, customDate = null) {
    const idEl = document.getElementById('modal-compound-id');
    const titleEl = document.getElementById('modal-compound-title');
    const dtEl = document.getElementById('modal-datetime');
    const confEl = document.getElementById('modal-confidence');
    const modalEl = document.getElementById('dose-modal');

    if (idEl) idEl.value = compoundId;
    if (titleEl) titleEl.innerText = `Log ${compoundName}`;
    
    const targetDate = customDate || selectedDate;
    const now = new Date();
    const todayIso = getTodayIso();
    
    if (dtEl) {
      if (targetDate === todayIso) {
        const localIso = new Date(now.getTime() - (now.getTimezoneOffset() * 60000)).toISOString().slice(0, 16);
        dtEl.value = localIso;
        if (confEl) confEl.value = 'exact';
      } else {
        dtEl.value = `${targetDate}T08:00`;
        if (confEl) confEl.value = 'recalled';
      }
    }

    if (modalEl) modalEl.classList.remove('hidden');
  }

  function closeDoseModal() {
    const modalEl = document.getElementById('dose-modal');
    if (modalEl) modalEl.classList.add('hidden');
  }

  async function saveDoseModal() {
    const compoundId = document.getElementById('modal-compound-id')?.value;
    const datetime = document.getElementById('modal-datetime')?.value;
    const divergence = document.getElementById('modal-divergence')?.value || 'adherent';
    const precision = document.getElementById('modal-precision')?.value || 'exact';
    const confidence = document.getElementById('modal-confidence')?.value || 'exact';
    const notes = document.getElementById('modal-notes')?.value || '';
    
    if (!compoundId || !datetime) {
      closeDoseModal();
      return;
    }

    const dateStr = datetime.split('T')[0];

    try {
      const response = await fetch('/api/log_dose', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          compound_id: compoundId,
          datetime: datetime,
          divergence: divergence,
          precision: precision,
          confidence: confidence,
          notes: notes
        })
      });

      if (response.ok) {
        // Soft refresh: update in-memory state and re-render without full page reload
        const matrixRows = window.__DASHBOARD_DATA__?.hud?.adherence_matrix?.rows
          || window.globalDashboardData?.hud?.adherence_matrix?.rows;
        if (matrixRows) {
          const row = matrixRows.find(r => r.compound_id === compoundId);
          if (row && row.days) {
            let dayObj = row.days.find(d => d.date === dateStr);
            if (!dayObj) {
              dayObj = { date: dateStr, status: 'done', divergence: divergence };
              row.days.push(dayObj);
            } else {
              dayObj.status = (divergence === 'deliberate_skip') ? 'skipped' : (divergence === 'missed' ? 'missed' : 'done');
              dayObj.divergence = divergence;
            }
          }
        }

        closeDoseModal();
        renderMatrix();
        if (window.App && typeof window.App.syncHudToSelectedDate === 'function') {
          window.App.syncHudToSelectedDate(selectedDate);
        }
      } else {
        console.error('Failed to log dose:', response.statusText);
      }
    } catch (err) {
      console.error('Error logging dose:', err);
    }
  }

  // =========================================================================
  // FAST PROTOCOL ADMINISTRATION MODAL (ADR-026: Affirmative Attestation)
  // =========================================================================

  function openFastDoseModal() {
    const modalEl = document.getElementById('fast-dose-modal');
    if (!modalEl) return;

    const data = window.__DASHBOARD_DATA__ || window.globalDashboardData || {};
    const timers = data.hud?.timers || {};
    const now = new Date();
    const todayIso = getTodayIso();

    const timeLabel = document.getElementById('fast-dose-timestamp-label');
    if (timeLabel) {
      timeLabel.innerText = `Scheduled Regimen for Today (${now.toLocaleDateString([], { weekday: 'short', day: 'numeric', month: 'short' })}) • Affirmative Attestation`;
    }

    const activeList = document.getElementById('fast-dose-active-list');
    const offList = document.getElementById('fast-dose-offcadence-list');

    const cadenceHours = timers.hours_remaining ?? timers.cadence_hours_remaining;
    const cadenceDue = (cadenceHours === null || cadenceHours === undefined || cadenceHours <= 0);

    const intervalDays = timers.interval_days;
    const intervalDue = (intervalDays === null || intervalDays === undefined || intervalDays >= 3);

    const exSessions = data.exercise?.recent_sessions || [];
    const hadWorkoutToday = exSessions.some(s => s.date === todayIso);

    let items = [];
    if (data.active_protocol && Array.isArray(data.active_protocol.compounds) && data.active_protocol.compounds.length > 0) {
      items = data.active_protocol.compounds.map(c => {
        let isAct = false;
        let desc = c.saturation_requirement || c.clinical_target || `${c.dose || ''} ${c.route || ''}`.trim();
        if (c.frequency_hours === 24 || c.cadence === 'daily') {
          isAct = true;
          desc = desc ? `Daily • ${desc}` : 'Daily Scheduled Dose';
        } else if (c.frequency_hours === 72 || c.cadence === 'q72h') {
          isAct = cadenceDue;
          desc = cadenceDue ? 'Cadence Window • Due today' : `Cadence Window • ${cadenceHours}h remaining`;
        } else if (c.cadence === 'lifting_days') {
          isAct = hadWorkoutToday;
          desc = hadWorkoutToday ? 'Lifting Day Stack • Workout logged today' : 'Pre-workout stack (Check if lifting today)';
        } else {
          isAct = true;
        }
        return {
          id: c.id,
          name: c.name,
          desc: desc,
          active: isAct,
          checked: isAct
        };
      });
    } else {
      items = [
        {
          id: 'creatine',
          name: 'Creatine Monohydrate (5g)',
          desc: 'Daily Cellular Energy & Muscular Saturation',
          active: true,
          checked: true
        },
        {
          id: 'omega3',
          name: 'Omega-3 EPA/DHA (2000mg)',
          desc: 'Daily Cardiovascular & Autonomic Support',
          active: true,
          checked: true
        },
        {
          id: 'vit_d3',
          name: 'Vitamin D3 + K2 (5000 IU)',
          desc: 'Daily Bone Density & Immune Support',
          active: true,
          checked: true
        },
        {
          id: 'magnesium',
          name: 'Magnesium Bisglycinate (400mg)',
          desc: 'Daily Neuromuscular & Sleep Architecture',
          active: true,
          checked: true
        },
        {
          id: 'protein',
          name: 'Whey Isolate (40g)',
          desc: 'Daily Muscle Protein Synthesis',
          active: true,
          checked: true
        }
      ];
    }

    if (activeList) {
      const activeItems = items.filter(i => i.active);
      activeList.innerHTML = activeItems.map(item => `
        <label class="flex items-start gap-3 p-2.5 rounded-xl border border-slate-200 dark:border-slate-800 hover:bg-slate-50 dark:hover:bg-slate-900/60 transition cursor-pointer">
          <input type="checkbox" id="fast-check-${item.id}" value="${item.id}" ${item.checked ? 'checked' : ''} class="mt-0.5 w-4 h-4 rounded text-emerald-600 focus:ring-0 border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900">
          <div class="flex-1 min-w-0">
            <span class="text-xs font-bold text-slate-900 dark:text-white block">${item.name}</span>
            <span class="text-[11px] text-slate-500 dark:text-slate-400">${item.desc}</span>
          </div>
        </label>
      `).join('');
    }

    if (offList) {
      const offItems = items.filter(i => !i.active);
      const offSec = document.getElementById('fast-dose-offcadence-section');
      if (offItems.length === 0) {
        if (offSec) offSec.classList.add('hidden');
      } else {
        if (offSec) offSec.classList.remove('hidden');
        offList.innerHTML = offItems.map(item => `
          <label class="flex items-start gap-3 p-2 rounded-xl border border-slate-200/70 dark:border-slate-800/70 opacity-80 hover:opacity-100 hover:bg-slate-50 dark:hover:bg-slate-900/60 transition cursor-pointer">
            <input type="checkbox" id="fast-check-${item.id}" value="${item.id}" class="mt-0.5 w-4 h-4 rounded text-emerald-600 focus:ring-0 border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-900">
            <div class="flex-1 min-w-0">
              <span class="text-xs font-semibold text-slate-700 dark:text-slate-300 block">${item.name}</span>
              <span class="text-[11px] text-slate-400 dark:text-slate-500">${item.desc}</span>
            </div>
          </label>
        `).join('');
      }
    }

    modalEl.classList.remove('hidden');
  }

  function closeFastDoseModal() {
    const modalEl = document.getElementById('fast-dose-modal');
    if (modalEl) modalEl.classList.add('hidden');
  }

  async function confirmFastDoseAttestation() {
    const modalEl = document.getElementById('fast-dose-modal');
    const checkedInputs = modalEl ? modalEl.querySelectorAll('input[type="checkbox"]:checked') : [];
    if (checkedInputs.length === 0) {
      closeFastDoseModal();
      return;
    }

    const todayIso = getTodayIso();
    const nowIso = new Date().toISOString();

    const matrixRows = window.__DASHBOARD_DATA__?.hud?.adherence_matrix?.rows
      || window.globalDashboardData?.hud?.adherence_matrix?.rows;

    const failedCompounds = [];
    let successCount = 0;

    for (const input of checkedInputs) {
      const compoundId = input.value;
      try {
        const res = await fetch('/api/log_dose', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            compound_id: compoundId,
            datetime: nowIso,
            divergence: 'adherent',
            precision: 'exact',
            confidence: 'exact',
            notes: 'Fast attestation confirmation'
          })
        });

        if (!res.ok) {
          throw new Error(`HTTP ${res.status}`);
        }
        successCount++;

        // In-memory update only upon successful 2xx response
        if (matrixRows) {
          const row = matrixRows.find(r => r.compound_id === compoundId);
          if (row && row.days) {
            let dayObj = row.days.find(d => d.date === todayIso);
            if (!dayObj) {
              row.days.push({ date: todayIso, status: 'done', divergence: 'adherent' });
            } else {
              dayObj.status = 'done';
              dayObj.divergence = 'adherent';
            }
          }
        }
      } catch (e) {
        console.warn(`Fast dose log sync failed for ${compoundId}:`, e);
        failedCompounds.push(compoundId);
      }
    }

    if (failedCompounds.length > 0) {
      const msg = `Failed to log: ${failedCompounds.join(', ')}. Sync server required.`;
      if (window.App && typeof window.App.showToast === 'function') {
        window.App.showToast(msg, 'error');
      } else {
        alert(msg);
      }
    } else if (successCount > 0) {
      if (window.App && typeof window.App.showToast === 'function') {
        window.App.showToast(`Logged ${successCount} dose(s) successfully.`, 'success');
      }
    }

    closeFastDoseModal();
    renderMatrix();
    if (window.App && typeof window.App.syncHudToSelectedDate === 'function') {
      window.App.syncHudToSelectedDate(todayIso);
    }
    if (window.WidgetManager && typeof window.WidgetManager.updatePreviewChips === 'function') {
      window.WidgetManager.updatePreviewChips(window.__DASHBOARD_DATA__ || window.globalDashboardData);
    }
  }

  function initListeners() {
    // Week navigation buttons
    const prevBtn = document.getElementById('btn-prev-week');
    const nextBtn = document.getElementById('btn-next-week');
    const todayBtn = document.getElementById('btn-jump-today');
    const picker = document.getElementById('matrix-date-picker');

    if (prevBtn) prevBtn.onclick = () => navigateWeek(-1);
    if (nextBtn) nextBtn.onclick = () => navigateWeek(1);
    if (todayBtn) todayBtn.onclick = () => jumpToToday();
    if (picker) picker.onchange = (e) => jumpToDate(e.target.value);

    // Modal dismiss on Escape key & Backdrop click
    window.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') {
        closeDoseModal();
        closeFastDoseModal();
        if (typeof window.closeScansModal === 'function') window.closeScansModal();
      }
    });

    const modalEl = document.getElementById('dose-modal');
    if (modalEl) {
      modalEl.addEventListener('click', (e) => {
        if (e.target === modalEl) closeDoseModal();
      });
    }

    const fastModalEl = document.getElementById('fast-dose-modal');
    if (fastModalEl) {
      fastModalEl.addEventListener('click', (e) => {
        if (e.target === fastModalEl) closeFastDoseModal();
      });
    }

    const scansModalEl = document.getElementById('scans-review-modal');
    if (scansModalEl) {
      scansModalEl.addEventListener('click', (e) => {
        if (e.target === scansModalEl && typeof window.closeScansModal === 'function') window.closeScansModal();
      });
    }
  }

  return {
    initListeners,
    getWeekDays,
    getSelectedDate,
    getAnchorEndDate,
    selectDate,
    navigateWeek,
    jumpToToday,
    jumpToDate,
    renderMatrix,
    scrollToTodayOnMobile,
    openDoseModal,
    closeDoseModal,
    saveDoseModal,
    openFastDoseModal,
    closeFastDoseModal,
    confirmFastDoseAttestation
  };
})();

// Attach globally for inline HTML event handlers and app orchestration
window.MatrixManager = MatrixManager;
window.selectDate = MatrixManager.selectDate;
window.openDoseModal = MatrixManager.openDoseModal;
window.closeDoseModal = MatrixManager.closeDoseModal;
window.saveDoseModal = MatrixManager.saveDoseModal;
window.openFastDoseModal = MatrixManager.openFastDoseModal;
window.closeFastDoseModal = MatrixManager.closeFastDoseModal;
window.confirmFastDoseAttestation = MatrixManager.confirmFastDoseAttestation;
window.renderMatrix = MatrixManager.renderMatrix;
window.scrollToTodayOnMobile = MatrixManager.scrollToTodayOnMobile;


/**
 * Personal Health Dashboard — Exercise & Training Telemetry Explorer (ADR-026)
 * 
 * Features:
 * 1. Weekly Cadence & Stimulus (a):
 *    - 7-day workout volume (completed vs target 4 sessions)
 *    - Active calorie burn total & duration
 *    - Macro-cycle periodization status (Standard Mode, deload countdown at Week 6)
 * 2. Mechanical Load & Split (b):
 *    - Exercise split categorization (eGym, Weight Machine, Rowing, Cardio)
 *    - Session duration, notes, and pacing
 * 3. Cardiovascular Strain & HR Zones (c):
 *    - Intra-workout relative time-series Heart Rate curves via Chart.js
 *    - Cardiac Zone breakdown: Zone 1 (Warmup), Zone 2 (Aerobic), Zone 3 (Tempo), Zone 4 (Threshold), Zone 5 (Peak)
 *    - Post-exercise vagal recovery drop (HRR-60)
 * 4. Interactive Workout Pager:
 *    - Step through recent sessions with instant curve reflow
 */

const ExerciseManager = (() => {
  let exerciseData = null;
  let currentSessionIndex = 0;
  let hrChart = null;

  function init(data) {
    exerciseData = (data && data.exercise) ? data.exercise : (window.__DASHBOARD_DATA__ && window.__DASHBOARD_DATA__.exercise);
    if (!exerciseData) return;

    renderCadenceStrip();
    renderCurrentSession();
    updateCollapsedPreview();
  }

  function renderCadenceStrip() {
    if (!exerciseData || !exerciseData.cadence_7d) return;
    const c = exerciseData.cadence_7d;

    const elTotal = document.getElementById('ex-kpi-sessions');
    if (elTotal) {
      elTotal.innerHTML = `<span class="text-slate-900 dark:text-white font-bold font-mono">${c.total_sessions}</span> <span class="text-slate-400 font-normal text-xs">/ ${c.target_sessions} goal</span>`;
    }

    const elLifting = document.getElementById('ex-kpi-resistance');
    if (elLifting) {
      elLifting.innerHTML = `<span class="text-emerald-600 dark:text-emerald-400 font-bold font-mono">${c.resistance_sessions}</span> <span class="text-slate-400 font-normal text-xs">Lifting/Circuit</span>`;
    }

    const elEnergy = document.getElementById('ex-kpi-calories');
    if (elEnergy) {
      elEnergy.innerHTML = `<span class="text-cyan-600 dark:text-cyan-400 font-bold font-mono">${Math.round(c.total_calories)}</span> <span class="text-slate-400 font-normal text-xs">kcal burn</span>`;
    }

    const elDeload = document.getElementById('ex-kpi-deload');
    if (elDeload) {
      const wks = c.deload_due_week - c.current_week;
      elDeload.innerHTML = `<span class="text-purple-600 dark:text-purple-400 font-bold font-mono">${wks > 0 ? wks + ' wks' : 'Due'}</span> <span class="text-slate-400 font-normal text-xs">(W${c.deload_due_week} -30%)</span>`;
    }
  }

  function renderCurrentSession() {
    if (!exerciseData || !exerciseData.recent_sessions || exerciseData.recent_sessions.length === 0) {
      const box = document.getElementById('ex-session-container');
      if (box) box.innerHTML = `<div class="p-6 text-center text-xs text-slate-400">No recent exercise sessions recorded.</div>`;
      return;
    }

    const sessions = exerciseData.recent_sessions;
    if (currentSessionIndex < 0) currentSessionIndex = 0;
    if (currentSessionIndex >= sessions.length) currentSessionIndex = sessions.length - 1;

    const s = sessions[currentSessionIndex];

    // Pager indicators
    const labelEl = document.getElementById('ex-session-label');
    if (labelEl) {
      const dateStr = s.date || '';
      let datePretty = dateStr;
      try {
        const p = dateStr.split('-');
        const d = new Date(Number(p[0]), Number(p[1]) - 1, Number(p[2]));
        datePretty = d.toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short' });
      } catch (e) {
        datePretty = dateStr;
      }
      labelEl.innerHTML = `<span class="text-slate-900 dark:text-white font-bold">${s.name}</span> <span class="text-slate-400 dark:text-slate-500 font-normal">(${datePretty})</span>`;
    }

    const countEl = document.getElementById('ex-session-counter');
    if (countEl) {
      countEl.innerText = `${currentSessionIndex + 1} of ${sessions.length}`;
    }

    const prevBtn = document.getElementById('ex-btn-prev');
    if (prevBtn) prevBtn.disabled = (currentSessionIndex >= sessions.length - 1);

    const nextBtn = document.getElementById('ex-btn-next');
    if (nextBtn) nextBtn.disabled = (currentSessionIndex <= 0);

    // Stats
    const durEl = document.getElementById('ex-stat-dur');
    if (durEl) durEl.innerText = s.duration_m ? `${Math.round(s.duration_m)} min` : '--';

    const calEl = document.getElementById('ex-stat-cal');
    if (calEl) calEl.innerText = s.calories ? `${Math.round(s.calories)} kcal` : '--';

    const meanEl = document.getElementById('ex-stat-meanhr');
    if (meanEl) meanEl.innerText = s.mean_hr ? `${Math.round(s.mean_hr)} bpm` : '--';

    const maxEl = document.getElementById('ex-stat-maxhr');
    if (maxEl) {
      if (s.max_hr) {
        const pctMax = Math.round((s.max_hr / 168.0) * 100);
        maxEl.innerHTML = `${Math.round(s.max_hr)} bpm <span class="text-[10px] text-slate-400">(${pctMax}% Max)</span>`;
      } else {
        maxEl.innerText = '--';
      }
    }

    // Zone pills
    const zonesEl = document.getElementById('ex-stat-zones');
    if (zonesEl && s.hr_zones_pct) {
      const z = s.hr_zones_pct;
      zonesEl.innerHTML = `
        <span class="inline-flex items-center gap-1 text-[11px] px-1.5 py-0.5 rounded bg-blue-50 dark:bg-blue-950/40 text-blue-700 dark:text-blue-300 font-mono" title="Zone 2 Aerobic (100–118 bpm)">
          Z2: ${z.zone2_aerobic}%
        </span>
        <span class="inline-flex items-center gap-1 text-[11px] px-1.5 py-0.5 rounded bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 font-mono" title="Zone 3 Tempo (119–134 bpm)">
          Z3: ${z.zone3_tempo}%
        </span>
        <span class="inline-flex items-center gap-1 text-[11px] px-1.5 py-0.5 rounded bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-300 font-mono" title="Zone 4 Threshold (135–151 bpm)">
          Z4: ${z.zone4_threshold}%
        </span>
      `;
    }

    renderHeartRateChart(s);

    // Render Mechanical Stimulus & eGym Multi-Set Loads (ADR-030 / ADR-031)
    const mechContainer = document.getElementById('ex-mechanical-loads-container');
    const mechCount = document.getElementById('ex-mechanical-count');
    const mechList = document.getElementById('ex-mechanical-loads-list');

    if (mechContainer && mechList) {
      if (s.egym_exercises && s.egym_exercises.length > 0) {
        mechContainer.classList.remove('hidden');
        if (mechCount) mechCount.innerText = `${s.egym_exercises.length} Machines Reconciled`;

        mechList.innerHTML = s.egym_exercises.map(m => {
          const setsChips = (m.sets || []).map(st => `
            <span class="inline-flex items-center gap-1 text-[10px] font-mono px-1.5 py-0.5 rounded bg-slate-100 dark:bg-slate-800/80 text-slate-700 dark:text-slate-300 border border-slate-200/60 dark:border-slate-700/60">
              <span class="text-slate-400">S${st.set_number}:</span>
              <span class="font-bold text-emerald-600 dark:text-emerald-400">${st.load_kg !== null && st.load_kg !== undefined ? st.load_kg : '--'}kg</span>
              <span class="text-slate-500">×${st.reps !== null && st.reps !== undefined ? st.reps : '--'}</span>
            </span>
          `).join('');

          return `
            <div class="p-2.5 rounded-xl bg-slate-50/80 dark:bg-slate-900/50 border border-slate-200/70 dark:border-slate-800/80 space-y-1.5">
              <div class="flex items-center justify-between">
                <span class="text-xs font-bold text-slate-900 dark:text-white truncate" title="${m.machine_name}">${m.machine_name}</span>
                <span class="text-[9px] uppercase tracking-wider px-1.5 py-0.5 rounded font-semibold bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-400">
                  ${m.mode || 'Circuit'}
                </span>
              </div>
              <div class="flex items-center justify-between text-[11px] text-slate-500 dark:text-slate-400">
                <span>Peak Load: <strong class="text-slate-800 dark:text-slate-200 font-mono">${m.peak_load_kg !== null && m.peak_load_kg !== undefined ? m.peak_load_kg : '--'} kg</strong></span>
                ${m.est_energy_exp_kcal ? `<span class="text-[10px] text-slate-400 font-mono">${Math.round(m.est_energy_exp_kcal)} kcal</span>` : ''}
              </div>
              <div class="flex flex-wrap gap-1 pt-0.5">
                ${setsChips}
              </div>
            </div>
          `;
        }).join('');
      } else {
        mechContainer.classList.add('hidden');
        mechList.innerHTML = '';
        if (mechCount) mechCount.innerText = '';
      }
    }
  }

  function renderHeartRateChart(session) {
    const canvas = document.getElementById('chart-exercise-hr');
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    if (hrChart) {
      hrChart.destroy();
      hrChart = null;
    }

    const isDark = document.documentElement.classList.contains('dark');
    const points = session.hr_curve || [];

    if (points.length === 0) {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.fillStyle = isDark ? '#94A3B8' : '#64748B';
      ctx.font = '12px -apple-system, sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText('Summary telemetry recorded (Continuous curve unavailable for this session)', canvas.width / 2, canvas.height / 2);
      return;
    }

    const labels = points.map(p => `${p.offset_m}m`);
    const data = points.map(p => p.bpm);

    const gradient = ctx.createLinearGradient(0, 0, 0, canvas.height || 200);
    gradient.addColorStop(0, isDark ? 'rgba(16, 185, 129, 0.35)' : 'rgba(16, 185, 129, 0.25)');
    gradient.addColorStop(1, isDark ? 'rgba(16, 185, 129, 0.0)' : 'rgba(16, 185, 129, 0.0)');

    hrChart = new Chart(ctx, {
      type: 'line',
      data: {
        labels: labels,
        datasets: [{
          label: 'Heart Rate (bpm)',
          data: data,
          borderColor: isDark ? '#34D399' : '#10B981',
          backgroundColor: gradient,
          fill: true,
          tension: 0.25,
          borderWidth: 2,
          pointRadius: points.length > 30 ? 0 : 2,
          pointHoverRadius: 4,
          pointBackgroundColor: '#10B981'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: { duration: 300 },
        interaction: { mode: 'index', intersect: false },
        plugins: {
          legend: { display: false },
          tooltip: {
            backgroundColor: isDark ? '#1E293B' : '#FFFFFF',
            titleColor: isDark ? '#F8FAFC' : '#0F172A',
            bodyColor: isDark ? '#94A3B8' : '#475569',
            borderColor: isDark ? '#334155' : '#E2E8F0',
            borderWidth: 1,
            padding: 8,
            callbacks: {
              title: items => `T+${items[0].label}`,
              label: item => ` ${item.parsed.y} bpm`
            }
          }
        },
        scales: {
          x: {
            grid: { color: isDark ? 'rgba(30, 41, 59, 0.4)' : 'rgba(226, 232, 240, 0.8)' },
            ticks: {
              color: isDark ? '#94A3B8' : '#64748B',
              font: { family: 'ui-monospace, monospace', size: 10 },
              maxTicksLimit: 8
            }
          },
          y: {
            min: Math.max(50, Math.floor((Math.min(...data) - 10) / 10) * 10),
            max: Math.min(200, Math.ceil((Math.max(...data) + 10) / 10) * 10),
            grid: { color: isDark ? 'rgba(30, 41, 59, 0.4)' : 'rgba(226, 232, 240, 0.8)' },
            ticks: {
              color: isDark ? '#94A3B8' : '#64748B',
              font: { family: 'ui-monospace, monospace', size: 10 }
            }
          }
        }
      }
    });
  }

  function navigateSession(delta) {
    if (!exerciseData || !exerciseData.recent_sessions) return;
    currentSessionIndex -= delta;
    renderCurrentSession();
  }

  function updateCollapsedPreview() {
    const previewEl = document.getElementById('preview-exp-exercise');
    if (!previewEl || !exerciseData) return;

    const c = exerciseData.cadence_7d;
    const latest = exerciseData.recent_sessions && exerciseData.recent_sessions[0];
    const latestStr = latest ? `${latest.name.split(' ')[0]} ${Math.round(latest.duration_m || 0)}m` : 'Workouts';

    previewEl.innerHTML = `<span class="text-slate-700 dark:text-slate-300 font-mono font-bold">${c.total_sessions}/${c.target_sessions} Sessions</span> • <span class="text-emerald-600 dark:text-emerald-400 font-mono font-bold">${Math.round(c.total_calories)} kcal</span> • <span class="text-slate-500 dark:text-slate-400">${latestStr}</span>`;
  }

  function resizeCharts() {
    if (hrChart && typeof hrChart.resize === 'function') {
      const canvas = document.getElementById('chart-exercise-hr');
      if (canvas && canvas.offsetParent !== null) {
        hrChart.resize();
      }
    }
  }

  return {
    init,
    navigateSession,
    renderCadenceStrip,
    renderCurrentSession,
    resizeCharts,
    updateCollapsedPreview
  };
})();

// Attach globally
window.ExerciseManager = ExerciseManager;
window.navigateExerciseSession = ExerciseManager.navigateSession;

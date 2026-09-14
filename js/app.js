/**
 * Personal Health Dashboard — Application Bootstrap & Orchestration Module
 * Coordinates:
 * 1. Multi-tier data ingestion pipeline (Offline Embedded -> Relative Fetch -> Root Fetch)
 * 2. Tier 1 HUD state, Live +1.37 kg E2 safety alert banner, pharmacokinetic timers
 * 3. Tier 2 Domain Explorers (Body Comp, Nutrition, Autonomic, Gated Sleep)
 * 4. Tier 3 Life Eras & Longitudinal Autobiography
 * 5. Selected-date HUD card synchronization & DOMContentLoaded lifecycle bootloader
 */

const App = (() => {
  let globalData = null;

  function escapeHtml(str) {
    if (str === null || str === undefined) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  async function loadData(forceFetch = false) {
    const isHttp = window.location.protocol.startsWith('http');
    const embedded = window.__DASHBOARD_DATA__;
    const hasEmbedded = embedded && typeof embedded === 'object' && Object.keys(embedded).length > 0;

    // 1. If not forcing fetch and embedded payload is present, check freshness via /api/status (Finding P3.3)
    if (!forceFetch && hasEmbedded) {
      if (isHttp) {
        try {
          const statusRes = await fetch('/api/status', { signal: AbortSignal.timeout(1500) });
          if (statusRes.ok) {
            const statusJson = await statusRes.json();
            const serverFreshness = statusJson?.telemetry_freshness?.sensor_current_through
              || statusJson?.telemetry_freshness?.sensor_freshness?.data_current_through;
            const embeddedFreshness = embedded.data_current_through;
            // If embedded data is identical to server currency, skip redundant 1.7MB payload transfer
            if (serverFreshness && embeddedFreshness && serverFreshness === embeddedFreshness) {
              return embedded;
            }
          }
        } catch (e) {
          // If status probe times out or fails (e.g. offline or static server), trust embedded payload
          return embedded;
        }
      } else {
        // file:// or non-HTTP environment
        return embedded;
      }
    }

    // 2. Fetch live json if forceFetch requested, if server has newer data, or if no embedded payload
    const cacheBuster = `_ts=${Date.now()}`;
    if (forceFetch || isHttp || !hasEmbedded) {
      try {
        const res = await fetch(`dashboard_data.json?${cacheBuster}`);
        if (res.ok) return await res.json();
      } catch (e) {
        // Fall through
      }

      try {
        const res2 = await fetch(`/dashboard_data.json?${cacheBuster}`);
        if (res2.ok) return await res2.json();
      } catch (e2) {
        // Fall through
      }
    }

    // 3. Fallback to embedded payload if available
    if (hasEmbedded) {
      return embedded;
    }

    // 4. Fallback relative fetch without cache buster
    try {
      const res = await fetch('dashboard_data.json');
      if (res.ok) return await res.json();
    } catch (e) {
      // ignore
    }

    return null;
  }

  function showToast(message, type = 'info', durationMs = 4000) {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = 'pointer-events-auto flex items-center gap-2.5 px-4 py-2.5 rounded-xl shadow-lg border backdrop-blur-md text-xs font-semibold transition-all duration-300 transform translate-y-2 opacity-0';

    let icon = 'ℹ️';
    let styleClasses = 'bg-slate-900/90 text-slate-100 border-slate-700/80';

    if (type === 'success') {
      icon = '✓';
      styleClasses = 'bg-emerald-950/90 text-emerald-200 border-emerald-500/50 shadow-emerald-900/20';
    } else if (type === 'error') {
      icon = '⚠️';
      styleClasses = 'bg-rose-950/90 text-rose-200 border-rose-500/50 shadow-rose-900/20';
    } else if (type === 'warning') {
      icon = '⏳';
      styleClasses = 'bg-amber-950/90 text-amber-200 border-amber-500/50 shadow-amber-900/20';
    }

    toast.className += ` ${styleClasses}`;
    toast.innerHTML = `<span>${icon}</span><span class="flex-1">${message}</span>`;

    container.appendChild(toast);

    requestAnimationFrame(() => {
      toast.classList.remove('translate-y-2', 'opacity-0');
      toast.classList.add('translate-y-0', 'opacity-100');
    });

    setTimeout(() => {
      toast.classList.remove('translate-y-0', 'opacity-100');
      toast.classList.add('translate-y-2', 'opacity-0');
      setTimeout(() => {
        if (toast.parentNode) toast.parentNode.removeChild(toast);
      }, 300);
    }, durationMs);
  }

  function updateDualFreshnessUI(freshness, dataCurrentThrough) {
    const dotEl = document.getElementById('freshness-status-dot');
    const timeEl = document.getElementById('generated-timestamp');
    const chipEl = document.getElementById('freshness-sync-chip');
    const badgeEl = document.getElementById('telemetry-freshness-badge');

    const sensorIso = (freshness && (freshness.sensor_current_through || freshness.sensor_freshness?.data_current_through)) || dataCurrentThrough;
    if (timeEl && sensorIso) {
      const d = new Date(sensorIso.replace(' ', 'T'));
      const isToday = new Date().toDateString() === d.toDateString();
      timeEl.innerText = isToday
        ? `Data current through ${d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`
        : `Data current through ${d.toLocaleDateString([], { day: '2-digit', month: 'short' })} ${d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
    }

    const lastSdkSync = freshness?.last_sdk_sync || freshness?.sync_freshness?.last_companion_sync;
    const lastScaleSync = freshness?.last_scale_sync || freshness?.sync_freshness?.last_scale_sync;
    const sdkDevice = freshness?.sdk_device || freshness?.sync_freshness?.companion_device || 'Android Companion';

    if (dotEl) {
      if (!lastSdkSync) {
        dotEl.className = 'w-2 h-2 rounded-full bg-slate-400';
      } else {
        const syncDate = new Date(lastSdkSync);
        const ageHours = (Date.now() - syncDate.getTime()) / (1000 * 60 * 60);

        if (ageHours < 4) {
          dotEl.className = 'w-2 h-2 rounded-full bg-emerald-500 animate-pulse';
        } else if (ageHours < 12) {
          dotEl.className = 'w-2 h-2 rounded-full bg-amber-400';
        } else {
          dotEl.className = 'w-2 h-2 rounded-full bg-rose-500 animate-pulse';
        }
      }
    }

    if (chipEl) {
      if (lastSdkSync) {
        const syncDate = new Date(lastSdkSync);
        const diffMins = Math.round((Date.now() - syncDate.getTime()) / (1000 * 60));
        let relativeSync = `${diffMins}m ago`;
        if (diffMins < 1) relativeSync = 'just now';
        else if (diffMins >= 60) {
          const diffHours = Math.floor(diffMins / 60);
          relativeSync = `${diffHours}h ago`;
        }
        chipEl.innerText = `Sync: ${relativeSync}`;
        chipEl.classList.remove('hidden');
      } else {
        chipEl.innerText = 'Sync: Local';
      }
    }

    if (badgeEl) {
      const sThrough = sensorIso ? new Date(sensorIso.replace(' ', 'T')).toLocaleString() : 'N/A';
      const sdkStr = lastSdkSync ? `${new Date(lastSdkSync).toLocaleString()} (${sdkDevice})` : 'None logged';
      const scaleStr = lastScaleSync ? new Date(lastScaleSync).toLocaleString() : 'None logged';
      badgeEl.title = `Dual Telemetry Freshness:\n• Sensor currency: ${sThrough}\n• Companion sync: ${sdkStr}\n• Scale sync: ${scaleStr}`;
    }
  }

  function syncHudToSelectedDate(dateIso) {
    if (!dateIso) return;
    const matrixRows = window.__DASHBOARD_DATA__?.hud?.adherence_matrix?.rows
      || window.globalDashboardData?.hud?.adherence_matrix?.rows
      || [];

    // Update Cadence card status for selected date
    const cadenceRow = matrixRows.find(r => r.frequency === 'q72h' || r.compound_id === 'act-cadence' || (r.days && r.days.length > 0));
    const cadenceDay = cadenceRow && cadenceRow.days ? cadenceRow.days.find(d => d.date === dateIso) : null;
    const dutTimerEl = document.getElementById('dut-timer');
    const dutSubEl = document.getElementById('dut-timer-sublabel');
    if (dutTimerEl) {
      if (cadenceDay && (cadenceDay.status === 'done' || cadenceDay.status === 'taken')) {
        dutTimerEl.innerText = 'Administered';
        if (dutSubEl) dutSubEl.innerText = 'Logged for date';
      } else {
        const hRemaining = window.globalDashboardData?.hud?.timers?.hours_remaining ?? window.globalDashboardData?.hud?.timers?.cadence_hours_remaining;
        if (hRemaining !== null && hRemaining !== undefined) {
          dutTimerEl.innerText = `${hRemaining}h`;
          if (dutSubEl) dutSubEl.innerText = 'hours remaining';
        } else {
          dutTimerEl.innerText = '—';
          if (dutSubEl) dutSubEl.innerText = 'No dose logged yet';
        }
      }
    }

    // Update Interval counter for selected date
    const intervalRow = matrixRows.find(r => r.frequency === 'Interval' || r.compound_id === 'act-interval');
    const intervalDay = intervalRow && intervalRow.days ? intervalRow.days.find(d => d.date === dateIso) : null;
    const aiCounterEl = document.getElementById('ai-counter');
    const aiSubEl = document.getElementById('ai-counter-sublabel');
    if (aiCounterEl) {
      if (intervalDay && (intervalDay.status === 'done' || intervalDay.status === 'taken')) {
        aiCounterEl.innerText = 'Day 0';
        if (aiSubEl) aiSubEl.innerText = 'Dosed on date';
      } else {
        const daysSince = window.globalDashboardData?.hud?.timers?.interval_days;
        if (daysSince !== null && daysSince !== undefined) {
          aiCounterEl.innerText = `Day ${daysSince}`;
          if (aiSubEl) aiSubEl.innerText = 'of cycle';
        } else {
          aiCounterEl.innerText = '—';
          if (aiSubEl) aiSubEl.innerText = 'No dose logged yet';
        }
      }
    }
  }

  function populateDashboard(data) {
    if (!data) return;
    globalData = data;
    window.globalDashboardData = data;

    // Dynamic System & Schema Version Header
    const headerSubtitleEl = document.getElementById('header-subtitle');
    if (headerSubtitleEl) {
      const sysVer = data.system_version || 'v1.2.0';
      const schVer = data.schema_version ? `Schema v${data.schema_version}` : 'Schema v11';
      headerSubtitleEl.innerText = `Clinical Protocol Command Center • ${sysVer} • ${schVer} • Metric UoM (kg, g, nmol/L)`;
    }

    // -----------------------------------------------------------------------
    // 1. Tier 1 Morning Command HUD
    // -----------------------------------------------------------------------
    if (data.hud) {
      // 1. Macro-Protocol Milestone Bar (ADR-020)
      if (data.hud.phase) {
        const ph = data.hud.phase;
        const phaseNameEl = document.getElementById('milestone-phase-name');
        const weekChipEl = document.getElementById('milestone-week-chip');
        const modeChipEl = document.getElementById('milestone-mode-chip');
        const intentEl = document.getElementById('milestone-intent-text');
        const pctEl = document.getElementById('milestone-progress-pct');
        const fillEl = document.getElementById('milestone-progress-fill');
        const daysElapsedEl = document.getElementById('milestone-days-elapsed');
        const compoundsEl = document.getElementById('milestone-compounds-text');
        const nextEventEl = document.getElementById('milestone-next-event');

        if (phaseNameEl) phaseNameEl.innerText = ph.name || '16-Week Metabolic Conditioning (Cycle 1)';
        if (weekChipEl) weekChipEl.innerText = `Week ${ph.current_week || 1} of ${ph.total_weeks || 16}`;
        if (modeChipEl) modeChipEl.innerText = ph.current_mode || 'Standard Mode (Weeks 1–4)';
        if (intentEl) intentEl.innerText = ph.intent || 'Clinical protocol evaluation.';
        if (compoundsEl) compoundsEl.innerText = ph.compounds || 'Active compounds';

        const totalWeeks = ph.total_weeks || 16;
        const currentWeek = ph.current_week || 1;
        const totalDays = totalWeeks * 7;
        
        let daysElapsed = (currentWeek - 1) * 7 + 1;
        if (ph.start_date && data.data_current_through) {
          const startDt = new Date(ph.start_date + 'T00:00:00Z');
          const currentDt = new Date(data.data_current_through);
          const diffDays = Math.floor((currentDt - startDt) / (1000 * 60 * 60 * 24));
          if (diffDays >= 0) daysElapsed = Math.min(totalDays, diffDays + 1);
        }
        
        const progressPct = Math.min(100, Math.max(0, ((currentWeek / totalWeeks) * 100))).toFixed(1);
        if (pctEl) pctEl.innerText = `${progressPct}%`;
        if (fillEl) fillEl.style.width = `${progressPct}%`;
        if (daysElapsedEl) daysElapsedEl.innerText = `(Day ${daysElapsed} / ${totalDays})`;

        if (nextEventEl) {
          const milestones = data.active_protocol?.milestones || [];
          const upcoming = milestones.find(m => m.week > currentWeek || (m.week === currentWeek && m.status !== 'completed'));
          if (upcoming) {
            const weeksAway = Math.max(0, upcoming.week - currentWeek);
            nextEventEl.innerText = `Next Milestone: ${upcoming.name} (${weeksAway > 0 ? 'in ' + weeksAway + 'w' : 'Due this week'})`;
          } else if (currentWeek <= 4) {
            nextEventEl.innerText = `Next Milestone: Week 4 Transition (in ${4 - currentWeek + 1}w)`;
          } else if (currentWeek <= 6) {
            nextEventEl.innerText = `Next Milestone: Week 6 Deload & Steady-State Bloods (in ${6 - currentWeek + 1}w)`;
          } else if (currentWeek <= 8) {
            nextEventEl.innerText = `Next Milestone: Week 8 Safety Bloods (in ${8 - currentWeek + 1}w)`;
          } else if (currentWeek <= 12) {
            nextEventEl.innerText = `Next Milestone: Week 12 Deload 2 (in ${12 - currentWeek + 1}w)`;
          } else {
            nextEventEl.innerText = `Next Milestone: Week 16 Peak Evaluation (in ${16 - currentWeek + 1}w)`;
          }
        }
      }

      // Dual Telemetry Freshness (ADR-029)
      updateDualFreshnessUI(data.hud?.telemetry_freshness, data.data_current_through);

      // Data Health Status Pill
      const dh = data.hud.data_health;
      if (dh) {
        const sEl = document.getElementById('dh-scale');
        const hEl = document.getElementById('dh-hr');
        const nEl = document.getElementById('dh-nutr');
        const slEl = document.getElementById('dh-sleep');
        if (sEl) sEl.innerText = `${dh.scale_days}/${dh.days_evaluated}d`;
        if (hEl) hEl.innerText = `${dh.hr_days}/${dh.days_evaluated}d`;
        if (nEl) nEl.innerText = `${dh.nutrition_days}/${dh.days_evaluated}d`;
        if (slEl) slEl.innerText = `${dh.sleep_days}/${dh.days_evaluated}d`;
      }

      // Live High-E2 Scale Jump Alert
      const e2Banner = document.getElementById('e2-alert-banner');
      const e2Text = document.getElementById('e2-delta-text');
      if (data.hud.e2_alert && data.hud.e2_alert.active) {
        if (e2Banner) e2Banner.classList.remove('hidden');
        if (e2Text) e2Text.innerText = `+${data.hud.e2_alert.delta_48h} kg in 48h`;
      } else {
        if (e2Banner) e2Banner.classList.add('hidden');
      }

      // Scans Queue Badge (ADR-030 / ADR-031)
      const scanBadge = document.getElementById('scans-queue-badge');
      if (scanBadge) {
        const pendingCount = (data.staged_scans && data.staged_scans.pending_count) ? data.staged_scans.pending_count : 0;
        if (pendingCount > 0) {
          scanBadge.innerText = pendingCount;
          scanBadge.classList.remove('hidden');
        } else {
          scanBadge.classList.add('hidden');
        }
      }
    }

    // -----------------------------------------------------------------------
    // 2. Tier 1 (Outcomes): Body Composition & Nutrition Partitioning
    // -----------------------------------------------------------------------

    // Initialize Contextual Life Events Engine (Milestone 24, ADR-038)
    if (window.LifeEventsManager && typeof window.LifeEventsManager.init === 'function') {
      window.LifeEventsManager.init(data);
    }

    // Body Composition Explorer
    if (data.body_comp) {
      if (data.body_comp.stats) {
        const st = data.body_comp.stats;
        const fatStatEl = document.getElementById('bc-fat-stat');
        const ffmiEl = document.getElementById('bc-ffmi');
        const ffmEl = document.getElementById('bc-ffm');

        if (fatStatEl && st.latest_fat_kg) {
          fatStatEl.innerHTML = `${st.latest_fat_kg} kg <span class="text-[10px] font-normal text-emerald-500">(${st.latest_fat_pct ?? '—'}% BF)</span>`;
        }
        if (ffmiEl) {
          const ffmiVal = st.current_ffmi_norm ?? st.current_ffmi ?? '—';
          const ffmiRaw = st.current_ffmi_raw ?? '—';
          const tier = (ffmiVal !== '—' && ChartsManager.getFFMITier) ? ChartsManager.getFFMITier(ffmiVal) : '—';
          ffmiEl.innerHTML = `FFMI ${ffmiVal} <span class="text-[10px] font-normal text-emerald-500 dark:text-emerald-400">(${tier} • Raw ${ffmiRaw})</span>`;
        }
        if (ffmEl) {
          const ffmVal = st.latest_ffm_kg ?? (st.latest_weight && st.latest_fat_mass_kg ? (st.latest_weight - st.latest_fat_mass_kg).toFixed(1) : (st.latest_muscle_kg ? st.latest_muscle_kg : '—'));
          const muscleStr = st.latest_muscle_kg ? `(${st.latest_muscle_kg} kg Muscle)` : '';
          ffmEl.innerHTML = `${ffmVal} kg <span class="text-[10px] font-normal text-cyan-600 dark:text-cyan-400">${muscleStr}</span>`;
        }
        const targetEl = document.getElementById('bc-target');
        if (targetEl) {
          const targetFfmi = st.ffmi_cycle_target ?? '—';
          const targetFfm = st.target_ffm_16wk_kg ?? '—';
          const deltaLean = (targetFfm !== '—' && st.latest_ffm_kg) ? ` • +${(targetFfm - st.latest_ffm_kg).toFixed(1)} kg Lean` : '';
          targetEl.innerHTML = `FFMI ${targetFfmi} <span class="text-[10px] font-normal text-purple-500 dark:text-purple-300">(${targetFfm} kg FFM${deltaLean})</span>`;
        }
      }

      ChartsManager.initBodyCompChart(data);
    }

    // Nutrition Partitioning Explorer
    if (data.nutrition) {
      ChartsManager.renderNutritionChart(data.nutrition);
    }

    // -----------------------------------------------------------------------
    // 3. Tier 2 (Readiness): Autonomic, Gated Sleep & Longitudinal Life Eras
    // -----------------------------------------------------------------------

    // Autonomic & Cardio Recovery Explorer
    if (data.autonomic) {
      ChartsManager.renderAutonomicChart(data.autonomic);
    }

    // Gated / Unlocked Sleep State
    if (data.sleep) {
      const sleepMsgEl = document.getElementById('sleep-gated-msg');
      const sleepStatusBadge = document.getElementById('sleep-status-badge');
      const sleepGatedBox = document.getElementById('sleep-gated-box');
      const sleepUnlockedView = document.getElementById('sleep-unlocked-view');

      if (sleepMsgEl && data.sleep.gated_message) {
        sleepMsgEl.innerText = data.sleep.gated_message;
      }
      if (sleepStatusBadge) {
        if (data.sleep.is_gated) {
          sleepStatusBadge.innerText = 'Coverage Gated (<4/7 nights)';
          sleepStatusBadge.className = 'px-2.5 py-1 text-xs font-semibold bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-300 border border-amber-200 dark:border-amber-700 rounded-lg';
        } else {
          sleepStatusBadge.innerText = `Coverage Unlocked (${data.sleep.recent_7d_nights}/7 nights)`;
          sleepStatusBadge.className = 'px-2.5 py-1 text-xs font-semibold bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-700 rounded-lg';
        }
      }

      if (data.sleep.is_gated) {
        if (sleepGatedBox) sleepGatedBox.classList.remove('hidden');
        if (sleepUnlockedView) sleepUnlockedView.classList.add('hidden');
      } else {
        if (sleepGatedBox) sleepGatedBox.classList.add('hidden');
        if (sleepUnlockedView) sleepUnlockedView.classList.remove('hidden');

        const avg = data.sleep.averages_7d || {};
        const durEl = document.getElementById('sleep-stat-duration');
        const deepEl = document.getElementById('sleep-stat-deep');
        const remEl = document.getElementById('sleep-stat-rem');
        const effEl = document.getElementById('sleep-stat-eff');

        if (durEl) durEl.innerText = avg.avg_duration_h ? `${avg.avg_duration_h}h` : '—';
        if (deepEl) deepEl.innerHTML = avg.avg_deep_mins ? `${avg.avg_deep_mins}m <span class="text-[10px] font-normal text-slate-400">(&ge;60m target)</span>` : '—';
        if (remEl) remEl.innerHTML = avg.avg_rem_mins ? `${avg.avg_rem_mins}m <span class="text-[10px] font-normal text-slate-400">(&ge;75m target)</span>` : '—';
        if (effEl) effEl.innerText = avg.avg_efficiency_pct ? `${avg.avg_efficiency_pct}%` : '—';

        const tbody = document.getElementById('sleep-sessions-tbody');
        if (tbody && data.sleep.recent_sessions) {
          tbody.innerHTML = data.sleep.recent_sessions.slice(0, 7).map(s => {
            const dt = new Date(s.wake_date + 'T00:00:00');
            const dateStr = dt.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
            const totalH = (s.total_sleep_mins / 60.0).toFixed(1);
            return `
              <tr class="hover:bg-slate-50 dark:hover:bg-slate-900/40 transition">
                <td class="p-2.5 font-semibold text-slate-900 dark:text-white">${dateStr}</td>
                <td class="p-2.5 text-purple-600 dark:text-purple-400">${totalH}h (${s.total_sleep_mins}m)</td>
                <td class="p-2.5 text-emerald-600 dark:text-emerald-400 font-bold">${s.deep_mins || '—'}m</td>
                <td class="p-2.5 text-cyan-600 dark:text-cyan-400 font-bold">${s.rem_mins || '—'}m</td>
                <td class="p-2.5">${s.efficiency_pct ? `${s.efficiency_pct}%` : '—'}</td>
                <td class="p-2.5 text-slate-500">${s.score || '—'}</td>
              </tr>
            `;
          }).join('');
        }
      }
    }

    // -----------------------------------------------------------------------
    // 3. Tier 3 Life Eras & Historical Autobiography
    // -----------------------------------------------------------------------
    if (data.life_eras) {
      const tbody = document.getElementById('life-eras-tbody');
      if (tbody) {
        let rowsHtml = '';
        for (let i = 0; i < data.life_eras.length; i++) {
          const era = data.life_eras[i];
          rowsHtml += `
            <tr class="hover:bg-slate-50 dark:hover:bg-slate-900/40 transition">
              <td class="p-3 font-semibold text-slate-900 dark:text-white">${era.name}</td>
              <td class="p-3">
                <span class="px-2 py-0.5 rounded text-xs font-semibold uppercase ${era.category === 'protocol' ? 'bg-emerald-100 dark:bg-emerald-500/20 text-emerald-800 dark:text-emerald-300' : 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300'}">
                  ${era.category}
                </span>
              </td>
              <td class="p-3 text-slate-500 dark:text-slate-400 font-mono text-xs">${era.start_date} &rarr; ${era.end_date || 'Active'}</td>
              <td class="p-3 text-slate-700 dark:text-slate-300">${era.intent}</td>
              <td class="p-3 text-cyan-700 dark:text-cyan-300 font-medium">${era.compounds}</td>
              <td class="p-3 text-slate-500 dark:text-slate-400">${era.learnings}</td>
            </tr>
          `;

          // Detect unassigned multi-day gap before next era (Finding P3.2, AGENTS.md §3.4)
          if (i < data.life_eras.length - 1 && era.end_date) {
            const nextEra = data.life_eras[i + 1];
            const endD = new Date(era.end_date);
            const nextStartD = new Date(nextEra.start_date);
            const diffDays = Math.round((nextStartD - endD) / (1000 * 60 * 60 * 24)) - 1;
            if (diffDays > 30) {
              rowsHtml += `
                <tr class="bg-amber-50/50 dark:bg-amber-950/20 text-slate-500 dark:text-slate-400 border-y border-dashed border-amber-300/70 dark:border-amber-800/50">
                  <td class="p-3 font-semibold text-amber-800 dark:text-amber-300 flex items-center gap-1.5">
                    <span>⚠️</span> Unassigned Window
                  </td>
                  <td class="p-3">
                    <span class="px-2 py-0.5 rounded text-xs font-medium bg-amber-100 dark:bg-amber-900/40 text-amber-800 dark:text-amber-300">
                      Unassigned
                    </span>
                  </td>
                  <td class="p-3 font-mono text-xs text-amber-700 dark:text-amber-400">
                    ${era.end_date} &rarr; ${nextEra.start_date} (${diffDays.toLocaleString()} days)
                  </td>
                  <td class="p-3 italic text-amber-900/70 dark:text-amber-200/70" colspan="3">
                    1,089-day unassigned historical window · Flagged for review & retrospective assignment (HANDOVER §6.8).
                  </td>
                </tr>
              `;
            }
          }
        }
        tbody.innerHTML = rowsHtml;
      }
    }

    // Refresh matrix and sync initial HUD state
    MatrixManager.renderMatrix();
    syncHudToSelectedDate(MatrixManager.getSelectedDate());

    // Initialize Bloodwork & Clinical Biomarker Radar (Milestone 12)
    if (data.bloodwork && window.BloodworkManager && typeof window.BloodworkManager.init === 'function') {
      window.BloodworkManager.init(data.bloodwork);
    }

    // Initialize Exercise & Training Telemetry (ADR-026)
    if (data.exercise && window.ExerciseManager && typeof window.ExerciseManager.init === 'function') {
      window.ExerciseManager.init(data.exercise);
    }

    // Initialize Protocol Studio (Milestone 23, ADR-037)
    if (window.ProtocolStudio && typeof window.ProtocolStudio.init === 'function') {
      window.ProtocolStudio.init();
    }

    // Sync widget preview chips
    if (window.WidgetManager && typeof window.WidgetManager.updatePreviewChips === 'function') {
      window.WidgetManager.updatePreviewChips(data);
    }
  }

  function saveE2Checkin() {
    const areola = document.getElementById('e2-sym-areola')?.checked || false;
    const edema = document.getElementById('e2-sym-edema')?.checked || false;
    const mood = document.getElementById('e2-sym-mood')?.checked || false;
    const timestamp = new Date().toISOString();

    const checkin = { areola, edema, mood, timestamp };
    try {
      localStorage.setItem('health_dashboard_e2_checkin', JSON.stringify(checkin));
    } catch (e) {
      console.warn('Failed to save E2 check-in:', e);
    }

    const statusEl = document.getElementById('e2-save-status');
    if (statusEl) {
      statusEl.innerText = 'Saved ✓';
      setTimeout(() => {
        statusEl.innerText = '';
      }, 3000);
    }
  }

  function loadE2Checkin() {
    try {
      const stored = localStorage.getItem('health_dashboard_e2_checkin');
      if (stored) {
        const checkin = JSON.parse(stored);
        const aEl = document.getElementById('e2-sym-areola');
        const eEl = document.getElementById('e2-sym-edema');
        const mEl = document.getElementById('e2-sym-mood');
        if (aEl) aEl.checked = !!checkin.areola;
        if (eEl) eEl.checked = !!checkin.edema;
        if (mEl) mEl.checked = !!checkin.mood;
      }
    } catch (e) {}
  }

  function updateWithingsStatusUI(withings) {
    const dotEl = document.getElementById('withings-sync-dot');
    const textEl = document.getElementById('withings-sync-text');
    const btnEl = document.getElementById('withings-sync-btn');
    if (!dotEl || !textEl || !btnEl) return;

    if (!withings || withings.status === 'unconfigured') {
      dotEl.className = 'w-2 h-2 rounded-full bg-slate-400';
      textEl.innerText = 'Sync Scale';
      btnEl.title = 'Withings Scale Sync (Ready)';
      return;
    }

    if (withings.status === 'success') {
      dotEl.className = 'w-2 h-2 rounded-full bg-emerald-500';
      textEl.innerText = 'Scale Synced';
      const timeStr = withings.last_success ? new Date(withings.last_success).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '';
      btnEl.title = `Withings Scale: Last synced ${timeStr} (${withings.records_synced || 0} records). Click to sync now.`;
    } else if (withings.status === 'nas_authoritative' || (withings.token_authority === 'nas' && withings.status !== 'failed')) {
      dotEl.className = 'w-2 h-2 rounded-full bg-cyan-500';
      textEl.innerText = 'NAS Scale Hub';
      btnEl.title = 'Withings token authority managed on NAS container (ADR-018). Proxied through local sync server. Click to trigger sync.';
    } else if (withings.status === 'auth_expired' || withings.status === 'auth_revoked') {
      dotEl.className = 'w-2 h-2 rounded-full bg-amber-500 animate-pulse';
      textEl.innerText = 'Auth Expired';
      btnEl.title = `Withings authentication revoked or expired (${withings.error || 're-auth needed'}). Re-authenticate on NAS portal.`;
    } else if (withings.status === 'failed') {
      const is503 = withings.error && (withings.error.includes('503') || withings.error.toLowerCase().includes('unavailable'));
      dotEl.className = is503 ? 'w-2 h-2 rounded-full bg-amber-500' : 'w-2 h-2 rounded-full bg-rose-500 animate-pulse';
      textEl.innerText = is503 ? 'Scale (503)' : 'Scale Error';
      btnEl.title = `Withings Sync: ${withings.error || 'Transient error'}. Click to retry.`;
    }
  }

  async function checkServerStatus() {
    try {
      const res = await fetch('/api/status');
      if (!res.ok) return;
      const statusData = await res.json();
      if (statusData) {
        if (statusData.withings) {
          updateWithingsStatusUI(statusData.withings);
        }
        if (statusData.telemetry_freshness) {
          updateDualFreshnessUI(statusData.telemetry_freshness, statusData.telemetry_freshness.sensor_current_through || globalData?.data_current_through);
        }
      }
    } catch (e) {
      // Offline / file:// mode
    }
  }

  async function triggerWithingsSync() {
    const btnEl = document.getElementById('withings-sync-btn');
    const dotEl = document.getElementById('withings-sync-dot');
    const textEl = document.getElementById('withings-sync-text');
    const iconEl = document.getElementById('withings-sync-icon');

    if (btnEl) btnEl.disabled = true;
    if (dotEl) dotEl.className = 'w-2 h-2 rounded-full bg-amber-400 animate-ping';
    if (textEl) textEl.innerText = 'Syncing...';
    if (iconEl) iconEl.classList.add('animate-spin');

    try {
      const res = await fetch('/api/sync/withings', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: '{}'
      });
      const result = await res.json();
      if (res.ok && result.status === 'success') {
        updateWithingsStatusUI(result);
        showToast(`Scale sync complete: +${result.records_synced || 0} records`, 'success');
        // Refresh dashboard data
        const freshData = await loadData(true);
        if (freshData) populateDashboard(freshData);
      } else if (res.status === 503 || (result.error && result.error.includes('503'))) {
        updateWithingsStatusUI({
          status: 'failed',
          error: result.error || '503 Service Unavailable',
          last_attempt: new Date().toISOString()
        });
        showToast('Withings API 503 (temporary upstream outage). Retrying later.', 'warning');
      } else if (res.status === 401 || (result.error && (result.error.includes('401') || result.error.includes('invalid_grant')))) {
        updateWithingsStatusUI({
          status: 'auth_expired',
          error: result.error || 'Token expired or revoked',
          last_attempt: new Date().toISOString()
        });
        showToast('Withings authentication expired. Re-authenticate via NAS portal.', 'error');
      } else {
        updateWithingsStatusUI({
          status: 'failed',
          error: result.error || 'Server error',
          last_attempt: new Date().toISOString()
        });
        showToast(`Withings sync failed: ${result.error || 'Server error'}`, 'error');
      }
    } catch (err) {
      updateWithingsStatusUI({
        status: 'failed',
        error: err.message || 'Connection error',
        last_attempt: new Date().toISOString()
      });
      showToast(`Sync server unreachable: ${err.message}`, 'error');
    } finally {
      if (btnEl) btnEl.disabled = false;
      if (iconEl) iconEl.classList.remove('animate-spin');
    }
  }

  let pollingTimer = null;
  let lastPollTime = Date.now();

  async function pollTelemetryStatus() {
    if (document.visibilityState === 'hidden') return;

    try {
      const res = await fetch('/api/status');
      if (!res.ok) return;
      const statusData = await res.json();
      lastPollTime = Date.now();

      if (statusData) {
        if (statusData.withings) {
          updateWithingsStatusUI(statusData.withings);
        }

        if (statusData.telemetry_freshness) {
          const fresh = statusData.telemetry_freshness;
          const currentSensor = globalData?.data_current_through;
          const currentSdkSync = globalData?.hud?.telemetry_freshness?.last_sdk_sync;

          // Update badge
          updateDualFreshnessUI(fresh, fresh.sensor_current_through || currentSensor);

          // If server reports newer sensor data or newer SDK sync, seamlessly reload
          if ((fresh.sensor_current_through && fresh.sensor_current_through !== currentSensor) ||
              (fresh.last_sdk_sync && fresh.last_sdk_sync !== currentSdkSync)) {
            console.log('[TelemetryPolling] Newer data detected on server. Refreshing...');
            const freshData = await loadData(true);
            if (freshData) {
              populateDashboard(freshData);
              showToast('Live telemetry updated from server.', 'info', 3000);
            }
          }
        }
      }
    } catch (e) {
      // Offline / file:// mode
    }
  }

  function startTelemetryPolling(intervalMs = 180000) {
    if (pollingTimer) clearInterval(pollingTimer);
    pollingTimer = setInterval(pollTelemetryStatus, intervalMs);

    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'visible') {
        const elapsed = Date.now() - lastPollTime;
        if (elapsed >= intervalMs) {
          pollTelemetryStatus();
        }
      }
    });
  }

  // =========================================================================
  // Visual Scans Review Queue (ADR-030 / ADR-031 Visual Telemetry Intake)
  // =========================================================================

  async function openScansModal() {
    const modalEl = document.getElementById('scans-review-modal');
    if (!modalEl) return;
    modalEl.classList.remove('hidden');

    const contentEl = document.getElementById('scans-modal-content');
    if (contentEl) {
      contentEl.innerHTML = `
        <div class="py-12 flex flex-col items-center justify-center gap-3 text-slate-400 text-xs">
          <div class="w-6 h-6 border-2 border-cyan-500 border-t-transparent rounded-full animate-spin"></div>
          <span>Loading visual scans queue...</span>
        </div>
      `;
    }

    await refreshScansList();
  }

  function closeScansModal() {
    const modalEl = document.getElementById('scans-review-modal');
    if (modalEl) modalEl.classList.add('hidden');
  }

  async function refreshScansList() {
    const contentEl = document.getElementById('scans-modal-content');
    const badgeEl = document.getElementById('scans-queue-badge');

    try {
      const res = await fetch('/api/scans/pending');
      if (!res.ok) {
        throw new Error(`Server returned status ${res.status}`);
      }
      const data = await res.json();
      const scans = data.scans || (data.staged_scans && Array.isArray(data.staged_scans.scans) ? data.staged_scans.scans : (Array.isArray(data.staged_scans) ? data.staged_scans : [])) || [];

      // Update badge
      if (badgeEl) {
        if (scans.length > 0) {
          badgeEl.innerText = scans.length;
          badgeEl.classList.remove('hidden');
        } else {
          badgeEl.classList.add('hidden');
        }
      }

      if (!contentEl) return;

      if (scans.length === 0) {
        contentEl.innerHTML = `
          <div class="py-12 text-center text-slate-400 dark:text-slate-500 text-xs space-y-2">
            <div class="text-3xl">📷</div>
            <p class="font-semibold text-slate-700 dark:text-slate-300">All Scans Reconciled</p>
            <p>No pending visual scans in the review queue. New console photos or InBody sheets dropped in the inbox will appear here.</p>
          </div>
        `;
        return;
      }

      contentEl.innerHTML = scans.map(s => {
        const prop = s.proposal || (s.reconciliation_proposals && s.reconciliation_proposals[0]);
        const domain = s.domain || s.scan_type || (prop && prop.domain) || 'egym';
        const isEgym = domain === 'egym';
        const typeLabel = isEgym ? 'eGym Console Workout' : 'InBody Body Comp Scan';
        const typeBadgeClass = isEgym
          ? 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 border-emerald-500/30'
          : 'bg-cyan-500/15 text-cyan-600 dark:text-cyan-400 border-cyan-500/30';

        const ext = prop ? (prop.extracted_payload || {}) : {};
        const confidence = prop?.confidence ?? (s.confidence ?? 0.0);
        const hasExtractedData = confidence > 0.0 && (
          (isEgym && (ext.egym_data || ext.machines)) ||
          (!isEgym && (ext.inbody_data || ext.weight_kg))
        );
        const isExtracted = hasExtractedData || s.status === 'extracted';

        let detailsHtml = '';
        if (hasExtractedData) {
          if (isEgym) {
            const egym = ext.egym_data || {};
            const machineName = egym.machine_name || (ext.machines && ext.machines[0]?.name) || 'eGym Machine';
            const sets = egym.sets || [];
            const setsStr = sets.length > 0
              ? sets.map(st => `${escapeHtml(st.reps)}@${escapeHtml(st.load_kg)}kg`).join(', ')
              : (ext.machines ? `${ext.machines.length} machines` : 'Sets pending');
            const peakKg = egym.peak_load_kg || ext.summary_peak_load_kg || '—';
            const matched = prop.context?.wearable_session || prop.matched_exercise_session || prop.matches?.exercise_session;
            const matchText = matched
              ? `Matched: ${escapeHtml(matched.name || 'workout')} (${escapeHtml(matched.start_time || '')})`
              : 'Pending session match';
            const reasons = prop.review_reasons || [];
            const reasonBadge = reasons.length > 0
              ? `<div class="text-[11px] text-amber-600 dark:text-amber-400 mt-1">⚠️ Review required: ${escapeHtml(reasons.join(', '))}</div>`
              : '';

            detailsHtml = `
              <div class="mt-2 text-xs space-y-1 bg-slate-50 dark:bg-slate-900/60 p-2.5 rounded-lg border border-slate-200/60 dark:border-slate-800/80">
                <div class="flex items-center justify-between text-slate-700 dark:text-slate-300 font-medium">
                  <span><strong>${escapeHtml(machineName)}</strong>: [${setsStr}] • Peak ${escapeHtml(peakKg)} kg</span>
                  <span class="text-[11px] text-emerald-600 dark:text-emerald-400 font-semibold">${matchText}</span>
                </div>
                ${reasonBadge}
              </div>
            `;
          } else {
            const inbody = ext.inbody_data || ext;
            const wt = inbody.weight_kg ?? '—';
            const smm = inbody.skeletal_muscle_mass_kg ?? inbody.smm_kg ?? '—';
            const pa = inbody.phase_angle_deg ?? '—';
            const bf = inbody.body_fat_pct ?? '—';
            const diurnal = prop.context?.diurnal_offset_kg ?? prop.diurnal_offset_kg ?? prop.matches?.diurnal_offset_kg;
            const diurnalText = (diurnal !== undefined && diurnal !== null)
              ? `(diurnal ${diurnal > 0 ? '+' : ''}${escapeHtml(diurnal)} kg vs Withings)`
              : '';
            const reasons = prop.review_reasons || [];
            const reasonBadge = reasons.length > 0
              ? `<div class="text-[11px] text-amber-600 dark:text-amber-400 mt-1">⚠️ Review required: ${escapeHtml(reasons.join(', '))}</div>`
              : '';

            detailsHtml = `
              <div class="mt-2 text-xs space-y-1 bg-slate-50 dark:bg-slate-900/60 p-2.5 rounded-lg border border-slate-200/60 dark:border-slate-800/80">
                <div class="flex items-center justify-between text-slate-700 dark:text-slate-300 font-medium">
                  <span>Weight: <strong>${escapeHtml(wt)} kg</strong> • SMM: <strong>${escapeHtml(smm)} kg</strong> • BF: <strong>${escapeHtml(bf)}%</strong></span>
                  <span class="text-[11px] text-cyan-600 dark:text-cyan-400 font-semibold">Phase Angle: ${escapeHtml(pa)}°</span>
                </div>
                ${diurnalText ? `<div class="text-[11px] text-slate-500 dark:text-slate-400">${diurnalText}</div>` : ''}
                ${reasonBadge}
              </div>
            `;
          }
        } else {
          detailsHtml = `
            <div class="mt-2 text-xs text-amber-600 dark:text-amber-400 bg-amber-500/10 p-2 rounded-lg border border-amber-500/20">
              Staged raw image. Click "Run Vision Extraction" or let intake pipeline process.
            </div>
          `;
        }

        const scanIdSafe = escapeHtml(s.id);
        const imageUrlSafe = s.image_url ? escapeHtml(s.image_url) : '';
        const imgBlock = imageUrlSafe
          ? `<img src="${imageUrlSafe}" alt="${scanIdSafe}" class="w-20 h-20 sm:w-24 sm:h-24 object-cover rounded-xl border border-slate-200 dark:border-slate-700 cursor-pointer hover:opacity-90 transition shadow-xs" onclick="window.open('${imageUrlSafe}', '_blank')">`
          : `<div class="w-20 h-20 sm:w-24 sm:h-24 rounded-xl bg-slate-100 dark:bg-slate-800 flex items-center justify-center text-slate-400 text-2xl">📷</div>`;

        const statusLabel = isExtracted ? 'Extracted (Ready for Review)' : 'Staged';
        const statusBadgeClass = isExtracted ? 'bg-blue-500/20 text-blue-600 dark:text-blue-400' : 'bg-amber-500/20 text-amber-600 dark:text-amber-400';

        return `
          <div class="p-3.5 rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-[#151D2A] flex flex-col sm:flex-row items-start sm:items-center gap-3.5 shadow-xs">
            ${imgBlock}
            <div class="flex-1 min-w-0 w-full">
              <div class="flex flex-wrap items-center justify-between gap-2">
                <div class="flex items-center gap-2">
                  <span class="px-2 py-0.5 text-[10px] font-bold rounded-md uppercase tracking-wider border ${typeBadgeClass}">
                    ${typeLabel}
                  </span>
                  <span class="text-[11px] font-mono text-slate-400 dark:text-slate-500">${escapeHtml(s.uploaded_at || s.id)}</span>
                </div>
                <span class="text-[10px] font-semibold px-2 py-0.5 rounded-full ${statusBadgeClass}">
                  ${statusLabel}
                </span>
              </div>
              ${detailsHtml}
              <div class="mt-2.5 flex items-center justify-end gap-2">
                ${isExtracted ? `
                  <button onclick="App.triggerScanExtract('${scanIdSafe}')" id="btn-extract-${scanIdSafe}" class="px-2.5 py-1 rounded-lg bg-slate-100 dark:bg-slate-800 hover:bg-slate-200 dark:hover:bg-slate-700 text-slate-700 dark:text-slate-300 font-semibold text-xs transition border border-slate-200 dark:border-slate-700 cursor-pointer">
                    Re-run Extraction
                  </button>
                  <button onclick="App.confirmScanReconciliation('${scanIdSafe}')" id="btn-reconcile-${scanIdSafe}" class="px-3 py-1 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white font-semibold text-xs transition shadow-xs cursor-pointer flex items-center gap-1">
                    <span>✓</span> Confirm & Reconcile
                  </button>
                ` : `
                  <button onclick="App.triggerScanExtract('${scanIdSafe}')" id="btn-extract-${scanIdSafe}" class="px-3 py-1 rounded-lg bg-blue-600 hover:bg-blue-500 text-white font-semibold text-xs transition shadow-xs cursor-pointer">
                    Run Vision Extraction
                  </button>
                `}
              </div>
            </div>
          </div>
        `;
      }).join('');

    } catch (err) {
      if (contentEl) {
        contentEl.innerHTML = `
          <div class="py-8 text-center text-slate-400 text-xs space-y-2">
            <div class="text-amber-500 text-xl">⚠️</div>
            <p class="font-semibold text-slate-700 dark:text-slate-300">Sync Server Offline</p>
            <p class="text-slate-500">Could not fetch pending scans (${err.message}). In offline file:// mode, review queue requires local daemon at <code>http://127.0.0.1:8765</code>.</p>
          </div>
        `;
      }
    }
  }

  async function triggerScanExtract(scanId) {
    const btn = document.getElementById(`btn-extract-${scanId}`);
    if (btn) {
      btn.disabled = true;
      btn.innerText = 'Extracting...';
    }

    try {
      const res = await fetch('/api/scans/trigger_extract', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ scan_id: scanId })
      });
      const data = await res.json();
      if (res.ok && data.status === 'success') {
        showToast(`Extraction complete for ${scanId}`, 'success');
        await refreshScansList();
      } else {
        showToast(`Extraction failed: ${data.error || 'Server error'}`, 'error');
        if (btn) {
          btn.disabled = false;
          btn.innerText = 'Run Vision Extraction';
        }
      }
    } catch (e) {
      showToast(`Network error triggering extraction: ${e.message}`, 'error');
      if (btn) {
        btn.disabled = false;
        btn.innerText = 'Run Vision Extraction';
      }
    }
  }

  async function confirmScanReconciliation(scanId) {
    const btn = document.getElementById(`btn-reconcile-${scanId}`);
    if (btn) {
      btn.disabled = true;
      btn.innerText = 'Reconciling...';
    }

    try {
      const res = await fetch('/api/scans/reconcile', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ scan_id: scanId })
      });
      const data = await res.json();
      if (res.ok && data.status === 'success') {
        showToast(`Scan ${scanId} successfully reconciled!`, 'success');
        await refreshScansList();
        const freshData = await loadData(true);
        if (freshData) populateDashboard(freshData);
      } else {
        showToast(`Reconciliation failed: ${data.error || 'Server error'}`, 'error');
        if (btn) {
          btn.disabled = false;
          btn.innerText = 'Confirm & Reconcile';
        }
      }
    } catch (e) {
      showToast(`Network error reconciling scan: ${e.message}`, 'error');
      if (btn) {
        btn.disabled = false;
        btn.innerText = 'Confirm & Reconcile';
      }
    }
  }

  async function init() {
    // 1. Initialize Theme (defaults to crisp Clinical Light mode)
    ThemeManager.applyTheme(ThemeManager.getStoredTheme());

    // 2. Wire up UI event listeners
    MatrixManager.initListeners();

    // 3. Initial matrix render with defaults
    MatrixManager.selectDate(MatrixManager.getSelectedDate());

    // 4. Restore E2 checkin state
    loadE2Checkin();

    // 5. Fetch telemetry payload and populate surfaces
    const data = await loadData();
    if (data) {
      populateDashboard(data);
    }

    // 6. Initialize Movable & Collapsible Widget Engine
    if (window.WidgetManager && typeof window.WidgetManager.init === 'function') {
      window.WidgetManager.init();
    }

    // 7. Check server status & Withings telemetry
    checkServerStatus();

    // 8. Initialize Responsive & Mobile Reframing Engine (ADR-025)
    if (window.ResponsiveManager && typeof window.ResponsiveManager.init === 'function') {
      window.ResponsiveManager.init();
    }

    // 9. Start background telemetry polling (ADR-029: 3m interval with visibility guard)
    startTelemetryPolling(180000);
  }

  return {
    init,
    loadData,
    populateDashboard,
    syncHudToSelectedDate,
    saveE2Checkin,
    triggerWithingsSync,
    checkServerStatus,
    updateDualFreshnessUI,
    showToast,
    startTelemetryPolling,
    openScansModal,
    closeScansModal,
    refreshScansList,
    triggerScanExtract,
    confirmScanReconciliation,
    getData: () => globalData
  };
})();

// Attach globally
window.App = App;
window.populateDashboard = App.populateDashboard;
window.triggerWithingsSync = App.triggerWithingsSync;
window.openScansModal = App.openScansModal;
window.closeScansModal = App.closeScansModal;

// Auto-bootstrap on DOM ready
document.addEventListener('DOMContentLoaded', () => {
  App.init();
});

/**
 * Visualization & Chart.js Engine Module (ADR-003 to ADR-008)
 * Manages:
 * 1. 9-Year Body Composition & Muscularity Trajectory (12 datasets, 4 focus tabs, 5 zoom horizons, adaptive Y-scaling, dynamic physiological corridors)
 * 2. Nutrition Partitioning Explorer (Complete-day filtering & coverage denominators)
 * 3. Autonomic Balance & 60s Cardio Recovery (HRV corridor & HRR-60)
 * 4. Dynamic Light/Dark theme styling across all Chart.js instances
 */

const ChartsManager = (() => {
  let bodyCompChartInstance = null;
  let nutritionChartInstance = null;
  let autonomicChartInstance = null;

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

  function getTodayDate() {
    const now = new Date();
    return new Date(now.getFullYear(), now.getMonth(), now.getDate());
  }

  const bodyCompState = {
    horizon: 'all', // 'all', 'year', 'quarter', 'month', 'week'
    anchorDate: null,
    metricTab: 'weight' // 'weight', 'ffmi', 'fat', 'all'
  };

  let currentFilteredBodyComp = [];
  let currentHoveredIndex = null;

  function getFFMITier(ffmi) {
    if (!ffmi) return 'Intermediate Trained';
    const num = Number(ffmi);
    if (num >= 25.0) return 'Genetic Limit';
    if (num >= 23.0) return 'Elite Natural';
    if (num >= 22.0) return 'Advanced Natural';
    if (num >= 20.0) return 'Intermediate Trained';
    if (num >= 18.0) return 'Recreationally Active';
    return 'Sedentary Baseline';
  }

  function syncBodyCompHUD(row) {
    if (!row) return;
    const isDark = ThemeManager.isDarkMode();
    const metaLabelEl = document.getElementById('bc-meta-label');
    const metaStatEl = document.getElementById('bc-meta-stat');
    const fatStatEl = document.getElementById('bc-fat-stat');
    const ffmiEl = document.getElementById('bc-ffmi');
    const ffmEl = document.getElementById('bc-ffm');
    const stripEl = document.getElementById('bc-stats-strip');

    if (stripEl) {
      stripEl.classList.add('ring-1', isDark ? 'ring-emerald-500/40' : 'ring-emerald-400');
    }

    if (metaLabelEl && metaStatEl) {
      metaLabelEl.innerText = 'Scrubbing Date:';
      metaLabelEl.className = 'text-emerald-600 dark:text-emerald-400 font-semibold';
      const d = new Date(row.date + 'T00:00:00');
      const dateFormatted = d.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
      const weightVal = row.weight || row.weight_ema30 || '—';
      metaStatEl.innerHTML = `<span class="text-emerald-600 dark:text-emerald-400">${dateFormatted}</span> <span class="text-[10px] font-normal text-slate-400">(${weightVal} kg)</span>`;
    }

    if (fatStatEl) {
      const fatKg = row.fat_mass_kg || row.fat_mass_ema30 || '—';
      const fatPct = row.fat_pct || row.fat_ema30 || '—';
      fatStatEl.innerHTML = `${fatKg} kg <span class="text-[10px] font-normal text-emerald-500">(${fatPct}% BF)</span>`;
    }

    if (ffmiEl) {
      const ffmiVal = row.ffmi_norm || row.ffmi || row.ffmi_ema30 || '—';
      const ffmiRaw = row.ffmi_raw || (ffmiVal !== '—' ? (Number(ffmiVal) - 0.44).toFixed(1) : '—');
      const tier = getFFMITier(ffmiVal);
      ffmiEl.innerHTML = `FFMI ${ffmiVal} <span class="text-[10px] font-normal text-emerald-500 dark:text-emerald-400">(${tier} • Raw ${ffmiRaw})</span>`;
    }

    if (ffmEl) {
      const ffmKg = row.ffm_kg || '—';
      const muscleKg = row.muscle_kg || row.muscle_ema30 || '—';
      ffmEl.innerHTML = `${ffmKg} kg <span class="text-[10px] font-normal text-cyan-600 dark:text-cyan-400">(${muscleKg} kg Muscle)</span>`;
    }
  }

  function resetBodyCompHUD() {
    const data = window.globalDashboardData || window.__DASHBOARD_DATA__;
    const metaLabelEl = document.getElementById('bc-meta-label');
    const metaStatEl = document.getElementById('bc-meta-stat');
    const fatStatEl = document.getElementById('bc-fat-stat');
    const ffmiEl = document.getElementById('bc-ffmi');
    const ffmEl = document.getElementById('bc-ffm');
    const stripEl = document.getElementById('bc-stats-strip');

    if (stripEl) {
      stripEl.classList.remove('ring-1', 'ring-emerald-500/40', 'ring-emerald-400');
    }

    if (metaLabelEl && metaStatEl) {
      metaLabelEl.innerText = 'Demographics:';
      metaLabelEl.className = 'text-slate-500 dark:text-slate-400';
      const prof = data?.user_profile;
      const height = prof?.height_cm ? `${Math.round(prof.height_cm)} cm` : '—';
      const age = prof?.age ? `${prof.age}yo` : '—';
      const bmi = (data?.body_comp?.stats?.latest_bmi ?? (prof?.height_cm && data?.body_comp?.stats?.latest_weight_kg ? (data.body_comp.stats.latest_weight_kg / Math.pow(prof.height_cm/100, 2)).toFixed(1) : null));
      const bmiStr = bmi ? `<span class="text-[10px] font-normal text-slate-400">(BMI ${bmi})</span>` : '';
      metaStatEl.innerHTML = `${height} • ${age} ${bmiStr}`.trim();
    }

    if (data && data.body_comp && data.body_comp.stats) {
      const st = data.body_comp.stats;
      if (fatStatEl && st.latest_fat_mass_kg) {
        fatStatEl.innerHTML = `${st.latest_fat_mass_kg} kg <span class="text-[10px] font-normal text-emerald-500">(${st.latest_fat_pct}% BF)</span>`;
      }
      if (ffmiEl) {
        const ffmiVal = st.latest_ffmi_norm ?? st.current_ffmi_norm ?? st.latest_ffmi ?? st.current_ffmi ?? '—';
        const ffmiRaw = st.latest_ffmi_raw ?? st.current_ffmi_raw ?? '—';
        const tier = ffmiVal !== '—' ? getFFMITier(ffmiVal) : '—';
        ffmiEl.innerHTML = `FFMI ${ffmiVal} <span class="text-[10px] font-normal text-emerald-500 dark:text-emerald-400">(${tier} • Raw ${ffmiRaw})</span>`;
      }
      if (ffmEl) {
        const ffmVal = st.latest_ffm_kg ?? (st.latest_weight_kg && st.latest_fat_mass_kg ? (st.latest_weight_kg - st.latest_fat_mass_kg).toFixed(1) : (st.latest_muscle_kg ?? '—'));
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
  }

  const crossingValueChipsPlugin = {
    id: 'crossingValueChipsPlugin',
    afterDraw: (chart) => {
      const { ctx, chartArea, scales } = chart;
      if (!chartArea || !scales.x) return;
      const ticks = scales.x.ticks || [];
      if (ticks.length === 0) return;
      const isDark = ThemeManager.isDarkMode();

      ctx.save();
      ctx.font = 'bold 11px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, monospace';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';

      const dsCount = chart.data.datasets.length;

      ticks.forEach((tick, tickIdx) => {
        const dataIdx = tick.value;
        const isHoveredPoint = (currentHoveredIndex !== null && currentHoveredIndex === dataIdx);
        const isLatestPoint = (currentHoveredIndex === null && tickIdx === ticks.length - 1);
        if (!isHoveredPoint && !isLatestPoint) return;

        const x = scales.x.getPixelForTick(tickIdx);
        if (x < chartArea.left || x > chartArea.right) return;

        // Collect all visible curve points at this x
        const points = [];
        for (let dsIdx = 0; dsIdx < dsCount; dsIdx++) {
          const ds = chart.data.datasets[dsIdx];
          if (
            chart.isDatasetVisible(dsIdx) &&
            !ds.label.includes('Ref') &&
            !ds.label.includes('Corridor') &&
            !ds.label.includes('Target') &&
            !ds.label.includes('Upper') &&
            !ds.label.includes('Baseline') &&
            !ds.label.includes('Benchmark')
          ) {
            const val = ds.data[dataIdx];
            if (val !== null && val !== undefined) {
              const yAxisId = ds.yAxisID || 'y';
              const yAxis = chart.scales[yAxisId];
              if (yAxis) {
                let unit = ' kg';
                if (yAxisId === 'yPct') unit = '%';
                else if (yAxisId === 'yFFMI') unit = '';
                else if (yAxisId === 'yHRV') unit = ' ms';
                else if (yAxisId === 'yHR') unit = ' bpm';
                else if (yAxisId === 'yKcal') unit = ' kcal';

                points.push({
                  val: Number(val),
                  unit: unit,
                  y: yAxis.getPixelForValue(val),
                  color: ds.borderColor || '#10B981',
                  isHovered: currentHoveredIndex === dataIdx
                });
              }
            }
          }
        }

        if (points.length === 0) return;

        if (points.length > 1) {
          // Sort by y (ascending: top of canvas to bottom)
          points.sort((a, b) => a.y - b.y);

          // Calculate vertical positions with collision avoidance (min 16px distance)
          for (let i = 1; i < points.length; i++) {
            const prev = points[i - 1];
            const curr = points[i];
            if (curr.y - prev.y < 16) {
              prev.pillOffset = -12;
              curr.pillOffset = 12;
            }
          }
        }

        for (let pIdx = 0; pIdx < points.length; pIdx++) {
          const pt = points[pIdx];
          // Draw crossing node circle
          ctx.beginPath();
          ctx.arc(x, pt.y, pt.isHovered ? 4.5 : 3, 0, Math.PI * 2);
          ctx.fillStyle = pt.color;
          ctx.fill();
          ctx.lineWidth = pt.isHovered ? 2 : 1.5;
          ctx.strokeStyle = isDark ? '#0F172A' : '#FFFFFF';
          ctx.stroke();

          // Text formatting with unit suffix
          const text = `${pt.val.toFixed(1)}${pt.unit || ''}`;
          const textW = ctx.measureText(text).width;
          const pillW = textW + 8;
          const pillH = 14;

          const offset = pt.pillOffset !== undefined ? pt.pillOffset : (pt.y < chartArea.top + 20 ? 10 : -10);
          const pillY = pt.y + offset - (pillH / 2);
          let pillX = x - (pillW / 2);

          // Boundary clamping
          if (pillX < chartArea.left + 2) pillX = chartArea.left + 2;
          if (pillX + pillW > chartArea.right - 2) pillX = chartArea.right - 2 - pillW;

          // Draw pill background
          ctx.fillStyle = isDark
            ? (pt.isHovered ? 'rgba(30, 41, 59, 0.95)' : 'rgba(15, 23, 42, 0.85)')
            : (pt.isHovered ? 'rgba(255, 255, 255, 0.98)' : 'rgba(248, 250, 252, 0.90)');
          ctx.beginPath();
          if (ctx.roundRect) {
            ctx.roundRect(pillX, pillY, pillW, pillH, 3);
          } else {
            ctx.rect(pillX, pillY, pillW, pillH);
          }
          ctx.fill();

          // Pill border
          ctx.lineWidth = pt.isHovered ? 1.5 : 1;
          ctx.strokeStyle = pt.color;
          ctx.stroke();

          // Text inside pill
          ctx.fillStyle = isDark ? '#F8FAFC' : '#0F172A';
          ctx.fillText(text, pillX + (pillW / 2), pillY + (pillH / 2) + 0.5);
        }
      });
      ctx.restore();
    }
  };

  const crosshairPlugin = {
    id: 'crosshairPlugin',
    afterDraw: (chart) => {
      if (currentHoveredIndex === null || currentHoveredIndex === undefined) return;
      const { ctx, chartArea, scales } = chart;
      if (!chartArea || !scales.x) return;
      const x = scales.x.getPixelForValue(currentHoveredIndex);
      if (x < chartArea.left || x > chartArea.right) return;

      ctx.save();
      // Active Hover Guide Line
      ctx.beginPath();
      ctx.setLineDash([4, 4]);
      ctx.strokeStyle = ThemeManager.isDarkMode() ? 'rgba(16, 185, 129, 0.65)' : 'rgba(16, 185, 129, 0.75)';
      ctx.lineWidth = 1.5;
      ctx.moveTo(x, chartArea.top);
      ctx.lineTo(x, chartArea.bottom);
      ctx.stroke();

      // Active Hover intersection pulse rings
      chart.data.datasets.forEach((ds, i) => {
        if (chart.isDatasetVisible(i) && !ds.label.includes('Ref') && !ds.label.includes('Corridor') && !ds.label.includes('Target Upper') && !ds.label.includes('Baseline')) {
          const yVal = ds.data[currentHoveredIndex];
          if (yVal !== null && yVal !== undefined) {
            const yAxis = chart.scales[ds.yAxisID || 'y'];
            if (yAxis) {
              const y = yAxis.getPixelForValue(yVal);
              ctx.beginPath();
              ctx.arc(x, y, 5.5, 0, Math.PI * 2);
              ctx.fillStyle = ds.borderColor || '#10B981';
              ctx.fill();
              ctx.lineWidth = 2;
              ctx.strokeStyle = ThemeManager.isDarkMode() ? '#0B0F17' : '#FFFFFF';
              ctx.stroke();
            }
          }
        }
      });
      ctx.restore();
    }
  };

  const lifeEventsSuperimpositionPlugin = {
    id: 'lifeEventsSuperimpositionPlugin',
    beforeDraw: (chart) => {
      if (typeof LifeEventsManager === 'undefined') return;
      const { ctx, chartArea, scales } = chart;
      if (!chartArea || !scales.x) return;

      const labels = chart.data.labels;
      if (!labels || labels.length === 0) return;

      // 1. Draw Macro Life Eras if enabled
      if (LifeEventsManager.areErasEnabled() && window.__DASHBOARD_DATA__ && Array.isArray(window.__DASHBOARD_DATA__.life_eras)) {
        const eras = window.__DASHBOARD_DATA__.life_eras;
        const firstD = labels[0];
        const lastD = labels[labels.length - 1];

        ctx.save();
        ctx.beginPath();
        ctx.rect(chartArea.left, chartArea.top, chartArea.width, chartArea.height);
        ctx.clip();

        eras.forEach(era => {
          const start = era.start_date;
          const end = era.end_date || lastD;
          if (end < firstD || start > lastD) return;

          let startIdx = labels.findIndex(l => l >= start);
          if (startIdx === -1) startIdx = 0;
          let endIdx = -1;
          for (let i = labels.length - 1; i >= 0; i--) {
            if (labels[i] <= end) {
              endIdx = i;
              break;
            }
          }
          if (endIdx === -1) endIdx = labels.length - 1;

          const x1 = scales.x.getPixelForValue(startIdx);
          const x2 = scales.x.getPixelForValue(endIdx);
          const leftX = Math.min(x1, x2);
          const width = Math.max(x1, x2) - leftX;

          if (width > 2) {
            const isProtocol = era.category === 'protocol';
            ctx.fillStyle = isProtocol
              ? (ThemeManager.isDarkMode() ? 'rgba(16, 185, 129, 0.06)' : 'rgba(16, 185, 129, 0.04)')
              : (ThemeManager.isDarkMode() ? 'rgba(148, 163, 184, 0.04)' : 'rgba(148, 163, 184, 0.03)');
            ctx.fillRect(leftX, chartArea.top, width, chartArea.height);
          }
        });
        ctx.restore();
      }

      // 2. Draw Acute Contextual Life Events
      const activeEvents = LifeEventsManager.getActiveEvents();
      if (!activeEvents || activeEvents.length === 0) return;

      const firstDate = labels[0];
      const lastDate = labels[labels.length - 1];

      ctx.save();
      ctx.beginPath();
      ctx.rect(chartArea.left, chartArea.top, chartArea.width, chartArea.height);
      ctx.clip();

      activeEvents.forEach(evt => {
        const start = evt.start_date;
        const end = evt.end_date || evt.start_date;
        if (end < firstDate || start > lastDate) return;

        let startIdx = labels.findIndex(l => l >= start);
        if (startIdx === -1) startIdx = 0;
        let endIdx = -1;
        for (let i = labels.length - 1; i >= 0; i--) {
          if (labels[i] <= end) {
            endIdx = i;
            break;
          }
        }
        if (endIdx === -1) endIdx = labels.length - 1;

        const xStart = scales.x.getPixelForValue(startIdx);
        const xEnd = scales.x.getPixelForValue(endIdx);
        const catMeta = LifeEventsManager.CATEGORIES[evt.category] || LifeEventsManager.CATEGORIES.travel;

        if (start === end || Math.abs(xEnd - xStart) < 4) {
          // Single-day pin
          ctx.beginPath();
          ctx.setLineDash([4, 4]);
          ctx.strokeStyle = catMeta.border;
          ctx.lineWidth = 1.5;
          ctx.moveTo(xStart, chartArea.top);
          ctx.lineTo(xStart, chartArea.bottom);
          ctx.stroke();
        } else {
          // Multi-day pastel shaded band
          const leftX = Math.min(xStart, xEnd);
          const rightX = Math.max(xStart, xEnd);
          const width = rightX - leftX;

          ctx.fillStyle = catMeta.color;
          ctx.fillRect(leftX, chartArea.top, width, chartArea.height);

          ctx.setLineDash([3, 3]);
          ctx.strokeStyle = catMeta.border;
          ctx.lineWidth = 1;
          ctx.beginPath();
          ctx.moveTo(leftX, chartArea.top);
          ctx.lineTo(leftX, chartArea.bottom);
          ctx.moveTo(rightX, chartArea.top);
          ctx.lineTo(rightX, chartArea.bottom);
          ctx.stroke();
        }
      });
      ctx.restore();
    },

    afterDraw: (chart) => {
      if (typeof LifeEventsManager === 'undefined') return;
      const { ctx, chartArea, scales } = chart;
      if (!chartArea || !scales.x) return;

      const labels = chart.data.labels;
      if (!labels || labels.length === 0) return;

      const activeEvents = LifeEventsManager.getActiveEvents();
      if (!activeEvents || activeEvents.length === 0) return;

      const firstDate = labels[0];
      const lastDate = labels[labels.length - 1];

      ctx.save();
      activeEvents.forEach((evt, idx) => {
        const start = evt.start_date;
        const end = evt.end_date || evt.start_date;
        if (end < firstDate || start > lastDate) return;

        let startIdx = labels.findIndex(l => l >= start);
        if (startIdx === -1) startIdx = 0;
        let endIdx = -1;
        for (let i = labels.length - 1; i >= 0; i--) {
          if (labels[i] <= end) {
            endIdx = i;
            break;
          }
        }
        if (endIdx === -1) endIdx = labels.length - 1;

        const xStart = scales.x.getPixelForValue(startIdx);
        const xEnd = scales.x.getPixelForValue(endIdx);
        const midX = (xStart + xEnd) / 2;
        if (midX < chartArea.left || midX > chartArea.right) return;

        const catMeta = LifeEventsManager.CATEGORIES[evt.category] || LifeEventsManager.CATEGORIES.travel;
        const badgeY = chartArea.top + 14 + ((idx % 3) * 16);

        const pillText = `${catMeta.icon} ${evt.title}`;
        ctx.font = 'bold 9px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif';
        const textWidth = ctx.measureText(pillText).width;
        const pillW = textWidth + 10;
        const pillH = 15;
        const pillX = Math.max(chartArea.left + 2, Math.min(chartArea.right - pillW - 2, midX - (pillW / 2)));

        const isDark = ThemeManager.isDarkMode();
        ctx.fillStyle = isDark ? 'rgba(15, 23, 42, 0.88)' : 'rgba(255, 255, 255, 0.92)';
        ctx.strokeStyle = catMeta.border;
        ctx.lineWidth = 1;

        ctx.beginPath();
        if (ctx.roundRect) {
          ctx.roundRect(pillX, badgeY - (pillH / 2), pillW, pillH, 4);
        } else {
          ctx.rect(pillX, badgeY - (pillH / 2), pillW, pillH);
        }
        ctx.fill();
        ctx.stroke();

        ctx.fillStyle = isDark ? '#F1F5F9' : '#0F172A';
        ctx.textAlign = 'left';
        ctx.textBaseline = 'middle';
        ctx.fillText(pillText, pillX + 5, badgeY);
      });
      ctx.restore();
    }
  };

  function getChartColors(isDark) {
    return {
      gridColor: isDark ? 'rgba(30, 41, 59, 0.4)' : 'rgba(226, 232, 240, 0.8)',
      tickColor: isDark ? '#94A3B8' : '#64748B',
      legendColor: isDark ? '#E2E8F0' : '#1E293B'
    };
  }

  // Synchronize theme styling across all active charts
  function updateTheme(isDark) {
    const { gridColor, tickColor, legendColor } = getChartColors(isDark);

    if (bodyCompChartInstance && bodyCompChartInstance.data.datasets) {
      const ds = bodyCompChartInstance.data.datasets;
      // Dataset 8: FFMI Intermediate Trained Corridor (20.0–22.0)
      if (ds[8]) ds[8].backgroundColor = isDark ? 'rgba(168, 85, 247, 0.15)' : 'rgba(168, 85, 247, 0.10)';
      // Dataset 10: Muscle Normal Corridor (74-87%)
      if (ds[10]) ds[10].backgroundColor = isDark ? 'rgba(6, 182, 212, 0.28)' : 'rgba(6, 182, 212, 0.22)';
      // Dataset 12: Fat Normal Corridor (14-18%)
      if (ds[12]) ds[12].backgroundColor = isDark ? 'rgba(245, 158, 11, 0.26)' : 'rgba(245, 158, 11, 0.20)';
    }

    [bodyCompChartInstance, nutritionChartInstance, autonomicChartInstance].forEach(chart => {
      if (!chart) return;
      if (chart.options.scales) {
        Object.keys(chart.options.scales).forEach(scaleKey => {
          const scale = chart.options.scales[scaleKey];
          if (scale.grid && scale.grid.display !== false) scale.grid.color = gridColor;
          if (scale.ticks && scaleKey !== 'yFFMI' && scaleKey !== 'yPct') scale.ticks.color = tickColor;
        });
      }
      if (chart.options.plugins) {
        if (chart.options.plugins.legend && chart.options.plugins.legend.labels) {
          chart.options.plugins.legend.labels.color = legendColor;
        }
        if (chart.options.plugins.tooltip) {
          chart.options.plugins.tooltip.backgroundColor = isDark ? 'rgba(15, 23, 42, 0.94)' : 'rgba(255, 255, 255, 0.96)';
          chart.options.plugins.tooltip.titleColor = isDark ? '#F8FAFC' : '#0F172A';
          chart.options.plugins.tooltip.bodyColor = isDark ? '#CBD5E1' : '#334155';
          chart.options.plugins.tooltip.borderColor = isDark ? 'rgba(51, 65, 85, 0.8)' : 'rgba(226, 232, 240, 0.9)';
        }
      }
      chart.update('none');
    });
  }

  // =========================================================================
  // 1. Body Composition Engine (ADR-003 to ADR-008)
  // =========================================================================

  function setBodyCompTab(tab) {
    bodyCompState.metricTab = tab;
    ['weight', 'ffmi', 'fat', 'all'].forEach(t => {
      const btn = document.getElementById(`tab-bc-${t}`);
      if (!btn) return;
      if (t === tab) {
        btn.className = 'px-3 py-1.5 rounded-lg bg-emerald-500 text-white shadow-sm transition font-semibold whitespace-nowrap';
      } else {
        btn.className = 'px-3 py-1.5 rounded-lg text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white transition font-medium whitespace-nowrap';
      }
    });
    updateBodyCompChart();
  }

  function setBodyCompHorizon(horizon) {
    bodyCompState.horizon = horizon;
    try {
      localStorage.setItem('health_dashboard_bc_horizon', horizon);
    } catch (e) {}

    const todayDate = getTodayDate();
    if (!bodyCompState.anchorDate || bodyCompState.anchorDate > todayDate) {
      bodyCompState.anchorDate = new Date(todayDate);
    }

    ['all', 'year', 'quarter', 'month', 'week'].forEach(h => {
      const btn = document.getElementById(`btn-hz-${h}`);
      if (!btn) return;
      if (h === horizon) {
        btn.className = 'px-2.5 py-1.5 rounded-lg bg-cyan-600 text-white shadow-sm transition font-semibold whitespace-nowrap';
      } else {
        btn.className = 'px-2.5 py-1.5 rounded-lg text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white transition font-medium whitespace-nowrap';
      }
    });
    updateBodyCompChart();
  }

  function navigateBodyCompPeriod(delta) {
    const todayDate = getTodayDate();
    const todayIso = getTodayIso();
    if (!bodyCompState.anchorDate) {
      bodyCompState.anchorDate = new Date(todayDate);
    }
    const d = new Date(bodyCompState.anchorDate);
    if (bodyCompState.horizon === 'year') {
      d.setFullYear(d.getFullYear() + delta);
    } else if (bodyCompState.horizon === 'quarter') {
      d.setMonth(d.getMonth() + (delta * 3));
    } else if (bodyCompState.horizon === 'month') {
      d.setMonth(d.getMonth() + delta);
    } else if (bodyCompState.horizon === 'week') {
      d.setDate(d.getDate() + (delta * 7));
    }

    if (delta > 0) {
      const candidateRange = getHorizonRange(bodyCompState.horizon, d);
      if (candidateRange.startStr > todayIso) {
        bodyCompState.anchorDate = new Date(todayDate);
      } else {
        bodyCompState.anchorDate = d;
      }
    } else {
      bodyCompState.anchorDate = d;
    }
    updateBodyCompChart();
  }

  function jumpBodyCompToLatest() {
    bodyCompState.anchorDate = getTodayDate();
    updateBodyCompChart();
  }

  function getHorizonRange(horizon, anchor) {
    let startStr = '', endStr = '', label = '', prevLabel = 'Prev', nextLabel = 'Next';
    const a = anchor ? new Date(anchor) : getTodayDate();
    const y = a.getFullYear();
    const m = a.getMonth(); // 0-11

    if (horizon === 'all') {
      startStr = '2017-01-01';
      endStr = '2026-12-31';
      label = 'All Time (2017–2026)';
    } else if (horizon === 'year') {
      startStr = `${y}-01-01`;
      endStr = `${y}-12-31`;
      label = `Year ${y}`;
      prevLabel = `${y - 1}`;
      nextLabel = `${y + 1}`;
    } else if (horizon === 'quarter') {
      const q = Math.floor(m / 3) + 1;
      const qStartMonth = (q - 1) * 3;
      const qEndMonth = qStartMonth + 2;
      const qStart = new Date(y, qStartMonth, 1);
      const qEnd = new Date(y, qEndMonth + 1, 0);
      startStr = formatDateIso(qStart);
      endStr = formatDateIso(qEnd);
      const monthNames = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
      label = `Q${q} ${y} (${monthNames[qStartMonth]}–${monthNames[qEndMonth]})`;
      prevLabel = q === 1 ? `Q4 ${y - 1}` : `Q${q - 1} ${y}`;
      nextLabel = q === 4 ? `Q1 ${y + 1}` : `Q${q + 1} ${y}`;
    } else if (horizon === 'month') {
      const mStart = new Date(y, m, 1);
      const mEnd = new Date(y, m + 1, 0);
      startStr = formatDateIso(mStart);
      endStr = formatDateIso(mEnd);
      const fullMonthNames = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
      label = `${fullMonthNames[m]} ${y}`;
      const prevM = m === 0 ? 11 : m - 1;
      const prevY = m === 0 ? y - 1 : y;
      const nextM = m === 11 ? 0 : m + 1;
      const nextY = m === 11 ? y + 1 : y;
      prevLabel = `${fullMonthNames[prevM].slice(0, 3)} ${prevM === 11 ? prevY : ''}`.trim();
      nextLabel = `${fullMonthNames[nextM].slice(0, 3)} ${nextM === 0 ? nextY : ''}`.trim();
    } else if (horizon === 'week') {
      const d = new Date(a);
      const day = d.getDay(); // 0 is Sun, 1 is Mon, ...
      const diffToMonday = (day === 0 ? -6 : 1) - day;
      const wStart = new Date(d);
      wStart.setDate(d.getDate() + diffToMonday);
      const wEnd = new Date(wStart);
      wEnd.setDate(wStart.getDate() + 6);
      startStr = formatDateIso(wStart);
      endStr = formatDateIso(wEnd);
      const startFormatted = wStart.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
      const endFormatted = wEnd.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
      label = `Week (Mon–Sun): ${startFormatted} – ${endFormatted}`;
      prevLabel = 'Prev Wk';
      nextLabel = 'Next Wk';
    }

    return { startStr, endStr, label, prevLabel, nextLabel };
  }

  function updateBodyCompChart() {
    const data = window.globalDashboardData || window.__DASHBOARD_DATA__;
    if (!bodyCompChartInstance || !data || !data.body_comp) return;

    const rawHistory = data.body_comp.history || [];
    if (!bodyCompState.anchorDate) {
      bodyCompState.anchorDate = getTodayDate();
    }
    const range = getHorizonRange(bodyCompState.horizon, bodyCompState.anchorDate);
    const todayIso = getTodayIso();
    const isAtMax = range.endStr >= todayIso;

    // Update Pager UI
    const pagerEl = document.getElementById('bc-period-pager');
    if (pagerEl) {
      if (bodyCompState.horizon === 'all') {
        pagerEl.classList.add('hidden');
      } else {
        pagerEl.classList.remove('hidden');
        const rangeLbl = document.getElementById('bc-current-range-label');
        const prevLbl = document.getElementById('bc-prev-btn-label');
        const nextLbl = document.getElementById('bc-next-btn-label');
        const nextBtn = document.getElementById('bc-next-btn');
        if (rangeLbl) rangeLbl.innerText = range.label;
        if (prevLbl) prevLbl.innerText = range.prevLabel;
        if (nextLbl) nextLbl.innerText = range.nextLabel;
        if (nextBtn) {
          if (isAtMax) {
            nextBtn.disabled = true;
            nextBtn.classList.add('opacity-40', 'cursor-not-allowed');
          } else {
            nextBtn.disabled = false;
            nextBtn.classList.remove('opacity-40', 'cursor-not-allowed');
          }
        }
      }
    }

    const filtered = (bodyCompState.horizon === 'all')
      ? rawHistory
      : rawHistory.filter(r => r.date >= range.startStr && r.date <= range.endStr);

    currentFilteredBodyComp = filtered;

    const countEl = document.getElementById('bc-range-points-count');
    if (countEl) countEl.innerText = `${filtered.length} readings`;

    const isMicro = bodyCompState.horizon === 'week' || bodyCompState.horizon === 'month';
    const isMedium = bodyCompState.horizon === 'quarter';

    const len = filtered.length;
    const labels = new Array(len);
    const rawWeight = new Array(len);
    const weightEma = new Array(len);
    const muscleEma = new Array(len);
    const fatMassEma = new Array(len);
    const fatPctEma = new Array(len);
    const ffmiEma = new Array(len);
    const targetGoal = new Array(len);
    const ffmiInterUpper = new Array(len);
    const ffmiInterLower = new Array(len);
    const muscleRefMax = new Array(len);
    const muscleRefMin = new Array(len);
    const fatRefMax = new Array(len);
    const fatRefMin = new Array(len);

    const isAllOrYear = bodyCompState.horizon === 'all' || bodyCompState.horizon === 'year';
    const isWeek = bodyCompState.horizon === 'week';

    for (let i = 0; i < len; i++) {
      const r = filtered[i];
      if (isAllOrYear) {
        labels[i] = r.date;
      } else {
        const d = new Date(r.date + 'T00:00:00');
        labels[i] = isWeek
          ? d.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' })
          : d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
      }

      rawWeight[i] = r.weight;
      weightEma[i] = isMicro ? (r.weight_ema7 || r.weight_ema30) : r.weight_ema30;
      muscleEma[i] = isMicro ? (r.muscle_ema7 || r.muscle_ema30) : r.muscle_ema30;
      fatMassEma[i] = isMicro ? (r.fat_mass_ema7 || r.fat_mass_ema30) : r.fat_mass_ema30;
      fatPctEma[i] = isMicro ? (r.fat_ema7 || r.fat_ema30) : r.fat_ema30;
      ffmiEma[i] = isMicro ? (r.ffmi_norm_ema7 || r.ffmi_ema7 || r.ffmi_ema30) : (r.ffmi_norm_ema30 || r.ffmi_ema30);
      targetGoal[i] = 21.8;
      ffmiInterUpper[i] = 22.0;
      ffmiInterLower[i] = 20.0;
      muscleRefMax[i] = isMicro ? (r.muscle_ref_max_ema7 || r.muscle_ref_max) : r.muscle_ref_max;
      muscleRefMin[i] = isMicro ? (r.muscle_ref_min_ema7 || r.muscle_ref_min) : r.muscle_ref_min;
      fatRefMax[i] = isMicro ? (r.fat_ref_max_ema7 || r.fat_ref_max) : r.fat_ref_max;
      fatRefMin[i] = isMicro ? (r.fat_ref_min_ema7 || r.fat_ref_min) : r.fat_ref_min;
    }

    const inbodyMap = {};
    if (data.body_comp && data.body_comp.inbody_benchmarks) {
      data.body_comp.inbody_benchmarks.forEach(ib => {
        inbodyMap[ib.date] = ib;
      });
    }

    const inbodyData = new Array(len).fill(null);
    const inbodyMeta = new Array(len).fill(null);

    for (let i = 0; i < len; i++) {
      const r = filtered[i];
      if (inbodyMap[r.date]) {
        const ib = inbodyMap[r.date];
        inbodyData[i] = ib.smm_kg;
        inbodyMeta[i] = ib;
      }
    }

    const chart = bodyCompChartInstance;
    chart.data.labels = labels;

    // Dataset 0: Raw Weight
    chart.data.datasets[0].data = rawWeight;
    chart.data.datasets[0].pointRadius = isMicro ? 3.5 : (isMedium ? 2 : 1);
    chart.data.datasets[0].pointHoverRadius = isMicro ? 5 : (isMedium ? 3 : 2);

    // Dataset 1: Weight EMA (7d in micro, 30d in macro)
    chart.data.datasets[1].data = weightEma;
    chart.data.datasets[1].label = isMicro ? 'Weight 7d EMA (kg)' : 'Weight 30d EMA (kg)';
    chart.data.datasets[1].pointRadius = 0;
    chart.data.datasets[1].pointHoverRadius = 4;

    // Dataset 2: Muscle Mass EMA
    chart.data.datasets[2].data = muscleEma;
    chart.data.datasets[2].label = isMicro ? 'Muscle Mass 7d EMA (kg)' : 'Muscle Mass 30d EMA (kg)';
    chart.data.datasets[2].pointRadius = 0;
    chart.data.datasets[2].pointHoverRadius = 4;

    // Dataset 3: Fat Mass EMA (kg) (ADR-007)
    chart.data.datasets[3].data = fatMassEma;
    chart.data.datasets[3].label = isMicro ? 'Fat Mass 7d EMA (kg)' : 'Fat Mass 30d EMA (kg)';
    chart.data.datasets[3].pointRadius = 0;
    chart.data.datasets[3].pointHoverRadius = 4;

    // Dataset 4: Fat Ratio EMA (%) (ADR-007)
    chart.data.datasets[4].data = fatPctEma;
    chart.data.datasets[4].label = isMicro ? 'Fat Ratio 7d EMA (%)' : 'Fat Ratio 30d EMA (%)';
    chart.data.datasets[4].pointRadius = 0;
    chart.data.datasets[4].pointHoverRadius = 4;

    // Dataset 5: Normalized FFMI EMA (ADR-004 & ADR-015)
    chart.data.datasets[5].data = ffmiEma;
    chart.data.datasets[5].label = isMicro ? 'Normalized FFMI 7d EMA (kg/m²)' : 'Normalized FFMI 30d EMA (kg/m²)';
    chart.data.datasets[5].pointRadius = 0;
    chart.data.datasets[5].pointHoverRadius = 4;

    // Dataset 6: 16-Wk Target Goal Line (21.5 kg/m²)
    chart.data.datasets[6].data = targetGoal;

    // Dataset 7 & 8: Intermediate Trained Normative Reference Corridor (20.0–22.0 kg/m²)
    chart.data.datasets[7].data = ffmiInterUpper;
    chart.data.datasets[8].data = ffmiInterLower;

    // Dataset 9 & 10: Muscle Dynamic Reference Corridor (74% to 87% Weight) (ADR-008)
    chart.data.datasets[9].data = muscleRefMax;
    chart.data.datasets[10].data = muscleRefMin;

    // Dataset 11 & 12: Fat Dynamic Reference Corridor (14% to 18% Weight) (ADR-008 & ADR-015)
    chart.data.datasets[11].data = fatRefMax;
    chart.data.datasets[12].data = fatRefMin;

    // Dataset 13: InBody Gold Standard Benchmarks (ADR-030 / ADR-031)
    if (chart.data.datasets[13]) {
      chart.data.datasets[13].data = inbodyData;
      chart.data.datasets[13].inbodyMeta = inbodyMeta;
    }

    // Tab visibility & Adaptive Y-Axis Scale Expansion (ADR-007, ADR-008, ADR-015)
    const tab = bodyCompState.metricTab;

    if (tab === 'weight') {
      chart.setDatasetVisibility(0, isMicro);
      chart.setDatasetVisibility(1, true);  // Weight EMA (75 kg)
      chart.setDatasetVisibility(2, true);  // Muscle EMA (56 kg)
      chart.setDatasetVisibility(3, false); // Fat Mass (available on legend click)
      chart.setDatasetVisibility(4, false); // Fat %
      chart.setDatasetVisibility(5, false); // FFMI
      chart.setDatasetVisibility(6, false);
      chart.setDatasetVisibility(7, false);
      chart.setDatasetVisibility(8, false);
      chart.setDatasetVisibility(9, true);  // Muscle Ref Max (87%)
      chart.setDatasetVisibility(10, true); // Muscle Ref Min (74% shaded corridor)
      chart.setDatasetVisibility(11, false);
      chart.setDatasetVisibility(12, false);
      if (chart.data.datasets[13]) chart.setDatasetVisibility(13, true); // InBody SMM Benchmark

      chart.options.scales.y.display = true;
      chart.options.scales.y.min = 0; // Solid 0-95 kg ground plane across all mass views
      chart.options.scales.y.suggestedMax = 95;
      chart.options.scales.yFFMI.display = false;
      chart.options.scales.yPct.display = false;
    } else if (tab === 'ffmi') {
      chart.setDatasetVisibility(0, false);
      chart.setDatasetVisibility(1, false);
      chart.setDatasetVisibility(2, false);
      chart.setDatasetVisibility(3, false);
      chart.setDatasetVisibility(4, false);
      chart.setDatasetVisibility(5, true);  // Normalized FFMI EMA
      chart.setDatasetVisibility(6, true);  // 🎯 16-Wk Target Goal: 21.5 (kg/m²)
      chart.setDatasetVisibility(7, true);  // Intermediate Upper (22.0)
      chart.setDatasetVisibility(8, true);  // Intermediate Trained Corridor (20.0–22.0)
      chart.setDatasetVisibility(9, false);
      chart.setDatasetVisibility(10, false);
      chart.setDatasetVisibility(11, false);
      chart.setDatasetVisibility(12, false);
      if (chart.data.datasets[13]) chart.setDatasetVisibility(13, false);

      chart.options.scales.y.display = false;
      chart.options.scales.yFFMI.display = true;
      chart.options.scales.yFFMI.min = 16.0;
      chart.options.scales.yFFMI.max = 24.0;
      chart.options.scales.yPct.display = false;
    } else if (tab === 'fat') {
      chart.setDatasetVisibility(0, false);
      chart.setDatasetVisibility(1, false);
      chart.setDatasetVisibility(2, false);
      chart.setDatasetVisibility(3, true);  // Fat Mass (kg)
      chart.setDatasetVisibility(4, true);  // Fat Ratio (%)
      chart.setDatasetVisibility(5, false);
      chart.setDatasetVisibility(6, false);
      chart.setDatasetVisibility(7, false);
      chart.setDatasetVisibility(8, false);
      chart.setDatasetVisibility(9, false);
      chart.setDatasetVisibility(10, false);
      chart.setDatasetVisibility(11, true); // Fat Ref Max (18%)
      chart.setDatasetVisibility(12, true); // Fat Ref Min (14% shaded corridor)
      if (chart.data.datasets[13]) chart.setDatasetVisibility(13, false);

      chart.options.scales.y.display = true;
      chart.options.scales.y.min = 0; // Unified 0-95 kg scale with dual-axis Fat %
      chart.options.scales.y.suggestedMax = 95;
      chart.options.scales.yFFMI.display = false;
      chart.options.scales.yPct.display = true;
      chart.options.scales.yPct.min = 0;
      chart.options.scales.yPct.max = 35;
    } else if (tab === 'all') {
      chart.setDatasetVisibility(0, isMicro);
      chart.setDatasetVisibility(1, true);  // Total Weight (75 kg)
      chart.setDatasetVisibility(2, true);  // Muscle Mass (56 kg)
      chart.setDatasetVisibility(3, true);  // Fat Mass (15 kg)
      chart.setDatasetVisibility(4, false); // Suppress Fat % in all-in-one to prevent clutter
      chart.setDatasetVisibility(5, true);  // FFMI (20.5 on right axis)
      chart.setDatasetVisibility(6, true);  // 🎯 Target Goal (21.5)
      chart.setDatasetVisibility(7, false); // Suppress corridor in all-in-one to prevent visual clutter
      chart.setDatasetVisibility(8, false);
      chart.setDatasetVisibility(9, true);  // Muscle corridor
      chart.setDatasetVisibility(10, true);
      chart.setDatasetVisibility(11, true); // Fat corridor
      chart.setDatasetVisibility(12, true);
      if (chart.data.datasets[13]) chart.setDatasetVisibility(13, true); // InBody SMM Benchmark

      chart.options.scales.y.display = true;
      chart.options.scales.y.min = 0; // Proportional 3-tier tissue breakdown from 0 to 95 kg
      chart.options.scales.y.suggestedMax = 95;
      chart.options.scales.yFFMI.display = true;
      chart.options.scales.yFFMI.min = 16.0;
      chart.options.scales.yFFMI.max = 24.0;
      chart.options.scales.yPct.display = false;
    }

    chart.update();
  }

  function initBodyCompChart(data) {
    const isDark = ThemeManager.isDarkMode();
    const { gridColor, tickColor, legendColor } = getChartColors(isDark);

    const canvas = document.getElementById('chart-body-comp');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');

    if (bodyCompChartInstance) {
      bodyCompChartInstance.destroy();
    }

    bodyCompChartInstance = new Chart(ctx, {
      type: 'line',
      plugins: [crossingValueChipsPlugin, crosshairPlugin, lifeEventsSuperimpositionPlugin],
      data: {
        labels: [],
        datasets: [
          {
            label: 'Raw Weight (kg)',
            data: [],
            borderColor: 'rgba(148, 163, 184, 0.45)',
            backgroundColor: 'transparent',
            borderWidth: 1,
            pointRadius: 1,
            tension: 0.1,
            hidden: true,
            yAxisID: 'y'
          },
          {
            label: 'Weight 30d EMA (kg)',
            data: [],
            borderColor: '#10B981',
            backgroundColor: 'transparent',
            borderWidth: 2.5,
            pointRadius: 0,
            tension: 0.3,
            yAxisID: 'y'
          },
          {
            label: 'Muscle Mass 30d EMA (kg)',
            data: [],
            borderColor: '#06B6D4',
            backgroundColor: 'transparent',
            borderWidth: 2,
            pointRadius: 0,
            tension: 0.3,
            yAxisID: 'y'
          },
          {
            label: 'Fat Mass 30d EMA (kg)',
            data: [],
            borderColor: '#F59E0B',
            backgroundColor: 'transparent',
            borderWidth: 2,
            pointRadius: 0,
            tension: 0.3,
            hidden: true,
            yAxisID: 'y'
          },
          {
            label: 'Fat Ratio 30d EMA (%)',
            data: [],
            borderColor: '#EAB308',
            borderDash: [4, 4],
            backgroundColor: 'transparent',
            borderWidth: 1.5,
            pointRadius: 0,
            tension: 0.3,
            hidden: true,
            yAxisID: 'yPct'
          },
          {
            label: 'Normalized FFMI 30d EMA (kg/m²)',
            data: [],
            borderColor: '#A855F7',
            backgroundColor: 'transparent',
            borderWidth: 2.5,
            pointRadius: 0,
            tension: 0.3,
            hidden: true,
            yAxisID: 'yFFMI'
          },
          {
            label: '16-Wk Target: 21.8 (64.0 kg FFM)',
            data: [],
            borderColor: '#F59E0B',
            borderDash: [6, 6],
            backgroundColor: 'transparent',
            borderWidth: 1.8,
            pointRadius: 0,
            tension: 0,
            hidden: true,
            yAxisID: 'yFFMI'
          },
          {
            label: 'Intermediate Upper (22.0)',
            data: [],
            borderColor: 'rgba(168, 85, 247, 0.40)',
            borderDash: [3, 3],
            backgroundColor: 'transparent',
            borderWidth: 0.8,
            pointRadius: 0,
            tension: 0.2,
            hidden: true,
            yAxisID: 'yFFMI'
          },
          {
            label: 'Intermediate Trained Corridor (20.0–22.0)',
            data: [],
            borderColor: 'transparent',
            borderWidth: 0,
            fill: '-1',
            backgroundColor: isDark ? 'rgba(168, 85, 247, 0.15)' : 'rgba(168, 85, 247, 0.10)',
            pointRadius: 0,
            tension: 0.2,
            hidden: true,
            yAxisID: 'yFFMI'
          },
          {
            label: 'Muscle Ref Max (87%)',
            data: [],
            borderColor: 'rgba(6, 182, 212, 0.35)',
            borderDash: [3, 3],
            backgroundColor: 'transparent',
            borderWidth: 0.8,
            pointRadius: 0,
            tension: 0.2,
            hidden: true,
            yAxisID: 'y'
          },
          {
            label: 'Muscle Normal Corridor (74–87%)',
            data: [],
            borderColor: 'transparent',
            borderWidth: 0,
            fill: '-1',
            backgroundColor: isDark ? 'rgba(6, 182, 212, 0.28)' : 'rgba(6, 182, 212, 0.22)',
            pointRadius: 0,
            tension: 0.2,
            hidden: true,
            yAxisID: 'y'
          },
          {
            label: 'Fat Ref Max (18%)',
            data: [],
            borderColor: 'rgba(245, 158, 11, 0.35)',
            borderDash: [3, 3],
            backgroundColor: 'transparent',
            borderWidth: 0.8,
            pointRadius: 0,
            tension: 0.2,
            hidden: true,
            yAxisID: 'y'
          },
          {
            label: 'Fat Normal Corridor (14–18%)',
            data: [],
            borderColor: 'transparent',
            borderWidth: 0,
            fill: '-1',
            backgroundColor: isDark ? 'rgba(245, 158, 11, 0.26)' : 'rgba(245, 158, 11, 0.20)',
            pointRadius: 0,
            tension: 0.2,
            hidden: true,
            yAxisID: 'y'
          },
          {
            label: 'InBody Gold Benchmark (SMM kg)',
            data: [],
            borderColor: '#06B6D4',
            backgroundColor: '#06B6D4',
            pointStyle: 'rectRot',
            pointRadius: 6,
            pointHoverRadius: 8,
            showLine: false,
            yAxisID: 'y'
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        onHover: (event, elements, chart) => {
          if (elements && elements.length > 0) {
            const idx = elements[0].index;
            if (currentHoveredIndex !== idx) {
              currentHoveredIndex = idx;
              if (currentFilteredBodyComp && currentFilteredBodyComp[idx]) {
                syncBodyCompHUD(currentFilteredBodyComp[idx]);
              }
              chart.draw();
            }
          } else {
            if (currentHoveredIndex !== null) {
              currentHoveredIndex = null;
              resetBodyCompHUD();
              chart.draw();
            }
          }
        },
        scales: {
          x: {
            grid: {
              display: true,
              color: isDark ? 'rgba(51, 65, 85, 0.40)' : 'rgba(226, 232, 240, 0.85)',
              borderDash: [3, 3],
              lineWidth: 1
            },
            ticks: { color: tickColor, maxTicksLimit: 12 }
          },
          y: {
            position: 'left',
            grid: { color: gridColor },
            ticks: { color: tickColor }
          },
          yFFMI: {
            position: 'right',
            display: false,
            title: { display: true, text: 'Normalized FFMI (kg/m²)', color: '#A855F7' },
            min: 16.0,
            max: 24.0,
            grid: { display: false },
            ticks: { color: '#A855F7' }
          },
          yPct: {
            position: 'right',
            display: false,
            title: { display: true, text: 'Body Fat (%)', color: '#EAB308' },
            min: 0,
            max: 35,
            grid: { display: false },
            ticks: { color: '#EAB308' }
          }
        },
        plugins: {
          legend: {
            labels: {
              color: legendColor,
              boxWidth: 12,
              filter: function(item) {
                const text = item.text || '';
                // Finding P2.3: Suppress reference corridor boundary lines and shaded fills from cluttering the legend
                if (text.includes('Ref') || text.includes('Corridor') || text.includes('Upper') || text.includes('Baseline')) {
                  return false;
                }
                const tab = bodyCompState.metricTab;
                const isMicro = bodyCompState.horizon === 'week' || bodyCompState.horizon === 'month';
                if (tab === 'weight') {
                  return (text.includes('Weight') && text.includes('EMA')) ||
                         (text.includes('Muscle Mass') && text.includes('EMA')) ||
                         (isMicro && text.includes('Raw Weight')) ||
                         text.includes('InBody Gold');
                } else if (tab === 'ffmi') {
                  return text.includes('Normalized FFMI') || text.includes('Target');
                } else if (tab === 'fat') {
                  return text.includes('Fat Mass') || text.includes('Fat Ratio');
                } else if (tab === 'all') {
                  return (text.includes('Weight') && text.includes('EMA')) ||
                         (text.includes('Muscle Mass') && text.includes('EMA')) ||
                         (text.includes('Fat Mass') && text.includes('EMA')) ||
                         text.includes('Normalized FFMI') ||
                         text.includes('Target') ||
                         text.includes('InBody Gold');
                }
                return true;
              }
            }
          },
          tooltip: {
            enabled: true,
            filter: function(tooltipItem) {
              const label = tooltipItem.dataset.label || '';
              // Exclude static benchmarks and corridor boundaries from tooltip
              return !label.includes('Ref') && !label.includes('Corridor') && !label.includes('Target Upper') && !label.includes('Baseline');
            },
            backgroundColor: isDark ? 'rgba(15, 23, 42, 0.94)' : 'rgba(255, 255, 255, 0.96)',
            titleColor: isDark ? '#F8FAFC' : '#0F172A',
            bodyColor: isDark ? '#CBD5E1' : '#334155',
            borderColor: isDark ? 'rgba(51, 65, 85, 0.8)' : 'rgba(226, 232, 240, 0.9)',
            borderWidth: 1,
            padding: 10,
            boxPadding: 4,
            cornerRadius: 8,
            usePointStyle: true,
            titleFont: { size: 12, weight: 'bold' },
            bodyFont: { size: 12 },
            callbacks: {
              label: function(context) {
                let label = context.dataset.label || '';
                const val = context.parsed.y;
                if (val === null || val === undefined) return null;

                if (context.datasetIndex === 13 && context.dataset.inbodyMeta) {
                  const ib = context.dataset.inbodyMeta[context.dataIndex];
                  if (ib) {
                    const diffStr = ib.diurnal_offset_kg != null ? ` (diurnal ${ib.diurnal_offset_kg > 0 ? '+' : ''}${Number(ib.diurnal_offset_kg).toFixed(2)} kg)` : '';
                    return [
                      ` InBody SMM: ${Number(ib.smm_kg).toFixed(1)} kg${diffStr}`,
                      `   Phase Angle: ${ib.phase_angle_deg != null ? ib.phase_angle_deg + '°' : '--'}`
                    ];
                  }
                }

                label = label.replace(' 30d EMA', '').replace(' 7d EMA', '').replace(' (kg)', '').replace(' (kg/m²)', '').replace(' (%)', '');
                let unit = ' kg';
                if (context.dataset.yAxisID === 'yFFMI') unit = ' (Norm FFMI)';
                else if (context.dataset.yAxisID === 'yPct') unit = '%';
                return ` ${label}: ${Number(val).toFixed(2)}${unit}`;
              }
            }
          }
        }
      }
    });

    canvas.onmouseleave = () => {
      currentHoveredIndex = null;
      resetBodyCompHUD();
      if (bodyCompChartInstance) bodyCompChartInstance.draw();
    };

    const initialHorizon = localStorage.getItem('health_dashboard_bc_horizon') || 'month';
    setBodyCompHorizon(initialHorizon);
    setBodyCompTab('weight');
  }

  // =========================================================================
  // 2. Nutrition Partitioning Chart
  // =========================================================================

  function renderNutritionChart(nutritionData) {
    if (!nutritionData) return;
    const isDark = ThemeManager.isDarkMode();
    const { gridColor, tickColor, legendColor } = getChartColors(isDark);

    const denomEl = document.getElementById('nutr-denominator-label');
    const avgProtEl = document.getElementById('nutr-avg-protein');
    if (denomEl && nutritionData.coverage_last_90d) denomEl.innerText = nutritionData.coverage_last_90d.denominator_label;
    if (avgProtEl && nutritionData.averages_complete) avgProtEl.innerText = `${nutritionData.averages_complete.protein_g} g / day`;

    const canvas = document.getElementById('chart-nutrition');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');

    if (nutritionChartInstance) {
      nutritionChartInstance.destroy();
    }

    const rawSeries = nutritionData.recent_series || nutritionData.complete_days || [];
    const completeDays = rawSeries.filter(d => d.is_complete !== false).slice(-21);
    const labels = completeDays.map(d => {
      const dt = new Date(d.date + 'T00:00:00');
      return dt.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
    });

    const proteinG = completeDays.map(d => d.protein ?? d.protein_g ?? 0);
    const carbsG = completeDays.map(d => d.carbs ?? d.carbs_g ?? 0);
    const fatG = completeDays.map(d => d.fat ?? d.fat_g ?? 0);
    const alcoholG = completeDays.map(d => d.alcohol ?? d.alcohol_g ?? 0);
    const totalKcal = completeDays.map(d => d.calories ?? d.total_kcal ?? 0);

    nutritionChartInstance = new Chart(ctx, {
      type: 'bar',
      data: {
        labels: labels,
        datasets: [
          {
            label: 'Protein (g)',
            data: proteinG,
            backgroundColor: 'rgba(59, 130, 246, 0.85)',
            stack: 'macros'
          },
          {
            label: 'Carbs (g)',
            data: carbsG,
            backgroundColor: 'rgba(16, 185, 129, 0.85)',
            stack: 'macros'
          },
          {
            label: 'Fat (g)',
            data: fatG,
            backgroundColor: 'rgba(245, 158, 11, 0.85)',
            stack: 'macros'
          },
          {
            label: 'Alcohol (g)',
            data: alcoholG,
            backgroundColor: 'rgba(239, 68, 68, 0.75)',
            stack: 'macros'
          },
          {
            label: 'Total Energy (kcal)',
            data: totalKcal,
            type: 'line',
            borderColor: '#8B5CF6',
            backgroundColor: 'transparent',
            borderWidth: 2,
            pointRadius: 3,
            tension: 0.2,
            yAxisID: 'yKcal'
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        scales: {
          x: {
            stacked: true,
            grid: { display: false },
            ticks: { color: tickColor }
          },
          y: {
            stacked: true,
            position: 'left',
            grid: { color: gridColor },
            ticks: { color: tickColor },
            title: { display: true, text: 'Grams (g)', color: tickColor }
          },
          yKcal: {
            position: 'right',
            grid: { display: false },
            ticks: { color: '#8B5CF6' },
            title: { display: true, text: 'Energy (kcal)', color: '#8B5CF6' }
          }
        },
        plugins: {
          legend: { labels: { color: legendColor } }
        }
      }
    });
  }

  // =========================================================================
  // 3. Autonomic & Cardio Recovery Chart
  // =========================================================================

  function renderAutonomicChart(autonomicData) {
    if (!autonomicData) return;
    const isDark = ThemeManager.isDarkMode();
    const { gridColor, tickColor, legendColor } = getChartColors(isDark);

    const canvas = document.getElementById('chart-autonomic');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');

    if (autonomicChartInstance) {
      autonomicChartInstance.destroy();
    }

    const autoSeries = (autonomicData.series || autonomicData.history || []).slice(-30);
    const labels = autoSeries.map(d => {
      const dt = new Date(d.date + 'T00:00:00');
      return dt.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
    });

    const hrvVals = autoSeries.map(d => d.hrv_rmssd);
    const hrAvgVals = autoSeries.map(d => d.hr_avg ?? d.resting_hr ?? null);
    const hrvUpperVals = autoSeries.map(d => d.hrv_upper ?? null);
    const hrvLowerVals = autoSeries.map(d => d.hrv_lower ?? null);

    const rhrStatEl = document.getElementById('autonomic-rhr-stat');
    const hrrStatEl = document.getElementById('autonomic-hrr-stat');
    const badgeEl = document.getElementById('autonomic-badge-header');

    if (autoSeries.length > 0) {
      const latest = autoSeries[autoSeries.length - 1];
      if (badgeEl && latest.hrv_rmssd != null) {
        badgeEl.innerText = `HRV rmssd: ~${Math.round(latest.hrv_rmssd)}ms`;
      }
      if (rhrStatEl) {
        const rhr = latest.resting_hr ?? latest.hr_avg;
        rhrStatEl.innerText = rhr != null ? `${Math.round(rhr)} bpm` : '— bpm';
      }
    }
    if (hrrStatEl) {
      if (autonomicData.latest_hrr_meta && autonomicData.latest_hrr_meta.duration_s) {
        hrrStatEl.innerText = `Active Session (${autonomicData.latest_hrr_meta.duration_s}s recovery)`;
      } else if (autonomicData.latest_hrr_60 && autonomicData.latest_hrr_60.length) {
        hrrStatEl.innerText = `-15 bpm in 60s (Recorded)`;
      } else {
        hrrStatEl.innerText = '—';
      }
    }

    const datasets = [
      {
        label: 'HRV RMSSD (ms)',
        data: hrvVals,
        borderColor: '#06B6D4',
        backgroundColor: 'transparent',
        borderWidth: 2,
        pointRadius: 2,
        tension: 0.3,
        yAxisID: 'yHRV'
      },
      {
        label: 'Heart Rate (bpm)',
        data: hrAvgVals,
        borderColor: '#EF4444',
        backgroundColor: 'transparent',
        borderWidth: 2,
        pointRadius: 2,
        tension: 0.3,
        yAxisID: 'yHR'
      }
    ];

    if (hrvUpperVals.some(v => v !== null) && hrvLowerVals.some(v => v !== null)) {
      datasets.push({
        label: 'HRV Upper Corridor (+1 SD)',
        data: hrvUpperVals,
        borderColor: 'rgba(6, 182, 212, 0.25)',
        backgroundColor: 'rgba(6, 182, 212, 0.08)',
        borderWidth: 1,
        pointRadius: 0,
        fill: '+1',
        tension: 0.3,
        yAxisID: 'yHRV'
      });
      datasets.push({
        label: 'HRV Lower Corridor (-1 SD)',
        data: hrvLowerVals,
        borderColor: 'rgba(6, 182, 212, 0.25)',
        backgroundColor: 'transparent',
        borderWidth: 1,
        pointRadius: 0,
        fill: false,
        tension: 0.3,
        yAxisID: 'yHRV'
      });
    }

    autonomicChartInstance = new Chart(ctx, {
      type: 'line',
      plugins: [crossingValueChipsPlugin, lifeEventsSuperimpositionPlugin],
      data: {
        labels: labels,
        datasets: datasets
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        scales: {
          x: {
            grid: { color: gridColor },
            ticks: { color: tickColor }
          },
          yHRV: {
            position: 'left',
            grid: { color: gridColor },
            ticks: { color: '#06B6D4' },
            title: { display: true, text: 'HRV (ms)', color: '#06B6D4' }
          },
          yHR: {
            position: 'right',
            grid: { display: false },
            ticks: { color: '#EF4444' },
            title: { display: true, text: 'Heart Rate (bpm)', color: '#EF4444' }
          }
        },
        plugins: {
          legend: { labels: { color: legendColor } },
          tooltip: {
            callbacks: {
              title: function(items) {
                const idx = items[0].dataIndex;
                const rawDate = autoSeries[idx].date;
                const dt = new Date(rawDate + 'T00:00:00');
                return dt.toLocaleDateString('en-US', { weekday: 'long', month: 'short', day: 'numeric', year: 'numeric' });
              }
            }
          }
        }
      }
    });
  }

  function resizeCharts() {
    requestAnimationFrame(() => {
      if (bodyCompChartInstance && bodyCompChartInstance.canvas && bodyCompChartInstance.canvas.offsetParent !== null) {
        bodyCompChartInstance.resize();
      }
      if (nutritionChartInstance && nutritionChartInstance.canvas && nutritionChartInstance.canvas.offsetParent !== null) {
        nutritionChartInstance.resize();
      }
      if (autonomicChartInstance && autonomicChartInstance.canvas && autonomicChartInstance.canvas.offsetParent !== null) {
        autonomicChartInstance.resize();
      }
    });
  }

  return {
    updateTheme,
    getFFMITier,
    setBodyCompTab,
    setBodyCompHorizon,
    navigateBodyCompPeriod,
    jumpBodyCompToLatest,
    updateBodyCompChart,
    initBodyCompChart,
    renderNutritionChart,
    renderAutonomicChart,
    resizeCharts,
    refreshActiveCharts: () => {
      if (bodyCompChartInstance) bodyCompChartInstance.update('none');
      if (autonomicChartInstance) autonomicChartInstance.update('none');
    },
    getBodyCompChartInstance: () => bodyCompChartInstance,
    getNutritionChartInstance: () => nutritionChartInstance,
    getAutonomicChartInstance: () => autonomicChartInstance
  };
})();

// Attach globally for inline handlers and app coordination
window.ChartsManager = ChartsManager;
window.ChartRenderer = ChartsManager;
window.resizeCharts = ChartsManager.resizeCharts;
window.setBodyCompTab = ChartsManager.setBodyCompTab;
window.setBodyCompHorizon = ChartsManager.setBodyCompHorizon;
window.navigateBodyCompPeriod = ChartsManager.navigateBodyCompPeriod;
window.jumpBodyCompToLatest = ChartsManager.jumpBodyCompToLatest;
window.updateBodyCompChart = ChartsManager.updateBodyCompChart;

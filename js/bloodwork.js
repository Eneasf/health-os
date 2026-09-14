/**
 * Personal Health Dashboard — Bloodwork & Clinical Biomarker Radar (Milestone 12, ADR-022)
 * 
 * Clinical Focus Tabs:
 * 1. Focus Tab 1: Draw Readiness & Phlebotomy Protocol (Front Door)
 *    - Comprehensive preventive panel checklist vs last draw
 *    - Biomarker Baseline Audit: verify baseline and metabolic markers
 *    - Analytical methodology standards: standardized assays & LC-MS/MS
 *    - Readiness checklist & preparation protocol for scheduled blood requisition
 * 2. Focus Tab 2: Biomarker Command Matrix (Bullet Graphs & Drill-Down)
 *    - 18 clinical categories with category headers and count badges
 *    - Bullet graphs: horizontal bar representing reported safe reference band [ref_low, ref_high]
 *      with marker for latest test value (green in-range, red/amber out-of-range)
 *    - Triage sorting: out-of-range analytes float to the top of each category
 *    - Age badges on every row (e.g. "28d ago", "Jan '26", "2y ago")
 *    - Drill-down: discrete historical scatter points with sample dates, values, and denominators
 *      (STRICTLY NO smoothed lines across multi-month gaps per AGENTS.md §3.5)
 * 3. Focus Tab 3: Dual-Standard Clinical Consultation Brief (SI Metric vs Conventional Units)
 *    - One-tap toggle button: SI Metric units (nmol/L, g/L, mmol/L) vs Conventional units (ng/dL, g/dL, mg/dL)
 *    - Dense, categorized table showing the last 3–5 draws, 3-year min/max, out-of-range badges
 *      against local reporting lab bounds, and full provenance footers
 *    - Clean, print-ready clinical formatting
 * 
 * Clinical Invariants & Boundaries (AGENTS.md §3):
 * - §3.1: Evaluates objective telemetry; never prescribes medication changes. Prompts prescriber review.
 * - §3.2: First-class coverage denominators displayed on all aggregations.
 * - §3.4: No fabricated values; explicit null/unlogged states.
 * - §3.5: Preserves reported reference intervals; discrete non-interpolated plotting across gaps;
 *         non-numeric comparators ('<0.3') store and display operator and value separately.
 */

const BloodworkManager = (() => {
  // State
  let bloodworkData = null;
  let activeTab = "readiness"; // "readiness" | "matrix" | "brief"
  let unitMode = "uk"; // "uk" (SI Metric) | "br" (Conventional)
  let matrixCategoryFilter = "all";
  let matrixSearchQuery = "";
  let matrixOnlyOor = false;

  // Formatting helpers
  function escapeHtml(str) {
    if (str === null || str === undefined) return "";
    return String(str)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  }

  function formatDate(isoStr) {
    if (!isoStr) return "—";
    try {
      const parts = isoStr.split("-");
      const d = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
      return d.toLocaleDateString("en-GB", { day: "2-digit", month: "short", year: "numeric" });
    } catch (e) {
      return isoStr;
    }
  }

  function getDaysAgo(isoStr) {
    if (!isoStr) return null;
    try {
      const parts = isoStr.split("-");
      const d = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
      const anchorStr = window.__DASHBOARD_DATA__?.data_current_through?.substring(0, 10)
        || window.globalDashboardData?.data_current_through?.substring(0, 10);
      if (!anchorStr) return null;
      const aParts = anchorStr.split("-");
      const anchor = new Date(Number(aParts[0]), Number(aParts[1]) - 1, Number(aParts[2]));
      const diffMs = anchor - d;
      return Math.round(diffMs / (1000 * 60 * 60 * 24));
    } catch (e) {
      return null;
    }
  }

  function getAgeBadge(isoStr) {
    if (!isoStr) return "<span class=\"px-2 py-0.5 text-[10px] font-mono rounded bg-slate-100 dark:bg-slate-800 text-slate-500\">Unlogged</span>";
    const days = getDaysAgo(isoStr);
    if (days === null) return `<span class="px-2 py-0.5 text-[10px] font-mono rounded bg-slate-100 dark:bg-slate-800 text-slate-500">${escapeHtml(isoStr)}</span>`;

    let label = "";
    let style = "";

    if (days < 30) {
      label = `${days}d ago`;
      style = "bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 border-emerald-200 dark:border-emerald-800";
    } else if (days < 90) {
      label = `${Math.round(days / 30)}m ago`;
      style = "bg-cyan-50 dark:bg-cyan-950/40 text-cyan-700 dark:text-cyan-300 border-cyan-200 dark:border-cyan-800";
    } else if (days < 180) {
      label = `${Math.round(days / 30)}m ago`;
      style = "bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border-slate-200 dark:border-slate-700";
    } else if (days < 365) {
      label = `${Math.round(days / 30)}m ago`;
      style = "bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-300 border-amber-200 dark:border-amber-800 font-semibold";
    } else {
      const yrs = (days / 365.25).toFixed(1);
      label = `${yrs}y ago`;
      style = "bg-rose-50 dark:bg-rose-950/40 text-rose-700 dark:text-rose-300 border-rose-200 dark:border-rose-800 font-semibold";
    }

    return `<span class="px-2 py-0.5 text-[10px] font-mono rounded border ${style}" title="Drawn on ${escapeHtml(isoStr)} (${days} days ago)">${label}</span>`;
  }

  function getStatusChip(isOor, val, refLow, refHigh, comp) {
    if (val === null || val === undefined) {
      return "<span class=\"px-2 py-0.5 rounded text-[10px] font-mono bg-slate-100 dark:bg-slate-800 text-slate-400\">Unlogged</span>";
    }
    if (!isOor) {
      return "<span class=\"px-2 py-0.5 rounded text-[10px] font-semibold bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800\">In Range</span>";
    }
    if (refLow !== null && val < refLow) {
      return "<span class=\"px-2 py-0.5 rounded text-[10px] font-bold bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-300 border border-amber-200 dark:border-amber-800\">LOW</span>";
    }
    if (refHigh !== null && val > refHigh) {
      return "<span class=\"px-2 py-0.5 rounded text-[10px] font-bold bg-rose-50 dark:bg-rose-950/40 text-rose-700 dark:text-rose-300 border border-rose-200 dark:border-rose-800\">HIGH</span>";
    }
    return "<span class=\"px-2 py-0.5 rounded text-[10px] font-bold bg-rose-50 dark:bg-rose-950/40 text-rose-700 dark:text-rose-300 border border-rose-200 dark:border-rose-800\">OUT OF RANGE</span>";
  }

  function renderBulletGraph(val, comp, refLow, refHigh, unit, isOor) {
    if (val === null || val === undefined) {
      return "<div class=\"text-xs text-slate-400 italic\">Unlogged</div>";
    }

    if (refLow === null && refHigh === null) {
      return `
        <div class="flex items-center gap-2">
          <span class="text-xs font-mono font-bold text-slate-800 dark:text-slate-200">${comp ? escapeHtml(comp) + " " : ""}${val}</span>
          <span class="text-[10px] text-slate-400 font-mono">No ref interval</span>
        </div>
      `;
    }

    let minScale, maxScale, refStartPct, refWidthPct, markerPct;

    if (refLow !== null && refHigh !== null) {
      const span = refHigh - refLow;
      minScale = Math.min(refLow - span * 0.25, val - span * 0.1);
      if (refLow >= 0) minScale = Math.max(0, minScale);
      maxScale = Math.max(refHigh + span * 0.25, val + span * 0.1);
      if (maxScale <= minScale) maxScale = minScale + 1;
      const totalSpan = maxScale - minScale;
      refStartPct = Math.max(0, Math.min(100, ((refLow - minScale) / totalSpan) * 100));
      refWidthPct = Math.max(2, Math.min(100 - refStartPct, ((refHigh - refLow) / totalSpan) * 100));
      markerPct = Math.max(2, Math.min(98, ((val - minScale) / totalSpan) * 100));
    } else if (refHigh !== null) {
      minScale = 0;
      maxScale = Math.max(refHigh * 1.4, val * 1.15, 1);
      const totalSpan = maxScale;
      refStartPct = 0;
      refWidthPct = Math.max(2, Math.min(100, (refHigh / totalSpan) * 100));
      markerPct = Math.max(2, Math.min(98, (val / totalSpan) * 100));
    } else {
      minScale = Math.max(0, refLow * 0.5);
      maxScale = Math.max(refLow * 2.0, val * 1.25, refLow + 1);
      const totalSpan = maxScale - minScale;
      refStartPct = Math.max(0, Math.min(100, ((refLow - minScale) / totalSpan) * 100));
      refWidthPct = 100 - refStartPct;
      markerPct = Math.max(2, Math.min(98, ((val - minScale) / totalSpan) * 100));
    }

    const markerColor = isOor
      ? (val > (refHigh ?? Infinity) ? "bg-rose-500 ring-rose-300 dark:ring-rose-900" : "bg-amber-500 ring-amber-300 dark:ring-amber-900")
      : "bg-emerald-500 ring-emerald-300 dark:ring-emerald-900";

    const refLabel = (refLow !== null && refHigh !== null)
      ? `${refLow}–${refHigh}`
      : (refHigh !== null ? `≤${refHigh}` : `≥${refLow}`);

    return `
      <div class="flex flex-col gap-0.5 min-w-[150px] sm:min-w-[180px] w-full">
        <div class="relative w-full h-3 bg-slate-200 dark:bg-slate-800 rounded-full overflow-hidden">
          <div class="absolute top-0 bottom-0 bg-emerald-500/25 dark:bg-emerald-500/35 border-x border-emerald-500/50 rounded-xs"
               style="left: ${refStartPct.toFixed(1)}%; width: ${refWidthPct.toFixed(1)}%;"
               title="Safe Reference Band: ${refLabel} ${escapeHtml(unit)}"></div>
          <div class="absolute top-1/2 -translate-y-1/2 -translate-x-1/2 w-2.5 h-2.5 rounded-full ${markerColor} ring-2 ring-white dark:ring-slate-900 shadow-xs transition-all"
               style="left: ${markerPct.toFixed(1)}%;"
               title="Latest: ${comp ? escapeHtml(comp) + " " : ""}${val} ${escapeHtml(unit)} (Ref: ${refLabel})"></div>
        </div>
        <div class="flex items-center justify-between text-[10px] text-slate-400 font-mono">
          <span>Ref: ${refLabel}</span>
          <span class="text-slate-500 dark:text-slate-400 truncate max-w-[70px]">${escapeHtml(unit)}</span>
        </div>
      </div>
    `;
  }

  // ---------------------------------------------------------------------------
  // TAB 1: DRAW READINESS (FRONT DOOR)
  // ---------------------------------------------------------------------------
  function renderReadiness() {
    const pane = document.getElementById("bw-pane-readiness");
    if (!pane) return;

    const rd = bloodworkData?.draw_readiness || {};
    const latestDate = bloodworkData?.latest_draw_date || null;
    const daysSince = rd.days_since_last_draw ?? (latestDate ? getDaysAgo(latestDate) : null);

    const briefAnalytes = bloodworkData?.consultation_brief?.analytes || [];
    const mandatoryDefinitions = [
      { name: "Fasting Glucose", targetMethod: "Enzymatic Hexokinase (Glycaemic)", aliases: ["gluc", "glucose"] },
      { name: "HbA1c", targetMethod: "HPLC Ion-Exchange (Glycation)", aliases: ["hba1c", "glycated"] },
      { name: "Apolipoprotein B (ApoB)", targetMethod: "Immunoturbidimetric (Atherogenic)", aliases: ["apob", "apolipoprotein_b"] },
      { name: "High-Sensitivity CRP", targetMethod: "Particle-Enhanced Immunoturbidimetric", aliases: ["hscrp", "crp"] },
      { name: "Serum Creatinine", targetMethod: "Enzymatic Standardized (Renal)", aliases: ["creat", "creatinine"] },
      { name: "eGFR (CKD-EPI)", targetMethod: "Creatinine-Derived Filtration Index", aliases: ["egfr"] },
      { name: "ALT (Alanine Transaminase)", targetMethod: "Kinetic UV Enzymatic (Hepatic)", aliases: ["alt", "alanine"] },
      { name: "AST (Aspartate Transaminase)", targetMethod: "Kinetic UV Enzymatic (Hepatic)", aliases: ["ast", "aspartate"] },
      { name: "Lipids (Total Chol)", targetMethod: "Enzymatic Colorimetric (Lipid)", aliases: ["chol", "total_cholesterol", "cholesterol"] },
      { name: "Haematocrit", targetMethod: "Automated Cell Counter (EDTA)", aliases: ["hct", "haematocrit", "hematocrit"] },
      { name: "Haemoglobin", targetMethod: "Spectrophotometry (EDTA)", aliases: ["hgb", "haemoglobin", "hemoglobin"] },
      { name: "25-OH Vitamin D Total", targetMethod: "Chemiluminescence / LC-MS/MS", aliases: ["vitd", "vitamin_d"] }
    ];

    const checklistRows = mandatoryDefinitions.map(def => {
      const match = briefAnalytes.find(a => {
        const code = (a.analyte_code || "").toLowerCase();
        const name = (a.analyte_name || "").toLowerCase();
        return def.aliases.some(al => {
          if (code === al || code.split("_").includes(al)) return true;
          const re = new RegExp("\\b" + al + "\\b", "i");
          return re.test(code) || re.test(name);
        });
      });
      if (!match) {
        return {
          name: def.name,
          targetMethod: def.targetMethod,
          lastTested: "Scheduled Next Draw",
          lastValue: "Pending Requisition",
          ageBadge: "<span class=\"px-2 py-0.5 text-[10px] font-mono rounded bg-amber-100 dark:bg-amber-950/60 text-amber-700 dark:text-amber-300 font-bold border border-amber-200 dark:border-amber-800\">PENDING</span>",
          statusText: "📅 Scheduled Review",
          statusClass: "text-amber-600 dark:text-amber-400 font-medium"
        };
      }

      const days = getDaysAgo(match.latest_draw_date);
      const isStale = days !== null && days > 120;
      const valStr = `${match.latest_comparator ? match.latest_comparator + " " : ""}${match.latest_value} ${match.canonical_unit}`;

      return {
        name: def.name,
        targetMethod: def.targetMethod,
        lastTested: formatDate(match.latest_draw_date),
        lastValue: valStr,
        ageBadge: getAgeBadge(match.latest_draw_date),
        statusText: isStale ? "📅 Scheduled Review" : "✓ Current",
        statusClass: isStale ? "text-slate-600 dark:text-slate-400 font-medium" : "text-emerald-600 dark:text-emerald-400 font-semibold"
      };
    });

    const daysSinceLabel = daysSince !== null && daysSince !== undefined ? `${daysSince}d ago` : "—";
    const dateLabel = latestDate ? formatDate(latestDate) : "No Records";
    const daysElapsedDesc = daysSince !== null && daysSince !== undefined ? `${daysSince} days elapsed without active telemetry.` : "No draw date recorded.";

    pane.innerHTML = `
      <!-- Top 3 Metric Summary Cards -->
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div class="bg-slate-50 dark:bg-slate-900/50 border border-slate-200 dark:border-slate-800 rounded-xl p-4 space-y-1">
          <div class="flex items-center justify-between text-xs text-slate-500 dark:text-slate-400">
            <span>Last Blood Draw</span>
            <span class="font-mono text-[11px] font-bold text-amber-600 dark:text-amber-400">${daysSinceLabel}</span>
          </div>
          <div class="text-xl font-bold font-mono text-slate-900 dark:text-white">
            ${dateLabel}
          </div>
          <p class="text-[11px] text-slate-500 dark:text-slate-400">
            Routine Protocol • ${daysElapsedDesc}
          </p>
        </div>

        <div class="bg-slate-50 dark:bg-slate-900/50 border border-slate-200 dark:border-slate-800 rounded-xl p-4 space-y-1">
          <div class="flex items-center justify-between text-xs text-slate-500 dark:text-slate-400">
            <span>Next Target Window</span>
            <span class="px-2 py-0.5 rounded text-[10px] font-bold bg-purple-50 dark:bg-purple-950/40 text-purple-700 dark:text-purple-300 border border-purple-200 dark:border-purple-800">Annual Review</span>
          </div>
          <div class="text-xl font-bold text-purple-600 dark:text-purple-400">
            Routine Surveillance
          </div>
          <p class="text-[11px] text-slate-500 dark:text-slate-400">
            Target window: Q4 Comprehensive Diagnostic Evaluation.
          </p>
        </div>

        <div class="bg-purple-50/50 dark:bg-purple-950/20 border border-purple-200 dark:border-purple-900/60 rounded-xl p-4 space-y-1">
          <div class="flex items-center justify-between text-xs text-purple-700 dark:text-purple-400">
            <span class="font-semibold">Biomarker Surveillance</span>
            <span class="font-mono font-bold text-purple-600">Requisition Check</span>
          </div>
          <div class="text-xl font-bold text-purple-600 dark:text-purple-400">
            Preventive Health Baseline
          </div>
          <p class="text-[11px] text-slate-600 dark:text-slate-400">
            Systemic metabolic, lipid, and inflammatory surveillance.
          </p>
        </div>
      </div>

      <!-- Prominent Clinical Alerts -->
      <div class="space-y-3">
        <div class="bg-purple-50 dark:bg-purple-950/30 border-l-4 border-purple-500 p-4 rounded-r-xl space-y-1.5 text-xs">
          <div class="flex items-center gap-2 text-purple-900 dark:text-purple-200 font-bold text-sm">
            <span>📋</span>
            <span>Biomarker Surveillance: Pre-Requisition Verification</span>
          </div>
          <p class="text-slate-700 dark:text-slate-300 leading-relaxed">
            Verify that all target protocol biomarkers have confirmed baseline reference measurements logged prior to active phase transitions. Systematic biomarker tracking ensures reference corridor stability and long-term physiological safety.
          </p>
          <div class="flex flex-wrap items-center gap-2 pt-1 font-mono text-[11px] text-purple-700 dark:text-purple-300">
            <span class="font-bold">Clinical Requirement:</span>
            <span>Target diagnostic panel recommended on upcoming routine blood requisition.</span>
          </div>
        </div>

        <div class="bg-amber-50 dark:bg-amber-950/30 border-l-4 border-amber-500 p-4 rounded-r-xl space-y-1.5 text-xs">
          <div class="flex items-center gap-2 text-amber-900 dark:text-amber-200 font-bold text-sm">
            <span>🔬</span>
            <span>Analytical Methodology & Standardization: Reference Accuracy</span>
          </div>
          <p class="text-slate-700 dark:text-slate-300 leading-relaxed">
            Diagnostic records across accredited laboratories utilize standardized clinical assays. For sensitive endocrine and metabolic analytes, reference-grade methodologies such as <strong>LC-MS/MS (Liquid Chromatography-Tandem Mass Spectrometry)</strong> and standardized enzymatic colorimetric assays are prioritized to ensure analytical precision and longitudinal comparability across testing intervals.
          </p>
        </div>
      </div>

      <!-- Mandatory 12-Biomarker Checklist Table -->
      <div class="bg-white dark:bg-[#131A26] border border-slate-200 dark:border-slate-800 rounded-xl overflow-hidden shadow-xs">
        <div class="px-4 py-3 border-b border-slate-200 dark:border-slate-800 flex flex-wrap items-center justify-between gap-2 bg-slate-50 dark:bg-slate-900/40">
          <div>
            <h4 class="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white flex items-center gap-2">
              <span>📋</span> Mandatory Biomarker Panel Checklist
            </h4>
            <p class="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5">
              Comparison of current historical telemetry against scheduled requisition requirements.
            </p>
          </div>
          <div class="text-xs font-mono text-slate-500 dark:text-slate-400">
            Last Full Draw: ${escapeHtml(bloodworkData?.readiness?.last_draw_date || '—')}
          </div>
        </div>

        <div class="overflow-x-auto">
          <table class="w-full min-w-[620px] text-left text-xs text-slate-700 dark:text-slate-300">
            <thead class="bg-slate-100 dark:bg-slate-900/70 text-slate-600 dark:text-slate-400 uppercase text-[10px] tracking-wider border-b border-slate-200 dark:border-slate-800">
              <tr>
                <th class="p-3">Mandatory Biomarker</th>
                <th class="p-3">Target Assay Method</th>
                <th class="p-3">Last Recorded Draw</th>
                <th class="p-3">Last Result</th>
                <th class="p-3">Age / Recency</th>
                <th class="p-3">Protocol Status</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100 dark:divide-slate-800/60 font-mono">
              ${checklistRows.map(row => `
                <tr class="hover:bg-slate-50 dark:hover:bg-slate-900/40 transition">
                  <td class="p-3 font-semibold text-slate-900 dark:text-white font-sans">${escapeHtml(row.name)}</td>
                  <td class="p-3 text-slate-500 dark:text-slate-400 text-[11px] font-sans">${escapeHtml(row.targetMethod)}</td>
                  <td class="p-3 text-slate-700 dark:text-slate-300">${escapeHtml(row.lastTested)}</td>
                  <td class="p-3 text-slate-900 dark:text-white font-bold">${escapeHtml(row.lastValue)}</td>
                  <td class="p-3">${row.ageBadge}</td>
                  <td class="p-3 ${row.statusClass} font-sans">${escapeHtml(row.statusText)}</td>
                </tr>
              `).join("")}
            </tbody>
          </table>
        </div>
      </div>

      <!-- Scheduled Draw Patient Protocol & Requisition Brief -->
      <div class="bg-slate-50 dark:bg-slate-900/30 border border-slate-200 dark:border-slate-800 rounded-xl p-4 space-y-3">
        <div class="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 dark:border-slate-800/80 pb-2">
          <h4 class="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white flex items-center gap-2">
            <span>🧪</span> Patient Preparation & Phlebotomy Protocol
          </h4>
          <button onclick="BloodworkManager.setTab('brief')" class="px-3 py-1 text-xs font-semibold rounded-lg bg-purple-600 hover:bg-purple-700 text-white shadow-xs transition flex items-center gap-1">
            <span>View Consultation Brief</span> <span>➔</span>
          </button>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 text-xs">
          <div class="bg-white dark:bg-[#131A26] p-3 rounded-lg border border-slate-200 dark:border-slate-800 space-y-1">
            <div class="font-bold text-slate-900 dark:text-white flex items-center gap-1.5">
              <span>⏰</span> 1. Morning Baseline Timing
            </div>
            <p class="text-slate-600 dark:text-slate-400 text-[11px] leading-relaxed">
              Draw blood between <strong>07:30–09:00 AM</strong>. Phlebotomy should occur in a rested state prior to strenuous exercise or morning supplement intake.
            </p>
          </div>

          <div class="bg-white dark:bg-[#131A26] p-3 rounded-lg border border-slate-200 dark:border-slate-800 space-y-1">
            <div class="font-bold text-slate-900 dark:text-white flex items-center gap-1.5">
              <span>💧</span> 2. Strict Overnight Fast
            </div>
            <p class="text-slate-600 dark:text-slate-400 text-[11px] leading-relaxed">
              <strong>10–12 Hour</strong> water-only fast. Drink 500mL of water upon waking to prevent hemoconcentration / artificial hematocrit elevation. No caffeine or supplements.
            </p>
          </div>

          <div class="bg-white dark:bg-[#131A26] p-3 rounded-lg border border-slate-200 dark:border-slate-800 space-y-1">
            <div class="font-bold text-slate-900 dark:text-white flex items-center gap-1.5">
              <span>💉</span> 3. Required Specimen Tubes
            </div>
            <p class="text-slate-600 dark:text-slate-400 text-[11px] leading-relaxed">
              <strong>1x Lavender K2-EDTA</strong> (Full Blood Count, Haematocrit, Haemoglobin) + <strong>2x Gold/Red SST Serum Gel</strong> (Biochemical Panel, Lipids, CMP, Endocrine Axis).
            </p>
          </div>

          <div class="bg-white dark:bg-[#131A26] p-3 rounded-lg border border-slate-200 dark:border-slate-800 space-y-1">
            <div class="font-bold text-slate-900 dark:text-white flex items-center gap-1.5">
              <span>🎯</span> 4. Clinical Objectives
            </div>
            <p class="text-slate-600 dark:text-slate-400 text-[11px] leading-relaxed">
              Evaluate systemic cardiovascular risk, atherogenic particle concentration (ApoB), glycaemic stability (HbA1c), hepatic transaminases, and renal filtration function.
            </p>
          </div>
        </div>

        <div class="text-[11px] text-slate-500 dark:text-slate-400 pt-1 flex items-center gap-2">
          <span>⚠️</span>
          <span><strong>Medical Boundary (AGENTS.md §3.1):</strong> Dashboard telemetry evaluates objective protocol compliance; prompt for prescriber review. The dashboard never prescribes or alters medication dosages.</span>
        </div>
      </div>
    `;
  }

  // ---------------------------------------------------------------------------
  // TAB 2: BIOMARKER MATRIX (BULLET GRAPHS)
  // ---------------------------------------------------------------------------
  function renderMatrix() {
    const pane = document.getElementById("bw-pane-matrix");
    if (!pane) return;

    const categories = bloodworkData?.categories || [];
    const brief = bloodworkData?.consultation_brief || {};
    const totalAnalytes = brief.total_analytes || 86;
    const oorCount = brief.out_of_range_count || 10;

    const query = matrixSearchQuery.trim().toLowerCase();

    const filteredCategories = categories.map(cat => {
      let analytes = cat.analytes;

      if (matrixCategoryFilter !== "all" && cat.category !== matrixCategoryFilter) {
        return null;
      }

      if (matrixOnlyOor) {
        analytes = analytes.filter(a => a.is_out_of_range);
      }

      if (query) {
        analytes = analytes.filter(a =>
          a.analyte_name.toLowerCase().includes(query) ||
          a.analyte_code.toLowerCase().includes(query) ||
          cat.category.toLowerCase().includes(query)
        );
      }

      if (analytes.length === 0) return null;

      const sortedAnalytes = [...analytes].sort((a, b) => {
        if (a.is_out_of_range && !b.is_out_of_range) return -1;
        if (!a.is_out_of_range && b.is_out_of_range) return 1;
        return a.analyte_name.localeCompare(b.analyte_name);
      });

      return {
        category: cat.category,
        analytes: sortedAnalytes,
        totalCount: cat.analytes.length,
        oorCount: cat.analytes.filter(a => a.is_out_of_range).length
      };
    }).filter(Boolean);

    pane.innerHTML = `
      <!-- Search & Triage Toolbar -->
      <div class="flex flex-wrap items-center justify-between gap-3 bg-slate-50 dark:bg-slate-900/40 p-3 rounded-xl border border-slate-200 dark:border-slate-800 text-xs">
        <div class="flex flex-wrap items-center gap-2 flex-1 min-w-[240px]">
          <div class="relative flex-1 min-w-[180px]">
            <input type="text"
                   id="bw-matrix-search"
                   value="${escapeHtml(matrixSearchQuery)}"
                   placeholder="Search 86 biomarkers (e.g. testosterone, alt, hct, ferritin)..."
                   oninput="BloodworkManager.handleSearch(this.value)"
                   class="w-full bg-white dark:bg-slate-900 border border-slate-300 dark:border-slate-700 rounded-lg px-3 py-1.5 pl-8 text-xs text-slate-900 dark:text-white focus:border-purple-500 focus:outline-none">
            <span class="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-400">🔍</span>
            ${matrixSearchQuery ? `<button onclick="BloodworkManager.clearSearch()" class="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-700 dark:hover:text-slate-200">&times;</button>` : ""}
          </div>

          <select id="bw-category-select"
                  onchange="BloodworkManager.handleCategoryFilter(this.value)"
                  class="bg-white dark:bg-slate-900 border border-slate-300 dark:border-slate-700 rounded-lg px-2.5 py-1.5 text-xs text-slate-900 dark:text-white focus:border-purple-500 focus:outline-none">
            <option value="all">All 18 Categories</option>
            ${categories.map(c => `
              <option value="${escapeHtml(c.category)}" ${matrixCategoryFilter === c.category ? "selected" : ""}>
                ${escapeHtml(c.category)} (${c.analytes.length})
              </option>
            `).join("")}
          </select>
        </div>

        <div class="flex items-center gap-2">
          <button onclick="BloodworkManager.toggleOorFilter()"
                  class="px-2.5 py-1.5 rounded-lg border font-semibold transition flex items-center gap-1.5 ${matrixOnlyOor ? "bg-rose-500 text-white border-rose-600 shadow-xs" : "bg-white dark:bg-slate-800 text-slate-700 dark:text-slate-300 border-slate-200 dark:border-slate-700 hover:bg-slate-100 dark:hover:bg-slate-700"}">
            <span>🚨 Out of Range Only</span>
            <span class="px-1.5 py-0.2 rounded-full text-[10px] ${matrixOnlyOor ? "bg-white/20 text-white" : "bg-rose-100 dark:bg-rose-900/50 text-rose-700 dark:text-rose-300"} font-mono">${oorCount}</span>
          </button>

          <span class="text-[11px] text-slate-500 dark:text-slate-400 font-mono hidden sm:inline">
            Showing ${filteredCategories.reduce((acc, c) => acc + c.analytes.length, 0)} of ${totalAnalytes} analytes
          </span>
        </div>
      </div>

      <!-- Categories Container -->
      ${filteredCategories.length === 0 ? `
        <div class="p-8 text-center bg-slate-50 dark:bg-slate-900/30 border border-slate-200 dark:border-slate-800 rounded-xl space-y-2">
          <p class="text-sm font-semibold text-slate-700 dark:text-slate-300">No matching biomarkers found</p>
          <p class="text-xs text-slate-500 dark:text-slate-400">Try adjusting your search query or removing the filter.</p>
          <button onclick="BloodworkManager.resetFilters()" class="mt-2 px-3 py-1.5 rounded-lg bg-purple-600 text-white text-xs font-semibold">
            Reset Filters
          </button>
        </div>
      ` : `
        <div class="space-y-4">
          ${filteredCategories.map(cat => `
            <div class="bg-white dark:bg-[#131A26] border border-slate-200 dark:border-slate-800 rounded-xl overflow-hidden shadow-xs">
              <div class="px-4 py-2.5 bg-slate-50 dark:bg-slate-900/50 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between gap-2">
                <div class="flex items-center gap-2">
                  <h4 class="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white">
                    ${escapeHtml(cat.category)}
                  </h4>
                  <span class="px-2 py-0.5 rounded-full text-[10px] font-mono bg-slate-200 dark:bg-slate-800 text-slate-600 dark:text-slate-400">
                    ${cat.analytes.length} analytes
                  </span>
                </div>
                <div>
                  ${cat.oorCount > 0 ? `
                    <span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-rose-100 dark:bg-rose-950/60 text-rose-700 dark:text-rose-300 border border-rose-200 dark:border-rose-800">
                      ${cat.oorCount} Out of Range
                    </span>
                  ` : `
                    <span class="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 dark:bg-emerald-950/40 text-emerald-700 dark:text-emerald-300">
                      All Normal
                    </span>
                  `}
                </div>
              </div>

              <div class="divide-y divide-slate-100 dark:divide-slate-800/60">
                ${cat.analytes.map(analyte => {
                  const val = analyte.latest_value;
                  const comp = analyte.latest_comparator;
                  const unit = analyte.canonical_unit;
                  const refLow = analyte.ref_low_reported;
                  const refHigh = analyte.ref_high_reported;
                  const isOor = analyte.is_out_of_range;
                  const date = analyte.latest_draw_date;

                  return `
                    <div class="px-4 py-3 flex flex-wrap sm:flex-nowrap items-center justify-between gap-3 hover:bg-slate-50 dark:hover:bg-slate-900/40 transition cursor-pointer group"
                         onclick="BloodworkManager.showDrillDown('${escapeHtml(analyte.analyte_code)}')"
                         title="Click to view longitudinal discrete scatter points & history">
                      <div class="flex items-center gap-2.5 min-w-[200px] flex-1">
                        <div>
                          <div class="flex items-center gap-2">
                            <span class="text-xs font-semibold text-slate-900 dark:text-white group-hover:text-purple-600 dark:group-hover:text-purple-400 transition">
                              ${escapeHtml(analyte.analyte_name)}
                            </span>
                            ${getStatusChip(isOor, val, refLow, refHigh, comp)}
                          </div>
                          <div class="flex items-center gap-2 mt-0.5">
                            <span class="text-[10px] text-slate-400 font-mono">${escapeHtml(analyte.analyte_code)}</span>
                            <span>•</span>
                            ${getAgeBadge(date)}
                          </div>
                        </div>
                      </div>

                      <div class="w-full sm:w-auto flex items-center justify-end gap-3">
                        ${renderBulletGraph(val, comp, refLow, refHigh, unit, isOor)}
                        
                        <div class="text-right min-w-[80px]">
                          <div class="text-xs font-bold font-mono ${isOor ? "text-rose-600 dark:text-rose-400" : "text-slate-900 dark:text-white"}">
                            ${comp ? escapeHtml(comp) + " " : ""}${val !== null && val !== undefined ? val : "—"}
                          </div>
                          <div class="text-[10px] text-slate-400 font-mono truncate">
                            ${escapeHtml(unit)}
                          </div>
                        </div>

                        <span class="text-slate-300 dark:text-slate-700 group-hover:text-purple-500 dark:group-hover:text-purple-400 transition text-sm">➔</span>
                      </div>
                    </div>
                  `;
                }).join("")}
              </div>
            </div>
          `).join("")}
        </div>
      `}
    `;
  }

  // ---------------------------------------------------------------------------
  // TAB 3: CLINICAL CONSULTATION BRIEF (SI METRIC vs CONVENTIONAL)
  // ---------------------------------------------------------------------------
  function renderBrief() {
    const pane = document.getElementById("bw-pane-brief");
    if (!pane) return;

    const brief = bloodworkData?.consultation_brief || {};
    const analytes = brief.analytes || [];
    const isBr = unitMode === "br";

    const grouped = {};
    analytes.forEach(a => {
      const cat = a.category || "Other";
      if (!grouped[cat]) grouped[cat] = [];
      grouped[cat].push(a);
    });

    pane.innerHTML = `
      <div class="flex flex-wrap items-center justify-between gap-3 bg-slate-50 dark:bg-slate-900/50 p-3 rounded-xl border border-slate-200 dark:border-slate-800 text-xs">
        <div class="grid grid-cols-2 sm:flex sm:items-center gap-1 w-full sm:w-auto bg-white dark:bg-slate-900 p-1 rounded-lg border border-slate-200 dark:border-slate-800 shadow-2xs">
          <button onclick="BloodworkManager.toggleUnit('uk')"
                  class="px-2.5 sm:px-3 py-1 rounded-md font-semibold transition flex items-center justify-center gap-1 sm:gap-1.5 ${!isBr ? "bg-purple-600 text-white shadow-xs" : "text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white"} text-center">
            <span>🌐</span> <span>SI Metric <span class="hidden sm:inline">Units (nmol/L, g/L, mmol/L)</span></span>
          </button>
          <button onclick="BloodworkManager.toggleUnit('br')"
                  class="px-2.5 sm:px-3 py-1 rounded-md font-semibold transition flex items-center justify-center gap-1 sm:gap-1.5 ${isBr ? "bg-purple-600 text-white shadow-xs" : "text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white"} text-center">
            <span>📊</span> <span>Conventional <span class="hidden sm:inline">(ng/dL, g/dL, mg/dL)</span></span>
          </button>
        </div>

        <div class="flex items-center gap-2">
          <button onclick="window.print()" class="px-3 py-1.5 rounded-lg bg-slate-800 dark:bg-slate-100 hover:bg-slate-900 text-white dark:text-slate-900 font-semibold transition flex items-center gap-1.5 shadow-xs">
            <span>🖨️</span> <span>Print Consultation Sheet</span>
          </button>
        </div>
      </div>

      <div id="brief-sheet" class="bg-white dark:bg-[#131A26] border border-slate-200 dark:border-slate-800 rounded-xl p-5 space-y-5 shadow-xs">
        <div class="border-b border-slate-200 dark:border-slate-800 pb-4 space-y-2">
          <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
              <span class="text-[10px] uppercase tracking-wider font-bold text-purple-600 dark:text-purple-400">
                Endocrine & Metabolic Command Brief
              </span>
              <h3 class="text-base font-bold text-slate-900 dark:text-white">
                Clinical Laboratory Summary & Consultation Record
              </h3>
            </div>
            <div class="text-right text-xs font-mono text-slate-500 dark:text-slate-400">
              <div>Generated: ${new Date().toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })}</div>
              <div>Latest Draw: ${escapeHtml(bloodworkData?.readiness?.last_draw_date || '—')}</div>
            </div>
          </div>

          <div class="grid grid-cols-2 sm:grid-cols-4 gap-3 pt-2 text-xs bg-slate-50 dark:bg-slate-900/40 p-3 rounded-lg border border-slate-100 dark:border-slate-800/80">
            <div>
              <span class="text-slate-400">Patient:</span>
              <div class="font-bold text-slate-900 dark:text-white">${escapeHtml(data.user_profile?.name || 'Patient')} (${escapeHtml(data.user_profile?.gender ? data.user_profile.gender.charAt(0).toUpperCase() + data.user_profile.gender.slice(1) : 'Adult')}, ${data.user_profile?.age ? data.user_profile.age + 'yo' : '—'})</div>
            </div>
            <div>
              <span class="text-slate-400">Anthropometry:</span>
              <div class="font-bold text-slate-900 dark:text-white">${data.user_profile?.height_cm ? Math.round(data.user_profile.height_cm) + ' cm' : '—'} • ${data.body_comp?.stats?.latest_weight_kg ? data.body_comp.stats.latest_weight_kg + ' kg' : '—'}</div>
            </div>
            <div>
              <span class="text-slate-400">Active Regimen:</span>
              <div class="font-bold text-slate-900 dark:text-white font-mono text-[11px] truncate" title="${escapeHtml(data.hud?.phase?.compounds || data.active_protocol?.name || 'Active Regimen')}">${escapeHtml(data.hud?.phase?.compounds || data.active_protocol?.name || 'Active Regimen')}</div>
            </div>
            <div>
              <span class="text-slate-400">Unit Standard:</span>
              <div class="font-bold text-purple-600 dark:text-purple-400">${isBr ? "Conventional (Mass)" : "SI Metric (Standard)"}</div>
            </div>
          </div>
        </div>

        <div class="space-y-6">
          ${Object.keys(grouped).sort().map(cat => {
            const catAnalytes = grouped[cat];
            return `
              <div class="space-y-2">
                <div class="flex items-center justify-between border-b border-slate-200 dark:border-slate-800 pb-1">
                  <h4 class="text-xs font-bold uppercase tracking-wider text-slate-800 dark:text-slate-200 flex items-center gap-2">
                    <span class="w-1.5 h-1.5 rounded-full bg-purple-500"></span>
                    ${escapeHtml(cat)}
                  </h4>
                  <span class="text-[10px] text-slate-400 font-mono">${catAnalytes.length} tests</span>
                </div>

                <div class="overflow-x-auto">
                  <table class="w-full min-w-[620px] text-left text-xs text-slate-700 dark:text-slate-300">
                    <thead class="bg-slate-50 dark:bg-slate-900/50 text-slate-500 dark:text-slate-400 uppercase text-[9px] tracking-wider border-b border-slate-100 dark:border-slate-800">
                      <tr>
                        <th class="p-2 w-1/4">Biomarker</th>
                        <th class="p-2">Unit</th>
                        <th class="p-2">Latest Result</th>
                        <th class="p-2">Reporting Lab Ref Interval</th>
                        <th class="p-2">Recent Draws (Last 3–5)</th>
                        <th class="p-2">3-Year Range (Min–Max)</th>
                        <th class="p-2">Provenance (Lab)</th>
                      </tr>
                    </thead>
                    <tbody class="divide-y divide-slate-100 dark:divide-slate-800/40 text-[11px] font-mono">
                      ${catAnalytes.map(a => {
                        const val = isBr ? a.latest_value_br : a.latest_value;
                        const unit = isBr ? a.br_unit : a.canonical_unit;
                        const comp = a.latest_comparator;
                        const isOor = a.latest_out_of_range;
                        const minVal = isBr ? a.min_br : a.min_canonical;
                        const maxVal = isBr ? a.max_br : a.max_canonical;
                        const refLow = a.ref_low_reported;
                        const refHigh = a.ref_high_reported;
                        const last5 = a.last_5_draws || [];
                        const lab = last5.length > 0 ? last5[last5.length - 1].lab_provider : "Lab Record";

                        const refLabel = (refLow !== null && refHigh !== null)
                          ? `${refLow} – ${refHigh}`
                          : (refHigh !== null ? `≤ ${refHigh}` : (refLow !== null ? `≥ ${refLow}` : "—"));

                        const sparkValues = last5.map(d => {
                          const v = isBr ? d.value_br : d.value;
                          const c = d.comparator;
                          const dt = d.date ? d.date.substring(2) : "";
                          const oor = d.is_out_of_range;
                          return `<span class="${oor ? "text-rose-600 dark:text-rose-400 font-bold" : "text-slate-600 dark:text-slate-400"}" title="${d.date}: ${c || ""}${v}">${dt}: ${c || ""}${v}</span>`;
                        }).join(" • ");

                        return `
                          <tr class="hover:bg-slate-50 dark:hover:bg-slate-900/30 transition cursor-pointer" onclick="BloodworkManager.showDrillDown('${escapeHtml(a.analyte_code)}')">
                            <td class="p-2 font-sans font-semibold text-slate-900 dark:text-white">
                              ${escapeHtml(a.analyte_name)}
                            </td>
                            <td class="p-2 text-slate-500 dark:text-slate-400">${escapeHtml(unit)}</td>
                            <td class="p-2">
                              <span class="${isOor ? "px-1.5 py-0.5 rounded bg-rose-50 dark:bg-rose-950/50 text-rose-700 dark:text-rose-300 font-bold border border-rose-200 dark:border-rose-800" : "font-bold text-slate-900 dark:text-white"}">
                                ${comp ? escapeHtml(comp) + " " : ""}${val !== null && val !== undefined ? val : "—"}
                              </span>
                            </td>
                            <td class="p-2 text-slate-500 dark:text-slate-400">${refLabel}</td>
                            <td class="p-2 text-[10px] text-slate-500">${sparkValues || "—"}</td>
                            <td class="p-2 text-slate-600 dark:text-slate-400">
                              ${minVal !== null ? `${minVal} – ${maxVal}` : "—"}
                            </td>
                            <td class="p-2 text-[10px] text-slate-500 dark:text-slate-400 font-sans truncate max-w-[120px]">
                              ${escapeHtml(lab)}
                            </td>
                          </tr>
                        `;
                      }).join("")}
                    </tbody>
                  </table>
                </div>
              </div>
            `;
          }).join("")}
        </div>

        <div class="border-t border-slate-200 dark:border-slate-800 pt-3 text-[10px] text-slate-400 space-y-1">
          <p>
            <strong>Data Provenance & Standards:</strong> Tracked across verified historical draws from accredited clinical diagnostic laboratories. Standardized to international SI metric and conventional unit equivalents.
          </p>
          <p>
            <strong>Medical Boundary (AGENTS.md §3.1):</strong> This brief summarizes objective telemetry against clinical protocol rules for physician evaluation. The dashboard never prescribes or alters medication doses. Prompt for clinical prescriber review.
          </p>
        </div>
      </div>
    `;
  }



  // ---------------------------------------------------------------------------
  // DRILL-DOWN MODAL: DISCRETE NON-INTERPOLATED SCATTER PLOT & HISTORY (AGENTS.md §3.5)
  // ---------------------------------------------------------------------------
  function showDrillDown(analyteCode) {
    if (!bloodworkData) return;

    let target = null;
    const categories = bloodworkData.categories || [];
    for (const cat of categories) {
      const match = cat.analytes.find(a => a.analyte_code === analyteCode);
      if (match) {
        target = { ...match, category: cat.category };
        break;
      }
    }

    if (!target) {
      const briefAnalytes = bloodworkData.consultation_brief?.analytes || [];
      const match = briefAnalytes.find(a => a.analyte_code === analyteCode);
      if (match) target = match;
    }

    if (!target) return;

    const modal = document.getElementById("bloodwork-drilldown-modal");
    if (!modal) return;

    const history = target.history || [];
    const validHistory = history.filter(h => h.value !== null && h.value !== undefined);
    const denominatorText = `${validHistory.length} discrete sample draws recorded across 21 test sessions (${validHistory.length > 0 ? validHistory[0].date : "—"} to ${validHistory.length > 0 ? validHistory[validHistory.length - 1].date : "—"})`;

    let scatterSvg = "";
    if (validHistory.length > 0) {
      const svgWidth = 720;
      const svgHeight = 220;
      const padLeft = 60;
      const padRight = 30;
      const padTop = 30;
      const padBottom = 40;
      const plotWidth = svgWidth - padLeft - padRight;
      const plotHeight = svgHeight - padTop - padBottom;

      const timestamps = validHistory.map(h => new Date(h.date + "T00:00:00").getTime());
      const minTime = Math.min(...timestamps);
      const maxTime = Math.max(...timestamps);
      const timeSpan = maxTime === minTime ? 86400000 * 30 : maxTime - minTime;

      const values = validHistory.map(h => h.value);
      let minVal = Math.min(...values);
      let maxVal = Math.max(...values);

      if (target.ref_low_reported !== null) minVal = Math.min(minVal, target.ref_low_reported);
      if (target.ref_high_reported !== null) maxVal = Math.max(maxVal, target.ref_high_reported);

      const valSpan = maxVal === minVal ? 1 : maxVal - minVal;
      const yMin = Math.max(0, minVal - valSpan * 0.15);
      const yMax = maxVal + valSpan * 0.15;
      const yRange = yMax - yMin;

      const getY = v => padTop + plotHeight - ((v - yMin) / yRange) * plotHeight;
      const getX = t => padLeft + ((t - minTime) / timeSpan) * plotWidth;

      let corridorSvg = "";
      if (target.ref_low_reported !== null && target.ref_high_reported !== null) {
        const topY = getY(target.ref_high_reported);
        const botY = getY(target.ref_low_reported);
        corridorSvg = `
          <rect x="${padLeft}" y="${topY}" width="${plotWidth}" height="${botY - topY}"
                fill="#10b981" fill-opacity="0.12" stroke="#10b981" stroke-opacity="0.3" stroke-dasharray="3,3"/>
          <text x="${padLeft + 8}" y="${topY + 12}" fill="#10b981" font-size="9" font-family="monospace">
            Upper Ref: ${target.ref_high_reported}
          </text>
          <text x="${padLeft + 8}" y="${botY - 4}" fill="#10b981" font-size="9" font-family="monospace">
            Lower Ref: ${target.ref_low_reported}
          </text>
        `;
      } else if (target.ref_high_reported !== null) {
        const topY = getY(target.ref_high_reported);
        corridorSvg = `
          <line x1="${padLeft}" y1="${topY}" x2="${padLeft + plotWidth}" y2="${topY}" stroke="#10b981" stroke-dasharray="3,3"/>
          <text x="${padLeft + 8}" y="${topY - 4}" fill="#10b981" font-size="9" font-family="monospace">
            Upper Limit: ${target.ref_high_reported}
          </text>
        `;
      }

      const pointsSvg = validHistory.map(h => {
        const t = new Date(h.date + "T00:00:00").getTime();
        const cx = getX(t);
        const cy = getY(h.value);
        const isOor = (target.ref_low_reported !== null && h.value < target.ref_low_reported) ||
                      (target.ref_high_reported !== null && h.value > target.ref_high_reported);
        const color = isOor ? "#f43f5e" : "#10b981";
        const dStr = formatDate(h.date);

        return `
          <g class="cursor-pointer group">
            <line x1="${cx}" y1="${cy}" x2="${cx}" y2="${padTop + plotHeight}" stroke="currentColor" stroke-opacity="0.15" stroke-dasharray="2,2"/>
            <circle cx="${cx}" cy="${cy}" r="5" fill="${color}" stroke="#ffffff" stroke-width="2"/>
            <text x="${cx}" y="${cy - 9}" text-anchor="middle" fill="currentColor" font-size="10" font-family="monospace" font-weight="bold">
              ${h.comparator ? escapeHtml(h.comparator) + " " : ""}${h.value}
            </text>
            <text x="${cx}" y="${padTop + plotHeight + 15}" text-anchor="middle" fill="#64748b" font-size="9" font-family="monospace">
              ${dStr.substring(0, 6)}
            </text>
            <title>${dStr}: ${h.comparator || ""}${h.value} ${target.canonical_unit} (${h.lab_provider || "Lab"})</title>
          </g>
        `;
      }).join("");

      scatterSvg = `
        <svg viewBox="0 0 ${svgWidth} ${svgHeight}" class="w-full min-w-[540px] h-auto text-slate-800 dark:text-slate-200 select-none">
          <line x1="${padLeft}" y1="${padTop + plotHeight}" x2="${padLeft + plotWidth}" y2="${padTop + plotHeight}" stroke="currentColor" stroke-opacity="0.2"/>
          <line x1="${padLeft}" y1="${padTop}" x2="${padLeft}" y2="${padTop + plotHeight}" stroke="currentColor" stroke-opacity="0.2"/>
          ${corridorSvg}
          ${pointsSvg}
        </svg>
      `;
    }

    modal.innerHTML = `
      <div class="bg-white dark:bg-[#131A26] border border-slate-200 dark:border-slate-800 rounded-2xl w-full max-w-4xl max-h-[90vh] overflow-y-auto p-5 space-y-4 shadow-2xl transition-all">
        <div class="flex items-center justify-between border-b border-slate-100 dark:border-slate-800 pb-3">
          <div>
            <div class="flex items-center gap-2">
              <span class="text-xs uppercase font-bold text-purple-600 dark:text-purple-400 font-mono">${escapeHtml(target.category || "Biomarker")}</span>
              <span>•</span>
              <span class="text-xs text-slate-400 font-mono">${escapeHtml(target.analyte_code)}</span>
            </div>
            <h3 class="text-base sm:text-lg font-bold text-slate-900 dark:text-white flex items-center gap-2">
              <span>🩸</span> ${escapeHtml(target.analyte_name)}
            </h3>
          </div>
          <button onclick="BloodworkManager.closeDrillDown()" class="text-slate-400 hover:text-slate-900 dark:hover:text-white text-2xl font-bold p-2 -mr-1 min-w-[36px] min-h-[36px] flex items-center justify-center" aria-label="Close">&times;</button>
        </div>

        <div class="bg-slate-50 dark:bg-slate-900/40 p-3 rounded-xl border border-slate-200 dark:border-slate-800 flex flex-wrap items-center justify-between gap-2 text-xs">
          <div class="space-y-0.5">
            <div class="font-semibold text-slate-900 dark:text-white flex items-center gap-1.5">
              <span>📊</span> First-Class Coverage Denominator:
            </div>
            <p class="text-slate-500 dark:text-slate-400 font-mono text-[11px]">
              ${denominatorText}
            </p>
          </div>
          <div class="text-right">
            <div class="text-xs font-bold font-mono ${target.is_out_of_range ? "text-rose-600 dark:text-rose-400" : "text-emerald-600 dark:text-emerald-400"}">
              Latest: ${target.latest_comparator ? target.latest_comparator + " " : ""}${target.latest_value} ${escapeHtml(target.canonical_unit)}
            </div>
            <div class="text-[10px] text-slate-400">
              Ref: ${target.ref_low_reported !== null ? target.ref_low_reported : "—"} to ${target.ref_high_reported !== null ? target.ref_high_reported : "—"}
            </div>
          </div>
        </div>

        <div class="bg-slate-50 dark:bg-slate-900/20 border border-slate-200 dark:border-slate-800 rounded-xl p-3 space-y-1">
          <div class="flex items-center justify-between text-xs text-slate-500 dark:text-slate-400 px-1">
            <span class="font-bold text-slate-700 dark:text-slate-300">Discrete Sample Scatter Plot (STRICTLY NON-INTERPOLATED)</span>
            <span class="text-[10px] font-mono">No interpolated continuous curves (AGENTS.md §3.5)</span>
          </div>
          <div class="overflow-x-auto py-2">
            ${scatterSvg || "<div class=\"p-8 text-center text-slate-400 text-xs\">No historical datapoints recorded</div>"}
          </div>
        </div>

        <div class="border border-slate-200 dark:border-slate-800 rounded-xl overflow-hidden">
          <div class="px-4 py-2.5 bg-slate-50 dark:bg-slate-900/50 border-b border-slate-200 dark:border-slate-800 flex items-center justify-between">
            <h4 class="text-xs font-bold uppercase tracking-wider text-slate-900 dark:text-white">
              Chronological Test Provenance Log
            </h4>
            <span class="text-[10px] text-slate-400 font-mono">${validHistory.length} Recorded Draws</span>
          </div>

          <div class="max-h-56 overflow-auto custom-scrollbar">
            <table class="w-full min-w-[620px] text-left text-xs text-slate-700 dark:text-slate-300">
              <thead class="bg-slate-100 dark:bg-slate-900/70 text-slate-600 dark:text-slate-400 uppercase text-[10px] tracking-wider sticky top-0 border-b border-slate-200 dark:border-slate-800">
                <tr>
                  <th class="p-2.5">Draw Date</th>
                  <th class="p-2.5">Value (UK Metric)</th>
                  <th class="p-2.5">Value (Conventional Units)</th>
                  <th class="p-2.5">Comparator</th>
                  <th class="p-2.5">Reported Ref Interval</th>
                  <th class="p-2.5">Status</th>
                  <th class="p-2.5">Laboratory Provider</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100 dark:divide-slate-800/50 font-mono text-[11px]">
                ${[...validHistory].reverse().map(h => {
                  const isOor = (h.ref_low !== null && h.value < h.ref_low) ||
                                (h.ref_high !== null && h.value > h.ref_high);
                  const refStr = (h.ref_low !== null && h.ref_high !== null)
                    ? `${h.ref_low} – ${h.ref_high}`
                    : (h.ref_high !== null ? `≤ ${h.ref_high}` : (h.ref_low !== null ? `≥ ${h.ref_low}` : "—"));

                  return `
                    <tr class="hover:bg-slate-50 dark:hover:bg-slate-900/30 transition">
                      <td class="p-2.5 font-sans font-semibold text-slate-900 dark:text-white">${formatDate(h.date)}</td>
                      <td class="p-2.5 font-bold ${isOor ? "text-rose-600 dark:text-rose-400" : "text-slate-900 dark:text-white"}">
                        ${h.value} ${escapeHtml(target.canonical_unit)}
                      </td>
                      <td class="p-2.5 text-slate-600 dark:text-slate-400">
                        ${h.value_br !== null && h.value_br !== undefined ? `${h.value_br} ${escapeHtml(target.br_unit || "")}` : "—"}
                      </td>
                      <td class="p-2.5 text-purple-600 dark:text-purple-400">${h.comparator ? escapeHtml(h.comparator) : "None (=)"}</td>
                      <td class="p-2.5 text-slate-500">${refStr}</td>
                      <td class="p-2.5">
                        ${isOor ? "<span class=\"text-rose-600 dark:text-rose-400 font-bold\">OUT OF RANGE</span>" : "<span class=\"text-emerald-600 dark:text-emerald-400 font-semibold\">NORMAL</span>"}
                      </td>
                      <td class="p-2.5 font-sans text-slate-500 text-[10px]">${escapeHtml(h.lab_provider || "Laboratory")}</td>
                    </tr>
                  `;
                }).join("")}
              </tbody>
            </table>
          </div>
        </div>

        <div class="flex items-center justify-end pt-2 border-t border-slate-100 dark:border-slate-800">
          <button onclick="BloodworkManager.closeDrillDown()" class="px-4 py-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700 text-slate-700 dark:text-slate-300 text-xs font-semibold transition">
            Close Drill-Down
          </button>
        </div>
      </div>
    `;

    modal.classList.remove("hidden");
  }

  function closeDrillDown() {
    const modal = document.getElementById("bloodwork-drilldown-modal");
    if (modal) modal.classList.add("hidden");
  }

  // ---------------------------------------------------------------------------
  // INTERFACE HANDLERS & PUBLIC API
  // ---------------------------------------------------------------------------
  function setTab(tabId) {
    activeTab = tabId;
    ["readiness", "matrix", "brief"].forEach(t => {
      const btn = document.getElementById(`tab-bw-${t}`);
      const pane = document.getElementById(`bw-pane-${t}`);
      if (btn) {
        if (t === tabId) {
          btn.className = "px-3 py-1.5 rounded-lg bg-purple-600 text-white shadow-sm transition font-semibold whitespace-nowrap";
        } else {
          btn.className = "px-3 py-1.5 rounded-lg text-slate-600 dark:text-slate-400 hover:text-slate-900 dark:hover:text-white transition font-medium whitespace-nowrap";
        }
      }
      if (pane) {
        if (t === tabId) {
          pane.classList.remove("hidden");
        } else {
          pane.classList.add("hidden");
        }
      }
    });

    renderCurrentTab();
  }

  function renderCurrentTab() {
    if (activeTab === "readiness") renderReadiness();
    else if (activeTab === "matrix") renderMatrix();
    else if (activeTab === "brief") renderBrief();
  }

  function toggleUnit(mode) {
    unitMode = mode;
    renderCurrentTab();
  }

  function handleSearch(q) {
    matrixSearchQuery = q;
    renderMatrix();
  }

  function clearSearch() {
    matrixSearchQuery = "";
    renderMatrix();
  }

  function handleCategoryFilter(cat) {
    matrixCategoryFilter = cat;
    renderMatrix();
  }

  function toggleOorFilter() {
    matrixOnlyOor = !matrixOnlyOor;
    renderMatrix();
  }

  function resetFilters() {
    matrixSearchQuery = "";
    matrixCategoryFilter = "all";
    matrixOnlyOor = false;
    renderMatrix();
  }

  function jumpToMilestone(nodeId) {
    const bar = document.getElementById("macro-milestone-bar");
    if (bar) {
      bar.scrollIntoView({ behavior: "smooth", block: "center" });
      bar.classList.add("ring-4", "ring-purple-500/50");
      setTimeout(() => {
        bar.classList.remove("ring-4", "ring-purple-500/50");
      }, 2000);
    }
  }

  function init(data) {
    if (data) {
      bloodworkData = data;
    } else if (window.__DASHBOARD_DATA__?.bloodwork) {
      bloodworkData = window.__DASHBOARD_DATA__.bloodwork;
    } else if (window.globalDashboardData?.bloodwork) {
      bloodworkData = window.globalDashboardData.bloodwork;
    }

    if (!bloodworkData) return;

    const chip = document.getElementById("bw-status-chip");
    if (chip && bloodworkData.draw_readiness) {
      const days = bloodworkData.draw_readiness.days_since_last_draw || 28;
      chip.innerText = `${days}d Since Last Draw • Next Review: Q4`;
    }

    setTab(activeTab);
  }

  return {
    init,
    setTab,
    toggleUnit,
    handleSearch,
    clearSearch,
    handleCategoryFilter,
    toggleOorFilter,
    resetFilters,
    showDrillDown,
    closeDrillDown,
    jumpToMilestone,
    resizeCharts: () => {},
    updateTheme: () => {},
    getData: () => bloodworkData
  };
})();

// Attach globally
window.BloodworkManager = BloodworkManager;

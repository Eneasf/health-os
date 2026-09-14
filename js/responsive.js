/**
 * Personal Health Dashboard — Responsive Architecture & Mobile Reframing Engine (ADR-025)
 * 
 * Manages:
 * 1. Mobile & tablet orientation change lifecycle (screen.orientation & orientationchange)
 * 2. Two-phase layout reframing (RAF immediate + 150ms settling timeout for mobile OS bar transitions)
 * 3. Dynamic header & sticky dock offset synchronization with zero layout jitter
 * 4. Chart.js dimension reflow coordinator (ChartsManager & BloodworkManager canvas reflow)
 * 5. Short-landscape viewport detection and dynamic constraint application
 * 6. Mobile Studio drawer state toggle
 */

const ResponsiveManager = (() => {
  let resizeTimeout = null;
  let orientationTimeout = null;

  function isMobileLandscape() {
    const isLandscape = window.matchMedia('(orientation: landscape)').matches || (window.innerWidth > window.innerHeight);
    return isLandscape && window.innerHeight < 520;
  }

  function updateLandscapeClass() {
    if (isMobileLandscape()) {
      document.body.classList.add('is-mobile-landscape');
    } else {
      document.body.classList.remove('is-mobile-landscape');
    }
  }

  function syncHeaderOffset() {
    const header = document.getElementById('main-header') || document.querySelector('header');
    const banner = document.getElementById('layout-control-banner');
    if (header && banner) {
      const headerHeight = header.offsetHeight;
      banner.style.top = `${headerHeight}px`;
    }
  }

  function reflowCharts() {
    if (window.ChartsManager && typeof window.ChartsManager.resizeCharts === 'function') {
      window.ChartsManager.resizeCharts();
    }
    if (window.BloodworkManager && typeof window.BloodworkManager.resizeCharts === 'function') {
      window.BloodworkManager.resizeCharts();
    }
  }

  function handleReflow() {
    updateLandscapeClass();
    syncHeaderOffset();
    reflowCharts();

    if (window.MatrixManager && typeof window.MatrixManager.scrollToTodayOnMobile === 'function') {
      window.MatrixManager.scrollToTodayOnMobile();
    }
  }

  function onViewportChange() {
    // Phase 1: Immediate RAF reframing
    requestAnimationFrame(() => {
      handleReflow();
    });

    // Phase 2: Settling timeout to capture mobile browser dynamic URL bars & rotation completion
    if (orientationTimeout) clearTimeout(orientationTimeout);
    orientationTimeout = setTimeout(() => {
      handleReflow();
    }, 150);
  }

  function onWindowResize() {
    if (resizeTimeout) clearTimeout(resizeTimeout);
    resizeTimeout = setTimeout(() => {
      onViewportChange();
    }, 100);
  }

  function toggleMobileStudioMenu() {
    const drawer = document.getElementById('mobile-studio-drawer');
    const btn = document.getElementById('btn-mobile-studio');
    if (!drawer) return;

    const isHidden = drawer.classList.contains('hidden');
    if (isHidden) {
      drawer.classList.remove('hidden');
      if (btn) btn.classList.add('bg-slate-200', 'dark:bg-slate-700');
    } else {
      drawer.classList.add('hidden');
      if (btn) btn.classList.remove('bg-slate-200', 'dark:bg-slate-700');
    }
    syncHeaderOffset();
  }

  function init() {
    updateLandscapeClass();
    syncHeaderOffset();

    // 1. Orientation Change Listeners
    if (window.screen && window.screen.orientation && typeof window.screen.orientation.addEventListener === 'function') {
      window.screen.orientation.addEventListener('change', onViewportChange);
    }
    window.addEventListener('orientationchange', onViewportChange);

    // 2. Debounced Window Resize Listener
    window.addEventListener('resize', onWindowResize);

    // 3. ResizeObserver on main container for granular DOM card toggles
    const mainContainer = document.getElementById('main-container');
    if (window.ResizeObserver && mainContainer) {
      const ro = new ResizeObserver(() => {
        syncHeaderOffset();
      });
      ro.observe(mainContainer);
    }

    // Initial reflow check
    setTimeout(() => {
      handleReflow();
    }, 60);
  }

  return {
    init,
    syncHeaderOffset,
    reflowCharts,
    handleReflow,
    toggleMobileStudioMenu,
    isMobileLandscape
  };
})();

// Attach globally
window.ResponsiveManager = ResponsiveManager;
window.toggleMobileStudioMenu = ResponsiveManager.toggleMobileStudioMenu;

/**
 * Theme Engine Module (ADR-005)
 * Manages Clinical Light Mode (Default: #F8FAFC) & Refined Dark Mode (#0B0F17)
 * Synchronizes DOM state, localStorage persistence, and Chart.js theme variables.
 */

const ThemeManager = (() => {
  const STORAGE_KEY = 'health_dashboard_theme';

  function getStoredTheme() {
    return localStorage.getItem(STORAGE_KEY) || 'light';
  }

  function isDarkMode() {
    return document.documentElement.classList.contains('dark');
  }

  function applyTheme(theme) {
    const isDark = theme === 'dark';
    const root = document.documentElement;

    if (isDark) {
      root.classList.add('dark');
      root.classList.remove('light');
    } else {
      root.classList.remove('dark');
      root.classList.add('light');
    }

    const iconEl = document.getElementById('theme-icon');
    const textEl = document.getElementById('theme-text');
    if (iconEl) {
      iconEl.innerHTML = isDark
        ? `<svg class="w-3.5 h-3.5 inline" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z"></path></svg>`
        : `<svg class="w-3.5 h-3.5 inline" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z"></path></svg>`;
    }
    if (textEl) textEl.innerText = isDark ? 'Light' : 'Dark';

    localStorage.setItem(STORAGE_KEY, theme);

    // Synchronize Chart.js theme if ChartsManager is loaded
    if (window.ChartsManager && typeof window.ChartsManager.updateTheme === 'function') {
      window.ChartsManager.updateTheme(isDark);
    }
  }

  function toggleTheme() {
    const currentlyDark = isDarkMode();
    applyTheme(currentlyDark ? 'light' : 'dark');
  }

  return {
    getStoredTheme,
    isDarkMode,
    applyTheme,
    toggleTheme
  };
})();

// Attach globally for inline event handlers and external script compatibility
window.ThemeManager = ThemeManager;
window.getStoredTheme = ThemeManager.getStoredTheme;
window.applyTheme = ThemeManager.applyTheme;
window.toggleTheme = ThemeManager.toggleTheme;

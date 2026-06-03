/**
 * Smartshala Theme Toggle
 * Self-injecting dark/light theme switcher.
 * Include this script on any page to get a floating toggle button.
 * Persists preference in localStorage.
 */
(function () {
  'use strict';

  const STORAGE_KEY = 'smartshala_theme';

  // Apply saved theme ASAP (before paint) to avoid flash
  const saved = localStorage.getItem(STORAGE_KEY);
  if (saved === 'dark') {
    document.documentElement.setAttribute('data-theme', 'dark');
  }

  // Create toggle button once DOM is ready
  function init() {
    const btn = document.createElement('button');
    btn.className = 'theme-toggle-btn';
    btn.setAttribute('aria-label', 'Toggle dark mode');
    btn.setAttribute('title', 'Toggle dark/light theme');

    function updateIcon() {
      const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
      btn.innerHTML = isDark
        ? '<span class="material-icons">light_mode</span>'
        : '<span class="material-icons">dark_mode</span>';
    }

    btn.addEventListener('click', function () {
      const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
      if (isDark) {
        document.documentElement.removeAttribute('data-theme');
        localStorage.setItem(STORAGE_KEY, 'light');
      } else {
        document.documentElement.setAttribute('data-theme', 'dark');
        localStorage.setItem(STORAGE_KEY, 'dark');
      }
      updateIcon();
    });

    updateIcon();
    document.body.appendChild(btn);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();

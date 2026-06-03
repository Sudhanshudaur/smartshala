// Smartshala — global utilities
(function () {
  const navToggle = document.getElementById('nav-toggle');
  const nav = document.getElementById('main-nav');
  const contactForm = document.getElementById('contact-form');

  function toggleNav() { nav && nav.classList.toggle('open'); }
  if (navToggle) navToggle.addEventListener('click', toggleNav);

  // ── Toast notification system ────────────────────────────────────────────
  function showToast(msg, type = 'success') {
    let container = document.getElementById('ss-toast-container');
    if (!container) {
      container = document.createElement('div');
      container.id = 'ss-toast-container';
      container.style.cssText = 'position:fixed;bottom:1.5rem;right:1.5rem;z-index:9999;display:flex;flex-direction:column;gap:.5rem;max-width:320px';
      document.body.appendChild(container);
    }
    const colors = { success: '#10b981', error: '#ef4444', warning: '#f59e0b', info: '#6366f1' };
    const el = document.createElement('div');
    el.style.cssText = `background:${colors[type]||colors.info};color:#fff;padding:.75rem 1rem;border-radius:.75rem;font-size:.9rem;box-shadow:0 4px 12px rgba(0,0,0,.15);display:flex;align-items:center;gap:.5rem;animation:ssSlideIn .25s ease`;
    const icons = { success: 'check_circle', error: 'error', warning: 'warning', info: 'info' };
    el.innerHTML = `<span class="material-icons" style="font-size:18px">${icons[type]||'info'}</span><span style="flex:1">${msg}</span><button onclick="this.parentElement.remove()" style="background:none;border:none;color:#fff;cursor:pointer;padding:0;line-height:1;font-size:18px">&times;</button>`;
    container.appendChild(el);
    setTimeout(() => el && el.remove(), 4000);
  }

  // Inject toast animation once
  if (!document.getElementById('ss-toast-style')) {
    const s = document.createElement('style');
    s.id = 'ss-toast-style';
    s.textContent = '@keyframes ssSlideIn{from{opacity:0;transform:translateX(20px)}to{opacity:1;transform:none}}';
    document.head.appendChild(s);
  }

  // ── Offline detector ─────────────────────────────────────────────────────
  window.addEventListener('offline', () => showToast('No internet connection', 'warning'));
  window.addEventListener('online',  () => showToast('Back online', 'success'));

  // ── Central fetch wrapper — handles 401 globally ─────────────────────────
  window.apiFetch = async function(url, options = {}) {
    try {
      const res = await fetch(url, options);
      if (res.status === 401) {
        showToast('Session expired. Logging out…', 'warning');
        setTimeout(() => {
          localStorage.removeItem('smartshala_auth');
          sessionStorage.removeItem('smartshala_auth');
          location.href = 'login.html';
        }, 1200);
        return null;
      }
      return res;
    } catch (err) {
      if (!navigator.onLine) showToast('No internet connection', 'warning');
      else showToast('Network error. Please try again.', 'error');
      return null;
    }
  };

  // ── Button loading helper ─────────────────────────────────────────────────
  window.btnLoading = function(btn, isLoading, originalHTML) {
    if (isLoading) {
      btn._original = btn.innerHTML;
      btn.disabled = true;
      btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>';
    } else {
      btn.disabled = false;
      btn.innerHTML = originalHTML !== undefined ? originalHTML : (btn._original || btn.innerHTML);
    }
  };

  // ── Contact form ──────────────────────────────────────────────────────────
  if (contactForm) {
    contactForm.addEventListener('submit', (e) => {
      e.preventDefault();
      const name = document.getElementById('contact-name').value.trim();
      const email = document.getElementById('contact-email').value.trim();
      const message = document.getElementById('contact-message').value.trim();
      if (!name || !email || !message) { Smartshala.alert('Please complete the form'); return; }
      const messages = JSON.parse(localStorage.getItem('smartshala_messages') || '[]');
      messages.push({ name, email, message, ts: Date.now() });
      localStorage.setItem('smartshala_messages', JSON.stringify(messages));
      showToast('Message sent — thank you!');
      contactForm.reset();
    });
  }

  // ── Global Modal System ───────────────────────────────────────────────────
  const modalHTML = `
    <div id="smart-modal" class="smart-modal-overlay">
      <div class="smart-modal-content">
        <h3 id="smart-modal-title" class="smart-modal-title"></h3>
        <p id="smart-modal-message" class="smart-modal-message"></p>
        <input type="text" id="smart-modal-input" class="smart-modal-input" style="display:none">
        <div id="smart-modal-actions" class="smart-modal-actions"></div>
      </div>
    </div>
  `;
  if (!document.getElementById('smart-modal')) {
    document.body.insertAdjacentHTML('beforeend', modalHTML);
  }

  const modal     = document.getElementById('smart-modal');
  const titleEl   = document.getElementById('smart-modal-title');
  const msgEl     = document.getElementById('smart-modal-message');
  const inputEl   = document.getElementById('smart-modal-input');
  const actionsEl = document.getElementById('smart-modal-actions');

  function showModal(title, message, type = 'alert', placeholder = '') {
    return new Promise((resolve) => {
      titleEl.textContent = message;
      msgEl.textContent   = '';
      inputEl.value       = '';
      inputEl.style.display = type === 'prompt' ? 'block' : 'none';
      if (type === 'prompt' && placeholder) inputEl.placeholder = placeholder;
      actionsEl.innerHTML = '';

      const close = (val) => {
        modal.classList.remove('active');
        setTimeout(() => resolve(val), 300);
      };
      if (type === 'confirm' || type === 'prompt') {
        const cancelBtn = document.createElement('button');
        cancelBtn.className = 'smart-modal-btn smart-modal-btn-secondary';
        cancelBtn.textContent = 'Cancel';
        cancelBtn.onclick = () => close(null);
        actionsEl.appendChild(cancelBtn);
      }
      const okBtn = document.createElement('button');
      okBtn.className = 'smart-modal-btn smart-modal-btn-primary';
      okBtn.textContent = type === 'prompt' ? 'Submit' : 'OK';
      okBtn.onclick = () => close(type === 'prompt' ? inputEl.value : true);
      actionsEl.appendChild(okBtn);
      if (type === 'prompt') inputEl.onkeydown = (e) => { if (e.key === 'Enter') okBtn.click(); };
      modal.classList.add('active');
      if (type === 'prompt') setTimeout(() => inputEl.focus(), 50);
    });
  }

  // ── Coming Soon Modal ─────────────────────────────────────────────────────
  if (!document.getElementById('coming-soon-modal')) {
    document.body.insertAdjacentHTML('beforeend', `
      <div class="modal fade" id="coming-soon-modal" tabindex="-1" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered">
          <div class="modal-content rounded-4 border-0 shadow-lg text-center p-4">
            <div class="mb-3"><span class="material-icons text-primary" style="font-size:3rem">rocket_launch</span></div>
            <h4 class="fw-bold mb-2">Feature Coming Soon</h4>
            <p class="text-muted mb-4">This feature is under development. Stay tuned!</p>
            <button class="btn btn-primary rounded-pill px-4" data-bs-dismiss="modal">Got it</button>
          </div>
        </div>
      </div>`);
  }

  window.showComingSoonModal = function() {
    if (typeof bootstrap !== 'undefined') {
      new bootstrap.Modal(document.getElementById('coming-soon-modal')).show();
    }
  };

  // ── Smartshala public API ─────────────────────────────────────────────────
  window.Smartshala = {
    logout() {
      ['smartshala_current','smartshala_session','smartshala_auth'].forEach(k => localStorage.removeItem(k));
      sessionStorage.removeItem('smartshala_auth');
      location.href = 'login.html';
    },
    getCurrentUser() {
      try {
        return JSON.parse(localStorage.getItem('smartshala_auth') || sessionStorage.getItem('smartshala_auth') || 'null');
      } catch (e) { return null; }
    },
    alert:   (msg, title = 'Notice')  => showModal(title, msg, 'alert'),
    confirm: (msg, title = 'Confirm') => showModal(title, msg, 'confirm'),
    prompt:  (msg, placeholder = '', title = 'Input Required') => showModal(title, msg, 'prompt', placeholder),
    toast:   showToast,
  };

  if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => navigator.serviceWorker.register('/sw.js').catch(() => {}));
  }
})();

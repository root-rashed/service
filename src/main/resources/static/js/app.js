/* Shared behaviour: mobile nav, user menu, toasts, password toggle, form loading state. */
(function () {
  'use strict';

  // ----- Mobile navigation -----
  var header = document.querySelector('[data-site-header]');
  var toggle = document.querySelector('[data-nav-toggle]');
  if (header && toggle) {
    toggle.addEventListener('click', function () {
      var open = header.classList.toggle('is-open');
      toggle.setAttribute('aria-expanded', String(open));
    });
  }

  // ----- User dropdown (<details>): close on outside click / Escape -----
  document.addEventListener('click', function (e) {
    document.querySelectorAll('details.user-menu[open]').forEach(function (d) {
      if (!d.contains(e.target)) { d.removeAttribute('open'); }
    });
  });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') {
      document.querySelectorAll('details.user-menu[open]').forEach(function (d) { d.removeAttribute('open'); });
    }
  });

  // ----- Toasts -----
  function dismissToast(toast) {
    if (!toast || toast.classList.contains('is-leaving')) { return; }
    toast.classList.add('is-leaving');
    setTimeout(function () { toast.remove(); }, 220);
  }

  function wireToast(toast) {
    var close = toast.querySelector('[data-toast-close]');
    if (close) { close.addEventListener('click', function () { dismissToast(toast); }); }
    setTimeout(function () { dismissToast(toast); }, 6000);
  }

  function showToast(type, message) {
    var container = document.querySelector('.toast-container');
    if (!container) {
      container = document.createElement('div');
      container.className = 'toast-container';
      container.setAttribute('aria-live', 'polite');
      document.body.appendChild(container);
    }
    var icons = { success: 'bi-check-circle-fill', error: 'bi-exclamation-circle-fill', info: 'bi-info-circle-fill' };
    var toast = document.createElement('div');
    toast.className = 'toast toast--' + (icons[type] ? type : 'info');
    toast.setAttribute('role', type === 'error' ? 'alert' : 'status');

    var icon = document.createElement('i');
    icon.className = 'bi ' + (icons[type] || icons.info);
    var text = document.createElement('span');
    text.textContent = message; // textContent: never inject HTML
    var close = document.createElement('button');
    close.type = 'button';
    close.className = 'toast__close';
    close.setAttribute('aria-label', 'Dismiss');
    close.setAttribute('data-toast-close', '');
    close.innerHTML = '&times;';

    toast.appendChild(icon);
    toast.appendChild(text);
    toast.appendChild(close);
    container.appendChild(toast);
    wireToast(toast);
  }

  document.querySelectorAll('.toast-container .toast').forEach(wireToast);

  // ----- CSRF helper for fetch() calls -----
  function csrfHeaders() {
    var token = document.querySelector('meta[name="_csrf"]');
    var name = document.querySelector('meta[name="_csrf_header"]');
    var headers = {};
    if (token && name) { headers[name.content] = token.content; }
    return headers;
  }

  // ----- Show / hide password -----
  document.addEventListener('click', function (e) {
    var btn = e.target.closest('[data-toggle-password]');
    if (!btn) { return; }
    var input = document.querySelector(btn.getAttribute('data-toggle-password'));
    if (!input) { return; }
    var show = input.type === 'password';
    input.type = show ? 'text' : 'password';
    btn.setAttribute('aria-label', show ? 'Hide password' : 'Show password');
    var icon = btn.querySelector('i');
    if (icon) { icon.className = show ? 'bi bi-eye-slash' : 'bi bi-eye'; }
  });

  // ----- Loading state on submit (forms with data-loading-on-submit) -----
  document.addEventListener('submit', function (e) {
    var form = e.target;
    if (!form.matches || !form.matches('form[data-loading-on-submit]')) { return; }
    var btn = form.querySelector('button[type="submit"]');
    if (!btn) { return; }
    btn.classList.add('is-loading');
    btn.setAttribute('aria-busy', 'true');
    setTimeout(function () { btn.disabled = true; }, 0); // after the submit event has been dispatched
  });

  // ----- Scroll reveal -----
  // Adds .is-revealed when an element scrolls into view. Nothing is hidden without JS:
  // the hidden state only exists once this observer has been registered, so a failed or
  // blocked script leaves the page fully visible.
  var revealTargets = document.querySelectorAll('[data-reveal], [data-reveal-group]');
  var reduceMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  if (revealTargets.length && !reduceMotion && 'IntersectionObserver' in window) {
    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) { return; }
        entry.target.classList.add('is-revealed');
        observer.unobserve(entry.target); // once only, never animated on the way back up
      });
    }, { rootMargin: '0px 0px -12% 0px', threshold: 0.08 });

    revealTargets.forEach(function (el) { observer.observe(el); });
  } else {
    // No observer, or motion is not wanted: show everything immediately.
    revealTargets.forEach(function (el) { el.classList.add('is-revealed'); });
  }

  window.App = { showToast: showToast, csrfHeaders: csrfHeaders };
})();

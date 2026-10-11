/* Heart buttons: [data-favorite] with data-service-id.
   Customers toggle through the API; visitors are sent to log in; other roles get a short message. */
(function () {
  'use strict';

  var userMeta = document.querySelector('meta[name="app-user"]');
  var role = userMeta ? userMeta.getAttribute('content') : null; // "customer", "other" or null (visitor)
  var apiBase = (document.querySelector('meta[name="app-favorites"]') || {}).content || '/api/favorites';
  var loginUrl = (document.querySelector('meta[name="app-login"]') || {}).content || '/login';

  function buttons() { return document.querySelectorAll('[data-favorite]'); }

  function apply(btn, on) {
    btn.classList.toggle('is-active', on);
    btn.setAttribute('aria-pressed', String(on));
    var icon = btn.querySelector('i.bi');
    if (icon) {
      icon.classList.toggle('bi-heart-fill', on);
      icon.classList.toggle('bi-heart', !on);
    }
    var text = btn.querySelector('[data-favorite-text]');
    if (text) { text.textContent = on ? 'Saved' : 'Save for later'; }
  }

  function toast(type, message) { if (window.App) { window.App.showToast(type, message); } }

  // Fill the hearts for customers.
  if (role === 'customer' && buttons().length) {
    var ids = [];
    buttons().forEach(function (b) {
      var id = b.getAttribute('data-service-id');
      if (id && ids.indexOf(id) === -1) { ids.push(id); }
    });
    if (ids.length) {
      fetch(apiBase + '/state?ids=' + encodeURIComponent(ids.join(',')), {
        headers: { 'Accept': 'application/json' }, credentials: 'same-origin'
      }).then(function (res) { return res.ok ? res.json() : null; })
        .then(function (body) {
          if (!body || !body.data) { return; }
          buttons().forEach(function (b) { apply(b, body.data.indexOf(b.getAttribute('data-service-id')) !== -1); });
        }).catch(function () { /* hearts simply stay empty */ });
    }
  }

  document.addEventListener('click', function (e) {
    var btn = e.target.closest('[data-favorite]');
    if (!btn) { return; }
    e.preventDefault();

    if (!role) { window.location.href = loginUrl; return; }
    if (role !== 'customer') { toast('info', 'Saving services is available to customer accounts.'); return; }
    if (btn.disabled) { return; }

    var id = btn.getAttribute('data-service-id');
    var headers = window.App ? window.App.csrfHeaders() : {};
    headers['Accept'] = 'application/json';
    btn.disabled = true;

    fetch(apiBase + '/' + encodeURIComponent(id) + '/toggle', {
      method: 'POST', headers: headers, credentials: 'same-origin'
    }).then(function (res) {
      if (res.redirected) { window.location.href = loginUrl; return null; }
      if (!res.ok) { throw new Error('request failed'); }
      return res.json();
    }).then(function (body) {
      if (!body || !body.data) { return; }
      var on = body.data.favorite === true;
      apply(btn, on);
      toast('success', on ? 'Saved to your favorites.' : 'Removed from your favorites.');

      // On the favorites page an unsaved card disappears.
      var page = btn.closest('[data-favorites-page]');
      if (page && !on) {
        var card = btn.closest('.service-card');
        if (card) { card.remove(); }
        if (!page.querySelector('.service-card')) { window.location.reload(); }
      }
    }).catch(function () {
      toast('error', 'Could not update your favorites. Please try again.');
    }).then(function () { btn.disabled = false; });
  });
})();

/* Service listing page: auto-submit on sort change, "use my location", mobile filter panel. */
(function () {
  'use strict';

  var form = document.getElementById('filter-form');
  if (!form) { return; }

  document.querySelectorAll('[data-auto-submit]').forEach(function (el) {
    el.addEventListener('change', function () { form.submit(); });
  });

  /* The sort control lives in its own form (it only carries the active filters), so it submits
     itself rather than the filter form. */
  document.querySelectorAll('[data-auto-submit-sort]').forEach(function (el) {
    el.addEventListener('change', function () { el.form.submit(); });
  });

  var lat = form.querySelector('[data-geo-lat]');
  var lng = form.querySelector('[data-geo-lng]');

  var useLocation = document.querySelector('[data-use-location]');
  if (useLocation && lat && lng) {
    useLocation.addEventListener('click', function () {
      if (!navigator.geolocation) {
        window.App.showToast('error', 'Location is not supported by your browser.');
        return;
      }
      useLocation.disabled = true;
      navigator.geolocation.getCurrentPosition(function (pos) {
        lat.value = pos.coords.latitude.toFixed(6);
        lng.value = pos.coords.longitude.toFixed(6);
        form.submit();
      }, function () {
        useLocation.disabled = false;
        window.App.showToast('error', 'Could not get your location. Check your browser permissions.');
      }, { timeout: 10000 });
    });
  }

  document.querySelectorAll('[data-clear-location]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      if (lat) { lat.value = ''; }
      if (lng) { lng.value = ''; }
      form.submit();
    });
  });

  var toggle = document.querySelector('[data-filters-toggle]');
  var panel = document.querySelector('.filters');
  if (toggle && panel) {
    toggle.addEventListener('click', function () {
      var open = panel.classList.toggle('is-open');
      toggle.setAttribute('aria-expanded', String(open));
    });
  }
})();

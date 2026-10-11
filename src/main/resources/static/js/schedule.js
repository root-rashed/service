/* Schedule page: grey out the time fields of days marked as not working. */
(function () {
  'use strict';

  function sync(row) {
    var toggle = row.querySelector('[data-day-toggle]');
    var working = toggle && toggle.checked;
    row.classList.toggle('is-off', !working);
    row.querySelectorAll('[data-day-time]').forEach(function (input) { input.disabled = !working; });
  }

  document.querySelectorAll('[data-day-row]').forEach(function (row) {
    sync(row);
    var toggle = row.querySelector('[data-day-toggle]');
    if (toggle) { toggle.addEventListener('change', function () { sync(row); }); }
  });
})();

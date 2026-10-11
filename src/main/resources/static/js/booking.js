/* Checkout: step navigation, live free-slot loading, selection summary.
   Without JavaScript every step is visible and the form still submits; the server re-checks everything. */
(function () {
  'use strict';

  var root = document.querySelector('[data-booking]');
  if (!root) { return; }

  var form = root.querySelector('form');
  var steps = Array.prototype.slice.call(root.querySelectorAll('[data-step]'));
  var indicators = Array.prototype.slice.call(document.querySelectorAll('[data-step-indicator]'));
  var dateInput = form.querySelector('[name="date"]');
  var startInput = form.querySelector('[name="startMinute"]');
  var addressInput = form.querySelector('[name="address"]');
  var slotsBox = root.querySelector('[data-slots]');
  var slotsStatus = root.querySelector('[data-slots-status]');
  var summaryDate = root.querySelector('[data-summary-date]');
  var summaryTime = root.querySelector('[data-summary-time]');
  var slotsUrl = root.getAttribute('data-slots-url');
  var current = parseInt(root.getAttribute('data-start-step') || '1', 10);
  var selectedLabel = null;

  function showStep(n, scroll) {
    current = n;
    steps.forEach(function (s) { s.hidden = parseInt(s.getAttribute('data-step'), 10) !== n; });
    indicators.forEach(function (i) {
      var idx = parseInt(i.getAttribute('data-step-indicator'), 10);
      i.classList.toggle('is-active', idx === n);
      i.classList.toggle('is-done', idx < n);
    });
    if (scroll) { root.scrollIntoView({ behavior: 'smooth', block: 'start' }); }
  }

  function setError(name, message) {
    var el = root.querySelector('[data-js-error="' + name + '"]');
    if (el) { el.textContent = message || ''; el.hidden = !message; }
  }

  function formatDate(iso) {
    var d = new Date(iso + 'T00:00:00');
    return d.toLocaleDateString('en-GB', { weekday: 'short', day: '2-digit', month: 'short', year: 'numeric' });
  }

  function updateSummary() {
    if (summaryDate) { summaryDate.textContent = dateInput.value ? formatDate(dateInput.value) : 'Not selected'; }
    if (summaryTime) { summaryTime.textContent = (startInput.value !== '' && selectedLabel) ? selectedLabel : 'Not selected'; }
  }

  function renderSlots(slots, keepStart) {
    slotsBox.textContent = '';
    var stillThere = false;
    slots.forEach(function (slot) {
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'slot-btn';
      btn.textContent = slot.startLabel;
      btn.setAttribute('data-start', String(slot.startMinute));
      btn.setAttribute('aria-label', slot.startLabel + ' to ' + slot.endLabel);
      if (keepStart && String(slot.startMinute) === startInput.value) {
        btn.classList.add('is-selected');
        selectedLabel = slot.startLabel + ' - ' + slot.endLabel;
        stillThere = true;
      }
      btn.addEventListener('click', function () {
        slotsBox.querySelectorAll('.slot-btn.is-selected').forEach(function (b) { b.classList.remove('is-selected'); });
        btn.classList.add('is-selected');
        startInput.value = String(slot.startMinute);
        selectedLabel = slot.startLabel + ' - ' + slot.endLabel;
        setError('slot', '');
        updateSummary();
      });
      slotsBox.appendChild(btn);
    });
    if (!stillThere) { startInput.value = ''; selectedLabel = null; }
    slotsStatus.textContent = slots.length === 0
      ? 'No free times on this day. Please try another date.'
      : slots.length + ' free time' + (slots.length === 1 ? '' : 's') + ' on this day. Choose when the job should start.';
    updateSummary();
  }

  function loadSlots(keepStart) {
    slotsBox.textContent = '';
    if (!dateInput.value) { slotsStatus.textContent = 'Choose a date to see free times.'; updateSummary(); return; }
    slotsStatus.textContent = 'Loading free times...';
    fetch(slotsUrl + '?date=' + encodeURIComponent(dateInput.value), {
      headers: { 'Accept': 'application/json' }, credentials: 'same-origin'
    }).then(function (res) {
      if (!res.ok) { throw new Error('request failed'); }
      return res.json();
    }).then(function (body) {
      renderSlots(body.data || [], keepStart);
    }).catch(function () {
      slotsStatus.textContent = 'Could not load free times. Please try again.';
      if (window.App) { window.App.showToast('error', 'Could not load free times. Please try again.'); }
    });
  }

  function validateStep(n) {
    if (n === 2) {
      if (!dateInput.value) { setError('slot', 'Please choose a date.'); return false; }
      if (startInput.value === '') { setError('slot', 'Please choose a free time.'); return false; }
    }
    if (n === 3) {
      if (!addressInput.value || addressInput.value.trim().length < 10) {
        setError('address', 'Please enter the full address (at least 10 characters).');
        return false;
      }
      setError('address', '');
    }
    return true;
  }

  root.querySelectorAll('[data-next]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      if (validateStep(current)) { showStep(current + 1, true); }
    });
  });
  root.querySelectorAll('[data-back]').forEach(function (btn) {
    btn.addEventListener('click', function () { showStep(current - 1, true); });
  });

  // Enter inside a single-line field must not submit the whole form early.
  form.addEventListener('keydown', function (e) {
    if (e.key === 'Enter' && e.target.tagName === 'INPUT') { e.preventDefault(); }
  });

  dateInput.addEventListener('change', function () { startInput.value = ''; selectedLabel = null; setError('slot', ''); loadSlots(false); });

  form.addEventListener('submit', function (e) {
    if (!validateStep(2)) { e.preventDefault(); showStep(2, true); return; }
    if (!validateStep(3)) { e.preventDefault(); showStep(3, true); }
  });

  root.classList.add('js');
  showStep(current, false);
  if (dateInput.value) { loadSlots(true); } else { updateSummary(); }
})();

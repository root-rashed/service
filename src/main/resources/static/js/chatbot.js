/* Booking assistant: suggestion chips fill the box, and the log keeps the newest turn in view. */
(function () {
  'use strict';

  var form = document.querySelector('[data-chat-form]');
  var input = form && form.querySelector('#question');
  var log = document.getElementById('chat-log');

  document.querySelectorAll('[data-chat-chip]').forEach(function (chip) {
    chip.addEventListener('click', function () {
      if (!input) { return; }
      input.value = chip.textContent.trim();
      form.submit();
    });
  });

  if (log) {
    log.scrollTop = log.scrollHeight;
  }

  if (input) {
    input.focus();
  }

  /* A hint before the visitor starts typing, so the box is never mistaken for a search field. */
  if (input && !input.value) {
    input.setAttribute('placeholder', 'Ask about a service, a price or how booking works');
  }
})();
/* Image helpers: upload preview, destructive-action confirm, service gallery thumbnails. */
(function () {
  'use strict';

  // ----- Preview selected files before upload -----
  var input = document.querySelector('[data-image-input]');
  var previews = document.querySelector('[data-image-previews]');
  if (input && previews) {
    input.addEventListener('change', function () {
      previews.textContent = '';
      Array.prototype.forEach.call(input.files, function (file) {
        if (!file.type || file.type.indexOf('image/') !== 0) { return; }
        var img = document.createElement('img');
        img.alt = '';
        img.src = URL.createObjectURL(file);
        img.onload = function () { URL.revokeObjectURL(img.src); };
        previews.appendChild(img);
      });
    });
  }

  // ----- Confirm before submitting forms marked data-confirm -----
  document.addEventListener('submit', function (e) {
    var form = e.target;
    if (form.matches && form.matches('form[data-confirm]')) {
      if (!window.confirm(form.getAttribute('data-confirm'))) { e.preventDefault(); }
    }
  });

  // ----- Service gallery: thumbnail click swaps the main photo -----
  var main = document.querySelector('[data-gallery-main]');
  if (main) {
    document.querySelectorAll('[data-gallery-thumb]').forEach(function (thumb) {
      thumb.addEventListener('click', function () {
        main.src = thumb.getAttribute('data-src');
        document.querySelectorAll('[data-gallery-thumb].is-active').forEach(function (t) { t.classList.remove('is-active'); });
        thumb.classList.add('is-active');
      });
    });
  }
})();

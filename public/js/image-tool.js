/* Compress / convert images in the browser using a canvas. Files never leave the device. */
(function () {
  'use strict';
  var root = document.getElementById('tool');
  if (!root) return;

  var mode = root.getAttribute('data-mode'); // 'compress' or 'convert'
  var fileInput = document.getElementById('file');
  var fmt = document.getElementById('fmt');
  var q = document.getElementById('q');
  var qv = document.getElementById('qv');
  var max = document.getElementById('max');
  var run = document.getElementById('run');
  var out = document.getElementById('out');
  var status = document.getElementById('status');
  var picked = document.getElementById('picked');
  var EXT = { 'image/jpeg': 'jpg', 'image/png': 'png', 'image/webp': 'webp' };
  var files = [];
  var urls = [];

  if (mode === 'convert') { fmt.value = 'image/png'; max.value = '0'; }
  else { fmt.value = 'image/jpeg'; q.value = '75'; max.value = '2000'; }

  function syncQuality() {
    qv.textContent = q.value + '%';
    q.disabled = fmt.value === 'image/png';
  }
  q.addEventListener('input', syncQuality);
  fmt.addEventListener('change', syncQuality);
  syncQuality();

  function clearResults() {
    urls.forEach(function (u) { URL.revokeObjectURL(u); });
    urls = [];
    out.textContent = '';
    status.textContent = '';
  }

  fileInput.addEventListener('change', function () {
    files = Array.prototype.slice.call(fileInput.files || []);
    clearResults();
    run.disabled = files.length === 0;
    picked.textContent = files.length ? files.length + (files.length === 1 ? ' image selected' : ' images selected') : '';
  });

  function addRow(name, info, note) {
    var li = SE.el('li', { 'class': 'row' });
    var main = SE.el('div', { 'class': 'grow' }, SE.el('strong', null, name));
    if (info) {
      var saved = info.before > 0 ? Math.round((1 - info.after / info.before) * 100) : 0;
      var line = SE.fmtBytes(info.before) + ' \u2192 ' + SE.fmtBytes(info.after) +
        (saved > 0 ? ' (' + saved + '% smaller)' : saved < 0 ? ' (' + Math.abs(saved) + '% larger)' : '');
      main.appendChild(SE.el('div', { 'class': 'sub' }, line));
    }
    if (note) main.appendChild(SE.el('div', { 'class': 'sub warn' }, note));
    li.appendChild(main);
    if (info) li.appendChild(SE.el('a', { 'class': 'btn small', href: info.url, download: info.filename }, 'Download'));
    out.appendChild(li);
  }

  async function processOne(file) {
    var bmp = await SE.loadBitmap(file);
    var size = SE.scaleTo(bmp.width, bmp.height, parseInt(max.value, 10));
    var type = fmt.value;
    var canvas = SE.draw(bmp, size.w, size.h, type === 'image/jpeg');
    if (bmp.close) bmp.close();
    var blob = await SE.toBlob(canvas, type, parseInt(q.value, 10) / 100);
    if (!blob) throw new Error('encode failed');
    var note = blob.type !== type ? 'Your browser saved this as ' + blob.type.replace('image/', '').toUpperCase() + ' instead.' : '';
    var ext = EXT[blob.type] || 'img';
    var url = URL.createObjectURL(blob);
    urls.push(url);
    addRow(file.name, { before: file.size, after: blob.size, url: url, filename: SE.baseName(file.name) + (mode === 'compress' ? '-compressed.' : '.') + ext }, note);
  }

  run.addEventListener('click', async function () {
    clearResults();
    run.disabled = true;
    for (var i = 0; i < files.length; i++) {
      status.textContent = 'Working on ' + (i + 1) + ' of ' + files.length + '\u2026';
      try { await processOne(files[i]); }
      catch (e) { addRow(files[i].name, null, 'This file could not be read as an image.'); }
    }
    status.textContent = 'Done. Tap Download next to each result.';
    run.disabled = false;
  });
})();

/* Images -> PDF and PDF merge, fully in the browser (uses the bundled pdf-lib). */
(function () {
  'use strict';
  var PT = {};

  // Page geometry in PDF points. Pure function so it can be unit-tested.
  PT.layout = function (size, margin, w, h) {
    if (size === 'fit') {
      var s = Math.min(1, 842 / Math.max(w, h)); // never upscale; cap longest side at 842 pt
      var fw = Math.max(1, Math.round(w * s)), fh = Math.max(1, Math.round(h * s));
      return { pw: fw, ph: fh, x: 0, y: 0, dw: fw, dh: fh };
    }
    var base = size === 'letter' ? [612, 792] : [595.28, 841.89];
    var landscape = w > h;
    var pw = landscape ? base[1] : base[0];
    var ph = landscape ? base[0] : base[1];
    var sc = Math.min((pw - 2 * margin) / w, (ph - 2 * margin) / h);
    var dw = w * sc, dh = h * sc;
    return { pw: pw, ph: ph, x: (pw - dw) / 2, y: (ph - dh) / 2, dw: dw, dh: dh };
  };

  // items: [{ bytes: Uint8Array (JPEG), width, height }]
  PT.imagesToPdf = async function (PDFLib, items, opts) {
    var doc = await PDFLib.PDFDocument.create();
    for (var i = 0; i < items.length; i++) {
      // Copy into a standalone array: pdf-lib misreads views that don't start at offset 0 (e.g. Node Buffers, subarrays).
      var img = await doc.embedJpg(new Uint8Array(items[i].bytes));
      var L = PT.layout(opts.size, opts.margin, items[i].width, items[i].height);
      var page = doc.addPage([L.pw, L.ph]);
      page.drawImage(img, { x: L.x, y: L.y, width: L.dw, height: L.dh });
    }
    return await doc.save();
  };

  // inputs: [{ name, bytes }]. Throws an Error with .fileName set if one can't be read
  // (damaged or password-protected).
  PT.mergePdfs = async function (PDFLib, inputs) {
    var docs = [];
    for (var i = 0; i < inputs.length; i++) {
      try { docs.push(await PDFLib.PDFDocument.load(new Uint8Array(inputs[i].bytes))); }
      catch (e) { var err = new Error('unreadable pdf'); err.fileName = inputs[i].name; throw err; }
    }
    var out = await PDFLib.PDFDocument.create();
    for (var d = 0; d < docs.length; d++) {
      var pages = await out.copyPages(docs[d], docs[d].getPageIndices());
      pages.forEach(function (p) { out.addPage(p); });
    }
    return await out.save();
  };

  if (typeof module !== 'undefined' && module.exports) { module.exports = PT; return; }
  window.PT = PT;

  /* ---------- page wiring (browser only) ---------- */
  var root = document.getElementById('tool');
  if (!root) return;
  var kind = root.getAttribute('data-tool'); // 'images-to-pdf' or 'merge-pdf'
  var fileInput = document.getElementById('file');
  var list = document.getElementById('list');
  var run = document.getElementById('run');
  var status = document.getElementById('status');
  var result = document.getElementById('result');
  var items = [];      // {file, thumb}
  var resultUrl = null;

  function setStatus(msg, bad) { status.textContent = msg; status.className = bad ? 'warn' : ''; }

  function render() {
    list.textContent = '';
    items.forEach(function (it, i) {
      var li = SE.el('li', { 'class': 'row' });
      if (kind === 'images-to-pdf') li.appendChild(SE.el('img', { 'class': 'thumb', src: it.thumb, alt: '' }));
      else li.appendChild(SE.el('span', { 'class': 'pdfmark', 'aria-hidden': 'true' }, 'PDF'));
      li.appendChild(SE.el('div', { 'class': 'grow' }, SE.el('strong', null, it.file.name), SE.el('div', { 'class': 'sub' }, SE.fmtBytes(it.file.size))));
      var up = SE.el('button', { type: 'button', 'class': 'icon', 'aria-label': 'Move up' }, '\u25B2');
      var down = SE.el('button', { type: 'button', 'class': 'icon', 'aria-label': 'Move down' }, '\u25BC');
      var del = SE.el('button', { type: 'button', 'class': 'icon', 'aria-label': 'Remove' }, '\u2715');
      up.disabled = i === 0; down.disabled = i === items.length - 1;
      up.addEventListener('click', function () { swap(i, i - 1); });
      down.addEventListener('click', function () { swap(i, i + 1); });
      del.addEventListener('click', function () { remove(i); });
      li.appendChild(up); li.appendChild(down); li.appendChild(del);
      list.appendChild(li);
    });
    var min = kind === 'merge-pdf' ? 2 : 1;
    run.disabled = items.length < min;
  }
  function swap(a, b) { var t = items[a]; items[a] = items[b]; items[b] = t; render(); }
  function remove(i) { var it = items.splice(i, 1)[0]; if (it.thumb) URL.revokeObjectURL(it.thumb); render(); }

  function clearResult() {
    if (resultUrl) { URL.revokeObjectURL(resultUrl); resultUrl = null; }
    result.textContent = '';
    setStatus('');
  }

  fileInput.addEventListener('change', function () {
    clearResult();
    Array.prototype.slice.call(fileInput.files || []).forEach(function (f) {
      items.push({ file: f, thumb: kind === 'images-to-pdf' ? URL.createObjectURL(f) : null });
    });
    fileInput.value = ''; // allow picking the same file again
    render();
  });

  function showResult(bytes, filename) {
    var blob = new Blob([bytes], { type: 'application/pdf' });
    resultUrl = URL.createObjectURL(blob);
    result.appendChild(SE.el('div', { 'class': 'row' },
      SE.el('div', { 'class': 'grow' }, SE.el('strong', null, filename), SE.el('div', { 'class': 'sub' }, SE.fmtBytes(blob.size))),
      SE.el('a', { 'class': 'btn small', href: resultUrl, download: filename }, 'Download')));
  }

  run.addEventListener('click', async function () {
    clearResult();
    if (!window.PDFLib) { setStatus('The PDF library could not be loaded. Check your connection and reload the page.', true); return; }
    run.disabled = true;
    try {
      if (kind === 'merge-pdf') {
        setStatus('Merging\u2026');
        var inputs = [];
        for (var i = 0; i < items.length; i++) inputs.push({ name: items[i].file.name, bytes: new Uint8Array(await items[i].file.arrayBuffer()) });
        showResult(await PT.mergePdfs(window.PDFLib, inputs), 'merged.pdf');
      } else {
        var imgs = [];
        for (var j = 0; j < items.length; j++) {
          setStatus('Preparing image ' + (j + 1) + ' of ' + items.length + '\u2026');
          var bmp = await SE.loadBitmap(items[j].file);
          var s = SE.scaleTo(bmp.width, bmp.height, 2480);
          var canvas = SE.draw(bmp, s.w, s.h, true);
          if (bmp.close) bmp.close();
          var blob = await SE.toBlob(canvas, 'image/jpeg', 0.9);
          imgs.push({ bytes: new Uint8Array(await blob.arrayBuffer()), width: s.w, height: s.h });
        }
        setStatus('Building PDF\u2026');
        var bytes = await PT.imagesToPdf(window.PDFLib, imgs, {
          size: document.getElementById('size').value,
          margin: parseInt(document.getElementById('margin').value, 10)
        });
        showResult(bytes, 'images.pdf');
      }
      setStatus('Done. Tap Download to save your PDF.');
    } catch (e) {
      setStatus(e && e.fileName ? '\u201C' + e.fileName + '\u201D could not be read. It may be damaged or password-protected.' : 'Something went wrong. Try fewer or smaller files.', true);
    }
    render();
  });

  render();
})();

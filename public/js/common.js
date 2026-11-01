/* Shared helpers for the in-browser tools. Nothing here sends data anywhere. */
(function () {
  'use strict';
  var SE = {};

  SE.fmtBytes = function (n) {
    if (n < 1024) return n + ' B';
    if (n < 1048576) return (n / 1024).toFixed(1) + ' KB';
    return (n / 1048576).toFixed(2) + ' MB';
  };

  SE.baseName = function (name) {
    var i = name.lastIndexOf('.');
    return i > 0 ? name.slice(0, i) : name;
  };

  // Scale (w, h) down so the longest side is at most `max` (0 = keep original).
  SE.scaleTo = function (w, h, max) {
    if (!max || Math.max(w, h) <= max) return { w: w, h: h };
    var s = max / Math.max(w, h);
    return { w: Math.max(1, Math.round(w * s)), h: Math.max(1, Math.round(h * s)) };
  };

  // Decode an image file, honouring EXIF rotation where the browser supports it.
  SE.loadBitmap = async function (file) {
    if (window.createImageBitmap) {
      try { return await createImageBitmap(file, { imageOrientation: 'from-image' }); } catch (e) { /* try next */ }
      try { return await createImageBitmap(file); } catch (e) { /* fall back to <img> */ }
    }
    return await new Promise(function (resolve, reject) {
      var url = URL.createObjectURL(file);
      var img = new Image();
      img.onload = function () {
        resolve({ width: img.naturalWidth, height: img.naturalHeight, _img: img, close: function () { URL.revokeObjectURL(url); } });
      };
      img.onerror = function () { URL.revokeObjectURL(url); reject(new Error('unreadable image')); };
      img.src = url;
    });
  };

  SE.draw = function (bmp, w, h, fillWhite) {
    var c = document.createElement('canvas');
    c.width = w; c.height = h;
    var ctx = c.getContext('2d');
    if (fillWhite) { ctx.fillStyle = '#ffffff'; ctx.fillRect(0, 0, w, h); }
    ctx.drawImage(bmp._img || bmp, 0, 0, w, h);
    return c;
  };

  SE.toBlob = function (canvas, type, quality) {
    return new Promise(function (resolve) { canvas.toBlob(resolve, type, quality); });
  };

  // Small DOM helper: el('li', {class: 'x'}, child, 'text')
  SE.el = function (tag, attrs) {
    var node = document.createElement(tag);
    if (attrs) Object.keys(attrs).forEach(function (k) { node.setAttribute(k, attrs[k]); });
    for (var i = 2; i < arguments.length; i++) {
      var c = arguments[i];
      if (c == null) continue;
      node.appendChild(typeof c === 'string' ? document.createTextNode(c) : c);
    }
    return node;
  };

  window.SE = SE;
})();

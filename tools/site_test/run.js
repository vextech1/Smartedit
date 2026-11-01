// Tests for the website's PDF tools. Run with:  node tools/site_test/run.js
'use strict';
const assert = require('node:assert/strict');
const path = require('node:path');
const PDFLib = require(path.join(__dirname, '../../public/vendor/pdf-lib-1.17.1.min.js'));
const PT = require(path.join(__dirname, '../../public/js/pdf-tools.js'));
const { JPEG_40x20, ENCRYPTED_PDF } = require('./fixtures.js');

let passed = 0;
async function test(name, fn) {
  try { await fn(); passed++; console.log('ok   - ' + name); }
  catch (e) { console.error('FAIL - ' + name + '\n' + (e && e.stack || e)); process.exitCode = 1; }
}
const near = (a, b, eps = 0.01) => assert.ok(Math.abs(a - b) <= eps, `${a} !~ ${b}`);

async function makePdf(sizes) {
  const d = await PDFLib.PDFDocument.create();
  sizes.forEach(([w, h]) => d.addPage([w, h]));
  return await d.save();
}

(async () => {
  await test('layout: fit keeps small images at their size', () => {
    const L = PT.layout('fit', 0, 400, 300);
    assert.deepEqual([L.pw, L.ph, L.x, L.y, L.dw, L.dh], [400, 300, 0, 0, 400, 300]);
  });
  await test('layout: fit caps the longest side at 842 pt, keeping aspect', () => {
    const L = PT.layout('fit', 0, 4000, 2000);
    assert.equal(L.pw, 842); assert.equal(L.ph, 421);
  });
  await test('layout: A4 portrait for a tall image, centred inside the margin', () => {
    const L = PT.layout('a4', 36, 1000, 2000);
    near(L.pw, 595.28); near(L.ph, 841.89);
    assert.ok(L.x >= 36 - 0.01 && L.y >= 36 - 0.01);
    assert.ok(L.x + L.dw <= L.pw - 36 + 0.01 && L.y + L.dh <= L.ph - 36 + 0.01);
    near(L.dw / L.dh, 0.5, 0.001);
  });
  await test('layout: Letter switches to landscape for a wide image', () => {
    const L = PT.layout('letter', 0, 2000, 1000);
    assert.equal(L.pw, 792); assert.equal(L.ph, 612);
    near(L.dw, 792); near(L.dh, 396);
  });

  await test('merge: pages keep the order of the inputs', async () => {
    const a = await makePdf([[100, 100]]);
    const b = await makePdf([[200, 200], [300, 300]]);
    const c = await makePdf([[400, 400]]);
    const merged = await PDFLib.PDFDocument.load(await PT.mergePdfs(PDFLib, [
      { name: 'a.pdf', bytes: a }, { name: 'b.pdf', bytes: b }, { name: 'c.pdf', bytes: c }]));
    assert.deepEqual(merged.getPages().map((p) => p.getWidth()), [100, 200, 300, 400]);
  });
  await test('merge: a damaged file is reported by name', async () => {
    const ok = await makePdf([[100, 100]]);
    await assert.rejects(
      PT.mergePdfs(PDFLib, [{ name: 'ok.pdf', bytes: ok }, { name: 'broken.pdf', bytes: new Uint8Array([1, 2, 3, 4]) }]),
      (e) => e.fileName === 'broken.pdf');
  });
  await test('merge: a password-protected file is reported by name', async () => {
    const ok = await makePdf([[100, 100]]);
    await assert.rejects(
      PT.mergePdfs(PDFLib, [{ name: 'ok.pdf', bytes: ok }, { name: 'locked.pdf', bytes: Buffer.from(ENCRYPTED_PDF, 'base64') }]),
      (e) => e.fileName === 'locked.pdf');
  });

  await test('images to pdf: one page per image; a wide image gets a landscape A4 page', async () => {
    const jpeg = Buffer.from(JPEG_40x20, 'base64');
    const bytes = await PT.imagesToPdf(PDFLib, [
      { bytes: jpeg, width: 40, height: 20 }, { bytes: jpeg, width: 40, height: 20 }], { size: 'a4', margin: 36 });
    const doc = await PDFLib.PDFDocument.load(bytes);
    assert.equal(doc.getPageCount(), 2);
    near(doc.getPage(0).getWidth(), 841.89, 0.5);   // 40x20 is wider than tall -> landscape
    near(doc.getPage(0).getHeight(), 595.28, 0.5);
  });
  await test('images to pdf: fit mode makes pages the size of the image', async () => {
    const jpeg = Buffer.from(JPEG_40x20, 'base64');
    const doc = await PDFLib.PDFDocument.load(await PT.imagesToPdf(PDFLib, [{ bytes: jpeg, width: 40, height: 20 }], { size: 'fit', margin: 0 }));
    assert.deepEqual([doc.getPage(0).getWidth(), doc.getPage(0).getHeight()], [40, 20]);
  });

  console.log(`\n${passed} passed` + (process.exitCode ? ', some FAILED' : ''));
})();

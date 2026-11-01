import { describe, expect, it } from 'vitest';
import { createRequire } from 'node:module';
import { PDFDocument } from 'pdf-lib';
import { baseName, fmtBytes, scaleTo } from '../src/lib/core/format';
import { imagesToPdf, layout, mergePdfs, UnreadablePdfError } from '../src/lib/core/pdf';
import { tools, toolBySlug } from '../src/lib/tools';

const { JPEG_40x20, ENCRYPTED_PDF } = createRequire(import.meta.url)('../../tools/site_test/fixtures.js');
const b64 = (s: string) => new Uint8Array(Buffer.from(s, 'base64'));
const blank = async (n: number) => { const d = await PDFDocument.create(); for (let i = 0; i < n; i++) d.addPage([200, 200]); return d.save(); };

describe('format', () => {
  it('formats bytes', () => { expect(fmtBytes(512)).toBe('512 B'); expect(fmtBytes(2048)).toBe('2.0 KB'); expect(fmtBytes(3145728)).toBe('3.00 MB'); });
  it('strips extensions', () => { expect(baseName('a.b.jpg')).toBe('a.b'); expect(baseName('.hidden')).toBe('.hidden'); });
  it('scales down only', () => {
    expect(scaleTo(4000, 2000, 2000)).toEqual({ w: 2000, h: 1000 });
    expect(scaleTo(100, 50, 2000)).toEqual({ w: 100, h: 50 });
    expect(scaleTo(100, 50, 0)).toEqual({ w: 100, h: 50 });
  });
});

describe('pdf layout', () => {
  it('fit caps longest side at 842pt', () => { const L = layout('fit', 0, 4000, 2000); expect([L.pw, L.ph]).toEqual([842, 421]); });
  it('letter goes landscape for wide images and respects margin', () => {
    const L = layout('letter', 36, 2000, 1000);
    expect([L.pw, L.ph]).toEqual([792, 612]);
    expect(L.x).toBeGreaterThanOrEqual(36 - 0.01); expect(L.x + L.dw).toBeLessThanOrEqual(792 - 36 + 0.01);
  });
});

describe('pdf operations', () => {
  it('images to pdf makes one page per image', async () => {
    const jpg = b64(JPEG_40x20);
    const out = await PDFDocument.load(await imagesToPdf([{ bytes: jpg, width: 40, height: 20 }, { bytes: jpg, width: 40, height: 20 }], { size: 'a4', margin: 0 }));
    expect(out.getPageCount()).toBe(2);
  });
  it('merges page counts in order', async () => {
    const out = await PDFDocument.load(await mergePdfs([{ name: 'a', bytes: await blank(2) }, { name: 'b', bytes: await blank(3) }]));
    expect(out.getPageCount()).toBe(5);
  });
  it('names the unreadable file', async () => {
    const enc = typeof ENCRYPTED_PDF === 'string' ? b64(ENCRYPTED_PDF) : new Uint8Array(ENCRYPTED_PDF);
    await expect(mergePdfs([{ name: 'ok.pdf', bytes: await blank(1) }, { name: 'locked.pdf', bytes: enc }]))
      .rejects.toMatchObject({ fileName: 'locked.pdf' });
  });
});

describe('tool registry', () => {
  it('has unique slugs and lookups work', () => {
    expect(new Set(tools.map((t) => t.slug)).size).toBe(tools.length);
    expect(toolBySlug('merge-pdf')?.kind).toBe('pdf');
    expect(toolBySlug('nope')).toBeUndefined();
  });
});

import { buildFromPages, countPages, everyPage, parseRanges, splitPdf } from '../src/lib/core/pdf-edit';
import { zipFiles } from '../src/lib/core/zip';
import { unzipSync } from 'fflate';

const numbered = async (n: number) => { const d = await PDFDocument.create(); for (let i = 0; i < n; i++) d.addPage([100 + i * 10, 200]); return d.save(); };

describe('page editing', () => {
  it('reorders, drops and rotates pages', async () => {
    const out = await PDFDocument.load(await buildFromPages(await numbered(4), [{ index: 2, rotate: 90 }, { index: 0, rotate: 0 }]));
    expect(out.getPageCount()).toBe(2);
    expect(out.getPage(0).getWidth()).toBe(120);
    expect(out.getPage(0).getRotation().angle).toBe(90);
    expect(out.getPage(1).getWidth()).toBe(100);
  });
  it('adds to an existing rotation and wraps at 360', async () => {
    const d = await PDFDocument.create(); const p = d.addPage([100, 100]); p.setRotation({ type: 'degrees' as never, angle: 270 });
    const out = await PDFDocument.load(await buildFromPages(await d.save(), [{ index: 0, rotate: 180 }]));
    expect(out.getPage(0).getRotation().angle).toBe(90);
  });
  it('counts pages', async () => { expect(await countPages(await numbered(3))).toBe(3); });
});

describe('ranges and split', () => {
  it('parses ranges', () => {
    expect(parseRanges('1-3, 5', 9)).toEqual([[0, 1, 2], [4]]);
    expect(parseRanges('', 3)).toEqual([[0, 1, 2]]);
    expect(parseRanges('ALL', 2)).toEqual([[0, 1]]);
  });
  it('rejects bad input with readable messages', () => {
    expect(() => parseRanges('0', 5)).toThrow(RangeError);
    expect(() => parseRanges('3-1', 5)).toThrow(/valid range/);
    expect(() => parseRanges('9', 5)).toThrow(/5 pages/);
    expect(() => parseRanges('a', 5)).toThrow(/isn’t a page/);
  });
  it('splits into one PDF per group', async () => {
    const parts = await splitPdf(await numbered(5), [[0, 1], [4]]);
    expect((await PDFDocument.load(parts[0])).getPageCount()).toBe(2);
    expect((await PDFDocument.load(parts[1])).getPageCount()).toBe(1);
    expect(everyPage(3)).toEqual([[0], [1], [2]]);
  });
});

describe('zip', () => {
  it('round-trips and de-duplicates names', () => {
    const z = unzipSync(zipFiles([{ name: 'a.pdf', bytes: new Uint8Array([1]) }, { name: 'a.pdf', bytes: new Uint8Array([2]) }]));
    expect(Object.keys(z).sort()).toEqual(['a-2.pdf', 'a.pdf']);
    expect(Array.from(z['a-2.pdf'])).toEqual([2]);
  });
});

import { compressPdfImages, savingsLabel } from '../src/lib/core/pdf-compress';
import { applyAdjust, cropRect, isNeutral, NO_ADJUST } from '../src/lib/core/image-edit';
import { joinPages } from '../src/lib/core/ocr';
import { pagesToPdf } from '../src/lib/core/pdf';

const withJpegs = async (n: number) => {
  const d = await PDFDocument.create(); const jpg = b64(JPEG_40x20);
  for (let i = 0; i < n; i++) { const im = await d.embedJpg(jpg); d.addPage([100, 100]).drawImage(im, { x: 0, y: 0, width: 40, height: 20 }); }
  return d.save();
};

describe('pdf image compression', () => {
  it('replaces pictures only when the new one is smaller', async () => {
    const src = await withJpegs(2);
    const keep = await compressPdfImages(src, async (j) => ({ bytes: j, width: 40, height: 20 }));
    expect(keep.found).toBe(2); expect(keep.replaced).toBe(0);
    const small = await compressPdfImages(src, async (j) => ({ bytes: j.slice(0, Math.floor(j.length / 2)), width: 20, height: 10 }));
    expect(small.replaced).toBe(2);
    expect((await PDFDocument.load(small.bytes)).getPageCount()).toBe(2); // still a valid PDF with its pages
  });
  it('skips pictures the encoder cannot handle', async () => {
    const r = await compressPdfImages(await withJpegs(1), async () => null);
    expect(r.found).toBe(1); expect(r.replaced).toBe(0);
  });
  it('labels results like the Android app', () => {
    expect(savingsLabel(100, 100)).toBe('optimized'); expect(savingsLabel(100, 120)).toBe('optimized');
    expect(savingsLabel(1000, 990)).toBe('barely'); expect(savingsLabel(1000, 500)).toBe('smaller');
  });
  it('rebuilds pages at their exact size', async () => {
    const out = await PDFDocument.load(await pagesToPdf([{ bytes: b64(JPEG_40x20), wPt: 300, hPt: 150 }]));
    expect([out.getPage(0).getWidth(), out.getPage(0).getHeight()]).toEqual([300, 150]);
  });
});

describe('image editing', () => {
  const px = (r: number, g: number, b: number) => new Uint8ClampedArray([r, g, b, 255]);
  it('leaves neutral settings alone', () => { const p = px(10, 120, 240); applyAdjust(p, NO_ADJUST); expect(Array.from(p)).toEqual([10, 120, 240, 255]); expect(isNeutral(NO_ADJUST)).toBe(true); });
  it('brightens and darkens', () => {
    const up = px(100, 100, 100); applyAdjust(up, { ...NO_ADJUST, brightness: 20 }); expect(up[0]).toBeGreaterThan(100);
    const dn = px(100, 100, 100); applyAdjust(dn, { ...NO_ADJUST, brightness: -20 }); expect(dn[0]).toBeLessThan(100);
  });
  it('grey mode equalises channels and keeps alpha', () => { const p = px(255, 0, 0); applyAdjust(p, { ...NO_ADJUST, mode: 'gray' }); expect(p[0]).toBe(p[1]); expect(p[1]).toBe(p[2]); expect(p[3]).toBe(255); });
  it('document mode pushes light paper to white and dark ink to black', () => {
    const paper = px(215, 215, 210); const ink = px(70, 70, 75);
    applyAdjust(paper, { ...NO_ADJUST, mode: 'document' }); applyAdjust(ink, { ...NO_ADJUST, mode: 'document' });
    expect(paper[0]).toBeGreaterThan(215); expect(ink[0]).toBeLessThan(70);
  });
  it('normalises and clamps crop drags', () => {
    expect(cropRect(50, 60, 10, 20, 100, 100)).toEqual({ x: 10, y: 20, w: 40, h: 40 });
    expect(cropRect(-20, -20, 500, 500, 100, 80)).toEqual({ x: 0, y: 0, w: 100, h: 80 });
    expect(cropRect(10, 10, 12, 12, 100, 100)).toBeNull();
  });
});

describe('ocr text', () => {
  it('joins pages with markers only when there are several', () => {
    expect(joinPages([' hi '])).toBe('hi');
    expect(joinPages(['a', ''])).toBe('--- Page 1 ---\na\n\n--- Page 2 ---\n(no text found)');
  });
});

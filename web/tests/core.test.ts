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

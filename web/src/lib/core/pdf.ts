// PDF building blocks. Pure where possible so they can be unit-tested in Node.
export type PageSize = 'fit' | 'a4' | 'letter';
export interface PageLayout { pw: number; ph: number; x: number; y: number; dw: number; dh: number }

/** Page geometry in PDF points for an image of w x h pixels. */
export function layout(size: PageSize, margin: number, w: number, h: number): PageLayout {
  if (size === 'fit') {
    const s = Math.min(1, 842 / Math.max(w, h)); // never upscale; cap longest side at 842 pt
    const fw = Math.max(1, Math.round(w * s)), fh = Math.max(1, Math.round(h * s));
    return { pw: fw, ph: fh, x: 0, y: 0, dw: fw, dh: fh };
  }
  const base = size === 'letter' ? [612, 792] : [595.28, 841.89];
  const landscape = w > h;
  const pw = landscape ? base[1] : base[0];
  const ph = landscape ? base[0] : base[1];
  const sc = Math.min((pw - 2 * margin) / w, (ph - 2 * margin) / h);
  const dw = w * sc, dh = h * sc;
  return { pw, ph, x: (pw - dw) / 2, y: (ph - dh) / 2, dw, dh };
}

export interface JpegItem { bytes: Uint8Array; width: number; height: number }

export async function imagesToPdf(items: JpegItem[], opts: { size: PageSize; margin: number }): Promise<Uint8Array> {
  const { PDFDocument } = await import('pdf-lib');
  const doc = await PDFDocument.create();
  for (const it of items) {
    // Copy to a standalone array: pdf-lib misreads views that don't start at offset 0.
    const img = await doc.embedJpg(new Uint8Array(it.bytes));
    const L = layout(opts.size, opts.margin, it.width, it.height);
    doc.addPage([L.pw, L.ph]).drawImage(img, { x: L.x, y: L.y, width: L.dw, height: L.dh });
  }
  return doc.save();
}

export class UnreadablePdfError extends Error {
  constructor(public fileName: string) { super('unreadable pdf'); }
}

/** Throws UnreadablePdfError (with the file name) if an input is damaged or password-protected. */
export async function mergePdfs(inputs: { name: string; bytes: Uint8Array }[]): Promise<Uint8Array> {
  const { PDFDocument } = await import('pdf-lib');
  const docs = [];
  for (const inp of inputs) {
    try { docs.push(await PDFDocument.load(new Uint8Array(inp.bytes))); }
    catch { throw new UnreadablePdfError(inp.name); }
  }
  const out = await PDFDocument.create();
  for (const d of docs) {
    const pages = await out.copyPages(d, d.getPageIndices());
    pages.forEach((p) => out.addPage(p));
  }
  return out.save();
}

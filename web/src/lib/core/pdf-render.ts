// Browser-only: render PDF pages with pdf.js (loaded on demand, worker bundled by Vite).
import type { PDFDocumentProxy } from 'pdfjs-dist';

let lib: typeof import('pdfjs-dist') | null = null;
async function pdfjs() {
  if (!lib) {
    lib = await import('pdfjs-dist');
    lib.GlobalWorkerOptions.workerSrc = (await import('pdfjs-dist/build/pdf.worker.min.mjs?url')).default;
  }
  return lib;
}

export async function openPdf(bytes: Uint8Array): Promise<PDFDocumentProxy> {
  const { getDocument } = await pdfjs();
  return getDocument({ data: bytes.slice() }).promise; // pdf.js takes ownership of the buffer, so hand it a copy
}

/** Render page n (1-based). Give either the longest side in px (`maxSide`) or a `scale` (1 = 72 dpi). */
export async function renderPage(doc: PDFDocumentProxy, n: number, opt: { maxSide?: number; scale?: number }): Promise<HTMLCanvasElement> {
  const page = await doc.getPage(n);
  const base = page.getViewport({ scale: 1 });
  const scale = opt.scale ?? (opt.maxSide ?? 240) / Math.max(base.width, base.height);
  const viewport = page.getViewport({ scale });
  const canvas = document.createElement('canvas');
  canvas.width = Math.max(1, Math.floor(viewport.width));
  canvas.height = Math.max(1, Math.floor(viewport.height));
  await page.render({ canvas, viewport, background: '#ffffff' }).promise;
  page.cleanup();
  return canvas;
}

/** Release the document and its worker memory. */
export function closePdf(doc: PDFDocumentProxy): void { void doc.loadingTask.destroy(); }

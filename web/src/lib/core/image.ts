// Browser-only image helpers. Nothing here touches the network.
export interface Bitmap { width: number; height: number; source: CanvasImageSource; close: () => void }

/** Decode an image file, honouring EXIF rotation where the browser supports it. */
export async function loadBitmap(file: Blob): Promise<Bitmap> {
  if (typeof createImageBitmap === 'function') {
    for (const opts of [{ imageOrientation: 'from-image' as const }, undefined]) {
      try {
        const b = await createImageBitmap(file, opts);
        return { width: b.width, height: b.height, source: b, close: () => b.close() };
      } catch { /* try next */ }
    }
  }
  const url = URL.createObjectURL(file);
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve({ width: img.naturalWidth, height: img.naturalHeight, source: img, close: () => URL.revokeObjectURL(url) });
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('unreadable image')); };
    img.src = url;
  });
}

export function draw(bmp: Bitmap, w: number, h: number, fillWhite: boolean): HTMLCanvasElement {
  const c = document.createElement('canvas');
  c.width = w; c.height = h;
  const ctx = c.getContext('2d')!;
  if (fillWhite) { ctx.fillStyle = '#ffffff'; ctx.fillRect(0, 0, w, h); }
  ctx.drawImage(bmp.source, 0, 0, w, h);
  return c;
}

export function toBlob(canvas: HTMLCanvasElement, type: string, quality?: number): Promise<Blob | null> {
  return new Promise((resolve) => canvas.toBlob(resolve, type, quality));
}

/** Encoder for PDF compression. Ignores EXIF orientation on purpose: PDF viewers do too. Returns null on anything it can't decode (e.g. CMYK). */
export function jpegRecompressor(quality: number, maxSide: number) {
  return async (jpeg: Uint8Array) => {
    try {
      const bmp = await createImageBitmap(new Blob([jpeg as BlobPart], { type: 'image/jpeg' }), { imageOrientation: 'none' });
      const k = maxSide && Math.max(bmp.width, bmp.height) > maxSide ? maxSide / Math.max(bmp.width, bmp.height) : 1;
      const w = Math.max(1, Math.round(bmp.width * k)), h = Math.max(1, Math.round(bmp.height * k));
      const c = document.createElement('canvas'); c.width = w; c.height = h;
      const ctx = c.getContext('2d')!; ctx.fillStyle = '#fff'; ctx.fillRect(0, 0, w, h); ctx.drawImage(bmp, 0, 0, w, h);
      bmp.close();
      const out = await toBlob(c, 'image/jpeg', quality);
      c.width = c.height = 0;
      return out ? { bytes: new Uint8Array(await out.arrayBuffer()), width: w, height: h } : null;
    } catch { return null; }
  };
}

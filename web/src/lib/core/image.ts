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

// Pixel adjustments for the image editor. Operates on RGBA data in place so it's testable without a canvas.
export interface Adjust { brightness: number; contrast: number; saturation: number; mode: 'none' | 'gray' | 'document' } // sliders: -100..100
export const NO_ADJUST: Adjust = { brightness: 0, contrast: 0, saturation: 0, mode: 'none' };
export const isNeutral = (a: Adjust) => a.brightness === 0 && a.contrast === 0 && a.saturation === 0 && a.mode === 'none';

const clamp = (v: number) => (v < 0 ? 0 : v > 255 ? 255 : v);

export function applyAdjust(px: Uint8ClampedArray, a: Adjust): void {
  const b = a.brightness * 2.55;
  const c = a.contrast + (a.mode === 'document' ? 45 : 0);           // "Document" = grey + strong contrast, for scans and receipts
  const k = (259 * (c * 2.55 + 255)) / (255 * (259 - c * 2.55));
  const s = 1 + a.saturation / 100;
  const gray = a.mode !== 'none';
  for (let i = 0; i < px.length; i += 4) {
    let r = px[i], g = px[i + 1], bl = px[i + 2];
    const l = 0.2126 * r + 0.7152 * g + 0.0722 * bl;
    if (gray) { r = g = bl = l; }
    else if (s !== 1) { r = l + (r - l) * s; g = l + (g - l) * s; bl = l + (bl - l) * s; }
    px[i]     = clamp(k * (r + b - 128) + 128);
    px[i + 1] = clamp(k * (g + b - 128) + 128);
    px[i + 2] = clamp(k * (bl + b - 128) + 128);
  }
}

export interface Rect { x: number; y: number; w: number; h: number }
/** Normalise a drag from (x0,y0) to (x1,y1) into a rectangle clamped to the image. Returns null if it is too small to be a crop. */
export function cropRect(x0: number, y0: number, x1: number, y1: number, W: number, H: number, min = 8): Rect | null {
  const x = Math.max(0, Math.min(x0, x1)), y = Math.max(0, Math.min(y0, y1));
  const w = Math.min(W, Math.max(x0, x1)) - x, h = Math.min(H, Math.max(y0, y1)) - y;
  return w < min || h < min ? null : { x: Math.round(x), y: Math.round(y), w: Math.round(w), h: Math.round(h) };
}

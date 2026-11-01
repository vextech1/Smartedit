// "Keep text" PDF compression: recompress the JPEG pictures inside a PDF and leave everything else (text, vectors) alone.
// The encoder is injected so the logic is testable in Node; the browser encoder lives in image.ts.
export type ImageEncoder = (jpeg: Uint8Array) => Promise<{ bytes: Uint8Array; width: number; height: number } | null>;
export interface CompressResult { bytes: Uint8Array; found: number; replaced: number }

export async function compressPdfImages(input: Uint8Array, encode: ImageEncoder): Promise<CompressResult> {
  const { PDFDocument, PDFName, PDFRawStream, PDFArray } = await import('pdf-lib');
  const doc = await PDFDocument.load(new Uint8Array(input));
  const ctx = doc.context;
  const N = (s: string) => PDFName.of(s);
  let found = 0, replaced = 0;

  for (const [ref, obj] of ctx.enumerateIndirectObjects()) {
    if (!(obj instanceof PDFRawStream)) continue;
    const d = obj.dict;
    if (d.get(N('Subtype')) !== N('Image')) continue;
    // Leave alone: masks/transparency, stencil masks, custom decode arrays, anything that isn't a plain RGB JPEG.
    if (d.has(N('SMask')) || d.has(N('Mask')) || d.has(N('ImageMask')) || d.has(N('Decode'))) continue;
    const f = d.get(N('Filter'));
    const isJpeg = f === N('DCTDecode') || (f instanceof PDFArray && f.size() === 1 && f.get(0) === N('DCTDecode'));
    if (!isJpeg || d.get(N('ColorSpace')) !== N('DeviceRGB')) continue;
    found++;
    const original = obj.contents;
    const enc = await encode(original);
    if (!enc || enc.bytes.length >= original.length * 0.98) continue; // not worth it: keep the original picture
    const dict = ctx.obj({ Type: 'XObject', Subtype: 'Image', Width: enc.width, Height: enc.height,
      ColorSpace: 'DeviceRGB', BitsPerComponent: 8, Filter: 'DCTDecode', Length: enc.bytes.length });
    ctx.assign(ref, PDFRawStream.of(dict, enc.bytes));
    replaced++;
  }
  return { bytes: await doc.save({ useObjectStreams: true }), found, replaced };
}

export const PRESETS = {
  high:     { label: 'High quality', quality: 0.85, maxSide: 2500, dpi: 150 },
  balanced: { label: 'Balanced',     quality: 0.7,  maxSide: 1800, dpi: 110 },
  small:    { label: 'Smaller file', quality: 0.5,  maxSide: 1200, dpi: 80 }
} as const;
export type PresetKey = keyof typeof PRESETS;

/** Labels the outcome of a compression attempt the same way the Android app does. */
export function savingsLabel(before: number, after: number): 'optimized' | 'barely' | 'smaller' {
  if (after >= before) return 'optimized';          // not smaller: keep the original
  return (before - after) / before < 0.02 ? 'barely' : 'smaller';
}

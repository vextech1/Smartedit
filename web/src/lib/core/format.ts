export function fmtBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1048576) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / 1048576).toFixed(2)} MB`;
}

export function baseName(name: string): string {
  const i = name.lastIndexOf('.');
  return i > 0 ? name.slice(0, i) : name;
}

/** Scale (w, h) down so the longest side is at most `max` (0 = keep original). Never upscales. */
export function scaleTo(w: number, h: number, max: number): { w: number; h: number } {
  if (!max || Math.max(w, h) <= max) return { w, h };
  const s = max / Math.max(w, h);
  return { w: Math.max(1, Math.round(w * s)), h: Math.max(1, Math.round(h * s)) };
}

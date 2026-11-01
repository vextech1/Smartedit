import { zipSync } from 'fflate';

/** Store files in a ZIP without recompressing (PDFs and JPG/PNG are already compressed). Duplicate names get a suffix. */
export function zipFiles(files: { name: string; bytes: Uint8Array }[]): Uint8Array {
  const seen = new Map<string, number>();
  const entries: Record<string, Uint8Array> = {};
  for (const f of files) {
    const n = seen.get(f.name) ?? 0; seen.set(f.name, n + 1);
    const dot = f.name.lastIndexOf('.');
    entries[n === 0 ? f.name : `${f.name.slice(0, dot)}-${n + 1}${f.name.slice(dot)}`] = f.bytes;
  }
  return zipSync(entries, { level: 0 });
}

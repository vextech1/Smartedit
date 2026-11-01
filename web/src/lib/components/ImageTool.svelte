<script lang="ts">
  import FileDrop from './FileDrop.svelte';
  import { baseName, fmtBytes, scaleTo } from '#lib/core/format.ts';
  import { draw, loadBitmap, toBlob } from '#lib/core/image.ts';

  let { mode }: { mode: 'compress' | 'convert' } = $props();
  const EXT: Record<string, string> = { 'image/jpeg': 'jpg', 'image/png': 'png', 'image/webp': 'webp' };
  interface Result { name: string; before?: number; after?: number; url?: string; filename?: string; note?: string }

  let files = $state<File[]>([]);
  // svelte-ignore state_referenced_locally
  let fmt = $state(mode === 'convert' ? 'image/png' : 'image/jpeg');
  let quality = $state(75);
  // svelte-ignore state_referenced_locally
  let max = $state(mode === 'convert' ? 0 : 2000);
  let results = $state<Result[]>([]);
  let status = $state('');
  let busy = $state(false);
  let urls: string[] = [];

  function reset() { urls.forEach((u) => URL.revokeObjectURL(u)); urls = []; results = []; status = ''; }
  function pick(f: File[]) { reset(); files = f; }

  async function one(file: File): Promise<Result> {
    const bmp = await loadBitmap(file);
    const s = scaleTo(bmp.width, bmp.height, max);
    const canvas = draw(bmp, s.w, s.h, fmt === 'image/jpeg');
    bmp.close();
    const blob = await toBlob(canvas, fmt, quality / 100);
    if (!blob) throw new Error('encode failed');
    const url = URL.createObjectURL(blob); urls.push(url);
    return {
      name: file.name, before: file.size, after: blob.size, url,
      filename: baseName(file.name) + (mode === 'compress' ? '-compressed.' : '.') + (EXT[blob.type] ?? 'img'),
      note: blob.type !== fmt ? `Your browser saved this as ${blob.type.replace('image/', '').toUpperCase()} instead.` : ''
    };
  }

  async function run() {
    reset(); busy = true;
    for (let i = 0; i < files.length; i++) {
      status = `Working on ${i + 1} of ${files.length}…`;
      try { results.push(await one(files[i])); }
      catch { results.push({ name: files[i].name, note: 'This file could not be read as an image.' }); }
    }
    status = 'Done. Tap Download next to each result.'; busy = false;
  }
  const saved = (r: Result) => { const p = Math.round((1 - r.after! / r.before!) * 100); return p > 0 ? ` (${p}% smaller)` : p < 0 ? ` (${-p}% larger)` : ''; };
</script>

<FileDrop accept="image/*" label="Choose images" onfiles={pick} />
{#if files.length}<p class="muted">{files.length} {files.length === 1 ? 'image' : 'images'} selected</p>{/if}
<div class="controls">
  <label>Format
    <select bind:value={fmt}><option value="image/jpeg">JPG</option><option value="image/png">PNG</option><option value="image/webp">WebP</option></select></label>
  <label>Quality: {quality}%
    <input type="range" min="10" max="100" bind:value={quality} disabled={fmt === 'image/png'} /></label>
  <label>Longest side
    <select bind:value={max}><option value={0}>Original size</option><option value={3000}>3000 px</option><option value={2000}>2000 px</option><option value={1280}>1280 px</option><option value={800}>800 px</option></select></label>
</div>
<button class="btn" type="button" disabled={!files.length || busy} onclick={run}>{mode === 'compress' ? 'Compress' : 'Convert'}</button>
<p role="status" aria-live="polite" class="muted">{status}</p>
<ul class="rows">
  {#each results as r}
    <li class="row"><div class="grow"><strong>{r.name}</strong>
      {#if r.url}<div class="sub">{fmtBytes(r.before!)} → {fmtBytes(r.after!)}{saved(r)}</div>{/if}
      {#if r.note}<div class="sub warn">{r.note}</div>{/if}</div>
      {#if r.url}<a class="btn small" href={r.url} download={r.filename}>Download</a>{/if}</li>
  {/each}
</ul>

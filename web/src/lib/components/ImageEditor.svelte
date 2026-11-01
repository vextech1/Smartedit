<script lang="ts">
  import FileDrop from './FileDrop.svelte';
  import { baseName } from '#lib/core/format.ts';
  import { draw, loadBitmap, toBlob } from '#lib/core/image.ts';
  import { applyAdjust, cropRect, isNeutral, NO_ADJUST, type Adjust, type Rect } from '#lib/core/image-edit.ts';

  const PREVIEW = 720, MAX_SIDE = 4096, HISTORY = 10;
  let name = $state('');
  let loaded = $state(false);
  let adj = $state<Adjust>({ ...NO_ADJUST });
  let cropping = $state(false);
  let sel = $state<Rect | null>(null);
  let fmt = $state('image/jpeg'); let quality = $state(90);
  let msg = $state(''); let rev = $state(0); let undoCount = $state(0);
  let view: HTMLCanvasElement;
  let base: HTMLCanvasElement | null = null;
  let history: HTMLCanvasElement[] = [];
  let drag: { x: number; y: number } | null = null;
  let outUrl = $state('');

  const copyOf = (c: HTMLCanvasElement) => { const n = document.createElement('canvas'); n.width = c.width; n.height = c.height; n.getContext('2d')!.drawImage(c, 0, 0); return n; };
  function setBase(c: HTMLCanvasElement, keep = true) {
    if (keep && base) { history.push(base); if (history.length > HISTORY) { const old = history.shift(); if (old) old.width = 0; } }
    base = c; undoCount = history.length; rev++; sel = null;
  }
  function undo() { const p = history.pop(); if (p) { base = p; undoCount = history.length; rev++; sel = null; } }

  async function load(f: File[]) {
    msg = ''; history = []; undoCount = 0; adj = { ...NO_ADJUST }; cropping = false;
    try {
      const bmp = await loadBitmap(f[0]); const k = Math.min(1, MAX_SIDE / Math.max(bmp.width, bmp.height));
      const c = draw(bmp, Math.round(bmp.width * k), Math.round(bmp.height * k), true); bmp.close();
      name = f[0].name; base = null; setBase(c, false); loaded = true;
    } catch { msg = 'That file could not be read as an image.'; }
  }

  function transform(kind: 'left' | 'right' | 'flip') {
    if (!base) return;
    const sw = kind !== 'flip'; const c = document.createElement('canvas');
    c.width = sw ? base.height : base.width; c.height = sw ? base.width : base.height;
    const x = c.getContext('2d')!; x.translate(c.width / 2, c.height / 2);
    if (kind === 'left') x.rotate(-Math.PI / 2); else if (kind === 'right') x.rotate(Math.PI / 2); else x.scale(-1, 1);
    x.drawImage(base, -base.width / 2, -base.height / 2); setBase(c);
  }

  // Scale from the preview canvas back to the full-size image.
  const factor = () => (base ? base.width / view.width : 1);
  function applyCrop() {
    if (!base || !sel) return; const k = factor();
    const r = { x: Math.round(sel.x * k), y: Math.round(sel.y * k), w: Math.round(sel.w * k), h: Math.round(sel.h * k) };
    const c = document.createElement('canvas'); c.width = r.w; c.height = r.h;
    c.getContext('2d')!.drawImage(base, r.x, r.y, r.w, r.h, 0, 0, r.w, r.h); cropping = false; setBase(c);
  }

  $effect(() => {
    void rev; const a = { ...adj }; void sel;
    if (!base || !view) return;
    const k = Math.min(1, PREVIEW / Math.max(base.width, base.height));
    view.width = Math.round(base.width * k); view.height = Math.round(base.height * k);
    const ctx = view.getContext('2d')!; ctx.drawImage(base, 0, 0, view.width, view.height);
    if (!isNeutral(a)) { const d = ctx.getImageData(0, 0, view.width, view.height); applyAdjust(d.data, a); ctx.putImageData(d, 0, 0); }
    if (cropping && sel) {
      ctx.fillStyle = 'rgba(0,0,0,.45)'; ctx.fillRect(0, 0, view.width, view.height);
      ctx.drawImage(base, sel.x / k, sel.y / k, sel.w / k, sel.h / k, sel.x, sel.y, sel.w, sel.h);
      ctx.strokeStyle = '#ffd24a'; ctx.lineWidth = 2; ctx.strokeRect(sel.x, sel.y, sel.w, sel.h);
    }
  });

  const pos = (e: PointerEvent) => { const r = view.getBoundingClientRect(); return { x: (e.clientX - r.left) * view.width / r.width, y: (e.clientY - r.top) * view.height / r.height }; };
  function down(e: PointerEvent) { if (!cropping) return; view.setPointerCapture(e.pointerId); drag = pos(e); sel = null; }
  function move(e: PointerEvent) { if (!drag) return; const p = pos(e); sel = cropRect(drag.x, drag.y, p.x, p.y, view.width, view.height, 1); }
  function up() { drag = null; if (sel && (sel.w < 8 || sel.h < 8)) sel = null; }

  async function download() {
    if (!base) return; const full = copyOf(base);
    if (!isNeutral(adj)) { const x = full.getContext('2d')!; const d = x.getImageData(0, 0, full.width, full.height); applyAdjust(d.data, adj); x.putImageData(d, 0, 0); }
    const blob = await toBlob(full, fmt, quality / 100); full.width = 0;
    if (!blob) { msg = 'Your browser could not save that format. Try JPG or PNG.'; return; }
    if (outUrl) URL.revokeObjectURL(outUrl); outUrl = URL.createObjectURL(blob);
    const ext = blob.type === 'image/png' ? 'png' : blob.type === 'image/webp' ? 'webp' : 'jpg';
    const a = document.createElement('a'); a.href = outUrl; a.download = `${baseName(name)}-edited.${ext}`; a.click();
    msg = blob.type !== fmt ? `Saved as ${ext.toUpperCase()}: your browser doesn’t support the format you picked.` : 'Saved.';
  }
</script>

<FileDrop accept="image/*" multiple={false} label={loaded ? 'Choose a different image' : 'Choose an image'} hint="Nothing is uploaded." onfiles={load} />
<p role="status" aria-live="polite" class="muted">{msg}</p>
<div class="editor" hidden={!loaded}>
  <canvas bind:this={view} class="view" class:crop={cropping} onpointerdown={down} onpointermove={move} onpointerup={up} aria-label="Image preview"></canvas>
  <div class="bar">
    <button class="btn small" type="button" onclick={() => transform('left')}>↺ Rotate</button>
    <button class="btn small" type="button" onclick={() => transform('right')}>↻ Rotate</button>
    <button class="btn small" type="button" onclick={() => transform('flip')}>⇋ Flip</button>
    <button class="btn small" type="button" onclick={() => { cropping = !cropping; sel = null; }}>{cropping ? 'Cancel crop' : 'Crop'}</button>
    {#if cropping}<button class="btn small" type="button" disabled={!sel} onclick={applyCrop}>Apply crop</button>{/if}
    <button class="btn small" type="button" disabled={!undoCount} onclick={undo}>Undo</button>
  </div>
  {#if cropping}<p class="muted">Drag on the picture to choose the area to keep.</p>{/if}
  <div class="controls">
    <label>Brightness ({adj.brightness})<input type="range" min="-100" max="100" bind:value={adj.brightness} /></label>
    <label>Contrast ({adj.contrast})<input type="range" min="-100" max="100" bind:value={adj.contrast} /></label>
    <label>Colour ({adj.saturation})<input type="range" min="-100" max="100" bind:value={adj.saturation} disabled={adj.mode !== 'none'} /></label>
    <label>Look<select bind:value={adj.mode}><option value="none">Original colours</option><option value="gray">Black and white</option><option value="document">Clean document</option></select></label>
  </div>
  <div class="controls">
    <label>Save as<select bind:value={fmt}><option value="image/jpeg">JPG</option><option value="image/png">PNG</option><option value="image/webp">WebP</option></select></label>
    <label>Quality ({quality}%)<input type="range" min="40" max="100" bind:value={quality} disabled={fmt === 'image/png'} /></label>
  </div>
  <button class="btn" type="button" onclick={download}>Download</button>
</div>

<script lang="ts">
  import FileDrop from './FileDrop.svelte';
  import { baseName, fmtBytes } from '#lib/core/format.ts';
  import { jpegRecompressor, toBlob } from '#lib/core/image.ts';
  import { compressPdfImages, PRESETS, savingsLabel, type PresetKey } from '#lib/core/pdf-compress.ts';
  import { pagesToPdf } from '#lib/core/pdf.ts';
  import { closePdf, openPdf, renderPage } from '#lib/core/pdf-render.ts';
  import { pdfBlob, pdfErrorMessage, readBytes } from '#lib/core/files.ts';

  interface R { name: string; before?: number; after?: number; url?: string; filename?: string; note: string; bad?: boolean }
  let files = $state<File[]>([]);
  let preset = $state<PresetKey>('balanced');
  let method = $state<'keep' | 'max'>('keep');
  let results = $state<R[]>([]);
  let status = $state(''); let busy = $state(false);
  let urls: string[] = [];

  function reset() { urls.forEach((u) => URL.revokeObjectURL(u)); urls = []; results = []; status = ''; }
  function pick(f: File[]) { reset(); files = f.slice(0, 20); }

  async function rasterize(bytes: Uint8Array, name: string, i: number) {
    const P = PRESETS[preset], doc = await openPdf(bytes), pages = [];
    for (let n = 1; n <= doc.numPages; n++) {
      status = `File ${i + 1} of ${files.length}: page ${n} of ${doc.numPages}…`;
      const canvas = await renderPage(doc, n, { scale: P.dpi / 72 });
      const blob = await toBlob(canvas, 'image/jpeg', P.quality);
      pages.push({ bytes: new Uint8Array(await blob!.arrayBuffer()), wPt: canvas.width * 72 / P.dpi, hPt: canvas.height * 72 / P.dpi });
      canvas.width = canvas.height = 0;
    }
    closePdf(doc);
    return pagesToPdf(pages);
  }

  async function run() {
    reset(); busy = true;
    for (let i = 0; i < files.length; i++) {
      const f = files[i];
      try {
        status = `Compressing ${i + 1} of ${files.length}…`;
        const bytes = await readBytes(f);
        let out: Uint8Array, found = 0;
        if (method === 'keep') {
          const P = PRESETS[preset];
          const r = await compressPdfImages(bytes, jpegRecompressor(P.quality, P.maxSide));
          out = r.bytes; found = r.found;
        } else out = await rasterize(bytes, f.name, i);
        const kind = savingsLabel(f.size, out.length);
        if (kind === 'optimized') {
          results.push({ name: f.name, note: method === 'keep' && found === 0
            ? 'Already optimized. This PDF has no pictures that can be shrunk, so the original was kept.'
            : 'Already optimized. The compressed copy was not smaller, so the original was kept.' });
        } else {
          const url = URL.createObjectURL(pdfBlob(out)); urls.push(url);
          results.push({ name: f.name, before: f.size, after: out.length, url, filename: `${baseName(f.name)}-compressed.pdf`, note: kind === 'barely' ? 'Barely smaller.' : '' });
        }
      } catch (e) { results.push({ name: f.name, note: pdfErrorMessage(e, f.name), bad: true }); }
    }
    status = 'Done.'; busy = false;
  }
  const pct = (r: R) => Math.round((1 - r.after! / r.before!) * 100);
</script>

<FileDrop accept="application/pdf" label="Choose PDF files" hint="Up to 20 files. Nothing is uploaded." onfiles={pick} />
{#if files.length}<p class="muted">{files.length} {files.length === 1 ? 'PDF' : 'PDFs'} selected</p>{/if}
<div class="controls">
  <label>Amount
    <select bind:value={preset}>{#each Object.entries(PRESETS) as [k, p]}<option value={k}>{p.label}</option>{/each}</select></label>
  <label>Method
    <select bind:value={method}><option value="keep">Keep text (recommended)</option><option value="max">Maximum (turns pages into pictures)</option></select></label>
</div>
{#if method === 'max'}<p class="warn">Maximum compression turns every page into a picture. Text can no longer be selected or searched.</p>{/if}
<button class="btn" type="button" disabled={!files.length || busy} onclick={run}>Compress</button>
<p role="status" aria-live="polite" class="muted">{status}</p>
<ul class="rows">
  {#each results as r}
    <li class="row"><div class="grow"><strong>{r.name}</strong>
      {#if r.url}<div class="sub">{fmtBytes(r.before!)} → {fmtBytes(r.after!)} ({pct(r)}% smaller)</div>{/if}
      {#if r.note}<div class="sub" class:warn={r.bad}>{r.note}</div>{/if}</div>
      {#if r.url}<a class="btn small" href={r.url} download={r.filename}>Download</a>{/if}</li>
  {/each}
</ul>

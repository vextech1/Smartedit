<script lang="ts">
  import FileDrop from './FileDrop.svelte';
  import ResultRow from './ResultRow.svelte';
  import { baseName } from '#lib/core/format.ts';
  import { countPages, everyPage, parseRanges, splitPdf } from '#lib/core/pdf-edit.ts';
  import { pdfBlob, pdfErrorMessage, readBytes, zipBlob } from '#lib/core/files.ts';
  import { zipFiles } from '#lib/core/zip.ts';

  interface Out { name: string; bytes: number; url: string }
  let file = $state<File | null>(null);
  let total = $state(0);
  let mode = $state<'every' | 'ranges'>('every');
  let ranges = $state('');
  let status = $state(''); let bad = $state(false); let busy = $state(false);
  let outs = $state<Out[]>([]);
  let zip = $state<Out | null>(null);
  let bytes: Uint8Array | null = null;

  function clear() { outs.forEach((o) => URL.revokeObjectURL(o.url)); if (zip) URL.revokeObjectURL(zip.url); outs = []; zip = null; status = ''; bad = false; }

  async function load(files: File[]) {
    clear(); file = files[0]; total = 0;
    try { bytes = await readBytes(file); total = await countPages(bytes); }
    catch (e) { bad = true; status = pdfErrorMessage(e, file.name); }
  }

  async function run() {
    clear(); busy = true; status = 'Splitting…';
    try {
      const groups = mode === 'every' ? everyPage(total) : parseRanges(ranges, total);
      const parts = await splitPdf(bytes!, groups);
      const base = baseName(file!.name);
      const label = (g: number[]) => (g.length === 1 ? `page-${g[0] + 1}` : `pages-${g[0] + 1}-${g[g.length - 1] + 1}`);
      const named = parts.map((b, i) => ({ name: `${base}-${label(groups[i])}.pdf`, bytes: b }));
      outs = named.map((n) => ({ name: n.name, bytes: n.bytes.length, url: URL.createObjectURL(pdfBlob(n.bytes)) }));
      if (named.length > 1) { const z = zipFiles(named); zip = { name: `${base}-split.zip`, bytes: z.length, url: URL.createObjectURL(zipBlob(z)) }; }
      status = `Done. ${named.length} ${named.length === 1 ? 'file' : 'files'} ready.`;
    } catch (e) {
      bad = true; status = e instanceof RangeError ? e.message : pdfErrorMessage(e, file!.name);
    }
    busy = false;
  }
</script>

<FileDrop accept="application/pdf" multiple={false} label={file ? 'Choose a different PDF' : 'Choose a PDF'} hint="Nothing is uploaded." onfiles={load} />
{#if total}
  <p class="muted">{file?.name}: {total} {total === 1 ? 'page' : 'pages'}</p>
  <div class="controls">
    <label>Split by
      <select bind:value={mode}><option value="every">Every page</option><option value="ranges">Page ranges</option></select></label>
    {#if mode === 'ranges'}
      <label>Pages<input type="text" inputmode="numeric" placeholder="e.g. 1-3, 5, 7-9" bind:value={ranges} /></label>
    {/if}
  </div>
  <p class="muted">{mode === 'every' ? 'Each page becomes its own PDF.' : 'Each comma-separated range becomes its own PDF.'}</p>
  <button class="btn" type="button" disabled={busy} onclick={run}>Split PDF</button>
{/if}
<p role="status" aria-live="polite" class:warn={bad} class:muted={!bad}>{status}</p>
{#if zip}<div style="margin-bottom:10px"><ResultRow name={zip.name} bytes={zip.bytes} url={zip.url} filename={zip.name} label="Download all (ZIP)" /></div>{/if}
<ul class="rows">{#each outs as o}<li><ResultRow name={o.name} bytes={o.bytes} url={o.url} filename={o.name} /></li>{/each}</ul>

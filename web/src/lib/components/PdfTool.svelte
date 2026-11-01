<script lang="ts">
  import FileDrop from './FileDrop.svelte';
  import { fmtBytes, scaleTo } from '#lib/core/format.ts';
  import { draw, loadBitmap, toBlob } from '#lib/core/image.ts';
  import { imagesToPdf, mergePdfs, UnreadablePdfError, type JpegItem, type PageSize } from '#lib/core/pdf.ts';

  let { mode }: { mode: 'images-to-pdf' | 'merge-pdf' } = $props();
  // svelte-ignore state_referenced_locally
  const isImages = mode === 'images-to-pdf';
  interface Item { file: File; thumb?: string }

  let items = $state<Item[]>([]);
  let size = $state<PageSize>('a4');
  let margin = $state(24);
  let status = $state(''); let bad = $state(false); let busy = $state(false);
  let result = $state<{ url: string; name: string; bytes: number } | null>(null);

  function clear() { if (result) URL.revokeObjectURL(result.url); result = null; status = ''; bad = false; }
  function add(files: File[]) { clear(); items.push(...files.map((file) => ({ file, thumb: isImages ? URL.createObjectURL(file) : undefined }))); }
  function swap(a: number, b: number) { [items[a], items[b]] = [items[b], items[a]]; }
  function remove(i: number) { const [it] = items.splice(i, 1); if (it.thumb) URL.revokeObjectURL(it.thumb); }

  async function run() {
    clear(); busy = true;
    try {
      let bytes: Uint8Array;
      if (!isImages) {
        status = 'Merging…';
        bytes = await mergePdfs(await Promise.all(items.map(async (it) => ({ name: it.file.name, bytes: new Uint8Array(await it.file.arrayBuffer()) }))));
      } else {
        const jpegs: JpegItem[] = [];
        for (let i = 0; i < items.length; i++) {
          status = `Preparing image ${i + 1} of ${items.length}…`;
          const bmp = await loadBitmap(items[i].file);
          const s = scaleTo(bmp.width, bmp.height, 2480);
          const blob = await toBlob(draw(bmp, s.w, s.h, true), 'image/jpeg', 0.9);
          bmp.close();
          jpegs.push({ bytes: new Uint8Array(await blob!.arrayBuffer()), width: s.w, height: s.h });
        }
        status = 'Building PDF…';
        bytes = await imagesToPdf(jpegs, { size, margin });
      }
      const blob = new Blob([bytes as BlobPart], { type: 'application/pdf' });
      result = { url: URL.createObjectURL(blob), name: isImages ? 'images.pdf' : 'merged.pdf', bytes: blob.size };
      status = 'Done. Tap Download to save your PDF.';
    } catch (e) {
      bad = true;
      status = e instanceof UnreadablePdfError
        ? `“${e.fileName}” could not be read. It may be damaged or password-protected.`
        : 'Something went wrong. Try fewer or smaller files.';
    }
    busy = false;
  }
</script>

<FileDrop accept={isImages ? 'image/*' : 'application/pdf'} label={isImages ? 'Choose images' : 'Choose PDF files'} hint="Nothing is uploaded. Use the arrows to set the order." onfiles={add} />
{#if isImages}
  <div class="controls">
    <label>Page size<select bind:value={size}><option value="a4">A4</option><option value="letter">Letter</option><option value="fit">Fit to image</option></select></label>
    <label>Margin<select bind:value={margin}><option value={0}>None</option><option value={24}>Small</option><option value={48}>Large</option></select></label>
  </div>
{/if}
<ul class="rows">
  {#each items as it, i (it.file)}
    <li class="row">
      {#if it.thumb}<img class="thumb" src={it.thumb} alt="" />{:else}<span class="mark" aria-hidden="true">PDF</span>{/if}
      <div class="grow"><strong>{it.file.name}</strong><div class="sub">{fmtBytes(it.file.size)}</div></div>
      <button type="button" class="icon" aria-label="Move up" disabled={i === 0} onclick={() => swap(i, i - 1)}>▲</button>
      <button type="button" class="icon" aria-label="Move down" disabled={i === items.length - 1} onclick={() => swap(i, i + 1)}>▼</button>
      <button type="button" class="icon" aria-label="Remove" onclick={() => remove(i)}>✕</button>
    </li>
  {/each}
</ul>
<button class="btn" type="button" style="margin-top:14px" disabled={busy || items.length < (isImages ? 1 : 2)} onclick={run}>{isImages ? 'Create PDF' : 'Merge PDFs'}</button>
<p role="status" aria-live="polite" class:warn={bad} class:muted={!bad}>{status}</p>
{#if result}
  <div class="row"><div class="grow"><strong>{result.name}</strong><div class="sub">{fmtBytes(result.bytes)}</div></div>
    <a class="btn small" href={result.url} download={result.name}>Download</a></div>
{/if}

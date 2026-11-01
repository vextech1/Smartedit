<script lang="ts">
  import FileDrop from './FileDrop.svelte';
  import ResultRow from './ResultRow.svelte';
  import { baseName } from '#lib/core/format.ts';
  import { toBlob } from '#lib/core/image.ts';
  import { closePdf, openPdf, renderPage } from '#lib/core/pdf-render.ts';
  import { pdfErrorMessage, readBytes, zipBlob } from '#lib/core/files.ts';
  import { zipFiles } from '#lib/core/zip.ts';

  let file = $state<File | null>(null);
  let fmt = $state<'image/jpeg' | 'image/png'>('image/jpeg');
  let dpi = $state(150);
  let status = $state(''); let bad = $state(false); let busy = $state(false);
  let out = $state<{ name: string; bytes: number; url: string; count: number } | null>(null);

  function clear() { if (out) URL.revokeObjectURL(out.url); out = null; status = ''; bad = false; }
  function pick(f: File[]) { clear(); file = f[0]; }

  async function run() {
    clear(); busy = true;
    try {
      const doc = await openPdf(await readBytes(file!));
      const ext = fmt === 'image/png' ? 'png' : 'jpg', base = baseName(file!.name), pad = String(doc.numPages).length;
      const files: { name: string; bytes: Uint8Array }[] = [];
      for (let i = 1; i <= doc.numPages; i++) {
        status = `Converting page ${i} of ${doc.numPages}…`;
        const canvas = await renderPage(doc, i, { scale: dpi / 72 });
        const blob = await toBlob(canvas, fmt, 0.9);
        canvas.width = canvas.height = 0; // free the pixels right away
        if (!blob) throw new Error('encode failed');
        files.push({ name: `${base}-page-${String(i).padStart(pad, '0')}.${ext}`, bytes: new Uint8Array(await blob.arrayBuffer()) });
      }
      closePdf(doc);
      const z = zipFiles(files);
      out = { name: `${base}-images.zip`, bytes: z.length, url: URL.createObjectURL(zipBlob(z)), count: files.length };
      status = `Done. ${files.length} ${files.length === 1 ? 'image' : 'images'} in the ZIP.`;
    } catch (e) { bad = true; status = pdfErrorMessage(e, file!.name); }
    busy = false;
  }
</script>

<FileDrop accept="application/pdf" multiple={false} label={file ? 'Choose a different PDF' : 'Choose a PDF'} hint="Nothing is uploaded." onfiles={pick} />
{#if file}
  <p class="muted">{file.name}</p>
  <div class="controls">
    <label>Format<select bind:value={fmt}><option value="image/jpeg">JPG</option><option value="image/png">PNG</option></select></label>
    <label>Resolution<select bind:value={dpi}><option value={100}>Screen (100 dpi)</option><option value={150}>Standard (150 dpi)</option><option value={220}>High (220 dpi)</option></select></label>
  </div>
  <button class="btn" type="button" disabled={busy} onclick={run}>Convert to images</button>
{/if}
<p role="status" aria-live="polite" class:warn={bad} class:muted={!bad}>{status}</p>
{#if out}<ResultRow name={out.name} bytes={out.bytes} url={out.url} filename={out.name} label="Download ZIP" />{/if}

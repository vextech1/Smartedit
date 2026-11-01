<script lang="ts">
  import FileDrop from './FileDrop.svelte';
  import { baseName } from '#lib/core/format.ts';
  import { draw, loadBitmap } from '#lib/core/image.ts';
  import { joinPages, createOcr, OCR_LANGS } from '#lib/core/ocr.ts';
  import { closePdf, openPdf, renderPage } from '#lib/core/pdf-render.ts';
  import { pdfErrorMessage, readBytes } from '#lib/core/files.ts';

  const MAX_PDF_PAGES = 20;
  interface B { name: string; text: string; bad?: boolean }
  let files = $state<File[]>([]);
  let lang = $state('eng');
  let blocks = $state<B[]>([]);
  let status = $state(''); let pct = $state(0); let busy = $state(false);
  let copied = $state(-1);

  function pick(f: File[]) { files = f; blocks = []; status = ''; pct = 0; }

  async function run() {
    busy = true; blocks = []; pct = 0; status = 'Starting the text engine… (first use downloads it)';
    let ocr: Awaited<ReturnType<typeof createOcr>> | null = null;
    try {
      ocr = await createOcr(lang, (p, s) => { if (s.includes('recogniz')) pct = Math.round(p * 100); else status = s.charAt(0).toUpperCase() + s.slice(1) + '…'; });
      for (let i = 0; i < files.length; i++) {
        const f = files[i]; const pages: string[] = [];
        try {
          if (f.type === 'application/pdf') {
            const doc = await openPdf(await readBytes(f));
            const n = Math.min(doc.numPages, MAX_PDF_PAGES);
            for (let p = 1; p <= n; p++) {
              status = `${f.name}: page ${p} of ${n}…`;
              const c = await renderPage(doc, p, { scale: 2.5 });
              pages.push(await ocr.read(c)); c.width = c.height = 0;
            }
            const more = doc.numPages > n; closePdf(doc);
            blocks.push({ name: f.name, text: joinPages(pages) + (more ? `\n\n(Only the first ${n} pages were read.)` : '') });
          } else {
            status = `Reading ${f.name}…`;
            const bmp = await loadBitmap(f);
            const k = Math.min(1, 3000 / Math.max(bmp.width, bmp.height));
            const c = draw(bmp, Math.round(bmp.width * k), Math.round(bmp.height * k), true); bmp.close();
            blocks.push({ name: f.name, text: (await ocr.read(c)).trim() }); c.width = c.height = 0;
          }
        } catch (e) { blocks.push({ name: f.name, text: pdfErrorMessage(e, f.name), bad: true }); }
      }
      status = 'Done.';
    } catch { status = 'The text engine could not start. Check your connection and try again.'; }
    finally { await ocr?.stop(); busy = false; }
  }

  async function copy(i: number) { try { await navigator.clipboard.writeText(blocks[i].text); copied = i; setTimeout(() => (copied = -1), 1500); } catch { /* clipboard blocked */ } }
  const href = (t: string) => URL.createObjectURL(new Blob([t], { type: 'text/plain;charset=utf-8' }));
  function save(i: number) { const a = document.createElement('a'); a.href = href(blocks[i].text); a.download = baseName(blocks[i].name) + '.txt'; a.click(); setTimeout(() => URL.revokeObjectURL(a.href), 2000); }
</script>

<FileDrop accept="image/*,application/pdf" label="Choose photos or a PDF" hint="Your files are never uploaded. Reading happens on your device." onfiles={pick} />
{#if files.length}<p class="muted">{files.length} {files.length === 1 ? 'file' : 'files'} selected</p>{/if}
<div class="controls">
  <label>Language<select bind:value={lang}>{#each OCR_LANGS as l}<option value={l.code}>{l.label}</option>{/each}</select></label>
</div>
<button class="btn" type="button" disabled={!files.length || busy} onclick={run}>Extract text</button>
<p role="status" aria-live="polite" class="muted">{status}{#if busy && pct}  {pct}%{/if}</p>
{#each blocks as b, i}
  <h2 style="font-size:1.05rem">{b.name}</h2>
  <textarea class="out" readonly rows="10" aria-label="Text from {b.name}" value={b.text || '(no text found)'}></textarea>
  {#if !b.bad}<div class="bar"><button class="btn small" type="button" onclick={() => copy(i)}>{copied === i ? 'Copied' : 'Copy'}</button>
    <button class="btn small" type="button" onclick={() => save(i)}>Download .txt</button></div>{/if}
{/each}
<p class="muted" style="margin-top:18px">The first time you use a language, a small engine and that language’s data (a few MB) are downloaded from a public CDN and then kept by your browser. Your images and text are not sent anywhere.</p>

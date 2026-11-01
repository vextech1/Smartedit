<script lang="ts">
  import ImageTool from '#lib/components/ImageTool.svelte';
  import PdfTool from '#lib/components/PdfTool.svelte';
  import PageEditor from '#lib/components/PageEditor.svelte';
  import SplitTool from '#lib/components/SplitTool.svelte';
  import PdfToImages from '#lib/components/PdfToImages.svelte';
  import CompressPdf from '#lib/components/CompressPdf.svelte';
  import OcrTool from '#lib/components/OcrTool.svelte';
  import ImageEditor from '#lib/components/ImageEditor.svelte';
  let { data } = $props();
  const t = $derived(data.tool);
</script>
<svelte:head><title>{t.title}, free and private | Smart Edit</title>
<meta name="description" content="{t.intro} Free, private, no upload." /></svelte:head>

<h1>{t.h1}</h1>
<p class="lede">{t.intro}</p>
{#key t.slug}
  {#if t.mode === 'ocr'}<OcrTool />
  {:else if t.mode === 'edit'}<ImageEditor />
  {:else if t.kind === 'image'}<ImageTool mode={t.mode as 'compress' | 'convert'} />
  {:else if t.mode === 'organize-pdf'}<PageEditor />
  {:else if t.mode === 'split-pdf'}<SplitTool />
  {:else if t.mode === 'pdf-to-images'}<PdfToImages />
  {:else if t.mode === 'compress-pdf'}<CompressPdf />
  {:else}<PdfTool mode={t.mode as 'images-to-pdf' | 'merge-pdf'} />{/if}
{/key}
<p class="muted">Your files never leave your device. Nothing is uploaded, and there is no sign-up.</p>
<h2>How it works</h2>
<ol class="steps">{#each t.steps as s}<li>{s}</li>{/each}</ol>
{#if t.note}<p>{t.note}</p>{/if}

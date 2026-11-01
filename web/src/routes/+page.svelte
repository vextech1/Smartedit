<script lang="ts">
  import { tools } from '#lib/tools.ts';
  import { SITE } from '#lib/site.ts';
</script>
<svelte:head><title>{SITE.name}: scan, convert, compress and edit documents</title>
<meta name="description" content="Free document and image tools that run in your browser. Nothing is uploaded." /></svelte:head>

<h1>Documents and photos, sorted. Nothing leaves your device.</h1>
<p class="lede">Compress, convert and combine files right here in the browser. No sign-up, no uploads, no waiting on a server.</p>
<a class="btn" href="/tools">Open the tools</a>

<h2>Free tools in your browser</h2>
<div class="grid">
  {#each tools as t}
    <a class="card" href="/tools/{t.slug}"><h3>{t.title}</h3><p>{t.short}</p></a>
  {/each}
</div>
<p class="muted" style="margin-top:28px">Coming next: PDF page editing, the document scanner, text recognition and an image editor, matching the Smart Edit Android app.</p>

<script lang="ts">
  import { tools } from '#lib/tools.ts';
  const groups = ['PDF', 'Images'] as const;
</script>
<svelte:head><title>Free tools | Smart Edit</title><meta name="description" content="Free in-browser PDF and image tools. Private, no upload." /></svelte:head>
<h1>Free tools</h1>
<p class="lede">Everything runs on your device. Your files are never uploaded.</p>
{#each groups as g}
  <h2>{g}</h2>
  <div class="grid">
    {#each tools.filter((t) => t.group === g) as t}
      <a class="card" href="/tools/{t.slug}"><h3>{t.title}</h3><p>{t.short}</p></a>
    {/each}
  </div>
{/each}

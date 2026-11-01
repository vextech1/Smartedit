<script lang="ts">
  import '../app.css';
  import { page } from '$app/state';
  import { SITE } from '#lib/site.ts';
  let { children } = $props();
  const links = [['/tools', 'Tools'], ['/faq', 'FAQ'], ['/privacy', 'Privacy'], ['/contact', 'Contact']];
</script>

<header class="site"><div class="wrap">
  <a class="brand" href="/">{SITE.name}</a>
  <nav aria-label="Main">
    {#each links as [href, label]}
      <a {href} aria-current={page.url.pathname.startsWith(href) ? 'page' : undefined}>{label}</a>
    {/each}
  </nav>
</div></header>
<main><div class="wrap">{@render children()}</div></main>
<footer class="site"><div class="wrap">
  <a href="/tools">Free tools</a><a href="/faq">FAQ</a><a href="/privacy">Privacy</a><a href="/terms">Terms</a><a href="/contact">Contact</a>
  <p>© 2026 {SITE.name}. Files you scan, convert or edit stay on your device.</p>
</div></footer>

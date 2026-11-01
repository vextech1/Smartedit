<script lang="ts">
  import { fmtBytes } from '#lib/core/format.ts';
  let { name, bytes, url, filename, label = 'Download' }: { name: string; bytes: number; url: string; filename: string; label?: string } = $props();
</script>
<div class="row"><div class="grow"><strong>{name}</strong><div class="sub">{fmtBytes(bytes)}</div></div>
  <a class="btn small" href={url} download={filename}>{label}</a></div>

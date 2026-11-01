<script lang="ts">
  let { accept, multiple = true, label, hint = 'Nothing is uploaded.', onfiles }:
    { accept: string; multiple?: boolean; label: string; hint?: string; onfiles: (files: File[]) => void } = $props();
  let over = $state(false);
  let input: HTMLInputElement;
  const ok = (f: File) => accept.split(',').some((a) => (a.endsWith('/*') ? f.type.startsWith(a.slice(0, -1)) : f.type === a.trim()));
  function take(list: FileList | null) {
    const files = Array.from(list ?? []).filter(ok);
    if (files.length) onfiles(multiple ? files : files.slice(0, 1));
  }
</script>

<input bind:this={input} class="sr" id="file" type="file" {accept} {multiple}
  onchange={() => { take(input.files); input.value = ''; }} />
<label class="drop" class:over for="file"
  ondragover={(e) => { e.preventDefault(); over = true; }} ondragleave={() => (over = false)}
  ondrop={(e) => { e.preventDefault(); over = false; take(e.dataTransfer?.files ?? null); }}>
  {label}<small>{hint}</small>
</label>

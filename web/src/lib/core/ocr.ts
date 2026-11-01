// Text recognition with Tesseract.js (WASM, runs in a worker). The engine and the chosen language's data are downloaded
// from a public CDN the first time they're needed and cached by the browser; the image itself is never uploaded.
export const OCR_LANGS: { code: string; label: string }[] = [
  { code: 'eng', label: 'English' }, { code: 'spa', label: 'Spanish' }, { code: 'fra', label: 'French' },
  { code: 'deu', label: 'German' }, { code: 'ita', label: 'Italian' }, { code: 'por', label: 'Portuguese' },
  { code: 'nld', label: 'Dutch' }, { code: 'rus', label: 'Russian' }, { code: 'tur', label: 'Turkish' },
  { code: 'ara', label: 'Arabic' }, { code: 'hin', label: 'Hindi' }, { code: 'chi_sim', label: 'Chinese (Simplified)' },
  { code: 'chi_tra', label: 'Chinese (Traditional)' }, { code: 'jpn', label: 'Japanese' }, { code: 'kor', label: 'Korean' }
];

/** Join per-page text with a visible page break, trimming blank pages. */
export function joinPages(pages: string[]): string {
  const clean = pages.map((p) => p.trim());
  if (clean.length === 1) return clean[0];
  return clean.map((t, i) => `--- Page ${i + 1} ---\n${t || '(no text found)'}`).join('\n\n');
}

export async function createOcr(lang: string, onProgress: (p: number, status: string) => void) {
  const { createWorker } = await import('tesseract.js');
  const worker = await createWorker(lang, 1, {
    logger: (m: { status: string; progress: number }) => onProgress(m.progress ?? 0, m.status ?? '')
  });
  return {
    async read(image: HTMLCanvasElement | Blob): Promise<string> { return (await worker.recognize(image)).data.text; },
    stop: () => worker.terminate()
  };
}

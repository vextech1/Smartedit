export async function readBytes(file: Blob): Promise<Uint8Array> { return new Uint8Array(await file.arrayBuffer()); }
export const pdfBlob = (bytes: Uint8Array) => new Blob([bytes as BlobPart], { type: 'application/pdf' });
export const zipBlob = (bytes: Uint8Array) => new Blob([bytes as BlobPart], { type: 'application/zip' });

/** Plain-language message for a PDF that failed to open or save. */
export function pdfErrorMessage(e: unknown, name: string): string {
  const n = (e as { name?: string })?.name ?? '';
  const m = String((e as { message?: string })?.message ?? '');
  if (n === 'PasswordException' || /encrypt|password/i.test(m))
    return `“${name}” is password-protected. Remove the password in your PDF app first, then try again.`;
  if (n === 'InvalidPDFException' || /parse|invalid pdf|no pdf header/i.test(m)) return `“${name}” could not be read. It may be damaged.`;
  return 'Something went wrong. Try a smaller file.';
}

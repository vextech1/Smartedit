// Single source of truth for every tool: drives the nav, home grid, tool pages, sitemap and tests.
export type ToolKind = 'image' | 'pdf';
export interface Tool {
  slug: string;
  kind: ToolKind;
  mode: string;          // 'compress' | 'convert' | 'images-to-pdf' | 'merge-pdf'
  title: string;
  short: string;
  h1: string;
  intro: string;
  steps: string[];
  note?: string;
  group: 'Images' | 'PDF';
}

export const tools: Tool[] = [
  { slug: 'compress-image', kind: 'image', mode: 'compress', group: 'Images',
    title: 'Compress images', short: 'Shrink photos to save space.',
    h1: 'Compress images', intro: 'Make photos smaller so they are easy to send and store.',
    steps: ['Choose one or more photos.', 'Pick the quality and the longest side.', 'Compress, then download each result.'],
    note: 'PNG is lossless, so the quality slider has no effect on PNG output.' },
  { slug: 'convert-image', kind: 'image', mode: 'convert', group: 'Images',
    title: 'Convert images', short: 'JPG, PNG and WebP.',
    h1: 'Convert images', intro: 'Switch photos between JPG, PNG and WebP.',
    steps: ['Choose one or more images.', 'Pick the format you want.', 'Convert, then download each result.'],
    note: 'Transparent areas turn white when saving as JPG.' },
  { slug: 'images-to-pdf', kind: 'pdf', mode: 'images-to-pdf', group: 'PDF',
    title: 'Images to PDF', short: 'Pictures into one PDF.',
    h1: 'Images to PDF', intro: 'Put pictures into a single PDF, one per page, in the order you choose.',
    steps: ['Choose your images.', 'Use ▲ and ▼ to set the page order.', 'Pick a page size and create the PDF.'] },
  { slug: 'merge-pdf', kind: 'pdf', mode: 'merge-pdf', group: 'PDF',
    title: 'Merge PDFs', short: 'Join PDF files into one.',
    h1: 'Merge PDFs', intro: 'Combine several PDF files into one, in the order you choose.',
    steps: ['Choose two or more PDF files.', 'Use ▲ and ▼ to set the order.', 'Merge and download the result.'],
    note: 'Password-protected or damaged files can’t be merged. Remove the password in your PDF app first.' }
];

export const toolBySlug = (slug: string) => tools.find((t) => t.slug === slug);

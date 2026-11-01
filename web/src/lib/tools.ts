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
    note: 'Password-protected or damaged files can’t be merged. Remove the password in your PDF app first.' },
  { slug: 'organize-pdf', kind: 'pdf', mode: 'organize-pdf', group: 'PDF',
    title: 'Organize PDF pages', short: 'Rotate, reorder, delete, extract.',
    h1: 'Organize PDF pages', intro: 'Rotate, reorder, remove or extract pages, with a preview of every page.',
    steps: ['Choose a PDF.', 'Rotate, move or remove pages. Tick pages to extract them.', 'Save the PDF or extract the selected pages.'],
    note: 'Password-protected PDFs can’t be edited yet. Remove the password in your PDF app first.' },
  { slug: 'split-pdf', kind: 'pdf', mode: 'split-pdf', group: 'PDF',
    title: 'Split PDF', short: 'One PDF per page or range.',
    h1: 'Split a PDF', intro: 'Break a PDF into single pages or page ranges.',
    steps: ['Choose a PDF.', 'Pick every page, or type ranges like 1-3, 5.', 'Download each file, or all of them as a ZIP.'] },
  { slug: 'pdf-to-images', kind: 'pdf', mode: 'pdf-to-images', group: 'PDF',
    title: 'PDF to images', short: 'Pages as JPG or PNG.',
    h1: 'PDF to images', intro: 'Turn every page of a PDF into a JPG or PNG picture.',
    steps: ['Choose a PDF.', 'Pick the format and resolution.', 'Download the pictures as a ZIP.'],
    note: 'Long PDFs at high resolution use a lot of memory. If it stalls, pick a lower resolution.' },
  { slug: 'compress-pdf', kind: 'pdf', mode: 'compress-pdf', group: 'PDF',
    title: 'Compress PDF', short: 'Make PDFs smaller.',
    h1: 'Compress PDF files', intro: 'Shrink PDFs so they are easy to email and store. Text stays selectable by default.',
    steps: ['Choose up to 20 PDFs.', 'Pick how much to shrink them.', 'Compress, then download each result.'],
    note: 'If a file can’t be made smaller, the original is kept and you are told. “Maximum” turns pages into pictures, so text can no longer be selected.' },
  { slug: 'extract-text', kind: 'image', mode: 'ocr', group: 'Images',
    title: 'Extract text (OCR)', short: 'Read text from photos and scans.',
    h1: 'Extract text from photos and PDFs', intro: 'Read the text in a photo, a scan or a PDF, then copy it or save it as a .txt file.',
    steps: ['Choose photos or a PDF.', 'Pick the language of the text.', 'Extract, then copy or download the text.'],
    note: 'Clear, well-lit, straight-on photos give the best results. PDFs are read up to 20 pages.' },
  { slug: 'edit-image', kind: 'image', mode: 'edit', group: 'Images',
    title: 'Image editor', short: 'Crop, rotate and adjust.',
    h1: 'Edit an image', intro: 'Crop, rotate, flip and adjust brightness, contrast and colour. Try Clean document for scans and receipts.',
    steps: ['Choose an image.', 'Crop, rotate or adjust it.', 'Pick a format and download.'] }
];

export const toolBySlug = (slug: string) => tools.find((t) => t.slug === slug);

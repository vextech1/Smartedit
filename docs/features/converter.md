# Feature 4 — Converter

## Scope

Converter is a native feature boundary and does not depend on the legacy Flutter service structure.

Supported conversions:

- One or more images → multi-page PDF.
- One or more images → JPEG, PNG, or WebP (one output per source image).
- PDF → JPEG page images in a ZIP archive.
- PDF → PNG page images in a ZIP archive.
- PDF → WebP page images in a ZIP archive.
- PDF → DOCX. Headings, bullets, bold and italic, centred lines, indents and page breaks are rebuilt from the PDF's text layer. Pages with no text (scans) are kept as one picture each, which a switch can turn off (on by default).
- PDF → Markdown (`.md`, the default) or plain text (`.txt`), UTF-8, with optional page markers. A PDF with no text layer reports that it is probably scanned pictures instead of writing an empty file.

## User controls

- Ordered multi-image selection.
- PDF selection using Storage Access Framework.
- Persistable read permissions where the provider allows them.
- JPEG/WebP/PDF image quality.
- PDF page-image resolution presets: Screen (100 dpi), Standard (150 dpi, the default) and High (220 dpi). The request still accepts 72–300 dpi.
- PDF to text: Markdown or plain text, and a switch for page markers.
- PDF to Word: a switch to keep pages without text as pictures (108 dpi JPEG, scaled to fit the margins).
- Page selection expressions (`all`, `1-3,5`, etc.).
- Password for protected PDFs.
- Foreground progress and cancellation.
- Open/share every completed output.
- Recent conversion job state.

## Correctness invariants

- Conversion output is always written into EditPro managed storage.
- Completed output is registered in Files/Search and conversion History.
- Failed or cancelled operations remove partial generated output.
- Failed/cancelled jobs remain recorded with terminal state.
- Multi-image PDF generation loads images sequentially rather than retaining every bitmap at once.
- EXIF orientation is applied to image conversion and image-to-PDF conversion.
- PNG pages are rendered directly from PDF without a JPEG intermediate.
- WebP pages use a lossless PNG render intermediate before WebP encoding.
- Page ranges are 1-based to the user and validated before rendering.
- DOCX, Markdown and text share one layout analysis (`PdfTextLayout`, a port of the EditPro site's), so all three agree on what is a heading, a bullet or a paragraph. It does not claim layout fidelity: columns, tables and exact positions are not preserved. Headings come from text size relative to the body size, or from a short all-bold line.
- Text is read with position, size and bold/italic through a `PDFTextStripper` subclass (`PdfEngine.textPages`); it is only as good as the PDF's text layer.
- There is no page cap on text conversion (the site caps at 500 pages), so very long PDFs use more memory.

## Tests

Pure JVM/verifier coverage:

- Page-range parser, sorting, de-duplication, invalid ranges, quality/DPI clamping and request validation.
- `DocxWriterTest`: package parts, every XML part well-formed, headings, bullets, emphasis, centring, page breaks, picture embedding and scaling, XML escaping.
- `PdfTextLayoutTest`: line order and spacing, wrapped lines, de-hyphenation, heading and bullet detection, page numbers dropped, Markdown escaping, page markers.
- Resource parity across English, French, Spanish, German, Arabic and Hindi.
- Repository/DI/cancellation/render-path structural invariants.

Android integration tests defined in `AndroidConverterRepositoryTest`:

- Multi-image → PDF creates a two-page managed PDF and completed job.
- Batch image-format conversion creates one managed result per source.
- PDF → image ZIP honors selected pages and output format.
- PDF → TXT and DOCX create readable artifacts.
- Invalid PDF page range records a failed job.

The Android instrumentation suite requires an Android Gradle environment/device or emulator. `tools/verify_converter_feature.sh` is runnable without the Android SDK and exercises the production pure-Kotlin policies and DOCX writer.

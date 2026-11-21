# Feature 4 — Converter

## Scope

Converter is a native feature boundary and does not depend on the legacy Flutter service structure.

Supported conversions:

- One or more images → multi-page PDF.
- One or more images → JPEG, PNG, or WebP (one output per source image).
- PDF → JPEG page images in a ZIP archive.
- PDF → PNG page images in a ZIP archive.
- PDF → WebP page images in a ZIP archive.
- PDF → editable text-flow DOCX.
- PDF → plain UTF-8 text.

## User controls

- Ordered multi-image selection.
- PDF selection using Storage Access Framework.
- Persistable read permissions where the provider allows them.
- JPEG/WebP/PDF image quality.
- PDF page-image DPI from 72–300 DPI.
- Page selection expressions (`all`, `1-3,5`, etc.).
- Password for protected PDFs.
- Foreground progress and cancellation.
- Open/share every completed output.
- Recent conversion job state.

## Correctness invariants

- Conversion output is always written into Smart Edit managed storage.
- Completed output is registered in Files/Search and conversion History.
- Failed or cancelled operations remove partial generated output.
- Failed/cancelled jobs remain recorded with terminal state.
- Multi-image PDF generation loads images sequentially rather than retaining every bitmap at once.
- EXIF orientation is applied to image conversion and image-to-PDF conversion.
- PNG pages are rendered directly from PDF without a JPEG intermediate.
- WebP pages use a lossless PNG render intermediate before WebP encoding.
- Page ranges are 1-based to the user and validated before rendering.
- DOCX conversion is deliberately text-flow conversion; it does not claim layout fidelity for arbitrary PDFs.

## Tests

Pure JVM/verifier coverage:

- Page-range parser, sorting, de-duplication, invalid ranges, quality/DPI clamping and request validation.
- DOCX package structure and XML escaping.
- Resource parity across English, French, Spanish, German, Arabic and Hindi.
- Repository/DI/cancellation/render-path structural invariants.

Android integration tests defined in `AndroidConverterRepositoryTest`:

- Multi-image → PDF creates a two-page managed PDF and completed job.
- Batch image-format conversion creates one managed result per source.
- PDF → image ZIP honors selected pages and output format.
- PDF → TXT and DOCX create readable artifacts.
- Invalid PDF page range records a failed job.

The Android instrumentation suite requires an Android Gradle environment/device or emulator. `tools/verify_converter_feature.sh` is runnable without the Android SDK and exercises the production pure-Kotlin policies and DOCX writer.

# Phase 3: file actions, Compressor, OCR, Image editor

Status: **pure rules compiled and tested (113 checks); Android layer written, not compiled.**

## File actions (Files tab)
A file's menu now lists the tools that apply to it, using `FileActionPolicies`:
- PDF: Edit PDF, Convert, Compress.
- JPG/PNG/WebP image: Edit image, Extract text, Convert, Compress.
- Folders and other types: none.
Each action opens its tool with the file already chosen (`PdfToolsRoute(uri)`, `ImageEditorRoute(uri)`, `OcrRoute(uri)`, `ConverterRoute(uri)`, `CompressorRoute(uri)`). This restores the PDF editor, OCR and Image editor, which lost their only entry point when the old dashboard was removed. Each tool applies the pre-selected file once per screen instance, so rotating the phone never reopens it.
`UriMetadataReader` now understands `file:` URIs (name, size, type from extension), which the pre-selection relies on.

## Compressor
- Accepts PDFs and JPG/PNG/WebP (the old catch-all ZIP path is gone), up to 20 files at once, with progress, cancel and per-file results.
- Presets: High quality, Balanced, Smaller file.
- PDF methods: **Keep text** (default): shrinks pictures inside the PDF with PDFBox, text stays selectable. **Maximum**: renders every page as an image; the screen warns that text can no longer be selected or searched.
- Images: scaled to a preset limit, re-encoded as JPEG; images with transparency stay PNG.
- A compressed copy that is not smaller is deleted and the original is kept ("already optimized"). Savings under 2% are labelled "barely smaller".
- PDF pictures with masks or transparency, tiny pictures and already small streams are left alone. A picture shared by several pages is processed once so the file never grows from duplicates.
- Results: open, share, Save to device, percent saved, total saved. Partial files are removed on failure or cancel. Password-protected PDFs get a clear message.

## OCR (Extract text)
- Pick an image or arrive from the file menu; preview; choose the writing system (Latin, Chinese, Devanagari, Japanese, Korean).
- Detected language is shown; if it belongs to a different writing system a one-tap retry is offered.
- Result is editable, with word and character counts. Actions: Copy, Share, Save as note (tagged "ocr"), Save as .txt (into Files).
- Images only. PDFs are not offered Extract text (ML Kit reads images; PDF to TXT lives in Converter).

## Image editor
- Non-destructive: every edit is an operation on an undo/redo stack replayed on the original. Undo and redo in the top bar.
- Tools: rotate left/right, crop (edge sliders with a dimmed overlay), adjust (brightness, contrast, saturation, live preview), filters (original, grayscale, black and white), remove plain background.
- Consecutive rotations add up, a new adjustment replaces the previous one, edits that change nothing are ignored, a new edit clears redo.
- Saving replays the edits on a larger copy of the source (up to 4096 px) as JPEG, or PNG when transparency is involved. The result is registered in Files and Activity.

## Tests
`tools/verify_tools_feature.sh`: file-action rules, preset ordering, scaling, percent saved and verdicts, output names, which PDF pictures to skip, OCR text cleaning/stats/titles/file names/script suggestion, crop math, the whole edit stack (merging, cancelling, undo/redo, branching) and output size/format. `tools/static_checks.py` also checks the strings in all six languages.

## Not verified
- Compilation, including the PDFBox calls in `PdfBoxEngine.optimizeImages` (`cosObject.length`, `softMask`, `isStencil`, `resources.put`).
- Real-device behavior: ML Kit recognition, Bitmap memory on very large images, Compose layout of the editor and the crop overlay, file picker flows.
- How much real PDFs shrink in Keep text mode. PDFs with few or small pictures will report "already optimized".

## Known limits
- Crop uses sliders, not draggable handles.
- The "Original" filter chip only clears a filter that is the most recent edit.
- No audio or video tools (dropped from scope).

# Image tools: build plan (bring the EditPro site's photo tools and editing sheets into the app)

Audience: any engineer or AI agent helping build this. Read "Rules", "Architecture you must respect" and your phase, then work only inside your lane.
This is the follow-on to `docs/PDF_BUILD_PLAN.md`. The sheet framework built there (`core/ui/sheet/*`: `ToolSheet`, `SheetParts`, `SheetEdits`, `SheetPanel`, `Timeline`) is finished and merged, so image sheets reuse it as it is.
Reference implementation (read-only): the site, repo `vextech1/editpro` (SvelteKit). Everything the app must match is in
`src/lib/tools.ts` (tool list and wording), `src/lib/core/studio-tools.ts` (groups), `src/lib/components/editor/PhotoEditor.svelte` and `editor/sheets/*.svelte` (the sheets),
`src/lib/core/photo-ops.ts`, `presets.ts`, `shape.ts`, `annotate.ts`, `redact.ts`, `watermark.ts`, `frames.ts`, `collage.ts`, `palette.ts`, `print-sheet.ts`, `auto-crop.ts`, `image-edit.ts` (pure logic), and the older panels `ResizeImage`, `RoundImage`, `AnnotateImage`, `RedactImage`, `WatermarkImage`, `FrameImage`, `CollageImages`, `ImageTool` (compress and convert).

## Reminders
- **The app is ahead of the site in several image features. Never remove or weaken them** (list below). Bring the site's missing options in next to them.
- Image tools use the same rules as the PDF work: strings in all six languages, pure logic gets unit tests, CI is the compiler, branches from latest `main`, update the status table in the same commit.
- After the image tools: let the image editor use the wide-screen side panel for `ToolSheet` (the rule, `SheetPanel.useSidePanel`, and the layout already exist in the PDF editor; see `PdfToolsScreen.kt`), then a release check (Phase 9).

## Integration rule (decided by the owner): tools live inside the editors, not as separate features
- **No new Tools-tab tiles.** The Tools tab keeps only the popular entries it has today (Edit PDF, Create PDF, Compress, Edit Image, Convert, Scan Document, Extract Text). Every new image tool (Resize, Round, Draw & Text, Hide, Watermark, Frames, Export, Collage, Colour Picker, Photo sheet) is reached **from inside Edit Image**, in its More sheet grouped Edit, Mark and Output. Searching the Tools tab for "resize" or "watermark" may list them as shortcuts that open Edit Image with that sheet already open (use `ImageEditorRoute(uri, tool = ...)`), never as separate screens.
- **One set of sheets, used everywhere.** The Adjust, Crop, Rotate/Flip, Resize and Export sheets are written once in `feature/imageeditor/sheets/` and reused by the Scanner's page editor, the Converter's image list and the PDF editor's "add picture" flow, instead of each screen keeping its own copy. The Scanner's own crop-and-filter page editor (`ScanPageEditor.kt`) keeps its perspective corners and scan filters (those are app advantages) but swaps its Adjust, Rotate and Export controls for the shared sheets.
- **Scanner stays one popular tile, and hands off.** After scanning, each page has "Edit more", which opens that page in Edit Image (all sheets available) and returns the result to the scan. Its three start tiles (Live camera, Import images, Auto) stay as they are; nothing from the image plan adds a fourth. Export from the Scanner uses the same Export sheet (format, quality, size).
- **Multi-photo tools (Collage) are an editor tool too.** Collage opens from Edit Image > More > Output: it asks for photos inside the sheet flow, and its result lands back in the editor, not on a separate screen of its own. Colour Picker is a sheet over the open photo.
- **Result handling is shared.** Every tool ends on the editor's saved card (Open, Share, Save, Make PDF, Read text), so there is one place that does it. Do not write a new results screen.

## What the app already has (keep all of it)
| Area | App feature | Site equivalent |
|---|---|---|
| Image editor (`feature/imageeditor`) | Non-destructive edit stack with undo and redo, hold-to-compare, rotate left and right, free and ratio crop (1:1, 4:3, 3:4, 16:9), brightness, contrast, saturation, three filters (original, grayscale, black and white), **Remove background**, save as JPEG or PNG (PNG kept for transparency), Open and Share after save, "Next: make PDF" and "Next: read text" | Site has no background removal and no hold-to-compare |
| Scanner (`feature/scanner`) | Live camera with automatic page detection, perspective crop with draggable corners, scan modes (document, ID card, receipt, whiteboard, book spread split in two), five filters (original, grayscale, black and white, enhance, clean), page reordering, OCR per page, export to PDF | Site scanner is simpler (`Scanner.svelte`, `core/scan.ts`) |
| OCR (`feature/ocr`) | Text from photos and PDFs on the device, copy and share | `OcrTool.svelte` |
| Compressor (`feature/compressor`) | Images and PDFs together, three presets, per-file before/after and saved %, up to 20 files, ZIP of results | `ImageTool.svelte` (compress mode) |
| Converter (`feature/converter`) | Images to PDF with page size and order, image to JPEG, PNG or WebP, PDF to images and Word and text | `ImageTool.svelte` (convert mode) |

## What the site has that the app lacks (the gap)
Listed in the site's dock order (Edit, Mark, Output, `core/studio-tools.ts`).

### Edit group
| # | Site tool | What it does | Gap in the app |
|---|---|---|---|
| E1 | **Crop shapes** (`ASPECTS` in `image-edit.ts`) | Free, Original, 1:1, 4:5, 3:2, 16:9, 9:16; **Auto** crop of the page content (`auto-crop.ts`, `detectCropBox`); keyboard nudge; handles keep the ratio | App has Free, 1:1, 4:3, 3:4, 16:9 only. Add Original, 4:5, 3:2, 9:16 and Auto-crop |
| E2 | **Adjust** (`applyAdjust`) | Brightness, contrast, saturation, **temperature (cool to warm), tint (green to magenta)**, Look: original, black and white, **Clean document** (hard clip, paper to white, ink to black). Tone curve keeps pure black and white (no clipping) | App has three sliders and grayscale or B&W only. Add temperature, tint, Clean document, and the tone-table maths for brightness and contrast |
| E3 | **Rotate** and **Flip** sheets (`TurnSheet`, `Turn` model) | Quarter turns both ways, mirror left-right and top-bottom, shown live, applied with a tick; flip is correct after a quarter turn (`flipOnScreen`) | App has rotate left and right only, no flip |
| E4 | **Resize** (`ResizeSheet`, `presets.ts`, `resize.ts`, `photo-ops.ts` `targetPx`) | Size groups **ID and passport** (US, UK, Schengen, Canada, China, Australia, Kenya x2, Japan, three ID sizes), **Forms and exams** (200x230 px 20 to 50 KB, signature 140x60 px, 35x45 mm), **Print sizes** (wallet to A4), **Social media** (Instagram square, portrait, Story, YouTube thumbnail, profile, LinkedIn, Facebook, X); Custom with width, height, unit (px, mm, cm, in, %), dpi, keep shape; fit modes Crop to fill (zoom 100 to 400 %, drag to pan), Show whole photo (background colour), Stretch; **kept under a KB limit** for form presets (saved as JPEG); official sizes need a "I have checked this size" tick; limits 16 px to 8000 px and 40 MP; warns when enlarging | App has nothing |
| E5 | **Photo sheet for prints** (`print-sheet.ts`, in `ResizeImage.svelte`) | One-page PDF with the photo repeated to fill 4x6, 5x7, A4 or Letter, copies or fill the page, cut guides | App has nothing |
| E6 | **Round** (`RoundSheet`, `shape.ts`) | Circle, Rounded square, Soft corners, roundness 0 to 50 %, picture shape (original, 1:1, 4:5, 3:4, 9:16, 3:2, 16:9), zoom and drag, save as PNG or WebP (transparent corners) | App has nothing |

### Mark group
| # | Site tool | What it does | Gap in the app |
|---|---|---|---|
| M1 | **Draw & Text** (`MarkupSheet`, `annotate.ts`) | Pen, Arrow, Box, Oval, Text; six colours; thickness 1 to 10; text size 1 to 10; light box behind text; marks are in photo pixels so the saved picture stays sharp | App has nothing |
| M2 | **Blur or Hide** (`HideSheet`, `redact.ts`) | Drag boxes over faces, plates or addresses: Black box, Pixelate, Blur; strength 1 to 10; warns that earlier versions still show what is underneath | App has nothing |
| M3 | **Watermark** (`WatermarkSheet`, `watermark.ts`) | Text (colour, outline) or Logo (PNG); nine positions or Repeat; size (pinch), visible, turn, edge distance or spacing; drag to place | App has nothing (PDF watermark exists and its logic in `core/pdf/WatermarkLayout.kt` can be reused for the maths) |
| M4 | **Frames** (`FrameSheet`, `frames.ts`) | **Wall frame**: style (black, white, oak, walnut, gold), size (same shape as photo, 4x6 to 16x20 in, A4, A3, square), frame width, mat width and colour, soft shadow, hang on a wall colour. **Phone frame**: generic phone styles and colours, shadow, background none, colour or gradient, picture fit | App has nothing |
| M5 | **Colour Picker** (`ColorPicker.svelte`, `palette.ts`) | Tap the photo to read a colour (hex, RGB, HSL), main colours of the picture (median cut), readable text colour on it, copy | App has nothing |

### Output group
| # | Site tool | What it does | Gap in the app |
|---|---|---|---|
| O1 | **Export** (`ExportSheet`, `photo-ops.ts`) | Format JPG, PNG or WebP; quality 10 to 100 (off for PNG); longest side original, 2048, 1280 or 800 px; live size and **estimated file size**; never enlarges | App saves JPEG or PNG at a fixed quality and 4096 px |
| O2 | **Collage** (`CollageImages`, `collage.ts`) | Up to many photos; Grid (columns auto or 1 to 6, box shape 1:1 to 9:16, crop to fill or whole photo), Side by side, Stacked; spacing, background colour; output width 1080, 2000 or 3000; JPG, PNG or WebP with quality; reorder and remove photos; opens in the editor afterwards | App has nothing |
| O3 | **Compress** and **Convert** (single image panel) | Already covered by the app's Compressor and Converter | None (check WebP in Compressor and quality parity only) |

### Also check
- Site crossings between studios (`core/crossings.ts`: after an image result, offer Make PDF, Read text). The app already has "Next: make PDF" and "Next: read text" on the editor's saved card; the Round, Resize, Collage and Frames outputs must offer the same.
- Site shows "Earlier versions of this photo still show what is underneath" for Hide. The app's undo history must say the same.

## Architecture you must respect
1. **The editor is non-destructive.** `core/library/ImageEditModel.kt` holds `EditOp` and `EditStack` (done and undone lists, merging of consecutive adjusts and filters). `ImageEditorViewModel.render(source, ops)` replays the ops on a 1600 px preview, and `save()` replays them on a copy up to 4096 px. **Every new tool is a new `EditOp` plus its replay step**, not a bitmap you keep in a field. That keeps undo, redo and hold-to-compare working for free.
2. Put pure maths in `core/image/*` (new package, no Android imports where possible) and port from the matching site `.ts` file one to one, with unit tests in `app/src/test/java/com/vextech/editpro/core/image/`. Presets, resize maths, shape maths, redaction maths, watermark placement, frame layouts, collage layouts and palette are all pure and must be unit tested against numbers taken from the site's own tests (`src/lib/core/*.test.ts` if present).
3. **Sheets use `ToolSheet`** (`core/ui/sheet`): `Reset | Title | tick`, max 46 % height on phones (a side panel on wide screens once the image editor adopts `SheetPanel`), edits unapplied until the tick, two-level undo through `SheetEdits`. A sheet shows a live preview on the picture (as the PDF Watermark and Crop previews do) and only commits an `EditOp` on the tick.
4. (See the integration rule above.) The editor today has its own dock (`EditorDock`: Filters, Adjust, Crop, Rotate, Remove background) and its own crop and adjust panels. Move Adjust and Crop into sheets in Phase 1, keep Filters and Remove background, and add a **More** sheet of tools grouped like the PDF editor (Edit, Mark, Output).
5. Memory: 40 megapixels and 8000 px are the site's hard limits (`photo-ops.ts` `withinLimits`). Keep them, and downscale before filters on phones. Recycle intermediate bitmaps.
6. Hide and Blur must really destroy the pixels in the saved output (draw the box into the bitmap on the full-size replay), not just on the preview.

## Who does what, and status
Update this table in the same commit as your work. Claim a lane by changing the Owner and Status cells on `main` first (docs-only commit, `[skip ci]`), then branch.

| Item | Owner | Status | Branch / PR |
|---|---|---|---|
| Phase 0: image sheet host, `EditOp` extension points, Adjust and Crop moved into sheets (E1 crop shapes, E2 adjust extras) | stage-one session | Merged (CI green): Crop and Adjust are ToolSheets; crop shapes Original, 4:5, 3:2, 9:16 added beside the app's; Adjust has temperature and tint; filter Clean document; More sheet reads the registry `feature/imageeditor/ImageTools.kt` (add an `ImageToolId`, one `imageTools` entry and one branch in the screen's More handler). Pure logic in `core/image/` with tests. Auto-crop (E1) and Rotate/Flip sheet are still their own lanes | `img/phase0-sheet-host` |
| Phase 1 lane A: Rotate and Flip sheet (E3) | stage-one session | Merged (CI green): Rotate and Flip are ToolSheets with a live picture, mirror-on-screen is right after a quarter turn (`core/image/Turn.kt`), new `EditOp.Flip`; Flip added to the dock and More | `img/phase1-turn-export` |
| Phase 1 lane B: Export sheet with size estimate (O1) | stage-one session | Merged (CI green): Save opens the Export sheet: JPG, PNG, WebP, quality, longest side (original, 2048, 1280, 800), live size and about-how-large estimate; JPG not offered when the picture has see-through pixels; works on an unedited photo too (`core/image/ExportMath.kt`) | `img/phase1-turn-export` |
| Phase 2 lane C: Resize with presets (E4), logic and tests first | stage-one session | Merged (CI green): Resize sheet in More > Edit: 31 sizes in four groups (ID and passport, forms and exams, print, social), Custom with px, mm, cm, in and % plus dpi and keep shape, Crop to fill (zoom, drag to pan), Show whole photo (colour), Stretch; form sizes keep the file under their KB limit (JPG, best quality that fits); official sizes need the "checked" tick; 16 px to 8000 px and 40 MP limits; enlarge warning. Pure logic in `core/image/` (SizePresets, ResizeFit, ResizeForm) with tests. One generic note replaces the site's per-country notes | `img/phase2-resize` |
| Phase 2 lane D: Round corners (E6) | stage-one session | Merged (branch CI green; the run on `main` started at the merge): Round tool in the Edit Image editor (`RoundSheet.kt`) with circle, rounded and soft shapes, picture shape, roundness, zoom and drag; it crops to the shape and cuts transparent corners, so Export then offers PNG and WebP only. Pure shape logic has tests (`RoundShapeTest`); strings in all six languages. Merged to `main` by another session on request, fast-forward | `img/phase2-round` |
| Phase 2 lane E: Photo sheet for prints (E5) | stage-one session | Merged (CI green): Photo sheet in More > Output (`PrintSheetSheet.kt`): paper 4x6, 5x7, A4, US Letter; photo size from ID and print presets or typed mm; copies or fill the page; cutting lines; makes a one-page PDF (`PdfEngine.photoSheet`) and shows it on the saved card. Layout logic has tests (`PrintSheetTest`); strings in six languages. Simplification: the photo is cropped to fill the chosen size, not taken from the Resize sheet | `img/phase2-photosheet` |
| Phase 3 lane F: Draw & Text (M1) | stage-one session | Merged (CI green): Draw & Text in More > Mark (`MarkupSheet.kt`): pen, arrow, box, oval, text; six colours; thickness and text size 1 to 10; light box behind text; undo last mark. Marks are kept as fractions of the picture (`EditOp.Marks`), so they replay sharp at save size; shared drawing code `MarkPainter` draws both the on-screen layer and the saved picture. Logic has tests (`MarksTest`); strings in six languages | `img/phase3-draw` |
| Phase 3 lane G: Blur or Hide (M2) | stage-one session | Merged (CI green): Blur or Hide in More > Mark (`HideSheet.kt`): drag boxes, Black box, Pixelate, Blur, strength 1 to 10, undo last box, live preview. Boxes are fractions of the picture (`EditOp.Hide`) and `Redact` rewrites the pixels inside them in the full-size save replay, so nothing underneath survives; the sheet warns that earlier versions still show it. Logic has tests (`RedactTest`); strings in six languages | `img/phase3-hide` |
| Phase 3 lane H: Watermark (M3) | stage-one session | Merged (CI green): Watermark in More > Mark (`WatermarkSheet.kt`): text (colour, outline) or logo (picked image), nine spots or Repeat, size, visible, turn, edge distance or spacing; one finger drags the mark, two fingers pinch to resize. Placement maths ported from `watermark.ts` into `core/image/WatermarkMath.kt` with tests (`WatermarkMathTest`); the logo is staged to a file so the save replays it at full size. Not reused: the PDF `WatermarkLayout` (it works in PDF points, so the photo maths is its own) | `img/phase3-watermark` |
| Phase 4 lane I: Frames (M4) | stage-one session | Merged (CI green): Frames in More > Mark (`FrameSheet.kt`): Wall frame (black, white, oak, walnut, gold; nine sizes; frame and mat width; mat colour; wall colour or none; inner shadow; crop or whole photo) and generic Phone frame (four styles, four colours, shadow, no/colour/gradient background, crop or whole photo). Layout in `core/image/Frames.kt` with tests (`FramesTest`), drawing in `core/processing/FramePainter.kt`. A phone frame with no background is the only transparent result, so Export then offers PNG and WebP only | `img/phase4-frames` |
| Phase 5: Collage (O2) | stage-one session | Merged (CI green): Collage in More > Output (`CollageSheet.kt`): the open photo plus up to 11 more picked inside the sheet, reorder and remove, Grid (columns auto or 1 to 6, six box shapes, crop or whole photo), Side by side, Stacked; spacing, background colour, width 1080, 2000 or 3000; live preview. Making it replaces the photo in the editor (new source, so the edit history starts again); Save then uses the Export sheet for format and quality. Layouts in `core/image/Collage.kt` with tests (`CollageTest`) | `img/phase5-collage` |
| Phase 6: Colour Picker (M5) | open | Not started | |
| Phase 7: Auto-crop (E1) | open | Not started | |
| Phase 8: Crossings after each output (Make PDF, Read text) | open | Not started | |
| Phase 9: Polish and release check | whoever finishes last | Not started | |

## Phases, in building order
**Phase 0, foundation (do first, one agent).** Add the image sheet host and the tool groups to `ImageEditorScreen`; add `EditOp` variants as lanes need them (agree the sealed interface additions in the PR so lanes do not collide); move Crop and Adjust into `ToolSheet`; extend crop shapes (Original, 4:5, 3:2, 9:16) and Adjust (temperature, tint, Clean document, tone table). This phase touches `ImageEditorScreen.kt`, `ImageEditorViewModel.kt` and `core/library/ImageEditModel.kt`; **nobody else edits these until it merges**. Other lanes may write their pure logic and tests in `core/image/*` meanwhile.

**Phase 1, small and independent.** A: Rotate and Flip sheet. B: Export sheet (format, quality, longest side, live size estimate by encoding the preview and scaling).

**Phase 2, sizes and shapes.** C: Resize with all presets, units, dpi, fit modes, KB limit (binary search on JPEG quality), confirm tick, limits. D: Round with shapes and roundness, PNG or WebP output. E: Photo sheet for prints as a one-page PDF (reuse the app's PDF writer).

**Phase 3, marking.** F: Draw & Text. G: Blur or Hide (pixels destroyed in the full-size replay). H: Watermark (reuse `core/pdf/WatermarkLayout.kt` placement maths where it matches `watermark.ts`).

**Phase 4, Frames.** Wall and phone frames drawn with `Canvas`, no brand artwork (the site's phone shapes are generic on purpose; keep them generic).

**Phase 5, Collage.** Opens from Edit Image > More > Output (no separate screen or Tools tile); photo picking and layout controls inside a sheet flow; layouts from `collage.ts`; the result replaces the photo in the editor.

**Phase 6, Colour Picker.** Tap to read colours, palette, copy.

**Phase 7, Auto-crop.** Port `detectContentBox`; offered in Crop as "Auto".

**Phase 8, scanner and crossings.** Scanner pages get "Edit more" into Edit Image and share the Adjust, Rotate, Crop and Export sheets.  After Resize, Round, Collage, Frames and Export results, offer Make PDF and Read text, like the editor's saved card.

**Phase 9, polish.** Strings in all six languages and `static_checks.py` clean; TalkBack labels on the picture, handles and sliders; reduced motion; large-text check; check on a real phone (nothing has been tried on a device yet); update `docs/features` if it lists image tools.

## Rules
1. Package and application id: `com.vextech.editpro`. Code lives in `app/src/main/java/com/vextech/editpro/`. No "Smart Edit" anywhere.
2. Strings: every new user-visible string goes in `values/strings.xml` AND `values-ar`, `-de`, `-es`, `-fr`, `-hi`. Escape double quotes (`\"`) and apostrophes. `python3 tools/static_checks.py` enforces key parity, quote escaping and unused keys. Run it before every push.
3. CI: `.github/workflows/android-build.yml` (static checks, `assembleDebug`, `testDebugUnitTest`). Push to a branch, wait for green. No local Android SDK in the cloud workspace, so CI is the compiler. A manual run (`workflow_dispatch`) on `main` produces the `editpro-debug-apk` artifact.
4. Branches: `img/<phase>-<topic>` from the latest `main`. Merge to `main` only when CI is green. Use `[skip ci]` only for docs-only commits.
5. Secrets: never commit tokens, passwords or keystores except the existing shared debug key. `app/google-services.json` (project `editpro1`) is on `main` on purpose.
6. Commit trailers: `Co-Authored-By: <agent name> <noreply@anthropic.com>` and the session link if the harness gives one.
7. Every pure-logic piece gets a unit test. Screens are verified by CI compile only; say so in the PR.
8. Before claiming a lane: `git fetch --prune`, read this table on `main`, and check `git branch -r` for an existing `img/*` branch.

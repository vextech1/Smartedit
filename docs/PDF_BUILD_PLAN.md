# PDF tools: build plan (bring the EditPro site's PDF tools and editing sheets into the app)

Audience: any engineer or AI agent helping build this. Read "Rules", "Repo and branch state" and your phase, then work only inside your lane.
Reference implementation (read-only): the site, repo `vextech1/editpro` (SvelteKit). Everything the app must match is in
`src/lib/tools.ts` (tool list and wording), `src/lib/components/*Pdf*.svelte`, `src/lib/components/editor/*` (sheets, dock, undo) and `src/lib/core/pdf-*.ts` (logic).
The app's advantages (annotations, ink, redact, forms, bookmarks, search, password protection, print, undo/redo, saved-files list) stay. Never remove them.

## Reminders
- **After the PDF work, the next job is the image edit tools.** Do the same comparison for them (site `PhotoEditor.svelte`, `editor/sheets/*`, `core/image-*.ts`, `photo-*.ts` against the app's `feature/imageeditor`) and write `docs/IMAGE_BUILD_PLAN.md` before building. Do not start it until the PDF phases are done.
- **The app is ahead of the site in many features** (for example PDF annotations, ink, redaction, forms, bookmarks, search, password protection, print, undo/redo, saved-files list, plus extra converter formats such as WebP). Never remove or weaken an app feature to match the site. Bring the site's missing options in next to them. If the app already does something better, keep the app's version and note it in your PR.

## Who does what, and status
Owner of Phase 0 and Phase 1 ("stage one"): the Claude session that wrote this plan. Everyone else starts on the "next steps" below. **Update this table in the same commit as your work** (change the status, add your branch or PR), so it always shows what is being built.

| Item | Owner | Status | Branch / PR |
|---|---|---|---|
| Phase 0: ToolSheet, form parts, PageRange, two-level undo | stage-one session | Done and merged to main (CI green). Metadata runs in a ToolSheet. Wide-screen side panel not done yet | `pdf/phase0-sheet` |
| Phase 1 lane A: Organize extras | stage-one session | Built (Arrange dialog: reverse, rotate all, double-sided fix); awaiting CI | `pdf/phase1-organize` |
| Phase 1 lane B: Split by ranges + ZIP | stage-one session | Built (Split dialog: every page or ranges, ZIP when 2+ parts); awaiting CI | `pdf/phase1-split` |
| Phase 1 lane C: Converter (PDF to images, text, Word) | stage-one session | Queued | |
| Phase 1 lane D: Compressor grayscale | stage-one session | Queued | |
| Phase 1 lane E: Metadata remove-all | stage-one session | Queued | |
| Phase 1 lane F: Merge error, Images to PDF margin | stage-one session | Queued | |
| Phase 2 lane G: Watermark (logic + tests first) | open, for another agent | Not started | |
| Phase 2 lane H: Page numbers/headers/footers (logic + tests first) | open, for another agent | Not started | |
| Phase 2 lane I: Crop (logic + tests first) | open, for another agent | Not started | |
| Phase 3: Extract images | open, for another agent | Not started | |
| Phase 4: Compress summary | open, for another agent | Not started | |
| Phase 5: Reading mode | open, for another agent | Not started | |
| Phase 6: Polish and release check | whoever finishes last | Not started | |

**Next steps for other agents, in order of independence:**
1. Phase 3 (Extract images) and Phase 5 (Reading mode): they do not need the new sheet, so start now. Build the logic and tests, and put the screen in a plain screen or dialog; switch it into `ToolSheet` after Phase 0 merges.
2. Phase 2 (G, H, I): write the pure logic and its unit tests now (text layout, template tokens, margins, paper sizes). Build the sheet UI once Phase 0 is on `main`.
3. Phase 4 (Compress summary): after Phase 1 lane D merges, to avoid conflicts in the compressor.
Stage-one files you must not touch until Phase 0/1 merge: `feature/pdf/PdfToolsScreen.kt`, `PdfEditorViewModel.kt`, `feature/converter/*`, `feature/compressor/*`, `feature/pdf/data/*` (ask in the PR if you must).

## Rules
1. Package and application id: `com.vextech.editpro`. Code lives in `app/src/main/java/com/vextech/editpro/`. No "Smart Edit" / "smartedit" anywhere.
2. Strings: every new user-visible string goes in `values/strings.xml` AND `values-ar`, `-de`, `-es`, `-fr`, `-hi`. `python3 tools/static_checks.py` enforces key parity and unused keys. Run it before every push.
3. CI: `.github/workflows/android-build.yml` (static checks, `assembleDebug`, `testDebugUnitTest`). Push to a branch, trigger the workflow, wait for green. No local Android SDK in the cloud workspace, so CI is the compiler.
4. Branches: work on `pdf/<phase>-<topic>` branches from the latest `main`, open a PR into `main`. Only merge to `main` when CI is green. Use `[skip ci]` only for docs-only commits.
5. Secrets: never commit tokens, passwords or keystores except the existing shared debug key. `app/google-services.json` (project `editpro1`) is already committed on `main` on purpose.
6. Commit trailers (required): `Co-Authored-By: <agent name> <noreply@anthropic.com>` and the session link if the harness gives one.
7. Every new pure-logic piece (range parsing, stamp layout, split plans, ordering) gets a unit test in `app/src/test/java/com/vextech/editpro/...`. Screens are verified by CI compile only; say so in the PR.
8. Match the site's behaviour and wording first. Where the app's engine (PdfBox for editing) cannot match a site feature, stop and note it in the PR instead of faking it.
9. Keep Material 3 + Compose. Reuse `FeatureScaffold`, `EditProDesign.kt` (colours, rows, chips) from `feature/home`.

## Repo state
Only `main` matters. It has everything so far: EditPro rename and package, Google sign-in, My files, upload flow, Settings, the class Library with student and admin uploads and moderation (shared Firestore with the site), the fixed debug signing key, and AI Assist removed from Profile. Start every branch from the latest `main`.
Not done by design: replacing a Library file of the same name, Library links, structure editing and relabelling (admin side). Nothing has been run on a real device by a person yet (sign-in and uploads untested).

## Today: app vs site (PDF)
App already has: open/merge/insert/blank page, reorder/delete/duplicate/rotate/extract pages, split to single pages, crop (drawn box), fixed watermark text, fixed page numbers,
annotate (highlight, underline, strikeout, ink, rectangle, text, note, link, images, signature), redact, forms + flatten, bookmarks, search, extract text, metadata view/edit,
protect/unprotect, print, share, undo/redo, converter (images to PDF, PDF to images, PDF to Word/text), compressor, OCR, scanner.
Site extras the app lacks are listed in the phases below. Editor design differences:

| | Site | App |
|---|---|---|
| Applying edits | Edit in a bottom sheet, then tick (apply) or Reset | Applied immediately |
| Undo/redo | Unapplied edits first, then saved versions | Saved changes only |
| Settings UI | Bottom sheets (46% max on phones, side panel on wide screens) | Dialogs, panels, full screens |
| Tool access | Scrolling dock + group sheets | Dock + long More menu |
| Scope | Page ranges in many tools | Mostly whole document |

## Phases
Do phases in order; inside a phase, lanes can run in parallel because they touch different files.

### Phase 0: Foundation (one agent; blocks everything else)
Files: new `core/ui/sheet/` (or `feature/pdf/sheet/`), `feature/pdf/PdfToolsScreen.kt`, `PdfEditorViewModel.kt`.
- `ToolSheet` composable: grab handle (closes without applying), header `Reset | Title | tick`, max height 46% on phones, side panel (about 360dp) on wide screens, focus handling, Back closes the sheet. Reference: `editor/ToolSheet.svelte`, `sheet.css`.
- Form parts: labelled field/select, slider row, checkbox row, hint and warning text, page-range field. Reference: `Field.svelte`, `SliderRow.svelte`, `Chips.svelte`.
- `PageRange` parser: `1-3, 5, 7-9`, rejects 0, reversed, out of range; returns sorted unique indices. Unit tests mirror the site's range parsing.
- Two-level undo: a sheet keeps a timeline of unapplied edits (one step per drag or burst of typing); the top bar's Undo/Redo use the open sheet first, then the saved versions. Reference: `sheet-history.svelte.ts`, `core/timeline.ts`. Unit-test the timeline.
- Wire the dock/More menu to open sheets. Colours and corner radius follow `EditProDesign.kt`.
Done when: one existing tool (use Metadata) runs in a `ToolSheet` with Reset and tick, tests green.

### Phase 1: Missing options in tools the app has (parallel lanes)
- Lane A, Organize: Reverse order, Rotate all (both directions), double-sided scan fix (both "backs reversed" and normal). Port `doubleSidedOrder` from `core/pdf-edit.ts` with tests.
- Lane B, Split: split by ranges (`1-3, 5, 7-9`) plus every page; result as separate files and one ZIP. Port logic from `SplitTool.svelte`/`pdf-tools.ts`. Extend `splitToManaged`.
- Lane C, Converter: PDF to images single page + presets Screen 100 / Standard 150 / High 220 dpi; PDF to text Markdown output and page markers (port `core/pdf-markdown.ts`, `pdf-text.ts`); PDF to Word "keep pages with no text (scans) as pictures".
- Lane D, Compressor: grayscale option with the warning that text can no longer be selected; keep standard/maximum and presets.
- Lane E, Metadata: "Remove all metadata" tick box; show Created with and PDF producer; move into a `ToolSheet`.
- Lane F, small items: Merge error for protected/damaged files; Images to PDF margin (none/small/large).

### Phase 2: Full watermark, page numbers, crop (parallel lanes, each a `ToolSheet`)
- Lane G, Watermark: text or picture/logo; size %, angle (45, 0, 30, custom), see-through %, centre or tiled, page selection. Reference `WatermarkPdf.svelte`, `sheets/PdfWatermarkSheet.svelte`, `core/pdf-watermark.ts`. Keep the note that watermarks are removable.
- Lane H, Page numbers/headers/footers: six spots (header and footer left/centre/right), ready-made styles, custom text with `{n}`, `{total}`, `{date}`, `{file}`, `{bates}`, Bates prefix/start/digits, first number, from/to page, size, margin mm, grayscale. Reference `StampPdf.svelte`, `core/pdf-stamp.ts`. Western letters only; keep the site's note. Unit-test the text template and positions.
- Lane I, Crop: Trim margins in mm (top/bottom/left/right) and Fit to paper (A4, Letter, A3, A5, Legal; portrait/landscape/match each page; margin mm); page selection by range. Keep the app's drawn-box crop. Keep the note that cropped content is hidden, not erased (point to Redact). Reference `CropPdf.svelte`.

### Phase 3: Extract images from a PDF (one agent)
Port `ExtractImages.svelte` + `core/pdf-pictures.ts`: save embedded pictures singly or as a ZIP; JPEG (DCTDecode) copied byte-for-byte; option to skip images under 50 px; count skipped; keep the site's note about unsupported kinds (scanned fax, spot colour) and that vector drawings are not pictures. Add as a tool tile and a sheet.

### Phase 4: Compress summary
Per-file before/after size and saved %, up to 20 files at once, same notes as the site. Reference `CompressPdf.svelte`, `core/pdf-compress.ts`.

### Phase 5: Reading mode
Full-screen reader with a single Exit control, system Back also exits, document otherwise untouched. Reference `ReadingMode.svelte`, `PdfReader.svelte`.

### Phase 6: Polish and release check
- Strings in all six locales for everything added; `static_checks.py` clean.
- Accessibility: content descriptions, focus order in sheets, reduced motion.
- Remove More-menu entries that moved into sheets; update `docs/features/pdf.md`, `tools.md`.
- Final full CI run on `main`; a person checks sheets on a phone (cannot be done in CI).

## Risks
- PdfBox limits: grayscale compression and exact JPEG passthrough may need custom handling; confirm before promising.
- Memory: high-dpi PDF to images on long files; keep the site's warning, cap resolution.
- Text: PdfBox standard fonts cover Western letters only; keep the site's limitation text.
- Sheets cannot be visually tested in CI; keep layouts simple and follow the site's spacing.

## Hand-off checklist for each PR
Branch name, phase/lane, what changed, tests added, `static_checks.py` output, CI run link, anything that could not match the site and why.

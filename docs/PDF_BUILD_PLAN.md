# PDF tools: build plan (bring the EditPro site's PDF tools and editing sheets into the app)

Status (2026-11-24): **Phases 0 to 5 are built and merged to `main`, with CI green.** Phase 6 (polish) is partly done; what is left is listed under "Open items".
Nothing here has been tried on a real phone by a person yet.

Audience: any engineer or AI agent helping build this. Read "Rules" and "Open items", then work inside your own lane.
Reference implementation (read-only): the site, repo `vextech1/editpro` (SvelteKit). Everything the app must match is in
`src/lib/tools.ts` (tool list and wording), `src/lib/components/*Pdf*.svelte`, `src/lib/components/editor/*` (sheets, dock, undo) and `src/lib/core/pdf-*.ts` (logic).
The app's advantages (annotations, ink, redact, forms, bookmarks, search, password protection, print, undo/redo, saved-files list) stay. Never remove or weaken an app feature to match the site; bring the site's missing options in next to them.
The follow-on job, the image editor, has its own plan: `docs/IMAGE_BUILD_PLAN.md`. It reuses the sheet framework built here (`core/ui/sheet/*`).

## Status
Update this table in the same commit as your work.

| Phase | What was built | Status |
|---|---|---|
| 0: Foundation | `ToolSheet` (handle, `Reset \| Title \| tick`, 46% max height on phones), form parts (text box, select, tick box, slider, chips, colour swatches, hint, warning), page-range parser, two-level undo (unapplied sheet edits first, then saved versions). Metadata runs in a sheet | Merged |
| 1A: Organize | Arrange dialog: reverse, rotate all, double-sided scan fix | Merged |
| 1B: Split | Every page or by ranges; ZIP when there are two or more parts | Merged |
| 1C: Converter | PDF to images at 100/150/220 dpi; text as Markdown or plain with page markers; PDF to Word with headings, bullets and "keep scans as pictures" | Merged |
| 1D: Compressor | Grayscale tick box with the text-can't-be-selected warning; always uses the raster path; result kept even if larger | Merged |
| 1E: Metadata | Remove-all tick (info, dates, XMP); Created with and PDF producer fields | Merged |
| 1F: Small items | Images to PDF margin (none/small/large for A4 and Letter); clear error when a PDF to merge is protected or damaged | Merged |
| 2G: Watermark | Text or logo; nine spots or repeat; size, visible, turn, edge distance or spacing; page range; live preview on the page | Merged |
| 2H: Page numbers | Header and footer, left/centre/right; six ready-made styles; tokens `{n}` `{total}` `{date}` `{file}` `{bates}`; Bates; first number; page range; font, size, margin mm, gray | Merged |
| 2I: Crop and resize | Trim margins in mm or fit to paper (A3, A4, A5, Letter, Legal); page range; live trim preview; per-page "margins too large" check. The drawn-box crop stays | Merged |
| 3: Extract pictures | Sheet; JPEG copied byte for byte, others rebuilt as PNG; tiny pictures skipped; ZIP | Merged |
| 4: Compress summary | The app already had per-file before/after, saved %, 20 files and the notes. Added "N% larger / same size" for kept grayscale copies, a "Converted to grayscale" note, and one ZIP of all kept results | Merged |
| 5: Reading mode | Full-screen reader, one Exit button, Back also exits, system bars hidden. Opens from More > Reading mode | Merged |
| 6: Polish and release check | Strings in six languages and static checks clean; TalkBack semantics for the shared sheet parts; page thumbnails, grid tiles, annotate/sign options and dock items announce their role and state; Split and Arrange are sheets; `docs/features/pdf.md`, `tools.md`, `converter.md` and `ui-kit.md` updated; More menu reviewed (no duplicates); on wide screens a sheet is a side panel (`SheetPanel`); the old fixed page-number engine method removed; full CI run on `main` green | Partly done, see below |

## Open items
1. **A person checks everything on a phone.** CI only compiles and runs unit tests. Look at: each sheet with TalkBack, watermark and trim previews, a watermark with a see-through logo, page numbers and watermark on a rotated page, compressing 2 or 3 files (including one grayscale run) and the ZIP card, Reading mode exit.
2. ~~Wide-screen side panel for `ToolSheet`~~ Done on `pdf/phase6-side-panel`: on tablets and big windows (840 dp or wider) and on landscape screens at least 560 dp wide, an open sheet in the PDF editor is a panel beside the page (360 dp, at most 45% of the width) instead of a bottom sheet; the dock returns when the sheet closes. The rule is `SheetPanel.useSidePanel` (unit tested). Not yet used by the image editor; its sheets stay bottom sheets. Needs a look on a tablet and a landscape phone.
3. **Move the remaining tools into sheets.** Split, Arrange, Forms and Bookmarks are sheets. Decided with the owner: Search becomes a sheet next; the unsaved-changes, password and redaction confirmations and the one-field prompt (Save as, text, note, link) stay dialogs; the annotations list and extracted-text viewer stay dialogs (read-only).
4. **Accessibility audit of the page canvas and the dock.** Partly done on `pdf/phase6-a11y-converter-docs`: page thumbnails (strip and grid) now announce "Page N" and whether they are selected; the grid tile is a checkbox; the annotate/sign tool options are a radio group; dock items have a button role and a selected state. Not done: the page canvas itself, and focus order in sheets (not changed or tested).
5. ~~`docs/features/converter.md`~~ Done on `pdf/phase6-a11y-converter-docs`: compared with lane C and updated (text as Markdown, page markers, dpi presets, Word with structure and scan pictures, tests, the 500-page cap difference).
6. Branch `pdf/phase6-a11y-converter-docs` (items 4 and 5 above). The earlier claim on `pdf/phase6-polish` was never used; its work was merged by another session as `pdf/phase6-a11y-docs`.

## Notes from the build
- PdfBox limits: grayscale compression goes through the raster path (text cannot be selected afterwards, and the screen says so); JPEG pictures are copied byte for byte when extracted.
- Text on a page (watermark, page numbers) uses the PDF standard fonts, so Western letters only; the sheets say so. A logo covers other alphabets for watermarks.
- Cropping hides content, it does not erase it; the sheet points to Redact.
- A watermark can be edited or removed by anyone with PDF software; the sheet says so.
- High-dpi PDF to images on long files is memory-heavy; the converter keeps the site's warning and caps resolution.
- Sheets cannot be visually tested in CI; layouts follow the site's spacing and stay simple.

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

## Hand-off checklist for each PR
Branch name, phase/lane, what changed, tests added, `static_checks.py` output, CI run link, anything that could not match the site and why.

# Feature 3 — PDF Editor

Status: **implemented at source level**

EditPro's PDF feature is a native working-copy editor rather than a collection of one-shot conversion buttons. Opening a document stages an isolated editable copy. Mutations are undoable/redoable until Save or Save As commits a permanent managed output.

## Document and page editing

- Open password-protected or unprotected PDFs.
- Thumbnail/page strip with multi-page selection.
- Merge documents.
- Split every page to separate managed PDFs.
- Extract selected pages.
- Reorder pages using a strict full permutation; reordering never silently drops pages.
- Delete selected pages.
- Duplicate selected pages.
- Insert selected pages from another PDF.
- Insert a blank page using the current page size.
- Rotate selected pages in 90-degree increments.
- Crop selected pages using normalized visual coordinates that also account for page rotation (drawn on the page), or trim margins in millimetres and fit pages to paper from the Crop sheet (see "Tool sheets").
- Add a text or logo watermark (Watermark sheet).
- Add page numbers, headers, footers and Bates numbers (Page numbers sheet).
- Extract the pictures from a PDF (Extract pictures sheet).
- Read in a full-screen reading mode.
- Render page previews.
- Save, Save As, share the unsaved working copy, and print the unsaved working copy.

## Tool sheets

Tools with settings open as a bottom sheet over the page (`ToolSheet`): a handle, then `Reset | Title | tick`. The tick applies the tool; the handle or system Back closes it without applying. Edits inside a sheet stay unapplied until the tick, and the top bar's Undo and Redo step through them first. On phones a sheet covers at most 46% of the screen so the page stays visible. Page-range boxes read `1-3, 5` (blank means every page).

- **Watermark:** text (colour swatches) or a logo picture; nine spots or a repeat; size, how visible, turn, and edge distance or spacing; optional page range. The mark is drawn live on the page while the sheet is open, using the same layout code as the file writer, so the preview shows where it will land. Rotated pages stay upright. A repeat of marks too small to fit on the page shows a warning and the tick stays off. Text uses the standard Helvetica font (Western letters); other alphabets need a logo. A watermark can be edited or removed by anyone with PDF software.
- **Page numbers:** header and footer, each with left, centre and right text, from six ready-made styles or custom text. Tokens: `{n}`, `{total}`, `{date}`, `{file}`, `{bates}`. Options: first and last page, first number, font (sans, serif, typewriter), size, margin in millimetres, gray text, Bates prefix, start and digits. Western letters only.
- **Crop and resize:** Trim margins in millimetres (top, bottom, left, right, as the page looks) or Fit to paper (A3, A4, A5, Letter, Legal; match each page, portrait or landscape; margin), for every page or a page range. In Trim mode the page behind the sheet shades what would be hidden. A trim that would leave a chosen page under 12 points is refused in the sheet and names the first such page. Cropped content is hidden, not erased; use Redact to remove it for good.
- **Extract pictures:** pictures are saved from the PDF (JPEG copied byte for byte, others rebuilt as PNG, tiny ones skipped) and offered as a ZIP.
- **Properties:** title, author and other fields, a "remove all metadata" tick, and the Created with and PDF producer fields.
- **Organize and Split:** arrange pages (reverse, rotate all, double-sided scan fix); split every page or by ranges, with a ZIP when there are two or more parts.
- **Reading mode:** a full-screen page reader with one Exit button. System Back also exits, the system bars are hidden, and the document is left untouched.

### Accessibility of sheets

The sheet handle is a button that says which sheet it closes; the tick says which tool it applies; the title is a heading. Chips and colour swatches are selectable groups that speak the choice and whether it is selected. Swatches have 48 dp touch targets. Each slider speaks its label with the current value, and validation errors are announced when they appear. Sheets and the page previews do not animate, so there is nothing to reduce for reduced-motion settings. A person still needs to check the sheets with TalkBack on a phone.

## Annotation and content tools

- Highlight, underline, and strikeout as real PDF text-markup annotations with generated appearance streams.
- Sticky-note/comment annotations.
- External hyperlink annotations. Only `http`, `https`, and `mailto` URIs are accepted.
- Add searchable text overlay.
- Freehand ink.
- Rectangle drawing.
- Place an image, stamp, or visual handwritten signature.
- List document annotations and remove supported PDF annotations.
- Secure redaction.

### Important editing semantics

`Highlight`, `Underline`, `Strikeout`, `Note`, and `Link` are PDF annotations and can be enumerated/removed by the annotation manager.

Freehand ink and rectangle drawing are currently written into the page content stream. They participate in working-copy undo/redo but are not independently removable after a Save boundary.

Added text is a new searchable overlay; EditPro does not claim to semantically reflow or replace arbitrary existing PDF text objects. The current overlay writer uses PDFBox's built-in Helvetica and substitutes unsupported characters outside that font's simple encoding. Existing PDF text extraction/search is not subject to that overlay-font limitation.

The Signature tool is **visual Fill & Sign**: it places a user-selected signature image/stamp on the page. It is not a certificate-backed cryptographic/PKI digital signature and the UI/docs must not describe it as one.

## Forms

- Discover AcroForm fields.
- Display field name/value/type/read-only state.
- Fill writable fields.
- Flatten forms into page content.
- Form edits honor the PDF's form-fill permission.

Dynamic XFA form authoring/editing is not implemented; PDFBox itself does not flatten dynamic XFA as normal AcroForms.

## Search, metadata, bookmarks, and properties

- Extract document text.
- Search text page-by-page with page-indexed snippets.
- Read/update title, author, subject, keywords, creator, and producer metadata, or remove all metadata.
- Read bookmarks/outlines with destination pages.
- Add bookmarks.
- Remove bookmarks without relying on nonexistent PDFBox convenience APIs.
- Inspect page dimensions and rotation.

## Protection and permissions

- Open encrypted PDFs by password.
- Inspect printing, copying, modification, page-assembly, annotation, and form-fill permissions.
- Distinguish owner access from restricted user access.
- Apply standard password protection with explicit permissions.
- Prefer AES with a 128-bit key for the PDFBox 2.0.x standard security handler used by this project.
- Remove protection using owner access.
- Page organization, annotations, form filling, printing, and full-content modification each check the permission relevant to that operation rather than treating all editing as one permission.

## Secure redaction

Redaction is intentionally destructive, not a black rectangle placed over live text.

For the selected page EditPro:

1. Requires protection to be removed first, so redaction never silently changes or weakens encryption.
2. Flattens form fields so visible form state does not leave dangling form references.
3. Renders the selected page at high resolution.
4. Burns the black redaction region into the rendered page.
5. Rebuilds the document into a fresh PDF object graph, importing unaffected pages and replacing the selected page with the rasterized redacted page.
6. Recreates simple page bookmarks and copies standard document metadata.

This removes the selected page's original searchable/content objects from the new saved document rather than merely hiding them. The operation remains undoable while still inside the working-copy session; after Save, the old redacted content is not part of the newly saved PDF.

## Working-copy and history invariants

- The source PDF is never mutated directly.
- Each edit creates a rollback checkpoint before mutation.
- Failed mutations restore the prior working copy.
- Undo history is bounded to avoid unlimited cache growth.
- A new edit after Undo invalidates Redo history.
- Save/Save As establishes a new clean history boundary.
- Page-preview cache entries are invalidated after relevant mutations.
- Dirty sessions require explicit discard before back navigation.
- Closing a session deletes its staged working/history files.
- Sharing/printing use temporary copies of the current unsaved working document and do not force a permanent Save.

## Localization

PDF Editor UI, confirmations, errors, protection controls, and tool names are resource-backed in:

- English
- French
- Spanish
- German
- Arabic
- Hindi

## Verification

Fast verification that does not require Android dependency resolution:

```bash
./tools/verify_pdf_feature.sh
```

The verifier:

- compiles and executes production PDF policy code;
- exercises page-selection/reorder/rotation/geometry/security/link invariants;
- compiles and executes production working-copy history logic;
- checks undo/redo, failed-mutation rollback, bounded history, redo invalidation, and Save boundaries;
- validates all localized XML resources;
- checks `PdfEngine` and `PdfEditorRepository` interface/implementation parity;
- rejects unfinished PDF TODO/FIXME/NotImplementedError placeholders.

JVM tests:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.vextech.editpro.feature.pdf.*'
```

Android PDFBox round-trip tests:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.vextech.editpro.feature.pdf.PdfBoxEngineTest
```

The Android suite covers structural edits, blank-page insertion, page numbering, hyperlinks, removable markup annotations, metadata/search/bookmarks, password permissions, form fill/flatten, secure-redaction behavior (including encrypted-redaction rejection), and merge/split/extract outputs.

A full Android Gradle compile/test run cannot be completed in the current sandbox because the configured Gradle distribution cannot be resolved from `services.gradle.org`. The source-level verifier passing is not represented as a substitute for that Android build; the Android tests remain for execution in an environment with Gradle/dependency access.

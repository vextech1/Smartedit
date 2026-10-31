# Feature 2 — Scanner / Document Capture

## Scope
Native persistent multi-page scan sessions with three input paths: Google ML Kit automatic document scanner, Android SAF multi-image import, and CameraX live capture.

## Invariants
- A scan session persists independently of its UI lifecycle.
- Every page has a stable ID and explicit position.
- Original capture is retained; edits regenerate a rendered derivative from the original.
- Crop is represented by four normalized corners and perspective-mapped before filtering.
- Reordering preserves every page exactly once.
- Deleting a page renumbers remaining positions.
- OCR runs against the current rendered page and stores text + detected language on that page.
- PDF export is rejected for an empty session and respects current page order.
- Export registers the PDF in Smart Edit Files and History and updates the legacy aggregate scan row used by existing dashboard surfaces.
- Discard removes the session and its managed page files.
- Blocking bitmap, OCR, filesystem and PDF work runs on Dispatchers.IO.

## Phase 0 behaviour
- New scans are auto-named with a timestamp (e.g. "Scan 31 Oct 11:36").
- Draft sessions with no pages are deleted when the session is closed, and at screen start (60 s grace).
- Deleting a page can be undone for 5 seconds; the file is only removed after that. Any other change commits a pending delete first.
- OCR text opens in a sheet (copy, share, word/character count). Tapping the OCR badge reopens it.
- OCR script is auto-detected (Latin first, then the script of the detected language, then other scripts if nothing was found) and can be re-run as Latin, Chinese, Devanagari, Japanese or Korean.
- The page editor can apply the chosen filter and rotation to all pages; each page keeps its own crop.

## Phase 1: fast capture
- Opening the Scanner goes straight to the camera. A session with no pages is dropped when you come back.
- Live edge detection (back camera): `DocumentDetector` finds the page from low-resolution brightness frames (no OpenCV). It needs a page clearly lighter or darker than its surroundings and tilted less than 45 degrees. When nothing is found the static guide frame shows and the manual shutter still works.
- Auto-capture (toggle in the top bar, on by default): the shutter fires when the outline stays still for about 0.9 s, with a progress ring, haptic and flash. It will not take the same page twice; the page must leave the view or move before it re-arms. Manual shots also disarm it.
- Auto-crop: the outline at the moment of the shot is saved as the page's perspective crop (only when it covers at least 20% of the frame). The editor reopens with those corners.
- Batch mode: the camera stays open; a thumbnail strip shows every page and the newest one can be removed.
- Quick export: the PDF button on the camera exports the session and opens the share sheet.
- Camera and gallery photos are rewritten upright on import (EXIF orientation baked in), so crops line up with what the user saw.

## Phase 2: faster review
- Smart default filter: new camera and gallery pages start as Original (colourful), Black & White (clean text on paper) or Grayscale (everything else), judged on the cropped page. ML Kit scanner pages are left as they are. The choice is a heuristic (`FilterAdvisor`); the editor can change it.
- Drag to reorder: long-press a page and drag it onto another. Screen readers get "move left / right" actions instead of arrows. There is no auto-scroll while dragging, so on long documents move in steps.
- Multi-select: "Select" next to the page count; tap pages, then apply a filter to all of them or delete them (with the same 5-second undo).
- Fix orientation: a button in the editor reads the page at 0/90/180/270 degrees with OCR and turns it to the angle that yields the most text. Latin text only; returns "looks correct" when unsure.
- Capture check: after each camera shot the page is checked for blur (variance of the Laplacian below 100 on a 480 px copy) and darkness (mean brightness below 50), and a "Retake" prompt removes the page. Thresholds are starting values to tune on real captures.
- Fixed: reordering pages could fail on the unique (session, position) index; it now parks rows on temporary positions inside one transaction.

## Phase 3: capture modes
Pick a mode with the chips at the bottom of the camera.
- **Document:** as before (smart filter, auto-crop).
- **ID card:** shoot the front, then the back (auto-capture re-arms when the card is flipped). Both go on one A4 page at real card size (85.6 x 54 mm at 300 dpi), on a white sheet with a light border. "Skip back" (or Done / PDF) finishes with the front only. Retake on the front drops it.
- **Receipt / Whiteboard:** pre-crop to the outline and use the new **Clean** filter. Receipts accept narrower outlines than documents.
- **Book:** the whole spread is kept, cut at the dark gutter near the middle (or exactly in half when none is found) into two pages, with Clean applied.
- **Clean filter** (also available in the editor and multi-select): estimates the paper colour on a coarse grid, divides it out and darkens mid-tones, so shadows and uneven light disappear while coloured ink stays coloured.
- **Cut-outs:** the top-bar menu has "Signature to PNG" (Clean first, then plain background removed) and "Cut out to PNG" (plain background removed only) for the selected page. Margins are trimmed and the transparent PNG can be shared or saved. Only background connected to the edges is removed, so enclosed white (inside letters) stays; it needs a plain backdrop.

## Editing
Supported non-destructive operations: perspective/rectangular corner crop, 90-degree rotation, Original, Grayscale, Black & White, and Enhance filters.

## Verification
Run `./tools/verify_scanner_feature.sh`.

The local verifier compiles and executes production ScannerPolicies, checks persistence/DI/live-camera wiring, checks localized resources, and rejects TODO/FIXME/NotImplementedError placeholders.

JUnit tests: `ScannerPoliciesTest`.
Android instrumentation tests: `PerspectiveCropperTest`.

A full Android Gradle compile still requires network access to obtain the configured Gradle distribution/dependencies; this sandbox cannot resolve services.gradle.org.

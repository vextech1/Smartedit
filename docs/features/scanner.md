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

## Editing
Supported non-destructive operations: perspective/rectangular corner crop, 90-degree rotation, Original, Grayscale, Black & White, and Enhance filters.

## Verification
Run `./tools/verify_scanner_feature.sh`.

The local verifier compiles and executes production ScannerPolicies, checks persistence/DI/live-camera wiring, checks localized resources, and rejects TODO/FIXME/NotImplementedError placeholders.

JUnit tests: `ScannerPoliciesTest`.
Android instrumentation tests: `PerspectiveCropperTest`.

A full Android Gradle compile still requires network access to obtain the configured Gradle distribution/dependencies; this sandbox cannot resolve services.gradle.org.

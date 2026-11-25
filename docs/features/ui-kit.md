# Phase 1 — Shared UI kit (`core/ui`)

Status: **implemented at source level** (not yet compiled).

## Components
| Piece | File | Purpose |
|---|---|---|
| `UiText` | `UiText.kt` | ViewModel-safe localized message: `Res`, `Plural`, `Dynamic`. Resolve at the screen with `asString()`. |
| `UiEvent`, `UiEventChannel`, `CollectUiEvents` | `UiEvent.kt` | One-off effects (snackbar messages) kept out of `UiState`. Each event reaches one collector. |
| `LoadState`, `LoadStateContent` | `LoadState.kt` | Standard Loading / Success / Failure shape plus a renderer. |
| `FeatureScaffold` | `Common.kt` | Now takes `snackbarHost`; back-arrow description is localized (`R.string.back`). |
| `LoadingState`, `ErrorState`, `ConfirmDialog`, `ProgressCard`, `ResultCard`, `SectionHeader`, `SourcePickerRow` | `Components.kt` | Reusable building blocks. No hard-coded user text. |
| `rememberSaveToDevice` | `SaveToDevice.kt` | "Save to device" through the system file picker (ACTION_CREATE_DOCUMENT). No storage permission. |
| `ShareService.saveManagedTo` | `core/sharing/ShareService.kt` | Copies a managed file to the chosen destination on the injected IO dispatcher. Managed-root check is the same one used by open/share. |
| `@DefaultDispatcher` | `core/di/DispatchersModule.kt` | Added next to `@IoDispatcher`. |

## Adopted so far
- **Converter:** progress → `ProgressCard`; outputs → `ResultCard` with Save to device; open/share failures now show a message instead of failing silently; snackbar host wired.
- **Files:** move-to-trash dialog → `ConfirmDialog`.
- **All screens:** back-arrow content description now localized.

## Not migrated yet (deliberate)
- PDF editor and Scanner keep their typed message enums and custom dialogs. They are large and working; they adopt `UiText` / kit components when next touched.
- Tier B screens are rewritten on the kit in Phases 2–4, not patched.

## New string keys (all six locales)
`common_retry`, `common_loading`, `common_error_generic`, `common_save_to_device`, `common_saved_to_device`, `common_save_failed`, `common_open_failed`, `common_share_failed`, `common_progress_percent`.
`common_error_generic` is not used yet; the Notes/Library screens use it first.

## Verification
`tools/verify_ui_kit.sh` runs `tools/static_checks.py`: import resolution (main, test, androidTest), duplicate declarations, same type name in several packages, `R.string` references, locale key parity, XML validity, hard-coded text in `core/ui`, TODO/FIXME markers. No Android SDK needed.
Not run here: Gradle compile, Compose rendering, the system file picker flow.

## Tool sheets (`core/ui/sheet`)
Shared by the PDF editor and the image editor. Added after Phase 1; see `docs/PDF_BUILD_PLAN.md` for how they were built.

| Piece | File | Purpose |
|---|---|---|
| `ToolSheet` | `ToolSheet.kt` | The sheet frame: handle (closes without applying), `Reset | Title | tick`, scrolling content, max 46% of the screen height on phones. In a side panel (`LocalSheetPanel`) it fills the panel instead. |
| `SheetEdits`, `SheetHistory`, `SheetHistoryRegistry`, `rememberSheetEdits` | `SheetEdits.kt` | The unapplied edits of one sheet (the draft its fields show, plus undo and redo steps; quick runs of typing or dragging merge into one step). The top bar's Undo/Redo step through them first, then through the document's saved versions. |
| `SheetTextField`, `SheetSlider`, `SheetCheck`, `SheetChips`, `SheetSwatches`, `SheetHint`, `SheetWarning` and the other form parts | `SheetParts.kt` | Form building blocks. Sliders speak their label, chips and swatches are selectable groups, warnings are announced. |
| `SheetPanel`, `LocalSheetPanel` | `SheetPanel.kt` | When a sheet becomes a side panel (width 840 dp or more, or landscape and 560 dp or more) and how wide (360 dp, at most 45% of the width). The host lays out the page and panel side by side; the PDF editor does, the image editor does not yet. |
| `Timeline` | `Timeline.kt` | A list of snapshots with a position: the step-by-step history behind Undo and Redo for edits a sheet has not applied yet. Pure Kotlin, unit tested. |

